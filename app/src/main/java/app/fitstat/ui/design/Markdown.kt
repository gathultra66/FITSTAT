package app.fitstat.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fonts
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Bullet(val indent: Int, val text: String) : MdBlock
    data class Numbered(val indent: Int, val n: String, val text: String) : MdBlock
    data class Code(val lang: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val headingRe = Regex("^(#{1,4})\\s+(.*)$")
private val bulletRe = Regex("^(\\s*)[-*•]\\s+(.*)$")
private val numberRe = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val ruleRe = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val tableSepRe = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

private fun splitRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.substring(1)
    if (t.endsWith("|")) t = t.dropLast(1)
    return t.split("|").map { it.trim() }
}

internal fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotEmpty()) { out.add(MdBlock.Para(para.toString().trimEnd())); para.setLength(0) }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flush()
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            out.add(MdBlock.Code(lang, code.toString()))
            i++
            continue
        }
        if (trimmed.isEmpty()) { flush(); i++; continue }
        if (trimmed.startsWith("|") && i + 1 < lines.size && tableSepRe.matches(lines[i + 1].trim()) && lines[i + 1].contains("-")) {
            flush()
            val header = splitRow(trimmed)
            val rows = ArrayList<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(splitRow(lines[i]))
                i++
            }
            out.add(MdBlock.Table(header, rows))
            continue
        }
        val h = headingRe.matchEntire(trimmed)
        if (h != null) { flush(); out.add(MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim())); i++; continue }
        if (ruleRe.matches(trimmed)) { flush(); out.add(MdBlock.Rule); i++; continue }
        if (trimmed.startsWith(">")) { flush(); out.add(MdBlock.Quote(trimmed.removePrefix(">").trim())); i++; continue }
        val b = bulletRe.matchEntire(line)
        if (b != null) { flush(); out.add(MdBlock.Bullet(b.groupValues[1].length / 2, b.groupValues[2])); i++; continue }
        val nm = numberRe.matchEntire(line)
        if (nm != null) { flush(); out.add(MdBlock.Numbered(nm.groupValues[1].length / 2, nm.groupValues[2], nm.groupValues[3])); i++; continue }
        if (para.isNotEmpty()) para.append('\n')
        para.append(trimmed)
        i++
    }
    flush()
    return out
}

private val inlineRe = Regex(
    "(`[^`\\n]+`)|(\\*\\*[^*\\n]+?\\*\\*)|(__[^_\\n]+?__)|(~~[^~\\n]+?~~)|(\\[[^\\]\\n]+\\]\\([^)\\s]+\\))|(\\*[^*\\s][^*\\n]*?\\*)|((?<![A-Za-z0-9])_[^_\\s][^_\\n]*?_(?![A-Za-z0-9]))",
)

internal fun inlineMarkdown(text: String, codeBg: Color, codeFg: Color, linkColor: Color, mono: androidx.compose.ui.text.font.FontFamily): AnnotatedString {
    fun build(sb: AnnotatedString.Builder, s: String) {
        var last = 0
        for (m in inlineRe.findAll(s)) {
            if (m.range.first > last) sb.append(s.substring(last, m.range.first))
            val g = m.value
            when {
                g.startsWith("`") -> sb.withStyle(SpanStyle(fontFamily = mono, background = codeBg, color = codeFg)) { append(g.substring(1, g.length - 1)) }
                g.startsWith("**") || g.startsWith("__") -> sb.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { build(this, g.substring(2, g.length - 2)) }
                g.startsWith("~~") -> sb.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { build(this, g.substring(2, g.length - 2)) }
                g.startsWith("[") -> {
                    val close = g.indexOf("](")
                    val label = g.substring(1, close)
                    val url = g.substring(close + 2, g.length - 1)
                    sb.withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) { append(label) }
                }
                else -> sb.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { build(this, g.substring(1, g.length - 1)) }
            }
            last = m.range.last + 1
        }
        if (last < s.length) sb.append(s.substring(last))
    }
    return buildAnnotatedString { build(this, text) }
}

/** Texto con formato (Markdown básico): títulos, listas, negritas, código, tablas y citas. */
@Composable
fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Fs.colors.text,
    style: TextStyle = Fs.type.body,
) {
    val c = Fs.colors
    val blocks = remember(text) { parseMarkdown(text) }
    val mono = Fonts.mono()
    val codeBg = c.surface3.a(0.9f)
    fun inl(s: String) = inlineMarkdown(s, codeBg, c.accentText, c.accentText, mono)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Heading -> Txt(inl(b.text), style = when (b.level) { 1 -> Fs.type.h1; 2 -> Fs.type.h2; else -> Fs.type.h3 }, color = color)
                is MdBlock.Para -> Txt(inl(b.text), style = style, color = color)
                is MdBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 14).dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.padding(top = (style.fontSize.value * 0.62f).dp).size(5.dp).clip(Fs.shapes.round).background(c.accent))
                    Txt(inl(b.text), Modifier.weight(1f), style, color)
                }
                is MdBlock.Numbered -> Row(Modifier.padding(start = (b.indent * 14).dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Txt(b.n + ".", Modifier.width(22.dp), style.copy(fontWeight = FontWeight.Bold), c.accentText, maxLines = 1)
                    Txt(inl(b.text), Modifier.weight(1f), style, color)
                }
                is MdBlock.Quote -> Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(Fs.shapes.pill).background(c.accent.a(0.6f)))
                    Txt(inl(b.text), Modifier.weight(1f), style, c.textDim)
                }
                is MdBlock.Code -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(codeBg).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    Txt(b.text, style = Fs.type.mono, color = c.text, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
                }
                is MdBlock.Table -> MdTable(b, ::inl, style, color)
                MdBlock.Rule -> HairLine(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table, inl: (String) -> AnnotatedString, style: TextStyle, color: Color) {
    val c = Fs.colors
    val cols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val small = style.copy(fontSize = style.fontSize * 0.9f, lineHeight = style.lineHeight * 0.9f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface3.a(0.45f))) {
        Row(Modifier.fillMaxWidth().background(c.accent.a(0.14f)).padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until cols) Txt(inl(t.header.getOrElse(i) { "" }), Modifier.weight(1f), small.copy(fontWeight = FontWeight.Bold), c.accentText)
        }
        t.rows.forEachIndexed { r, row ->
            Row(
                Modifier.fillMaxWidth().background(if (r % 2 == 1) c.text.a(0.03f) else Color.Transparent).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0 until cols) Txt(inl(row.getOrElse(i) { "" }), Modifier.weight(1f), small, color)
            }
        }
    }
}
