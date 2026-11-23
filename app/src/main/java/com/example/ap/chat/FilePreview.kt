package com.example.ap.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ap.theme.LocalChatTheme
import com.example.ap.ui.AppIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * 附件预览页（全屏）。点聊天里的文件标签进来。
 * - 文本、数据、代码：显示文字（带行号，可长按选中复制），太大的只读前面一部分
 * - PDF：安卓自带渲染，一页一页竖着滑
 * - docx / xlsx / pptx：只抽出文字显示，没有排版和图片
 * - zip：列出里面的文件名
 * - 其他：信息页（大图标 + 文件名 + "用其他应用打开"）
 * 读不出来时一律退回信息页。尺寸数值在 chat_theme.json 的 preview* 里。
 */
@Composable
fun FilePreview(file: Attachment, onDismiss: () -> Unit, onOpenExternal: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val kind = remember(file.uri, file.name, file.mime) { previewKind(file) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.chatBg)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(t.dp("previewBarHeight"))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton(ARROW_LEFT, "返回", c.text, onDismiss)
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(
                        file.name,
                        color = c.text,
                        fontSize = t.sp("previewTitleFont"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        typeLabel(file) + if (file.sizeBytes >= 0) " · " + formatSize(file.sizeBytes) else "",
                        color = c.sub,
                        fontSize = t.sp("previewSubFont"),
                        maxLines = 1,
                    )
                }
                BarButton(EXTERNAL_LINK, "用其他应用打开", c.accent, onOpenExternal)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (kind) {
                    PreviewKind.Info -> InfoBody(file, "这类文件暂时不能在这里预览", onOpenExternal)
                    PreviewKind.Pdf -> PdfBody(file, onOpenExternal)
                    else -> LoadedBody(file, kind, onOpenExternal)
                }
            }
        }
    }
}

@Composable
private fun BarButton(path: String, description: String, tint: Color, onClick: () -> Unit) {
    val t = LocalChatTheme.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(path, tint, t.dp("previewBarIcon"), strokeWidth = t.f("iconStroke"))
    }
}

// ───────────────────────── 分类 ─────────────────────────

private enum class PreviewKind { Plain, Pdf, Docx, Xlsx, Pptx, Zip, Info }

private val TEXT_EXTS = setOf(
    "txt", "md", "markdown", "log", "json", "csv", "tsv", "xml", "yaml", "yml",
    "html", "htm", "js", "ts", "jsx", "tsx", "css", "kt", "java", "py", "sh",
    "c", "cpp", "h", "go", "rs", "ini", "conf", "toml", "sql", "srt", "lrc",
)

private fun previewKind(f: Attachment): PreviewKind {
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" || f.mime == "application/pdf" -> PreviewKind.Pdf
        ext == "docx" -> PreviewKind.Docx
        ext == "xlsx" -> PreviewKind.Xlsx
        ext == "pptx" -> PreviewKind.Pptx
        ext == "zip" -> PreviewKind.Zip
        ext == "rtf" -> PreviewKind.Info
        ext in TEXT_EXTS || f.mime?.startsWith("text/") == true -> PreviewKind.Plain
        else -> PreviewKind.Info
    }
}

private fun typeLabel(f: Attachment): String {
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "doc", "docx" -> "文档"
        "ppt", "pptx" -> "演示文稿"
        else -> when (fileKind(f.name)) {
            FileKind.Text -> "文本"
            FileKind.Data -> "数据"
            FileKind.Code -> "代码"
            FileKind.Pdf -> "PDF"
            FileKind.Sheet -> "表格"
            FileKind.Archive -> "压缩包"
            FileKind.Other -> "文件"
        }
    }
}

// ───────────────────────── 读取结果 ─────────────────────────

private class SheetData(val name: String, val rows: List<List<String>>, val colCount: Int, val truncated: Boolean)
private class ZipItem(val name: String, val isDir: Boolean, val size: Long)

