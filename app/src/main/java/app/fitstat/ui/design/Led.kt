package app.fitstat.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import java.text.Normalizer

/** Fuente de matriz de puntos 5x7 (como los letreros LED de la referencia). */
object LedFont {
    const val COLS = 5
    const val ROWS = 7

    private val table: Map<Char, String> = mapOf(
        'A' to ".###. #...# #...# ##### #...# #...# #...#",
        'B' to "####. #...# #...# ####. #...# #...# ####.",
        'C' to ".###. #...# #.... #.... #.... #...# .###.",
        'D' to "####. #...# #...# #...# #...# #...# ####.",
        'E' to "##### #.... #.... ####. #.... #.... #####",
        'F' to "##### #.... #.... ####. #.... #.... #....",
        'G' to ".###. #...# #.... #.### #...# #...# .###.",
        'H' to "#...# #...# #...# ##### #...# #...# #...#",
        'I' to ".###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.",
        'J' to "..### ...#. ...#. ...#. ...#. #..#. .##..",
        'K' to "#...# #..#. #.#.. ##... #.#.. #..#. #...#",
        'L' to "#.... #.... #.... #.... #.... #.... #####",
        'M' to "#...# ##.## #.#.# #.#.# #...# #...# #...#",
        'N' to "#...# #...# ##..# #.#.# #..## #...# #...#",
        'O' to ".###. #...# #...# #...# #...# #...# .###.",
        'P' to "####. #...# #...# ####. #.... #.... #....",
        'Q' to ".###. #...# #...# #...# #.#.# #..#. .##.#",
        'R' to "####. #...# #...# ####. #.#.. #..#. #...#",
        'S' to ".#### #.... #.... .###. ....# ....# ####.",
        'T' to "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..",
        'U' to "#...# #...# #...# #...# #...# #...# .###.",
        'V' to "#...# #...# #...# #...# #...# .#.#. ..#..",
        'W' to "#...# #...# #...# #.#.# #.#.# ##.## #...#",
        'X' to "#...# #...# .#.#. ..#.. .#.#. #...# #...#",
        'Y' to "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..",
        'Z' to "##### ....# ...#. ..#.. .#... #.... #####",
        '0' to ".###. #...# #..## #.#.# ##..# #...# .###.",
        '1' to "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.",
        '2' to ".###. #...# ....# ...#. ..#.. .#... #####",
        '3' to "##### ....# ...#. ..##. ....# #...# .###.",
        '4' to "...#. ..##. .#.#. #..#. ##### ...#. ...#.",
        '5' to "##### #.... ####. ....# ....# #...# .###.",
        '6' to "..##. .#... #.... ####. #...# #...# .###.",
        '7' to "##### ....# ...#. ..#.. .#... .#... .#...",
        '8' to ".###. #...# #...# .###. #...# #...# .###.",
        '9' to ".###. #...# #...# .#### ....# ...#. .##..",
        ' ' to "..... ..... ..... ..... ..... ..... .....",
        '-' to "..... ..... ..... ##### ..... ..... .....",
        '+' to "..... ..#.. ..#.. ##### ..#.. ..#.. .....",
        '.' to "..... ..... ..... ..... ..... .##.. .##..",
        ',' to "..... ..... ..... ..... .##.. ..#.. .#...",
        ':' to "..... .##.. .##.. ..... .##.. .##.. .....",
        ';' to "..... .##.. .##.. ..... .##.. ..#.. .#...",
        '/' to "....# ....# ...#. ..#.. .#... #.... #....",
        '%' to "##..# ##..# ...#. ..#.. .#... #..## #..##",
        '!' to "..#.. ..#.. ..#.. ..#.. ..#.. ..... ..#..",
        '?' to ".###. #...# ....# ...#. ..#.. ..... ..#..",
        '(' to "...#. ..#.. .#... .#... .#... ..#.. ...#.",
        ')' to ".#... ..#.. ...#. ...#. ...#. ..#.. .#...",
        '>' to "#.... .#... ..#.. ...#. ..#.. .#... #....",
        '<' to "....# ...#. ..#.. .#... ..#.. ...#. ....#",
        '_' to "..... ..... ..... ..... ..... ..... #####",
        '=' to "..... ..... ##### ..... ##### ..... .....",
        '*' to "..... #.#.# .###. ##### .###. #.#.# .....",
        '#' to ".#.#. ##### .#.#. ##### .#.#. ..... .....",
        '\'' to "..#.. ..#.. .#... ..... ..... ..... .....",
        '|' to "..#.. ..#.. ..#.. ..#.. ..#.. ..#.. ..#..",
        '°' to ".##.. #..#. #..#. .##.. ..... ..... .....",
        '•' to "..... ..... .##.. .##.. ..... ..... .....",
        '↑' to "..#.. .###. #.#.# ..#.. ..#.. ..#.. .....",
        '↓' to "..... ..#.. ..#.. ..#.. #.#.# .###. ..#..",
        '█' to "##### ##### ##### ##### ##### ##### #####",
    )

    // La usan la interfaz y el dibujo de los widgets (otro hilo), por eso es concurrente.
    private val cache = java.util.concurrent.ConcurrentHashMap<Char, BooleanArray>()

