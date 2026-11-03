package app.fitstat.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.data.Fmt
import app.fitstat.data.Stats
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class ChartPoint(val label: String, val value: Double, val record: Boolean = false, val note: String? = null)

/** Valor "redondo" por encima del máximo, para que la escala se vea limpia. */
fun niceMax(v: Double): Double {
    if (v <= 0.0) return 1.0
    val exp = Math.floor(Math.log10(v))
    val f = v / 10.0.pow(exp)
    val nf = when {
        f <= 1.0 -> 1.0
        f <= 1.5 -> 1.5
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 3.0 -> 3.0
        f <= 4.0 -> 4.0
        f <= 5.0 -> 5.0
        f <= 7.5 -> 7.5
        else -> 10.0
    }
    return nf * 10.0.pow(exp)
}

private fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val ang = -PI / 2 + i * PI / 5
        val x = cx + (rad * cos(ang)).toFloat()
        val y = cy + (rad * sin(ang)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.bubble(tm: TextMeasurer, lines: List<String>, anchor: Offset, style: TextStyle, bg: Color, border: Color, fg: Color, dim: Color) {
    val layouts = lines.mapIndexed { i, s -> tm.measure(s, if (i == 0) style.copy(color = fg) else style.copy(color = dim)) }
    val padX = 10.dp.toPx()
    val padY = 6.dp.toPx()
    val w = layouts.maxOf { it.size.width } + padX * 2
    val h = layouts.sumOf { it.size.height } + padY * 2
    var x = anchor.x - w / 2f
    x = x.coerceIn(2.dp.toPx(), size.width - w - 2.dp.toPx())
    var y = anchor.y - h - 8.dp.toPx()
    if (y < 0f) y = anchor.y + 10.dp.toPx()
    drawRoundRect(bg, Offset(x, y), Size(w, h), CornerRadius(10.dp.toPx()))
    drawRoundRect(border, Offset(x, y), Size(w, h), CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
    var cy = y + padY
    for (l in layouts) {
        drawText(l, topLeft = Offset(x + (w - l.size.width) / 2f, cy))
        cy += l.size.height
    }
}

/**
 * Gráfica de estadísticas. Según el ajuste 'Forma de mostrar las gráficas' se dibuja como
 * barras, línea, área, puntos LED o anillos. Se puede tocar o arrastrar para ver cada valor.
 */
@Composable
fun StatChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    style: String = Fs.ui.chartStyle,
    height: Dp = 180.dp,
    format: (Double) -> String = { Fmt.num(it) },
    unit: String = "",
    color: Color = Fs.colors.accent,
    maxValue: Double? = null,
    emptyText: String = "Aún no hay datos",
    /** Sin ejes, textos ni interacción: para vistas previas pequeñas. */
    compact: Boolean = false,
) {
    val c = Fs.colors
    val ui = Fs.ui
    val m = Fs.motion
    val tm = rememberTextMeasurer(cacheSize = 80)
    val sig = remember(points) { points.fold(points.size + 17) { h, p -> h * 31 + p.value.hashCode() } }
    val anim = remember(sig, style) { Animatable(if (m.enabled) 0f else 1f) }
    LaunchedEffect(sig, style, m.enabled) {
        if (m.enabled) {
            anim.snapTo(0f)
            anim.animateTo(1f, m.tween(950))
        } else anim.snapTo(1f)
    }
    var sel by remember(sig) { mutableIntStateOf(-1) }
    val n = points.size
    val labelStyle = Fs.type.caption.copy(color = c.textFaint)
    val valueStyle = Fs.type.caption.copy(color = c.textDim)
    val tipStyle = Fs.type.smallStrong
    val smallStyle = Fs.type.small.copy(color = c.textFaint)
    val grid = ui.grid
    val showValues = ui.values
    val showRecords = ui.records
    val col2 = if (color == c.accent) c.accent2 else color.mix(Color.Black, 0.2f)
    val glowK = ui.glow

    Canvas(
        modifier.fillMaxWidth().height(height)
            .pointerInput(n, compact) {
                if (compact) return@pointerInput
                detectTapGestures { o ->
                    if (n == 0) return@detectTapGestures
                    val pad = 6.dp.toPx()
                    val slot = (size.width - 2 * pad) / n
                    val i = ((o.x - pad) / slot).toInt().coerceIn(0, n - 1)
                    sel = if (sel == i) -1 else i
                }
            }
            .pointerInput(n, compact) {
                if (compact) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    if (n == 0) return@detectHorizontalDragGestures
                    change.consume()
                    val pad = 6.dp.toPx()
                    val slot = (size.width - 2 * pad) / n
                    sel = ((change.position.x - pad) / slot).toInt().coerceIn(0, n - 1)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val padL = 6.dp.toPx()
        val padR = 6.dp.toPx()
        val padT = (if (compact) 6.dp else 24.dp).toPx()
        val padB = (if (compact) 6.dp else 22.dp).toPx()
        val pw = w - padL - padR
        val ph = h - padT - padB
        val baseY = padT + ph

        if (n == 0) {
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            drawLine(c.outline, Offset(padL, baseY), Offset(w - padR, baseY), 1.5.dp.toPx(), pathEffect = dash)
            val t = tm.measure(emptyText, smallStyle)
            drawText(t, topLeft = Offset((w - t.size.width) / 2f, padT + ph / 2f - t.size.height / 2f))
            return@Canvas
        }

        val maxV = niceMax(maxValue ?: points.maxOf { it.value })
        val slot = pw / n
        fun cx(i: Int) = padL + slot * (i + 0.5f)
        val ease = FastOutSlowInEasing
        fun prog(i: Int): Float {
            val t = ((anim.value - 0.35f * i / max(n, 1)) / 0.65f).coerceIn(0f, 1f)
            return ease.transform(t)
        }
        fun yOf(v: Double, p: Float): Float = baseY - (ph * (v / maxV).toFloat().coerceIn(0f, 1f) * p)

        // Líneas de guía
        if (grid) {
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
            for (k in 0..2) {
                val y = baseY - ph * k / 2f
                drawLine(c.outline.a(if (k == 0) 0.9f else 0.55f), Offset(padL, y), Offset(w - padR, y), 1.dp.toPx(), pathEffect = if (k == 0) null else dash)
            }
            if (!compact) {
                val t = tm.measure(format(maxV), labelStyle)
                drawText(t, topLeft = Offset(padL + 2.dp.toPx(), padT - t.size.height - 2.dp.toPx() + 0f))
            }
        }

        // Etiquetas del eje X: se reparten y se omite la que se solape con otra (los extremos tienen prioridad).
        val wanted: List<Int> = when {
            n <= 1 -> listOf(0)
            n <= 6 && slot > 34.dp.toPx() -> (0 until n).toList()
            n <= 12 && slot > 40.dp.toPx() -> (0 until n).toList()
            else -> listOf(0, n - 1, (n - 1) / 2).distinct()
        }
        val placed = ArrayList<ClosedFloatingPointRange<Float>>()
        val minGap = 3.dp.toPx()
        for (i in (if (compact) emptyList<Int>() else wanted)) {
            val lbl = tm.measure(points[i].label, labelStyle)
            val x = (cx(i) - lbl.size.width / 2f).coerceIn(0f, (w - lbl.size.width).coerceAtLeast(0f))
            val span = x..(x + lbl.size.width)
            if (placed.any { span.start < it.endInclusive + minGap && span.endInclusive > it.start - minGap }) continue
            placed += span
            drawText(lbl, topLeft = Offset(x, baseY + 5.dp.toPx()))
        }

        val dim = if (sel >= 0) 0.42f else 1f

        when (style) {
            "line", "area" -> {
                val xs = FloatArray(n) { cx(it) }
                val ys = FloatArray(n) { yOf(points[it].value, prog(it)) }
                val path = Path()
                if (n == 1) {
                    path.moveTo(xs[0] - 12.dp.toPx(), ys[0]); path.lineTo(xs[0] + 12.dp.toPx(), ys[0])
                } else {
                    path.moveTo(xs[0], ys[0])
                    for (i in 1 until n) {
                        val mx = (xs[i - 1] + xs[i]) / 2f
                        path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
                    }
                }
                if (style == "area" && n > 1) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xs[n - 1], baseY); lineTo(xs[0], baseY); close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(color.a(0.38f), color.a(0.02f)), padT, baseY))
                }
                drawPath(path, Brush.horizontalGradient(listOf(col2, color), padL, w - padR), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                for (i in 0 until n) {
                    val last = i == n - 1
                    val on = i == sel
                    val r = (if (on) 6.dp else if (last) 5.dp else 3.5.dp).toPx()
                    if ((last || on) && glowK > 0.2f) drawCircle(color.a(0.25f * glowK), r * 2.2f, Offset(xs[i], ys[i]))
                    drawCircle(c.surface, r + 1.5.dp.toPx(), Offset(xs[i], ys[i]))
                    drawCircle(if (points[i].record && showRecords) c.warn else color, r, Offset(xs[i], ys[i]))
                }
                if (sel >= 0) drawLine(color.a(0.35f), Offset(xs[sel], padT), Offset(xs[sel], baseY), 1.5.dp.toPx())
            }
            "dots" -> {
                val rows = 9
                val rowH = ph / rows
                val r = min(slot * 0.3f, rowH * 0.36f).coerceAtLeast(2.dp.toPx())
                for (i in 0 until n) {
                    val lit = Math.round((rows * (points[i].value / maxV).toFloat().coerceIn(0f, 1f) * prog(i)).toDouble()).toInt()
                    val a = if (sel < 0 || sel == i) 1f else dim
                    for (rr in 0 until rows) {
                        val y = baseY - rowH * (rr + 0.5f)
                        val on = rr < lit
                        val top = on && rr == lit - 1
                        if (on) {
                            if (top && glowK > 0.2f) drawCircle(color.a(0.28f * glowK * a), r * 2.0f, Offset(cx(i), y))
                            drawCircle((if (top) color.mix(Color.White, 0.25f) else color).a(a), r, Offset(cx(i), y))
                        } else drawCircle(c.ledOff, r, Offset(cx(i), y))
                    }
                }
            }
            "ring" -> {
                val take = min(n, 8)
                val start = n - take
                val sl = pw / take
                val d = min(sl * 0.84f, ph * 0.98f)
                val sw = (d * 0.17f).coerceIn(4.dp.toPx(), 9.dp.toPx())
                for (k in 0 until take) {
                    val i = start + k
                    val ccx = padL + sl * (k + 0.5f)
                    val ccy = padT + ph / 2f
                    val rr = d / 2f - sw / 2f
                    drawCircle(c.surface3, rr, Offset(ccx, ccy), style = Stroke(sw))
                    val p = (points[i].value / maxV).toFloat().coerceIn(0f, 1f) * prog(k)
                    if (p > 0.004f) {
                        drawArc(
                            Brush.sweepGradient(listOf(col2, color, color.mix(Color.White, 0.25f)), Offset(ccx, ccy)),
                            -90f, 360f * p, false, Offset(ccx - rr, ccy - rr), Size(rr * 2, rr * 2),
                            style = Stroke(sw, cap = StrokeCap.Round),
                            alpha = if (sel < 0 || sel == i) 1f else dim,
                        )
                    }
                    val t = tm.measure(format(points[i].value), valueStyle)
                    if (!compact && t.size.width < d * 0.9f) drawText(t, topLeft = Offset(ccx - t.size.width / 2f, ccy - t.size.height / 2f))
                }
            }
            else -> { // barras
                val bw = min(slot * 0.62f, 30.dp.toPx())
                for (i in 0 until n) {
                    val p = prog(i)
                    val bh = (ph * (points[i].value / maxV).toFloat().coerceIn(0f, 1f) * p).coerceAtLeast(if (points[i].value > 0) 3.dp.toPx() * p else 0f)
                    val x = cx(i) - bw / 2f
                    val a = if (sel < 0 || sel == i) 1f else dim
                    if (points[i].value <= 0.0) {
                        drawCircle(c.surface3, 2.5.dp.toPx(), Offset(cx(i), baseY - 3.dp.toPx()))
                    } else {
                        val rec = showRecords && points[i].record
                        val top = if (rec) c.warn else color
                        drawRoundRect(
                            Brush.verticalGradient(listOf(top.a(a), (if (rec) c.warn.mix(Color.Black, 0.2f) else col2).a(a)), baseY - bh, baseY),
                            Offset(x, baseY - bh), Size(bw, bh), CornerRadius(bw * 0.4f),
                        )
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color.White.a(0.22f * a), Color.Transparent), baseY - bh, baseY - bh + bh * 0.5f),
                            Offset(x, baseY - bh), Size(bw, bh), CornerRadius(bw * 0.4f),
                        )
                        if (showValues && !compact && n <= 9 && sel < 0 && p > 0.9f) {
                            val t = tm.measure(format(points[i].value), valueStyle)
                            if (t.size.width < slot * 1.05f) drawText(t, topLeft = Offset(cx(i) - t.size.width / 2f, baseY - bh - t.size.height - 3.dp.toPx() - (if (rec) 8.dp.toPx() else 0f)))
                        }
                        if (rec && p > 0.9f) drawPath(starPath(cx(i), baseY - bh - 6.dp.toPx(), 4.5.dp.toPx()), c.warn)
                    }
                }
            }
        }

        if (sel in 0 until n) {
            val p = points[sel]
            val x = cx(sel)
            val y = when (style) {
                "line", "area" -> yOf(p.value, 1f)
                "ring" -> padT + ph / 2f - min(pw / min(n, 8) * 0.42f, ph * 0.49f)
                else -> yOf(p.value, 1f)
            }
            val ax = if (style == "ring") padL + (pw / min(n, 8)) * ((sel - (n - min(n, 8))) + 0.5f) else x
            if (style != "ring" || sel >= n - min(n, 8)) {
                bubble(
                    tm, listOfNotNull(format(p.value) + (if (unit.isNotEmpty()) " $unit" else ""), p.label + (p.note?.let { " · $it" } ?: "")),
                    Offset(ax, y), tipStyle.copy(fontSize = tipStyle.fontSize), if (c.dark) c.surface3 else Color(0xFF1B1B22),
                    c.outline, if (c.dark) c.text else Color.White, (if (c.dark) c.textDim else Color.White.a(0.7f)),
                )
            }
        }
    }
}

