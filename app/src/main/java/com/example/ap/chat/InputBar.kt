package com.example.ap.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import com.example.ap.theme.LocalChatTheme
import com.example.ap.ui.AppIcon
import com.example.ap.ui.AppIcons

/**
 * 输入栏：收起/展开、右侧按钮四种状态、排队面板。
 * 只管"长什么样"，真正的发送、语音、选图都由外面传进来的函数决定。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    assistantBusy: Boolean,
    queue: List<QueuedMessage>,
    pendingImageUris: List<String>,
    onRemovePendingImage: (Int) -> Unit,
    onPendingImageClick: (Int) -> Unit,
    pendingFiles: List<Attachment>,
    onRemovePendingFile: (Int) -> Unit,
    onSubmit: () -> Unit,
    onMicClick: () -> Unit,
    listening: Boolean = false,
    onImageClick: () -> Unit,
    onAttachClick: () -> Unit,
    onModeClick: () -> Unit,
    onQueueEdit: (Int) -> Unit,
    onQueueSendNow: (Int) -> Unit,
    onQueueDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalChatTheme.current
    val c = t.colors
    val mode = if (listening) {
        SendButtonMode.Listening
    } else {
        sendButtonMode(
            assistantBusy,
            text.isNotBlank() || pendingImageUris.isNotEmpty() || pendingFiles.isNotEmpty(),
        )
    }
    // 输入框是否拉高成"大输入框"。只属于界面，不放进业务状态；发送后自动恢复。
    var fullInput by rememberSaveable { mutableStateOf(false) }
    val dockShape = RoundedCornerShape(t.dp("dockRadius"))
    val submit = {
        fullInput = false
        onSubmit()
    }

    // 整个输入区是一张圆角卡片：排队面板、预览、功能胶囊、输入行都在里面
    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = t.dp("inputPadH"),
                end = t.dp("inputPadH"),
                bottom = t.dp("inputPadBottom"),
            )
            .shadow(t.dp("dockElevation"), dockShape)
            .background(c.dock, dockShape)
            .border(t.dp("bubbleBorder"), c.line, dockShape)
            .padding(t.dp("dockPad")),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(t.dp("inputBlockGap"))) {
            if (expanded) {
                Row(verticalAlignment = Alignment.Top) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(t.dp("chipGap")),
                        verticalArrangement = Arrangement.spacedBy(t.dp("chipGap")),
                    ) {
                        ActionChip(AppIcons.IMAGE, "图片", onImageClick)
                        ActionChip(AppIcons.CLIP, "附件", onAttachClick)
                        ActionChip(AppIcons.SWAP, "模式", onModeClick)
                    }
                    FullInputToggle(
                        fullInput,
                        Modifier.padding(top = (t.dp("chipHeight") - t.dp("toggleSize")) / 2),
                    ) { fullInput = !fullInput }
                }
            }

            if (queue.isNotEmpty()) {
                QueuePanel(queue, onQueueEdit, onQueueSendNow, onQueueDelete)
            }

            // 预览区和功能区绝不同时出现：展开功能区时，预览区先藏起来（图片还留着）
            if (!expanded && (pendingImageUris.isNotEmpty() || pendingFiles.isNotEmpty())) {
                PreviewArea(
                    pendingImageUris, pendingFiles,
                    onPendingImageClick, onRemovePendingImage, onRemovePendingFile,
                )
            }

            // 输入框会长高，所以两侧按钮贴着底部
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(t.dp("inputRowGap")),
            ) {
                ExpandButton(expanded, onToggleExpand)
                InputField(text, onTextChange, fullInput, listening, Modifier.weight(1f))
                SendButton(mode) {
                    if (mode == SendButtonMode.Mic || mode == SendButtonMode.Listening) onMicClick() else submit()
                }
            }
        }
    }
}

@Composable
private fun ActionChip(iconPath: String, label: String, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = RoundedCornerShape(t.dp("chipHeight") / 2)
    Row(
        Modifier
            .height(t.dp("chipHeight"))
            .clip(shape)
            .background(c.bubbleAi)
            .border(t.dp("bubbleBorder"), c.bubbleAiLine, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = t.dp("chipPadH")),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.dp("chipIconGap")),
    ) {
        AppIcon(iconPath, c.accent, t.dp("chipIcon"), strokeWidth = t.f("iconStroke"))
        Text(label, color = c.text, fontSize = t.sp("chipFont"))
    }
}

@Composable
private fun ExpandButton(expanded: Boolean, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    Box(
        Modifier
            .size(t.dp("controlSize"))
            .clip(CircleShape)
            .background(c.dockBtn)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (expanded) "收起" else "展开" },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            if (expanded) AppIcons.X else AppIcons.PLUS,
            c.text,
            t.dp("controlIcon"),
            strokeWidth = t.f("iconStroke"),
        )
    }
}

/** 输入框：默认最多 4 行，大输入模式下最少 110dp、最多 10 行。回车是换行，发送靠右边的按钮。 */
@Composable
private fun InputField(
    text: String,
    onTextChange: (String) -> Unit,
    fullInput: Boolean,
    listening: Boolean,
    modifier: Modifier,
) {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = RoundedCornerShape(t.dp("fieldRadius"))
    val minHeight = if (fullInput) t.dp("fullInputMinHeight") else t.dp("controlSize")
    val maxLines = (if (fullInput) t.f("fullInputMaxLines") else t.f("inputMaxLines")).toInt()
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        readOnly = listening,   // 正在听的时候文字由语音识别写入，不让手动改
        modifier = modifier,
        textStyle = TextStyle(color = c.text, fontSize = t.sp("fieldFont")),
        cursorBrush = SolidColor(c.accent),
        maxLines = maxLines,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(c.input, shape)
                    .border(t.dp("bubbleBorder"), c.bubbleAiLine, shape)
                    .padding(horizontal = t.dp("fieldPadH"), vertical = t.dp("fieldPadV")),
                contentAlignment = if (fullInput) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(if (listening) "正在听…" else "说点什么吧", color = c.sub, fontSize = t.sp("fieldFont"))
                }
                inner()
            }
        },
    )
}