private sealed interface Loaded {
    class Plain(val lines: List<String>, val truncated: Boolean) : Loaded
    class Doc(val paragraphs: List<String>, val truncated: Boolean) : Loaded
    class Sheets(val sheets: List<SheetData>) : Loaded
    class Slides(val slides: List<List<String>>) : Loaded
    class ZipList(val items: List<ZipItem>, val total: Int, val more: Boolean) : Loaded
    class Failed(val reason: String) : Loaded
}

@Composable
private fun LoadedBody(file: Attachment, kind: PreviewKind, onOpenExternal: () -> Unit) {
    val t = LocalChatTheme.current
    val context = LocalContext.current
    val loaded by produceState<Loaded?>(null, file.uri) {
        value = withContext(Dispatchers.IO) {
            loadPreview(context, file, kind, t.f("previewTextMaxKb").toInt(), t.f("previewListMax").toInt(),
                t.f("previewSheetRows").toInt(), t.f("previewSheetCols").toInt())
        }
    }
    when (val l = loaded) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在读取…", color = t.colors.sub, fontSize = t.sp("previewInfoSub"))
        }
        is Loaded.Failed -> InfoBody(file, l.reason, onOpenExternal)
        is Loaded.Plain -> PlainBody(l.lines, l.truncated)
        is Loaded.Doc -> DocBody(l.paragraphs, l.truncated)
        is Loaded.Sheets -> SheetBody(l.sheets)
        is Loaded.Slides -> SlidesBody(l.slides)
        is Loaded.ZipList -> ZipBody(l)
    }
}

private const val MB = 1024L * 1024L

private fun loadPreview(
    context: Context,
    file: Attachment,
    kind: PreviewKind,
    textMaxKb: Int,
    listMax: Int,
    sheetRows: Int,
    sheetCols: Int,
): Loaded {
    if (file.uri.isBlank()) return Loaded.Failed("这是演示用的文件，没有内容")
    return try {
        when (kind) {
            PreviewKind.Plain -> loadPlain(context, file.uri, textMaxKb * 1024)
            PreviewKind.Zip -> {
                if (file.sizeBytes > 200 * MB) Loaded.Failed("文件太大，不在这里列目录")
                else listZip(context, file.uri, listMax)
            }
            PreviewKind.Docx, PreviewKind.Xlsx, PreviewKind.Pptx -> {
                if (file.sizeBytes > 50 * MB) Loaded.Failed("文件太大，不在这里预览")
                else loadOffice(context, file.uri, kind, sheetRows, sheetCols)
            }
            else -> Loaded.Failed("这类文件暂时不能在这里预览")
        }
    } catch (e: Exception) {
        Loaded.Failed("这个文件读不出来（可能已损坏，或有密码）")
    } catch (e: OutOfMemoryError) {
        Loaded.Failed("文件太大，读不出来")
    }
}

// ───────────────────────── 文本 ─────────────────────────

