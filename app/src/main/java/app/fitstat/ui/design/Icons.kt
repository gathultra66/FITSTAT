package app.fitstat.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import java.util.concurrent.ConcurrentHashMap

internal class IconShape(val path: Path, val closed: Boolean)

internal object IconCache {
    private val map = ConcurrentHashMap<Ic, List<IconShape>>()

    fun get(ic: Ic): List<IconShape> = map.getOrPut(ic) {
        ic.shapes.map { s ->
            val closed = s.startsWith("*")
            val d = if (closed) s.substring(1) else s
            IconShape(PathParser().parsePathString(d).toPath(), closed)
        }
    }
}

private val icByName: Map<String, Ic> by lazy { Ic.entries.associateBy { it.name.lowercase() } }

fun icOf(name: String, fallback: Ic = Ic.Circle): Ic = icByName[name.lowercase()] ?: fallback

/**
 * Ícono vectorial (set Lucide). El estilo (línea / duotono / grueso) y el grosor salen de Ajustes,
 * salvo que se indiquen explícitamente.
 */
@Composable
fun Icon(
    ic: Ic,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Fs.content,
    style: String = Fs.ui.iconStyle,
    weight: Float = Fs.ui.iconWeight,
) {
    val shapes = remember(ic) { IconCache.get(ic) }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val sw = if (style == "bold") weight * 1.3f else weight
            val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (sh in shapes) {
                if (sh.closed && style != "line") {
                    drawPath(sh.path, tint.copy(alpha = tint.alpha * (if (style == "duo") 0.22f else 0.34f)))
                }
                drawPath(sh.path, tint, style = stroke)
            }
        }
    }
}