    /** 35 booleanos (7 filas x 5 columnas). */
    fun glyph(c: Char): BooleanArray = cache.getOrPut(c) {
        val s = table[c] ?: table['?']!!
        val rows = s.split(' ')
        BooleanArray(ROWS * COLS) { i -> rows[i / COLS][i % COLS] == '#' }
    }

    /** Quita acentos y pasa a mayúsculas para poder dibujar el texto. */
    fun normalize(text: String): String {
        val n = Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return n.map { c ->
            when {
                table.containsKey(c) -> c
                c == '×' -> 'X'
                c == '·' || c == '•' -> '•'
                c == '–' || c == '—' -> '-'
                c == '’' || c == '‘' -> '\''
                else -> '?'
            }
        }.joinToString("")
    }

    fun columns(text: String): Int = if (text.isEmpty()) 0 else text.length * (COLS + 1) - 1
}

/**
 * Texto en matriz de puntos. Los puntos apagados se ven tenues, como en un letrero LED.
 * [maxCols] limita el ancho y, con [scroll], hace correr el texto como marquesina.
 */
@Composable
fun LedText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 3.dp,
    gap: Dp = 1.dp,
    lit: Color = Fs.colors.text,
    off: Color = Fs.colors.ledOff,
    round: Boolean = Fs.skin.cut != "cut",
    glow: Boolean = true,
    scroll: Boolean = false,
    maxCols: Int? = null,
    pad: Int = 0,
) {
    val t = remember(text) { LedFont.normalize(text) }
    val textCols = LedFont.columns(t)
    val cols = (maxCols ?: (textCols + pad * 2)).coerceAtLeast(1)
    val glowOn = glow && Fs.ui.glow > 0.25f
    val needScroll = scroll && textCols > cols && Fs.motion.mascotLoops
    val shift = if (needScroll) {
        val tr = rememberInfiniteTransition(label = "led")
        val total = (textCols + 8).toFloat()
        val dur = (total * 110f / Fs.motion.speed).toInt().coerceAtLeast(1500)
        val v by tr.animateFloat(
            0f, total,
            infiniteRepeatable(tween(dur, easing = LinearEasing), RepeatMode.Restart),
            label = "shift",
        )
        v
    } else 0f
    val pitch = dot + gap
    Canvas(modifier.width(pitch * cols).height(pitch * LedFont.ROWS)) {
        val p = pitch.toPx()
        val d = dot.toPx()
        val r = d / 2f
        val firstCol = if (needScroll) 0 else pad
        clipRect {
            for (ci in t.indices) {
                val g = LedFont.glyph(t[ci])
                val baseCol = firstCol + ci * (LedFont.COLS + 1) - (if (needScroll) shift else 0f)
                for (row in 0 until LedFont.ROWS) {
                    for (col in 0 until LedFont.COLS) {
                        val x = (baseCol + col) * p
                        if (x < -p || x > size.width) continue
                        val y = row * p
                        val on = g[row * LedFont.COLS + col]
                        val c = if (on) lit else off
                        if (round) {
                            if (on && glowOn) drawCircle(lit.copy(alpha = 0.22f), r * 1.9f, Offset(x + r, y + r))
                            drawCircle(c, r, Offset(x + r, y + r))
                        } else {
                            drawRoundRect(c, Offset(x, y), Size(d, d), CornerRadius(d * 0.2f))
                        }
                    }
                }
            }
            // En modo marquesina el texto se repite para que el ciclo sea continuo.
            if (needScroll) {
                val offset = (textCols + 8).toFloat()
                for (ci in t.indices) {
                    val g = LedFont.glyph(t[ci])
                    val baseCol = ci * (LedFont.COLS + 1) + offset - shift
                    for (row in 0 until LedFont.ROWS) {
                        for (col in 0 until LedFont.COLS) {
                            val x = (baseCol + col) * p
                            if (x < -p || x > size.width) continue
                            if (g[row * LedFont.COLS + col]) {
                                val y = row * p
                                if (glowOn) drawCircle(lit.copy(alpha = 0.22f), r * 1.9f, Offset(x + r, y + r))
                                drawCircle(lit, r, Offset(x + r, y + r))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Letrero LED que ocupa todo el ancho. El tamaño del punto se ajusta para que el texto quepa
 * (entre [minPitch] y [maxPitch]); si aun así es más largo, corre como marquesina.
 * Con [cols] > 0 se fija el número de columnas.
 */
@Composable
fun LedBanner(
    text: String,
    modifier: Modifier = Modifier,
    cols: Int = 0,
    lit: Color = Fs.colors.textDim,
    scroll: Boolean = true,
    minPitch: Dp = 3.3.dp,
    maxPitch: Dp = 5.6.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val t = remember(text) { LedFont.normalize(text) }
        val need = LedFont.columns(t) + 1
        val ideal = (maxWidth.value / need).coerceIn(minPitch.value, maxPitch.value)
        val n = if (cols > 0) cols else (maxWidth.value / ideal).toInt().coerceAtLeast(8)
        val pitch = maxWidth / n
        val dot = pitch * 0.74f
        LedText(text, dot = dot, gap = pitch - dot, lit = lit, maxCols = n, scroll = scroll, glow = true)
    }
}