/** 展开区右侧的小按钮：把输入框在普通和大输入之间切换。 */
@Composable
private fun FullInputToggle(fullInput: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    Box(
        modifier
            .size(t.dp("toggleSize"))
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (fullInput) "收起输入框" else "展开输入框" },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            if (fullInput) AppIcons.COLLAPSE else AppIcons.EXPAND,
            c.sub,
            t.dp("toggleIcon"),
            strokeWidth = t.f("iconStroke"),
        )
    }
}

@Composable
private fun SendButton(mode: SendButtonMode, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val soft = mode == SendButtonMode.QueueClock || mode == SendButtonMode.Waiting
    val label = when (mode) {
        SendButtonMode.Mic -> "语音"
        SendButtonMode.Send -> "发送"
        SendButtonMode.QueueClock -> "排队发送"
        SendButtonMode.Waiting -> "对方回复中"
        SendButtonMode.Listening -> "停止语音输入"
    }
    Box(Modifier.size(t.dp("controlSize")), contentAlignment = Alignment.Center) {
    if (mode == SendButtonMode.Listening) {
        val transition = rememberInfiniteTransition(label = "listening")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(t.f("listenRingMs").toInt(), easing = LinearEasing), RepeatMode.Restart),
            label = "ring",
        )
        val grow = t.f("listenRingScale")
        val peak = t.f("listenRingAlpha")
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sc = 1f + grow * p
                    scaleX = sc
                    scaleY = sc
                    alpha = peak * (1f - p)
                }
                .background(c.accent, CircleShape),
        )
    }
    Box(
        Modifier
            .size(t.dp("controlSize"))
            .clip(CircleShape)
            .background(if (soft) c.soft else c.accent)
            .then(
                if (mode == SendButtonMode.Waiting) Modifier
                else Modifier.clickable(role = Role.Button, onClick = onClick),
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val icon = t.dp("controlIcon")
        val stroke = t.f("iconStroke")
        when (mode) {
            SendButtonMode.Mic -> AppIcon(AppIcons.MIC, c.onAccent, icon, strokeWidth = stroke)
            SendButtonMode.Send -> AppIcon(AppIcons.UP, c.onAccent, icon, strokeWidth = stroke)
            SendButtonMode.QueueClock -> AppIcon(AppIcons.CLOCK, c.onSoft, icon, strokeWidth = stroke)
            SendButtonMode.Listening -> Box(
                Modifier
                    .size(t.dp("listenStopSize"))
                    .background(c.onAccent, RoundedCornerShape(t.dp("listenStopRadius"))),
            )
            SendButtonMode.Waiting -> Unit
        }
    }
    }
}

