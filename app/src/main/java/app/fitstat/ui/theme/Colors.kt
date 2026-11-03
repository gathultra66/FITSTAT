package app.fitstat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

fun Color.mix(other: Color, t: Float): Color = lerp(this, other, t.coerceIn(0f, 1f))
fun Color.a(alpha: Float): Color = copy(alpha = alpha)
fun Color.on(bg: Color): Color = compositeOver(bg)

fun parseHexColor(s: String): Color? {
    val h = s.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or v))
}

fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

@Immutable
class AccentPalette(val id: String, val label: String, val accent: Color, val accent2: Color)

object Palettes {
    val all: List<AccentPalette> = listOf(
        AccentPalette("lavanda", "Lavanda", Color(0xFF9B8CF5), Color(0xFF7863E6)),
        AccentPalette("menta", "Menta", Color(0xFF5CE0B0), Color(0xFF29B98A)),
        AccentPalette("mandarina", "Mandarina", Color(0xFFFF9B5E), Color(0xFFF06A2A)),
        AccentPalette("rosa", "Rosa neón", Color(0xFFFF6FB5), Color(0xFFE53E94)),
        AccentPalette("azul", "Azul eléctrico", Color(0xFF6CA8FF), Color(0xFF3B78EE)),
        AccentPalette("lima", "Lima", Color(0xFFCBF45C), Color(0xFF9CCF2A)),
        AccentPalette("fuego", "Fuego", Color(0xFFFF6B5E), Color(0xFFE23C33)),
        AccentPalette("ambar", "Ámbar", Color(0xFFFFC857), Color(0xFFF1A116)),
        AccentPalette("turquesa", "Turquesa", Color(0xFF4FD9E8), Color(0xFF1FAEC4)),
        AccentPalette("mono", "Monocromo", Color(0xFFE8E8EE), Color(0xFFB4B4C0)),
    )

    fun byId(id: String): AccentPalette? = all.firstOrNull { it.id == id }

    fun fromColor(c: Color, id: String, label: String): AccentPalette =
        AccentPalette(id, label, c, c.mix(Color.Black, 0.22f))
}

@Immutable
class FsColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    /** Color del acento legible sobre las superficies (más oscuro en modo claro). */
    val accentText: Color,
    val accentSoft: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val scrim: Color,
) {
    /** Texto del color de énfasis sobre una tarjeta de color. */
    val onAccentDim: Color get() = onAccent.copy(alpha = 0.62f)
    val ledOff: Color get() = text.copy(alpha = if (dark) 0.10f else 0.12f)
}

fun buildColors(
    dark: Boolean,
    amoled: Boolean,
    palette: AccentPalette,
    tint: Float,
    highContrast: Boolean,
): FsColors {
    val accent = palette.accent
    val accent2 = palette.accent2
    val onAccent = if (accent.luminance() > 0.34f) Color(0xFF14111F) else Color.White
    val tintBase = if (dark) accent.mix(Color.Black, 0.60f) else accent.mix(Color.White, 0.78f)

    fun surf(base: Color, k: Float) = base.mix(tintBase, tint * k)

    return if (dark) {
        val bg = if (amoled) Color(0xFF000000) else Color(0xFF0D0D11)
        val s1 = if (amoled) Color(0xFF0E0E12) else Color(0xFF17171C)
        val s2 = if (amoled) Color(0xFF17171C) else Color(0xFF202027)
        val s3 = if (amoled) Color(0xFF22222A) else Color(0xFF2B2B34)
        FsColors(
            dark = true,
            bg = surf(bg, 0.5f), surface = surf(s1, 0.8f), surface2 = surf(s2, 1f), surface3 = surf(s3, 1.1f),
            outline = surf(Color(0xFF34343F), 1f).let { if (highContrast) it.mix(Color.White, 0.25f) else it },
            text = if (highContrast) Color.White else Color(0xFFF3F3F8),
            textDim = if (highContrast) Color(0xFFD0D0DA) else Color(0xFFA3A3B1),
            textFaint = if (highContrast) Color(0xFF9E9EAC) else Color(0xFF70707E),
            accent = accent, accent2 = accent2, onAccent = onAccent,
            accentText = accent.mix(Color.White, 0.06f),
            accentSoft = accent.a(0.16f),
            good = Color(0xFF5FE3A1), warn = Color(0xFFFFC857), bad = Color(0xFFFF6B6B),
            scrim = Color(0xAA000000),
        )
    } else {
        FsColors(
            dark = false,
            bg = surf(Color(0xFFE8E8EC), 0.6f), surface = surf(Color(0xFFF5F5F8), 0.5f), surface2 = Color(0xFFFFFFFF).mix(tintBase, tint * 0.25f),
            surface3 = surf(Color(0xFFDADAE1), 0.9f),
            outline = surf(Color(0xFFCBCBD5), 0.8f).let { if (highContrast) it.mix(Color.Black, 0.3f) else it },
            text = if (highContrast) Color.Black else Color(0xFF14141A),
            textDim = if (highContrast) Color(0xFF3A3A46) else Color(0xFF5C5C6A),
            textFaint = if (highContrast) Color(0xFF5E5E6C) else Color(0xFF8A8A98),
            accent = accent, accent2 = accent2, onAccent = onAccent,
            accentText = accent.mix(Color.Black, 0.38f),
            accentSoft = accent.a(0.20f),
            good = Color(0xFF1F9E65), warn = Color(0xFFB9810A), bad = Color(0xFFD64545),
            scrim = Color(0x66000000),
        )
    }
}
