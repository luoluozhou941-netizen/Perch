package com.example.ap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** 全部图标都是 24x24 的 SVG 路径，和设计存档里的是同一份。 */
object AppIcons {
    const val MIC = "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5V21 M8.5 21h7"
    const val UP = "M12 19V5M5 12l7-7 7 7"
    const val CLOCK = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5.5l3.5 2"
    const val IMAGE = "M7.5 3.5h9a4 4 0 0 1 4 4v9a4 4 0 0 1-4 4h-9a4 4 0 0 1-4-4v-9a4 4 0 0 1 4-4z M9 7.9a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2z M4 17l5-4.5 4 3.5 3-2.5 4.5 4"
    const val CLIP = "M20 11.5l-8 8a5 5 0 0 1-7-7l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7L9.5 16.9a1.7 1.7 0 0 1-2.4-2.4L14.5 7"
    const val SWAP = "M4 8h14l-3.5-3.5M20 16H6l3.5 3.5"
    const val PLUS = "M12 5v14M5 12h14"
    const val X = "M6 6l12 12M18 6L6 18"
    const val PENCIL = "M4 20l1-4L16.5 4.5a2 2 0 0 1 3 3L8 19l-4 1zM14 7l3 3"
    const val PLANE = "M21 3L3 10.5l7 3 3 7L21 3zM10 13.5L21 3"
    const val EXPAND = "M14 4h6v6 M10 20H4v-6 M20 4l-7 7 M4 20l7-7"
    const val COLLAPSE = "M20 10h-6V4 M4 14h6v6 M14 10l6-6 M10 14l-6 6"
    const val FILE_TEXT = "M14 3v4a1 1 0 0 0 1 1h4M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2zM9 9h1M9 13h6M9 17h6"
    const val BRACES = "M7 4a2 2 0 0 0-2 2v3a2 3 0 0 1-2 3 2 3 0 0 1 2 3v3a2 2 0 0 0 2 2M17 4a2 2 0 0 1 2 2v3a2 3 0 0 0 2 3 2 3 0 0 0-2 3v3a2 2 0 0 1-2 2"
    const val FILE_CODE = "M14 3v4a1 1 0 0 0 1 1h4M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2zM10 13l-1 2 1 2M14 13l1 2-1 2"
    const val FILE_PDF = "M14 3v4a1 1 0 0 0 1 1h4M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2zM9 17v-4h1.5a1.5 1.5 0 0 1 0 3H9"
    const val TABLE = "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM3 10h18M10 3v18"
    const val PACKAGE = "M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5"
    const val CHEVRON_DOWN = "M6 9l6 6 6-6"
    const val TRASH = "M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13M10 11v6M14 11v6"
}

@Composable
fun AppIcon(
    pathData: String,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.9f,
) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
