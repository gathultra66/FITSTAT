package app.fitstat.ui.design

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import app.fitstat.conn.Rhythm
import app.fitstat.conn.RhythmEngine
import app.fitstat.conn.RhythmFrame
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** El motor de ritmo de la app (null en pantallas que no lo proveen, como las pruebas: entonces no se anima nada). */
val LocalRhythm = staticCompositionLocalOf<RhythmEngine?> { null }

/** Estilos de animación. Los comparten todas las skins. */
object MusicFxStyles {
    val single = listOf("bars", "waves", "pulse", "particles", "aurora", "scope", "radial")
    val all = single + "mix"
}

/** Cómo se pinta una animación: colores y ajustes del usuario ya resueltos. */
class FxPaint(
    val c1: Color,
    val c2: Color,
    /** 0..1 */
    val intensity: Float,
    /** 0 = ahorro, 1 = normal, 2 = alta. */
    val detail: Int,
    val rainbow: Boolean,
    /** Multiplicador de velocidad del movimiento (1 = normal). */
    val speed: Float,
    val mixSecs: Float,
    /** Opacidad extra (para fundir un estilo con otro). */
    val fade: Float = 1f,
) {
    /** Color en la posición [pos] (0..1) del dibujo, en el instante [t]. */
    fun color(pos: Float, t: Float): Color =
        if (rainbow) Color.hsv(((t * 28f * speed + pos * 240f) % 360f + 360f) % 360f, 0.7f, 1f)
        else c1.mix(c2, pos.coerceIn(0f, 1f))

    /** Opacidad base de los trazos. */
    val alpha: Float get() = (0.10f + 0.55f * intensity) * fade

    fun faded(f: Float) = FxPaint(c1, c2, intensity, detail, rainbow, speed, mixSecs, fade * f)
}

private fun frac(x: Float) = x - floor(x)

