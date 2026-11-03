package app.fitstat.sys

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import app.fitstat.data.Prefs
import app.fitstat.ui.design.LedFont
import app.fitstat.ui.theme.Fonts
import app.fitstat.ui.theme.SkinStyle
import app.fitstat.ui.theme.skinColors
import app.fitstat.ui.theme.buildColors
import app.fitstat.ui.theme.resolvePalette

private fun withAlpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((((c ushr 24) and 0xFF) * a.coerceIn(0f, 1f)).toInt() shl 24)

/** Colores y rasgos con los que se dibujan los widgets (siguen la personalización de la app). */
class WidgetStyle(
    val dark: Boolean,
    val filled: Boolean,
    val bgTop: Int,
    val bgBottom: Int,
    val outline: Int,
    val fg: Int,
    val fgDim: Int,
    val fgFaint: Int,
    val hi: Int,
    val onHi: Int,
    val accent: Int,
    val accent2: Int,
    val good: Int,
    val soft: Int,
    val track: Int,
    val ledOff: Int,
    val led: Boolean,
    val fontId: String,
    val weightShift: Int,
    val radiusK: Float,
) {
    companion object {
        fun from(ctx: Context, prefs: Prefs): WidgetStyle {
            val theme = prefs.str("widgets.theme")
            val mode = prefs.str("theme.mode")
            val sysDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val skin = SkinStyle.from(prefs)
            val skinned = !skin.isDefault
            val appDark = if (skinned) true else when (mode) { "light" -> false; "system" -> sysDark; else -> true }
            val dark = if (skinned) true else when (theme) { "dark" -> true; "light" -> false; else -> appDark }
            val amoled = !skinned && mode == "amoled" && theme == "app"
            val palette = resolvePalette(prefs, ctx)
            val tint = when (prefs.str("theme.tint")) { "none" -> 0f; "strong" -> 0.22f; else -> 0.10f }
            // Con una skin puesta, los widgets usan sus colores, su letra y sus esquinas.
            val c = if (skinned) skinColors(skin) else buildColors(dark, amoled, palette, tint, prefs.str("theme.contrast") == "high")
            val opacity = (prefs.float("widgets.opacity") / 100f).coerceIn(0.2f, 1f)
            val filled = theme == "accent"
            val radiusK = if (skinned) (if (skin.cut == "round") 0.55f else 0.2f)
            else when (prefs.str("shape.radius")) { "sharp" -> 0.3f; "soft" -> 0.7f; "extra" -> 1.3f; else -> 1f }
            val fontId = if (skinned && !skin.ownFont) skin.info.defFont else prefs.str("type.font")
            val weightShift = when (prefs.str("type.weight")) { "light" -> -100; "strong" -> 100; else -> 0 }
            val accent = c.accent.toArgb()
            val accent2 = c.accent2.toArgb()
            val onAccent = c.onAccent.toArgb()

            return if (filled) {
                WidgetStyle(
                    dark = dark, filled = true,
                    bgTop = withAlpha(c.accent.toArgb(), opacity), bgBottom = withAlpha(accent2, opacity),
                    outline = withAlpha(0xFFFFFFFF.toInt(), 0.28f),
                    fg = onAccent, fgDim = withAlpha(onAccent, 0.70f), fgFaint = withAlpha(onAccent, 0.42f),
                    hi = onAccent, onHi = accent2, accent = accent, accent2 = accent2,
                    good = onAccent, soft = withAlpha(onAccent, 0.14f), track = withAlpha(onAccent, 0.22f),
                    ledOff = withAlpha(onAccent, 0.16f),
                    led = prefs.bool("widgets.led"), fontId = fontId, weightShift = weightShift, radiusK = radiusK,
                )
            } else {
                val text = c.text.toArgb()
                WidgetStyle(
                    dark = dark, filled = false,
                    bgTop = withAlpha(c.surface.toArgb(), opacity), bgBottom = withAlpha(c.surface2.toArgb(), opacity),
                    outline = if (dark) withAlpha(0xFFFFFFFF.toInt(), 0.09f) else withAlpha(0xFF000000.toInt(), 0.07f),
                    fg = text, fgDim = c.textDim.toArgb(), fgFaint = c.textFaint.toArgb(),
                    hi = accent, onHi = onAccent, accent = accent, accent2 = accent2,
                    good = c.good.toArgb(), soft = withAlpha(text, if (dark) 0.07f else 0.06f), track = withAlpha(text, if (dark) 0.12f else 0.10f),
                    ledOff = c.ledOff.toArgb(),
                    led = prefs.bool("widgets.led"), fontId = fontId, weightShift = weightShift, radiusK = radiusK,
                )
            }
        }

        /** Estilo fijo para pruebas y vistas previas (oscuro, paleta lavanda). */
        fun preview(dark: Boolean = true, filled: Boolean = false): WidgetStyle {
            val acc = Color(0xFF9B8CF5).toArgb()
            val acc2 = Color(0xFF7863E6).toArgb()
            val on = Color.White.toArgb()
            return if (filled) {
                WidgetStyle(true, true, acc, acc2, withAlpha(0xFFFFFFFF.toInt(), 0.28f), on, withAlpha(on, 0.7f), withAlpha(on, 0.42f), on, acc2, acc, acc2, on,
                    withAlpha(on, 0.14f), withAlpha(on, 0.22f), withAlpha(on, 0.16f), true, "manrope", 0, 1f)
            } else if (dark) {
                WidgetStyle(true, false, 0xFF17171C.toInt(), 0xFF202027.toInt(), withAlpha(0xFFFFFFFF.toInt(), 0.09f), 0xFFF3F3F7.toInt(), 0xFFA6A6B4.toInt(), 0xFF6E6E7C.toInt(),
                    acc, on, acc, acc2, 0xFF5CE0A0.toInt(), withAlpha(0xFFF3F3F7.toInt(), 0.07f), withAlpha(0xFFF3F3F7.toInt(), 0.12f), withAlpha(0xFFF3F3F7.toInt(), 0.10f), true, "manrope", 0, 1f)
            } else {
                WidgetStyle(false, false, 0xFFFFFFFF.toInt(), 0xFFF1F0F7.toInt(), withAlpha(0xFF000000.toInt(), 0.07f), 0xFF17161F.toInt(), 0xFF5E5C6E.toInt(), 0xFF9694A6.toInt(),
                    acc2, on, acc, acc2, 0xFF1E9E6A.toInt(), withAlpha(0xFF17161F.toInt(), 0.06f), withAlpha(0xFF17161F.toInt(), 0.10f), withAlpha(0xFF17161F.toInt(), 0.12f), true, "manrope", 0, 1f)
            }
        }
    }
}

