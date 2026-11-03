package app.fitstat.data

/**
 * Tamaños de un widget del inicio. La pantalla se divide en [HomeLayout.COLUMNS] columnas:
 * un widget pequeño ocupa 2 (caben tres por fila), uno mediano 3 (caben dos) y uno grande toda la fila.
 */
enum class HomeSize(val id: String, val span: Int, val label: String) {
    Small("s", 2, "Pequeño"),
    Medium("m", 3, "Mediano"),
    Large("l", 6, "Grande");

    companion object {
        fun of(id: String): HomeSize? = entries.firstOrNull { it.id == id }
    }
}

/** Un widget que se puede poner en el inicio. [icon] es el nombre de un ícono (ver icOf). */
class HomeWidgetDef(
    val id: String,
    val title: String,
    val desc: String,
    val icon: String,
    /** Tamaños que admite, del más chico al más grande. */
    val sizes: List<HomeSize>,
    val default: HomeSize,
    /** Ajuste antiguo («Tarjetas visibles», antes de v1.6) que lo apagaba; se respeta al actualizar. */
    val legacyKey: String? = null,
    /** Esquinas «interiores» (azulejos pequeños) en lugar de las de tarjeta. */
    val inner: Boolean = false,
    /** Sin datos no se muestra en el inicio (en el modo de edición sí, para poder moverlo). */
    val hideWhenEmpty: Boolean = false,
    /** No viene en la disposición de siempre: se agrega a mano o desde su conector. */
    val optIn: Boolean = false,
    /** null = widget del inicio (se puede poner en cualquier pantalla); si no, es un bloque propio de esa pantalla (ver [LayoutPage]). */
    val page: String? = null,
    /** Si se puede quitar de la pantalla. Las tarjetas de tus rutinas no: se acomodan, pero no se ocultan. */
    val removable: Boolean = true,
)

/**
 * Las pantallas cuya distribución se puede editar: el inicio, Ejercicios y Rutinas. En todas se mantiene pulsado un bloque
 * (o la pantalla) para mover, achicar, agrandar, quitar y agregar. En las tres se pueden poner los widgets del inicio.
 */
object LayoutPage {
    const val HOME = "home"
    const val EXERCISES = "exercises"
    const val ROUTINES = "routines"
    val all = listOf(HOME, EXERCISES, ROUTINES)

    fun title(page: String): String = when (page) {
        EXERCISES -> "Ejercicios"
        ROUTINES -> "Rutinas"
        else -> "Inicio"
    }

    /** Prefijo de las etiquetas de prueba (testTag) de cada pantalla. */
    fun tag(page: String): String = page

    /** «inicio», «ejercicios», «rutinas»… → la pantalla, o null si no existe. */
    fun find(ref: String?): String? = when (Search.norm(ref.orEmpty())) {
        "", "inicio", "home", "hub", "principal" -> HOME
        "ejercicios", "exercises", "ejercicio" -> EXERCISES
        "rutinas", "routines", "rutina" -> ROUTINES
        else -> null
    }
}

/**
 * Lógica pura de la pantalla de inicio personalizable: qué widgets existen, la disposición de siempre,
 * y las operaciones de quitar, agregar, cambiar de tamaño y mover. No depende de la interfaz.
 */
object HomeLayout {
    const val COLUMNS = 6

    private val S = HomeSize.Small
    private val M = HomeSize.Medium
    private val L = HomeSize.Large

