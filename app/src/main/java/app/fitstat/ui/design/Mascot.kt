package app.fitstat.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Estado de ánimo de FITBOT. */
enum class BotMood { Idle, Happy, Thinking, Typing, Talking, Sleepy, Error, Love }

internal enum class Reaction { Jump, Lift, Spin, Flex, Wink, Dizzy }

/** Permite "picar" a FITBOT desde fuera (p. ej. tocar su ícono). */
@Stable
class MascotController {
    var pokes by mutableIntStateOf(0)
        private set
    fun poke() { pokes++ }
}

@Composable
fun rememberMascotController() = remember { MascotController() }

internal class Pose {
    var bounce = 0f
    var squash = 1f
    var tilt = 0f
    var lift = 0f
    var spin = 0f
    var armL = 7f
    var armLE = 10f
    var armR = 7f
    var armRE = 10f
    var lookX = 0f
    var lookY = 0f
    var blink = 1f
    var eyes = Eyes.Normal
    var leftEyeOverride: Eyes? = null
    var talk = 0f
    var antenna = 0.6f
    var dots = -1f
    var zzz = -1f
    var spark = -1f
    var ecg = 0f
    var scan = 0f
    var stars = -1f
    var strain = 0f
    /** Progreso (0..1) y tipo de la reacción al toque; las skins los usan para sus efectos propios. */
    var react = 0f
    var kind = Reaction.Jump
    /** Opacidad de los adornos (pensamientos, zzz, chispas) mientras cambia el ánimo. */
    var fade = 1f
}

internal enum class Eyes { Normal, Happy, Sleepy, Cross, Heart, Squint, Scan, Wide }

internal fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Párpado: cierra rápido y abre un poco más despacio. [u] va de 0 a 1 durante el parpadeo. */
internal fun lid(u: Float): Float = if (u < 0.4f) 1f - 0.92f * smooth(0f, 0.4f, u) else 0.08f + 0.92f * smooth(0.4f, 1f, u)

internal fun bump(p: Float, a: Float, b: Float): Float = smooth(a, a + (b - a) * 0.3f, p) * (1f - smooth(a + (b - a) * 0.7f, b, p))

/**
 * FITBOT: robot con cabeza de mancuerna. Dibujado por completo en código (sin imágenes), así que
 * escala con nitidez y se anima: respira, parpadea, piensa, escribe y reacciona cuando lo tocas.
 */
