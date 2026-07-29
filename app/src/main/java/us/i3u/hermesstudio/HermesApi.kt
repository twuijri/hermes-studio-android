package us.i3u.hermesstudio

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client over the Hermes Studio HTTP API.
 *
 * Every endpoint used here is the same one the Studio web UI calls, so the app
 * stays compatible with a stock server. Keeping all of them in one file means a
 * server-side change only ever has to be chased in a single place.
 */
class HermesApi(
    private var baseUrl: String,
    private var token: String,
) {
    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun update(baseUrl: String, token: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.token = token
    }

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    private fun request(path: String, method: String, body: JSONObject?): Request {
        val builder = Request.Builder().url(url(path))
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        builder.header("Accept", "application/json")
        when (method) {
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(json))
            else -> builder.get()
        }
        return builder.build()
    }

    private fun call(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        client.newCall(request(path, method, body)).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("error") }.getOrNull()
                throw HermesException(
                    if (detail.isNullOrBlank()) "HTTP ${response.code}" else "HTTP ${response.code}: $detail",
                )
            }
            if (text.isBlank()) return JSONObject()
            return runCatching { JSONObject(text) }.getOrElse {
                JSONObject().put("data", runCatching { JSONArray(text) }.getOrDefault(JSONArray()))
            }
        }
    }

    /** POST /api/auth/login — returns the bearer token used by every other call. */
    fun login(username: String, password: String): String {
        val body = JSONObject().put("username", username).put("password", password)
        val result = call("/api/auth/login", "POST", body)
        val issued = result.optString("token")
        if (issued.isBlank()) throw HermesException("Login succeeded but no token was returned")
        return issued
    }

    /** GET /api/auth/me — cheap check that a stored token is still valid. */
    fun verifyToken(): String {
        val me = call("/api/auth/me")
        return me.optString("username").ifBlank { me.optString("userId") }
    }

    /** GET /api/hermes/profiles */
    fun profiles(): List<Profile> {
        val array = call("/api/hermes/profiles").optJSONArray("profiles") ?: JSONArray()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Profile(
                name = item.optString("name"),
                model = item.optString("model").takeIf { it.isNotBlank() && it != "—" },
                active = item.optBoolean("active", false),
                gatewayStatus = item.optString("gatewayStatus").ifBlank { item.optString("alias") },
            )
        }.filter { it.name.isNotBlank() }
    }

    /** GET /api/hermes/sessions — most recent conversations for a profile. */
    fun sessions(profile: String, limit: Int = 50): List<SessionSummary> {
        val path = "/api/hermes/sessions?profile=${enc(profile)}&limit=$limit"
        val array = call(path).optJSONArray("sessions") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = firstNonBlank(item, "id", "session_id", "sessionId") ?: return@mapNotNull null
            SessionSummary(
                id = id,
                title = firstNonBlank(item, "title", "name", "summary") ?: id.take(8),
                model = firstNonBlank(item, "model"),
                updatedAt = firstNonBlank(item, "updated_at", "updatedAt", "created_at", "createdAt"),
            )
        }
    }

    /**
     * POST /api/chat-run/runs — run one turn and wait for the final answer.
     *
     * This is the REST wrapper the server puts in front of its Socket.IO chat
     * channel, so a mobile client gets a complete reply without speaking the
     * streaming protocol.
     */
    fun sendMessage(profile: String, input: String, sessionId: String?): ChatReply {
        val body = JSONObject()
            .put("input", input)
            .put("profile", profile)
            .put("timeout_ms", 240_000)
        if (!sessionId.isNullOrBlank()) body.put("session_id", sessionId)

        val result = call("/api/chat-run/runs", "POST", body)
        val failure = result.optString("error").takeIf { it.isNotBlank() }
        val output = firstNonBlank(result, "output", "text", "message").orEmpty()
        return ChatReply(
            output = output,
            reasoning = firstNonBlank(result, "reasoning"),
            sessionId = firstNonBlank(result, "session_id", "sessionId") ?: sessionId,
            error = failure ?: if (output.isBlank()) "The run finished without any output" else null,
        )
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun firstNonBlank(source: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = source.optString(key)
            if (value.isNotBlank() && value != "null") return value
        }
        return null
    }
}

class HermesException(message: String) : Exception(message)

data class Profile(
    val name: String,
    val model: String?,
    val active: Boolean,
    val gatewayStatus: String?,
)

data class SessionSummary(
    val id: String,
    val title: String,
    val model: String?,
    val updatedAt: String?,
)

data class ChatReply(
    val output: String,
    val reasoning: String?,
    val sessionId: String?,
    val error: String?,
)
