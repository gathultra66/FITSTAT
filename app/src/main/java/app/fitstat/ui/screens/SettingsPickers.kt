package app.fitstat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.data.Opt
import app.fitstat.data.Settings
import app.fitstat.data.SettingSpec
import app.fitstat.ui.design.BigNumber
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.Card
import app.fitstat.ui.design.ChartPoint
import app.fitstat.ui.design.ChunkyProgress
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.FsTextField
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.IconBadge
import app.fitstat.ui.design.Label
import app.fitstat.ui.design.LedText
import app.fitstat.ui.design.Overlays
import app.fitstat.ui.design.Pill
import app.fitstat.ui.design.StatChart
import app.fitstat.ui.design.StatTile
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fonts
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.Palettes
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import app.fitstat.ui.theme.parseHexColor
import app.fitstat.ui.theme.systemAccent
import app.fitstat.ui.theme.toHex
import kotlin.math.max
import kotlin.math.min

// ───────────────────────── Vista previa viva

/** Un trozo de la app que se redibuja con los ajustes actuales. */
@Composable
internal fun StylePreview() {
    val c = Fs.colors
    Column(verticalArrangement = Arrangement.spacedBy(Fs.ui.pad(12))) {
        Card(accent = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("VISTA PREVIA", color = c.onAccentDim)
                    BigNumber("1,248", Modifier.padding(top = 4.dp), color = c.onAccent)
                    Txt("kg levantados esta semana", style = Fs.type.small, color = c.onAccentDim, maxLines = 1)
                }
                FitBot(size = 64.dp, animated = false)
            }
            ChunkyProgress(0.72f, Modifier.padding(top = 14.dp), track = c.onAccent.a(0.2f), fill = Brush.horizontalGradient(listOf(c.onAccent.a(0.85f), c.onAccent)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Fs.ui.pad(12))) {
            StatTile("Racha", "6", Modifier.weight(1f), unit = "semanas")
            StatTile("Récords", "12", Modifier.weight(1f), unit = "este mes")
        }
        Card {
            StatChart(samplePoints, height = 130.dp)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Pecho", selected = true)
                Pill("Pierna")
                Spacer(Modifier.weight(1f))
                IconBadge(Ic.Dumbbell, size = 38.dp)
                IconBadge(Ic.Flame, size = 38.dp)
            }
            FsButton("Botón principal", {}, Modifier.padding(top = 12.dp).fillMaxWidth(), icon = Ic.Play, height = 46.dp)
        }
    }
}

internal val samplePoints = listOf(
    ChartPoint("L", 4.0), ChartPoint("M", 7.0), ChartPoint("X", 5.0), ChartPoint("J", 9.0),
    ChartPoint("V", 6.0), ChartPoint("S", 11.0, record = true), ChartPoint("D", 8.0),
)

// ───────────────────────── Mosaicos con vista previa