@Composable
fun FitBot(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    mood: BotMood = BotMood.Idle,
    animated: Boolean = true,
    controller: MascotController? = null,
    lookX: Float = 0f,
    lookY: Float = 0f,
    onTap: (() -> Unit)? = null,
) {
    val c = Fs.colors
    val m = Fs.motion
    val loops = animated && m.mascotLoops
    val ctl = controller ?: remember { MascotController() }
    val hap = Fs.haptics

    // Reacciones al tocar (0..1)
    val react = remember { Animatable(0f) }
    var current by remember { mutableIntStateOf(0) }
    LaunchedEffect(ctl.pokes) {
        if (ctl.pokes > 0 && animated && m.enabled) {
            current = (ctl.pokes * 5 + 1) % Reaction.entries.size
            react.snapTo(0f)
            react.animateTo(1f, tween(m.ms(1100), easing = LinearEasing))
            react.snapTo(0f)
        }
    }

    // Bucles continuos (todos cierran su ciclo sin saltos)
    val tr = rememberInfiniteTransition(label = "bot")
    val zero = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val breath: State<Float> = if (loops) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(m.ms(2800), easing = LinearEasing), RepeatMode.Restart), label = "breath") else zero
    val blinkT: State<Float> = if (loops) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(m.ms(4200), easing = LinearEasing), RepeatMode.Restart), label = "blink") else zero
    // segundo parpadeo con otro ritmo: juntos dan intervalos irregulares (más natural que un metrónomo)
    val blinkT2: State<Float> = if (loops) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(m.ms(6300), easing = LinearEasing), RepeatMode.Restart), label = "blink2") else zero
    val fast: State<Float> = if (loops) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(m.ms(1500), easing = LinearEasing), RepeatMode.Restart), label = "fast") else zero
    val glance: State<Float> = if (loops) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(m.ms(9000), easing = LinearEasing), RepeatMode.Restart), label = "glance") else zero

    // Cambio de ánimo: en vez de saltar de una pose a otra, se mezclan (con un parpadeo al cambiar de gesto)
    val moodT = remember { Animatable(1f) }
    var from by remember { mutableStateOf(mood) }
    var to by remember { mutableStateOf(mood) }
    LaunchedEffect(mood) {
        if (mood == to) return@LaunchedEffect
        from = if (moodT.value >= 0.5f) to else from
        to = mood
        if (animated && m.enabled) {
            moodT.snapTo(0f)
            moodT.animateTo(1f, m.tween<Float>(440))
        } else moodT.snapTo(1f)
    }

    val lx by animateFloatAsState(lookX, m.spring(0.8f, 150f), label = "lx")
    val ly by animateFloatAsState(lookY, m.spring(0.8f, 150f), label = "ly")
    val pose = remember { Pose() }
    val prev = remember { Pose() }
    val ghost = remember { Pose() }

    val accent = c.accent.mix(Color.White, 0.18f)
    val accent2 = c.accent2
    val glowK = (Fs.ui.glow).coerceIn(0f, 1f)
    val bad = c.bad
    val skinHead = Fs.skin.head

    val tapMod = if (animated) {
        Modifier.pointerInput(Unit) {
            detectTapGestures {
                hap.tick()
                ctl.poke()
                onTap?.invoke()
            }
        }
    } else Modifier

    Canvas(modifier.size(size).then(tapMod)) {
        val s = this.size.minDimension / 100f
        val br = breath.value
        val f = fast.value
        val p = react.value
        val bt = blinkT.value
        val bt2 = blinkT2.value
        val gl = glance.value
        val mt = moodT.value
        pose.run {
            fillMood(this, to, loops, br, f, bt, bt2, gl, lx, ly)
            if (mt < 1f) {
                fillMood(prev, from, loops, br, f, bt, bt2, gl, lx, ly)
                bounce = lerp(prev.bounce, bounce, mt); squash = lerp(prev.squash, squash, mt)
                tilt = lerp(prev.tilt, tilt, mt); lift = lerp(prev.lift, lift, mt)
                armL = lerp(prev.armL, armL, mt); armLE = lerp(prev.armLE, armLE, mt)
                armR = lerp(prev.armR, armR, mt); armRE = lerp(prev.armRE, armRE, mt)
                this.lookX = lerp(prev.lookX, this.lookX, mt); this.lookY = lerp(prev.lookY, this.lookY, mt)
                talk = lerp(prev.talk, talk, mt); antenna = lerp(prev.antenna, antenna, mt)
                scan = lerp(prev.scan, scan, mt); strain = lerp(prev.strain, strain, mt)
                if (eyes != prev.eyes || leftEyeOverride != prev.leftEyeOverride) {
                    // cierra los ojos, cambia de gesto en lo oscuro y los abre
                    blink = min(blink, 0.08f + 0.92f * abs(2f * mt - 1f))
                    if (mt < 0.5f) { eyes = prev.eyes; leftEyeOverride = prev.leftEyeOverride }
                }
                fade = smooth(0.3f, 1f, mt)
                ghost.dots = prev.dots; ghost.zzz = prev.zzz; ghost.spark = prev.spark; ghost.stars = prev.stars
                ghost.fade = 1f - smooth(0f, 0.55f, mt)
            } else {
                ghost.dots = -1f; ghost.zzz = -1f; ghost.spark = -1f; ghost.stars = -1f
            }
            this.react = p
            kind = Reaction.entries[current]

            if (p > 0f) {
                fade = 1f
                when (Reaction.entries[current]) {
                    Reaction.Jump -> {
                        bounce += -17f * sin(PI.toFloat() * p)
                        squash *= 1f + 0.08f * sin(2f * PI.toFloat() * p) * (1f - p)
                        armL = 7f + 70f * bump(p, 0.1f, 0.8f); armR = armL
                        eyes = Eyes.Happy
                    }
                    Reaction.Lift -> {
                        val a = smooth(0f, 0.2f, p) * (1f - smooth(0.82f, 1f, p))
                        armL = 7f + 160f * a; armLE = 10f - 8f * a
                        armR = armL; armRE = armLE
                        val rep = abs(sin(2f * PI.toFloat() * p * 1.5f))
                        lift = -9f * a * rep
                        strain = a * rep
                        eyes = if (a * rep > 0.55f) Eyes.Squint else Eyes.Normal
                        if (p > 0.85f) eyes = Eyes.Happy
                    }
                    Reaction.Spin -> {
                        val e = smooth(0f, 1f, p)
                        spin = 360f * e
                        bounce += -9f * sin(PI.toFloat() * p)
                        eyes = Eyes.Wide
                    }
                    Reaction.Flex -> {
                        val a = bump(p, 0.05f, 0.95f)
                        armR = 7f + 78f * a; armRE = 10f + 100f * a
                        armL = 7f + 78f * a; armLE = 10f + 100f * a
                        eyes = if (a > 0.5f) Eyes.Squint else Eyes.Normal
                        spark = if (p in 0.3f..0.9f) (p - 0.3f) / 0.6f else -1f
                        squash *= 1f + 0.03f * a
                    }
                    Reaction.Wink -> {
                        val a = bump(p, 0.05f, 0.7f)
                        eyes = Eyes.Normal
                        if (a > 0.4f) leftEyeOverride = Eyes.Happy
                        tilt = 6f * a
                        spark = if (p in 0.25f..0.95f) (p - 0.25f) / 0.7f else -1f
                    }
                    Reaction.Dizzy -> {
                        tilt = 13f * sin(10f * PI.toFloat() * p) * (1f - p)
                        eyes = Eyes.Cross
                        stars = p
                        bounce += 1.5f * sin(14f * PI.toFloat() * p) * (1f - p)
                    }
                }
            }
        }
        scale(s, s, pivot = Offset.Zero) {
            if (skinHead != null) drawSkinHead(skinHead, pose, accent, accent2, glowK, bad, c.dark, br, f, loops)
            else drawBot(pose, accent, accent2, glowK, bad, c.dark)
            if (ghost.fade > 0.01f && (ghost.dots >= 0f || ghost.zzz >= 0f || ghost.spark >= 0f || ghost.stars >= 0f)) drawPoseOverlays(ghost, accent)
        }
    }
}

