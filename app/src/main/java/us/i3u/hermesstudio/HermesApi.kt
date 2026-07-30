package us.i3u.hermesstudio

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
            "PUT" -> builder.put((body ?: JSONObject()).toString().toRequestBody(json))
            "DELETE" -> builder.delete()
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
                avatar = AvatarSpec.from(item.optJSONObject("avatar")),
            )
        }.filter { it.name.isNotBlank() }
    }

    /** POST /api/hermes/sessions/{id}/rename */
    fun renameSession(sessionId: String, title: String) {
        call("/api/hermes/sessions/${enc(sessionId)}/rename", "POST", JSONObject().put("title", title))
    }

    /** DELETE /api/hermes/sessions/{id} */
    fun deleteSession(sessionId: String) {
        call("/api/hermes/sessions/${enc(sessionId)}", "DELETE")
    }

    /** POST /api/hermes/profiles */
    fun createProfile(name: String) {
        call("/api/hermes/profiles", "POST", JSONObject().put("name", name))
    }

    /** POST /api/hermes/profiles/{name}/rename */
    fun renameProfile(name: String, newName: String) {
        call("/api/hermes/profiles/${enc(name)}/rename", "POST", JSONObject().put("new_name", newName))
    }

    /** DELETE /api/hermes/profiles/{name} */
    fun deleteProfile(name: String) {
        call("/api/hermes/profiles/${enc(name)}", "DELETE")
    }

    /**
     * POST /api/hermes/group-chat/rooms — a room needs a name and an invite
     * code, and the agents it starts with are profiles.
     */
    fun createRoom(name: String, inviteCode: String, agents: List<String>): Room {
        val body = JSONObject()
            .put("name", name)
            .put("inviteCode", inviteCode)
            .put("agents", JSONArray().apply { agents.forEach { put(JSONObject().put("profile", it)) } })
        val result = call("/api/hermes/group-chat/rooms", "POST", body)
        val room = result.optJSONObject("room") ?: throw HermesException("The server returned no room")
        return Room(
            id = firstNonBlank(room, "id") ?: throw HermesException("The new room has no id"),
            name = firstNonBlank(room, "name") ?: name,
            agentCount = room.optInt("agentCount", agents.size),
            memberCount = room.optInt("memberCount", 1),
            updatedAt = firstNonBlank(room, "updatedAt", "updated_at"),
        )
    }

    /** DELETE /api/hermes/group-chat/rooms/{id} */
    fun deleteRoom(roomId: String) {
        call("/api/hermes/group-chat/rooms/${enc(roomId)}", "DELETE")
    }

    /** POST /api/hermes/group-chat/rooms/{id}/agents */
    fun addRoomAgent(roomId: String, profile: String) {
        call("/api/hermes/group-chat/rooms/${enc(roomId)}/agents", "POST", JSONObject().put("profile", profile))
    }

    /** GET /api/hermes/config — the pieces of it the app can act on. */
    fun serverConfig(profile: String): ServerConfig {
        val result = call("/api/hermes/config?profile=${enc(profile)}")
        val platforms = result.optJSONObject("platforms")
        val credentials = result.optJSONObject("platformCredentialStatus")
        val channels = buildList {
            val names = LinkedHashSet<String>()
            CHANNELS.forEach { names.add(it.platform) }
            platforms?.keys()?.forEach { names.add(it) }
            credentials?.keys()?.forEach { names.add(it) }
            names.forEach { platform ->
                val settings = platforms?.optJSONObject(platform)
                add(
                    ChannelStatus(
                        platform = platform,
                        // Hermes runs a channel unless it is explicitly turned off.
                        enabled = settings?.optBoolean("enabled", true) ?: true,
                        configured = credentials?.optBoolean(platform, false) ?: false,
                    ),
                )
            }
        }
        return ServerConfig(
            defaultModel = result.optJSONObject("model")?.let { firstNonBlank(it, "default") },
            // The server treats anything other than an explicit false as "yes".
            gatewayAutoStart = result.optJSONObject("gatewayAutoStart")?.optBoolean("enabled", true) ?: true,
            channels = channels,
        )
    }

    /**
     * PUT /api/hermes/config/credentials — writes a channel's secrets into the
     * profile's env file. The server restarts the gateway itself afterwards,
     * which is what actually puts the channel online.
     */
    fun updateChannelCredentials(profile: String, platform: String, values: Map<String, String>) {
        val payload = JSONObject()
        val extra = JSONObject()
        values.forEach { (path, value) ->
            if (path.startsWith("extra.")) extra.put(path.removePrefix("extra."), value)
            else payload.put(path, value)
        }
        if (extra.length() > 0) payload.put("extra", extra)
        val body = JSONObject().put("platform", platform).put("values", payload)
        call("/api/hermes/config/credentials?profile=${enc(profile)}", "PUT", body)
    }

    /** DELETE /api/hermes/config/credentials/{platform} */
    fun clearChannelCredentials(profile: String, platform: String) {
        call("/api/hermes/config/credentials/${enc(platform)}?profile=${enc(profile)}", "DELETE")
    }

    /** Turns a channel on or off without touching its credentials. */
    fun setChannelEnabled(profile: String, platform: String, enabled: Boolean) {
        updateConfigSection(profile, platform, JSONObject().put("enabled", enabled), restart = true)
    }

    /** PUT /api/hermes/config — one section at a time, as Studio does. */
    fun updateConfigSection(profile: String, section: String, values: JSONObject, restart: Boolean = false) {
        val body = JSONObject()
            .put("section", section)
            .put("values", values)
            .put("restart", restart)
        call("/api/hermes/config?profile=${enc(profile)}", "PUT", body)
    }

    /** GET /api/hermes/sessions — most recent conversations for a profile. */
    fun sessions(profile: String?, limit: Int = 80): List<SessionSummary> {
        val path = if (profile.isNullOrBlank()) {
            "/api/hermes/sessions?limit=$limit"
        } else {
            "/api/hermes/sessions?profile=${enc(profile)}&limit=$limit"
        }
        val array = call(path).optJSONArray("sessions") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = firstNonBlank(item, "id", "session_id", "sessionId") ?: return@mapNotNull null
            SessionSummary(
                id = id,
                title = firstNonBlank(item, "title", "name", "summary") ?: id.take(8),
                model = firstNonBlank(item, "model"),
                updatedAt = firstNonBlank(item, "updated_at", "updatedAt", "created_at", "createdAt"),
                profile = firstNonBlank(item, "profile"),
            )
        }
    }

    /** GET /api/hermes/available-models — flattened to what the picker needs. */
    fun availableModels(profile: String): List<ModelOption> {
        val result = call("/api/hermes/available-models?profile=${enc(profile)}")
        val options = LinkedHashMap<String, ModelOption>()

        fun collect(container: JSONObject) {
            val provider = firstNonBlank(container, "provider", "name", "label") ?: return
            val models = container.optJSONArray("models") ?: return
            for (index in 0 until models.length()) {
                val id = models.optString(index).takeIf { it.isNotBlank() }
                    ?: models.optJSONObject(index)?.let { firstNonBlank(it, "id", "name", "model") }
                    ?: continue
                if (id == "*") continue
                options.putIfAbsent(id, ModelOption(id = id, provider = provider))
            }
        }

        result.optJSONArray("groups")?.let { groups ->
            for (index in 0 until groups.length()) groups.optJSONObject(index)?.let(::collect)
        }
        if (options.isEmpty()) {
            result.optJSONArray("allProviders")?.let { providers ->
                for (index in 0 until providers.length()) providers.optJSONObject(index)?.let(::collect)
            }
        }
        return options.values.toList()
    }

    /** PUT /api/hermes/config/model — the profile's default model. */
    fun setDefaultModel(profile: String, model: String, provider: String?) {
        val body = JSONObject().put("default", model)
        if (!provider.isNullOrBlank()) body.put("provider", provider)
        call("/api/hermes/config/model?profile=${enc(profile)}", "PUT", body)
    }

    /** GET /api/hermes/config — the profile's current default model, if any. */
    fun defaultModel(profile: String): String? {
        val model = call("/api/hermes/config?profile=${enc(profile)}").optJSONObject("model")
        return model?.let { firstNonBlank(it, "default") }
    }

    /** POST /api/hermes/profiles/{name}/gateway/restart */
    fun restartGateway(profile: String) {
        call("/api/hermes/profiles/${enc(profile)}/gateway/restart", "POST", JSONObject())
    }

    /** POST /api/hermes/sessions/{id}/model */
    fun setSessionModel(sessionId: String, model: String, provider: String?) {
        val body = JSONObject().put("model", model)
        if (!provider.isNullOrBlank()) body.put("provider", provider)
        call("/api/hermes/sessions/${enc(sessionId)}/model", "POST", body)
    }

    /** GET /api/hermes/sessions/conversations/{id}/messages — existing history. */
    fun messages(sessionId: String, humanOnly: Boolean = true): List<Message> {
        val path = "/api/hermes/sessions/conversations/${enc(sessionId)}/messages?humanOnly=$humanOnly"
        val array = call(path).optJSONArray("messages") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val content = item.optString("content")
            if (content.isBlank()) return@mapNotNull null
            Message(
                id = item.optString("id"),
                role = item.optString("role").ifBlank { "assistant" },
                content = content,
                timestamp = firstNonBlank(item, "timestamp", "created_at", "createdAt"),
            )
        }
    }

    /** GET /api/hermes/group-chat/rooms */
    fun rooms(): List<Room> {
        val array = call("/api/hermes/group-chat/rooms").optJSONArray("rooms") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = firstNonBlank(item, "id", "roomId", "room_id") ?: return@mapNotNull null
            Room(
                id = id,
                name = firstNonBlank(item, "name", "title") ?: id.take(8),
                agentCount = item.optInt("agentCount", item.optJSONArray("agents")?.length() ?: 0),
                memberCount = item.optInt("memberCount", item.optJSONArray("members")?.length() ?: 0),
                updatedAt = firstNonBlank(item, "updatedAt", "updated_at", "lastMessageAt"),
            )
        }
    }

    /** GET /api/hermes/group-chat/rooms/{id} — room detail plus recent messages. */
    fun room(roomId: String, limit: Int = 80): RoomDetail {
        val result = call("/api/hermes/group-chat/rooms/${enc(roomId)}?limit=$limit&offset=0")
        val roomObject = result.optJSONObject("room")
        val name = roomObject?.let { firstNonBlank(it, "name", "title") } ?: roomId
        val agents = result.optJSONArray("agents") ?: JSONArray()
        val agentNames = (0 until agents.length()).mapNotNull { index ->
            agents.optJSONObject(index)?.let { firstNonBlank(it, "name", "profile", "agentId") }
        }
        val array = result.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val content = item.optString("content")
            if (content.isBlank()) return@mapNotNull null
            RoomMessage(
                id = item.optString("id"),
                sender = firstNonBlank(item, "senderName", "sender_name", "senderId") ?: "?",
                content = content,
                isAgent = item.optString("role") == "assistant",
                timestamp = firstNonBlank(item, "timestamp", "created_at", "createdAt"),
            )
        }
        return RoomDetail(id = roomId, name = name, agents = agentNames, messages = messages)
    }

    private fun multipart(path: String, field: String, bytes: ByteArray, filename: String, mime: String): JSONObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(field, filename, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val builder = Request.Builder().url(url(path)).post(body)
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        builder.header("Accept", "application/json")

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("error") }.getOrNull()
                throw HermesException(
                    if (detail.isNullOrBlank()) "HTTP ${response.code}" else "HTTP ${response.code}: $detail",
                )
            }
            return runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        }
    }

    /** POST /upload — stores the file under the profile upload dir and returns its path. */
    fun upload(profile: String, bytes: ByteArray, filename: String, mime: String): Upload {
        val result = multipart("/upload?profile=${enc(profile)}", "files", bytes, filename, mime)
        val files = result.optJSONArray("files") ?: JSONArray()
        val first = files.optJSONObject(0) ?: throw HermesException("Upload returned no file")
        return Upload(
            name = first.optString("name").ifBlank { filename },
            path = first.optString("path"),
            mime = mime,
        )
    }

    /**
     * POST /api/hermes/stt/transcribe — turns a recording into text with the
     * profile's configured provider, the same call the web composer makes.
     */
    fun transcribe(profile: String, bytes: ByteArray, filename: String, mime: String): String {
        val result = multipart("/api/hermes/stt/transcribe?profile=${enc(profile)}", "audio", bytes, filename, mime)
        return firstNonBlank(result, "text", "transcript", "output")
            ?: throw HermesException("The provider returned no text")
    }

    /**
     * POST /api/chat-run/runs — run one turn and wait for the final answer.
     *
     * This is the REST wrapper the server puts in front of its Socket.IO chat
     * channel, so a mobile client gets a complete reply without speaking the
     * streaming protocol.
     */
    fun sendMessage(
        profile: String,
        input: String,
        sessionId: String?,
        attachments: List<Upload> = emptyList(),
        reasoningEffort: String? = null,
    ): ChatReply {
        // Studio sends either a plain string or an array of content blocks; the
        // block form is what carries images and files.
        val payload: Any = if (attachments.isEmpty()) {
            input
        } else {
            JSONArray().apply {
                if (input.isNotBlank()) {
                    put(JSONObject().put("type", "text").put("text", input))
                }
                attachments.forEach { file ->
                    put(
                        JSONObject()
                            .put("type", if (file.mime.startsWith("image/")) "image" else "file")
                            .put("name", file.name)
                            .put("path", file.path)
                            .put("media_type", file.mime),
                    )
                }
            }
        }

        val body = JSONObject()
            .put("input", payload)
            .put("profile", profile)
            .put("timeout_ms", 240_000)
        if (!sessionId.isNullOrBlank()) body.put("session_id", sessionId)
        if (!reasoningEffort.isNullOrBlank()) body.put("reasoning_effort", reasoningEffort)

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

    /**
     * Fetches a static file the server publishes next to the web UI, such as
     * /logo.png. Returns null instead of throwing: branding is decoration, and a
     * server that does not serve it must not break a launch.
     */
    fun asset(path: String): ByteArray? = runCatching {
        val builder = Request.Builder().url(url(path)).get()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes()
        }
    }.getOrNull()

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
    val avatar: AvatarSpec? = null,
)

data class SessionSummary(
    val id: String,
    val title: String,
    val model: String?,
    val updatedAt: String?,
    val profile: String? = null,
)

data class ModelOption(
    val id: String,
    val provider: String,
)

data class Upload(
    val name: String,
    val path: String,
    val mime: String,
)

data class Message(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: String?,
) {
    val fromUser: Boolean get() = role == "user"
}

data class Room(
    val id: String,
    val name: String,
    val agentCount: Int,
    val memberCount: Int,
    val updatedAt: String?,
)

data class RoomMessage(
    val id: String,
    val sender: String,
    val content: String,
    val isAgent: Boolean,
    val timestamp: String?,
)

data class RoomDetail(
    val id: String,
    val name: String,
    val agents: List<String>,
    val messages: List<RoomMessage>,
)

data class ChannelStatus(
    val platform: String,
    val enabled: Boolean,
    val configured: Boolean,
)

data class ServerConfig(
    val defaultModel: String?,
    val gatewayAutoStart: Boolean,
    val channels: List<ChannelStatus>,
)

data class ChatReply(
    val output: String,
    val reasoning: String?,
    val sessionId: String?,
    val error: String?,
)