    /** El orden de esta lista es la disposición de siempre del inicio. */
    val all: List<HomeWidgetDef> = listOf(
        HomeWidgetDef("goal", "Meta semanal", "Días entrenados esta semana frente a tu meta.", "Target", listOf(M, L), L, "home.show_goal"),
        HomeWidgetDef("today", "Hoy", "Las rutinas y ejercicios que tocan hoy, con botón para empezar.", "CalendarCheck", listOf(M, L), L, "home.show_today"),
        HomeWidgetDef("train", "Entrenar", "El botón para empezar a entrenar ya.", "Play", listOf(S, M, L), M),
        HomeWidgetDef("fitbot", "FITBOT", "Atajo al chat con tu asistente.", "Bot", listOf(S, M, L), M),
        HomeWidgetDef("progress", "Progreso", "Atajo a tus estadísticas y gráficas.", "Stats", listOf(S, M, L), S, inner = true),
        HomeWidgetDef("milestones", "Hitos", "Atajo a tus logros y cuántos llevas.", "Trophy", listOf(S, M, L), S, inner = true),
        HomeWidgetDef("notes", "Notas", "Atajo a tus anotaciones del gym.", "NotebookPen", listOf(S, M, L), S, inner = true),
        HomeWidgetDef("streak", "Racha", "Semanas seguidas cumpliendo tu meta.", "Flame", listOf(S, M, L), M, "home.show_streak", inner = true),
        HomeWidgetDef("volume", "Volumen semanal", "Los kilos que moviste esta semana.", "TrendUp", listOf(S, M, L), M, "home.show_streak", inner = true),
        HomeWidgetDef("sleep", "Sueño", "Cómo dormiste: la última noche contra tu meta y la tendencia de la semana.", "Moon", listOf(S, M, L), L, inner = true),
        HomeWidgetDef("records", "Récords recientes", "Tus últimos récords personales.", "Star", listOf(M, L), L, "home.show_records", hideWhenEmpty = true),
        HomeWidgetDef("milestone", "Próximo hito", "El siguiente logro que estás por desbloquear.", "Medal", listOf(M, L), L, "home.show_milestone", hideWhenEmpty = true),
        HomeWidgetDef("muscles", "Mapa muscular", "Los músculos que trabajaste en los últimos 7 días.", "Body", listOf(L), L, "home.show_muscles"),
        HomeWidgetDef("heatmap", "Calendario de actividad", "Mapa de calor de tus últimas semanas.", "CalendarDays", listOf(L), L, "home.show_heatmap"),
        HomeWidgetDef("pinned", "Anotaciones fijadas", "Tus notas fijadas del gym.", "Pin", listOf(M, L), L, "home.show_notes", hideWhenEmpty = true),
        HomeWidgetDef("music", "Música", "La portada y los controles de tu música (Namida, Spotify o YouTube Music): anterior, play o pausa y siguiente.", "Music", listOf(S, M, L), M, inner = true, optIn = true),
    )

    private val byId: Map<String, HomeWidgetDef> = all.associateBy { it.id }

    const val CAT_PREFIX = "cat:"
    const val ROUTINE_PREFIX = "rt:"

    fun catId(categoryId: String) = CAT_PREFIX + categoryId
    fun routineId(routineId: String) = ROUTINE_PREFIX + routineId

    /** Bloques propios de Ejercicios y de Rutinas (las tarjetas de cada rutina se arman al vuelo, ver [def]). */
    val natives: List<HomeWidgetDef> = listOf(
        HomeWidgetDef("ex_search", "Buscador", "La barra para buscar entre todos los ejercicios.", "Search", listOf(L), L, page = LayoutPage.EXERCISES),
        HomeWidgetDef("ex_set", "Mi set", "Tus ejercicios de siempre.", "ListPlus", listOf(M, L), L, page = LayoutPage.EXERCISES),
    ) + Categories.all.map {
        HomeWidgetDef(catId(it.id), it.name, it.tagline, "Dumbbell", listOf(M, L), M, page = LayoutPage.EXERCISES)
    } + listOf(
        HomeWidgetDef("ex_create", "Crear ejercicio", "Atajo para crear tu propia plantilla de ejercicio.", "Plus", listOf(M, L), L, inner = true, page = LayoutPage.EXERCISES),
        HomeWidgetDef("rt_free", "Entrenamiento libre", "Empieza a entrenar sin rutina, eligiendo los ejercicios sobre la marcha.", "Zap", listOf(M, L), L, inner = true, hideWhenEmpty = true, page = LayoutPage.ROUTINES),
    )

    private val nativeById: Map<String, HomeWidgetDef> = natives.associateBy { it.id }

    private fun routineDef(id: String) =
        HomeWidgetDef(id, "Rutina", "Una de tus rutinas.", "Routines", listOf(M, L), L, page = LayoutPage.ROUTINES, removable = false)

    /** Es la tarjeta de una rutina (su id es «rt:» + el id de la rutina). */
    fun isRoutineBlock(id: String) = id.startsWith(ROUTINE_PREFIX) && id.length > ROUTINE_PREFIX.length

