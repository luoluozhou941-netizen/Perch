package com.example.ap.chat

/** 一个附件（文件）。地址用字符串存；大小未知时 sizeBytes 为 -1。 */
data class Attachment(
    val uri: String,
    val name: String,
    val sizeBytes: Long = -1L,
    val mime: String? = null,
)

/** 文件类型：只决定显示哪个图标，颜色统一。 */
enum class FileKind { Text, Data, Code, Pdf, Sheet, Archive, Other }

fun fileKind(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
    "txt", "md", "log", "rtf", "doc", "docx" -> FileKind.Text
    "json", "csv", "tsv", "xml", "yaml", "yml" -> FileKind.Data
    "html", "htm", "js", "ts", "jsx", "tsx", "css", "kt", "java", "py", "sh", "c", "cpp", "go", "rs" -> FileKind.Code
    "pdf" -> FileKind.Pdf
    "xls", "xlsx", "ods" -> FileKind.Sheet
    "zip", "rar", "7z", "tar", "gz" -> FileKind.Archive
    else -> FileKind.Other
}

/** 把文件名拆成"名字"和".后缀"，名字太长时只省略名字、保留后缀。 */
fun splitName(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    return if (dot <= 0 || dot == name.length - 1) name to "" else name.substring(0, dot) to name.substring(dot)
}

/** 365 KB、2.3 MB、18 MB 这样的写法。 */
fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "${(bytes + 512) / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> {
        val mb = bytes / (1024.0 * 1024.0)
        if (mb < 10) String.format(java.util.Locale.US, "%.1f MB", mb) else "${Math.round(mb)} MB"
    }
    else -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

/** 图片地址用字符串存，这样这一层不依赖安卓，以后别的平台也能复用。一条消息可带多张图。 */
data class ChatMessage(
    val id: Long,
    val text: String,
    val fromMe: Boolean,
    val imageUris: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
)

/** 对方回复中时排队等待发送的一条消息。 */
data class QueuedMessage(
    val text: String,
    val imageUris: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
)

/** 输入栏右侧按钮的四种状态。 */
enum class SendButtonMode {
    Mic,        // 空着，对方空闲：麦克风
    Send,       // 有内容，对方空闲：向上箭头
    QueueClock, // 有内容，对方回复中：小时钟（排队发送）
    Waiting,    // 空着，对方回复中：浅色空圆，点不了
}

/** hasContent：输入框里有字，或者已经选了图片、文件。 */
fun sendButtonMode(assistantBusy: Boolean, hasContent: Boolean): SendButtonMode = when {
    assistantBusy && hasContent -> SendButtonMode.QueueClock
    assistantBusy -> SendButtonMode.Waiting
    hasContent -> SendButtonMode.Send
    else -> SendButtonMode.Mic
}