/** Número «al azar» estable (0..1) para el elemento [i] con la semilla [k]. */
private fun hash(i: Int, k: Int): Float {
    var h = i * 374761393 + k * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

/** Dibuja el estilo [style] para el instante [f]. Es una función del tiempo y del ritmo: sin estado, así se puede probar. */
fun DrawScope.drawMusicFx(style: String, f: RhythmFrame, p: FxPaint) {
    if (f.quiet || size.width <= 1f || size.height <= 1f) return
    when (style) {
        "bars" -> fxBars(f, p)
        "waves" -> fxWaves(f, p)
        "pulse" -> fxPulse(f, p)
        "particles" -> fxParticles(f, p)
        "aurora" -> fxAurora(f, p)
        "scope" -> fxScope(f, p)
        "radial" -> fxRadial(f, p)
        "mix" -> {
            val list = MusicFxStyles.single
            val period = p.mixSecs.coerceAtLeast(4f)
            val slot = floor(f.time / period).toInt()
            val into = f.time - slot * period
            val cur = list[((slot % list.size) + list.size) % list.size]
            val nxt = list[(((slot + 1) % list.size) + list.size) % list.size]
            val x = ((into - (period - 1.2f)) / 1.2f).coerceIn(0f, 1f)
            val y = (into / 1.2f).coerceIn(0f, 1f)
            drawMusicFx(cur, f, p.faded((1f - x) * (0.35f + 0.65f * y)))
            if (x > 0f) drawMusicFx(nxt, f, p.faded(x))
        }
    }
}

// ───────────────────────── Barras

private fun DrawScope.fxBars(f: RhythmFrame, p: FxPaint) {
    val n = when (p.detail) { 0 -> 18; 2 -> 48; else -> 30 }
    val w = size.width
    val h = size.height
    val slot = w / n
    val bw = slot * 0.64f
    val base = p.alpha
    val d = density
    for (i in 0 until n) {
        val pos = i / (n - 1f)
        val b = pos * (Rhythm.BARS - 1)
        val i0 = b.toInt().coerceIn(0, Rhythm.BARS - 1)
        val i1 = min(i0 + 1, Rhythm.BARS - 1)
        val v = (f.bars[i0] * (1f - (b - i0)) + f.bars[i1] * (b - i0) + f.beat * 0.07f).coerceIn(0f, 1f)
        val bh = max(v * h * 0.92f, 3f * d)
        val x = i * slot + (slot - bw) / 2f
        drawRoundRect(
            p.color(pos, f.time).a(base * (0.45f + 0.55f * v)),
            Offset(x, h - bh), Size(bw, bh), CornerRadius(bw / 2f),
        )
    }
}

// ───────────────────────── Ondas

private fun DrawScope.fxWaves(f: RhythmFrame, p: FxPaint) {
    val layers = when (p.detail) { 0 -> 2; 2 -> 4; else -> 3 }
    val w = size.width
    val h = size.height
    val d = density
    val step = when (p.detail) { 0 -> 14f; 2 -> 5f; else -> 8f } * d
    val t = f.time * p.speed
    for (l in 0 until layers) {
        val amp = h * (0.06f + 0.30f * f.level * (1f + 0.6f * f.bass)) * (1f - l * 0.16f)
        val freq = 1.1f + 0.55f * l
        val dir = if (l % 2 == 0) 1f else -1f
        val mid = h * (0.64f - 0.06f * l)
        val path = Path()
        var x = 0f
        var first = true
        while (x <= w + step) {
            val u = x / w
            val y = mid + amp * sin(2f * PI.toFloat() * (u * freq + dir * t * (0.22f + 0.1f * l))) +
                0.35f * amp * sin(2f * PI.toFloat() * (u * freq * 2.3f - dir * t * 0.3f) + l)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            x += step
        }
        val line = Path().apply { addPath(path) }
        path.lineTo(w + step, h)
        path.lineTo(0f, h)
        path.close()
        val col = p.color(l / max(layers - 1f, 1f), f.time)
        drawPath(path, Brush.verticalGradient(listOf(col.a(p.alpha * 0.55f), col.a(0f)), startY = mid - amp, endY = h))
        drawPath(line, col.a(p.alpha * (0.95f - l * 0.15f)), style = Stroke(2.2f * d * (1f + p.detail * 0.2f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ───────────────────────── Pulso

private fun DrawScope.fxPulse(f: RhythmFrame, p: FxPaint) {
    val w = size.width
    val h = size.height
    val d = density
    val c = Offset(w * 0.5f, h * 0.5f)
    val maxR = hypot(w, h) * 0.55f
    // Un brillo que late en el centro.
    val glowR = min(w, h) * (0.22f + 0.25f * f.beat + 0.1f * f.level)
    drawCircle(
        Brush.radialGradient(listOf(p.color(0f, f.time).a(p.alpha * (0.25f + 0.55f * f.beat)), Color.Transparent), c, glowR),
        glowR, c,
    )
    for (k in 0 until Rhythm.BEATS) {
        val tb = f.beats[k]
        if (tb <= Rhythm.NONE / 2f) continue
        val age = (f.time - tb) * p.speed
        val life = 1.5f
        if (age < 0f || age > life) continue
        val q = age / life
        val r = q * maxR
        val a = (1f - q).let { it * it } * p.alpha * 1.25f
        drawCircle(p.color(q, f.time).a(a), r, c, style = Stroke((2f + (1f - q) * 7f * (0.4f + f.bass)) * d * (1f + p.detail * 0.15f)))
    }
    // Respiración suave entre golpes.
    val breathe = min(w, h) * (0.30f + 0.2f * f.level)
    drawCircle(p.color(0.5f, f.time).a(p.alpha * 0.35f), breathe, c, style = Stroke(1.6f * d))
}

// ───────────────────────── Partículas

private fun DrawScope.fxParticles(f: RhythmFrame, p: FxPaint) {
    val n = when (p.detail) { 0 -> 16; 2 -> 46; else -> 28 }
    val w = size.width
    val h = size.height
    val d = density
    val t = f.time * p.speed
    for (i in 0 until n) {
        val sx = hash(i, 1)
        val sy = hash(i, 2)
        val vy = 0.05f + hash(i, 3) * 0.10f
        val sz = 1.6f + hash(i, 4) * 3.6f
        val kick = if (i % 3 == 0) 1f else 0.4f
        val yn = frac(sy + t * vy * (0.6f + f.level))
        val x = sx * w + sin(t * 0.7f + i) * w * 0.02f
        val y = h * (1f - yn) - f.beat * h * 0.03f * kick
        val edge = min(yn, 1f - yn).coerceIn(0f, 0.2f) / 0.2f
        val r = sz * (1f + 0.9f * f.beat * kick + 0.5f * f.treble) * d
        val col = p.color(sx, f.time)
        drawCircle(col.a(p.alpha * (0.35f + 0.65f * hash(i, 5)) * edge), r, Offset(x, y))
        if (r > 3.6f * d) drawCircle(col.a(p.alpha * 0.18f * edge), r * 2.2f, Offset(x, y))
    }
}

// ───────────────────────── Aurora

private fun DrawScope.fxAurora(f: RhythmFrame, p: FxPaint) {
    val w = size.width
    val h = size.height
    val t = f.time * p.speed
    val energy = listOf(f.bass, f.mid, f.treble, f.level)
    val blobs = if (p.detail == 0) 3 else 4
    for (k in 0 until blobs) {
        val cx = w * (0.5f + 0.42f * sin(t * (0.23f + 0.05f * k) + k * 1.7f))
        val cy = h * (0.5f + 0.36f * sin(t * (0.31f + 0.04f * k) + k * 2.3f))
        val r = max(w, h) * (0.30f + 0.28f * energy[k] + 0.08f * f.beat)
        val col = p.color(k / 3f, f.time)
        drawCircle(
            Brush.radialGradient(listOf(col.a(p.alpha * (0.5f + 0.9f * energy[k]).coerceAtMost(1.2f)), Color.Transparent), Offset(cx, cy), r),
            r, Offset(cx, cy),
        )
    }
}

// ───────────────────────── Osciloscopio

private fun DrawScope.fxScope(f: RhythmFrame, p: FxPaint) {
    val w = size.width
    val h = size.height
    val t = f.time * p.speed
    val d = density
    val step = when (p.detail) { 0 -> 10f; 2 -> 3f; else -> 5f } * d
    val comps = 12
    val path = Path()
    var x = 0f
    var first = true
    while (x <= w + step) {
        val u = x / w
        var v = 0f
        for (j in 0 until comps) {
            val b = f.bars[(j * Rhythm.BARS / comps).coerceIn(0, Rhythm.BARS - 1)]
            v += b * sin(2f * PI.toFloat() * ((j + 1) * 0.5f * u + t * (0.25f + 0.07f * j)) + j)
        }
        val taper = sin(PI.toFloat() * u)
        val y = h * 0.5f - (v / 5f) * h * 0.42f * taper
        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        x += step
    }
    // Rejilla tenue.
    val grid = p.color(0.5f, f.time).a(p.alpha * 0.14f)
    for (i in 1..3) drawLine(grid, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), d)
    val col = p.color(0.5f, f.time)
    drawPath(path, col.a(p.alpha * 0.28f), style = Stroke(8f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, col.a(p.alpha * 1.1f), style = Stroke(2.4f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ───────────────────────── Radial

private fun DrawScope.fxRadial(f: RhythmFrame, p: FxPaint) {
    val w = size.width
    val h = size.height
    val d = density
    val n = when (p.detail) { 0 -> 36; 2 -> 96; else -> 64 }
    val c = Offset(w * 0.5f, h * 0.5f)
    val r0 = min(w, h) * (0.20f + 0.03f * f.beat)
    val maxLen = min(w, h) * 0.30f
    val t = f.time * p.speed
    val sw = (2.2f + 0.8f * p.detail) * 1.2f * d
    for (i in 0 until n) {
        val m = abs(i - n / 2f) / (n / 2f)
        val v = f.bars[((1f - m) * (Rhythm.BARS - 1)).toInt().coerceIn(0, Rhythm.BARS - 1)]
        val a = (i / n.toFloat()) * 2f * PI.toFloat() + t * 0.12f
        val len = 3f * d + v * maxLen
        val dx = cos(a)
        val dy = sin(a)
        drawLine(
            p.color(i / n.toFloat(), f.time).a(p.alpha * (0.4f + 0.6f * v)),
            Offset(c.x + dx * r0, c.y + dy * r0), Offset(c.x + dx * (r0 + len), c.y + dy * (r0 + len)),
            strokeWidth = sw, cap = StrokeCap.Round,
        )
    }
    drawCircle(p.color(0.5f, f.time).a(p.alpha * 0.4f), r0 * 0.92f, c, style = Stroke(1.6f * d))
}

// ───────────────────────── Uso en la interfaz

/**
 * El color dominante de una carátula (ARGB). No promedia todos los píxeles (mezclar tonos da gris): reparte los píxeles por
 * tono, pesando más los vivos y claros, toma el tono que más pesa y devuelve el promedio de los píxeles de ese tono.
 * Una carátula casi sin color (blanco y negro, cromo) devuelve su promedio. null si no se puede.
 */
fun coverColorOf(art: Bitmap?): Int? {
    if (art == null || art.width <= 0 || art.height <= 0) return null
    return try {
        val n = 24
        val small = Bitmap.createScaledBitmap(art, n, n, true)
        val buckets = 12
        val bw = FloatArray(buckets); val br = FloatArray(buckets); val bg = FloatArray(buckets); val bb = FloatArray(buckets)
        var ar = 0f; var ag = 0f; var ab = 0f
        for (y in 0 until n) for (x in 0 until n) {
            val px = small.getPixel(x, y)
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            ar += r; ag += g; ab += b
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx <= 0f) 0f else (mx - mn) / mx
            if (sat < 0.10f || mx < 0.12f) continue          // gris, casi blanco o casi negro: no tiene tono
            val d = mx - mn
            val hue = when (mx) {
                r -> ((g - b) / d + 6f) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } / 6f
            val i = (hue * buckets).toInt().coerceIn(0, buckets - 1)
            val w = 0.1f + sat * (0.3f + mx)
            bw[i] += w; br[i] += r * w; bg[i] += g * w; bb[i] += b * w
        }
        var best = -1
        for (i in 0 until buckets) {
            // un tono y sus vecinos cuentan juntos, para que un degradado no se parta en dos
            val score = bw[i] + 0.5f * (bw[(i + 1) % buckets] + bw[(i + buckets - 1) % buckets])
            if (best < 0 || score > bw[best] + 0.5f * (bw[(best + 1) % buckets] + bw[(best + buckets - 1) % buckets])) best = i
        }
        val total = n * n.toFloat()
        // sin tonos suficientes (menos del 4 % de los píxeles con color): el promedio
        if (best < 0 || bw[best] < total * 0.04f * 0.2f) return Color(ar / total, ag / total, ab / total).toArgb()
        Color(br[best] / bw[best], bg[best] / bw[best], bb[best] / bw[best]).toArgb()
    } catch (_: Throwable) {
        null
    }
}

/**
 * Pone una animación al ritmo de la música detrás del contenido de una tarjeta ([target]: «goal» o «music»). Los colores,
 * el estilo, la intensidad y lo demás salen de los ajustes (Ajustes › Animaciones de música). Solo se dibuja mientras suena
 * música (o la vista previa). [onDark]: la tarjeta es de color de acento (se usa el color del texto sobre el acento).
 */
@Composable
fun Modifier.musicFx(target: String, onAccent: Boolean = false, styleOverride: String? = null): Modifier {
    val eng = LocalRhythm.current ?: return this
    val prefs = Fs.prefs
    if (!Fs.motion.enabled) return this
    if (!prefs.bool("fx.enabled") || (styleOverride == null && !prefs.bool("fx.$target.on"))) return this
    DisposableEffect(eng, styleOverride) {
        val off = eng.attach(preview = styleOverride != null)
        onDispose { off() }
    }
    val c = Fs.colors
    val style = styleOverride ?: prefs.str("fx.$target.style")
    val choice = prefs.str("fx.color")
    val cover = eng.coverColor?.let { Color(it) }
    val base = when (choice) {
        "accent" -> if (onAccent) c.accent2.mix(Color.Black, 0.45f) else c.accent
        "contrast" -> if (onAccent) c.onAccent else c.text
        "cover" -> cover ?: (if (onAccent) c.onAccent else c.accent)
        else -> if (onAccent) c.onAccent else c.accent
    }
    val other = when (choice) {
        "cover" -> cover?.mix(Color.White, 0.35f) ?: base
        else -> if (onAccent) base.mix(c.accent2, 0.5f) else c.accent2
    }
    val paint = FxPaint(
        c1 = base, c2 = other, intensity = prefs.int("fx.intensity") / 100f, detail = when (prefs.str("fx.quality")) { "low" -> 0; "high" -> 2; else -> 1 },
        rainbow = choice == "rainbow", speed = prefs.int("fx.speed") / 100f, mixSecs = prefs.int("fx.mix_secs").toFloat(),
    )
    return this.drawBehind {
        val k = RhythmEngine.smooth(eng.fadeLevel)
        if (k > 0f) drawMusicFx(style, eng.frame, if (k < 1f) paint.faded(k) else paint)
    }
}

/** La portada vibra muy sutilmente al ritmo (un pulso de tamaño y un temblor mínimo). */
@Composable
fun Modifier.coverShake(): Modifier {
    val eng = LocalRhythm.current ?: return this
    val prefs = Fs.prefs
    if (!Fs.motion.enabled || !prefs.bool("fx.enabled") || !prefs.bool("fx.cover")) return this
    DisposableEffect(eng) {
        val off = eng.attach()
        onDispose { off() }
    }
    val baseAmt = prefs.int("fx.cover_amount") / 100f
    return this.graphicsLayer {
        val f = eng.frame
        val amt = baseAmt * RhythmEngine.smooth(eng.fadeLevel)
        if (!f.quiet && amt > 0f) {
            val pulse = (f.beat * 0.8f + f.bass * 0.35f).coerceIn(0f, 1f)
            val s = 1f + 0.028f * amt * pulse
            scaleX = s
            scaleY = s
            val px = density
            translationX = sin(f.time * 53f) * f.level * 1.1f * px * amt
            translationY = cos(f.time * 47f) * f.level * 1.1f * px * amt
            rotationZ = sin(f.time * 37f) * f.level * 0.4f * amt
        }
    }
}

/**
 * Mueve el motor de ritmo cuadro a cuadro, solo mientras hay una animación en pantalla y algo que animar
 * (música sonando o vista previa); el resto del tiempo duerme sin gastar nada.
 */
@Composable
fun RhythmDriver(engine: RhythmEngine) {
    val wanted = engine.wanted
    LaunchedEffect(engine, wanted) {
        if (!wanted) {
            engine.end()
            return@LaunchedEffect
        }
        engine.begin()
        try {
            while (isActive) {
                if (!engine.live && engine.resting) {
                    snapshotFlow { engine.live }.first { it }
                    engine.begin()
                    continue
                }
                engine.step(withInfiniteAnimationFrameNanos { it })
            }
        } finally {
            engine.end()
        }
    }
    val art = engine.art
    LaunchedEffect(engine, art) { engine.coverColor = coverColorOf(art) }
}