    /** Widgets del inicio y bloques propios de cada pantalla. */
    fun def(id: String): HomeWidgetDef? = byId[id] ?: nativeById[id] ?: if (isRoutineBlock(id)) routineDef(id) else null

    /** Si el bloque se puede poner en la pantalla [page]: los widgets del inicio van en todas; los demás, solo en la suya. */
    fun fits(d: HomeWidgetDef, page: String) = d.page == null || d.page == page

    /** Tamaño real de un widget (siempre uno de los que admite). */
    fun size(slot: HomeSlot): HomeSize {
        val d = def(slot.id) ?: return HomeSize.Medium
        val s = HomeSize.of(slot.size)
        return if (s != null && s in d.sizes) s else d.default
    }

    /** La disposición de siempre del inicio: todos los widgets menos los que se agregan a mano (ver [HomeWidgetDef.optIn]). */
    fun default(): List<HomeSlot> = all.filter { !it.optIn }.map { HomeSlot(it.id, it.default.id) }

    /**
     * La disposición de siempre de una pantalla. En Rutinas lleva una tarjeta por cada rutina ([routineIds], en su orden).
     */
    fun default(page: String, routineIds: List<String> = emptyList()): List<HomeSlot> = when (page) {
        LayoutPage.EXERCISES -> natives.filter { it.page == LayoutPage.EXERCISES }.map { HomeSlot(it.id, it.default.id) }
        LayoutPage.ROUTINES -> routineIds.map { HomeSlot(routineId(it), L.id) } + natives.filter { it.page == LayoutPage.ROUTINES }.map { HomeSlot(it.id, it.default.id) }
        else -> default()
    }

    /**
     * La disposición de quien actualiza desde una versión anterior sin haber personalizado el inicio:
     * es la de siempre, menos las tarjetas que tenía apagadas en «Tarjetas visibles».
     */
    fun legacyDefault(rawSettings: Map<String, String>): List<HomeSlot> =
        all.filter { d -> !d.optIn && (d.legacyKey == null || rawSettings[d.legacyKey] != "false") }.map { HomeSlot(it.id, it.default.id) }

    /** Lo que se muestra en el inicio: lo guardado (limpio) o, si nunca se personalizó, la disposición de siempre. */
    fun effective(stored: List<HomeSlot>?, rawSettings: Map<String, String>): List<HomeSlot> =
        if (stored == null) legacyDefault(rawSettings) else sanitize(stored)

    /**
     * Lo que se muestra en cualquier pantalla editable. En Rutinas se ajusta a las rutinas que hay ([routineIds]):
     * las borradas desaparecen y las nuevas aparecen junto a las demás.
     */
    fun effective(page: String, stored: List<HomeSlot>?, rawSettings: Map<String, String>, routineIds: List<String>): List<HomeSlot> = when (page) {
        LayoutPage.EXERCISES -> if (stored == null) default(page) else sanitize(stored, page)
        LayoutPage.ROUTINES -> if (stored == null) default(page, routineIds) else withRoutines(sanitize(stored, page), routineIds)
        else -> effective(stored, rawSettings)
    }

    /** Pone las tarjetas de [routineIds] (una por rutina): quita las de rutinas que ya no existen y agrega las nuevas junto a las demás. */
    fun withRoutines(slots: List<HomeSlot>, routineIds: List<String>): List<HomeSlot> {
        val want = routineIds.map { routineId(it) }
        val wantSet = want.toSet()
        val kept = slots.filter { !isRoutineBlock(it.id) || it.id in wantSet }
        val have = kept.map { it.id }.toSet()
        val fresh = want.filter { it !in have }.map { HomeSlot(it, L.id) }
        if (fresh.isEmpty()) return kept
        val last = kept.indexOfLast { isRoutineBlock(it.id) }
        if (last >= 0) return kept.take(last + 1) + fresh + kept.drop(last + 1)
        val free = kept.indexOfFirst { it.id == "rt_free" }
        return if (free >= 0) kept.take(free) + fresh + kept.drop(free) else kept + fresh
    }

    /** Corrige el tamaño de un bloque a uno que admita (o null si el bloque ya no existe). */
    private fun fix(s: HomeSlot): HomeSlot? {
        val d = def(s.id) ?: return null
        val want = HomeSize.of(s.size)
        val size = when {
            want != null && want in d.sizes -> want
            want != null -> d.sizes.minByOrNull { kotlin.math.abs(it.span - want.span) } ?: d.default
            else -> d.default
        }
        return HomeSlot(s.id, size.id)
    }

