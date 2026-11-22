package com.example.ap.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.example.ap.theme.LocalChatTheme
import com.example.ap.ui.AppIcon
import com.example.ap.ui.AppIcons

/**
 * 文件小胶囊条：聊天里的文件、折叠一叠、输入栏上方的待发送文件，都在这个文件里。
 * 以后做"美化款式"，只改这一个文件。
 */

/** 每种文件类型对应的线条图标（颜色统一，不按类型换色）。 */
fun fileIconPath(kind: FileKind): String = when (kind) {
    FileKind.Text -> AppIcons.FILE_TEXT
    FileKind.Data -> AppIcons.BRACES
    FileKind.Code -> AppIcons.FILE_CODE
    FileKind.Pdf -> AppIcons.FILE_PDF
    FileKind.Sheet -> AppIcons.TABLE
    FileKind.Archive -> AppIcons.PACKAGE
    FileKind.Other -> AppIcons.CLIP
}

/**
 * 聊天里的一组文件。少于 fileFoldFrom 个直接排开；
 * 达到这个数就折成"N 个附件"一叠，点开后展开成列表。
 */
@Composable
fun AttachmentGroup(
    files: List<Attachment>,
    fromMe: Boolean,
    maxWidth: Dp,
    onFileClick: (Attachment) -> Unit,
) {
    val t = LocalChatTheme.current
    var open by remember { mutableStateOf(false) }
    val fold = files.size >= t.f("fileFoldFrom").toInt() && !open
    if (fold) {
        FoldedFiles(files.size, fromMe, maxWidth) { open = true }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(t.dp("imageTextGap")),
            horizontalAlignment = if (fromMe) Alignment.End else Alignment.Start,
        ) {
            files.forEach { FileChip(it, fromMe, maxWidth) { onFileClick(it) } }
        }
    }
}

/** 胶囊的外壳：颜色跟发送者的气泡走（自己：赤陶实心；对方：奶油底加细边）。 */
@Composable
private fun CapsuleShell(
    fromMe: Boolean,
    maxWidth: Dp,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val t = LocalChatTheme.current
    val c = t.colors
    val h = t.dp("fileChipHeight")
    val shape = RoundedCornerShape(h / 2)
    Row(
        modifier = Modifier
            .height(h)
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(if (fromMe) c.accent else c.bubbleAi)
            .then(
                if (fromMe) Modifier
                else Modifier.border(t.dp("bubbleBorder"), c.bubbleAiLine, shape),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = t.dp("fileChipPadStart"), end = t.dp("fileChipPadEnd")),
        horizontalArrangement = Arrangement.spacedBy(t.dp("fileChipGap")),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun FileChip(file: Attachment, fromMe: Boolean, maxWidth: Dp, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    CapsuleShell(fromMe, maxWidth, onClick) {
        AppIcon(
            fileIconPath(fileKind(file.name)),
            if (fromMe) c.onAccent else c.accent,
            t.dp("fileChipIcon"),
            strokeWidth = t.f("iconStroke"),
        )
        FileName(
            file.name,
            if (fromMe) c.onAccent else c.text,
            t.sp("fileChipNameFont"),
            Modifier.weight(1f, fill = false),
        )
        if (file.sizeBytes >= 0) {
            Text(
                formatSize(file.sizeBytes),
                color = if (fromMe) c.onAccentSub else c.sub,
                fontSize = t.sp("fileChipSizeFont"),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** "N 个附件"一叠：胶囊下面垫一层，露出一点，点一下展开。 */
@Composable
private fun FoldedFiles(count: Int, fromMe: Boolean, maxWidth: Dp, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val under = c.foldUnder
    // 垫层的颜色：自己侧用 foldUnder；对方侧用气泡的细边色，更安静
    val underColor = if (fromMe) under else c.bubbleAiLine
    Box(
        Modifier
            .padding(bottom = t.dp("fileStackPeek"))
            .drawBehind {
                val inset = t.dp("fileStackInset").toPx()
                val h = t.dp("fileStackHeight").toPx()
                val peek = t.dp("fileStackPeek").toPx()
                drawRoundRect(
                    color = underColor,
                    topLeft = Offset(inset, size.height - h + peek),
                    size = Size(size.width - inset * 2, h),
                    cornerRadius = CornerRadius(t.dp("fileStackRadius").toPx()),
                )
            },
    ) {
        CapsuleShell(fromMe, maxWidth, onClick) {
            val tint = if (fromMe) c.onAccent else c.accent
            AppIcon(AppIcons.CLIP, tint, t.dp("fileChipIcon"), strokeWidth = t.f("iconStroke"))
            Text(
                "$count 个附件",
                color = if (fromMe) c.onAccent else c.text,
                fontSize = t.sp("fileChipNameFont"),
                maxLines = 1,
            )
            AppIcon(AppIcons.CHEVRON_DOWN, tint, t.dp("fileChipIcon"), strokeWidth = t.f("iconStroke"))
        }
    }
}

/** 文件名：名字太长时中间省略，后缀一直保留。 */
@Composable
private fun FileName(name: String, color: Color, fontSize: TextUnit, modifier: Modifier) {
    val (base, ext) = splitName(name)
    Row(modifier) {
        Text(
            base,
            color = color,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (ext.isNotEmpty()) {
            Text(ext, color = color, fontSize = fontSize, maxLines = 1, softWrap = false)
        }
    }
}

/** 输入栏上方预览区里的待发送文件：图标 + 文件名，右上角"×"取消。 */
@Composable
fun PendingFileChip(file: Attachment, onRemove: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = RoundedCornerShape(t.dp("pendingFileRadius"))
    // 外面这层和图片缩略图一样高，胶囊居中，"×"往上提一点不会被切掉
    Box(Modifier.height(t.dp("thumbSize")), contentAlignment = Alignment.Center) {
        Box {
            Row(
                Modifier
                    .height(t.dp("pendingFileHeight"))
                    .widthIn(max = t.dp("pendingFileMaxWidth"))
                    .clip(shape)
                    .background(c.bubbleAi)
                    .border(t.dp("bubbleBorder"), c.bubbleAiLine, shape)
                    .padding(start = t.dp("pendingFilePadStart"), end = t.dp("pendingFilePadEnd")),
                horizontalArrangement = Arrangement.spacedBy(t.dp("pendingFileGap")),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    fileIconPath(fileKind(file.name)),
                    c.accent,
                    t.dp("pendingFileIcon"),
                    strokeWidth = t.f("iconStroke"),
                )
                FileName(file.name, c.text, t.sp("pendingFileFont"), Modifier.weight(1f, fill = false))
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = -t.dp("pendingFileBadgeLift"))
                    .size(t.dp("thumbBadge"))
                    .clip(CircleShape)
                    .background(c.bubbleAi)
                    .border(t.dp("bubbleBorder"), c.line, CircleShape)
                    .clickable(role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = "取消文件" },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcons.X, c.text, t.dp("thumbBadgeIcon"), strokeWidth = t.f("thumbBadgeStroke"))
            }
        }
    }
}
