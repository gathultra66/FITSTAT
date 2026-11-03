package app.fitstat.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix

/** Valor animado que arranca en 0 y sube hasta [target] (si 'Números que cuentan' está activo). */
@Composable
fun animatedValue(target: Float, durationMs: Int = 900, delayMs: Int = 0): Float {
    val m = Fs.motion
    val anim = remember { Animatable(if (m.enabled && m.count) 0f else target) }
    LaunchedEffect(target, m.enabled, m.count) {
        if (m.enabled && m.count) {
            anim.animateTo(target, m.tween(durationMs, delayMs))
        } else {
            anim.snapTo(target)
        }
    }
    return anim.value
}

/** Progreso que siempre crece desde 0 al aparecer (aunque 'Números que cuentan' esté apagado, sigue animado). */
@Composable
fun animatedProgress(target: Float, durationMs: Int = 900, delayMs: Int = 0): Float {
    val m = Fs.motion
    val anim = remember { Animatable(if (m.enabled) 0f else target) }
    LaunchedEffect(target, m.enabled) {
        if (m.enabled) anim.animateTo(target, m.tween(durationMs, delayMs)) else anim.snapTo(target)
    }
    return anim.value
}

/** Barra de progreso gruesa, como la de la tarjeta de música de la referencia. */
@Composable
fun ChunkyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    track: Color = Fs.colors.surface3,
    fill: Brush? = null,
    delayMs: Int = 0,
) {
    val c = Fs.colors
    val p = animatedProgress(progress.coerceIn(0f, 1f), 900, delayMs)
    val brush = fill ?: Brush.horizontalGradient(listOf(c.accent2, c.accent))
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, Offset.Zero, size, r)
        if (p > 0.001f) {
            val w = (size.width * p).coerceAtLeast(size.height)
            drawRoundRect(brush, Offset.Zero, Size(w, size.height), r)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), 0f, size.height * 0.6f),
                Offset.Zero, Size(w, size.height), r,
            )
        }
    }
}

/** Barra dividida en segmentos (p. ej. días de la meta semanal). */
@Composable
fun SegmentedProgress(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    track: Color = Fs.colors.surface3,
    fill: Color = Fs.colors.accent,
) {
    val m = Fs.motion
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0 until total.coerceAtLeast(1)) {
            val on = i < done
            val p = animatedProgress(if (on) 1f else 0f, 420, m.stagger(i, 70, 700))
            Canvas(Modifier.weight(1f).height(height)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(track, Offset.Zero, size, r)
                if (p > 0.001f) drawRoundRect(fill, Offset.Zero, Size(size.width * p, size.height).let { Size(it.width.coerceAtLeast(size.height).coerceAtMost(size.width), it.height) }, r)
            }
        }
    }
}

/** Anillo de progreso con contenido al centro. */
@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 12.dp,
    color: Color = Fs.colors.accent,
    track: Color = Fs.colors.surface3,
    delayMs: Int = 0,
    animate: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val animated = animatedProgress(progress.coerceIn(0f, 1f), 1000, delayMs)
    val p = if (animate) animated else progress.coerceIn(0f, 1f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (p > 0.001f) {
                drawArc(
                    Brush.sweepGradient(listOf(color.mix(Color.Black, 0.25f), color, color.mix(Color.White, 0.2f))),
                    -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * Botón orbe neumórfico (como el de "Zen mode"): bisel hundido, cristal de color y un ícono que brilla.
 */
@Composable
fun Orb(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    ic: Ic = Ic.Dumbbell,
    active: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val c = Fs.colors
    val ui = Fs.ui
    val m = Fs.motion
    val breathe = if (m.loops && active) {
        val tr = rememberInfiniteTransition(label = "orb")
        val v by tr.animateFloat(0.75f, 1f, infiniteRepeatable(tween(m.ms(2400), easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
        v
    } else 1f
    Box(modifier.size(size).tappable(onClick, pressScale = 0.96f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val ctr = Offset(s / 2f, s / 2f)
            val dark = c.dark
            // resplandor exterior
            if (active && ui.glow > 0.05f) {
                drawCircle(
                    Brush.radialGradient(listOf(c.accent.a(0.38f * ui.glow * breathe), Color.Transparent), ctr, s * 0.62f),
                    s * 0.62f, ctr,
                )
            }
            // bisel exterior (convexo)
            drawCircle(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF3B3B45), Color(0xFF08080B)) else listOf(Color(0xFFFFFFFF), Color(0xFFB9B9C4)),
                    Offset(s * 0.15f, s * 0.05f), Offset(s * 0.85f, s * 0.95f),
                ),
                s * 0.5f, ctr,
            )
            // pozo (cóncavo)
            drawCircle(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF040406), Color(0xFF30303A)) else listOf(Color(0xFFA9A9B6), Color(0xFFF2F2F7)),
                    Offset(s * 0.2f, s * 0.1f), Offset(s * 0.8f, s * 0.9f),
                ),
                s * 0.45f, ctr,
            )
            // cristal
            val glass = s * 0.385f
            val a1 = if (active) c.accent.mix(Color.White, 0.2f) else c.textFaint
            val a2 = if (active) c.accent2 else c.surface3
            drawCircle(
                Brush.radialGradient(
                    listOf(a1.a(0.95f), a2.a(0.95f), a2.mix(Color.Black, 0.7f)),
                    Offset(s * 0.42f, s * 0.38f), glass * 1.25f,
                ),
                glass, ctr,
            )
            // brillo especular
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), Offset(s * 0.38f, s * 0.32f), glass * 0.7f),
                glass * 0.8f, Offset(s * 0.38f, s * 0.34f),
            )
            // borde interior del cristal
            drawCircle(Color.Black.copy(alpha = 0.25f), glass, ctr, style = Stroke(s * 0.012f))
        }
        if (content != null) content() else {
            Icon(ic, size = size * 0.27f, tint = if (active) Color.White.a(0.95f) else c.textDim, style = "bold", weight = 1.8f)
        }
    }
}
