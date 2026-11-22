package com.example.ap.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 纯演示：让你在手机上能看到设计里的各种状态。
 * 不联网、不存储，回复都是写死的几句话。
 * 以后接真功能时，整个文件可以删掉，换成真正的状态管理。
 */
class ChatDemoState(private val scope: CoroutineScope) {
    val messages = mutableStateListOf(
        ChatMessage(1, "今天过得怎么样?", fromMe = false),
        ChatMessage(2, "有点累，但还好。", fromMe = true),
        ChatMessage(3, "辛苦了。想聊聊是什么让你累吗，还是先安静地待一会儿?", fromMe = false),
        ChatMessage(4, "想先说说工作的事。", fromMe = true),
        // 下面两条只是演示附件的显示（地址是空的，点了打不开）
        ChatMessage(
            5, "我把文档发你", fromMe = false,
            attachments = listOf(Attachment("", "克拉拉与太阳.txt", 365L * 1024, "text/plain")),
        ),
        ChatMessage(
            6, "这几个文件你看看", fromMe = true,
            attachments = listOf(
                Attachment("", "2026年第四季度项目汇总报告终版.pdf", 2_400_000L, "application/pdf"),
                Attachment("", "koko-主题.json", 96L * 1024, "application/json"),
                Attachment("", "预算表.xlsx", 412L * 1024, null),
                Attachment("", "素材包.zip", 18L * 1024 * 1024, "application/zip"),
            ),
        ),
    )
    var showTyping by mutableStateOf(true)   // 设计图里一开始就显示"正在输入"
        private set
    var assistantBusy by mutableStateOf(false)
        private set
    var input by mutableStateOf("")
    var expanded by mutableStateOf(false)
    val queue = mutableStateListOf<QueuedMessage>()
    /** 待发送的图片（按选的顺序）。 */
    val pendingImages = mutableStateListOf<String>()
    /** 待发送的文件（按选的顺序）。 */
    val pendingFiles = mutableStateListOf<Attachment>()

    // 大图查看：看的是哪一组图、从第几张开始；viewingPending 为 true 时看的是待发送的图（可删）
    private var viewingUris by mutableStateOf<List<String>?>(null)
    var viewingIndex by mutableStateOf(0)
        private set
    var viewingPending by mutableStateOf(false)
        private set

    private val replies = listOf(
        "好，你慢慢说，我在听。是项目的事，还是人的事?",
        "嗯，听起来这件事让你有点为难。",
        "谢谢你愿意说。你想先从哪一步开始?",
    )
    private var nextId = 100L
    private var replyIndex = 0
    private var job: Job? = null

    fun toggleExpand() {
        expanded = !expanded
    }

    /** 还能再选几张。 */
    val remainingImageSlots: Int get() = MAX_IMAGES - pendingImages.size

    fun attachImages(uris: List<String>) {
        val fresh = uris.filter { it !in pendingImages }.take(remainingImageSlots.coerceAtLeast(0))
        pendingImages.addAll(fresh)
        expanded = false   // 选完回到聊天页，功能区收起，只剩预览区
    }

    /** 还能再选几个文件。 */
    val remainingFileSlots: Int get() = MAX_FILES - pendingFiles.size

    fun attachFiles(files: List<Attachment>) {
        val fresh = files.filter { f -> pendingFiles.none { it.uri == f.uri } }
            .take(remainingFileSlots.coerceAtLeast(0))
        pendingFiles.addAll(fresh)
        expanded = false   // 选完回到聊天页，功能区收起，只剩预览区
    }

    fun removePendingFile(index: Int) {
        if (index in pendingFiles.indices) pendingFiles.removeAt(index)
    }

    /** 点聊天里的图：看这一组，从第 index 张开始。 */
    fun openImages(uris: List<String>, index: Int) {
        viewingUris = uris
        viewingIndex = index
        viewingPending = false
    }

    /** 点预览区的缩略图：看待发送的图，可以在大图里删。 */
    fun openPendingImages(index: Int) {
        viewingUris = null
        viewingIndex = index
        viewingPending = true
    }

    /** 当前要显示的大图列表；没有在看图时返回 null。 */
    fun viewerUris(): List<String>? =
        if (viewingPending) pendingImages.toList().ifEmpty { null } else viewingUris

    fun closeImage() {
        viewingUris = null
        viewingPending = false
    }

    fun removePendingImage(index: Int) {
        if (index in pendingImages.indices) pendingImages.removeAt(index)
        if (viewingPending && pendingImages.isEmpty()) closeImage()
    }

    fun submit() {
        val t = input.trim()
        val images = pendingImages.toList()
        val files = pendingFiles.toList()
        if (t.isEmpty() && images.isEmpty() && files.isEmpty()) return
        expanded = false
        input = ""
        pendingImages.clear()
        pendingFiles.clear()
        if (assistantBusy) queue.add(QueuedMessage(t, images, files)) else send(t, images, files)
    }

    fun editQueued(i: Int) {
        if (i !in queue.indices) return
        val item = queue.removeAt(i)
        input = item.text
        pendingImages.clear()
        pendingImages.addAll(item.imageUris)
        pendingFiles.clear()
        pendingFiles.addAll(item.attachments)
    }

    fun sendQueuedNow(i: Int) {
        if (i !in queue.indices) return
        val item = queue.removeAt(i)
        send(item.text, item.imageUris, item.attachments)
    }

    fun deleteQueued(i: Int) {
        if (i in queue.indices) queue.removeAt(i)
    }

    private fun send(
        text: String,
        imageUris: List<String> = emptyList(),
        attachments: List<Attachment> = emptyList(),
    ) {
        messages.add(ChatMessage(nextId++, text, fromMe = true, imageUris = imageUris, attachments = attachments))
        startReply()
    }

    private fun startReply() {
        job?.cancel()
        assistantBusy = true
        showTyping = true
        job = scope.launch {
            delay(1400)
            messages.add(ChatMessage(nextId++, replies[replyIndex++ % replies.size], fromMe = false))
            showTyping = false
            if (queue.isNotEmpty()) {
                delay(900)
                if (queue.isNotEmpty()) {
                    val next = queue.removeAt(0)
                    send(next.text, next.imageUris, next.attachments)
                    return@launch
                }
            }
            assistantBusy = false
        }
    }
}

/** 一次最多能选的图片张数（数值在 chat_theme.json 的 maxImages，这里只是演示层的副本）。 */
const val MAX_IMAGES = 30

/** 一次最多能选的文件个数（数值在 chat_theme.json 的 maxFiles，这里只是演示层的副本）。 */
const val MAX_FILES = 10

@Composable
fun rememberChatDemoState(): ChatDemoState {
    val scope = rememberCoroutineScope()
    return remember { ChatDemoState(scope) }
}
