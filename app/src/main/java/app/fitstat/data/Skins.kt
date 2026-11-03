package app.fitstat.data

/**
 * Skins: temas completos que cambian toda la apariencia de la app (colores, formas, tipografía, fondo,
 * mascota, logo, ícono del lanzador y animaciones). Aquí solo hay datos simples (sin Compose) para que el
 * esquema de ajustes y FITBOT puedan usarlos; el dibujo está en ui/theme/Skin.kt y ui/design/Skin*.kt.
 */
class SkinAccent(val id: String, val label: String, val a1: Long, val a2: Long)

class SkinInfo(
    val id: String,
    val name: String,
    val tagline: String,
    /** Marca que sale en los rótulos LED y la pantalla de inicio. */
    val brand: String,
    /** Nombre de la división (segunda línea del logo). */
    val unit: String,
    /** Nombre en clave de la mascota. */
    val callsign: String,
    val accents: List<SkinAccent>,
    val defCut: String,
    val defBg: String,
    val defFx: Int,
    val defDigits: String,
    val defFont: String,
)

object Skins {
    const val DEFAULT = "fitstat"

    val fitstat = SkinInfo(
        "fitstat", "FITSTAT", "El diseño original: redondo, suave y lleno de color.",
        "FITSTAT", "", "FITBOT", emptyList(), "round", "full", 0, "font", "manrope",
    )

    val carmesi = SkinInfo(
        "carmesi", "ARASAKA", "Corporación de rendimiento: tarjetas oscuras, puntos LED y alerta roja.",
        "ARASAKA", "DIV. APTITUD", "CENTINELA",
        listOf(
            SkinAccent("base", "Rojo Arasaka", 0xFFE30613, 0xFF8F0410),
            SkinAccent("escarlata", "Escarlata", 0xFFFF3B2F, 0xFFC4231A),
            SkinAccent("naranja", "Naranja térmico", 0xFFFF5A1F, 0xFFD13E0A),
            SkinAccent("magenta", "Magenta", 0xFFE5207A, 0xFFA8115A),
            SkinAccent("hueso", "Hueso", 0xFFE9E3D8, 0xFFB5AEA0),
        ),
        "round", "full", 60, "led", "space_grotesk",
    )

    val acero = SkinInfo(
        "acero", "MILITECH", "Terminal industrial: bloques sólidos, esquinas cortadas y códigos técnicos.",
        "MILITECH", "OPS TÁCTICAS", "VIGÍA",
        listOf(
            SkinAccent("base", "Amarillo Militech", 0xFFFCEE0A, 0xFF9A8B00),
            SkinAccent("plata", "Plata", 0xFFC9D1DB, 0xFF8E99A6),
            SkinAccent("cobalto", "Cobalto", 0xFF4F86F7, 0xFF2C5FCC),
            SkinAccent("hielo", "Cian hielo", 0xFF7FE0EE, 0xFF3FB4C6),
            SkinAccent("arena", "Arena", 0xFFE8D93C, 0xFFA89400),
        ),
        "cut", "full", 45, "mono", "chakra_petch",
    )

    val all: List<SkinInfo> = listOf(fitstat, carmesi, acero)
    val special: List<SkinInfo> = listOf(carmesi, acero)

    fun byId(id: String): SkinInfo = all.firstOrNull { it.id == id } ?: fitstat
}