/**
 * La pose «de reposo» de un ánimo: respiración, parpadeo, mirada y los gestos propios del ánimo.
 * Se calcula para el ánimo actual y, mientras dura un cambio, también para el anterior (se mezclan).
 */
private fun fillMood(
    p: Pose, mood: BotMood, loops: Boolean,
    br: Float, f: Float, bt: Float, bt2: Float, gl: Float, lx: Float, ly: Float,
) {
    val tau = 2f * PI.toFloat()
    p.run {
        bounce = 0f; squash = 1f; tilt = 0f; lift = 0f; spin = 0f
        armL = 7f; armLE = 10f; armR = 7f; armRE = 10f
        lookX = lx; lookY = ly
        blink = 1f; eyes = Eyes.Normal; leftEyeOverride = null; talk = 0f; antenna = 0.55f
        dots = -1f; zzz = -1f; spark = -1f; scan = 0f; stars = -1f; strain = 0f
        react = 0f; fade = 1f
        ecg = f

        if (loops) {
            // inhalar sube y estira un poco; exhalar vuelve a la base (nunca se hunde en el suelo)
            val inhale = 0.5f - 0.5f * cos(br * tau)
            squash = 1f + 0.016f * inhale
            bounce = -1.1f * inhale
            // los brazos se balancean muy poco, un instante después del pecho
            val sway = sin(br * tau - 0.7f)
            armL = 7f + 1.6f * sway; armR = 7f - 1.6f * sway
            // dos parpadeos con ritmos distintos
            val ua = if (bt > 0.94f) (bt - 0.94f) / 0.06f else -1f
            val ub = if (bt2 > 0.96f) (bt2 - 0.96f) / 0.04f else -1f
            blink = min(if (ua >= 0f) lid(ua) else 1f, if (ub >= 0f) lid(ub) else 1f)
            // mirada que se pasea con suavidad; la cabeza la acompaña un poco
            if (mood == BotMood.Idle) {
                val g = -0.7f * bump(gl, 0.22f, 0.50f) + 0.7f * bump(gl, 0.58f, 0.86f)
                lookX += g
                tilt += 2.4f * g
            }
            antenna = 0.45f + 0.4f * (0.5f + 0.5f * sin(br * tau))
        }

        when (mood) {
            BotMood.Happy -> { eyes = Eyes.Happy; antenna = 0.9f }
            BotMood.Love -> { eyes = Eyes.Heart; antenna = 1f }
            BotMood.Sleepy -> {
                eyes = Eyes.Sleepy; tilt = -5f
                zzz = if (loops) (br * 2f) % 1f else 0.4f
                antenna = 0.15f
            }
            BotMood.Error -> { eyes = Eyes.Cross; tilt = if (loops) 5f * sin(f * 6f * tau) else 0f; antenna = 1f }
            BotMood.Typing -> {
                lookX = -0.55f; lookY = 0.85f
                tilt = -4f
                antenna = 0.5f + 0.5f * abs(sin(f * tau))
                if (loops) lift = 0.6f * sin(f * 2f * tau)
            }
            BotMood.Thinking -> {
                eyes = Eyes.Scan
                scan = if (loops) 0.5f + 0.5f * sin(f * tau) else 0.5f
                tilt = if (loops) -5f + 2f * sin(br * tau) else -5f
                dots = if (loops) f else 0.7f
                antenna = 0.5f + 0.5f * abs(sin(f * tau))
                // curls de bíceps mientras razona
                if (loops) {
                    val a = 0.5f + 0.5f * sin(f * tau)
                    val b = 0.5f + 0.5f * sin((f + 0.5f) * tau)
                    armL = 8f + 62f * a; armLE = 10f + 95f * a
                    armR = 8f + 62f * b; armRE = 10f + 95f * b
                }
            }
            BotMood.Talking -> {
                // la boca se abre y cierra con ritmo irregular, pero todo en múltiplos enteros del ciclo
                talk = if (loops) 0.5f + 0.5f * sin(f * 4f * tau) * (0.65f + 0.35f * sin(f * 3f * tau)) else 0.5f
                antenna = 0.9f
            }
            BotMood.Idle -> {}
        }
    }
}