private fun loadPlain(context: Context, uri: String, maxBytes: Int): Loaded {
    val stream = context.contentResolver.openInputStream(Uri.parse(uri))
        ?: return Loaded.Failed("这个文件现在打不开了")
    return stream.use { ins ->
        val buf = ByteArray(maxBytes + 1)
        var n = 0
        while (n < buf.size) {
            val r = ins.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        val truncated = n > maxBytes
        val len = minOf(n, maxBytes)
        for (i in 0 until minOf(len, 4096)) {
            if (buf[i] == 0.toByte()) return@use Loaded.Failed("这不是文本文件，没法在这里显示")
        }
        var s = String(buf, 0, len, Charsets.UTF_8)
        // 不是 UTF-8 的中文文本（常见 GBK）：改用 GBK 再读一次
        if (s.dropLast(3).contains('\uFFFD')) {
            try {
                s = String(buf, 0, len, Charset.forName("GBK"))
            } catch (e: Exception) {
                // 保持原样
            }
        }
        if (truncated) {
            val cut = s.lastIndexOf('\n')
            if (cut > 0) s = s.substring(0, cut)
        }
        val lines = s.split("\n").map { it.removeSuffix("\r") }.toMutableList()
        if (lines.size > 1 && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
        Loaded.Plain(lines, truncated)
    }
}

@Composable
private fun PlainBody(lines: List<String>, truncated: Boolean) {
    val t = LocalChatTheme.current
    val c = t.colors
    val gutter = (lines.size.toString().length * 8 + 6).dp
    val font = t.sp("previewTextFont")
    val lineHeight = t.sp("previewTextLine")
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = t.dp("previewPadH"), vertical = 10.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                Row {
                    DisableSelection {
                        Text(
                            "${i + 1}",
                            color = c.dot,
                            fontFamily = FontFamily.Monospace,
                            fontSize = font,
                            lineHeight = lineHeight,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(gutter),
                        )
                    }
                    Spacer(Modifier.width(t.dp("previewGutterGap")))
                    Text(
                        if (line.isEmpty()) " " else line,
                        color = c.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = font,
                        lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (truncated) {
                item { TruncatedNote("只显示了前面一部分，想看全部请用其他应用打开") }
            }
        }
    }
}

@Composable
private fun TruncatedNote(text: String) {
    val t = LocalChatTheme.current
    Box(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(t.colors.btn)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = t.colors.sub, fontSize = t.sp("previewSubFont"), textAlign = TextAlign.Center)
    }
}

// ───────────────────────── Office（docx / xlsx / pptx）─────────────────────────

/** 一次把需要的几个条目读出来（zip 是顺序读的，没法随机取）。单个条目超过 limit 就当没有。 */
private fun readZipEntries(
    context: Context,
    uri: String,
    wanted: (String) -> Boolean,
    limit: Int = 16 * 1024 * 1024,
): Map<String, ByteArray> {
    val out = HashMap<String, ByteArray>()
    val stream = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return out
    ZipInputStream(BufferedInputStream(stream)).use { zis ->
        while (true) {
            val e = zis.nextEntry ?: break
            if (e.isDirectory || !wanted(e.name)) continue
            val bos = ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            var total = 0
            var tooBig = false
            while (true) {
                val r = zis.read(tmp)
                if (r < 0) break
                total += r
                if (total > limit) { tooBig = true; break }
                bos.write(tmp, 0, r)
            }
            if (!tooBig) out[e.name] = bos.toByteArray()
        }
    }
    return out
}

private fun newParser(bytes: ByteArray): XmlPullParser {
    val p = Xml.newPullParser()
    p.setInput(ByteArrayInputStream(bytes), "UTF-8")
    return p
}

private fun XmlPullParser.local(): String = (name ?: "").substringAfter(':')

private const val MAX_CHARS = 200_000

private fun loadOffice(context: Context, uri: String, kind: PreviewKind, sheetRows: Int, sheetCols: Int): Loaded {
    return when (kind) {
        PreviewKind.Docx -> {
            val files = readZipEntries(context, uri, { it == "word/document.xml" })
            val bytes = files["word/document.xml"] ?: return Loaded.Failed("没读到文档内容")
            val (paras, truncated) = parseParagraphs(bytes, "t", "p", keepEmpty = true)
            Loaded.Doc(paras, truncated)
        }
        PreviewKind.Pptx -> {
            val files = readZipEntries(context, uri, { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") })
            val names = files.keys.sortedBy { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            if (names.isEmpty()) return Loaded.Failed("没读到幻灯片内容")
            Loaded.Slides(names.map { parseParagraphs(files.getValue(it), "t", "p", keepEmpty = false).first })
        }
        else -> loadXlsx(context, uri, sheetRows, sheetCols)
    }
}

/** 抽文字：textTag 里的字拼成一段，遇到 paraTag 结束就换段。 */
private fun parseParagraphs(bytes: ByteArray, textTag: String, paraTag: String, keepEmpty: Boolean): Pair<List<String>, Boolean> {
    val p = newParser(bytes)
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var inText = false
    var chars = 0
    var truncated = false
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
            XmlPullParser.START_TAG -> when (p.local()) {
                textTag -> inText = true
                "tab" -> cur.append('\t')
                "br" -> cur.append('\n')
            }
            XmlPullParser.TEXT -> if (inText) cur.append(p.text)
            XmlPullParser.END_TAG -> when (p.local()) {
                textTag -> inText = false
                paraTag -> {
                    val s = cur.toString()
                    cur.setLength(0)
                    if (s.isNotEmpty() || (keepEmpty && out.isNotEmpty() && out.last().isNotEmpty())) {
                        out.add(s)
                        chars += s.length
                    }
                }
            }
        }
        if (chars > MAX_CHARS) { truncated = true; break }
        ev = p.next()
    }
    while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.lastIndex)
    return out to truncated
}

private fun loadXlsx(context: Context, uri: String, maxRows: Int, maxCols: Int): Loaded {
    val files = readZipEntries(context, uri, {
        it == "xl/sharedStrings.xml" || it == "xl/workbook.xml" || it == "xl/_rels/workbook.xml.rels" ||
            (it.startsWith("xl/worksheets/") && it.endsWith(".xml"))
    })
    val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

    // 工作表的名字和对应的文件
    val sheetPaths = ArrayList<Pair<String, String>>()
    val wb = files["xl/workbook.xml"]
    val rels = files["xl/_rels/workbook.xml.rels"]
    if (wb != null && rels != null) {
        val targets = HashMap<String, String>()
        val rp = newParser(rels)
        var ev = rp.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && rp.local() == "Relationship") {
                val id = rp.getAttributeValue(null, "Id")
                val target = rp.getAttributeValue(null, "Target")
                if (id != null && target != null) {
                    targets[id] = if (target.startsWith("/")) target.trimStart('/') else "xl/$target"
                }
            }
            ev = rp.next()
        }
        val wp = newParser(wb)
        ev = wp.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && wp.local() == "sheet") {
                var name = ""
                var rid = ""
                for (i in 0 until wp.attributeCount) {
                    val an = wp.getAttributeName(i)
                    if (an == "name") name = wp.getAttributeValue(i)
                    else if (an.substringAfter(':') == "id") rid = wp.getAttributeValue(i)
                }
                val path = targets[rid]
                if (path != null) sheetPaths.add(name to path)
            }
            ev = wp.next()
        }
    }
    if (sheetPaths.isEmpty()) {
        files.keys.filter { it.startsWith("xl/worksheets/") }.sorted().forEachIndexed { i, p ->
            sheetPaths.add("工作表${i + 1}" to p)
        }
    }
    val sheets = sheetPaths.mapNotNull { (name, path) ->
        files[path]?.let { parseSheet(it, shared, name, maxRows, maxCols) }
    }
    if (sheets.isEmpty()) return Loaded.Failed("没读到表格内容")
    return Loaded.Sheets(sheets)
}

