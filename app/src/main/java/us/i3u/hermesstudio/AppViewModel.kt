package us.i3u.hermesstudio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Loading, Onboarding, Login, Chats, Groups, Conversation, Room, Profiles, Settings }

/** The two list tabs, mirroring Studio's chat / group-chat switch. */
enum class Tab { Chats, Groups }

data class ChatLine(
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false,
    val timestamp: String? = null,
    val sender: String? = null,
)

data class UiState(
    val screen: Screen = Screen.Login,
    val tab: Tab = Tab.Chats,
    val baseUrl: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val account: String? = null,
    val profiles: List<Profile> = emptyList(),
    /** Blank means "All profiles", the same default Studio shows. */
    val profileFilter: String = "",
    val activeProfile: String = "",
    val sessions: List<SessionSummary> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val openSession: SessionSummary? = null,
    val openRoom: RoomDetail? = null,
    val lines: List<ChatLine> = emptyList(),
    val loadingHistory: Boolean = false,
    val sending: Boolean = false,
    val attachments: List<Upload> = emptyList(),
    val attaching: Boolean = false,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    /** Text produced by the last recording, consumed by the composer. */
    val transcript: String? = null,
    val models: List<ModelOption> = emptyList(),
    val loadingModels: Boolean = false,
    /** Blank means the profile default, matching Studio's "Default" chip. */
    val reasoningEffort: String = "",
    /** BCP-47 tag chosen in Settings; blank follows the system. */
    val language: String = "",
    val sessionModel: String? = null,
    val defaultModel: String? = null,
    val savingSetting: Boolean = false,
    val notice: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    /** Resolves strings in the language chosen in Settings, not the phone's. */
    private val localized = AppLocale.wrap(app)
    private val api = HermesApi(store.baseUrl, store.token)
    private val recorder = Recorder(app)

    private val _state = MutableStateFlow(
        UiState(
            // A configured install must never flash the credentials form: it reads
            // as "sign in again" even though the token is still good.
            screen = when {
                store.isConfigured -> Screen.Loading
                store.onboarded -> Screen.Login
                else -> Screen.Onboarding
            },
            baseUrl = store.baseUrl,
            reasoningEffort = store.reasoningEffort,
            language = store.language,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // The cached mark is on disk, so the launch screen can show it at once.
        viewModelScope.launch { AppLogo.load(app) }
        if (store.isConfigured) restoreSession()
    }

    fun finishOnboarding() {
        store.onboarded = true
        _state.update { it.copy(screen = Screen.Login) }
    }

    private fun restoreSession() = launchWork(
        work = {
            api.update(store.baseUrl, store.token)
            Triple(api.verifyToken(), api.profiles(), api.sessions(null))
        },
        onSuccess = { (account, profiles, sessions) ->
            _state.update {
                it.copy(
                    screen = Screen.Chats,
                    account = account,
                    profiles = profiles,
                    activeProfile = pickProfile(profiles),
                    sessions = sessions,
                    error = null,
                )
            }
            syncBranding()
        },
        onFailure = { failure ->
            store.clearCredentials()
            _state.update {
                it.copy(
                    screen = Screen.Login,
                    error = str(R.string.error_session_expired, failure.readableMessage(localized)),
                )
            }
        },
    )

    fun login(baseUrl: String, username: String, password: String) {
        val normalized = normalizeUrl(baseUrl)
        if (normalized == null) {
            _state.update { it.copy(error = str(R.string.error_server_address)) }
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = str(R.string.error_credentials_required)) }
            return
        }

        launchWork(
            work = {
                api.update(normalized, "")
                val token = api.login(username.trim(), password)
                store.baseUrl = normalized
                store.token = token
                api.update(normalized, token)
                listOf(api.verifyToken(), api.profiles(), api.sessions(null))
            },
            onSuccess = { parts ->
                @Suppress("UNCHECKED_CAST")
                val profiles = parts[1] as List<Profile>

                @Suppress("UNCHECKED_CAST")
                val sessions = parts[2] as List<SessionSummary>
                _state.update {
                    it.copy(
                        screen = Screen.Chats,
                        baseUrl = normalized,
                        account = parts[0] as String,
                        profiles = profiles,
                        activeProfile = pickProfile(profiles),
                        sessions = sessions,
                        error = null,
                    )
                }
                syncBranding()
            },
        )
    }

    /** Pulls the Studio logo from the connected server for the launch screen. */
    private fun syncBranding(force: Boolean = false) {
        viewModelScope.launch {
            runCatching { AppLogo.syncFromServer(getApplication<Application>(), api, force) }
        }
    }

    // ── lists ─────────────────────────────────────────────────────────────

    fun showTab(tab: Tab) {
        _state.update { it.copy(tab = tab, error = null) }
        when (tab) {
            Tab.Chats -> {
                _state.update { it.copy(screen = Screen.Chats) }
                if (_state.value.sessions.isEmpty()) refreshSessions()
            }
            Tab.Groups -> {
                _state.update { it.copy(screen = Screen.Groups) }
                if (_state.value.rooms.isEmpty()) refreshRooms()
            }
        }
    }

    fun refreshSessions() = launchWork(
        work = { api.sessions(_state.value.profileFilter.ifBlank { null }) },
        onSuccess = { sessions -> _state.update { it.copy(sessions = sessions) } },
    )

    fun refreshRooms() = launchWork(
        work = { api.rooms() },
        onSuccess = { rooms -> _state.update { it.copy(rooms = rooms) } },
    )

    fun setProfileFilter(profile: String) {
        _state.update { it.copy(profileFilter = profile) }
        refreshSessions()
    }

    fun refreshProfiles() = launchWork(
        work = { api.profiles() },
        onSuccess = { profiles ->
            _state.update { it.copy(profiles = profiles, activeProfile = pickProfile(profiles)) }
        },
    )

    fun selectProfile(name: String) {
        store.profile = name
        _state.update { it.copy(activeProfile = name, screen = Screen.Chats, tab = Tab.Chats) }
    }

    // ── conversation ──────────────────────────────────────────────────────

    /** Open an existing Studio conversation and load its history. */
    fun openSession(session: SessionSummary) {
        val profile = session.profile ?: _state.value.activeProfile
        store.setSessionFor(profile.ifBlank { "default" }, session.id)
        _state.update {
            it.copy(
                screen = Screen.Conversation,
                openSession = session,
                sessionModel = session.model,
                lines = emptyList(),
                loadingHistory = true,
                error = null,
            )
        }

        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.messages(session.id) } }
                .onSuccess { history ->
                    _state.update { state ->
                        state.copy(
                            loadingHistory = false,
                            lines = history.map { message ->
                                ChatLine(
                                    text = message.content,
                                    fromUser = message.fromUser,
                                    timestamp = message.timestamp,
                                )
                            },
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loadingHistory = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun startNewConversation() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        store.setSessionFor(profile, "")
        _state.update {
            it.copy(screen = Screen.Conversation, openSession = null, lines = emptyList(), error = null)
        }
    }

    fun send(message: String) {
        val text = message.trim()
        val files = _state.value.attachments
        if ((text.isEmpty() && files.isEmpty()) || _state.value.sending) return
        val session = _state.value.openSession
        val profile = session?.profile?.ifBlank { null }
            ?: _state.value.activeProfile.ifBlank { "default" }

        val echo = if (files.isEmpty()) text else {
            listOf(text.ifBlank { null }, files.joinToString(", ") { "📎 " + it.name })
                .filterNotNull()
                .joinToString("\n")
        }
        _state.update {
            it.copy(
                lines = it.lines + ChatLine(echo, fromUser = true),
                sending = true,
                attachments = emptyList(),
                error = null,
            )
        }

        viewModelScope.launch {
            val stored = store.sessionFor(profile).ifBlank { session?.id }
            runCatching {
                withContext(Dispatchers.IO) {
                    api.sendMessage(
                        profile = profile,
                        input = text,
                        sessionId = stored,
                        attachments = files,
                        reasoningEffort = _state.value.reasoningEffort,
                    )
                }
            }.onSuccess { reply ->
                reply.sessionId?.let { store.setSessionFor(profile, it) }
                val line = if (reply.error != null && reply.output.isBlank()) {
                    ChatLine(reply.error, fromUser = false, isError = true)
                } else {
                    ChatLine(reply.output, fromUser = false)
                }
                _state.update { it.copy(lines = it.lines + line, sending = false) }
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        lines = it.lines + ChatLine(failure.readableMessage(localized), fromUser = false, isError = true),
                        sending = false,
                    )
                }
            }
        }
    }

    // ── attachments ───────────────────────────────────────────────────────

    /** Uploads the picked file to the server so the agent can read it by path. */
    fun attach(bytes: ByteArray, filename: String, mime: String) {
        val profile = currentProfile()
        _state.update { it.copy(attaching = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.upload(profile, bytes, filename, mime) } }
                .onSuccess { upload ->
                    _state.update { it.copy(attaching = false, attachments = it.attachments + upload) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(attaching = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    fun removeAttachment(upload: Upload) {
        _state.update { it.copy(attachments = it.attachments - upload) }
    }

    // ── voice ─────────────────────────────────────────────────────────────

    fun startRecording() {
        if (_state.value.recording) return
        if (recorder.start()) {
            _state.update { it.copy(recording = true, error = null) }
        } else {
            _state.update { it.copy(error = str(R.string.error_microphone)) }
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        _state.update { it.copy(recording = false) }
    }

    /** Stops the take and turns it into text with the profile's STT provider. */
    fun stopRecordingAndTranscribe() {
        if (!_state.value.recording) return
        val bytes = recorder.stop()
        _state.update { it.copy(recording = false) }
        if (bytes == null) {
            _state.update { it.copy(error = str(R.string.error_recording_short)) }
            return
        }

        val profile = currentProfile()
        _state.update { it.copy(transcribing = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.transcribe(profile, bytes, "voice.m4a", "audio/mp4")
                }
            }.onSuccess { text ->
                _state.update { it.copy(transcribing = false, transcript = text) }
            }.onFailure { failure ->
                _state.update { it.copy(transcribing = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    /** Sends the recorded audio itself instead of its transcript. */
    fun stopRecordingAndAttach() {
        if (!_state.value.recording) return
        val bytes = recorder.stop()
        _state.update { it.copy(recording = false) }
        if (bytes == null) {
            _state.update { it.copy(error = str(R.string.error_recording_short)) }
            return
        }
        attach(bytes, "voice-${System.currentTimeMillis()}.m4a", "audio/mp4")
    }

    fun consumeTranscript() = _state.update { it.copy(transcript = null) }

    // ── model and reasoning ───────────────────────────────────────────────

    fun loadModels() {
        if (_state.value.models.isNotEmpty() || _state.value.loadingModels) return
        val profile = currentProfile()
        _state.update { it.copy(loadingModels = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.availableModels(profile) } }
                .onSuccess { models -> _state.update { it.copy(models = models, loadingModels = false) } }
                .onFailure { failure ->
                    _state.update { it.copy(loadingModels = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    /** Applies a model to the open session, or remembers it for the next one. */
    fun selectModel(option: ModelOption) {
        val sessionId = _state.value.openSession?.id
            ?: store.sessionFor(currentProfile()).ifBlank { null }
        _state.update { it.copy(sessionModel = option.id) }
        if (sessionId == null) return

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.setSessionModel(sessionId, option.id, option.provider) }
            }.onFailure { failure ->
                _state.update { it.copy(error = failure.readableMessage(localized)) }
            }
        }
    }

    fun setReasoningEffort(effort: String) {
        store.reasoningEffort = effort
        _state.update { it.copy(reasoningEffort = effort) }
    }

    // ── group room ────────────────────────────────────────────────────────

    fun openRoom(room: Room) {
        _state.update {
            it.copy(screen = Screen.Room, openRoom = null, loadingHistory = true, error = null)
        }
        launchWork(
            work = { api.room(room.id) },
            onSuccess = { detail ->
                _state.update { it.copy(openRoom = detail, loadingHistory = false) }
            },
            onFailure = { failure ->
                _state.update { it.copy(loadingHistory = false, error = failure.readableMessage(localized)) }
            },
        )
    }

    // ── settings ──────────────────────────────────────────────────────────

    fun openSettings() {
        _state.update { it.copy(screen = Screen.Settings, error = null, notice = null) }
        loadModels()
        val profile = _state.value.activeProfile.ifBlank { "default" }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.defaultModel(profile) } }
                .onSuccess { model -> _state.update { it.copy(defaultModel = model) } }
        }
    }

    /** Writes the profile default, which is what new conversations start from. */
    fun setDefaultModel(option: ModelOption) {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.setDefaultModel(profile, option.id, option.provider) }
            }.onSuccess {
                _state.update {
                    it.copy(
                        savingSetting = false,
                        defaultModel = option.id,
                        notice = str(R.string.notice_default_model, profile, option.id),
                    )
                }
            }.onFailure { failure ->
                _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    fun restartGateway() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.restartGateway(profile) } }
                .onSuccess {
                    _state.update {
                        it.copy(savingSetting = false, notice = str(R.string.notice_gateway_restarting, profile))
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    /** Replaces the app mark with a picture from the device. */
    fun setAppLogo(bytes: ByteArray) {
        viewModelScope.launch {
            val applied = AppLogo.setCustom(getApplication<Application>(), bytes)
            _state.update {
                if (applied) it.copy(notice = str(R.string.notice_logo_updated), error = null)
                else it.copy(error = str(R.string.error_image_unreadable))
            }
        }
    }

    /** Goes back to whatever logo the connected Studio serves. */
    fun resetAppLogo() {
        viewModelScope.launch {
            AppLogo.clearCustom(getApplication<Application>())
            runCatching { AppLogo.syncFromServer(getApplication<Application>(), api, force = true) }
            _state.update {
                if (AppLogo.image != null) {
                    it.copy(notice = str(R.string.notice_logo_from_server), error = null)
                } else {
                    it.copy(error = str(R.string.error_no_server_logo))
                }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ── misc ──────────────────────────────────────────────────────────────

    fun back() {
        val target = if (_state.value.tab == Tab.Groups) Screen.Groups else Screen.Chats
        _state.update { it.copy(screen = target, error = null) }
    }

    fun show(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun signOut() {
        store.clearCredentials()
        _state.update {
            UiState(
                screen = Screen.Login,
                baseUrl = store.baseUrl,
                reasoningEffort = store.reasoningEffort,
                language = store.language,
            )
        }
    }

    /** Chooses the language for every screen; the activity restarts to apply it. */
    fun setLanguage(tag: String) {
        store.language = tag
        _state.update { it.copy(language = tag) }
    }

    private fun str(id: Int, vararg args: Any): String = localized.getString(id, *args)

    private fun currentProfile(): String =
        _state.value.openSession?.profile?.ifBlank { null }
            ?: _state.value.activeProfile.ifBlank { "default" }

    private fun pickProfile(profiles: List<Profile>): String {
        val stored = store.profile
        if (stored.isNotBlank() && profiles.any { it.name == stored }) return stored
        val chosen = profiles.firstOrNull { it.active }?.name
            ?: profiles.firstOrNull()?.name
            ?: "default"
        store.profile = chosen
        return chosen
    }

    private fun <T> launchWork(
        work: suspend () -> T,
        onSuccess: (T) -> Unit,
        onFailure: ((Throwable) -> Unit)? = null,
    ) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { work() } }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false) }
                    onSuccess(it)
                }
                .onFailure { failure ->
                    _state.update { state -> state.copy(busy = false) }
                    if (onFailure != null) onFailure(failure)
                    else _state.update { state -> state.copy(error = failure.readableMessage(localized)) }
                }
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return runCatching { java.net.URL(withScheme) }.map { withScheme }.getOrNull()
    }
}

private fun Throwable.readableMessage(context: android.content.Context): String = when (this) {
    // A HermesException already carries what the server said, in its own words.
    is HermesException -> message ?: context.getString(R.string.error_request_failed)
    is java.net.UnknownHostException -> context.getString(R.string.error_unreachable)
    is java.net.SocketTimeoutException -> context.getString(R.string.error_timeout)
    is javax.net.ssl.SSLException -> context.getString(R.string.error_tls)
    else -> message ?: this::class.java.simpleName
}
