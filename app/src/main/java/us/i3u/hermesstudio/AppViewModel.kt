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

enum class Screen { Login, Chats, Groups, Conversation, Room, Profiles }

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
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val api = HermesApi(store.baseUrl, store.token)
    private val recorder = Recorder(app)

    private val _state = MutableStateFlow(UiState(baseUrl = store.baseUrl))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (store.isConfigured) restoreSession()
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
        },
        onFailure = {
            store.clearCredentials()
            _state.update { it.copy(screen = Screen.Login, error = null) }
        },
    )

    fun login(baseUrl: String, username: String, password: String) {
        val normalized = normalizeUrl(baseUrl)
        if (normalized == null) {
            _state.update { it.copy(error = "Enter a server address such as https://hermes.example.com") }
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Username and password are required") }
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
            },
        )
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
                        it.copy(loadingHistory = false, error = failure.readableMessage())
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
                withContext(Dispatchers.IO) { api.sendMessage(profile, text, stored, files) }
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
                        lines = it.lines + ChatLine(failure.readableMessage(), fromUser = false, isError = true),
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
                    _state.update { it.copy(attaching = false, error = failure.readableMessage()) }
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
            _state.update { it.copy(error = "Could not start the microphone") }
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
            _state.update { it.copy(error = "That recording was too short") }
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
                _state.update { it.copy(transcribing = false, error = failure.readableMessage()) }
            }
        }
    }

    /** Sends the recorded audio itself instead of its transcript. */
    fun stopRecordingAndAttach() {
        if (!_state.value.recording) return
        val bytes = recorder.stop()
        _state.update { it.copy(recording = false) }
        if (bytes == null) {
            _state.update { it.copy(error = "That recording was too short") }
            return
        }
        attach(bytes, "voice-${System.currentTimeMillis()}.m4a", "audio/mp4")
    }

    fun consumeTranscript() = _state.update { it.copy(transcript = null) }

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
                _state.update { it.copy(loadingHistory = false, error = failure.readableMessage()) }
            },
        )
    }

    // ── misc ──────────────────────────────────────────────────────────────

    fun back() {
        val target = if (_state.value.tab == Tab.Groups) Screen.Groups else Screen.Chats
        _state.update { it.copy(screen = target, error = null) }
    }

    fun show(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun signOut() {
        store.clearCredentials()
        _state.update { UiState(baseUrl = store.baseUrl) }
    }

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
                    else _state.update { state -> state.copy(error = failure.readableMessage()) }
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

private fun Throwable.readableMessage(): String = when (this) {
    is HermesException -> message ?: "Request failed"
    is java.net.UnknownHostException -> "Cannot reach that server address"
    is java.net.SocketTimeoutException -> "The server took too long to answer"
    is javax.net.ssl.SSLException -> "TLS handshake failed for that address"
    else -> message ?: this::class.java.simpleName
}