private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val p = newParser(bytes)
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var inT = false
    var inPhonetic = false
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
            XmlPullParser.START_TAG -> when (p.local()) {
                "si" -> cur.setLength(0)
                "rPh" -> inPhonetic = true
                "t" -> inT = true
            }
            XmlPullParser.TEXT -> if (inT && !inPhonetic) cur.append(p.text)
            XmlPullParser.END_TAG -> when (p.local()) {
                "t" -> inT = false
                "rPh" -> inPhonetic = false
                "si" -> out.add(cur.toString())
            }
        }
        ev = p.next()
    }
    return out
}

private fun colIndex(ref: String): Int {
    var n = 0
    for (ch in ref) {
        if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1)
        else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1)
        else break
    }
    return n - 1
}

private fun rowIndex(ref: String): Int =
    (ref.dropWhile { !it.isDigit() }.toIntOrNull() ?: 0) - 1

private fun parseSheet(bytes: ByteArray, shared: List<String>, name: String, maxRows: Int, maxCols: Int): SheetData {
    val p = newParser(bytes)
    val grid = HashMap<Int, HashMap<Int, String>>()
    var maxRow = -1
    var maxCol = -1
    var truncated = false
    var ref = ""
    var type = ""
    var inV = false
    var inT = false
    val buf = StringBuilder()
    var ev = p.eventType
    loop@ while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
            XmlPullParser.START_TAG -> when (p.local()) {
                "c" -> {
                    ref = p.getAttributeValue(null, "r") ?: ""
                    type = p.getAttributeValue(null, "t") ?: ""
                    buf.setLength(0)
                }
                "v" -> inV = true
                "t" -> inT = true
            }
            XmlPullParser.TEXT -> if (inV || inT) buf.append(p.text)
            XmlPullParser.END_TAG -> when (p.local()) {
                "v" -> inV = false
                "t" -> inT = false
                "c" -> {
                    val r = rowIndex(ref)
                    val col = colIndex(ref)
                    if (r >= maxRows) { truncated = true; break@loop }
                    if (col >= maxCols) {
                        truncated = true
                    } else if (r >= 0 && col >= 0) {
                        val raw = buf.toString()
                        val value = when (type) {
                            "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            else -> raw
                        }.take(200)
                        if (value.isNotEmpty()) {
                            grid.getOrPut(r) { HashMap() }[col] = value
                            if (r > maxRow) maxRow = r
                            if (col > maxCol) maxCol = col
                        }
                    }
                }
            }
        }
        ev = p.next()
    }
    val cols = maxCol + 1
    val rows = (0..maxRow).map { r -> (0 until cols).map { c -> grid[r]?.get(c) ?: "" } }
    return SheetData(name, rows, cols.coerceAtLeast(1), truncated)
}

