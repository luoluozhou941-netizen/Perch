package com.example.ap.chat

import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.example.ap.theme.LocalChatTheme

/**
 * 聊天页。输入栏通过 inputBar 这个"插槽"传进来，
 * 以后换成别的输入栏，这个文件不用动。
 */
@Composable
fun ChatScreen(
    assistantName: String,
    statusText: String,
    avatarChar: String,
    messages: List<ChatMessage>,
    showTyping: Boolean,
    inputBar: @Composable () -> Unit,
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onFileClick: (Attachment) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalChatTheme.current.colors
    Column(
        modifier
            .fillMaxSize()
            .background(c.chatBg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)),
    ) {
        ChatHeader(assistantName, statusText, avatarChar)
        MessageList(messages, showTyping, onImageClick, onFileClick, Modifier.weight(1f).fillMaxWidth())
        inputBar()
    }
}

@Composable
private fun ChatHeader(name: String, status: String, avatarChar: String) {
    val t = LocalChatTheme.current
    val c = t.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = t.dp("headerPadH"), vertical = t.dp("headerPadV")),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.dp("headerGap")),
        ) {
            Box(
                Modifier
                    .size(t.dp("avatarSize"))
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(avatarChar, color = c.onAccent, fontSize = t.sp("avatarFont"))
            }
            Column {
                Text(
                    name,
                    color = c.text,
                    fontSize = t.sp("nameFont"),
                    lineHeight = t.sp("nameLineHeight"),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    status,
                    color = c.sub,
                    fontSize = t.sp("statusFont"),
                    modifier = Modifier.padding(top = t.dp("statusTopGap")),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(t.dp("headerBorder"))
                .background(c.line),
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    showTyping: Boolean,
    onImageClick: (List<String>, Int) -> Unit,
    onFileClick: (Attachment) -> Unit,
    modifier: Modifier,
) {
    val t = LocalChatTheme.current
    val listState = rememberLazyListState()
    val count = messages.size + if (showTyping) 1 else 0

    LaunchedEffect(count) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(t.dp("msgPad")),
        verticalArrangement = Arrangement.spacedBy(t.dp("msgGap")),
    ) {
        items(messages, key = { it.id }) { m -> MessageRow(m, onImageClick, onFileClick) }
        if (showTyping) {
            item(key = "typing") { TypingRow() }
        }
    }
}

@Composable
private fun bubbleShape(fromMe: Boolean): RoundedCornerShape {
    val t = LocalChatTheme.current
    val r = t.dp("bubbleRadius")
    val small = t.dp("bubbleCornerSmall")
    return RoundedCornerShape(
        topStart = r,
        topEnd = r,
        bottomEnd = if (fromMe) small else r,
        bottomStart = if (fromMe) r else small,
    )
}

@Composable
private fun MessageRow(
    m: ChatMessage,
    onImageClick: (List<String>, Int) -> Unit,
    onFileClick: (Attachment) -> Unit,
) {
    val t = LocalChatTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * t.f("bubbleMaxWidth")
        val hasText = m.text.isNotEmpty()
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(t.dp("imageTextGap")),
        ) {
            // 一次发送的排列：文字在最上面，然后图片，最后文件；没有的就跳过，不留空气泡
            if (hasText) {
                TextBubble(m.text, m.fromMe, maxBubbleWidth)
            }
            if (m.imageUris.size == 1) {
                ImageBlock(m.imageUris[0], m.fromMe, hasText) { onImageClick(m.imageUris, 0) }
            } else if (m.imageUris.size > 1) {
                ImageGrid(m.imageUris) { i -> onImageClick(m.imageUris, i) }
            }
            if (m.attachments.isNotEmpty()) {
                AttachmentGroup(m.attachments, m.fromMe, maxBubbleWidth, onFileClick)
            }
        }
    }
}

@Composable
private fun TextBubble(text: String, fromMe: Boolean, maxBubbleWidth: Dp) {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = bubbleShape(fromMe)
    Box(
        Modifier
            .widthIn(max = maxBubbleWidth)
            .background(if (fromMe) c.accent else c.bubbleAi, shape)
            .then(
                if (fromMe) Modifier
                else Modifier.border(t.dp("bubbleBorder"), c.bubbleAiLine, shape),
            )
            .padding(horizontal = t.dp("bubblePadH"), vertical = t.dp("bubblePadV")),
    ) {
        Text(
            text,
            color = if (fromMe) c.onAccent else c.text,
            fontSize = t.sp("bubbleFont"),
            lineHeight = t.sp("bubbleLineHeight"),
        )
    }
}

/** 图片块（点一下打开大图）：约 120dp 见方（imageMaxSide），保持原比例，比例钳制在 0.5~2 后居中裁切。 */
@Composable
private fun ImageBlock(uri: String, fromMe: Boolean, hasText: Boolean, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    // 下面还有文字气泡时，图片四个角都圆；只有图片时，沿用气泡的小圆角
    val shape = if (hasText) RoundedCornerShape(t.dp("bubbleRadius")) else bubbleShape(fromMe)
    val maxSide = t.dp("imageMaxSide")
    var ratio by remember(uri) { mutableStateOf(4f / 3f) }
    val shownRatio = ratio.coerceIn(0.5f, 2f)

    AsyncImage(
        model = uri,
        contentDescription = "图片",
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
        },
        modifier = Modifier
            .width(if (shownRatio >= 1f) maxSide else maxSide * shownRatio)
            .aspectRatio(shownRatio)
            .clip(shape)
            .background(c.btn)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * 多张图：最多两列的小方块。聊天里最多显示 imageGridMax 格，
 * 超出的张数盖在最后一格上显示"+N"；点任意一格进大图，从那一张开始看。
 */
@Composable
private fun ImageGrid(uris: List<String>, onClick: (Int) -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val cell = t.dp("imageGridCell")
    val gap = t.dp("imageGridGap")
    val shape = RoundedCornerShape(t.dp("imageGridRadius"))
    val shown = uris.take(t.f("imageGridMax").toInt())
    val hidden = uris.size - shown.size

    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        shown.chunked(2).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                items.forEachIndexed { col, uri ->
                    val index = row * 2 + col
                    Box(
                        Modifier
                            .size(cell)
                            .clip(shape)
                            .background(c.btn)
                            .clickable(role = Role.Button, onClick = { onClick(index) }),
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (hidden > 0 && index == shown.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(c.viewerBg.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+$hidden", color = c.onViewerBtn, fontSize = t.sp("imageGridMoreFont"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingRow() {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = bubbleShape(fromMe = false)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .height(t.dp("typingHeight"))
                .background(c.bubbleAi, shape)
                .border(t.dp("bubbleBorder"), c.bubbleAiLine, shape)
                .padding(horizontal = t.dp("typingPadH")),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(t.dp("typingDotGap")),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypingDot(delayMs = 0)
                TypingDot(delayMs = 200)
                TypingDot(delayMs = 400)
            }
        }
    }
}

@Composable
private fun TypingDot(delayMs: Int) {
    val t = LocalChatTheme.current
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.35f at 0
                1f at 360
                0.35f at 720
                0.35f at 1200
            },
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "typingAlpha",
    )
    Box(
        Modifier
            .size(t.dp("typingDotSize"))
            .graphicsLayer { this.alpha = alpha }
            .background(t.colors.dot, CircleShape),
    )
}
