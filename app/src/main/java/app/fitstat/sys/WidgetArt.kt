package app.fitstat.sys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import app.fitstat.data.Fmt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class WidgetKind(val id: String, val title: String, val blurb: String, val defaultW: Int, val defaultH: Int) {
    Today("hoy", "Hoy", "Tu entrenamiento del día y el avance de tu meta semanal.", 250, 110),
    Bot("fitbot", "FITBOT", "Tu mascota te saluda y te dice qué toca. Tócala para chatear.", 110, 110),
    Week("racha", "Racha semanal", "Los 7 días de tu semana de un vistazo, con tu racha.", 250, 110),
    Records("records", "Récords", "Tus últimos récords personales.", 250, 110),
}

private val LEFT = Paint.Align.LEFT
private val CENTER = Paint.Align.CENTER
private val RIGHT = Paint.Align.RIGHT

/** Dibuja los widgets en un Bitmap con el mismo estilo de la app. Todo en dp. */
object WidgetArt {
    private const val MAX_PIXELS = 1_000_000f

    /**
     * Dibuja el widget de [wDp]×[hDp]. Si el widget es bastante más grande que el tamaño de diseño (tablets: las celdas son más
     * grandes), se dibuja el mismo diseño ampliado en vez de dejarlo chico y con huecos.
     */
    fun render(ctx: Context, kind: WidgetKind, wDp: Float, hDp: Float, data: WidgetData, st: WidgetStyle, density: Float): Bitmap {
        var k = density.coerceIn(1f, 3.5f)
        while (wDp * k * hDp * k > MAX_PIXELS && k > 1f) k -= 0.1f
        val bmp = Bitmap.createBitmap(max(1, (wDp * k).roundToInt()), max(1, (hDp * k).roundToInt()), Bitmap.Config.ARGB_8888)
        val zoom = designZoom(kind, wDp, hDp)
        val w = wDp / zoom
        val h = hDp / zoom
        val ink = Ink(Canvas(bmp), k * zoom, st, Faces.get(ctx, st.fontId))
        ink.card(w, h)
        when (kind) {
            WidgetKind.Today -> today(ink, w, h, data)
            WidgetKind.Bot -> bot(ink, w, h, data)
            WidgetKind.Week -> week(ink, w, h, data)
            WidgetKind.Records -> records(ink, w, h, data)
        }
        return bmp
    }

    /** Cuánto se amplía el diseño: 1 hasta el tamaño normal, y hasta 1.6 en widgets muy grandes (siempre por el lado más justo). */
    internal fun designZoom(kind: WidgetKind, wDp: Float, hDp: Float): Float {
        val refW = kind.defaultW.toFloat()
        val refH = kind.defaultH.toFloat()
        return min(wDp / refW, hDp / refH).coerceIn(1f, 1.6f)
    }

    // ───────────────────────── Hoy

    private fun weekRing(ink: Ink, cx: Float, cy: Float, r: Float, d: WidgetData) {
        val st = ink.st
        val done = d.goalDone
        ink.ring(cx, cy, r, r * 0.24f, d.weekCount.toFloat() / d.goal, if (done && !st.filled) st.good else st.hi, st.track)
        ink.bigNumber(d.weekCount.toString(), cx, cy, r * 0.92f, st.fg)
    }