@Composable
private fun DocBody(paragraphs: List<String>, truncated: Boolean) {
    val t = LocalChatTheme.current
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = t.dp("previewPadH") + 2.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(paragraphs) { _, para ->
                Text(
                    if (para.isEmpty()) " " else para,
                    color = t.colors.text,
                    fontSize = t.sp("previewBodyFont"),
                    lineHeight = t.sp("previewBodyLine"),
                )
            }
            item { TruncatedNote(if (truncated) "只显示了前面一部分文字 · 这里不显示排版和图片" else "只显示文字 · 排版和图片请用其他应用打开") }
        }
    }
}

@Composable
private fun SlidesBody(slides: List<List<String>>) {
    val t = LocalChatTheme.current
    val c = t.colors
    val shape = RoundedCornerShape(14.dp)
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = t.dp("previewPadH"), vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(slides) { i, paras ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(c.bubbleAi)
                        .border(1.dp, c.bubbleAiLine, shape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DisableSelection {
                        Text("第 ${i + 1} 页", color = c.accent, fontSize = t.sp("previewSubFont"))
                    }
                    if (paras.isEmpty()) {
                        Text("（这一页没有文字）", color = c.sub, fontSize = t.sp("previewBodyFont"))
                    } else {
                        paras.forEach {
                            Text(it, color = c.text, fontSize = t.sp("previewBodyFont"), lineHeight = t.sp("previewBodyLine"))
                        }
                    }
                }
            }
            item { TruncatedNote("只显示文字 · 排版和图片请用其他应用打开") }
        }
    }
}

