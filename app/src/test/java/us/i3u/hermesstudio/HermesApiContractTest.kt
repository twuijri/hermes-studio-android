package us.i3u.hermesstudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fixtures from the current Hermes Studio HTTP contracts.
 *
 * These tests deliberately exercise the real request/response boundary so a
 * server-side field rename cannot silently turn into an empty label or a
 * request that looks successful in the UI but is rejected by Studio.
 */
class HermesApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HermesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HermesApi(server.url("/").toString().trimEnd('/'), "saved-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `auth me reads the nested current user and sends the token`() {
        enqueue("""{"user":{"id":17,"username":"owner","role":"super_admin"}}""")

        assertEquals("owner", api.verifyToken())
        val request = server.takeRequest()
        assertEquals("/api/auth/me", request.path)
        assertEquals("Bearer saved-token", request.getHeader("Authorization"))
    }

    @Test
    fun `session summaries use Studio recency and provider fields`() {
        enqueue(
            """
            {
              "sessions": [{
                "id": "session-1",
                "title": "Contract check",
                "profile": "manager",
                "provider": "openrouter",
                "model": "anthropic/claude-sonnet-4",
                "started_at": 1710000000,
                "last_active": 1710001234
              }]
            }
            """.trimIndent(),
        )

        val session = api.sessions("manager").single()

        assertEquals("1710001234", session.updatedAt)
        assertEquals("openrouter", session.provider)
        assertEquals("/api/hermes/sessions?profile=manager&limit=80", server.takeRequest().path)
    }

    @Test
    fun `room list does not invent zero counts absent from Studio`() {
        enqueue("""{"rooms":[{"id":"room-1","name":"Planning","inviteCode":"ABC234"}]}""")

        val room = api.rooms().single()

        assertNull(room.agentCount)
        assertNull(room.memberCount)
    }

    @Test
    fun `transcription discovers and posts the active provider`() {
        enqueue("""{"profile":"manager","configured":true,"activeProvider":"openai","reason":null}""")
        enqueue("""{"text":"hello from audio","provider":"openai","model":"whisper-1"}""")

        val text = api.transcribe("manager", byteArrayOf(1, 2, 3), "voice.m4a", "audio/mp4")

        assertEquals("hello from audio", text)
        assertEquals("/api/hermes/stt/profile-status?profile=manager", server.takeRequest().path)
        val upload = server.takeRequest()
        assertEquals("/api/hermes/stt/transcribe?profile=manager", upload.path)
        val multipart = upload.body.readUtf8()
        assertTrue(multipart.contains("name=\"provider\""))
        assertTrue(multipart.contains("\r\n\r\nopenai\r\n"))
        assertTrue(multipart.contains("name=\"audio\"; filename=\"voice.m4a\""))
    }

    @Test
    fun `transcription remains compatible with servers before profile status`() {
        enqueue("""{"error":"Not found"}""", code = 404)
        enqueue("""{"text":"legacy transcript"}""")

        assertEquals(
            "legacy transcript",
            api.transcribe("default", byteArrayOf(7), "voice.m4a", "audio/mp4"),
        )

        server.takeRequest()
        val multipart = server.takeRequest().body.readUtf8()
        assertFalse(multipart.contains("name=\"provider\""))
    }

    @Test
    fun `first run model and provider reach the REST fallback`() {
        enqueue("""{"ok":true,"output":"done","session_id":"session-1"}""")

        api.sendMessage(
            profile = "manager",
            input = "hello",
            sessionId = "session-1",
            reasoningEffort = "high",
            model = "anthropic/claude-sonnet-4",
            provider = "openrouter",
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("anthropic/claude-sonnet-4", body.getString("model"))
        assertEquals("openrouter", body.getString("provider"))
        assertEquals("high", body.getString("reasoning_effort"))
    }

    @Test
    fun `agent settings added in v011 follow the profile contract`() {
        enqueue(
            """
            {
              "agent": {
                "max_turns": 24,
                "gateway_timeout": 0,
                "restart_drain_timeout": 45,
                "tool_use_enforcement": "always"
              }
            }
            """.trimIndent(),
        )

        val settings = api.agentSettings("manager")

        assertEquals(24, settings.maxTurns)
        assertEquals(0, settings.gatewayTimeout)
        assertEquals(45, settings.restartDrainTimeout)
        assertEquals("always", settings.toolEnforcement)
        assertEquals(
            "/api/hermes/config?profile=manager&section=agent",
            server.takeRequest().path,
        )
    }

    @Test
    fun `gateway auto start policy preserves include profiles and all mode`() {
        enqueue("""{"gatewayAutoStart":{"enabled":true,"include":["manager"],"exclude":["sandbox"]}}""")

        val policy = api.autoStartPolicy()

        assertEquals(listOf("manager"), policy.include)
        assertEquals(listOf("sandbox"), policy.exclude)
        enqueue("""{"success":true}""")
        api.setAutoStartPolicy(policy.copy(include = null))

        val request = server.takeRequest()
        assertEquals("/api/hermes/config?section=gatewayAutoStart", request.path)
        val update = server.takeRequest()
        assertEquals("/api/hermes/config", update.path)
        val values = JSONObject(update.body.readUtf8()).getJSONObject("values")
        assertTrue(values.isNull("include"))
        assertEquals(true, values.getBoolean("enabled"))
    }

    @Test
    fun `HTTP status remains available to session recovery`() {
        enqueue("""{"error":"Unauthorized"}""", code = 401)

        val failure = assertThrows(HermesException::class.java) { api.verifyToken() }

        assertEquals(401, failure.statusCode)
        assertTrue(failure.message.orEmpty().contains("Unauthorized"))
    }

    @Test
    fun `only authentication failures invalidate the saved session`() {
        assertTrue(HermesException("Unauthorized", 401).invalidatesSavedSession())
        assertTrue(HermesException("Forbidden", 403).invalidatesSavedSession())
        assertFalse(HermesException("Server error", 500).invalidatesSavedSession())
        assertFalse(java.net.SocketTimeoutException().invalidatesSavedSession())
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }
}