    private fun today(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        val title = d.planTitle ?: "Día de descanso"
        if (w < 190f) {
            if (h < 80f) todayMicro(ink, w, h, d, title) else todayNarrow(ink, w, h, d, title)
            return
        }
        val tiny = h < 80f
        val tall = h >= 180f
        val pad = if (tiny) 10f else 16f
        val ringR = min(34f, (h - 2 * pad) / 2f).coerceAtLeast(16f)
        val cx = w - pad - ringR - 2f
        val cy = when {
            tall -> pad + ringR + 2f
            h >= 100f -> h / 2f - 6f
            else -> h / 2f
        }
        weekRing(ink, cx, cy, ringR, d)
        if (h >= 100f) ink.text("de ${d.goal} días", cx, cy + ringR + 15f, 10f, 600, st.fgDim, CENTER)
        val maxW = cx - ringR - 14f - pad
        if (tiny) {
            ink.text(title, pad + 4f, h / 2f - 9f, ink.fit(title, 17f, 13f, 700, maxW), 700, st.fg, maxW = maxW)
            ink.text(d.planSub, pad + 4f, h / 2f + 11f, 12f, 500, st.fgDim, maxW = maxW)
            return
        }
        val withAction = h >= 100f
        ink.text(Fmt.dateMid(d.today), pad, pad + 8f, 11.5f, 600, st.fgDim, maxW = maxW)
        ink.text(title, pad, pad + 33f, ink.fit(title, 21f, 15f, 700, maxW), 700, st.fg, maxW = maxW)
        // En una tarjeta baja el botón ocupa el lugar de la descripción.
        if (!withAction || h >= 140f) {
            ink.text(d.planSub, pad, pad + 56f, ink.fit(d.planSub, 12.5f, 10.5f, 500, maxW), 500, st.fgDim, maxW = maxW)
        }
        if (tall) {
            val top = cy + ringR + 26f
            val bottom = h - pad - 28f - 12f
            dayRow(ink, pad, (top + bottom) / 2f, w - 2 * pad, 26f, true, d)
        }
        if (withAction) action(ink, pad, h - pad - 28f, d, maxW)
    }

    /** Tarjeta chica y baja (2×1): nombre del día a la izquierda, anillo a la derecha. */
    private fun todayMicro(ink: Ink, w: Float, h: Float, d: WidgetData, title: String) {
        val st = ink.st
        val pad = 10f
        val r = ((h - 2 * pad) / 2f).coerceAtLeast(12f)
        val cx = w - pad - r
        weekRing(ink, cx, h / 2f, r, d)
        val maxW = (cx - r - 6f - pad).coerceAtLeast(20f)
        ink.text(title, pad + 2f, h / 2f - 8f, ink.fit(title, 15f, 11.5f, 700, maxW), 700, st.fg, maxW = maxW)
        ink.text(d.planSub, pad + 2f, h / 2f + 9f, ink.fit(d.planSub, 10.5f, 9f, 500, maxW), 500, st.fgDim, maxW = maxW)
    }

    /** Tarjeta angosta y alta: el anillo grande arriba al centro, la racha debajo y el día al fondo. */
    private fun todayColumn(ink: Ink, w: Float, h: Float, d: WidgetData, title: String) {
        val st = ink.st
        val pad = 12f
        val r = min(36f, (w - 2 * pad) / 2f - 4f).coerceAtLeast(16f)
        val cx = w / 2f
        val cy = pad + r + 10f
        weekRing(ink, cx, cy, r, d)
        val y = cy + r + 24f
        val maxW = w - 2 * pad
        if (d.streakWeeks > 0) {
            val n = "${d.streakWeeks}"
            val nw = ink.measure(n, 16f, 700)
            val label = "sem."
            val lw = ink.measure(label, 10.5f, 500)
            val total = 15f + nw + 5f + lw
            val x0 = cx - total / 2f
            ink.flame(x0 + 6f, y, 16f, st.hi)
            ink.text(n, x0 + 15f, y, 16f, 700, st.fg)
            ink.text(label, x0 + 15f + nw + 5f, y + 1.5f, 10.5f, 500, st.fgDim)
        } else {
            ink.text("de ${d.goal} días", cx, y, 11.5f, 600, st.fgDim, CENTER, maxW = maxW)
        }
        ink.text(title, pad, h - pad - 22f, ink.fit(title, 17f, 12.5f, 700, maxW), 700, st.fg, maxW = maxW)
        ink.text(d.planSub, pad, h - pad - 6f, ink.fit(d.planSub, 10.5f, 9.5f, 500, maxW), 500, st.fgDim, maxW = maxW)
    }