@Composable
private fun SheetBody(sheets: List<SheetData>) {
    val t = LocalChatTheme.current
    val c = t.colors
    var sel by remember(sheets) { mutableIntStateOf(0) }
    val sheet = sheets[sel.coerceIn(0, sheets.lastIndex)]
    val cellW = t.dp("previewCellW")
    val cellH = t.dp("previewCellH")
    val gutterW = t.dp("previewGutterW")
    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = t.dp("previewPadH"), vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sheets.forEachIndexed { i, s ->
                    val on = i == sel
                    Text(
                        s.name,
                        color = if (on) c.onAccent else c.text,
                        fontSize = t.sp("previewSubFont"),
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) c.accent else c.btn)
                            .clickable(role = Role.Tab) { sel = i }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            LazyColumn(Modifier.width(gutterW + cellW * sheet.colCount).fillMaxHeight()) {
                itemsIndexed(sheet.rows) { i, row ->
                    Row(
                        Modifier
                            .height(cellH)
                            .background(if (i % 2 == 0) c.chatBg else c.bubbleAi),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}",
                            color = c.dot,
                            fontSize = t.sp("previewSubFont"),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(gutterW),
                        )
                        row.forEach { cell ->
                            Text(
                                cell,
                                color = c.text,
                                fontSize = t.sp("previewTextFont"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(cellW).padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
                item {
                    Box(Modifier.width(gutterW + cellW * sheet.colCount).padding(horizontal = 12.dp)) {
                        TruncatedNote(
                            if (sheet.truncated) "只显示了前面一部分（行和列都有上限）" else "只显示内容 · 格式和公式请用其他应用打开",
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── zip ─────────────────────────

private fun listZip(context: Context, uri: String, maxShow: Int): Loaded {
    fun readWith(cs: Charset): Loaded {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: return Loaded.Failed("这个文件现在打不开了")
        val items = ArrayList<ZipItem>()
        var total = 0
        var more = false
        ZipInputStream(BufferedInputStream(stream), cs).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                total++
                if (items.size < maxShow) items.add(ZipItem(e.name, e.isDirectory, e.size))
                if (total >= 20000) { more = true; break }
            }
        }
        return Loaded.ZipList(items, total, more || total > items.size)
    }
    return try {
        readWith(Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        // 文件名不是 UTF-8（Windows 压的中文名常见 GBK）
        readWith(Charset.forName("GBK"))
    }
}

@Composable
private fun ZipBody(z: Loaded.ZipList) {
    val t = LocalChatTheme.current
    val c = t.colors
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = t.dp("previewPadH"), vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                "里面共 ${z.total}${if (z.total >= 20000) "+" else ""} 项",
                color = c.sub,
                fontSize = t.sp("previewSubFont"),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(z.items) { _, item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    item.name,
                    color = if (item.isDir) c.sub else c.text,
                    fontSize = t.sp("previewTextFont"),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!item.isDir && item.size >= 0) {
                    Text(formatSize(item.size), color = c.sub, fontSize = t.sp("previewSubFont"), maxLines = 1)
                }
            }
        }
        if (z.more) {
            item { TruncatedNote("只列出了前面一部分") }
        }
    }
}

// ───────────────────────── PDF ─────────────────────────

private class PdfDoc(val fd: ParcelFileDescriptor, val renderer: PdfRenderer) {
    val lock = Mutex()
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        try {
            val page = renderer.openPage(index)
            try {
                val w = widthPx.coerceAtLeast(1)
                val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bm.eraseColor(android.graphics.Color.WHITE)
                page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bm
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    suspend fun close() = lock.withLock {
        try { renderer.close() } catch (e: Exception) { }
        try { fd.close() } catch (e: Exception) { }
    }
}

private sealed interface PdfOpen {
    object Loading : PdfOpen
    class Fail(val reason: String) : PdfOpen
    class Ok(val doc: PdfDoc) : PdfOpen
}

private fun openPdf(context: Context, uri: String): PdfOpen {
    if (uri.isBlank()) return PdfOpen.Fail("这是演示用的文件，没有内容")
    return try {
        val fd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
            ?: return PdfOpen.Fail("这个文件现在打不开了")
        try {
            val renderer = PdfRenderer(fd)
            if (renderer.pageCount == 0) {
                renderer.close()
                fd.close()
                PdfOpen.Fail("这个 PDF 没有页面")
            } else {
                PdfOpen.Ok(PdfDoc(fd, renderer))
            }
        } catch (e: Exception) {
            try { fd.close() } catch (e2: Exception) { }
            PdfOpen.Fail("这个 PDF 打不开（可能有密码，或已损坏）")
        }
    } catch (e: Exception) {
        PdfOpen.Fail("这个文件现在打不开了")
    }
}

@Composable
private fun PdfBody(file: Attachment, onOpenExternal: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val context = LocalContext.current
    val opened by produceState<PdfOpen>(PdfOpen.Loading, file.uri) {
        value = withContext(Dispatchers.IO) { openPdf(context, file.uri) }
    }
    when (val o = opened) {
        is PdfOpen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在读取…", color = c.sub, fontSize = t.sp("previewInfoSub"))
        }
        is PdfOpen.Fail -> InfoBody(file, o.reason, onOpenExternal)
        is PdfOpen.Ok -> {
            val doc = o.doc
            DisposableEffect(doc) {
                onDispose { CoroutineScope(Dispatchers.IO).launch { doc.close() } }
            }
            val listState = rememberLazyListState()
            val density = LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxSize().background(c.line)) {
                val padH = t.dp("previewPdfPadH")
                val widthPx = with(density) { ((maxWidth - padH * 2).toPx() * t.f("previewPdfScale")).toInt() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = padH, vertical = t.dp("previewPdfGap")),
                    verticalArrangement = Arrangement.spacedBy(t.dp("previewPdfGap")),
                ) {
                    items(doc.pageCount, key = { it }) { index ->
                        PdfPage(doc, index, widthPx)
                    }
                }
                if (doc.pageCount > 1) {
                    Text(
                        "${listState.firstVisibleItemIndex + 1} / ${doc.pageCount}",
                        color = c.chatBg,
                        fontSize = t.sp("previewBadgeFont"),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .clip(CircleShape)
                            .background(c.text)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int) {
    val bmp by produceState<Bitmap?>(null, doc, index, widthPx) {
        value = withContext(Dispatchers.IO) { doc.render(index, widthPx) }
    }
    val b = bmp
    if (b == null) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White))
    } else {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = "第 ${index + 1} 页",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

// ───────────────────────── 信息页 ─────────────────────────

@Composable
private fun InfoBody(file: Attachment, note: String?, onOpenExternal: () -> Unit) {
    val t = LocalChatTheme.current
    val c = t.colors
    val boxShape = RoundedCornerShape(t.dp("previewInfoRadius"))
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(t.dp("previewInfoBox"))
                .clip(boxShape)
                .background(c.bubbleAi)
                .border(1.dp, c.bubbleAiLine, boxShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                fileIconPath(fileKind(file.name)),
                c.accent,
                t.dp("previewInfoIcon"),
                strokeWidth = t.f("iconStroke"),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            file.name,
            color = c.text,
            fontSize = t.sp("previewInfoName"),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            typeLabel(file) + if (file.sizeBytes >= 0) " · " + formatSize(file.sizeBytes) else "",
            color = c.sub,
            fontSize = t.sp("previewInfoSub"),
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .height(t.dp("previewBtnHeight"))
                .clip(CircleShape)
                .background(c.accent)
                .clickable(role = Role.Button, onClick = onOpenExternal)
                .padding(horizontal = t.dp("previewBtnPadH")),
            contentAlignment = Alignment.Center,
        ) {
            Text("用其他应用打开", color = c.onAccent, fontSize = t.sp("previewBtnFont"))
        }
        if (note != null) {
            Spacer(Modifier.height(12.dp))
            Text(note, color = c.dot, fontSize = t.sp("previewSubFont"), textAlign = TextAlign.Center)
        }
    }
}

// 两个只在预览页用到的线条图标（24×24 坐标，和 AppIcons 同一种画法）
private const val ARROW_LEFT = "M19 12H5M12 19l-7-7 7-7"
private const val EXTERNAL_LINK = "M14 4h6v6M20 4l-9 9M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4"
