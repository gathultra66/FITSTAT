package app.fitstat.bot

import app.fitstat.data.Categories
import app.fitstat.data.Exercise
import app.fitstat.data.Fmt
import app.fitstat.data.Muscle
import app.fitstat.data.Repo
import app.fitstat.data.Routine
import app.fitstat.data.Search
import app.fitstat.ui.app.Route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Foto del entrenamiento guiado en curso. */
class WorkoutStatus(
    val routine: String?,
    val minutes: Int,
    val sets: Int,
    val exercises: List<String>,
    val skipped: List<String>,
    val current: String?,
    val resting: Boolean,
    val finished: Boolean,
)

/** Lo que FITBOT necesita de la app para actuar: datos y control de pantallas. */
interface BotHost {
    val repo: Repo

    /** Clave de la pantalla visible ("home", "ex:press_de_banca", …). */
    fun currentScreen(): String
    fun navigate(route: Route)
    fun startWorkout(routineId: String?, exerciseIds: List<String>)
    fun workoutActive(): Boolean

    /** Estado del entrenamiento guiado en curso, o null si no hay. */
    fun workoutStatus(): WorkoutStatus? = null

    /** Finaliza el entrenamiento (conserva las series). Devuelve lo que pasó, o null si no había entrenamiento. */
    fun finishWorkout(): String? = null

    /** Cancela el entrenamiento; con [discardSets] borra las series de esa sesión. Devuelve lo que pasó, o null si no había. */
    fun cancelWorkout(discardSets: Boolean): String? = null
    fun ensureNotifications() {}

    /** Deshace lo último que FITBOT cambió. Devuelve un texto con lo que se deshizo, o null si no hay nada. */
    fun undoLastBotChange(): String? = null

    /** Si el usuario está editando el inicio a mano, guarda lo que lleva antes de que FITBOT cambie la disposición. */
    fun saveHomeEdit() {}

    /** Los conectores (Namida, Health Connect…); null si esta pantalla no los tiene. */
    fun connectors(): app.fitstat.conn.ConnectorHub? = null

    /** El ritmo de la música para las animaciones; null si esta pantalla no lo tiene. */
    fun rhythm(): app.fitstat.conn.RhythmEngine? = null
}

/** Resultado de una herramienta: lo que ve el modelo ([json]) y el texto corto que ve el usuario ([summary]). */
class ToolOutcome(val ok: Boolean, val json: JsonObject, val summary: String)

class BotTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    /** Cambia los datos del usuario (no cuenta abrir pantallas). */
    val write: Boolean = false,
    /** Decide, según los argumentos, si es una acción destructiva (borrar). */
    val destructive: (JsonObject) -> Boolean = { false },
    /** Pide permiso siempre, incluso con "Aplicar y avisarme". */
    val alwaysConfirm: Boolean = false,
    /** Frase corta de lo que va a hacer (para pedir confirmación). */
    val plan: (JsonObject, ToolCtx) -> String = { _, _ -> "" },
    val run: (JsonObject, ToolCtx) -> ToolOutcome,
) {
    fun spec(): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            put("parameters", parameters)
        }
    }
}

class ToolCtx(val host: BotHost, private val makeCheckpoint: () -> Long = { 0L }) {
    val repo: Repo get() = host.repo
    val units: String get() = repo.units

    /** Punto de restauración de este turno (0 = todavía no se cambió nada). */
    var checkpoint: Long = 0L
        private set

    /** Se llama justo antes de la primera herramienta que cambia datos. */
    fun beforeWrite() {
        if (checkpoint == 0L) checkpoint = makeCheckpoint()
    }

    /** Nombres que no se pudieron identificar, con ejercicios parecidos para que el modelo corrija. */
    fun missingJson(missing: List<String>): JsonArray = buildJsonArray {
        for (m in missing) add(
            buildJsonObject {
                put("query", m)
                arr("suggestions", suggestions(m).map { buildJsonObject { put("id", it.id); put("name", it.name) } })
            },
        )
    }

    /** kg guardados → unidad del usuario, redondeado. */
    fun disp(kg: Double): Double = Math.round(Fmt.toDisplay(kg, units) * 100.0) / 100.0
    fun toKg(v: Double): Double = Fmt.fromDisplay(v, units)

    /** Busca un ejercicio por id, por nombre exacto o por el mejor parecido claro. */
    fun findExercise(ref: String): Exercise? {
        val q = ref.trim()
        if (q.isEmpty()) return null
        repo.exercise(q)?.let { return it }
        val n = Search.norm(q)
        repo.exercises.firstOrNull { Search.norm(it.name) == n }?.let { return it }
        val hits = repo.search(q, 3)
        val top = hits.firstOrNull() ?: return null
        return if (hits.size == 1 || top.score >= hits[1].score * 1.25) top.ex else null
    }

    fun suggestions(ref: String): List<Exercise> = repo.search(ref, 4).map { it.ex }

    /** "Press de banca (id press_de_banca); …" para mensajes de error. */
    fun suggestionText(ref: String): String =
        suggestions(ref).joinToString("; ") { "${it.name} (id ${it.id})" }.ifEmpty { "ninguno parecido" }

    /** Resuelve varios ejercicios: los encontrados y los nombres que no se pudieron identificar. */
    fun resolveAll(refs: List<String>): Pair<List<Exercise>, List<String>> {
        val found = ArrayList<Exercise>()
        val missing = ArrayList<String>()
        for (r in refs) {
            val e = findExercise(r)
            if (e == null) missing += r else if (found.none { it.id == e.id }) found += e
        }
        return found to missing
    }