/** Línea mínima sin ejes, para tarjetas pequeñas. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = Fs.colors.accent, fill: Boolean = true) {
    val m = Fs.motion
    val anim = remember(values) { Animatable(if (m.enabled) 0f else 1f) }
    LaunchedEffect(values, m.enabled) { if (m.enabled) { anim.snapTo(0f); anim.animateTo(1f, m.tween(800)) } else anim.snapTo(1f) }
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(color.a(0.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), cap = StrokeCap.Round)
            return@Canvas
        }
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val pad = 3.dp.toPx()
        val xs = FloatArray(values.size) { pad + (size.width - 2 * pad) * it / (values.size - 1) }
        val ys = FloatArray(values.size) { size.height - pad - (size.height - 2 * pad) * ((values[it] - lo) / span).toFloat() * anim.value - (1f - anim.value) * 0f }
        val path = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until values.size) {
                val mx = (xs[i - 1] + xs[i]) / 2f
                cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
            }
        }
        if (fill) {
            val f = Path().apply { addPath(path); lineTo(xs.last(), size.height); lineTo(xs[0], size.height); close() }
            drawPath(f, Brush.verticalGradient(listOf(color.a(0.28f), color.a(0f))))
        }
        drawPath(path, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), Offset(xs.last(), ys.last()))
    }
}

/** Calendario de actividad (como el de GitHub): una columna por semana, un cuadro por día. */
@Composable
fun Heatmap(heat: Stats.Heat, mondayFirst: Boolean, modifier: Modifier = Modifier, today: java.time.LocalDate = java.time.LocalDate.now()) {
    val c = Fs.colors
    val m = Fs.motion
    val tm = rememberTextMeasurer(cacheSize = 60)
    val anim = remember(heat) { Animatable(if (m.enabled) 0f else 1f) }
    LaunchedEffect(heat, m.enabled) { if (m.enabled) { anim.snapTo(0f); anim.animateTo(1f, m.tween(1000)) } else anim.snapTo(1f) }
    var sel by remember(heat) { mutableIntStateOf(-1) }
    val labelStyle = Fs.type.caption.copy(color = c.textFaint)
    val tipStyle = Fs.type.smallStrong
    val dayLabels = if (mondayFirst) listOf("L", "", "X", "", "V", "", "D") else listOf("D", "", "M", "", "J", "", "S")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelW = 14.dp
        val gap = 3.dp
        val cell = ((maxWidth - labelW - gap * (heat.weeks)) / heat.weeks).coerceIn(8.dp, 26.dp)
        val total = (cell + gap) * 7 + 18.dp
        Canvas(
            Modifier.fillMaxWidth().height(total).pointerInput(heat) {
                detectTapGestures { o ->
                    val lw = 14.dp.toPx()
                    val g = 3.dp.toPx()
                    val cs = cell.toPx()
                    val top = 18.dp.toPx()
                    val wk = ((o.x - lw) / (cs + g)).toInt()
                    val dk = ((o.y - top) / (cs + g)).toInt()
                    if (wk in 0 until heat.weeks && dk in 0..6 && heat.at(wk, dk) >= 0) {
                        val idx = wk * 7 + dk
                        sel = if (sel == idx) -1 else idx
                    } else sel = -1
                }
            },
        ) {
            val lw = labelW.toPx()
            val g = gap.toPx()
            val cs = cell.toPx()
            val top = 18.dp.toPx()
            // etiquetas de día
            for (d in 0..6) {
                if (dayLabels[d].isEmpty()) continue
                val t = tm.measure(dayLabels[d], labelStyle)
                drawText(t, topLeft = Offset(0f, top + d * (cs + g) + (cs - t.size.height) / 2f))
            }
            // etiquetas de mes
            var lastMonth = -1
            for (wk in 0 until heat.weeks) {
                val d0 = heat.start.plusDays((wk * 7).toLong())
                val mo = d0.monthValue
                if (mo != lastMonth && (d0.dayOfMonth <= 7 || wk == 0)) {
                    if (!(wk == 0 && d0.dayOfMonth > 20)) {
                        val t = tm.measure(Fmt.monthShort(mo), labelStyle)
                        drawText(t, topLeft = Offset(lw + wk * (cs + g), 0f))
                    }
                    lastMonth = mo
                }
            }
            val mx = heat.max
            for (wk in 0 until heat.weeks) {
                val p = ((anim.value - 0.5f * wk / heat.weeks) / 0.5f).coerceIn(0f, 1f)
                for (dk in 0..6) {
                    val v = heat.at(wk, dk)
                    val x = lw + wk * (cs + g)
                    val y = top + dk * (cs + g)
                    val date = heat.start.plusDays((wk * 7 + dk).toLong())
                    val r = CornerRadius(cs * 0.28f)
                    if (v < 0) {
                        drawRoundRect(c.outline.a(0.35f), Offset(x, y), Size(cs, cs), r, style = Stroke(1.dp.toPx()))
                        continue
                    }
                    val lvl = when {
                        v == 0 -> 0
                        v <= 5 -> 1
                        v <= 12 -> 2
                        v <= 20 -> 3
                        else -> 4
                    }
                    val col = if (lvl == 0) c.surface3 else c.accent.a(0.25f + 0.2f * lvl)
                    val sc = 0.55f + 0.45f * p
                    val s2 = cs * sc
                    drawRoundRect(col.a(col.alpha * p), Offset(x + (cs - s2) / 2f, y + (cs - s2) / 2f), Size(s2, s2), r)
                    if (date == today) drawRoundRect(c.text.a(0.7f), Offset(x, y), Size(cs, cs), r, style = Stroke(1.5.dp.toPx()))
                    if (wk * 7 + dk == sel) drawRoundRect(c.accent, Offset(x - 1.dp.toPx(), y - 1.dp.toPx()), Size(cs + 2.dp.toPx(), cs + 2.dp.toPx()), r, style = Stroke(2.dp.toPx()))
                }
            }
            if (sel >= 0) {
                val wk = sel / 7
                val dk = sel % 7
                val v = heat.at(wk, dk)
                val date = heat.start.plusDays(sel.toLong())
                bubble(
                    tm, listOf(if (v == 0) "Descanso" else Fmt.plural(v, "serie", "series"), Fmt.dateMid(date)),
                    Offset(lw + wk * (cs + g) + cs / 2f, top + dk * (cs + g)), tipStyle, if (c.dark) c.surface3 else Color(0xFF1B1B22), c.outline,
                    if (c.dark) c.text else Color.White, (if (c.dark) c.textDim else Color.White.a(0.7f)),
                )
            }
        }
    }
}
