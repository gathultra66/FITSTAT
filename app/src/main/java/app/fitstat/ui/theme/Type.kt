@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.fitstat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.fitstat.R

object Fonts {
    private val weights = listOf(300, 400, 500, 600, 700, 800)

    private fun variable(res: Int): FontFamily = FontFamily(
        weights.map { w ->
            Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )

    private val cache = HashMap<String, FontFamily>()

    fun family(id: String): FontFamily = cache.getOrPut(id) {
        when (id) {
            "manrope" -> variable(R.font.manrope)
            "dm_sans" -> variable(R.font.dm_sans)
            "outfit" -> variable(R.font.outfit)
            "sora" -> variable(R.font.sora)
            "space_grotesk" -> variable(R.font.space_grotesk)
            "inter" -> variable(R.font.inter)
            "lexend" -> variable(R.font.lexend)
            "jetbrains_mono" -> variable(R.font.jetbrains_mono)
            "chakra_petch" -> FontFamily(
                Font(R.font.chakra_petch_regular, FontWeight.Normal),
                Font(R.font.chakra_petch_medium, FontWeight.Medium),
                Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
                Font(R.font.chakra_petch_bold, FontWeight.Bold),
                Font(R.font.chakra_petch_bold, FontWeight.ExtraBold),
                Font(R.font.chakra_petch_regular, FontWeight.Light),
            )
            else -> FontFamily.SansSerif
        }
    }

    fun mono(): FontFamily = family("jetbrains_mono")

    /** Recurso de la fuente (para dibujarla sin Compose, p. ej. en los widgets). */
    fun resId(id: String): Int? = when (id) {
        "manrope" -> R.font.manrope
        "dm_sans" -> R.font.dm_sans
        "outfit" -> R.font.outfit
        "sora" -> R.font.sora
        "space_grotesk" -> R.font.space_grotesk
        "inter" -> R.font.inter
        "lexend" -> R.font.lexend
        "jetbrains_mono" -> R.font.jetbrains_mono
        "chakra_petch" -> R.font.chakra_petch_bold
        else -> null
    }
}

/**
 * Estilos de texto de la app. Todo sale de aquí para que la fuente, el tamaño, el grosor y el
 * espaciado de letras se puedan cambiar desde Ajustes.
 */
@Immutable
class FsType(
    val family: FontFamily,
    val scale: Float,
    private val weightShift: Int,
    private val tracking: Float,
    val caps: Boolean,
    val digitsMode: String,
    /** Las skins escriben las etiquetas pequeñas en monoespaciada y con más aire entre letras. */
    val labelMono: Boolean = false,
) {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    fun make(
        size: Float,
        weight: Int = 500,
        lh: Float = 1.3f,
        track: Float = 0f,
        family: FontFamily = this.family,
        shift: Boolean = true,
    ): TextStyle {
        val w = (if (shift) weight + weightShift else weight).coerceIn(300, 800)
        val s = size * scale
        return TextStyle(
            fontFamily = family,
            fontSize = s.sp,
            fontWeight = FontWeight(w),
            lineHeight = (s * lh).sp,
            letterSpacing = (track + tracking).em,
            lineHeightStyle = trim,
        )
    }

    val display = make(44f, 700, 1.05f, -0.02f)
    val title = make(30f, 700, 1.15f, -0.015f)
    val h1 = make(24f, 700, 1.2f, -0.01f)
    val h2 = make(19f, 650, 1.25f)
    val h3 = make(16f, 650, 1.3f)
    val body = make(15f, 500, 1.4f)
    val bodyStrong = make(15f, 650, 1.4f)
    val small = make(13f, 500, 1.35f)
    val smallStrong = make(13f, 650, 1.35f)
    val label = if (labelMono) make(11.5f, 600, 1.3f, 0.09f, Fonts.mono(), shift = false) else make(12f, 600, 1.3f, 0.01f)
    val caption = if (labelMono) make(10.5f, 500, 1.3f, 0.07f, Fonts.mono(), shift = false) else make(11f, 500, 1.3f, 0.01f)

    /** Números grandes (según el ajuste 'Números grandes'). */
    val bigNumber: TextStyle
        get() = when (digitsMode) {
            "mono" -> make(40f, 700, 1.05f, -0.02f, Fonts.mono())
            else -> make(40f, 700, 1.05f, -0.02f)
        }

    val mono = make(13f, 500, 1.4f, 0f, Fonts.mono(), shift = false)
}
