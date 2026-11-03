package app.fitstat.data

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

sealed interface AppEvent {
    data class Record(val exerciseName: String, val weightKg: Double, val reps: Int) : AppEvent
    data class Milestone(val def: MilestoneDef) : AppEvent
    data class Message(val text: String) : AppEvent
}

class LogResult(val log: SetLog, val record: Stats.RecordEvent?, val milestones: List<MilestoneDef>)

@Serializable
class Backup(val app: String = "FITSTAT", val version: Int = 1, val exportedAt: Long = 0, val data: AppData = AppData())

/**
 * Estado único de la app: todos los datos del usuario, con guardado automático en disco,
 * deshacer y avisos de eventos (récords, hitos). La interfaz lee de aquí y FITBOT escribe aquí.
 */
class Repo(
    private val dir: File,
    private val scope: CoroutineScope,
    val now: () -> Long = { System.currentTimeMillis() },
    private val zoneProvider: () -> java.time.ZoneId = { java.time.ZoneId.systemDefault() },
) {
    private val file = File(dir, "data.json")
    private val bak = File(dir, "data.json.bak")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    var data: AppData by mutableStateOf(AppData())
        private set

    private var settingsState: Map<String, String> by mutableStateOf(emptyMap())

    // Piezas del estado con comparación estructural: así las pantallas solo se redibujan si su parte cambió.
    val prefs: Prefs by derivedStateOf { Prefs(settingsState) }
    val logs: List<SetLog> by derivedStateOf(structuralEqualityPolicy()) { data.logs }
    val routines: List<Routine> by derivedStateOf(structuralEqualityPolicy()) { data.routines }
    val notes: List<Note> by derivedStateOf(structuralEqualityPolicy()) { data.notes }
    val reminders: List<Reminder> by derivedStateOf(structuralEqualityPolicy()) { data.reminders }
    val configs: Map<String, ExerciseConfig> by derivedStateOf(structuralEqualityPolicy()) { data.configs }
    val custom: List<Exercise> by derivedStateOf(structuralEqualityPolicy()) { data.custom }
    val profile: Profile by derivedStateOf(structuralEqualityPolicy()) { data.profile }
    val unlocked: Map<String, Long> by derivedStateOf(structuralEqualityPolicy()) { data.unlocked }
    val searchHistory: List<String> by derivedStateOf(structuralEqualityPolicy()) { data.searchHistory }
    private val homeLayout: List<HomeSlot>? by derivedStateOf(structuralEqualityPolicy()) { data.homeLayout }
    val sleep: List<SleepSession> by derivedStateOf(structuralEqualityPolicy()) { data.sleep }

    private val pageLayouts: Map<String, List<HomeSlot>> by derivedStateOf(structuralEqualityPolicy()) { data.pageLayouts }

    /** Widgets del inicio, en orden y con su tamaño (la disposición de siempre si nunca se personalizó). */
    val homeSlots: List<HomeSlot> get() = HomeLayout.effective(homeLayout, prefs.raw)

    /** Bloques de una pantalla editable (inicio, Ejercicios o Rutinas), en orden y con su tamaño. */
    fun pageSlots(page: String): List<HomeSlot> =
        if (page == LayoutPage.HOME) homeSlots
        else HomeLayout.effective(page, pageLayouts[page], prefs.raw, routines.map { it.id })

    /** La disposición de siempre de una pantalla editable. */
    fun defaultSlots(page: String): List<HomeSlot> =
        if (page == LayoutPage.HOME) HomeLayout.default() else HomeLayout.default(page, routines.map { it.id })

    /** Si el usuario ya personalizó esa pantalla. */
    fun pageCustomized(page: String): Boolean = if (page == LayoutPage.HOME) homeLayout != null else pageLayouts.containsKey(page)

    val exercises: List<Exercise> by derivedStateOf { Catalog.all + custom }
    val exById: Map<String, Exercise> by derivedStateOf { exercises.associateBy { it.id } }
    private val searchIndex: Search.Index by derivedStateOf { Search.index(exercises) }

    // Deshacer
    private class Snap(val label: String, val data: AppData, val serial: Long)
    private val undoStack = ArrayList<Snap>()
    private val quietUndo = java.util.concurrent.atomic.AtomicInteger(0)
    private var serialCounter = 0L
    var undoDepth: Int by mutableIntStateOf(0)
        private set

    val events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)

    /** Ganchos que conecta la aplicación (recordatorios, widgets, ícono). */
    var onRemindersChanged: (() -> Unit)? = null
    var onDataChanged: (() -> Unit)? = null
    var onSettingChanged: ((String, String) -> Unit)? = null

    private val saveSignal = Channel<Unit>(Channel.CONFLATED)
    private val writeLock = Any()

    init {
        load()
        scope.launch(Dispatchers.IO) {
            for (u in saveSignal) {
                delay(300)
                writeNow()
            }
        }
    }

    // ───────────────────────── Disco

    private fun parse(f: File): AppData? = try {
        if (!f.exists()) null else json.decodeFromString(AppData.serializer(), f.readText())
    } catch (_: Throwable) {
        null
    }

    private fun load() {
        var loaded = parse(file)
        if (loaded == null && file.exists()) {
            runCatching { file.copyTo(File(dir, "data.corrupt-${now()}.json"), overwrite = true) }
            loaded = parse(bak)
        }
        data = loaded ?: AppData(profile = Profile(createdAt = now()))
        settingsState = data.settings
    }

    fun writeNow() {
        synchronized(writeLock) {
            try {
                dir.mkdirs()
                val text = json.encodeToString(AppData.serializer(), data)
                val tmp = File(dir, "data.json.tmp")
                tmp.writeText(text)
                if (file.exists()) runCatching { file.copyTo(bak, overwrite = true) }
                try {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Throwable) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun flush() = writeNow()

    // ───────────────────────── Núcleo de cambios

    @Synchronized
    fun update(label: String? = null, f: (AppData) -> AppData) {
        val old = data
        val new = f(old)
        if (new === old) return
        if (label != null && quietUndo.get() == 0) pushUndo(label, old)
        data = new
        if (new.settings != old.settings) settingsState = new.settings
        saveSignal.trySend(Unit)
        onDataChanged?.invoke()
    }

    /** Ejecuta [f] sin crear un punto de deshacer por cada cambio (FITBOT guarda uno solo por turno). */
    fun <T> withoutUndo(f: () -> T): T {
        quietUndo.incrementAndGet()
        try {
            return f()
        } finally {
            quietUndo.decrementAndGet()
        }
    }

    private fun pushUndo(label: String, old: AppData) {
        undoStack.add(Snap(label, old, ++serialCounter))
        if (undoStack.size > 25) undoStack.removeAt(0)
        undoDepth = undoStack.size
    }

    val undoLabel: String? get() = undoStack.lastOrNull()?.label

    @Synchronized
    fun undo(): String? {
        val s = undoStack.removeLastOrNull() ?: return null
        undoDepth = undoStack.size
        swapIn(s.data)
        return s.label
    }

    /** Pone [newData] como estado actual y avisa a los ganchos (ajustes, recordatorios, widgets). */
    private fun swapIn(newData: AppData) {
        val old = data
        data = newData
        if (newData.settings != old.settings) {
            settingsState = newData.settings
            for (k in (newData.settings.keys + old.settings.keys)) {
                if (newData.settings[k] != old.settings[k]) onSettingChanged?.invoke(k, newData.settings[k] ?: Settings.default(k))
            }
        }
        saveSignal.trySend(Unit)
        onDataChanged?.invoke()
        if (newData.reminders != old.reminders) onRemindersChanged?.invoke()
    }

    /** Marca un punto de restauración (por ejemplo antes de que FITBOT haga cambios). Devuelve su número. */
    @Synchronized
    fun checkpoint(label: String): Long {
        pushUndo(label, data)
        return undoStack.last().serial
    }

    /** Vuelve al estado de un punto de restauración, deshaciendo también todo lo que vino después. */
    @Synchronized
    fun restoreTo(serial: Long): Boolean {
        val i = undoStack.indexOfFirst { it.serial == serial }
        if (i < 0) return false
        val s = undoStack[i]
        while (undoStack.size > i) undoStack.removeAt(undoStack.size - 1)
        undoDepth = undoStack.size
        swapIn(s.data)
        return true
    }

    /** True si el punto de restauración sigue disponible. */
    @Synchronized
    fun hasCheckpoint(serial: Long): Boolean = undoStack.any { it.serial == serial }

    /** Si no hubo ningún cambio desde el punto de restauración, lo descarta para no ensuciar el historial. */
    @Synchronized
    fun dropCheckpointIfUnchanged(serial: Long): Boolean {
        val i = undoStack.indexOfFirst { it.serial == serial }
        if (i < 0) return false
        if (i != undoStack.size - 1) return false
        val s = undoStack[i]
        if (s.data === data || s.data == data) {
            undoStack.removeAt(i)
            undoDepth = undoStack.size
            return true
        }
        return false
    }

    fun newId(prefix: String): String {
        val r = (1..4).map { "abcdefghijklmnopqrstuvwxyz0123456789"[(Math.random() * 36).toInt().coerceAtMost(35)] }.joinToString("")
        return prefix + java.lang.Long.toString(now(), 36) + r
    }

    val zone: java.time.ZoneId get() = zoneProvider()
    fun today(): LocalDate = Fmt.date(now(), zone)

    // ───────────────────────── Sueño

    /** Meta de sueño en horas. */
    val sleepGoal: Double get() = prefs.float("sleep.goal").toDouble()

    /** Las noches de sueño (la más reciente primero). */
    fun sleepNights(): List<SleepNight> = Sleep.nights(sleep, zone)

    /** Registra una noche a mano. Falla si las horas no tienen sentido. */
    fun addSleep(start: Long, end: Long, note: String = "", label: String? = "Sueño registrado"): Result<SleepSession> {
        val s = SleepSession(newId("sl"), start, end, SleepSrc.MANUAL, note = note.trim().take(200))
        if (end <= start) return Result.failure(IllegalArgumentException("La hora de despertar tiene que ser después de la de dormir."))
        if (!Sleep.valid(s)) return Result.failure(IllegalArgumentException("Una noche dura entre ${Sleep.fmt(Sleep.MIN_MS)} y ${Sleep.fmt(Sleep.MAX_MS)}."))
        if (end > now() + 3600_000L) return Result.failure(IllegalArgumentException("No se puede registrar sueño en el futuro."))
        update(label) { it.copy(sleep = it.sleep + s) }
        return Result.success(s)
    }

    fun deleteSleep(id: String, label: String? = "Sueño eliminado"): Boolean {
        if (sleep.none { it.id == id }) return false
        update(label) { d -> d.copy(sleep = d.sleep.filterNot { it.id == id }) }
        return true
    }

    /**
     * Guarda las noches que trajo una fuente automática (Health Connect o el teléfono). Si ya había una con el mismo id,
     * se actualiza. No deja punto de «Deshacer». Devuelve cuántas son nuevas o cambiaron.
     */
    fun mergeSleep(found: List<SleepSession>): Int {
        val ok = found.filter { Sleep.valid(it) }
        if (ok.isEmpty()) return 0
        var changed = 0
        update(null) { d ->
            val byId = d.sleep.associateBy { it.id }.toMutableMap()
            for (s in ok) {
                if (byId[s.id] != s) {
                    byId[s.id] = s
                    changed++
                }
            }
            if (changed == 0) d else d.copy(sleep = byId.values.sortedBy { it.start })
        }
        return changed
    }

    // ───────────────────────── Ajustes

    /** Cambia un ajuste validándolo. Devuelve el valor guardado o un mensaje de error. */
    fun setSetting(key: String, raw: String): Result<String> {
        val spec = Settings.byKey[key] ?: return Result.failure(IllegalArgumentException("No existe el ajuste '$key'."))
        val v = Settings.normalize(spec, raw)
            ?: return Result.failure(IllegalArgumentException("Valor no válido para '$key'. Opciones: ${Settings.optionsText(spec)}."))
        update {
            val m = it.settings.toMutableMap()
            if (v == spec.default) m.remove(key) else m[key] = v
            if (m == it.settings) it else it.copy(settings = m)
        }
        onSettingChanged?.invoke(key, v)
        return Result.success(v)
    }

    fun resetSetting(key: String) {
        val spec = Settings.byKey[key] ?: return
        update { if (it.settings.containsKey(key)) it.copy(settings = it.settings - key) else it }
        onSettingChanged?.invoke(key, spec.default)
    }

    fun resetPage(page: String) {
        val keys = Settings.specsOf(page).map { it.key }
        update("Ajustes restablecidos") { d -> d.copy(settings = d.settings.filterKeys { it !in keys }) }
        keys.forEach { onSettingChanged?.invoke(it, Settings.default(it)) }
    }

    fun resetAllSettings() {
        val keys = data.settings.keys.toList()
        update("Ajustes restablecidos") { it.copy(settings = emptyMap()) }
        keys.forEach { onSettingChanged?.invoke(it, Settings.default(it)) }
    }

    /** Guarda la disposición del inicio (null = volver a la de siempre sin personalizar). */
    fun setHomeLayout(slots: List<HomeSlot>?, label: String? = "Inicio personalizado") {
        update(label) { d -> d.copy(homeLayout = slots?.let { HomeLayout.sanitize(it) }) }
    }

    /** Guarda la disposición de una pantalla editable (null = volver a la de siempre sin personalizar). */
    fun setPageLayout(page: String, slots: List<HomeSlot>?, label: String? = "${LayoutPage.title(page)} personalizado") {
        if (page == LayoutPage.HOME) return setHomeLayout(slots, label)
        update(label) { d ->
            d.copy(pageLayouts = if (slots == null) d.pageLayouts - page else d.pageLayouts + (page to HomeLayout.sanitize(slots, page)))
        }
    }

    fun setProfileName(name: String) = update { it.copy(profile = it.profile.copy(name = name.trim().take(40))) }

    fun finishOnboarding() = update { it.copy(profile = it.profile.copy(onboarded = true)) }

    val weeklyGoal: Int get() = prefs.int("train.goal")
    val mondayFirst: Boolean get() = prefs.str("home.week_start") == "mon"
    val units: String get() = prefs.str("stats.units")
    val formula: String get() = prefs.str("stats.formula")

    // ───────────────────────── Ejercicios

    fun exercise(id: String): Exercise? = exById[id]

    fun saveCustomExercise(ex: Exercise): Exercise {
        val e = ex.copy(custom = true, id = if (ex.id.isBlank()) newId("u_") else ex.id, name = ex.name.trim().take(60))
        update { d ->
            val list = if (d.custom.any { it.id == e.id }) d.custom.map { if (it.id == e.id) e else it } else d.custom + e
            d.copy(custom = list)
        }
        return e
    }

    fun deleteCustomExercise(id: String) {
        update("Ejercicio eliminado") { d ->
            d.copy(
                custom = d.custom.filterNot { it.id == id },
                configs = d.configs - id,
                routines = d.routines.map { r -> r.copy(items = r.items.filterNot { it.exerciseId == id }) },
            )
        }
    }

    fun defaultConfig(id: String) = ExerciseConfig(
        exerciseId = id, inSet = false,
        sets = prefs.int("train.sets"), repsMin = prefs.int("train.reps"), restSec = prefs.int("train.rest"),
    )

    fun config(id: String): ExerciseConfig = configs[id] ?: defaultConfig(id)
    fun inSet(id: String): Boolean = configs[id]?.inSet == true

    fun updateConfig(id: String, label: String? = null, f: (ExerciseConfig) -> ExerciseConfig) {
        update(label) { d ->
            val cur = d.configs[id] ?: defaultConfig(id)
            val n = f(cur)
            if (n == cur && d.configs.containsKey(id)) d else d.copy(configs = d.configs + (id to n))
        }
    }

    fun addToSet(id: String) {
        updateConfig(id, "Agregado a tu set") { it.copy(inSet = true) }
        checkMilestones()
    }

    fun removeFromSet(id: String) = updateConfig(id, "Quitado de tu set") { it.copy(inSet = false) }

    val setIds: List<String> get() = configs.values.filter { it.inSet }.map { it.exerciseId }

    // ───────────────────────── Rutinas

    fun routine(id: String): Routine? = routines.firstOrNull { it.id == id }

    fun createRoutine(
        name: String,
        icon: String = "Dumbbell",
        colorIdx: Int = (routines.size) % RoutineColors,
        exerciseIds: List<String> = emptyList(),
        days: Set<Int> = emptySet(),
        note: String = "",
    ): Routine {
        val r = Routine(
            id = newId("r_"), name = name.trim().ifEmpty { "Rutina ${routines.size + 1}" }.take(40), icon = icon, colorIdx = colorIdx,
            items = exerciseIds.distinct().map { RoutineItem(it) }, days = days, note = note, createdAt = now(),
        )
        update { it.copy(routines = it.routines + r) }
        checkMilestones()
        return r
    }

    fun updateRoutine(id: String, label: String? = null, f: (Routine) -> Routine) {
        update(label) { d -> d.copy(routines = d.routines.map { if (it.id == id) f(it) else it }) }
    }

    fun deleteRoutine(id: String) {
        update("Rutina eliminada") { d ->
            d.copy(
                routines = d.routines.filterNot { it.id == id },
                reminders = d.reminders.map { if (it.routineId == id) it.copy(routineId = null) else it },
            )
        }
        onRemindersChanged?.invoke()
    }

    fun duplicateRoutine(id: String): Routine? {
        val r = routine(id) ?: return null
        val c = r.copy(id = newId("r_"), name = (r.name + " (copia)").take(40), createdAt = now(), reminderId = null)
        update { it.copy(routines = it.routines + c) }
        return c
    }

    fun addToRoutine(routineId: String, exerciseIds: List<String>) {
        updateRoutine(routineId) { r ->
            val have = r.items.map { it.exerciseId }.toSet()
            r.copy(items = r.items + exerciseIds.filter { it !in have }.distinct().map { RoutineItem(it) })
        }
    }

    fun removeFromRoutine(routineId: String, exerciseId: String) =
        updateRoutine(routineId, "Ejercicio quitado de la rutina") { r -> r.copy(items = r.items.filterNot { it.exerciseId == exerciseId }) }

    /** Series que se harán de un ejercicio dentro de una rutina (usa las del ejercicio si la rutina no define las suyas). */
    fun itemSets(item: RoutineItem): Int = if (item.sets > 0) item.sets else config(item.exerciseId).sets

    /** Repeticiones objetivo (mínimo, máximo) de un ejercicio dentro de una rutina. Máximo 0 = fijas. */
    fun itemReps(item: RoutineItem): Pair<Int, Int> {
        val cfg = config(item.exerciseId)
        val lo = if (item.repsMin > 0) item.repsMin else cfg.repsMin
        val hi = if (item.repsMin > 0) item.repsMax else cfg.repsMax
        return lo to (if (hi > lo) hi else 0)
    }

    /** Repeticiones objetivo como texto ("10" o "8-12"). */
    fun itemRepsLabel(item: RoutineItem): String {
        val (lo, hi) = itemReps(item)
        return if (hi > lo) "$lo-$hi" else "$lo"
    }

    /** Duración aproximada de una rutina en minutos (≈45 s por serie más el descanso). */
    fun routineMinutes(r: Routine): Int {
        var sec = 0
        for (it in r.items) sec += itemSets(it) * (45 + config(it.exerciseId).restSec)
        return Math.round(sec / 60.0).toInt().coerceAtLeast(if (r.items.isEmpty()) 0 else 1)
    }

    fun moveInRoutine(routineId: String, from: Int, to: Int) {
        updateRoutine(routineId) { r ->
            if (from !in r.items.indices || to !in r.items.indices || from == to) r
            else {
                val l = r.items.toMutableList()
                l.add(to, l.removeAt(from))
                r.copy(items = l)
            }
        }
    }

    // ───────────────────────── Registro de series

    fun logsOf(exerciseId: String): List<SetLog> = logs.filter { it.exerciseId == exerciseId }

    fun lastSet(exerciseId: String): SetLog? = logs.filter { it.exerciseId == exerciseId }.maxByOrNull { it.ts }

    fun logSet(exerciseId: String, weightKg: Double, reps: Int, routineId: String? = null, note: String = "", ts: Long = now()): LogResult {
        val prevBest = logs.asSequence().filter { it.exerciseId == exerciseId }.maxOfOrNull { Stats.score(it.weightKg, it.reps, formula) } ?: 0.0
        val s = SetLog(newId("l_"), exerciseId, ts, weightKg.coerceIn(0.0, 2000.0), reps.coerceIn(1, 1000), routineId, note.take(120))
        update("Serie registrada") { it.copy(logs = it.logs + s) }
        val e = Stats.score(s.weightKg, s.reps, formula)
        val rec = if (prevBest > 0.0 && e > prevBest + 0.0001) Stats.RecordEvent(s.ts, exerciseId, s.weightKg, s.reps, e) else null
        if (rec != null) events.tryEmit(AppEvent.Record(exercise(exerciseId)?.name ?: "Ejercicio", s.weightKg, s.reps))
        val ms = checkMilestones()
        return LogResult(s, rec, ms)
    }

    fun deleteLog(id: String) = update("Serie eliminada") { d -> d.copy(logs = d.logs.filterNot { it.id == id }) }

    fun updateLog(id: String, weightKg: Double, reps: Int) = update("Serie corregida") { d ->
        d.copy(logs = d.logs.map { if (it.id == id) it.copy(weightKg = weightKg.coerceIn(0.0, 2000.0), reps = reps.coerceIn(1, 1000)) else it })
    }

    fun deleteLogsOfDay(exerciseId: String, date: LocalDate) = update("Sesión eliminada") { d ->
        d.copy(logs = d.logs.filterNot { it.exerciseId == exerciseId && Fmt.date(it.ts, zone) == date })
    }

    // ───────────────────────── Notas

    fun saveNote(n: Note): Note {
        val t = now()
        val note = n.copy(
            id = if (n.id.isBlank()) newId("n_") else n.id,
            title = n.title.trim().take(80), body = n.body.take(4000),
            createdAt = if (n.createdAt == 0L) t else n.createdAt, updatedAt = t,
        )
        update { d ->
            val list = if (d.notes.any { it.id == note.id }) d.notes.map { if (it.id == note.id) note else it } else d.notes + note
            d.copy(notes = list)
        }
        checkMilestones()
        return note
    }

    fun deleteNote(id: String) = update("Anotación eliminada") { d -> d.copy(notes = d.notes.filterNot { it.id == id }) }

    // ───────────────────────── Recordatorios

    fun saveReminder(r: Reminder): Reminder {
        val rem = r.copy(id = if (r.id.isBlank()) newId("a_") else r.id, title = r.title.trim().ifEmpty { "Hora de entrenar" }.take(60), body = r.body.take(160))
        update { d ->
            val list = if (d.reminders.any { it.id == rem.id }) d.reminders.map { if (it.id == rem.id) rem else it } else d.reminders + rem
            d.copy(reminders = list)
        }
        onRemindersChanged?.invoke()
        checkMilestones()
        return rem
    }

    fun deleteReminder(id: String) {
        update("Recordatorio eliminado") { d -> d.copy(reminders = d.reminders.filterNot { it.id == id }) }
        onRemindersChanged?.invoke()
    }

    // ───────────────────────── Búsqueda

    private fun boost(ex: Exercise): Double = if (inSet(ex.id)) 8.0 else 0.0

    fun search(query: String, limit: Int = 40): List<Search.Hit> = Search.search(searchIndex, query, limit) { boost(it) }

    fun rememberSearch(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        update { d -> d.copy(searchHistory = (listOf(t) + d.searchHistory.filterNot { it.equals(t, true) }).take(8)) }
    }

    fun clearSearchHistory() = update { it.copy(searchHistory = emptyList()) }

    // ───────────────────────── Hitos

    fun aggregates(): Stats.Aggregates =
        Stats.aggregates(data, exById, weeklyGoal, mondayFirst, today(), formula, zone)

    fun milestoneStates(): List<MilestoneState> = Milestones.evaluate(data, aggregates())

    /** Revisa si algún hito se cumplió y lo marca. Devuelve los nuevos. */
    fun checkMilestones(): List<MilestoneDef> {
        val fresh = Milestones.newlyDone(data, aggregates())
        if (fresh.isEmpty()) return fresh
        val t = now()
        update { d -> d.copy(unlocked = d.unlocked + fresh.associate { it.id to t }) }
        fresh.forEach { events.tryEmit(AppEvent.Milestone(it)) }
        return fresh
    }

    fun unlock(id: String) {
        if (data.unlocked.containsKey(id)) return
        update { d -> d.copy(unlocked = d.unlocked + (id to now())) }
        checkMilestones()
    }

    // ───────────────────────── Copias de seguridad

    fun exportJson(): String {
        val b = Backup(exportedAt = now(), data = data)
        return Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }.encodeToString(Backup.serializer(), b)
    }

    fun exportCsv(): String {
        val sb = StringBuilder("fecha,hora,ejercicio,peso_kg,repeticiones,volumen_kg,rutina,nota\n")
        for (l in logs.sortedBy { it.ts }) {
            val name = exercise(l.exerciseId)?.name ?: l.exerciseId
            val d = Fmt.date(l.ts, zone)
            val r = l.routineId?.let { routine(it)?.name } ?: ""
            fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            sb.append("$d,${Fmt.hourMinute(l.ts, zone)},${q(name)},${Fmt.num(l.weightKg, 2)},${l.reps},${Fmt.num(l.volume, 2)},${q(r)},${q(l.note)}\n")
        }
        return sb.toString()
    }

    /** Reemplaza todos los datos con los de una copia. Devuelve un resumen o un error. */
    fun importJson(text: String): Result<String> {
        val parsed: AppData = try {
            val b = json.decodeFromString(Backup.serializer(), text)
            if (!b.app.equals("FITSTAT", true)) return Result.failure(IllegalArgumentException("El archivo no es una copia de FITSTAT."))
            b.data
        } catch (_: Throwable) {
            try {
                json.decodeFromString(AppData.serializer(), text)
            } catch (e: Throwable) {
                return Result.failure(IllegalArgumentException("No pude leer el archivo. ¿Es una copia de FITSTAT?"))
            }
        }
        val old = data
        update("Datos importados") { parsed.copy(profile = parsed.profile.copy(onboarded = true)) }
        val keys = old.settings.keys + parsed.settings.keys
        keys.forEach { k -> if (old.settings[k] != parsed.settings[k]) onSettingChanged?.invoke(k, parsed.settings[k] ?: Settings.default(k)) }
        onRemindersChanged?.invoke()
        return Result.success("${parsed.logs.size} series, ${parsed.routines.size} rutinas, ${parsed.notes.size} anotaciones")
    }

    /** Borra datos del usuario (se puede deshacer). [keepSettings] conserva la personalización. */
    fun eraseAll(keepSettings: Boolean = true) {
        update("Datos borrados") { d ->
            AppData(profile = d.profile.copy(onboarded = true), settings = if (keepSettings) d.settings else emptyMap())
        }
        onRemindersChanged?.invoke()
    }

    fun replaceAll(newData: AppData, label: String? = null) {
        update(label) { newData }
        onRemindersChanged?.invoke()
    }

    val hasUserData: Boolean get() = data.logs.isNotEmpty() || data.routines.isNotEmpty() || data.configs.isNotEmpty() || data.notes.isNotEmpty()

    companion object {
        /** Cantidad de colores de rutina (coincide con Palettes.all.size). */
        const val RoutineColors = 10
    }
}
