package app.fitstat.bot

import app.fitstat.data.Categories
import app.fitstat.data.Exercise
import app.fitstat.data.Fmt
import app.fitstat.data.HomeLayout
import app.fitstat.data.HomeSize
import app.fitstat.data.HomeSlot
import app.fitstat.data.HomeWidgetDef
import app.fitstat.data.LayoutPage
import app.fitstat.data.Note
import app.fitstat.data.Reminder
import app.fitstat.data.Routine
import app.fitstat.data.Repo
import app.fitstat.data.SType
import app.fitstat.data.Search
import app.fitstat.data.SettingSpec
import app.fitstat.data.Settings
import app.fitstat.data.Weekdays
import app.fitstat.ui.app.Route
import app.fitstat.ui.screens.routineIcons
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate

// ───────────────────────── Piezas compartidas

private fun refs(a: JsonObject, vararg keys: String): List<String> = keys.flatMap { a.list(it) }.distinct()

private fun primStr(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull?.trim()

internal fun shownValue(sp: SettingSpec, v: String): String = when (val t = sp.type) {
    is SType.Choice -> t.options.firstOrNull { it.value == v }?.label ?: v
    SType.Toggle -> if (v == "true") "sí" else "no"
    is SType.Slider -> v + t.unit
    else -> v
}

internal fun noExercise(c: ToolCtx, ref: String?): ToolOutcome =
    fail("No encontré el ejercicio '${ref.orEmpty()}'. Parecidos: ${c.suggestionText(ref.orEmpty())}.")

internal fun noRoutine(c: ToolCtx, ref: String?): ToolOutcome =
    fail(
        "No encontré la rutina '${ref.orEmpty()}'. " +
            if (c.repo.routines.isEmpty()) "Todavía no hay rutinas." else "Rutinas: " + c.repo.routines.joinToString("; ") { "${it.name} (id ${it.id})" } + ".",
    )

private fun findSpec(key: String): SettingSpec? {
    Settings.byKey[key]?.let { return it }
    val n = Search.norm(key)
    return Settings.all.firstOrNull { Search.norm(it.key) == n || Search.norm(it.title) == n }
}

/** Acepta {changes:[{key,value}]}, {changes:{clave:valor}} o {key,value} sueltos. */
internal fun readChanges(a: JsonObject): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    when (val ch = a["changes"] ?: a["settings"]) {
        is JsonArray -> for (e in ch) {
            val o = e as? JsonObject ?: continue
            val k = o.s("key") ?: o.s("name") ?: o.s("setting") ?: continue
            val v = primStr(o["value"]) ?: continue
            out += k to v
        }
        is JsonObject -> for ((k, v) in ch) primStr(v)?.let { out += k to it }
        else -> {}
    }
    val k = a.s("key")
    val v = primStr(a["value"])
    if (k != null && v != null) out += k to v
    return out
}


/** Un widget del inicio por su id o por su nombre («racha», «mapa muscular»…). */
internal fun findWidget(ref: String): HomeWidgetDef? {
    val n = Search.norm(ref)
    if (n.isEmpty()) return null
    return HomeLayout.all.firstOrNull { it.id == n || Search.norm(it.id) == n || Search.norm(it.title) == n }
        ?: HomeLayout.all.firstOrNull { Search.norm(it.title).contains(n) || (n.length >= 4 && n.contains(Search.norm(it.title))) }
}

/** Un bloque de la pantalla [page] por su id o su nombre (widgets del inicio, categorías, nombre de una rutina…). */
internal fun findBlock(ref: String, page: String, routines: List<Routine> = emptyList()): HomeWidgetDef? {
    val raw = ref.trim()
    val n = Search.norm(raw)
    if (n.isEmpty()) return null
    if (page == LayoutPage.ROUTINES) {
        val r = routines.firstOrNull { it.id == raw || HomeLayout.routineId(it.id) == raw || Search.norm(it.name) == n }
            ?: routines.firstOrNull { n.length >= 3 && Search.norm(it.name).contains(n) }
        if (r != null) return HomeLayout.def(HomeLayout.routineId(r.id))
    }
    val own = HomeLayout.natives.filter { it.page == page }
    own.firstOrNull { it.id.equals(raw, true) || Search.norm(it.title) == n }?.let { return it }
    findWidget(ref)?.let { return it }
    return own.firstOrNull { n.length >= 3 && Search.norm(it.title).contains(n) }
}

/** El nombre de un bloque (las tarjetas de rutina llevan el nombre de su rutina). */
internal fun blockTitleOf(id: String, routines: List<Routine>): String =
    if (HomeLayout.isRoutineBlock(id)) routines.firstOrNull { HomeLayout.routineId(it.id) == id }?.name ?: "Rutina"
    else HomeLayout.def(id)?.title ?: id

/** "s", "pequeño", "chico", "m", "mediano", "l", "grande"… → tamaño. */
internal fun parseHomeSize(raw: String): HomeSize? = when (Search.norm(raw)) {
    "s", "small", "pequeno", "chico", "chica", "pequena" -> HomeSize.Small
    "m", "medium", "mediano", "mediana" -> HomeSize.Medium
    "l", "large", "grande", "ancho", "completo" -> HomeSize.Large
    else -> null
}

internal fun homeLayoutJson(slots: List<HomeSlot>, routines: List<Routine> = emptyList()): List<JsonObject> = slots.mapIndexed { i, s ->
    buildJsonObject {
        put("position", i + 1)
        put("id", s.id)
        put("title", blockTitleOf(s.id, routines))
        put("size", HomeLayout.size(s).id)
        put("size_name", HomeLayout.size(s).label)
        if (HomeLayout.def(s.id)?.removable == false) put("removable", false)
    }
}

/**
 * Aplica los cambios de «edit_home» sobre [start] (la pantalla [page]). Devuelve la lista nueva y las observaciones
 * (avisos de lo que no se pudo).
 */
internal fun applyHomeEdits(
    a: JsonObject,
    start: List<HomeSlot>,
    page: String = LayoutPage.HOME,
    routines: List<Routine> = emptyList(),
    defaults: List<HomeSlot> = if (page == LayoutPage.HOME) HomeLayout.default() else HomeLayout.default(page, routines.map { it.id }),
): Pair<List<HomeSlot>, List<String>> {
    var l = start
    val notes = ArrayList<String>()
    val where = LayoutPage.title(page).lowercase()
    fun find(ref: String) = findBlock(ref, page, routines)
    if (a.b("reset") == true) l = defaults
    for (ref in a.list("remove")) {
        val d = find(ref)
        when {
            d == null -> notes += "No existe el bloque «$ref»."
            !d.removable -> notes += "La tarjeta de «${blockTitleOf(d.id, routines)}» no se quita (las rutinas se acomodan, no se ocultan)."
            l.none { it.id == d.id } -> notes += "«${d.title}» ya no estaba en $where."
            else -> l = HomeLayout.remove(l, d.id)
        }
    }
    for (ref in a.list("add")) {
        val d = find(ref)
        when {
            d == null -> notes += "No existe el bloque «$ref»."
            !HomeLayout.fits(d, page) -> notes += "«${d.title}» no va en $where."
            l.any { it.id == d.id } -> notes += "«${blockTitleOf(d.id, routines)}» ya estaba en $where."
            HomeLayout.isRoutineBlock(d.id) -> notes += "Las tarjetas de rutina ya están siempre en $where."
            else -> l = HomeLayout.add(l, d.id, page)
        }
    }
    for (e in (a["resize"] as? JsonArray).orEmpty()) {
        val o = e as? JsonObject ?: continue
        val ref = o.s("id") ?: o.s("widget") ?: continue
        val d = find(ref)
        val size = o.s("size")?.let { parseHomeSize(it) }
        when {
            d == null -> notes += "No existe el bloque «$ref»."
            size == null -> notes += "Tamaño no válido para «${blockTitleOf(d.id, routines)}»: usa s (pequeño), m (mediano) o l (grande)."
            l.none { it.id == d.id } -> notes += "«${blockTitleOf(d.id, routines)}» no está en $where; agrégalo primero."
            else -> {
                val next = HomeLayout.setSize(l, d.id, size)
                if (size !in d.sizes) notes += "«${blockTitleOf(d.id, routines)}» no admite ${size.label.lowercase()}; usé el más parecido."
                l = next
            }
        }
    }
    for (e in (a["move"] as? JsonArray).orEmpty()) {
        val o = e as? JsonObject ?: continue
        val ref = o.s("id") ?: o.s("widget") ?: continue
        val d = find(ref)
        val pos = o.i("position")
        when {
            d == null -> notes += "No existe el bloque «$ref»."
            pos == null -> notes += "Falta la posición (1 = arriba del todo) para «${blockTitleOf(d.id, routines)}»."
            l.none { it.id == d.id } -> notes += "«${blockTitleOf(d.id, routines)}» no está en $where; agrégalo primero."
            else -> l = HomeLayout.move(l, d.id, pos - 1)
        }
    }
    return HomeLayout.sanitize(l, page) to notes
}

