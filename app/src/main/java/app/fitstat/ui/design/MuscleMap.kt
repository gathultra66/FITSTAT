package app.fitstat.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.fitstat.data.Muscle
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix

/** Qué músculos del modelo se pintan para cada región del cuerpo. */
private fun regionMuscles(key: String): List<Muscle> = when (key) {
    "neck" -> listOf(Muscle.NECK)
    "traps" -> listOf(Muscle.TRAPS)
    "front_delt", "rear_delt" -> listOf(Muscle.SHOULDERS)
    "chest" -> listOf(Muscle.CHEST)
    "biceps" -> listOf(Muscle.BICEPS)
    "triceps" -> listOf(Muscle.TRICEPS)
    "forearm" -> listOf(Muscle.FOREARMS)
    "abs", "obliques" -> listOf(Muscle.ABS)
    "upper_back" -> listOf(Muscle.LATS, Muscle.MID_BACK)
    "lower_back" -> listOf(Muscle.LOWER_BACK)
    "glutes" -> listOf(Muscle.GLUTES, Muscle.ABDUCTORS)
    "abductor_back", "adductor_front" -> listOf(Muscle.ADDUCTORS)
    "quads" -> listOf(Muscle.QUADS)
    "hamstring" -> listOf(Muscle.HAMSTRINGS)
    "calves", "soleus_l", "soleus_r" -> listOf(Muscle.CALVES)
    else -> emptyList()
}

private object MusclePaths {
    private val cache = HashMap<String, List<Pair<String, List<Path>>>>()

    private fun build(regions: Array<MuscleRegion>): List<Pair<String, List<Path>>> = regions.map { r ->
        r.key to r.polys.map { poly ->
            val n = poly.trim().split(' ').filter { it.isNotEmpty() }.map { it.toFloat() }
            Path().apply {
                var i = 0
                while (i + 1 < n.size) {
                    if (i == 0) moveTo(n[0], n[1]) else lineTo(n[i], n[i + 1])
                    i += 2
                }
                close()
            }
        }
    }

    val front: List<Pair<String, List<Path>>> get() = cache.getOrPut("f") { build(MuscleGeo.front) }
    val back: List<Pair<String, List<Path>>> get() = cache.getOrPut("b") { build(MuscleGeo.back) }
}

/** Nivel de trabajo de un músculo: 0 = nada, 1 = secundario, 2 = principal. */
typealias MuscleLevels = Map<Muscle, Int>

/**
 * Figura humana (frente o espalda) con los músculos trabajados resaltados.
 * Los principales brillan con el color de la paleta; los secundarios se ven más suaves.
 * [pulse] (0..1) anima el resplandor; [focus] resalta un músculo concreto.
 */
@Composable
fun MuscleFigure(
    levels: MuscleLevels,
    modifier: Modifier = Modifier,
    back: Boolean = false,
    pulse: Float = 0f,
    focus: Muscle? = null,
    padding: Float = 0.04f,
    heat: Map<Muscle, Float>? = null,
) {
    val c = Fs.colors
    val glowK = Fs.ui.glow
    val regions = remember(back) { if (back) MusclePaths.back else MusclePaths.front }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = padding * minOf(w, h)
        val s = minOf((w - 2 * pad) / 100f, (h - 2 * pad) / 200f)
        val ox = (w - 100f * s) / 2f
        val oy = (h - 200f * s) / 2f
        val base = if (c.dark) Color(0xFF30303A) else Color(0xFFC9C9D3)
        val baseHead = if (c.dark) Color(0xFF3A3A45) else Color(0xFFB9B9C6)
        translate(ox, oy) {
            scale(s, s, pivot = Offset.Zero) {
                // base del cuerpo
                for ((key, paths) in regions) {
                    val col = if (key == "head" || key == "knees") baseHead else base
                    for (p in paths) drawPath(p, col)
                }
                // músculos resaltados
                for ((key, paths) in regions) {
                    val ms = regionMuscles(key)
                    if (ms.isEmpty()) continue
                    var lv = 0
                    var heatV = 0f
                    for (m in ms) {
                        lv = maxOf(lv, levels[m] ?: 0)
                        heatV = maxOf(heatV, heat?.get(m) ?: 0f)
                    }
                    val isFocus = focus != null && focus in ms
                    if (lv == 0 && heatV <= 0f && !isFocus) continue
                    val strength = when {
                        isFocus -> 1f
                        lv == 2 -> 1f
                        lv == 1 -> 0.5f
                        else -> heatV
                    }
                    val col = if (lv == 1 && !isFocus) c.accent.mix(c.accent2, 0.3f) else c.accent
                    for (p in paths) {
                        if (glowK > 0.05f && strength > 0.6f) {
                            val g = (0.10f + 0.12f * pulse) * glowK
                            drawPath(p, col.a(g), style = Stroke(5.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(p, col.a(g * 1.4f), style = Stroke(2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        drawPath(p, col.a((0.30f + 0.7f * strength) * (if (strength > 0.6f) 0.88f + 0.12f * pulse else 1f)))
                        if (strength > 0.6f) {
                            drawPath(p, Color.White.a(0.18f + 0.1f * pulse), style = Stroke(0.5f, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
    }
}

/** Lado del cuerpo (frente o espalda) donde se ven mejor los músculos indicados. */
fun bestSide(levels: MuscleLevels): Boolean {
    var front = 0f
    var back = 0f
    for ((m, lv) in levels) {
        val w = if (lv == 2) 2f else 1f
        val f = m.isFront
        val b = m.isBack
        if (f && !b) front += w
        else if (b && !f) back += w
        else { front += w * 0.5f; back += w * 0.5f }
    }
    return back > front
}