    /** Tarjeta cuadrada (2×2): anillo y racha arriba, nombre del día abajo. */
    private fun todayNarrow(ink: Ink, w: Float, h: Float, d: WidgetData, title: String) {
        if (h >= 170f) {
            todayColumn(ink, w, h, d, title)
            return
        }
        val st = ink.st
        val pad = 12f
        val r = min(22f, (w - 2 * pad) * 0.26f).coerceAtLeast(12f)
        val cx = pad + r + 1f
        val cy = pad + r + 1f
        weekRing(ink, cx, cy, r, d)
        val fx = cx + r + 9f
        val room = (w - pad - fx).coerceAtLeast(10f)
        if (d.streakWeeks > 0) {
            ink.flame(fx + 6f, cy - 8f, 16f, st.hi)
            ink.text("${d.streakWeeks}", fx + 16f, cy - 8f, 16f, 700, st.fg, maxW = (room - 16f).coerceAtLeast(6f))
            ink.text("sem.", fx, cy + 10f, 10f, 500, st.fgDim, maxW = room)
        } else {
            ink.text("de ${d.goal}", fx, cy - 7f, 12f, 600, st.fg, maxW = room)
            ink.text("días", fx, cy + 9f, 10f, 500, st.fgDim, maxW = room)
        }
        val maxW = w - 2 * pad
        ink.text(title, pad, h - pad - 22f, ink.fit(title, 16f, 12.5f, 700, maxW), 700, st.fg, maxW = maxW)
        ink.text(d.planSub, pad, h - pad - 6f, ink.fit(d.planSub, 10.5f, 9.5f, 500, maxW), 500, st.fgDim, maxW = maxW)
    }

