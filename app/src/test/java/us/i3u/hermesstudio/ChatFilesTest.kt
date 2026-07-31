package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFilesTest {

    @Test
    fun `assistant local markdown files become native download cards`() {
        val parsed = parseChatMessage(
            """
            اكتمل تجهيز العرض.

            ### الملفات

            [تحميل عرض PowerPoint](</home/agent/.hermes/profiles/mohamed/workspace/Manpower_Cascading_RTL_MOD.pptx>)

            [للمعاينة تحميل نسخة PDF](</home/agent/.hermes/profiles/mohamed/workspace/Manpower_Cascading_RTL_MOD.pdf>)
            """.trimIndent(),
        )

        assertEquals(2, parsed.files.size)
        assertEquals("تحميل عرض PowerPoint", parsed.files[0].label)
        assertEquals("Manpower_Cascading_RTL_MOD.pptx", parsed.files[0].fileName)
        assertEquals("Manpower_Cascading_RTL_MOD.pdf", parsed.files[1].fileName)
        assertEquals("اكتمل تجهيز العرض.", parsed.text)
        assertFalse(parsed.text.contains("/home/agent"))
    }

    @Test
    fun `web links and markdown images remain regular message content`() {
        val content = "[الموقع](https://example.com) ![صورة](/home/agent/image.png)"
        val parsed = parseChatMessage(content)

        assertTrue(parsed.files.isEmpty())
        assertEquals(content, parsed.text)
    }

    @Test
    fun `existing download URLs are unwrapped and decoded once`() {
        val parsed = parseChatMessage(
            "[تنزيل](/api/hermes/download?path=%2Fhome%2Fagent%2FMy%2520Report.pdf&name=ignored)",
        )

        assertEquals("/home/agent/My%20Report.pdf", parsed.files.single().path)
        assertEquals("My Report.pdf", parsed.files.single().fileName)
    }

    @Test
    fun `visible filename with extension wins over server path`() {
        assertEquals(
            "final-ar.pptx",
            inferDownloadFileName("/workspace/generated-output.bin", "final-ar.pptx"),
        )
    }
}