@Composable
internal fun StyleTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp = 62.dp,
    preview: @Composable BoxScope.() -> Unit,
) {
    val c = Fs.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.tappable(onClick, pressScale = 0.96f).clip(shape)
            .background(if (selected) c.accent.a(0.12f) else c.surface2)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline.a(0.7f), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(12.dp)).background(c.bg), contentAlignment = Alignment.Center, content = preview)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            if (selected) Icon(Ic.Check, size = 13.dp, tint = c.accentText, style = "line", weight = 3f)
            Txt(label, style = Fs.type.caption, color = if (selected) c.accentText else c.textDim, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Cuadrícula de mosaicos de una opción de ajustes. */
@Composable
internal fun StyleTiles(spec: SettingSpec, options: List<Opt>, value: String, columns: Int, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in row) {
                    StyleTile(o.label.substringBefore(" ("), o.value == value, { onSelect(o.value) }, Modifier.weight(1f)) {
                        StylePreviewFor(spec.key, o.value)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Gráficas: una fila que se desliza con una muestra de cada estilo. */
@Composable
internal fun ChartTiles(options: List<Opt>, value: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            StyleTile(o.label, o.value == value, { onSelect(o.value) }, Modifier.width(150.dp), previewHeight = 92.dp) {
                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                    StatChart(samplePoints, Modifier.fillMaxSize(), style = o.value, height = 92.dp, compact = true)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.StylePreviewFor(key: String, id: String) {
    val c = Fs.colors
    when (key) {
        "theme.mode" -> Canvas(Modifier.fillMaxSize()) {
            fun draw(bg: Color, card: Color, x0: Float, x1: Float) {
                val w = x1 - x0
                drawRect(bg, Offset(x0, 0f), Size(w, size.height))
                drawRoundRect(card, Offset(x0 + w * 0.12f, size.height * 0.2f), Size(w * 0.76f, size.height * 0.28f), CornerRadius(8.dp.toPx()))
                drawRoundRect(c.accent, Offset(x0 + w * 0.12f, size.height * 0.58f), Size(w * 0.46f, size.height * 0.16f), CornerRadius(6.dp.toPx()))
            }
            when (id) {
                "dark" -> draw(Color(0xFF0D0D11), Color(0xFF202027), 0f, size.width)
                "light" -> draw(Color(0xFFE8E8EC), Color.White, 0f, size.width)
                "amoled" -> draw(Color.Black, Color(0xFF17171C), 0f, size.width)
                else -> {
                    draw(Color(0xFF0D0D11), Color(0xFF202027), 0f, size.width / 2f)
                    draw(Color(0xFFE8E8EC), Color.White, size.width / 2f, size.width)
                }
            }
        }
        "theme.background" -> Canvas(Modifier.fillMaxSize()) {
            when (id) {
                "gradient" -> {
                    drawRect(Brush.radialGradient(listOf(c.accent.a(0.45f), Color.Transparent), Offset(size.width * 0.9f, 0f), size.width * 0.9f))
                    drawRect(Brush.radialGradient(listOf(c.accent2.a(0.22f), Color.Transparent), Offset(0f, size.height), size.width * 0.7f))
                }
                "dots" -> {
                    val step = 9.dp.toPx()
                    var y = step / 2
                    while (y < size.height) {
                        var x = step / 2
                        while (x < size.width) { drawCircle(c.text.a(0.28f), 1.1.dp.toPx(), Offset(x, y)); x += step }
                        y += step
                    }
                }
                "grid" -> {
                    val step = 12.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(c.text.a(0.16f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                    var y = 0f
                    while (y < size.height) { drawLine(c.text.a(0.16f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                }
                else -> {}
            }
        }
        "shape.radius" -> {
            val k = when (id) { "sharp" -> 0.3f; "soft" -> 0.7f; "extra" -> 1.3f; else -> 1f }
            Box(Modifier.size(width = 78.dp, height = 40.dp).clip(RoundedCornerShape((20 * k).dp)).background(c.accent.a(0.28f)).border(2.dp, c.accent, RoundedCornerShape((20 * k).dp)))
        }
        "shape.card" -> {
            val shape = RoundedCornerShape(14.dp)
            val m = Modifier.size(width = 78.dp, height = 42.dp)
            when (id) {
                "flat" -> Box(m.clip(shape).background(c.surface2))
                "outline" -> Box(m.clip(shape).background(c.surface).border(1.5.dp, c.outline, shape))
                "glass" -> Box(m.clip(shape).background(c.surface.a(0.5f)).border(1.dp, Brush.linearGradient(listOf(Color.White.a(0.5f), Color.White.a(0.05f))), shape))
                else -> Box(m.shadow(if (c.dark) 0.dp else 5.dp, shape).clip(shape).background(c.surface).border(1.dp, Brush.verticalGradient(listOf(Color.White.a(if (c.dark) 0.2f else 0.9f), Color.Transparent)), shape))
            }
        }
        "shape.button" -> {
            val shape = when (id) { "square" -> RoundedCornerShape(4.dp); "rounded" -> RoundedCornerShape(10.dp); else -> CircleShape }
            Box(Modifier.size(width = 70.dp, height = 28.dp).clip(shape).background(Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.12f), c.accent2))))
        }
        "shape.density" -> {
            val gap = when (id) { "compact" -> 3.dp; "roomy" -> 11.dp; else -> 6.dp }
            Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(3) { Box(Modifier.size(width = 64.dp, height = 8.dp).clip(CircleShape).background(c.accent.a(if (it == 0) 0.9f else 0.4f))) }
            }
        }
        "shape.thumbs" -> {
            val s = when (id) { "small" -> 22.dp; "large" -> 40.dp; else -> 30.dp }
            Box(Modifier.size(s).clip(RoundedCornerShape(s / 3)).background(Brush.linearGradient(listOf(c.accent, c.accent2))), contentAlignment = Alignment.Center) {
                Icon(Ic.Dumbbell, size = s * 0.55f, tint = c.onAccent)
            }
        }
        "shape.icon_style" -> Icon(Ic.Dumbbell, size = 34.dp, tint = c.accent, style = id, weight = 2f)
        "shape.icon_container" -> IconBadge(Ic.Dumbbell, size = 42.dp, container = id)
        "type.digits" -> when (id) {
            "led" -> LedText("1248", dot = 2.3.dp, gap = 0.9.dp, lit = c.accent)
            "mono" -> Txt("1,248", style = Fs.type.h1.copy(fontFamily = Fonts.mono()), color = c.text, maxLines = 1)
            else -> Txt("1,248", style = Fs.type.h1, color = c.text, maxLines = 1)
        }
        "home.nav" -> Canvas(Modifier.fillMaxSize()) {
            if (id == "docked") {
                drawRect(c.surface2, Offset(0f, size.height * 0.68f), Size(size.width, size.height * 0.32f))
                drawRect(c.outline, Offset(0f, size.height * 0.68f), Size(size.width, 1.dp.toPx()))
                for (i in 0 until 5) drawCircle(if (i == 0) c.accent else c.textFaint, 3.dp.toPx(), Offset(size.width * (0.14f + 0.18f * i), size.height * 0.84f))
            } else {
                val r = CornerRadius(size.height * 0.16f)
                drawRoundRect(c.surface2, Offset(size.width * 0.1f, size.height * 0.62f), Size(size.width * 0.8f, size.height * 0.28f), r)
                drawRoundRect(c.accent, Offset(size.width * 0.13f, size.height * 0.66f), Size(size.width * 0.22f, size.height * 0.2f), r)
                for (i in 1 until 5) drawCircle(c.textFaint, 3.dp.toPx(), Offset(size.width * (0.28f + 0.14f * i), size.height * 0.76f))
            }
        }
        else -> Txt(id, style = Fs.type.caption, color = c.textDim)
    }
}

// ───────────────────────── Paletas

@Composable
internal fun PaletteGrid(current: String, customHex: String, onSelect: (String) -> Unit) {
    val c = Fs.colors
    val ctx = LocalContext.current
    val sys = remember { systemAccent(ctx) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in Palettes.all) {
            PaletteCell(p.label.substringBefore(' '), current == p.id) {
                Swatch(Brush.linearGradient(listOf(p.accent, p.accent2)), current == p.id, { onSelect(p.id) })
            }
        }
        PaletteCell("Sistema", current == "sistema") {
            val col = sys ?: c.accent
            Swatch(Brush.linearGradient(listOf(col, col.mix(Color.Black, 0.25f))), current == "sistema", { onSelect("sistema") }) {
                Icon(Ic.Phone, size = 18.dp, tint = if (col.luminance() > 0.4f) Color(0xFF14111F) else Color.White)
            }
        }
        PaletteCell("Libre", current == "custom") {
            val col = parseHexColor(customHex) ?: c.accent
            Box(
                Modifier.size(46.dp).tappable({ onSelect("custom") }, pressScale = 0.9f).clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    .then(if (current == "custom") Modifier.border(3.dp, c.text, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(col).border(2.dp, Color.White, CircleShape))
            }
        }
    }
}

@Composable
private fun PaletteCell(label: String, selected: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        // Nombres como «Mandarina» o «Monocromo» pueden ser más anchos que la celda: se dejan salir hacia los lados.
        Box(Modifier.width(52.dp).wrapContentWidth(unbounded = true)) {
            Txt(label, style = Fs.type.caption, color = if (selected) Fs.colors.accentText else Fs.colors.textFaint, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun Swatch(brush: Brush, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit = {}) {
    val c = Fs.colors
    Box(
        Modifier.size(46.dp).tappable(onClick, pressScale = 0.9f).clip(CircleShape).background(brush)
            .then(if (selected) Modifier.border(3.dp, c.text, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Ic.Check, size = 22.dp, tint = Color.White, style = "line", weight = 3f) else content()
    }
}

// ───────────────────────── Tipografías

@Composable
internal fun FontList(options: List<Opt>, current: String, onSelect: (String) -> Unit) {
    val c = Fs.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            val fam = Fonts.family(o.value)
            val sel = o.value == current
            Row(
                Modifier.fillMaxWidth().tappable({ onSelect(o.value) }, pressScale = 0.985f).clip(Fs.shapes.inner)
                    .background(if (sel) c.accent.a(0.14f) else c.surface2)
                    .border(if (sel) 2.dp else 1.dp, if (sel) c.accent else c.outline.a(0.6f), Fs.shapes.inner)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Txt("Aa", Modifier.width(44.dp), Fs.type.title.copy(fontFamily = fam), if (sel) c.accentText else c.text, maxLines = 1, softWrap = false)
                Column(Modifier.weight(1f)) {
                    Txt(o.label, style = Fs.type.bodyStrong.copy(fontFamily = fam), maxLines = 1)
                    Txt("Entrena fuerte, descansa mejor · 0123456789", style = Fs.type.small.copy(fontFamily = fam), color = c.textDim, maxLines = 1)
                }
                if (sel) Icon(Ic.Check, size = 20.dp, tint = c.accentText, style = "line", weight = 2.6f)
            }
        }
    }
}

// ───────────────────────── Selector de color libre

private fun rgbToHsv(col: Color): FloatArray {
    val r = col.red
    val g = col.green
    val b = col.blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
}

@Composable
private fun GradientSlider(value: Float, onChange: (Float) -> Unit, colors: List<Color>, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val cur by rememberUpdatedState(onChange)
    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val pad = 14.dp.toPx()
                    cur(((o.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val pad = 14.dp.toPx()
                    cur(((change.position.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f))
                }
            },
    ) {
        val pad = 14.dp.toPx()
        val h = 14.dp.toPx()
        val cy = size.height / 2f
        drawRoundRect(Brush.horizontalGradient(colors, pad, size.width - pad), Offset(pad, cy - h / 2), Size(size.width - 2 * pad, h), CornerRadius(h / 2))
        val x = pad + (size.width - 2 * pad) * value.coerceIn(0f, 1f)
        drawCircle(Color.Black.a(0.25f), 13.dp.toPx(), Offset(x, cy + 1.dp.toPx()))
        drawCircle(Color.White, 12.dp.toPx(), Offset(x, cy))
        drawCircle(c.bg, 12.dp.toPx(), Offset(x, cy), style = Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun ColorPickerBody(title: String, initial: Color, onApply: (String) -> Unit, onCancel: () -> Unit) {
    val c = Fs.colors
    val hsv0 = remember { rgbToHsv(initial) }
    var h by remember { mutableFloatStateOf(hsv0[0]) }
    var s by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    val color = Color.hsv(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    var hexText by remember { mutableStateOf(color.toHex()) }
    var editingHex by remember { mutableStateOf(false) }
    val shown = if (editingHex) hexText else color.toHex()

    SheetBody(title, subtitle = "Mueve los controles o escribe el código del color") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(color).border(1.dp, c.outline, RoundedCornerShape(22.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("CÓDIGO HEX")
                FsTextField(
                    shown, {
                        editingHex = true
                        hexText = it.uppercase().take(7)
                        parseHexColor(hexText)?.let { col ->
                            val x = rgbToHsv(col)
                            h = x[0]; s = x[1]; v = x[2]
                        }
                    },
                    placeholder = "#9B8CF5",
                )
            }
        }
        Label("Tono")
        GradientSlider(h / 360f, { h = it * 360f; editingHex = false }, listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
        Label("Intensidad")
        GradientSlider(s, { s = it; editingHex = false }, listOf(Color.hsv(h.coerceIn(0f, 359.9f), 0f, v.coerceAtLeast(0.2f)), Color.hsv(h.coerceIn(0f, 359.9f), 1f, v.coerceAtLeast(0.2f))))
        Label("Brillo")
        GradientSlider(v, { v = it; editingHex = false }, listOf(Color.Black, Color.hsv(h.coerceIn(0f, 359.9f), s, 1f)))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FsButton("Cancelar", onCancel, Modifier.weight(1f), kind = BtnKind.Secondary, height = 48.dp)
            FsButton("Aplicar", { onApply(color.toHex()) }, Modifier.weight(1f), height = 48.dp)
        }
    }
}

fun Overlays.pickColor(title: String, initial: Color, onPick: (String) -> Unit) {
    sheet { close -> ColorPickerBody(title, initial, { onPick(it); close() }, close) }
}
