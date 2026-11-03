package app.fitstat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.fitstat.data.Prefs
import app.fitstat.data.SkinAccent
import app.fitstat.data.SkinInfo
import app.fitstat.data.Skins

/**
 * La skin activa ya resuelta (con las personalizaciones del usuario). Todo el sistema de diseño la consulta
 * a través de [Fs.skin]: colores, formas, tipografía, fondo, textos y animaciones salen de aquí.
 */
@Immutable
class SkinStyle(
    val info: SkinInfo,
    val accent: AccentPalette,
    val black: Boolean,
    val cut: String,
    val bg: String,
    val fx: Float,
    val ownFont: Boolean,
    val digits: String,
    val caps: Boolean,
    val codes: Boolean,
    val words: Boolean,
    val anim: Float,
    val mascotOn: Boolean,
) {
    val id: String get() = info.id
    val isDefault: Boolean get() = info.id == Skins.DEFAULT
    val brand: String get() = info.brand
    val isCarmesi: Boolean get() = info.id == "carmesi"
    val isAcero: Boolean get() = info.id == "acero"

    /** Hay animaciones propias de la skin (el interruptor general de animaciones también cuenta). */
    fun animates(m: FsMotion): Boolean = !isDefault && anim > 0f && m.enabled
    fun fxLevel(m: FsMotion): Float = if (animates(m)) anim else 0f

    /** Mascota de cabeza de la skin (null = FITBOT clásico). */
    val head: String? get() = if (isDefault || !mascotOn) null else info.id

    /** Cambia los textos de la app por los de la skin (solo coincidencias exactas de títulos y botones). */
    fun word(s: String): String = if (isDefault || !words) s else (Dictionary.of(info.id)[s] ?: s)

    /** Línea de estado bajo los títulos (solo con «Códigos y marcas técnicas»). */
    fun tag(seed: String): String? {
        if (isDefault || !codes) return null
        val n = 10 + (kotlin.math.abs(seed.hashCode()) % 90)
        return when (info.id) {
            "carmesi" -> "SISTEMA EN LÍNEA · SEC-$n"
            else -> "ACCESO AUTORIZADO // NODO $n"
        }
    }

    companion object {
        val Default = SkinStyle(
            Skins.fitstat, AccentPalette("lavanda", "Lavanda", Color(0xFF9B8CF5), Color(0xFF7863E6)),
            false, "round", "full", 0f, true, "font", false, false, false, 0f, false,
        )

        private fun accentOf(info: SkinInfo, prefs: Prefs): AccentPalette {
            val k = "skin.${info.id}."
            val id = prefs.str(k + "accent")
            if (id == "custom") {
                val c = parseHexColor(prefs.str(k + "custom")) ?: Color(info.accents.first().a1)
                return Palettes.fromColor(c, "custom", "Personalizado")
            }
            val a: SkinAccent = info.accents.firstOrNull { it.id == id } ?: info.accents.first()
            return AccentPalette(a.id, a.label, Color(a.a1), Color(a.a2))
        }

        fun from(prefs: Prefs): SkinStyle {
            val info = Skins.byId(prefs.str("skin.id"))
            if (info.id == Skins.DEFAULT) return Default
            val k = "skin.${info.id}."
            return SkinStyle(
                info = info,
                accent = accentOf(info, prefs),
                black = prefs.str(k + "surface") == "black",
                cut = prefs.str(k + "cut"),
                bg = prefs.str(k + "bg"),
                fx = prefs.float(k + "fx") / 100f,
                ownFont = prefs.bool(k + "font"),
                digits = prefs.str(k + "led"),
                caps = prefs.bool(k + "caps"),
                codes = prefs.bool(k + "codes"),
                words = prefs.bool(k + "words"),
                anim = when (prefs.str(k + "anim")) { "off" -> 0f; "soft" -> 0.5f; else -> 1f },
                mascotOn = prefs.bool(k + "mascot"),
            )
        }
    }
}

val LocalSkin = staticCompositionLocalOf { SkinStyle.Default }