    /** Botón de la tarjeta "Hoy": Empezar, o el resumen de lo que ya hiciste. */
    private fun action(ink: Ink, x: Float, y: Float, d: WidgetData, maxW: Float) {
        val st = ink.st
        val hgt = 28f
        when {
            d.doneToday -> {
                val label = "Hecho · ${d.todaySets} " + if (d.todaySets == 1) "serie" else "series"
                val tw = ink.measure(label, 12.5f, 700)
                val pw = min(maxW, tw + 40f)
                ink.rrect(x, y, pw, hgt, hgt / 2, if (st.filled) st.soft else (st.good and 0x00FFFFFF) or 0x2E000000)
                ink.check(x + 14f, y + hgt / 2, 9f, if (st.filled) st.fg else st.good, 2f)
                ink.text(label, x + 26f, y + hgt / 2, 12.5f, 700, if (st.filled) st.fg else st.good, maxW = pw - 34f)
            }
            d.planTitle != null -> {
                val label = "Empezar"
                val tw = ink.measure(label, 12.5f, 700)
                val pw = min(maxW, tw + 44f)
                ink.rrect(x, y, pw, hgt, hgt / 2, st.hi)
                val tri = Path().apply {
                    val k = ink.k
                    moveTo((x + 13f) * k, (y + hgt / 2 - 5f) * k)
                    lineTo((x + 13f) * k, (y + hgt / 2 + 5f) * k)
                    lineTo((x + 21f) * k, (y + hgt / 2) * k)
                    close()
                }
                ink.canvas.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.onHi })
                ink.text(label, x + 28f, y + hgt / 2, 12.5f, 700, st.onHi, maxW = pw - 34f)
            }
        }
    }

    // ───────────────────────── Racha semanal

    /** Un día de la semana. Con [letter] la inicial va dentro del círculo (versión compacta). */
    private fun bubble(ink: Ink, state: DayState, cx: Float, cy: Float, bub: Float, letter: String? = null) {
        val st = ink.st
        val ls = (bub * 0.46f).coerceIn(8f, 12f)
        when (state) {
            DayState.Trained -> {
                ink.circle(cx, cy, bub / 2f, st.hi)
                ink.check(cx, cy, bub * 0.42f, st.onHi, max(1.8f, bub * 0.08f))
            }
            DayState.TodayOpen -> {
                ink.circleStroke(cx, cy, bub / 2f - 1.2f, st.hi, 2.2f)
                if (letter != null) ink.text(letter, cx, cy, ls, 800, st.fg, CENTER) else ink.circle(cx, cy, bub * 0.12f, st.hi)
            }
            DayState.Planned -> {
                ink.circleStroke(cx, cy, bub / 2f - 0.8f, st.fgFaint, 1.6f)
                if (letter != null) ink.text(letter, cx, cy, ls, 600, st.fgDim, CENTER)
            }
            DayState.Missed -> {
                ink.circle(cx, cy, bub / 2f, st.soft)
                if (letter != null) ink.text(letter, cx, cy, ls, 600, st.fgFaint, CENTER)
                else ink.rrect(cx - bub * 0.16f, cy - 1f, bub * 0.32f, 2f, 1f, st.fgFaint)
            }
            DayState.Future -> {
                ink.circle(cx, cy, bub / 2f, st.soft)
                if (letter != null) ink.text(letter, cx, cy, ls, 600, st.fgFaint, CENTER)
            }
        }
    }

    private fun dayRow(ink: Ink, x: Float, cy: Float, width: Float, bubMax: Float, labels: Boolean, d: WidgetData) {
        val st = ink.st
        val cw = width / 7f
        val bub = min(bubMax, cw - 5f).coerceAtLeast(12f)
        val total = if (labels) 11f + 7f + bub else bub
        val top = cy - total / 2f
        for (i in 0 until 7) {
            val cx = x + cw * (i + 0.5f)
            val by = if (labels) top + 18f + bub / 2f else cy
            if (labels) {
                val today = d.dayStates[i] == DayState.TodayOpen
                ink.text(d.dayLabels[i], cx, top + 5.5f, 11f, if (today) 800 else 600, if (today) st.fg else st.fgDim, CENTER)
            }
            bubble(ink, d.dayStates[i], cx, by, bub)
        }
    }

    /** Cuadrícula de 4×2: los 7 días y la racha en la octava casilla. */
    private fun weekNarrow(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        val pad = 12f
        ink.text("Racha", pad, pad + 7f, 12.5f, 700, st.hi)
        ink.text("${d.weekCount}/${d.goal}", w - pad, pad + 7f, 12.5f, 700, st.fg, RIGHT)
        val top0 = pad + 20f
        // En una tarjeta alta, abajo cabe la barra de la meta semanal.
        val goalBar = h >= 190f
        val reserve = if (goalBar) 36f else 0f
        val rowsH = min((h - pad - top0 - reserve) / 2f, 40f)
        val top = top0 + ((h - pad - top0 - reserve) - 2 * rowsH) / 2f
        if (goalBar) {
            val y = h - pad - 5f
            val bw = w - 2 * pad
            val pct = (d.weekCount.toFloat() / d.goal).coerceIn(0f, 1f)
            ink.text("Meta", pad, y - 13f, 10.5f, 600, st.fgDim)
            ink.text(Fmt.percent(pct), w - pad, y - 13f, 10.5f, 700, st.fg, RIGHT)
            ink.rrect(pad, y - 4f, bw, 8f, 4f, st.track)
            if (pct > 0.01f) ink.rrect(pad, y - 4f, max(8f, bw * pct), 8f, 4f, st.hi)
        }
        val cols = 4
        val cw = (w - 2 * pad) / cols
        val bub = min(min(cw - 3f, rowsH - 4f), 26f).coerceAtLeast(12f)
        for (i in 0 until 8) {
            val cx = pad + cw * (i % cols + 0.5f)
            val cy = top + rowsH * (i / cols + 0.5f)
            if (i < 7) {
                bubble(ink, d.dayStates[i], cx, cy, bub, d.dayLabels[i])
            } else if (d.streakWeeks > 0) {
                ink.flame(cx, cy - 6f, 15f, st.hi)
                ink.text("${d.streakWeeks}", cx, cy + 9f, 11f, 700, st.fg, CENTER)
            }
        }
    }

    private fun week(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        if (h < 80f) {
            val p = 12f
            ink.text("${d.weekCount}/${d.goal}", p + 2f, h / 2f - 8f, 21f, 700, st.fg)
            if (w < 190f) {
                ink.text("semana", p + 2f, h / 2f + 13f, 10f, 500, st.fgDim)
                if (d.streakWeeks > 0) {
                    val n = "${d.streakWeeks}"
                    ink.text(n, w - p, h / 2f, 20f, 700, st.fg, RIGHT)
                    ink.flame(w - p - ink.measure(n, 20f, 700) - 10f, h / 2f, 18f, st.hi)
                }
                return
            }
            ink.text("esta semana", p + 2f, h / 2f + 13f, 10f, 500, st.fgDim)
            val x0 = p + 84f
            dayRow(ink, x0, h / 2f, w - p - x0, 24f, false, d)
            return
        }
        if (w < 150f) {
            weekNarrow(ink, w, h, d)
            return
        }
        val pad = 16f
        val led = st.led && (w - 2 * pad) > 150f
        if (led) ink.led("RACHA", pad, pad + 8f, 2.1f, st.hi)
        else ink.text("Racha", pad, pad + 8f, 13f, 700, st.hi)
        var rx = w - pad
        val txt = "${d.weekCount}/${d.goal} días"
        ink.text(txt, rx, pad + 8f, 13f, 700, st.fg, RIGHT)
        rx -= ink.measure(txt, 13f, 700) + 12f
        if (d.streakWeeks > 0) {
            val s = d.streakWeeks.toString()
            ink.text(s, rx, pad + 8f, 13f, 700, st.fg, RIGHT)
            rx -= ink.measure(s, 13f, 700) + 9f
            ink.flame(rx, pad + 8f, 15f, st.hi)
        }
        val tall = h >= 130f
        val top = pad + 22f
        val bottom = h - pad - if (tall) 26f else 0f
        dayRow(ink, pad, (top + bottom) / 2f + 2f, w - 2 * pad, 32f, true, d)
        if (tall) {
            val y = h - pad - 5f
            val bw = w - 2 * pad
            ink.text("Meta semanal", pad, y - 13f, 10.5f, 600, st.fgDim)
            val pct = (d.weekCount.toFloat() / d.goal).coerceIn(0f, 1f)
            ink.text(Fmt.percent(pct), w - pad, y - 13f, 10.5f, 700, st.fg, RIGHT)
            ink.rrect(pad, y - 4f, bw, 8f, 4f, st.track)
            if (pct > 0.01f) ink.rrect(pad, y - 4f, max(8f, bw * pct), 8f, 4f, st.hi)
        }
    }

    // ───────────────────────── Récords

    /** Tarjeta baja y chica (2×1): solo el mejor récord en dos renglones. */
    private fun recordsMicro(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        val pad = 11f
        val maxW = w - 2 * pad
        val r = d.records.firstOrNull()
        if (r == null) {
            ink.text("Récords", pad, h / 2f - 8f, 13f, 700, st.hi, maxW = maxW)
            ink.text("Aún sin marcas", pad, h / 2f + 9f, 10.5f, 500, st.fgDim, maxW = maxW)
            return
        }
        ink.text(r.exercise, pad, h / 2f - 9f, ink.fit(r.exercise, 12f, 10f, 650, maxW), 650, st.fg, maxW = maxW)
        val wt = ink.text(r.weight, pad, h / 2f + 9f, 15f, 700, st.hi, maxW = maxW)
        ink.text("×${r.reps}", pad + wt + 6f, h / 2f + 10f, 11f, 600, st.fgDim, maxW = (maxW - wt - 6f).coerceAtLeast(0f))
    }

    /** Tarjeta ancha y baja (4×1): el mejor récord en una sola franja, sin encabezado. */
    private fun recordsStrip(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        val pad = 14f
        val r = d.records.firstOrNull()
        if (r == null) {
            ink.text("Récords", pad, h / 2f - 8f, 14f, 700, st.hi, maxW = w - 2 * pad)
            ink.text("Registra una serie y tu mejor marca aparecerá aquí.", pad, h / 2f + 10f, 11f, 500, st.fgDim, maxW = w - 2 * pad)
            return
        }
        val wtSize = 19f
        val wtW = ink.measure(r.weight, wtSize, 700)
        ink.text(r.weight, w - pad, h / 2f, wtSize, 700, st.hi, RIGHT)
        val x = pad + 17f
        val maxW = (w - pad - wtW - 14f - x).coerceAtLeast(30f)
        ink.star(pad + 5f, h / 2f - 8f, 5.4f, st.hi)
        ink.text(r.exercise, x, h / 2f - 8f, ink.fit(r.exercise, 14f, 11.5f, 650, maxW), 650, st.fg, maxW = maxW)
        ink.text("${r.reps} reps · ${r.when_}", x, h / 2f + 10f, 11f, 500, st.fgDim, maxW = maxW)
    }

    private fun recordsNarrow(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        if (h < 80f) {
            recordsMicro(ink, w, h, d)
            return
        }
        val pad = 12f
        ink.text("Récords", pad, pad + 7f, 12.5f, 700, st.hi)
        if (d.totalRecords > 0) {
            val n = "${d.totalRecords}"
            ink.text(n, w - pad - 12f, pad + 7f, 12.5f, 700, st.fg, RIGHT)
            ink.star(w - pad - 4f, pad + 7f, 5.5f, st.hi)
        }
        val maxW = w - 2 * pad
        if (d.records.isEmpty()) {
            val lines = ink.wrap("Aún no hay récords", 13f, 700, maxW, 2)
            val y0 = h / 2f - 2f - (lines.size - 1) * 8f
            lines.forEachIndexed { i, l -> ink.text(l, pad, y0 + i * 16f, 13f, 700, st.fg, maxW = maxW) }
            ink.text("Registra una serie", pad, h - pad - 6f, 10.5f, 500, st.fgDim, maxW = maxW)
            return
        }
        val top = pad + 22f
        val blockH = 60f
        val n = ((h - top - pad) / blockH).toInt().coerceIn(1, d.records.size)
        val shift = ((h - pad - top - n * blockH) / 2f).coerceIn(0f, 10f)
        for (i in 0 until n) {
            val r = d.records[i]
            val y = top + shift + i * blockH
            if (i > 0) ink.rrect(pad, y - 8f, maxW, 1f, 0.5f, st.track)
            ink.text(r.exercise, pad, y + 9f, ink.fit(r.exercise, 13f, 11f, 650, maxW), 650, st.fg, maxW = maxW)
            ink.text(r.weight, pad, y + 28f, 18f, 700, st.hi, maxW = maxW)
            // Si "reps · cuándo" no cabe en el ancho, solo se muestran las repeticiones.
            val sub = "${r.reps} reps · ${r.when_}"
            val shown = if (ink.measure(sub, 10f, 500) <= maxW) sub else "${r.reps} reps"
            ink.text(shown, pad, y + 45f, 10f, 500, st.fgDim, maxW = maxW)
        }
    }

    private fun records(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        if (w < 200f) {
            recordsNarrow(ink, w, h, d)
            return
        }
        if (h < 90f) {
            recordsStrip(ink, w, h, d)
            return
        }
        val pad = 16f
        val led = st.led && (w - 2 * pad) > 130f
        if (led) ink.led("RECORDS", pad, pad + 8f, 2.1f, st.hi)
        else ink.text("Récords", pad, pad + 8f, 13f, 700, st.hi)
        if (d.totalRecords > 0 && w >= 190f) {
            ink.star(w - pad - 4f, pad + 8f, 6f, st.hi)
            ink.text("${d.totalRecords}", w - pad - 14f, pad + 8f, 13f, 700, st.fg, RIGHT)
        }
        if (d.records.isEmpty()) {
            val cx = w / 2f
            ink.text("Aún no hay récords", cx, h / 2f + 4f, 14f, 700, st.fg, CENTER, maxW = w - 2 * pad)
            val lines = ink.wrap("Registra una serie y tu mejor marca aparecerá aquí.", 11.5f, 500, w - 2 * pad - 8f, 2)
            lines.forEachIndexed { i, l -> ink.text(l, cx, h / 2f + 24f + i * 15f, 11.5f, 500, st.fgDim, CENTER) }
            return
        }
        val top = pad + 26f
        val rowH = 37f
        val n = ((h - top - pad + 8f) / rowH).toInt().coerceIn(1, d.records.size)
        // Si sobra espacio abajo, el bloque se centra un poco en vez de quedar pegado arriba.
        val shift = ((h - pad - top - (n * rowH - 8f)) / 2f).coerceIn(0f, 12f)
        for (i in 0 until n) {
            val r = d.records[i]
            val y = top + shift + i * rowH
            if (i > 0) ink.rrect(pad, y - 3f, w - 2 * pad, 1f, 0.5f, st.track)
            ink.star(pad + 5f, y + 10f, 5.2f, if (i == 0) st.hi else st.fgFaint)
            val wt = ink.measure(r.weight, 14f, 700)
            ink.text(r.weight, w - pad, y + 10f, 14f, 700, st.hi, RIGHT)
            ink.text(r.exercise, pad + 17f, y + 10f, 14f, 650, st.fg, maxW = w - 2 * pad - 17f - wt - 8f)
            ink.text("${r.reps} reps · ${r.when_}", pad + 17f, y + 26f, 11f, 500, st.fgDim, maxW = w - 2 * pad - 17f)
        }
    }

    // ───────────────────────── FITBOT

    private fun bot(ink: Ink, w: Float, h: Float, d: WidgetData) {
        val st = ink.st
        val dark = st.dark
        fun mascot(cx: Float, cy: Float, orbR: Float) {
            WidgetMascot.orb(ink, cx, cy, orbR)
            val s = orbR * 2f * 0.96f
            WidgetMascot.draw(ink.canvas, (cx - s / 2f) * ink.k, (cy - s / 2f + orbR * 0.04f) * ink.k, s * ink.k, st.accent, st.accent2, d.feel, dark)
        }
        if (w >= 200f) {
            val pad = 14f
            val r = (h - 2 * pad) / 2f
            val cx = pad + r
            val cy = h / 2f
            mascot(cx, cy, r)
            val x = cx + r + 18f
            val maxW = w - x - pad
            val big = h >= 100f
            val lines = ink.wrap(d.botLine, if (big) 16f else 14f, 700, maxW, if (big) 3 else 2)
            val lh = if (big) 21f else 18f
            val y0 = (if (big) h * 0.40f else h / 2f) - (lines.size - 1) * lh / 2f
            lines.forEachIndexed { i, l -> ink.text(l, x, y0 + i * lh, if (big) 16f else 14f, 700, st.fg) }
            if (big) {
                val label = "Chatear"
                val pw = min(maxW, ink.measure(label, 12.5f, 700) + 44f)
                val py = h - pad - 30f
                ink.rrect(x, py, pw, 28f, 14f, st.hi)
                ink.text(label, x + pw / 2f + 6f, py + 14f, 12.5f, 700, st.onHi, CENTER)
                ink.rrect(x + 12f, py + 9.5f, 9f, 6.5f, 2.5f, st.onHi)
                val tail = Path().apply {
                    val k = ink.k
                    moveTo((x + 14f) * k, (py + 15.5f) * k); lineTo((x + 13f) * k, (py + 19f) * k); lineTo((x + 18f) * k, (py + 16f) * k); close()
                }
                ink.canvas.drawPath(tail, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.onHi })
            }
        } else if (w < 90f || h < 90f) {
            mascot(w / 2f, h / 2f, min(w, h) / 2f - 7f)
        } else {
            val r = min(w / 2f - 12f, h * 0.34f)
            mascot(w / 2f, h * 0.40f, r)
            ink.text(d.botLine.trimEnd('.'), w / 2f, h - 15f, 11.5f, 650, st.fg, CENTER, maxW = w - 16f)
        }
    }
}