/** "18:30", "6:30 pm", "6pm", "18" → (hora, minuto). */
internal fun parseTime(raw: String): Pair<Int, Int>? {
    val t = raw.trim().lowercase().replace(".", "").replace(" ", "")
    val m = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm|h|hrs)?$").matchEntire(t) ?: return null
    var h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    when (m.groupValues[3]) {
        "pm" -> if (h in 1..11) h += 12
        "am" -> if (h == 12) h = 0
    }
    return if (h in 0..23 && min in 0..59) h to min else null
}

/** "10" → (10, 0); "8-12" → (8, 12). */
internal fun parseReps(raw: String?): Pair<Int, Int>? {
    val t = raw?.trim()?.lowercase()?.replace("–", "-")?.replace(" a ", "-")?.replace(" ", "") ?: return null
    val m = Regex("^(\\d{1,3})(?:-(\\d{1,3}))?$").matchEntire(t) ?: return null
    val lo = m.groupValues[1].toInt()
    val hi = m.groupValues[2].toIntOrNull() ?: 0
    if (lo !in 1..300) return null
    return lo to (if (hi > lo) hi else 0)
}

internal fun parseDate(raw: String, today: LocalDate): LocalDate? = when (Search.norm(raw)) {
    "hoy", "today" -> today
    "ayer", "yesterday" -> today.minusDays(1)
    "anteayer", "antier", "antes de ayer" -> today.minusDays(2)
    else -> runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
}

private fun findNote(c: ToolCtx, ref: String): Note? {
    val q = ref.trim()
    if (q.isEmpty()) return null
    c.repo.notes.firstOrNull { it.id == q }?.let { return it }
    val n = Search.norm(q)
    c.repo.notes.firstOrNull { Search.norm(it.title) == n }?.let { return it }
    return c.repo.notes.filter { Search.norm(it.title).contains(n) }.singleOrNull()
}

private fun findReminder(c: ToolCtx, ref: String): Reminder? {
    val q = ref.trim()
    if (q.isEmpty()) return null
    c.repo.reminders.firstOrNull { it.id == q }?.let { return it }
    val n = Search.norm(q)
    c.repo.reminders.firstOrNull { Search.norm(it.title) == n }?.let { return it }
    return c.repo.reminders.filter { Search.norm(it.title).contains(n) }.singleOrNull()
}

private fun configJson(c: ToolCtx, ex: Exercise): JsonObject = buildJsonObject {
    val cfg = c.repo.config(ex.id)
    put("exercise", ex.name)
    put("in_my_set", cfg.inSet)
    put("sets", cfg.sets)
    put("reps", cfg.repsLabel)
    put("rest_sec", cfg.restSec)
    put("days_label", Weekdays.label(cfg.days))
    put("notes", cfg.notes)
    put("favorite", cfg.favorite)
    put("target_weight", c.disp(cfg.targetWeightKg))
}

private fun reminderJson(m: Reminder, c: ToolCtx): JsonObject = buildJsonObject {
    put("id", m.id)
    put("title", m.title)
    put("time", Fmt.time(m.hour, m.minute))
    put("days_label", Weekdays.label(m.days))
    put("enabled", m.enabled)
    m.routineId?.let { id -> put("routine", c.repo.routine(id)?.name ?: id) }
}

private fun noteJson(n: Note, c: ToolCtx): JsonObject = buildJsonObject {
    put("id", n.id)
    put("title", n.title)
    put("body", n.body.take(300))
    put("pinned", n.pinned)
    n.exerciseId?.let { id -> put("exercise", c.repo.exercise(id)?.name ?: id) }
}

private fun muscleList(a: JsonObject, key: String): List<app.fitstat.data.Muscle> = a.list(key).mapNotNull { parseMuscle(it) }.distinct()

private val dayParam = S.strs("Días: lunes…domingo, 1 (lunes) a 7 (domingo), 'entre semana', 'fin de semana' o 'todos'.")
private val exercisesParam = S.strs("Ejercicios: nombres o ids.")
private val repsParam = S.str("Repeticiones: '10' o un rango '8-12'.")

// ───────────────────────── Herramientas que cambian datos

