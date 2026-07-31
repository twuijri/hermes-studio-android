package us.i3u.hermesstudio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatSocketEventTest {

    @Test
    fun `studio tool start keeps its id name and useful argument`() {
        val event = parseToolEvent(
            event = JSONObject()
                .put("tool_call_id", "call-7")
                .put("tool_name", "vision_analyze")
                .put("args", JSONObject().put("path", "/workspace/slide-4.jpg")),
            status = ToolRunStatus.Running,
            occurredAtMillis = 1_000L,
        )

        assertNotNull(event)
        assertEquals("call-7", event?.id)
        assertEquals("vision_analyze", event?.name)
        assertEquals("/workspace/slide-4.jpg", event?.detail)
        assertEquals(ToolRunStatus.Running, event?.status)
    }

    @Test
    fun `current socket aliases and duration are accepted on completion`() {
        val event = parseToolEvent(
            event = JSONObject()
                .put("call_id", "call-8")
                .put("tool", "terminal")
                .put("arguments", JSONObject().put("command", "gradle assembleDebug"))
                .put("duration_seconds", 2.9),
            status = ToolRunStatus.Done,
            occurredAtMillis = 4_000L,
        )

        assertEquals("terminal", event?.name)
        assertEquals("gradle assembleDebug", event?.detail)
        assertEquals(2.9, event?.durationSeconds ?: 0.0, 0.001)
        assertEquals(ToolRunStatus.Done, event?.status)
    }

    @Test
    fun `payload without a tool identity is ignored`() {
        assertNull(
            parseToolEvent(
                event = JSONObject().put("args", JSONObject().put("path", "/tmp/file")),
                status = ToolRunStatus.Running,
                occurredAtMillis = 1_000L,
            ),
        )
    }

    @Test
    fun `completed event carrying an error is shown as failed`() {
        val event = parseToolEvent(
            event = JSONObject()
                .put("tool_call_id", "call-9")
                .put("name", "terminal")
                .put("error", "permission denied"),
            status = ToolRunStatus.Done,
            occurredAtMillis = 2_000L,
        )

        assertEquals(ToolRunStatus.Error, event?.status)
    }
}
