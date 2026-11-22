package com.example.ap.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** 一套颜色。数值全部来自 assets/chat_theme.json，不在界面代码里写死。 */
data class ChatColors(
    val chatBg: Color,
    val line: Color,
    val bubbleAi: Color,
    val bubbleAiLine: Color,
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val onSoft: Color,
    val text: Color,
    val sub: Color,
    val dot: Color,
    val btn: Color,
    val queue: Color,
    val input: Color,
    val dock: Color,
    val dockBtn: Color,
    val viewerBg: Color,
    val viewerBtn: Color,
    val onViewerBtn: Color,
    val onAccentSub: Color,
    val foldUnder: Color,
)

/** 颜色 + 尺寸。尺寸用名字取：theme.dp("avatarSize")。 */
class ChatTheme(
    val colors: ChatColors,
    private val metrics: Map<String, Float>,
) {
    fun f(key: String): Float =
        metrics[key] ?: error("chat_theme.json 的 metrics 里缺少 \"$key\"")

    fun dp(key: String): Dp = f(key).dp
    fun sp(key: String): TextUnit = f(key).sp

    companion object {
        fun load(context: Context, dark: Boolean): ChatTheme {
            val text = context.assets.open("chat_theme.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            val c = root.getJSONObject("colors").getJSONObject(if (dark) "dark" else "light")
            fun color(key: String) = Color(android.graphics.Color.parseColor(c.getString(key)))

            val m = root.getJSONObject("metrics")
            val metrics = m.keys().asSequence().associateWith { m.getDouble(it).toFloat() }

            return ChatTheme(
                colors = ChatColors(
                    chatBg = color("chatBg"),
                    line = color("line"),
                    bubbleAi = color("bubbleAi"),
                    bubbleAiLine = color("bubbleAiLine"),
                    accent = color("accent"),
                    onAccent = color("onAccent"),
                    soft = color("soft"),
                    onSoft = color("onSoft"),
                    text = color("text"),
                    sub = color("sub"),
                    dot = color("dot"),
                    btn = color("btn"),
                    queue = color("queue"),
                    input = color("input"),
                    dock = color("dock"),
                    dockBtn = color("dockBtn"),
                    viewerBg = color("viewerBg"),
                    viewerBtn = color("viewerBtn"),
                    onViewerBtn = color("onViewerBtn"),
                    onAccentSub = color("onAccentSub"),
                    foldUnder = color("foldUnder"),
                ),
                metrics = metrics,
            )
        }
    }
}

val LocalChatTheme = staticCompositionLocalOf<ChatTheme> {
    error("ChatTheme 还没有提供")
}
