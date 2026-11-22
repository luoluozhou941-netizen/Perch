package com.example.ap.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ap.theme.LocalChatTheme
import com.example.ap.ui.AppIcon
import com.example.ap.ui.AppIcons

/**
 * 全屏大图查看层，可以左右滑动看同一组的所有图。
 * - 没放大时：左右滑切换图片；单击、右上角"×"、返回键都能关闭。
 * - 双指缩放，放大后单指拖动（这时左右滑只拖图，不翻页）；双击在原大小和放大之间切换。
 * - 多于一张时顶部显示"第几张/共几张"。
 * - onDelete 不为空时（从待发送预览区进来），底部有"删除这张"按钮。
 */
@Composable
fun ImageViewer(
    uris: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDelete: ((Int) -> Unit)? = null,
) {
    val t = LocalChatTheme.current
    val c = t.colors
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, uris.lastIndex)) { uris.size }
    // 当前这一页是否放大了：放大时关掉翻页
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(uris.size) { zoomed = false }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.viewerBg),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
                pageSpacing = t.dp("viewerPageGap"),
                key = { uris[it] },
            ) { page ->
                ZoomablePage(uris[page], onZoomChange = { zoomed = it }, onDismiss = onDismiss)
            }

            if (uris.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = t.dp("viewerClosePad"))
                        .height(t.dp("viewerCloseSize")),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${pagerState.currentPage + 1}/${uris.size}",
                        color = c.onViewerBtn,
                        fontSize = t.sp("viewerCounterFont"),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(c.viewerBtn)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(t.dp("viewerClosePad"))
                    .size(t.dp("viewerCloseSize"))
                    .clip(CircleShape)
                    .background(c.viewerBtn)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "关闭" },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    AppIcons.X,
                    c.onViewerBtn,
                    t.dp("viewerCloseIcon"),
                    strokeWidth = t.f("iconStroke"),
                )
            }

            if (onDelete != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = t.dp("viewerDeleteBottomPad"))
                        .height(t.dp("viewerDeleteHeight"))
                        .clip(CircleShape)
                        .background(c.viewerBtn)
                        .clickable(role = Role.Button) { onDelete(pagerState.currentPage) }
                        .padding(horizontal = t.dp("viewerDeletePadH")),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(t.dp("chipIconGap")),
                    ) {
                        AppIcon(
                            AppIcons.TRASH,
                            c.onViewerBtn,
                            t.dp("viewerDeleteIcon"),
                            strokeWidth = t.f("iconStroke"),
                        )
                        Text("删除这张", color = c.onViewerBtn, fontSize = t.sp("viewerDeleteFont"))
                    }
                }
            }
        }
    }
}

/** 单张图的一页：自己管缩放和拖动。没放大时单指滑动不拦截，交给外面的翻页。 */
@Composable
private fun ZoomablePage(uri: String, onZoomChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val t = LocalChatTheme.current
    val maxScale = t.f("viewerMaxScale")
    val doubleTapScale = t.f("viewerDoubleTapScale")

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun applyScale(s: Float) {
        scale = s
        onZoomChange(s > 1f)
    }

    // 放大后只允许拖到图片边缘（按放大后多出来的那部分算）
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (scale <= 1f) onDismiss() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            applyScale(1f)
                            offset = Offset.Zero
                        } else {
                            applyScale(doubleTapScale)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // 两指（缩放）或者已经放大（拖动）才接管手势；否则不拦，让翻页接手
                        if (fingers >= 2 || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, maxScale)
                            applyScale(newScale)
                            offset = if (newScale <= 1f) Offset.Zero
                            else clamp(offset + event.calculatePan(), newScale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "大图",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