    fun findRoutine(ref: String): Routine? {
        val q = ref.trim()
        if (q.isEmpty()) return null
        repo.routine(q)?.let { return it }
        val n = Search.norm(q)
        repo.routines.firstOrNull { Search.norm(it.name) == n }?.let { return it }
        val partial = repo.routines.filter { Search.norm(it.name).contains(n) || n.contains(Search.norm(it.name)) }
        return partial.singleOrNull()
    }
}

// ───────────────────────── Lectura tolerante de argumentos

internal fun JsonObject.has(k: String): Boolean = containsKey(k) && this[k] !is JsonNull

internal fun JsonObject.s(k: String): String? =
    (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

internal fun JsonObject.i(k: String): Int? = (this[k] as? JsonPrimitive)?.let { p ->
    p.contentOrNull?.toIntOrNull() ?: p.doubleOrNull?.toInt() ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull()?.toInt()
}

internal fun JsonObject.d(k: String): Double? = (this[k] as? JsonPrimitive)?.let { p ->
    p.doubleOrNull ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
}

internal fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.let { p ->
    p.booleanOrNull ?: when (p.contentOrNull?.lowercase()) {
        "true", "si", "sí", "yes", "on" -> true
        "false", "no", "off" -> false
        else -> null
    }
}

/** Lista de textos: acepta un arreglo o un texto separado por comas. */
internal fun JsonObject.list(k: String): List<String> = when (val v = this[k]) {
    is JsonArray -> v.mapNotNull { e ->
        when (e) {
            is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            is JsonObject -> (e["id"] ?: e["name"] ?: e["exercise"])?.let { (it as? JsonPrimitive)?.contentOrNull }
            else -> null
        }
    }
    is JsonPrimitive -> v.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    else -> emptyList()
}

internal fun JsonObject.objs(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

private val dayPrefixes = listOf(
    listOf("lu", "mo"), listOf("ma", "tu"), listOf("mi", "we"), listOf("ju", "th"), listOf("vi", "fr"), listOf("sa"), listOf("do", "su"),
)

/** Días de la semana (1 = lunes … 7 = domingo) a partir de números o nombres. */
internal fun parseDays(vals: List<String>): Set<Int> {
    val out = sortedSetOf<Int>()
    for (v in vals) {
        val t = Search.norm(v)
        if (t.isEmpty()) continue
        val n = t.toIntOrNull()
        when {
            n != null -> if (n in 1..7) out += n
            t.contains("fin de semana") || t.contains("weekend") -> { out += 6; out += 7 }
            t.contains("entre semana") || t.contains("weekdays") || t.contains("laborales") -> out.addAll(1..5)
            t.contains("todos") || t.contains("diario") || t.contains("every") || t == "all" -> out.addAll(1..7)
            else -> {
                val idx = dayPrefixes.indexOfFirst { ps -> ps.any { t.startsWith(it) } }
                if (idx >= 0) out += idx + 1
            }
        }
    }
    return out
}

internal fun parseMuscle(v: String): Muscle? {
    val n = Search.norm(v)
    if (n.isEmpty()) return null
    Muscle.entries.firstOrNull { Search.norm(it.label) == n || it.name.equals(v.trim().replace(' ', '_'), true) }?.let { return it }
    return Muscle.entries.firstOrNull { Search.norm(it.label).startsWith(n.take(4)) && n.length >= 3 }
}

internal fun parseCategory(v: String): String? {
    val n = Search.norm(v)
    return Categories.all.firstOrNull { it.id == n || Search.norm(it.name) == n }?.id
        ?: Categories.all.firstOrNull { Search.norm(it.name).startsWith(n.take(4)) && n.length >= 3 }?.id
}

// ───────────────────────── Armado de respuestas

internal fun ok(summary: String, build: JsonObjectBuilder.() -> Unit = {}): ToolOutcome =
    ToolOutcome(true, buildJsonObject { put("ok", true); build() }, summary)

internal fun fail(msg: String): ToolOutcome =
    ToolOutcome(false, buildJsonObject { put("ok", false); put("error", msg) }, msg)

internal fun JsonObjectBuilder.arr(key: String, items: Iterable<JsonElement>) {
    put(key, buildJsonArray { items.forEach { add(it) } })
}

internal fun JsonObjectBuilder.strs(key: String, items: Iterable<String>) {
    putJsonArray(key) { items.forEach { add(it) } }
}

// ───────────────────────── Esquemas JSON para las herramientas

internal object S {
    fun str(desc: String, enum: List<String>? = null): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", desc)
        if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
    }

    fun int(desc: String): JsonObject = buildJsonObject { put("type", "integer"); put("description", desc) }
    fun num(desc: String): JsonObject = buildJsonObject { put("type", "number"); put("description", desc) }
    fun bool(desc: String): JsonObject = buildJsonObject { put("type", "boolean"); put("description", desc) }

    fun strs(desc: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", desc)
        putJsonObject("items") { put("type", "string") }
    }

    fun objs(desc: String, item: JsonObject): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", desc)
        put("items", item)
    }

    fun obj(props: List<Pair<String, JsonObject>> = emptyList(), required: List<String> = emptyList()): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
    }
}