/** Tipos de letra de la app disponibles para dibujar (con el grosor pedido). */
class Faces private constructor(private val base: Typeface, private val mono: Typeface) {
    private val cache = HashMap<Int, Typeface>()

    fun face(weight: Int, mono: Boolean = false): Typeface = cache.getOrPut(weight * 2 + if (mono) 1 else 0) {
        val src = if (mono) this.mono else base
        val p = Paint()
        p.typeface = src
        val ok = try { p.setFontVariationSettings("'wght' $weight") } catch (_: Throwable) { false }
        if (ok) (p.typeface ?: src) else if (weight >= 600) Typeface.create(src, Typeface.BOLD) else src
    }

    companion object {
        private val loaded = HashMap<String, Typeface>()

        private fun load(ctx: Context, id: String): Typeface = synchronized(loaded) {
            loaded.getOrPut(id) {
                val res = Fonts.resId(id)
                (if (res != null) runCatching { ResourcesCompat.getFont(ctx, res) }.getOrNull() else null) ?: Typeface.SANS_SERIF
            }
        }

        fun get(ctx: Context, fontId: String): Faces = Faces(load(ctx, fontId), load(ctx, "jetbrains_mono"))
    }
}

/** Pincel de los widgets: todas las medidas están en dp y se escalan solas a píxeles. */
class Ink(val canvas: Canvas, val k: Float, val st: WidgetStyle, private val faces: Faces) {
    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    fun rect(x: Float, y: Float, w: Float, h: Float) = RectF(x * k, y * k, (x + w) * k, (y + h) * k)

    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        val p = paint().apply { this.color = color }
        canvas.drawRoundRect(rect(x, y, w, h), r * k, r * k, p)
    }

    fun rrectStroke(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, stroke: Float) {
        val p = paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke * k }
        canvas.drawRoundRect(rect(x, y, w, h), r * k, r * k, p)
    }

    fun rrectGrad(x: Float, y: Float, w: Float, h: Float, r: Float, c1: Int, c2: Int, vertical: Boolean = false) {
        val p = paint()
        p.shader = if (vertical) LinearGradient(0f, y * k, 0f, (y + h) * k, c1, c2, Shader.TileMode.CLAMP)
        else LinearGradient(x * k, y * k, (x + w) * k, (y + h) * k, c1, c2, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect(x, y, w, h), r * k, r * k, p)
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Int) {
        canvas.drawCircle(cx * k, cy * k, r * k, paint().apply { this.color = color })
    }

    fun circleStroke(cx: Float, cy: Float, r: Float, color: Int, stroke: Float) {
        canvas.drawCircle(cx * k, cy * k, r * k, paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke * k })
    }

    fun radial(cx: Float, cy: Float, r: Float, inner: Int, outer: Int) {
        val p = paint()
        p.shader = RadialGradient(cx * k, cy * k, r * k, inner, outer, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx * k, cy * k, r * k, p)
    }

    /** Anillo de progreso: pista completa y arco de [progress] (0..1) desde arriba. */
    fun ring(cx: Float, cy: Float, r: Float, stroke: Float, progress: Float, color: Int, track: Int) {
        val rect = RectF((cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k)
        val t = paint().apply { this.color = track; style = Paint.Style.STROKE; strokeWidth = stroke * k }
        canvas.drawOval(rect, t)
        val p = progress.coerceIn(0f, 1f)
        if (p > 0.001f) {
            val a = paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke * k; strokeCap = Paint.Cap.ROUND }
            canvas.drawArc(rect, -90f, 360f * p, false, a)
        }
    }

    fun check(cx: Float, cy: Float, size: Float, color: Int, stroke: Float = 2.2f) {
        val s = size
        val path = Path().apply {
            moveTo((cx - s * 0.5f) * k, (cy + s * 0.02f) * k)
            lineTo((cx - s * 0.15f) * k, (cy + s * 0.38f) * k)
            lineTo((cx + s * 0.52f) * k, (cy - s * 0.36f) * k)
        }
        val p = paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke * k; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        canvas.drawPath(path, p)
    }

    /** Llama de racha. [size] es la altura en dp. */
    fun flame(cx: Float, cy: Float, size: Float, color: Int) {
        val s = size / 24f
        fun X(v: Float) = (cx + (v - 12f) * s) * k
        fun Y(v: Float) = (cy + (v - 12f) * s) * k
        val path = Path().apply {
            moveTo(X(12f), Y(2f))
            cubicTo(X(13f), Y(6f), X(19f), Y(8.5f), X(19f), Y(14.5f))
            cubicTo(X(19f), Y(18.6f), X(15.9f), Y(22f), X(12f), Y(22f))
            cubicTo(X(8.1f), Y(22f), X(5f), Y(18.6f), X(5f), Y(14.5f))
            cubicTo(X(5f), Y(12f), X(6.4f), Y(10.2f), X(8f), Y(9f))
            cubicTo(X(8.2f), Y(11f), X(9f), Y(12f), X(10f), Y(12.6f))
            cubicTo(X(10f), Y(8f), X(10.6f), Y(4.6f), X(12f), Y(2f))
            close()
        }
        canvas.drawPath(path, paint().apply { this.color = color })
    }

    /** Estrella de cinco puntas (récords). */
    fun star(cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path()
        for (i in 0 until 10) {
            val ang = Math.toRadians(-90.0 + i * 36.0)
            val rr = if (i % 2 == 0) r else r * 0.45f
            val x = (cx + rr * Math.cos(ang).toFloat()) * k
            val y = (cy + rr * Math.sin(ang).toFloat()) * k
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint().apply { this.color = color })
    }

    private fun textPaint(size: Float, weight: Int, color: Int, align: Paint.Align, mono: Boolean) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = faces.face((weight + st.weightShift).coerceIn(300, 800), mono)
            textSize = size * k
            this.color = color
            textAlign = align
        }

    /** Dibuja texto centrado verticalmente en [cy]. Devuelve el ancho dibujado (dp). */
    fun text(
        s: String, x: Float, cy: Float, size: Float, weight: Int, color: Int,
        align: Paint.Align = Paint.Align.LEFT, maxW: Float = Float.MAX_VALUE, mono: Boolean = false,
    ): Float {
        val p = textPaint(size, weight, color, align, mono)
        val shown: CharSequence = if (maxW < Float.MAX_VALUE / 2) TextUtils.ellipsize(s, p, maxW * k, TextUtils.TruncateAt.END) else s
        val fm = p.fontMetrics
        canvas.drawText(shown, 0, shown.length, x * k, cy * k - (fm.ascent + fm.descent) / 2f, p)
        return p.measureText(shown, 0, shown.length) / k
    }

    /** Tamaño de letra (entre [min] y [size]) con el que [s] cabe en [maxW] dp. Si ni así cabe, devuelve [min]. */
    fun fit(s: String, size: Float, min: Float, weight: Int, maxW: Float): Float {
        var sz = size
        while (sz > min && measure(s, sz, weight) > maxW) sz -= 0.5f
        return sz.coerceAtLeast(min)
    }

    fun measure(s: String, size: Float, weight: Int, mono: Boolean = false): Float {
        val p = textPaint(size, weight, 0, Paint.Align.LEFT, mono)
        return p.measureText(s) / k
    }

    /** Parte [s] en hasta [maxLines] líneas que caben en [maxW] dp. La última se corta con «…». */
    fun wrap(s: String, size: Float, weight: Int, maxW: Float, maxLines: Int): List<String> {
        val p = textPaint(size, weight, 0, Paint.Align.LEFT, false)
        val words = s.split(' ').filter { it.isNotEmpty() }
        val lines = ArrayList<String>()
        var cur = ""
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val tryLine = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(tryLine) / k <= maxW || cur.isEmpty()) {
                cur = tryLine
                i++
            } else {
                lines += cur
                cur = ""
                if (lines.size == maxLines) break
            }
        }
        if (lines.size < maxLines && cur.isNotEmpty()) lines += cur
        else if (lines.size == maxLines && i < words.size) {
            lines[maxLines - 1] = TextUtils.ellipsize(lines[maxLines - 1] + " " + words.drop(i).joinToString(" "), p, maxW * k, TextUtils.TruncateAt.END).toString()
        }
        return lines
    }

    /** Texto de matriz de puntos (como el letrero LED de la referencia). Devuelve el ancho (dp). */
    fun led(
        s: String, x: Float, cy: Float, dot: Float, on: Int, off: Int = st.ledOff,
        align: Paint.Align = Paint.Align.LEFT, glow: Boolean = true,
    ): Float {
        val gap = dot * 0.34f
        val pitch = dot + gap
        val t = LedFont.normalize(s)
        val totalW = LedFont.columns(t) * pitch - gap
        val x0 = when (align) { Paint.Align.CENTER -> x - totalW / 2f; Paint.Align.RIGHT -> x - totalW; else -> x }
        val y0 = cy - (LedFont.ROWS * pitch - gap) / 2f
        val onP = paint().apply { color = on }
        val offP = paint().apply { color = off }
        val glowP = paint().apply { color = withAlpha(on, 0.22f) }
        for ((ci, ch) in t.withIndex()) {
            val g = LedFont.glyph(ch)
            for (r in 0 until LedFont.ROWS) for (c in 0 until LedFont.COLS) {
                val lit = g[r * LedFont.COLS + c]
                val px = (x0 + (ci * (LedFont.COLS + 1) + c) * pitch + dot / 2f) * k
                val py = (y0 + r * pitch + dot / 2f) * k
                if (lit && glow) canvas.drawCircle(px, py, dot * 0.92f * k, glowP)
                canvas.drawCircle(px, py, dot / 2f * k, if (lit) onP else offP)
            }
        }
        return totalW
    }

    fun ledWidth(s: String, dot: Float): Float {
        val pitch = dot * 1.34f
        return LedFont.columns(LedFont.normalize(s)) * pitch - dot * 0.34f
    }

    /** Número grande: puntos LED si el ajuste lo pide, y si no, con la fuente de la app. */
    fun bigNumber(s: String, cx: Float, cy: Float, height: Float, color: Int, weight: Int = 700) {
        if (st.led) {
            val dot = height / (LedFont.ROWS * 1.34f)
            led(s, cx, cy, dot, color, align = Paint.Align.CENTER)
        } else {
            text(s, cx, cy, height * 1.05f, weight, color, Paint.Align.CENTER)
        }
    }

    /** Fondo de tarjeta con esquinas redondeadas, degradado y borde sutil. */
    fun card(w: Float, h: Float) {
        val r = (26f * st.radiusK).coerceAtLeast(8f)
        val p = paint()
        p.shader = LinearGradient(0f, 0f, w * k * 0.55f, h * k, st.bgTop, st.bgBottom, Shader.TileMode.CLAMP)
        val rr = RectF(0f, 0f, w * k, h * k)
        canvas.drawRoundRect(rr, r * k, r * k, p)
        if (st.filled) {
            val g = paint()
            g.shader = RadialGradient(w * k * 0.18f, 0f, w * k * 0.9f, withAlpha(0xFFFFFFFF.toInt(), 0.22f), 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rr, r * k, r * k, g)
        }
        val b = paint().apply { color = st.outline; style = Paint.Style.STROKE; strokeWidth = k }
        canvas.drawRoundRect(RectF(k / 2, k / 2, w * k - k / 2, h * k - k / 2), r * k, r * k, b)
    }
}