internal val writeTools: List<BotTool> = listOf(

    // ───────── Ajustes
    tool(
        "set_settings",
        "Cambia uno o varios ajustes de la app (tema, paleta, tipografía, animaciones, estadísticas, entrenamiento, FITBOT…). Usa list_settings para ver las claves y valores válidos.",
        S.obj(
            listOf(
                "changes" to S.objs(
                    "Cambios a aplicar.",
                    S.obj(listOf("key" to S.str("Clave del ajuste, p. ej. 'theme.mode'."), "value" to S.str("Nuevo valor (true/false en interruptores).")), listOf("key", "value")),
                ),
            ),
            listOf("changes"),
        ),
        write = true,
        plan = { a, _ ->
            "Cambiar ajustes: " + readChanges(a).joinToString(", ") { (k, v) ->
                findSpec(k)?.let { it.title + " → " + shownValue(it, Settings.normalize(it, v) ?: v) } ?: k
            }
        },
    ) { a, c ->
        val changes = readChanges(a)
        if (changes.isEmpty()) return@tool fail("Indica 'changes': una lista de {key, value}.")
        val results = ArrayList<JsonObject>()
        val titles = ArrayList<String>()
        for ((k, raw) in changes.take(30)) {
            val sp = findSpec(k)
            if (sp == null) {
                results += buildJsonObject { put("key", k); put("ok", false); put("error", "No existe ese ajuste. Usa list_settings.") }
                continue
            }
            val before = c.repo.prefs.str(sp.key)
            c.repo.setSetting(sp.key, raw)
                .onSuccess { v ->
                    titles += sp.title + " → " + shownValue(sp, v)
                    results += buildJsonObject {
                        put("key", sp.key)
                        put("ok", true)
                        put("value", v)
                        put("previous", before)
                        sp.visibleIf?.let { (dk, dv) -> if (c.repo.prefs.str(dk) != dv) put("note", "Solo se ve cuando $dk = $dv.") }
                    }
                }
                .onFailure { e ->
                    results += buildJsonObject {
                        put("key", sp.key)
                        put("ok", false)
                        put("error", e.message ?: "Valor no válido.")
                        put("allowed", Settings.optionsText(sp))
                    }
                }
        }
        val summary = if (titles.isEmpty()) "No pude cambiar los ajustes" else "Ajustes: " + titles.joinToString(", ")
        ToolOutcome(titles.isNotEmpty(), buildJsonObject { put("ok", titles.isNotEmpty()); arr("results", results) }, summary)
    },

    tool(
        "reset_settings",
        "Restablece ajustes a su valor original: una lista de claves, una página completa (p. ej. 'tema') o todos.",
        S.obj(
            listOf(
                "keys" to S.strs("Claves a restablecer."),
                "page" to S.str("Página de ajustes a restablecer."),
                "all" to S.bool("true para restablecer todos los ajustes."),
            ),
        ),
        write = true,
        destructive = { it.b("all") == true },
        plan = { a, _ -> if (a.b("all") == true) "Restablecer TODOS los ajustes" else "Restablecer ajustes" },
    ) { a, c ->
        when {
            a.b("all") == true -> {
                c.repo.resetAllSettings()
                ok("Restableció todos los ajustes")
            }
            a.s("page") != null -> {
                val page = resolvePage(a.s("page").orEmpty()) ?: return@tool fail("No existe esa página. Páginas: ${Settings.pages.joinToString { it.id }}.")
                c.repo.resetPage(page)
                ok("Restableció los ajustes de «$page»")
            }
            a.list("keys").isNotEmpty() -> {
                val specs = a.list("keys").mapNotNull { findSpec(it) }
                if (specs.isEmpty()) return@tool fail("Esas claves no existen. Usa list_settings.")
                specs.forEach { c.repo.resetSetting(it.key) }
                ok("Restableció: " + specs.joinToString(", ") { it.title })
            }
            else -> fail("Indica keys, page o all=true.")
        }
    },

    tool(
        "edit_home",
        "Personaliza la distribución de una pantalla (page: inicio, ejercicios o rutinas; por defecto el inicio): agrega, quita, cambia de tamaño o mueve bloques. " +
            "Widgets (valen en las tres pantallas): " +
            HomeLayout.all.joinToString("; ") { "${it.id} (${it.title}, tamaños ${it.sizes.joinToString("/") { s -> s.id }})" } +
            ". Bloques propios: en ejercicios ex_search, ex_set, ex_create y una tarjeta por categoría (cat:<id> o su nombre); en rutinas rt_free y la tarjeta de cada rutina (por su nombre; se mueven y cambian de tamaño, pero no se quitan)." +
            " Tamaños: s = pequeño (3 por fila), m = mediano (2 por fila), l = grande (fila completa). Orden de aplicación: reset, remove, add, resize, move. Usa get_home_layout para ver cómo está ahora.",
        S.obj(
            listOf(
                "page" to S.str("Pantalla: inicio (por defecto), ejercicios o rutinas.", listOf("inicio", "ejercicios", "rutinas")),
                "reset" to S.bool("true para volver primero a la disposición de siempre de esa pantalla."),
                "remove" to S.strs("Bloques a quitar (id o nombre)."),
                "add" to S.strs("Bloques o widgets a agregar al final (id o nombre)."),
                "resize" to S.objs(
                    "Cambios de tamaño.",
                    S.obj(listOf("id" to S.str("Widget."), "size" to S.str("s, m o l.", listOf("s", "m", "l"))), listOf("id", "size")),
                ),
                "move" to S.objs(
                    "Mover widgets a una posición (1 = arriba del todo).",
                    S.obj(listOf("id" to S.str("Widget."), "position" to S.int("Posición final, empezando en 1.")), listOf("id", "position")),
                ),
            ),
        ),
        write = true,
        plan = { a, _ ->
            buildList {
                if (a.b("reset") == true) add("restablecer")
                a.list("remove").takeIf { it.isNotEmpty() }?.let { add("quitar " + it.joinToString(", ")) }
                a.list("add").takeIf { it.isNotEmpty() }?.let { add("agregar " + it.joinToString(", ")) }
                if ((a["resize"] as? JsonArray)?.isNotEmpty() == true) add("cambiar tamaños")
                if ((a["move"] as? JsonArray)?.isNotEmpty() == true) add("reordenar")
            }.joinToString(", ").let { "${LayoutPage.title(LayoutPage.find(a.s("page")) ?: LayoutPage.HOME)}: $it" }
        },
    ) { a, c ->
        val wants = a.b("reset") == true || a.list("remove").isNotEmpty() || a.list("add").isNotEmpty() ||
            (a["resize"] as? JsonArray)?.isNotEmpty() == true || (a["move"] as? JsonArray)?.isNotEmpty() == true
        if (!wants) return@tool fail("Indica al menos uno de: reset, remove, add, resize, move.")
        val page = LayoutPage.find(a.s("page")) ?: return@tool fail("Esa pantalla no existe. Usa inicio, ejercicios o rutinas.")
        val name = LayoutPage.title(page)
        c.host.saveHomeEdit()
        val routines = c.repo.routines
        val before = c.repo.pageSlots(page)
        val (after, notes) = applyHomeEdits(a, before, page, routines)
        if (after == before) {
            return@tool ToolOutcome(false, buildJsonObject { put("ok", false); put("error", "No cambió nada."); put("page", page); strs("notes", notes); arr("layout", homeLayoutJson(before, routines)) }, "$name ya estaba así")
        }
        c.repo.setPageLayout(page, after, "$name cambiado por FITBOT")
        ok("Cambió $name") {
            put("page", page)
            strs("notes", notes)
            arr("layout", homeLayoutJson(c.repo.pageSlots(page), routines))
            strs("not_in_page", HomeLayout.missing(c.repo.pageSlots(page), page).map { it.id })
        }
    },

    tool(
        "set_profile",
        "Cambia el nombre del usuario y/o su meta semanal de entrenamientos.",
        S.obj(listOf("name" to S.str("Nombre."), "weekly_goal" to S.int("Días de entrenamiento por semana (1-7)."))),
        write = true,
    ) { a, c ->
        val name = a.s("name")
        val goal = a.i("weekly_goal")
        if (name == null && goal == null) return@tool fail("Indica name o weekly_goal.")
        name?.let { c.repo.setProfileName(it) }
        goal?.let { c.repo.setSetting("train.goal", it.toString()) }
        ok("Perfil actualizado") {
            put("name", c.repo.profile.name)
            put("weekly_goal", c.repo.weeklyGoal)
        }
    },

    // ───────── Rutinas
    tool(
        "create_routine",
        "Crea una rutina nueva con ejercicios (por nombre o id), días y nota opcionales.",
        S.obj(
            listOf(
                "name" to S.str("Nombre de la rutina."),
                "exercises" to exercisesParam,
                "days" to dayParam,
                "note" to S.str("Nota opcional."),
                "icon" to S.str("Ícono opcional.", routineIcons),
                "color" to S.int("Color 0-9 opcional."),
            ),
            listOf("name"),
        ),
        write = true,
        plan = { a, _ -> "Crear la rutina «${a.s("name") ?: "nueva"}»" },
    ) { a, c ->
        val name = a.s("name") ?: return@tool fail("Falta el nombre de la rutina.")
        c.repo.routines.firstOrNull { Search.norm(it.name) == Search.norm(name) }?.let {
            return@tool fail("Ya existe la rutina «${it.name}» (id ${it.id}). Modifícala con routine_add_exercises / update_routine, o elige otro nombre.")
        }
        val (found, missing) = c.resolveAll(refs(a, "exercises", "exercise"))
        val days = parseDays(a.list("days"))
        val icon = a.s("icon")?.let { i -> routineIcons.firstOrNull { it.equals(i, true) } } ?: "Dumbbell"
        val color = a.i("color")?.takeIf { it in 0 until Repo.RoutineColors } ?: (c.repo.routines.size % Repo.RoutineColors)
        val r = c.repo.createRoutine(name, icon, color, found.map { it.id }, days, a.s("note").orEmpty())
        ok("Creó la rutina «${r.name}»" + if (found.isNotEmpty()) " con ${found.size} ejercicio${if (found.size == 1) "" else "s"}" else "") {
            put("routine", routineJson(r, c, detail = true))
            if (missing.isNotEmpty()) put("not_found", c.missingJson(missing))
        }
    },

    tool(
        "update_routine",
        "Cambia el nombre, los días, la nota, el ícono o el color de una rutina.",
        S.obj(
            listOf(
                "routine" to S.str("Id o nombre de la rutina."),
                "name" to S.str("Nuevo nombre."),
                "days" to dayParam,
                "note" to S.str("Nueva nota (vacía para borrarla)."),
                "icon" to S.str("Ícono.", routineIcons),
                "color" to S.int("Color 0-9."),
            ),
            listOf("routine"),
        ),
        write = true,
        plan = { a, _ -> "Modificar la rutina «${a.s("routine").orEmpty()}»" },
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool noRoutine(c, a.s("routine"))
        val newName = a.s("name")
        val icon = a.s("icon")?.let { i -> routineIcons.firstOrNull { it.equals(i, true) } }
        val color = a.i("color")?.takeIf { it in 0 until Repo.RoutineColors }
        val days = if (a.has("days")) parseDays(a.list("days")) else null
        val note = if (a.has("note")) a.s("note").orEmpty() else null
        if (newName == null && icon == null && color == null && days == null && note == null) return@tool fail("No indicaste nada que cambiar.")
        c.repo.updateRoutine(r.id) {
            it.copy(
                name = newName?.take(40) ?: it.name, icon = icon ?: it.icon, colorIdx = color ?: it.colorIdx,
                days = days ?: it.days, note = note ?: it.note,
            )
        }
        ok("Actualizó la rutina «${newName ?: r.name}»") { put("routine", routineJson(c.repo.routine(r.id) ?: r, c, detail = false)) }
    },

    tool(
        "delete_routine",
        "Elimina una rutina (no borra sus series registradas).",
        S.obj(listOf("routine" to S.str("Id o nombre de la rutina.")), listOf("routine")),
        write = true,
        destructive = { true },
        plan = { a, c -> "Eliminar la rutina «${c.findRoutine(a.s("routine").orEmpty())?.name ?: a.s("routine").orEmpty()}»" },
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool noRoutine(c, a.s("routine"))
        c.repo.deleteRoutine(r.id)
        ok("Eliminó la rutina «${r.name}»")
    },

    tool(
        "duplicate_routine",
        "Crea una copia de una rutina.",
        S.obj(listOf("routine" to S.str("Id o nombre de la rutina.")), listOf("routine")),
        write = true,
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool noRoutine(c, a.s("routine"))
        val copy = c.repo.duplicateRoutine(r.id) ?: return@tool fail("No pude duplicarla.")
        ok("Duplicó «${r.name}»") { put("routine", routineJson(copy, c, detail = false)) }
    },

    tool(
        "routine_add_exercises",
        "Agrega ejercicios al final de una rutina.",
        S.obj(listOf("routine" to S.str("Id o nombre de la rutina."), "exercises" to exercisesParam), listOf("routine", "exercises")),
        write = true,
        plan = { a, _ -> "Agregar ejercicios a la rutina «${a.s("routine").orEmpty()}»" },
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool noRoutine(c, a.s("routine"))
        val (found, missing) = c.resolveAll(refs(a, "exercises", "exercise"))
        if (found.isEmpty()) return@tool fail("No encontré ninguno de esos ejercicios. " + missing.joinToString("; ") { "'$it': ${c.suggestionText(it)}" })
        val have = r.items.map { it.exerciseId }.toSet()
        val added = found.filter { it.id !in have }
        c.repo.addToRoutine(r.id, added.map { it.id })
        ok("Agregó ${added.size} ejercicio${if (added.size == 1) "" else "s"} a «${r.name}»") {
            strs("added", added.map { it.name })
            strs("already_in_routine", found.filter { it.id in have }.map { it.name })
            if (missing.isNotEmpty()) put("not_found", c.missingJson(missing))
            put("routine", routineJson(c.repo.routine(r.id) ?: r, c, detail = true))
        }
    },

    tool(
        "routine_remove_exercises",
        "Quita ejercicios de una rutina (no los borra).",
        S.obj(listOf("routine" to S.str("Id o nombre de la rutina."), "exercises" to exercisesParam), listOf("routine", "exercises")),
        write = true,
        plan = { a, _ -> "Quitar ejercicios de la rutina «${a.s("routine").orEmpty()}»" },
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool noRoutine(c, a.s("routine"))
        val (found, missing) = c.resolveAll(refs(a, "exercises", "exercise"))
        val ids = found.map { it.id }.toSet()
        val inRoutine = found.filter { f -> r.items.any { it.exerciseId == f.id } }
        if (inRoutine.isEmpty()) return@tool fail("Ninguno de esos ejercicios está en «${r.name}».")
        c.repo.updateRoutine(r.id) { rt -> rt.copy(items = rt.items.filterNot { it.exerciseId in ids }) }
        ok("Quitó ${inRoutine.size} ejercicio${if (inRoutine.size == 1) "" else "s"} de «${r.name}»") {
            strs("removed", inRoutine.map { it.name })
            if (missing.isNotEmpty()) put("not_found", c.missingJson(missing))
        }
    },

    tool(
        "routine_set_exercise",
        "Cambia las series y/o repeticiones de un ejercicio dentro de una rutina. Con sets=0 o reps='default' usa los valores del ejercicio.",
        S.obj(
            listOf("routine" to S.str("Id o nombre de la rutina."), "exercise" to S.str("Id o nombre del ejercicio."), "sets" to S.int("Series."), "reps" to repsParam),
            listOf("routine", "exercise"),
        ),
        write = true,
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool noRoutine(c, a.s("routine"))
        val ex = c.findExercise(a.s("exercise").orEmpty()) ?: return@tool noExercise(c, a.s("exercise"))
        if (r.items.none { it.exerciseId == ex.id }) return@tool fail("«${ex.name}» no está en «${r.name}». Agrégalo con routine_add_exercises.")
        val sets = a.i("sets")
        val repsRaw = a.s("reps")
        val resetReps = repsRaw.equals("default", true)
        val reps = if (resetReps) null else parseReps(repsRaw)
        if (sets == null && repsRaw == null) return@tool fail("Indica sets y/o reps.")
        if (repsRaw != null && !resetReps && reps == null) return@tool fail("reps no válido: usa '10' o '8-12'.")
        c.repo.updateRoutine(r.id) { rt ->
            rt.copy(
                items = rt.items.map {
                    if (it.exerciseId != ex.id) it
                    else it.copy(
                        sets = sets?.coerceIn(0, 20) ?: it.sets,
                        repsMin = if (repsRaw == null) it.repsMin else (reps?.first ?: 0),
                        repsMax = if (repsRaw == null) it.repsMax else (reps?.second ?: 0),
                    )
                },
            )
        }
        val item = c.repo.routine(r.id)?.items?.firstOrNull { it.exerciseId == ex.id }
        ok("Ajustó «${ex.name}» en «${r.name}»") {
            if (item != null) {
                put("sets", c.repo.itemSets(item))
                put("reps", c.repo.itemRepsLabel(item))
            }
        }
    },

    tool(
        "routine_reorder",
        "Reordena los ejercicios de una rutina. Los que no menciones quedan al final en su orden original.",
        S.obj(listOf("routine" to S.str("Id o nombre de la rutina."), "order" to S.strs("Ejercicios (nombres o ids) en el nuevo orden.")), listOf("routine", "order")),
        write = true,
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool noRoutine(c, a.s("routine"))
        val (found, missing) = c.resolveAll(a.list("order"))
        val inRoutine = found.map { it.id }.filter { id -> r.items.any { it.exerciseId == id } }
        if (inRoutine.isEmpty()) return@tool fail("Ninguno de esos ejercicios está en «${r.name}».")
        c.repo.updateRoutine(r.id) { rt ->
            val first = inRoutine.mapNotNull { id -> rt.items.firstOrNull { it.exerciseId == id } }
            rt.copy(items = first + rt.items.filter { it !in first })
        }
        ok("Reordenó «${r.name}»") {
            put("routine", routineJson(c.repo.routine(r.id) ?: r, c, detail = true))
            if (missing.isNotEmpty()) put("not_found", c.missingJson(missing))
        }
    },

    // ───────── Mi set y ejercicios
    tool(
        "add_to_my_set",
        "Agrega ejercicios a «mi set» (los ejercicios que el usuario hace habitualmente).",
        S.obj(listOf("exercises" to exercisesParam), listOf("exercises")),
        write = true,
    ) { a, c ->
        val (found, missing) = c.resolveAll(refs(a, "exercises", "exercise"))
        if (found.isEmpty()) return@tool fail("No encontré ejercicios. " + missing.joinToString("; ") { "'$it': ${c.suggestionText(it)}" })
        val fresh = found.filter { !c.repo.inSet(it.id) }
        fresh.forEach { c.repo.addToSet(it.id) }
        ok("Agregó ${fresh.size} ejercicio${if (fresh.size == 1) "" else "s"} a tu set") {
            strs("added", fresh.map { it.name })
            strs("already_in_set", found.filter { c.repo.inSet(it.id) && it !in fresh }.map { it.name })
            if (missing.isNotEmpty()) put("not_found", c.missingJson(missing))
        }
    },

    tool(
        "remove_from_my_set",
        "Quita ejercicios de «mi set» (conserva su historial).",
        S.obj(listOf("exercises" to exercisesParam), listOf("exercises")),
        write = true,
    ) { a, c ->
        val (found, missing) = c.resolveAll(refs(a, "exercises", "exercise"))
        val inSet = found.filter { c.repo.inSet(it.id) }
        if (inSet.isEmpty()) return@tool fail("Ninguno de esos ejercicios está en tu set." + if (missing.isNotEmpty()) " No encontré: " + missing.joinToString() else "")
        inSet.forEach { c.repo.removeFromSet(it.id) }
        ok("Quitó ${inSet.size} ejercicio${if (inSet.size == 1) "" else "s"} de tu set") {
            strs("removed", inSet.map { it.name })
            if (missing.isNotEmpty()) put("not_found", c.missingJson(missing))
        }
    },

    tool(
        "configure_exercise",
        "Configura un ejercicio: series, repeticiones, descanso, días de la semana, anotaciones, favorito, peso objetivo y si está en mi set. Si le pones días y no estaba en tu set, lo agrega.",
        S.obj(
            listOf(
                "exercise" to S.str("Id o nombre del ejercicio."),
                "sets" to S.int("Series."),
                "reps" to repsParam,
                "rest_sec" to S.int("Descanso en segundos."),
                "days" to dayParam,
                "notes" to S.str("Reemplaza las anotaciones (vacío para borrarlas)."),
                "add_note" to S.str("Agrega una línea a las anotaciones."),
                "favorite" to S.bool("Marcar como favorito."),
                "target_weight" to S.num("Peso objetivo en la unidad del usuario."),
                "in_my_set" to S.bool("Agregar o quitar de mi set."),
            ),
            listOf("exercise"),
        ),
        write = true,
        plan = { a, c -> "Configurar «${c.findExercise(a.s("exercise").orEmpty())?.name ?: a.s("exercise").orEmpty()}»" },
    ) { a, c ->
        val ex = c.findExercise(a.s("exercise").orEmpty()) ?: return@tool noExercise(c, a.s("exercise"))
        val cur = c.repo.config(ex.id)
        val sets = a.i("sets")?.coerceIn(1, 20)
        val repsRaw = a.s("reps")
        val reps = parseReps(repsRaw)
        if (repsRaw != null && reps == null) return@tool fail("reps no válido: usa '10' o '8-12'.")
        val rest = a.i("rest_sec")?.coerceIn(0, 900)
        val days = if (a.has("days")) parseDays(a.list("days")) else null
        val notes = if (a.has("notes")) a.s("notes").orEmpty().take(1000) else null
        val addNote = a.s("add_note")
        val fav = a.b("favorite")
        val target = a.d("target_weight")?.let { c.toKg(it.coerceAtLeast(0.0)) }
        var inSet = a.b("in_my_set")
        var joined = false
        if (inSet == null && !days.isNullOrEmpty() && !cur.inSet) {
            inSet = true
            joined = true
        }
        if (sets == null && reps == null && rest == null && days == null && notes == null && addNote == null && fav == null && target == null && inSet == null) {
            return@tool fail("No indicaste nada que cambiar.")
        }
        c.repo.updateConfig(ex.id) { cfg ->
            cfg.copy(
                sets = sets ?: cfg.sets,
                repsMin = reps?.first ?: cfg.repsMin,
                repsMax = reps?.second ?: cfg.repsMax,
                restSec = rest ?: cfg.restSec,
                days = days ?: cfg.days,
                notes = when {
                    notes != null -> notes
                    addNote != null -> (if (cfg.notes.isBlank()) addNote else cfg.notes.trimEnd() + "\n" + addNote).take(1000)
                    else -> cfg.notes
                },
                favorite = fav ?: cfg.favorite,
                targetWeightKg = target ?: cfg.targetWeightKg,
                inSet = inSet ?: cfg.inSet,
            )
        }
        if (inSet == true && !cur.inSet) c.repo.checkMilestones()
        ok("Configuró «${ex.name}»") {
            put("config", configJson(c, ex))
            if (joined) put("note", "Se agregó a tu set para que aparezca en el plan de esos días.")
        }
    },

    tool(
        "create_exercise",
        "Crea un ejercicio propio (plantilla) cuando no existe en el catálogo.",
        S.obj(
            listOf(
                "name" to S.str("Nombre."),
                "category" to S.str("Categoría.", Categories.all.map { it.id }),
                "primary_muscles" to S.strs("Músculos principales: " + app.fitstat.data.Muscle.entries.joinToString { it.label }),
                "secondary_muscles" to S.strs("Músculos secundarios."),
                "equipment" to S.str("Equipo (barra, mancuernas, máquina, peso corporal…)."),
                "level" to S.str("Nivel (principiante, intermedio, avanzado)."),
                "description" to S.str("Descripción breve."),
                "add_to_my_set" to S.bool("Agregarlo a mi set."),
            ),
            listOf("name", "category"),
        ),
        write = true,
        plan = { a, _ -> "Crear el ejercicio «${a.s("name").orEmpty()}»" },
    ) { a, c ->
        val name = a.s("name") ?: return@tool fail("Falta el nombre.")
        val cat = Categories.byId(a.s("category")?.let { parseCategory(it) } ?: "")
            ?: return@tool fail("Elige una categoría: ${Categories.all.joinToString { it.id }}.")
        c.repo.exercises.firstOrNull { Search.norm(it.name) == Search.norm(name) }?.let {
            return@tool fail("Ya existe «${it.name}» (id ${it.id}). Úsalo con ese id.")
        }
        val primary = muscleList(a, "primary_muscles").ifEmpty { cat.muscles.take(1) }
        val secondary = muscleList(a, "secondary_muscles").filter { it !in primary }
        val e = c.repo.saveCustomExercise(
            Exercise(
                id = "", name = name, category = cat.id, equipment = a.s("equipment").orEmpty(), level = a.s("level").orEmpty(),
                primary = primary, secondary = secondary, description = a.s("description").orEmpty().take(300), custom = true,
            ),
        )
        if (a.b("add_to_my_set") == true) c.repo.addToSet(e.id)
        ok("Creó el ejercicio «${e.name}»") { put("exercise", exerciseRow(e, c)) }
    },

    tool(
        "edit_custom_exercise",
        "Edita un ejercicio propio (los del catálogo no se pueden editar).",
        S.obj(
            listOf(
                "exercise" to S.str("Id o nombre del ejercicio propio."),
                "name" to S.str("Nuevo nombre."),
                "category" to S.str("Categoría.", Categories.all.map { it.id }),
                "primary_muscles" to S.strs("Músculos principales."),
                "secondary_muscles" to S.strs("Músculos secundarios."),
                "equipment" to S.str("Equipo."),
                "description" to S.str("Descripción."),
            ),
            listOf("exercise"),
        ),
        write = true,
    ) { a, c ->
        val ex = c.findExercise(a.s("exercise").orEmpty()) ?: return@tool noExercise(c, a.s("exercise"))
        if (!ex.custom) return@tool fail("«${ex.name}» es del catálogo y no se puede editar. Puedes crear uno propio con create_exercise.")
        val cat = a.s("category")?.let { parseCategory(it) ?: return@tool fail("Categoría no válida: ${Categories.all.joinToString { k -> k.id }}.") }
        val primary = if (a.has("primary_muscles")) muscleList(a, "primary_muscles") else null
        val secondary = if (a.has("secondary_muscles")) muscleList(a, "secondary_muscles") else null
        val e = c.repo.saveCustomExercise(
            ex.copy(
                name = a.s("name") ?: ex.name, category = cat ?: ex.category, equipment = a.s("equipment") ?: ex.equipment,
                description = a.s("description")?.take(300) ?: ex.description,
                primary = primary?.ifEmpty { null } ?: ex.primary, secondary = secondary ?: ex.secondary,
            ),
        )
        ok("Actualizó «${e.name}»") { put("exercise", exerciseRow(e, c)) }
    },

    tool(
        "delete_custom_exercise",
        "Elimina un ejercicio propio, lo quita de rutinas y de mi set (su historial de series se conserva).",
        S.obj(listOf("exercise" to S.str("Id o nombre del ejercicio propio.")), listOf("exercise")),
        write = true,
        destructive = { true },
        plan = { a, c -> "Eliminar el ejercicio «${c.findExercise(a.s("exercise").orEmpty())?.name ?: a.s("exercise").orEmpty()}»" },
    ) { a, c ->
        val ex = c.findExercise(a.s("exercise").orEmpty()) ?: return@tool noExercise(c, a.s("exercise"))
        if (!ex.custom) return@tool fail("«${ex.name}» es del catálogo y no se puede eliminar. Usa remove_from_my_set si ya no lo haces.")
        c.repo.deleteCustomExercise(ex.id)
        ok("Eliminó el ejercicio «${ex.name}»")
    },

    // ───────── Registro de series
    tool(
        "log_sets",
        "Registra series hechas. Para series iguales usa weight, reps y count; para series distintas usa 'sets'. El peso va en la unidad del usuario.",
        S.obj(
            listOf(
                "exercise" to S.str("Id o nombre del ejercicio."),
                "weight" to S.num("Peso por serie (0 si es peso corporal)."),
                "reps" to S.int("Repeticiones por serie (minutos en cardio)."),
                "count" to S.int("Cantidad de series iguales (por defecto 1)."),
                "sets" to S.objs("Series distintas.", S.obj(listOf("weight" to S.num("Peso."), "reps" to S.int("Repeticiones.")), listOf("reps"))),
                "date" to S.str("Fecha AAAA-MM-DD, 'hoy' o 'ayer' (por defecto hoy)."),
                "routine" to S.str("Rutina a la que pertenecen (opcional)."),
                "note" to S.str("Nota (opcional)."),
            ),
            listOf("exercise"),
        ),
        write = true,
        plan = { a, c -> "Registrar series de «${c.findExercise(a.s("exercise").orEmpty())?.name ?: a.s("exercise").orEmpty()}»" },
    ) { a, c ->
        val ex = c.findExercise(a.s("exercise").orEmpty()) ?: return@tool noExercise(c, a.s("exercise"))
        val today = c.repo.today()
        val date = a.s("date")?.let { parseDate(it, today) ?: return@tool fail("Fecha no válida: usa AAAA-MM-DD, 'hoy' o 'ayer'.") } ?: today
        if (date.isAfter(today)) return@tool fail("No puedo registrar series en el futuro.")
        val routine = a.s("routine")?.let { c.findRoutine(it) }
        val list = ArrayList<Pair<Double, Int>>()
        for (o in a.objs("sets")) {
            val w = o.d("weight") ?: o.d("peso") ?: 0.0
            val r = o.i("reps") ?: o.i("repeticiones") ?: continue
            repeat((o.i("count") ?: 1).coerceIn(1, 20)) { list += c.toKg(w.coerceAtLeast(0.0)) to r }
        }
        if (list.isEmpty()) {
            val r = a.i("reps") ?: return@tool fail("Indica las repeticiones (reps) o una lista 'sets'.")
            val w = a.d("weight") ?: 0.0
            repeat((a.i("count") ?: 1).coerceIn(1, 20)) { list += c.toKg(w.coerceAtLeast(0.0)) to r }
        }
        if (list.size > 30) return@tool fail("Máximo 30 series por llamada.")
        if (list.any { it.second !in 1..1000 }) return@tool fail("Las repeticiones deben estar entre 1 y 1000.")
        val baseTs = if (date == today) c.repo.now() else date.atTime(12, 0).atZone(c.repo.zone).toInstant().toEpochMilli()
        val note = a.s("note").orEmpty()
        val results = list.mapIndexed { i, (kg, reps) -> c.repo.logSet(ex.id, kg, reps, routine?.id, note, baseTs + i) }
        val records = results.mapNotNull { it.record }
        val milestones = results.flatMap { it.milestones }.distinctBy { it.id }
        val first = list.first()
        val what = if (list.all { it == first }) "${list.size} × ${Fmt.weight(first.first, c.units)} × ${first.second}" else "${list.size} series"
        ok("Registró $what en «${ex.name}»") {
            put("exercise", ex.name)
            put("date", date.toString())
            put("sets_logged", list.size)
            put("weight_unit", c.units)
            if (records.isNotEmpty()) strs("new_records", records.map { "${c.disp(it.weightKg)} ${c.units} × ${it.reps}" })
            if (milestones.isNotEmpty()) strs("milestones_unlocked", milestones.map { it.title })
        }
    },

    tool(
        "delete_logs",
        "Borra series registradas: por ids (de list_logs) o todas las de un ejercicio en una fecha.",
        S.obj(listOf("ids" to S.strs("Ids de series."), "exercise" to S.str("Ejercicio (junto con date)."), "date" to S.str("Fecha AAAA-MM-DD, 'hoy' o 'ayer'."))),
        write = true,
        destructive = { true },
        plan = { a, _ -> if (a.list("ids").isNotEmpty()) "Borrar ${a.list("ids").size} serie(s)" else "Borrar las series de «${a.s("exercise").orEmpty()}» del ${a.s("date").orEmpty()}" },
    ) { a, c ->
        val ids = a.list("ids")
        if (ids.isNotEmpty()) {
            val have = c.repo.logs.map { it.id }.toSet()
            val del = ids.filter { it in have }.toSet()
            if (del.isEmpty()) return@tool fail("Esos ids no existen. Usa list_logs.")
            c.repo.update { d -> d.copy(logs = d.logs.filterNot { it.id in del }) }
            ok("Borró ${del.size} serie${if (del.size == 1) "" else "s"}")
        } else {
            val ex = c.findExercise(a.s("exercise").orEmpty()) ?: return@tool fail("Indica ids o exercise + date.")
            val date = parseDate(a.s("date") ?: return@tool fail("Falta la fecha."), c.repo.today()) ?: return@tool fail("Fecha no válida.")
            val n = c.repo.logs.count { it.exerciseId == ex.id && Fmt.date(it.ts, c.repo.zone) == date }
            if (n == 0) return@tool fail("No hay series de «${ex.name}» el $date.")
            c.repo.deleteLogsOfDay(ex.id, date)
            ok("Borró $n serie${if (n == 1) "" else "s"} de «${ex.name}»")
        }
    },

    tool(
        "update_log",
        "Corrige el peso y/o las repeticiones de una serie ya registrada.",
        S.obj(listOf("id" to S.str("Id de la serie (de list_logs)."), "weight" to S.num("Nuevo peso en la unidad del usuario."), "reps" to S.int("Nuevas repeticiones.")), listOf("id")),
        write = true,
    ) { a, c ->
        val l = c.repo.logs.firstOrNull { it.id == a.s("id") } ?: return@tool fail("No existe esa serie. Usa list_logs.")
        val w = a.d("weight")?.let { c.toKg(it.coerceAtLeast(0.0)) } ?: l.weightKg
        val r = a.i("reps") ?: l.reps
        if (r !in 1..1000) return@tool fail("Las repeticiones deben estar entre 1 y 1000.")
        c.repo.updateLog(l.id, w, r)
        ok("Corrigió una serie de «${c.repo.exercise(l.exerciseId)?.name ?: "ejercicio"}»") {
            put("weight", c.disp(w))
            put("reps", r)
        }
    },

    // ───────── Anotaciones
    tool(
        "create_note",
        "Crea una anotación del gym, opcionalmente ligada a un ejercicio y/o fijada.",
        S.obj(
            listOf("title" to S.str("Título."), "body" to S.str("Texto."), "pinned" to S.bool("Fijarla."), "exercise" to S.str("Ejercicio al que pertenece (opcional).")),
        ),
        write = true,
    ) { a, c ->
        val title = a.s("title").orEmpty()
        val body = a.s("body").orEmpty()
        if (title.isEmpty() && body.isEmpty()) return@tool fail("Indica un título o un texto.")
        val ex = a.s("exercise")?.let { c.findExercise(it) ?: return@tool noExercise(c, it) }
        val n = c.repo.saveNote(Note(id = "", title = title, body = body, pinned = a.b("pinned") == true, exerciseId = ex?.id))
        ok("Creó la anotación «${n.title.ifEmpty { n.body.take(24) }}»") { put("note", noteJson(n, c)) }
    },

    tool(
        "update_note",
        "Edita una anotación: título, texto (reemplaza o agrega al final), fijada o ejercicio.",
        S.obj(
            listOf(
                "note" to S.str("Id o título de la anotación."),
                "title" to S.str("Nuevo título."),
                "body" to S.str("Nuevo texto (reemplaza)."),
                "append" to S.str("Texto a agregar al final."),
                "pinned" to S.bool("Fijar o desfijar."),
                "exercise" to S.str("Ejercicio al que pertenece (vacío para desligarla)."),
            ),
            listOf("note"),
        ),
        write = true,
    ) { a, c ->
        val n = findNote(c, a.s("note").orEmpty()) ?: return@tool fail("No encontré esa anotación. Usa list_notes.")
        val ex = if (a.has("exercise")) a.s("exercise")?.let { c.findExercise(it) ?: return@tool noExercise(c, it) } else null
        val updated = n.copy(
            title = a.s("title") ?: n.title,
            body = a.s("body") ?: a.s("append")?.let { if (n.body.isBlank()) it else n.body.trimEnd() + "\n" + it } ?: n.body,
            pinned = a.b("pinned") ?: n.pinned,
            exerciseId = if (a.has("exercise")) ex?.id else n.exerciseId,
        )
        val saved = c.repo.saveNote(updated)
        ok("Actualizó la anotación «${saved.title.ifEmpty { saved.body.take(24) }}»") { put("note", noteJson(saved, c)) }
    },

    tool(
        "delete_note",
        "Elimina una anotación.",
        S.obj(listOf("note" to S.str("Id o título de la anotación.")), listOf("note")),
        write = true,
        destructive = { true },
        plan = { a, c -> "Eliminar la anotación «${findNote(c, a.s("note").orEmpty())?.title ?: a.s("note").orEmpty()}»" },
    ) { a, c ->
        val n = findNote(c, a.s("note").orEmpty()) ?: return@tool fail("No encontré esa anotación. Usa list_notes.")
        c.repo.deleteNote(n.id)
        ok("Eliminó la anotación «${n.title.ifEmpty { n.body.take(24) }}»")
    },

    // ───────── Recordatorios
    tool(
        "create_reminder",
        "Crea un recordatorio de entrenamiento a cierta hora y días.",
        S.obj(
            listOf(
                "title" to S.str("Título del aviso."),
                "time" to S.str("Hora 'HH:MM' en formato 24 h (por defecto la hora predeterminada de ajustes)."),
                "days" to dayParam,
                "body" to S.str("Texto del aviso (opcional)."),
                "routine" to S.str("Rutina asociada (opcional)."),
            ),
        ),
        write = true,
        plan = { a, _ -> "Crear el recordatorio «${a.s("title") ?: "Hora de entrenar"}»" },
    ) { a, c ->
        val t = a.s("time")?.let { parseTime(it) ?: return@tool fail("Hora no válida: usa HH:MM, por ejemplo 18:30.") }
            ?: parseTime(c.repo.prefs.str("reminders.time")) ?: (18 to 0)
        val days = if (a.has("days")) parseDays(a.list("days")) else (1..7).toSet()
        if (days.isEmpty()) return@tool fail("No entendí los días. Usa lunes…domingo, 1-7, 'entre semana' o 'todos'.")
        val routine = a.s("routine")?.let { c.findRoutine(it) ?: return@tool noRoutine(c, it) }
        val m = c.repo.saveReminder(
            Reminder(id = "", title = a.s("title").orEmpty(), body = a.s("body").orEmpty(), hour = t.first, minute = t.second, days = days, enabled = true, routineId = routine?.id),
        )
        c.host.ensureNotifications()
        ok("Creó el recordatorio «${m.title}» a las ${Fmt.time(m.hour, m.minute)}") {
            put("reminder", reminderJson(m, c))
            if (!c.repo.prefs.bool("reminders.enabled")) put("warning", "Los recordatorios están apagados en Ajustes > Recordatorios; no sonarán hasta activarlos.")
        }
    },

    tool(
        "update_reminder",
        "Edita un recordatorio: título, hora, días, activarlo/desactivarlo o su rutina.",
        S.obj(
            listOf(
                "reminder" to S.str("Id o título del recordatorio."),
                "title" to S.str("Nuevo título."),
                "time" to S.str("Nueva hora HH:MM (24 h)."),
                "days" to dayParam,
                "enabled" to S.bool("Activar o desactivar."),
                "body" to S.str("Nuevo texto."),
                "routine" to S.str("Rutina asociada (vacío para quitarla)."),
            ),
            listOf("reminder"),
        ),
        write = true,
    ) { a, c ->
        val m = findReminder(c, a.s("reminder").orEmpty()) ?: return@tool fail("No encontré ese recordatorio. Usa list_reminders.")
        val t = a.s("time")?.let { parseTime(it) ?: return@tool fail("Hora no válida: usa HH:MM.") }
        val days = if (a.has("days")) parseDays(a.list("days")).ifEmpty { return@tool fail("No entendí los días.") } else null
        val routine = if (a.has("routine")) a.s("routine")?.let { c.findRoutine(it) ?: return@tool noRoutine(c, it) } else null
        val saved = c.repo.saveReminder(
            m.copy(
                title = a.s("title") ?: m.title, body = a.s("body") ?: m.body, hour = t?.first ?: m.hour, minute = t?.second ?: m.minute,
                days = days ?: m.days, enabled = a.b("enabled") ?: m.enabled, routineId = if (a.has("routine")) routine?.id else m.routineId,
            ),
        )
        if (saved.enabled) c.host.ensureNotifications()
        ok("Actualizó el recordatorio «${saved.title}»") { put("reminder", reminderJson(saved, c)) }
    },

    tool(
        "delete_reminder",
        "Elimina un recordatorio.",
        S.obj(listOf("reminder" to S.str("Id o título del recordatorio.")), listOf("reminder")),
        write = true,
        destructive = { true },
        plan = { a, c -> "Eliminar el recordatorio «${findReminder(c, a.s("reminder").orEmpty())?.title ?: a.s("reminder").orEmpty()}»" },
    ) { a, c ->
        val m = findReminder(c, a.s("reminder").orEmpty()) ?: return@tool fail("No encontré ese recordatorio. Usa list_reminders.")
        c.repo.deleteReminder(m.id)
        ok("Eliminó el recordatorio «${m.title}»")
    },

    // ───────── Pantallas y entrenamiento
    tool(
        "navigate",
        "Abre una pantalla de la app para el usuario. Pantallas: inicio, ejercicios, rutinas, fitbot, ajustes, progreso, hitos, notas, mi_set, buscar, registro_de_cambios, datos, acerca_de; con id: ejercicio, categoria, musculos, rutina; ajustes con página (p. ej. 'tema').",
        S.obj(
            listOf(
                "screen" to S.str("Nombre de la pantalla."),
                "id" to S.str("Ejercicio, categoría, rutina, página de ajustes o texto de búsqueda, según la pantalla."),
            ),
            listOf("screen"),
        ),
    ) { a, c ->
        val raw = a.s("screen").orEmpty()
        val name = raw.substringBefore(':').trim()
        val arg = a.s("id") ?: raw.substringAfter(':', "").trim().ifEmpty { null }
        val first = Route.fromName(name, arg)
            ?: return@tool fail("Pantalla no válida. Opciones: ${Route.names.joinToString()}.")
        val route: Route = when (first) {
            is Route.Exercise -> c.findExercise(first.id)?.let { Route.Exercise(it.id) } ?: return@tool noExercise(c, first.id)
            is Route.Muscles -> c.findExercise(first.id)?.let { Route.Muscles(it.id) } ?: return@tool noExercise(c, first.id)
            is Route.Category -> parseCategory(first.id)?.let { Route.Category(it) } ?: return@tool fail("Categoría no válida: ${Categories.all.joinToString { it.id }}.")
            is Route.RoutineDetail -> c.findRoutine(first.id)?.let { Route.RoutineDetail(it.id) } ?: return@tool noRoutine(c, first.id)
            is Route.SettingsPage -> resolvePage(first.page)?.let { Route.SettingsPage(it) }
                ?: return@tool fail("Página de ajustes no válida: ${Settings.pages.joinToString { it.id }}.")
            is Route.Workout -> return@tool fail("Para entrenar usa start_workout.")
            else -> first
        }
        c.host.navigate(route)
        val label = when (route) {
            is Route.Exercise -> "el ejercicio «${c.repo.exercise(route.id)?.name}»"
            is Route.Muscles -> "los músculos de «${c.repo.exercise(route.id)?.name}»"
            is Route.Category -> "la categoría «${Categories.byId(route.id)?.name}»"
            is Route.RoutineDetail -> "la rutina «${c.repo.routine(route.id)?.name}»"
            is Route.SettingsPage -> "los ajustes de «${Settings.pages.firstOrNull { it.id == route.page }?.title}»"
            else -> "«${raw.ifEmpty { name }}»"
        }
        ok("Abrió $label") { put("screen", route.key) }
    },

    tool(
        "start_workout",
        "Inicia un entrenamiento guiado de una rutina, o libre con ejercicios sueltos.",
        S.obj(listOf("routine" to S.str("Id o nombre de la rutina (opcional)."), "exercises" to exercisesParam)),
    ) { a, c ->
        val rt = a.s("routine")?.let { c.findRoutine(it) ?: return@tool noRoutine(c, it) }
        val (found, missing) = c.resolveAll(refs(a, "exercises", "exercise"))
        c.host.startWorkout(rt?.id, found.map { it.id })
        ok("Inició " + (rt?.let { "la rutina «${it.name}»" } ?: "un entrenamiento libre")) {
            if (missing.isNotEmpty()) put("not_found", c.missingJson(missing))
        }
    },

    tool(
        "finish_workout",
        "Finaliza el entrenamiento guiado en curso y CONSERVA todas las series registradas (muestra el resumen). Úsala cuando el usuario diga que terminó o pida finalizar la rutina.",
    ) { _, c ->
        val msg = c.host.finishWorkout() ?: return@tool fail("No hay ningún entrenamiento en curso.")
        ok(msg)
    },

    tool(
        "cancel_workout",
        "Cancela el entrenamiento guiado en curso (por ejemplo si se inició por error). Por defecto BORRA las series que se registraron en esa sesión; con keep_sets=true las conserva. Se puede deshacer.",
        S.obj(listOf("keep_sets" to S.bool("true = cancelar pero conservar las series ya registradas (por defecto false)."))),
        write = true,
        destructive = { it.b("keep_sets") != true },
        plan = { a, c ->
            val n = c.host.workoutStatus()?.sets ?: 0
            if (a.b("keep_sets") == true) "Cancelar el entrenamiento en curso (conservando las series registradas)"
            else if (n > 0) "Cancelar el entrenamiento en curso y borrar las $n series que registraste en esta sesión"
            else "Cancelar el entrenamiento en curso"
        },
    ) { a, c ->
        val msg = c.host.cancelWorkout(a.b("keep_sets") != true) ?: return@tool fail("No hay ningún entrenamiento en curso.")
        ok(msg)
    },

    // ───────── Deshacer y borrar todo
    tool(
        "undo_last_change",
        "Deshace los últimos cambios que hizo FITBOT en los datos de la app.",
    ) { _, c ->
        val what = c.host.undoLastBotChange() ?: return@tool fail("No hay cambios míos que deshacer.")
        ok("Deshizo: $what")
    },

    tool(
        "erase_data",
        "Borra los datos del usuario (series, rutinas, notas, recordatorios, ejercicios propios y mi set). Pide siempre confirmación. Se puede deshacer.",
        S.obj(listOf("keep_settings" to S.bool("Conservar la personalización (por defecto true)."))),
        write = true,
        destructive = { true },
        alwaysConfirm = true,
        plan = { _, _ -> "Borrar TODOS tus datos (series, rutinas, notas, recordatorios y mi set)" },
    ) { a, c ->
        c.repo.eraseAll(a.b("keep_settings") != false)
        ok("Borró todos los datos")
    },
)

/** Todas las herramientas que FITBOT puede usar. */
internal val allTools: List<BotTool> = readTools + writeTools + connectorTools + namstatTools
