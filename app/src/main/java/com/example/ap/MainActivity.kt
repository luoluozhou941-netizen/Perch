package com.example.ap

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ap.chat.Attachment
import com.example.ap.chat.ChatScreen
import com.example.ap.chat.FilePreview
import com.example.ap.chat.ImageViewer
import com.example.ap.chat.InputBar
import com.example.ap.chat.MAX_FILES
import com.example.ap.chat.MAX_IMAGES
import com.example.ap.chat.rememberChatDemoState
import com.example.ap.chat.rememberVoiceInput
import com.example.ap.theme.ChatTheme
import com.example.ap.theme.LocalChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val theme = remember(dark) { ChatTheme.load(context, dark) }

            CompositionLocalProvider(LocalChatTheme provides theme) {
                val demo = rememberChatDemoState()
                // 正在预览的文件（点聊天里的文件标签打开）
                var previewFile by remember { mutableStateOf<Attachment?>(null) }
                // 语音转文字：识别出来的字实时写进输入框
                val voice = rememberVoiceInput(
                    onText = { t -> runOnUiThread { demo.input = t } },
                    onMessage = { m -> runOnUiThread { Toast.makeText(context, m, Toast.LENGTH_SHORT).show() } },
                )
                val askMic = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        voice.start()
                    } else {
                        Toast.makeText(context, "需要允许麦克风才能语音输入，可以在系统设置里打开", Toast.LENGTH_LONG).show()
                    }
                }
                // 系统相册：剩 1 个名额时用单选（多选至少要 2），否则多选；选多了只取名额内的
                val pickOne = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    if (uri != null) demo.attachImages(listOf(uri.toString()))
                }
                val pickMany = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
                ) { uris ->
                    if (uris.isNotEmpty()) demo.attachImages(uris.map { it.toString() })
                }
                // 系统选文件页：可多选，任何类型；不需要额外权限
                val pickFiles = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        // 按文件类型分：图片进图片预览区，其余进文件区（不看是从哪个入口选的）
                        val (imgs, others) = uris
                            .map { it to readAttachment(context, it) }
                            .partition { isImageAttachment(it.second) }
                        if (imgs.isNotEmpty()) {
                            if (imgs.size > demo.remainingImageSlots) {
                                Toast.makeText(context, "最多选 $MAX_IMAGES 张图片", Toast.LENGTH_SHORT).show()
                            }
                            demo.attachImages(imgs.map { it.first.toString() })
                        }
                        if (others.isNotEmpty()) {
                            val left = demo.remainingFileSlots
                            if (others.size > left) {
                                Toast.makeText(context, "最多选 $MAX_FILES 个文件，已选前 $left 个", Toast.LENGTH_SHORT).show()
                            }
                            demo.attachFiles(others.map { it.second })
                        }
                    }
                }
                ChatScreen(
                    assistantName = "小陪",
                    statusText = "在线，随时听你说",
                    avatarChar = "陪",
                    messages = demo.messages,
                    showTyping = demo.showTyping,
                    onImageClick = demo::openImages,
                    onFileClick = { file -> previewFile = file },
                    inputBar = {
                        InputBar(
                            text = demo.input,
                            onTextChange = { demo.input = it },
                            expanded = demo.expanded,
                            onToggleExpand = demo::toggleExpand,
                            assistantBusy = demo.assistantBusy,
                            queue = demo.queue,
                            pendingImageUris = demo.pendingImages,
                            onRemovePendingImage = demo::removePendingImage,
                            onPendingImageClick = demo::openPendingImages,
                            pendingFiles = demo.pendingFiles,
                            onRemovePendingFile = demo::removePendingFile,
                            onSubmit = demo::submit,
                            onMicClick = {
                                when {
                                    voice.listening -> voice.stop()
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED -> voice.start()
                                    else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            listening = voice.listening,
                            onImageClick = {
                                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                when {
                                    demo.remainingImageSlots <= 0 ->
                                        Toast.makeText(context, "最多选 $MAX_IMAGES 张图片", Toast.LENGTH_SHORT).show()
                                    demo.remainingImageSlots == 1 -> pickOne.launch(request)
                                    else -> pickMany.launch(request)
                                }
                            },
                            onAttachClick = {
                                if (demo.remainingFileSlots <= 0) {
                                    Toast.makeText(context, "最多选 $MAX_FILES 个文件", Toast.LENGTH_SHORT).show()
                                } else {
                                    pickFiles.launch(arrayOf("*/*"))
                                }
                            },
                            onModeClick = { },   // "模式"还没想好，先占位
                            onQueueEdit = demo::editQueued,
                            onQueueSendNow = demo::sendQueuedNow,
                            onQueueDelete = demo::deleteQueued,
                        )
                    },
                )
                demo.viewerUris()?.let { uris ->
                    ImageViewer(
                        uris = uris,
                        startIndex = demo.viewingIndex,
                        onDismiss = demo::closeImage,
                        // 只有从预览区（待发送的图）进来才能删
                        onDelete = if (demo.viewingPending) demo::removePendingImage else null,
                    )
                }
                previewFile?.let { file ->
                    FilePreview(
                        file = file,
                        onDismiss = { previewFile = null },
                        onOpenExternal = { openFile(context, file) },
                    )
                }
            }
        }
    }
}

/** 从系统返回的地址里读出文件名、大小、类型。读不到的就用默认值，不报错。 */
private fun readAttachment(context: Context, uri: Uri): Attachment {
    var name = uri.lastPathSegment ?: "文件"
    var size = -1L
    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sz = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n)
                if (sz >= 0 && !cursor.isNull(sz)) size = cursor.getLong(sz)
            }
        }
    } catch (e: Exception) {
        // 读不到就保持默认
    }
    return Attachment(uri.toString(), name, size, context.contentResolver.getType(uri))
}

/** 是不是图片：看 mime 或扩展名。svg 暂不算（Coil 还没装 SVG 解码），仍按文件显示。 */
private fun isImageAttachment(att: Attachment): Boolean {
    val ext = att.name.substringAfterLast('.', "").lowercase()
    if (ext == "svg" || att.mime == "image/svg+xml") return false
    if (att.mime?.startsWith("image/") == true) return true
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif")
}

/** 点聊天里的文件：调系统"用其他应用打开"；没有能打开的应用就提示一句。 */
private fun openFile(context: Context, file: Attachment) {
    if (file.uri.isBlank()) {
        Toast.makeText(context, "这是演示用的文件，打不开", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(file.uri), file.mime ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "没有能打开这个文件的应用", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "这个文件现在打不开了", Toast.LENGTH_SHORT).show()
    }
}