@Composable
private fun QueuePanel(
    queue: List<QueuedMessage>,
    onEdit: (Int) -> Unit,
    onSendNow: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = RoundedCornerShape(t.dp("queueRadius"))
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.input, shape)
            .border(t.dp("bubbleBorder"), c.line, shape)
            .padding(
                start = t.dp("queuePadH"),
                end = t.dp("queuePadH"),
                top = t.dp("queuePadTop"),
                bottom = t.dp("queuePadBottom"),
            ),
    ) {
        Row(
            Modifier.padding(bottom = t.dp("queueHeaderPadBottom")),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.dp("queueHeaderGap")),
        ) {
            AppIcon(AppIcons.CLOCK, c.sub, t.dp("queueHeaderIcon"), strokeWidth = t.f("iconStroke"))
            Text("已排队 ${queue.size} 条", color = c.sub, fontSize = t.sp("queueHeaderFont"))
        }
        queue.forEachIndexed { i, item ->
            Box(Modifier.fillMaxWidth().height(t.dp("bubbleBorder")).background(c.line))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = t.dp("queueRowPadV")),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(t.dp("queueRowGap")),
            ) {
                Text(
                    queueLabel(item),
                    color = c.text,
                    fontSize = t.sp("queueRowFont"),
                    modifier = Modifier.weight(1f),
                )
                QueueIconButton(AppIcons.PENCIL, c.sub, "修改") { onEdit(i) }
                QueueIconButton(AppIcons.PLANE, c.accent, "马上发送") { onSendNow(i) }
                QueueIconButton(AppIcons.TRASH, c.sub, "删除") { onDelete(i) }
            }
        }
    }
}

/** 排队里的图片消息前缀：一张"[图片] "，多张"[图片×3] "；文件同理写"[文件] "。 */
private fun tagPrefix(label: String, count: Int): String = when {
    count <= 0 -> ""
    count == 1 -> "[$label] "
    else -> "[$label×$count] "
}

/** 排队面板里一行的文字：前缀 + 文字；只发了文件、没写字时，显示文件名。 */
private fun queueLabel(item: QueuedMessage): String {
    val body = when {
        item.text.isNotEmpty() -> item.text
        item.imageUris.isEmpty() && item.attachments.isNotEmpty() ->
            item.attachments.first().name + if (item.attachments.size > 1) " 等" else ""
        else -> ""
    }
    return tagPrefix("图片", item.imageUris.size) + tagPrefix("文件", item.attachments.size) + body
}

@Composable
private fun QueueIconButton(
    iconPath: String,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
    onClick: () -> Unit,
) {
    val t = LocalChatTheme.current
    Box(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(t.dp("queueIconPad"))
            .semantics { contentDescription = label },
    ) {
        AppIcon(iconPath, tint, t.dp("queueRowIcon"), strokeWidth = t.f("iconStroke"))
    }
}

/** 预览区：放待发送的内容（现在是图片的小缩略图，横着排、可以左右滑；点缩略图看大图，右上角"×"取消这一张）。以后别的待发送内容也放这里。 */
@Composable
private fun PreviewArea(
    uris: List<String>,
    files: List<Attachment>,
    onOpen: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRemoveFile: (Int) -> Unit,
) {
    val t = LocalChatTheme.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(t.dp("thumbGap")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 先图片后文件，和发出去以后的排列顺序一致
        itemsIndexed(uris, key = { _, uri -> "img:$uri" }) { index, uri ->
            PreviewThumb(uri, { onOpen(index) }, { onRemove(index) })
        }
        itemsIndexed(files, key = { _, f -> "file:${f.uri}" }) { index, f ->
            PendingFileChip(f) { onRemoveFile(index) }
        }
    }
}

@Composable
private fun PreviewThumb(uri: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = RoundedCornerShape(t.dp("thumbRadius"))
    Box(Modifier.size(t.dp("thumbSize"))) {
        AsyncImage(
            model = uri,
            contentDescription = "已选图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(c.btn)
                .border(t.dp("bubbleBorder"), c.bubbleAiLine, shape)
                .clickable(role = Role.Button, onClick = onOpen),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(t.dp("thumbBadgePad"))
                .size(t.dp("thumbBadge"))
                .clip(CircleShape)
                .background(c.bubbleAi)
                .border(t.dp("bubbleBorder"), c.line, CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = "取消图片" },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.X, c.text, t.dp("thumbBadgeIcon"), strokeWidth = t.f("thumbBadgeStroke"))
        }
    }
}