// ───────────────────────── Dibujo (lienzo de 100 x 100)

private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, brush: Brush, alpha: Float = 1f) {
    drawRoundRect(brush, Offset(x, y), Size(w, h), CornerRadius(r, r), alpha)
}

private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) {
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))
}

internal fun star(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val pts = 8
    for (i in 0 until pts) {
        val ang = (-PI / 2 + i * PI / 4).toFloat()
        val rr = if (i % 2 == 0) r else r * 0.28f
        val x = cx + rr * cos(ang)
        val y = cy + rr * sin(ang)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawArm(sx: Float, sy: Float, side: Float, upper: Float, elbow: Float, metal: Brush, hand: Color) {
    val l1 = 10.5f
    val l2 = 9.5f
    val a1 = Math.toRadians(upper.toDouble()).toFloat()
    val ex = sx + side * l1 * sin(a1)
    val ey = sy + l1 * cos(a1)
    val a2 = Math.toRadians((upper + elbow).toDouble()).toFloat()
    val hx = ex + side * l2 * sin(a2)
    val hy = ey + l2 * cos(a2)
    drawLine(metal, Offset(sx, sy), Offset(ex, ey), 5.2f, StrokeCap.Round)
    drawLine(metal, Offset(ex, ey), Offset(hx, hy), 4.6f, StrokeCap.Round)
    drawCircle(Color(0xFF15151B), 2.6f, Offset(ex, ey))
    drawCircle(hand, 3.4f, Offset(hx, hy))
}

private fun DrawScope.drawBot(p: Pose, accent: Color, accent2: Color, glowK: Float, bad: Color, darkTheme: Boolean) {
    val metalV = Brush.verticalGradient(listOf(Color(0xFF7B7B88), Color(0xFF3A3A44), Color(0xFF23232A)), 0f, 100f)
    val body = Brush.verticalGradient(listOf(Color(0xFF55555F), Color(0xFF2A2A31)), 56f, 82f)
    val limb = Brush.verticalGradient(listOf(Color(0xFF6A6A76), Color(0xFF34343D)), 55f, 95f)
    val hand = Color(0xFF9A9AA8)
    val ink = Color(0xFF0A0A0F)
    val eyeColor = if (p.eyes == Eyes.Cross) bad else accent

    // sombra en el suelo
    val sh = (1f + p.bounce / 70f).coerceIn(0.5f, 1.2f)
    drawOval(Color.Black.copy(alpha = if (darkTheme) 0.5f else 0.22f), Offset(50f - 17f * sh, 94.8f), Size(34f * sh, 4.2f))

    // cuerpo completo: squash y bounce desde los pies
    translate(0f, p.bounce) {
        scale(1f / p.squash.coerceAtLeast(0.2f).let { 1f + (1f - it) * 0.5f }, p.squash, pivot = Offset(50f, 95f)) {
            // piernas
            val legL = Offset(43.5f, 80f)
            val legR = Offset(56.5f, 80f)
            drawLine(limb, legL, Offset(43.5f, 90f), 5.4f, StrokeCap.Round)
            drawLine(limb, legR, Offset(56.5f, 90f), 5.4f, StrokeCap.Round)
            rr(38.5f, 89.5f, 10.5f, 5f, 2.5f, Color(0xFF8A8A98))
            rr(51f, 89.5f, 10.5f, 5f, 2.5f, Color(0xFF8A8A98))

            // brazos (detrás del torso al bajar)
            drawArm(35.5f, 62.5f, -1f, p.armL, p.armLE, limb, hand)
            drawArm(64.5f, 62.5f, 1f, p.armR, p.armRE, limb, hand)

            // torso
            rr(35f, 58f, 30f, 25f, 9f, body)
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), 58f, 68f), Offset(35f, 58f), Size(30f, 12f), CornerRadius(9f))
            // panel del pecho con latido
            rr(41.5f, 63f, 17f, 12f, 4f, ink)
            val ecg = Path().apply {
                moveTo(43f, 69.5f); lineTo(47f, 69.5f); lineTo(48.6f, 65.8f); lineTo(51f, 73.2f); lineTo(52.6f, 69.5f); lineTo(57f, 69.5f)
            }
            drawPath(ecg, accent.copy(alpha = 0.28f), style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(ecg, accent, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val dotX = 43f + 14f * ((p.ecg * 1f) % 1f)
            drawCircle(Color.White.copy(alpha = 0.9f), 1.1f, Offset(dotX, 69.5f))

            // cuello
            rr(45f, 53.5f, 10f, 6f, 2f, Color(0xFF17171D))

            // cabeza (mancuerna) con inclinación, levantamiento y giro
            translate(0f, p.lift) {
                rotate(p.tilt + p.spin, Offset(50f, 36f)) {
                    drawHead(p, accent, accent2, glowK, eyeColor, metalV, ink)
                }
            }
        }
    }

    drawPoseOverlays(p, accent)
}

/** Pensamientos, zzz, chispas y estrellitas (los comparten FITBOT y las mascotas de las skins). */
internal fun DrawScope.drawPoseOverlays(p: Pose, accent: Color) {
    // pensamientos "..."
    if (p.dots >= 0f) {
        for (i in 0 until 3) {
            val ph = ((p.dots * 3f) - i).let { ((it % 3f) + 3f) % 3f }
            val k = if (ph < 1f) sin(PI.toFloat() * ph) else 0f
            drawCircle(accent.copy(alpha = (0.35f + 0.65f * k) * p.fade), 2.1f + 1.4f * k, Offset(80f + i * 7f, 9f - k * 2f))
        }
    }
    // zzz
    if (p.zzz >= 0f) {
        for (i in 0 until 3) {
            val ph = ((p.zzz + i / 3f) % 1f)
            val x = 78f + ph * 12f
            val y = 22f - ph * 18f
            val a = (1f - ph).coerceIn(0f, 1f) * (if (ph < 0.15f) ph / 0.15f else 1f) * p.fade
            val sz = 2.2f + ph * 3f
            val z = Path().apply { moveTo(x - sz, y - sz); lineTo(x + sz, y - sz); lineTo(x - sz, y + sz); lineTo(x + sz, y + sz) }
            drawPath(z, accent.copy(alpha = a), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    // chispas
    if (p.spark >= 0f) {
        val k = sin(PI.toFloat() * p.spark)
        drawPath(star(80f, 18f, 3f + 7f * k), Color.White.copy(alpha = 0.95f * k * p.fade))
        drawPath(star(84f, 11f, 1.5f + 3.5f * k), accent.copy(alpha = k * p.fade))
        drawPath(star(18f, 20f, 1.2f + 3f * k), accent.copy(alpha = 0.9f * k * p.fade))
    }
    // estrellitas de mareo
    if (p.stars >= 0f) {
        for (i in 0 until 3) {
            val ang = (p.stars * 2f * 2f * PI.toFloat()) + i * (2f * PI.toFloat() / 3f)
            val cx = 50f + 30f * cos(ang)
            val cy = 14f + 5f * sin(ang)
            drawPath(star(cx, cy, 3.4f), Color(0xFFFFD84D).copy(alpha = (1f - p.stars).coerceIn(0f, 1f) * p.fade))
        }
    }
}

private fun DrawScope.drawHead(p: Pose, accent: Color, accent2: Color, glowK: Float, eyeColor: Color, metalV: Brush, ink: Color) {
    val plateOut = Brush.horizontalGradient(listOf(Color(0xFF8D8D9B), Color(0xFF4B4B57), Color(0xFF2B2B33)), 0f, 12f)
    // antena
    drawLine(Color(0xFF5A5A66), Offset(50f, 25f), Offset(50f, 17.5f), 1.8f, StrokeCap.Round)
    val aG = p.antenna
    drawCircle(accent.copy(alpha = 0.18f + 0.22f * aG * glowK), 5.4f, Offset(50f, 15.5f))
    drawCircle(accent.mix(Color.White, 0.25f * aG).copy(alpha = 0.55f + 0.45f * aG), 2.5f, Offset(50f, 15.5f))

    // placas exteriores
    rr(3.5f, 12f, 13f, 48f, 5f, Brush.horizontalGradient(listOf(Color(0xFF8D8D9B), Color(0xFF4B4B57), Color(0xFF2B2B33)), 3.5f, 16.5f))
    rr(83.5f, 12f, 13f, 48f, 5f, Brush.horizontalGradient(listOf(Color(0xFF2B2B33), Color(0xFF4B4B57), Color(0xFF8D8D9B)), 83.5f, 96.5f))
    // placas interiores
    rr(17f, 19f, 11f, 34f, 4f, Brush.horizontalGradient(listOf(Color(0xFF7A7A88), Color(0xFF45454F), Color(0xFF2E2E36)), 17f, 28f))
    rr(72f, 19f, 11f, 34f, 4f, Brush.horizontalGradient(listOf(Color(0xFF2E2E36), Color(0xFF45454F), Color(0xFF7A7A88)), 72f, 83f))
    // brillo superior de las placas
    for (x in listOf(3.5f to 13f, 83.5f to 13f)) {
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), 12f, 26f), Offset(x.first, 12f), Size(x.second, 14f), CornerRadius(5f))
    }
    // tornillos / aro de acento en las placas exteriores
    for (cx in listOf(10f, 90f)) {
        drawCircle(Color(0xFF17171D), 3.6f, Offset(cx, 36f))
        drawCircle(accent.copy(alpha = 0.95f), 3.6f, Offset(cx, 36f), style = Stroke(1.3f))
        drawCircle(accent.copy(alpha = 0.9f), 1.0f, Offset(cx, 36f))
        drawCircle(Color(0xFF17171D), 1.1f, Offset(cx, 20f))
        drawCircle(Color(0xFF17171D), 1.1f, Offset(cx, 52f))
    }
    // barra (mango) con el visor
    rr(27f, 24f, 46f, 24f, 8f, Color(0xFF1B1B21))
    rr(29.5f, 26.5f, 41f, 19f, 6.5f, Brush.verticalGradient(listOf(Color(0xFF0B0B10), Color(0xFF14141B)), 26f, 46f))
    // reflejo del visor
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), 26.5f, 36f), Offset(29.5f, 26.5f), Size(41f, 9f), CornerRadius(6.5f))

    // ojos
    val ex = p.lookX * 3.2f
    val ey = p.lookY * 1.8f
    drawEyes(p, 39.5f + ex, 34.5f + ey, 60.5f + ex, 34.5f + ey, eyeColor, glowK)

    // boca
    val my = 41.2f + ey * 0.4f
    if (p.talk > 0.02f) {
        val h = 1.2f + 3.4f * p.talk
        rr(46f + ex * 0.3f, my - h / 2f + 0.4f, 8f, h, h / 2f, eyeColor)
    } else if (p.eyes == Eyes.Happy || p.eyes == Eyes.Heart) {
        val path = Path().apply { moveTo(46.5f + ex * 0.3f, my - 0.8f); quadraticTo(50f + ex * 0.3f, my + 2.6f, 53.5f + ex * 0.3f, my - 0.8f) }
        drawPath(path, eyeColor, style = Stroke(1.5f, cap = StrokeCap.Round))
    } else if (p.eyes == Eyes.Sleepy) {
        drawCircle(eyeColor.copy(alpha = 0.8f), 1.3f, Offset(50f, my + 0.2f))
    } else if (p.eyes != Eyes.Cross) {
        drawLine(eyeColor.copy(alpha = 0.55f), Offset(47.5f + ex * 0.3f, my), Offset(52.5f + ex * 0.3f, my), 1.4f, StrokeCap.Round)
    } else {
        val path = Path().apply { moveTo(46.5f, my + 1.2f); quadraticTo(50f, my - 1.8f, 53.5f, my + 1.2f) }
        drawPath(path, eyeColor, style = Stroke(1.5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEyes(p: Pose, lx: Float, ly: Float, rx: Float, ry: Float, color: Color, glowK: Float) {
    fun one(kind: Eyes, cx: Float, cy: Float, isLeft: Boolean) {
        val glow = 0.20f * (0.4f + glowK)
        when (kind) {
            Eyes.Normal, Eyes.Wide -> {
                val w = if (kind == Eyes.Wide) 8.4f else 6.6f
                val h = (if (kind == Eyes.Wide) 11.5f else 9f) * p.blink
                drawRoundRect(color.copy(alpha = glow), Offset(cx - w / 2 - 1.5f, cy - h / 2 - 1.5f), Size(w + 3f, h + 3f), CornerRadius((w + 3f) / 2.2f))
                drawRoundRect(color, Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(w / 2.6f))
                if (h > 4f) drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(cx - w / 2 + 1.0f, cy - h / 2 + 1.0f), Size(w * 0.32f, h * 0.28f), CornerRadius(1f))
            }
            Eyes.Happy -> {
                val path = Path().apply { moveTo(cx - 3.6f, cy + 2.2f); quadraticTo(cx, cy - 4.2f, cx + 3.6f, cy + 2.2f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5f, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(2.4f, cap = StrokeCap.Round))
            }
            Eyes.Sleepy -> {
                drawLine(color.copy(alpha = glow), Offset(cx - 3.6f, cy + 1.5f), Offset(cx + 3.6f, cy + 1.5f), 4.6f, StrokeCap.Round)
                drawLine(color, Offset(cx - 3.4f, cy + 1.5f), Offset(cx + 3.4f, cy + 1.5f), 2.2f, StrokeCap.Round)
            }
            Eyes.Squint -> {
                val s = if (isLeft) 1f else -1f
                val path = Path().apply { moveTo(cx - 3.2f * s, cy - 3.2f); lineTo(cx + 2.4f * s, cy); lineTo(cx - 3.2f * s, cy + 3.2f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, color, style = Stroke(2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Eyes.Cross -> {
                val path = Path().apply { moveTo(cx - 3f, cy - 3f); lineTo(cx + 3f, cy + 3f); moveTo(cx + 3f, cy - 3f); lineTo(cx - 3f, cy + 3f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5f, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(2.2f, cap = StrokeCap.Round))
            }
            Eyes.Heart -> {
                val path = Path().apply {
                    moveTo(cx, cy + 4f)
                    cubicTo(cx - 6f, cy - 0.5f, cx - 3.6f, cy - 5f, cx, cy - 1.6f)
                    cubicTo(cx + 3.6f, cy - 5f, cx + 6f, cy - 0.5f, cx, cy + 4f)
                    close()
                }
                drawPath(path, color.copy(alpha = glow * 1.6f), style = Stroke(3.5f, join = StrokeJoin.Round))
                drawPath(path, color)
            }
            Eyes.Scan -> {}
        }
    }
    if (p.eyes == Eyes.Scan) {
        // barra de escaneo: dos "pupilas" que recorren el visor
        val x = 36f + 28f * p.scan
        for (i in 0..2) {
            val a = 1f - i * 0.32f
            drawRoundRect(color.copy(alpha = 0.22f * a), Offset(x - 2.6f - i * (if (p.scan > 0.5f) -1.6f else 1.6f), 30f), Size(5.2f, 9.4f), CornerRadius(2.4f))
        }
        drawRoundRect(color, Offset(x - 2.4f, 30.6f), Size(4.8f, 8.2f), CornerRadius(2.2f))
        drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(x - 1.4f, 31.6f), Size(1.6f, 2.2f), CornerRadius(0.8f))
        return
    }
    one(p.leftEyeOverride ?: p.eyes, lx, ly, true)
    one(p.eyes, rx, ry, false)
}
