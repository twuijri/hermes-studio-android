package us.i3u.hermesstudio

import java.net.URLDecoder

/** A local Studio file linked from an assistant Markdown message. */
data class ChatFileLink(
    val label: String,
    val path: String,
    val fileName: String,
)

data class ParsedChatMessage(
    val text: String,
    val files: List<ChatFileLink>,
)

private val MARKDOWN_LINK = Regex(
    """(?<!!)\[([^\]\r\n]+)]\(\s*(?:<([^>\r\n]+)>|([^\s)\r\n]+))\s*\)""",
)
private val FILES_HEADING = Regex(
    """(?im)^\s{0,3}#{1,6}\s+(?:files|attachments|downloads|الملفات|المرفقات)\s*$""",
)
private val CONVENTIONAL_EXTENSION = Regex("""\.[A-Za-z0-9]{1,12}$""")

/**
 * Pulls absolute local-file links out of Markdown so the native UI can render
 * download cards. Ordinary web links and Markdown images stay in the message.
 */
fun parseChatMessage(content: String): ParsedChatMessage {
    val accepted = MARKDOWN_LINK.findAll(content).mapNotNull { match ->
        val rawTarget = match.groups[2]?.value ?: match.groups[3]?.value ?: return@mapNotNull null
        val path = unwrapStudioDownloadPath(rawTarget.trim())
        if (!isStudioLocalFile(path)) return@mapNotNull null
        val label = decodeUrlPart(match.groupValues[1]).trim().ifBlank { inferDownloadFileName(path) }
        match to ChatFileLink(
            label = label,
            path = path,
            fileName = inferDownloadFileName(path, label),
        )
    }.toList()

    if (accepted.isEmpty()) return ParsedChatMessage(content, emptyList())

    val body = buildString {
        var cursor = 0
        accepted.forEach { (match, _) ->
            append(content, cursor, match.range.first)
            cursor = match.range.last + 1
        }
        append(content, cursor, content.length)
    }
        .replace(FILES_HEADING, "")
        .replace(Regex("""\n[ \t]*\n(?:[ \t]*\n)+"""), "\n\n")
        .trim()

    return ParsedChatMessage(body, accepted.map { it.second })
}

fun inferDownloadFileName(path: String, label: String? = null): String {
    val cleanLabel = decodeUrlPart(label.orEmpty()).trim()
    val decodedPath = decodeUrlPart(unwrapStudioDownloadPath(path))
        .substringBefore('?')
        .substringBefore('#')
    val basename = decodedPath.split('/', '\\').lastOrNull().orEmpty().trim()
    val preferred = when {
        CONVENTIONAL_EXTENSION.containsMatchIn(cleanLabel) -> cleanLabel
        CONVENTIONAL_EXTENSION.containsMatchIn(basename) -> basename
        cleanLabel.isNotBlank() -> cleanLabel
        basename.isNotBlank() -> basename
        else -> "download"
    }
    return preferred
        .split('/', '\\')
        .last()
        .replace(Regex("""[\u0000-\u001f<>:\"/\\|?*]"""), "_")
        .trim()
        .ifBlank { "download" }
}

fun unwrapStudioDownloadPath(value: String): String {
    if (!value.startsWith("/api/hermes/download?")) return decodeUrlPart(value)
    val encoded = value.substringAfter('?').split('&')
        .firstOrNull { it.substringBefore('=') == "path" }
        ?.substringAfter('=', "")
        .orEmpty()
    return if (encoded.isBlank()) decodeUrlPart(value) else decodeUrlPart(encoded)
}

private fun isStudioLocalFile(path: String): Boolean =
    path.startsWith('/') || Regex("""^[A-Za-z]:[\\/]""").containsMatchIn(path)

private fun decodeUrlPart(value: String): String = runCatching {
    URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
}.getOrDefault(value)