    /** Quita bloques que ya no existen, que no van en la pantalla [page] o repetidos, y corrige tamaños que el bloque no admite. */
    fun sanitize(slots: List<HomeSlot>, page: String = LayoutPage.HOME): List<HomeSlot> {
        val seen = HashSet<String>()
        val out = ArrayList<HomeSlot>(slots.size)
        for (s in slots) {
            val d = def(s.id) ?: continue
            if (!fits(d, page)) continue
            if (!seen.add(s.id)) continue
            out += fix(s) ?: continue
        }
        return out
    }

    // ───────────────────────── Operaciones (devuelven una lista nueva)

    fun remove(slots: List<HomeSlot>, id: String): List<HomeSlot> {
        if (def(id)?.removable == false) return slots
        return slots.filterNot { it.id == id }
    }

    /** Agrega el bloque al final con su tamaño por defecto (si ya está, o no va en esa pantalla, no hace nada). */
    fun add(slots: List<HomeSlot>, id: String, page: String = LayoutPage.HOME): List<HomeSlot> {
        val d = def(id) ?: return slots
        if (!fits(d, page) || isRoutineBlock(id)) return slots
        if (slots.any { it.id == id }) return slots
        return slots + HomeSlot(id, d.default.id)
    }

    /** Bloques que se pueden agregar a la pantalla y todavía no están: primero los propios de la pantalla y luego los widgets del inicio. */
    fun missing(slots: List<HomeSlot>, page: String = LayoutPage.HOME): List<HomeWidgetDef> =
        (natives.filter { it.page == page } + all).filter { d -> slots.none { it.id == d.id } }

    /** El siguiente tamaño (+1 más grande, −1 más chico) que admite el widget, o null si ya está en el límite. */
    fun nextSize(slot: HomeSlot, step: Int): HomeSize? {
        val d = def(slot.id) ?: return null
        val i = d.sizes.indexOf(size(slot))
        val j = i + if (step > 0) 1 else -1
        return d.sizes.getOrNull(j)
    }

    fun canResize(slot: HomeSlot, step: Int): Boolean = nextSize(slot, step) != null

    /** Cambia el tamaño al siguiente más grande (+1) o más chico (−1). No da la vuelta: en el límite no hace nada. */
    fun resize(slots: List<HomeSlot>, id: String, step: Int): List<HomeSlot> {
        val s = slots.firstOrNull { it.id == id } ?: return slots
        val next = nextSize(s, step) ?: return slots
        return slots.map { if (it.id == id) HomeSlot(id, next.id) else it }
    }

    /** Pone un tamaño concreto; si el bloque no lo admite, usa el más parecido que sí. */
    fun setSize(slots: List<HomeSlot>, id: String, size: HomeSize): List<HomeSlot> {
        val s = slots.firstOrNull { it.id == id } ?: return slots
        val fixed = fix(HomeSlot(id, size.id)) ?: return slots
        return if (fixed.size == s.size) slots else slots.map { if (it.id == id) fixed else it }
    }

    /** Mueve el bloque para que quede en la posición [to] de la lista resultante. */
    fun move(slots: List<HomeSlot>, id: String, to: Int): List<HomeSlot> {
        val from = slots.indexOfFirst { it.id == id }
        if (from < 0) return slots
        val list = slots.toMutableList()
        val item = list.removeAt(from)
        val target = to.coerceIn(0, list.size)
        if (target == from) return slots
        list.add(target, item)
        return list
    }

    // ───────────────────────── Acomodo

    /**
     * Reparte widgets en filas de [COLUMNS] columnas, en orden: cada widget va en la fila actual si cabe,
     * y si no, empieza una nueva. [spans] son las columnas de cada widget. Devuelve los índices por fila.
     */
    fun pack(spans: List<Int>): List<List<Int>> {
        val rows = ArrayList<MutableList<Int>>()
        var used = 0
        for ((i, raw) in spans.withIndex()) {
            val span = raw.coerceIn(1, COLUMNS)
            if (rows.isEmpty() || used + span > COLUMNS) {
                rows += mutableListOf(i)
                used = span
            } else {
                rows.last() += i
                used += span
            }
        }
        return rows
    }
}
