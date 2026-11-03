package app.fitstat.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.mix
import java.util.Random

private class Piece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var rot: Float, var vr: Float, val w: Float, val h: Float, val color: Color, val round: Boolean,
)

/**
 * Lluvia de confeti. Cada vez que [trigger] cambia (y es mayor que 0) se lanza una ráfaga.
 * No intercepta toques. Respeta los ajustes de animación.
 */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val m = Fs.motion
    val c = Fs.colors
    val pieces = remember { ArrayList<Piece>() }
    val frame = remember { mutableLongStateOf(0L) }
    val skin = Fs.skin
    val skinned = !skin.isDefault
    val palette = remember(c.accent, c.accent2, skinned) {
        if (skinned) listOf(c.accent, c.accent, c.accent2, c.accent.mix(Color.White, 0.55f), Color.White, c.accent.mix(Color.Black, 0.35f))
        else listOf(c.accent, c.accent2, c.accent.mix(Color.White, 0.5f), Color.White, c.warn, c.good)
    }
    val acero = skin.isAcero
    val carmesi = skin.isCarmesi
    LaunchedEffect(trigger) {
        if (trigger <= 0 || !m.enabled || !m.confetti) return@LaunchedEffect
        val rnd = Random(trigger.toLong() * 7919L)
        val burst = 90
        repeat(burst) {
            val ang = (-Math.PI / 2) + (rnd.nextDouble() - 0.5) * 1.9
            val sp = 650f + rnd.nextFloat() * 600f
            pieces.add(
                Piece(
                    x = 0.5f, y = 0.55f,
                    vx = (Math.cos(ang) * sp * 0.45).toFloat(), vy = (Math.sin(ang) * sp).toFloat(),
                    rot = rnd.nextFloat() * 360f, vr = (rnd.nextFloat() - 0.5f) * 720f,
                    w = 6f + rnd.nextFloat() * 6f, h = 9f + rnd.nextFloat() * 9f,
                    color = palette[rnd.nextInt(palette.size)], round = rnd.nextInt(5) == 0,
                ),
            )
        }
        var last = withFrameNanos { it }
        val end = last + 2_600_000_000L
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            for (p in pieces) {
                p.vy += 1500f * dt
                p.vx *= (1f - 1.4f * dt)
                p.vy *= (1f - 0.6f * dt)
                p.x += p.vx * dt / 1000f
                p.y += p.vy * dt / 1000f
                p.rot += p.vr * dt
            }
            frame.longValue = now
            if (now > end) break
        }
        pieces.clear()
        frame.longValue = 0L
    }
    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") frame.longValue
        if (pieces.isEmpty()) return@Canvas
        val fade = 1f
        for (p in pieces) {
            val px = p.x * size.width
            val py = p.y * size.height
            if (py > size.height + 40f || px < -40f || px > size.width + 40f) continue
            val a = (1f - ((py / size.height) - 0.75f).coerceAtLeast(0f) * 3f).coerceIn(0f, 1f) * fade
            when {
                // Carmesí: píxeles cuadrados de pantalla LED que se van apagando
                carmesi -> drawRect(p.color.copy(alpha = a), Offset(px - p.w / 2, py - p.w / 2), Size(p.w, p.w))
                // Acero: astillas diagonales de metal
                acero -> rotate(40f + p.rot * 0.08f, Offset(px, py)) {
                    drawRect(p.color.copy(alpha = a), Offset(px - p.w * 0.25f, py - p.h * 0.9f), Size(p.w * 0.5f, p.h * 1.8f))
                }
                else -> rotate(p.rot, Offset(px, py)) {
                    if (p.round) drawCircle(p.color.copy(alpha = a), p.w * 0.6f, Offset(px, py))
                    else drawRect(p.color.copy(alpha = a), Offset(px - p.w / 2, py - p.h / 2), Size(p.w, p.h))
                }
            }
        }
    }
}
