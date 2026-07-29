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

enum class Screen { Login, Profiles, Chat, Sessions }

data class ChatLine(
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false,
)

data class UiState(
    val screen: Screen = Screen.Login,
    val baseUrl: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val account: String? = null,
    val profiles: List<Profile> = emptyList(),
    val activeProfile: String = "",
    val sessions: List<SessionSummary> = emptyList(),
    val lines: List<ChatLine> = emptyList(),
    val sending: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val api = HermesApi(store.baseUrl, store.token)

    private val _state = MutableStateFlow(UiState(baseUrl = store.baseUrl))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (store.isConfigured) {
            _state.update { it.copy(activeProfile = store.profile) }
            restoreSession()
        }
    }

    private fun restoreSession() = launchWork(
        work = {
            api.update(store.baseUrl, store.token)
            val account = api.verifyToken()
            val profiles = api.profiles()
            account to profiles
        },
        onSuccess = { (account, profiles) ->
            val active = pickProfile(profiles)
            _state.update {
                it.copy(
                    screen = Screen.Chat,
                    account = account,
                    profiles = profiles,
                    activeProfile = active,
                    error = null,
                )
            }
        },
        onFailure = {
            // A stale token should land the user on the login screen, not an error wall.
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
                val account = api.verifyToken()
                val profiles = api.profiles()
                Triple(account, profiles, normalized)
            },
            onSuccess = { (account, profiles, url) ->
                val active = pickProfile(profiles)
                _state.update {
                    it.copy(
                        screen = Screen.Chat,
                        baseUrl = url,
                        account = account,
                        profiles = profiles,
                        activeProfile = active,
                        error = null,
                    )
                }
            },
        )
    }

    fun refreshProfiles() = launchWork(
        work = { api.profiles() },
        onSuccess = { profiles ->
            _state.update { it.copy(profiles = profiles, activeProfile = pickProfile(profiles)) }
        },
    )

    fun selectProfile(name: String) {
        store.profile = name
        _state.update { it.copy(activeProfile = name, lines = emptyList(), screen = Screen.Chat) }
    }

    fun loadSessions() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(screen = Screen.Sessions) }
        launchWork(
            work = { api.sessions(profile) },
            onSuccess = { sessions -> _state.update { it.copy(sessions = sessions) } },
        )
    }

    /** Continue an existing conversation: later turns reuse this session id. */
    fun continueSession(session: SessionSummary) {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        store.setSessionFor(profile, session.id)
        _state.update {
            it.copy(
                screen = Screen.Chat,
                lines = listOf(ChatLine("Continuing “${session.title}”", fromUser = false)),
            )
        }
    }

    fun startNewConversation() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        store.setSessionFor(profile, "")
        _state.update { it.copy(lines = emptyList(), error = null) }
    }

    fun send(message: String) {
        val text = message.trim()
        if (text.isEmpty() || _state.value.sending) return
        val profile = _state.value.activeProfile.ifBlank { "default" }

        _state.update {
            it.copy(
                lines = it.lines + ChatLine(text, fromUser = true),
                sending = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.sendMessage(profile, text, store.sessionFor(profile).ifBlank { null })
                }
            }
            result.onSuccess { reply ->
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

    fun show(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun signOut() {
        store.clearCredentials()
        _state.update { UiState(baseUrl = store.baseUrl) }
    }

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