/** Colores de la skin: siempre oscuros, con el acento de la skin (o el que eligió el usuario). */
fun skinColors(s: SkinStyle): FsColors {
    val a = s.accent.accent
    val a2 = s.accent.accent2
    val light = a.luminance() > 0.34f
    return when (s.id) {
        "carmesi" -> {
            val t = a.mix(Color.Black, 0.55f)
            fun sf(base: Color, k: Float) = base.mix(t, 0.07f * k)
            FsColors(
                dark = true,
                bg = if (s.black) Color(0xFF000000) else sf(Color(0xFF0C0A0D), 0.5f),
                surface = if (s.black) sf(Color(0xFF0E0B0E), 0.8f) else sf(Color(0xFF161317), 0.8f),
                surface2 = if (s.black) sf(Color(0xFF171318), 1f) else sf(Color(0xFF1F1B20), 1f),
                surface3 = if (s.black) sf(Color(0xFF211C22), 1f) else sf(Color(0xFF2A252B), 1.1f),
                outline = sf(Color(0xFF342E35), 1f),
                text = Color(0xFFF2EDED), textDim = Color(0xFFA9A0A4), textFaint = Color(0xFF72696E),
                accent = a, accent2 = a2,
                onAccent = if (light) Color(0xFF140A0C) else Color.White,
                accentText = if (light) a else a.mix(Color.White, 0.22f),
                accentSoft = a.a(0.16f),
                good = Color(0xFF4FE0A0), warn = Color(0xFFFFB02E), bad = Color(0xFFFF7A5C),
                scrim = Color(0xB0000000),
            )
        }
        else -> {
            val t = a.mix(Color.Black, 0.6f)
            fun sf(base: Color, k: Float) = base.mix(t, 0.06f * k)
            FsColors(
                dark = true,
                bg = if (s.black) Color(0xFF000000) else sf(Color(0xFF05070A), 0.5f),
                surface = if (s.black) sf(Color(0xFF080C10), 0.8f) else sf(Color(0xFF0B1016), 0.8f),
                surface2 = if (s.black) sf(Color(0xFF0F161D), 1f) else sf(Color(0xFF111A22), 1f),
                surface3 = if (s.black) sf(Color(0xFF17212A), 1f) else sf(Color(0xFF1A2630), 1.1f),
                outline = a.mix(Color(0xFF1A2330), 0.80f).a(0.55f).on(Color(0xFF0B1016)),
                text = Color(0xFFE8F0F8), textDim = Color(0xFF93A6B9), textFaint = Color(0xFF5F7285),
                accent = a, accent2 = a2,
                onAccent = if (light) Color(0xFF05080C) else Color.White,
                accentText = if (light) a else a.mix(Color.White, 0.25f),
                accentSoft = a.a(0.15f),
                good = Color(0xFF5FE3A1), warn = Color(0xFFFFC857), bad = Color(0xFFFF6B6B),
                scrim = Color(0xB3000000),
            )
        }
    }
}

/** Diccionarios de «textos temáticos» por skin. Solo se cambian títulos de pantalla, secciones y botones conocidos. */
private object Dictionary {
    private val carmesi: Map<String, String> = mapOf(
        "Inicio" to "Base", "Ejercicios" to "Arsenal", "Rutinas" to "Misiones", "Ajustes" to "Sistema", "Progreso" to "Informe",
        "Hitos" to "Condecoraciones", "Anotaciones" to "Bitácora", "Notas" to "Bitácora", "Anotaciones fijadas" to "Bitácora fijada",
        "Récords recientes" to "Récords de campo", "Actividad" to "Despliegue", "Mapa muscular" to "Escaneo corporal",
        "Hoy" to "Hoy en misión", "Mi set" to "Mi equipo", "Mi rutina" to "Mi misión",
        "Empezar entrenamiento" to "Iniciar misión", "Empezar" to "Iniciar", "Comenzar" to "Iniciar",
        "Entrenamiento en curso" to "Misión en curso", "Finalizar entrenamiento" to "Cerrar misión", "Finalizar" to "Cerrar misión",
        "Cancelar entrenamiento" to "Abortar misión", "Seguir entrenando" to "Continuar misión", "Entrenamiento" to "Misión",
        "Resumen" to "Informe de misión", "Registro de cambios" to "Bitácora de versiones",
        "Personalización" to "Ajuste fino", "Tu app" to "Tu sistema", "Tus datos" to "Archivo",
        "Crear rutina" to "Crear misión", "Nueva rutina" to "Nueva misión", "Elegir ejercicios" to "Elegir arsenal",
        "Empezar a entrenar" to "Iniciar despliegue", "Registrar serie" to "Registrar serie de combate",
        "FITBOT" to "Centinela",
    )
    private val acero: Map<String, String> = mapOf(
        "Inicio" to "Mando", "Ejercicios" to "Equipo", "Rutinas" to "Operaciones", "Ajustes" to "Config", "Progreso" to "Reporte",
        "Hitos" to "Rangos", "Anotaciones" to "Registro", "Notas" to "Registro", "Anotaciones fijadas" to "Registro fijado",
        "Récords recientes" to "Marcas recientes", "Actividad" to "Historial", "Mapa muscular" to "Análisis corporal",
        "Hoy" to "Plan de hoy", "Mi set" to "Mi equipo", "Mi rutina" to "Mi operación",
        "Empezar entrenamiento" to "Iniciar operación", "Empezar" to "Iniciar", "Comenzar" to "Iniciar",
        "Entrenamiento en curso" to "Operación en curso", "Finalizar entrenamiento" to "Cerrar operación", "Finalizar" to "Cerrar operación",
        "Cancelar entrenamiento" to "Abortar operación", "Seguir entrenando" to "Continuar operación", "Entrenamiento" to "Operación",
        "Resumen" to "Parte de operación", "Registro de cambios" to "Registro de versiones",
        "Personalización" to "Calibración", "Tu app" to "Tu terminal", "Tus datos" to "Archivo",
        "Crear rutina" to "Crear operación", "Nueva rutina" to "Nueva operación", "Elegir ejercicios" to "Elegir equipo",
        "Empezar a entrenar" to "Iniciar operación", "Registrar serie" to "Registrar serie",
        "FITBOT" to "Vigía",
    )

    fun of(id: String): Map<String, String> = when (id) {
        "carmesi" -> carmesi
        "acero" -> acero
        else -> emptyMap()
    }
}
