package app.fitstat.bot

import app.fitstat.data.Categories
import app.fitstat.data.Exercise
import app.fitstat.data.Fmt
import app.fitstat.data.Milestones
import app.fitstat.data.Routine
import app.fitstat.data.Search
import app.fitstat.data.SettingSpec
import app.fitstat.data.Settings
import app.fitstat.data.Stats
import app.fitstat.data.Weekdays
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.LocalDate

internal fun tool(
    name: String,
    description: String,
    parameters: JsonObject = S.obj(),
    write: Boolean = false,
    destructive: (JsonObject) -> Boolean = { false },
    alwaysConfirm: Boolean = false,
    plan: (JsonObject, ToolCtx) -> String = { _, _ -> "" },
    run: (JsonObject, ToolCtx) -> ToolOutcome,
) = BotTool(name, description, parameters, write, destructive, alwaysConfirm, plan, run)

// ───────────────────────── Piezas compartidas

internal fun exerciseRow(ex: Exercise, c: ToolCtx): JsonObject = buildJsonObject {
    put("id", ex.id)
    put("name", ex.name)
    put("category", ex.category)
    if (ex.equipment.isNotBlank()) put("equipment", ex.equipment)
    if (ex.level.isNotBlank()) put("level", ex.level)
    strs("primary_muscles", ex.primary.map { it.label })
    put("in_my_set", c.repo.inSet(ex.id))
    if (ex.custom) put("custom", true)
}

internal fun routineJson(r: Routine, c: ToolCtx, detail: Boolean): JsonObject = buildJsonObject {
    val repo = c.repo
    put("id", r.id)
    put("name", r.name)
    put("days", buildJsonArrayOf(r.days.sorted()))
    put("days_label", Weekdays.label(r.days))
    put("exercise_count", r.items.size)
    put("approx_minutes", repo.routineMinutes(r))
    if (r.note.isNotBlank()) put("note", r.note)
    if (detail) {
        arr("exercises", r.items.map { it0 ->
            val ex = repo.exercise(it0.exerciseId)
            buildJsonObject {
                put("exercise_id", it0.exerciseId)
                put("name", ex?.name ?: "(ejercicio eliminado)")
                put("sets", repo.itemSets(it0))
                put("reps", repo.itemRepsLabel(it0))
                put("rest_sec", repo.config(it0.exerciseId).restSec)
            }
        })
    } else {
        strs("exercise_names", r.items.take(12).mapNotNull { repo.exercise(it.exerciseId)?.name })
    }
}

private fun buildJsonArrayOf(nums: List<Int>) = kotlinx.serialization.json.buildJsonArray { nums.forEach { add(JsonPrimitive(it)) } }

internal fun settingJson(sp: SettingSpec, c: ToolCtx): JsonObject = buildJsonObject {
    put("key", sp.key)
    put("title", sp.title)
    put("page", sp.page)
    put("value", c.repo.prefs.str(sp.key))
    put("default", sp.default)
    put("allowed", Settings.optionsText(sp))
    put("description", sp.desc)
    sp.visibleIf?.let { put("only_visible_if", it.first + "=" + it.second) }
}

internal fun resolvePage(p: String): String? {
    val n = Search.norm(p)
    return Settings.pages.firstOrNull { it.id == n || Search.norm(it.title) == n }?.id
        ?: Settings.pages.firstOrNull { Search.norm(it.title).contains(n) && n.length >= 3 }?.id
}

private fun dateOf(ts: Long, c: ToolCtx): LocalDate = Fmt.date(ts, c.repo.zone)

// ───────────────────────── Herramientas de lectura

internal val readTools: List<BotTool> = listOf(
    tool(
        "get_overview",
        "Resumen general de la app y del usuario: fecha y hora actuales, nombre, meta semanal, unidad de peso, conteos, semana en curso, racha, plan de hoy, próximo hito y pantalla abierta. Úsala al empezar si necesitas contexto.",
    ) { _, c ->
        val r = c.repo
        val today = r.today()
        val agg = r.aggregates()
        val days = Stats.daysThisWeek(r.logs, r.mondayFirst, today, r.zone)
        val plan = Stats.todayPlan(r.data, r.exById, today)
        val ms = r.milestoneStates()
        val next = Milestones.next(ms)
        val zdt = Instant.ofEpochMilli(r.now()).atZone(r.zone)
        ok("Revisó tu resumen") {
            put("today", today.toString())
            put("weekday", Fmt.dayName(today.dayOfWeek))
            put("time", "%02d:%02d".format(zdt.hour, zdt.minute))
            put("timezone", r.zone.id)
            putJsonObject("user") {
                put("name", r.profile.name)
                put("weekly_goal_days", r.weeklyGoal)
                put("weight_unit", c.units)
                put("week_starts_on", if (r.mondayFirst) "monday" else "sunday")
            }
            putJsonObject("counts") {
                put("exercises_in_catalog", r.exercises.size)
                put("custom_exercises", r.custom.size)
                put("in_my_set", r.setIds.size)
                put("routines", r.routines.size)
                put("logged_sets", agg.totalSets)
                put("training_days", agg.workoutDays)
                put("notes", r.notes.size)
                put("reminders", r.reminders.size)
            }
            putJsonObject("this_week") {
                put("days_trained", days.size)
                put("goal", r.weeklyGoal)
                strs("dates", days.sorted().map { it.toString() })
            }
            put("streak_weeks", agg.currentStreak)
            put("best_streak_weeks", agg.bestStreak)
            putJsonObject("today_plan") {
                arr("routines", plan.routines.map { buildJsonObject { put("id", it.id); put("name", it.name) } })
                arr("exercises", plan.exercises.map { buildJsonObject { put("id", it.id); put("name", it.name) } })
            }
            putJsonObject("milestones") {
                put("unlocked", ms.count { it.done })
                put("total", ms.size)
                if (next != null) putJsonObject("next") {
                    put("title", next.def.title)
                    put("description", next.def.desc)
                    put("progress_percent", (next.progress * 100).toInt())
                }
            }
            put("current_screen", c.host.currentScreen())
            put("workout_in_progress", c.host.workoutActive())
            put("bot_model", r.prefs.str("bot.model"))
        }
    },

    tool(
        "list_settings",
        "Lista los ajustes de la app con su valor actual, valor por defecto y valores permitidos. Sin 'page' devuelve todos. Páginas: " +
            Settings.pages.joinToString(", ") { it.id },
        S.obj(listOf("page" to S.str("Página de ajustes (opcional), por ejemplo 'tema' o 'animaciones'."))),
    ) { a, c ->
        val page = a.s("page")?.let { resolvePage(it) ?: return@tool fail("No existe la página de ajustes '${a.s("page")}'. Páginas: ${Settings.pages.joinToString { it.id }}.") }
        val specs = if (page == null) Settings.all else Settings.specsOf(page)
        ok("Revisó los ajustes") {
            arr("pages", Settings.pages.map { buildJsonObject { put("id", it.id); put("title", it.title) } })
            arr("settings", specs.map { settingJson(it, c) })
        }
    },

    tool(
        "get_setting",
        "Devuelve el valor actual de un ajuste.",
        S.obj(listOf("key" to S.str("Clave del ajuste, por ejemplo 'theme.mode'.")), listOf("key")),
    ) { a, c ->
        val sp = Settings.byKey[a.s("key").orEmpty()] ?: return@tool fail("No existe el ajuste '${a.s("key")}'. Usa list_settings.")
        ok("Revisó un ajuste") { put("setting", settingJson(sp, c)) }
    },

    tool(
        "get_home_layout",
        "Cómo está la distribución de una pantalla editable (page: inicio, ejercicios o rutinas; por defecto el inicio): bloques en orden con su tamaño, y cuáles se pueden agregar. Para cambiarla usa edit_home.",
        S.obj(listOf("page" to S.str("Pantalla: inicio (por defecto), ejercicios o rutinas.", listOf("inicio", "ejercicios", "rutinas")))),
    ) { a, c ->
        val page = app.fitstat.data.LayoutPage.find(a.s("page")) ?: return@tool fail("Esa pantalla no existe. Usa inicio, ejercicios o rutinas.")
        val slots = c.repo.pageSlots(page)
        ok("Revisó ${app.fitstat.data.LayoutPage.title(page).lowercase()}") {
            put("page", page)
            put("customized", c.repo.pageCustomized(page))
            arr("layout", homeLayoutJson(slots, c.repo.routines))
            arr("available_to_add", app.fitstat.data.HomeLayout.missing(slots, page).map { d ->
                buildJsonObject { put("id", d.id); put("title", d.title); put("what", d.desc); strs("sizes", d.sizes.map { it.id }) }
            })
        }
    },

    tool(
        "list_categories",
        "Las 10 categorías de ejercicios con cuántos ejercicios tiene cada una.",
    ) { _, c ->
        ok("Revisó las categorías") {
            arr("categories", Categories.all.map { k ->
                buildJsonObject {
                    put("id", k.id)
                    put("name", k.name)
                    put("exercise_count", c.repo.exercises.count { it.category == k.id })
                    put("in_my_set", c.repo.exercises.count { it.category == k.id && c.repo.inSet(it.id) })
                }
            })
        }
    },

    tool(
        "search_exercises",
        "Busca ejercicios por nombre (tolera errores y sinónimos), por categoría o entre los de 'mi set'. Devuelve ids que se usan en otras herramientas. Sin 'query' lista por categoría o por mi set.",
        S.obj(
            listOf(
                "query" to S.str("Texto a buscar, por ejemplo 'press banca'."),
                "category" to S.str("Categoría: " + Categories.all.joinToString { it.id }),
                "only_my_set" to S.bool("true para buscar solo entre los ejercicios de mi set."),
                "limit" to S.int("Máximo de resultados (1-30, por defecto 12)."),
            ),
        ),
    ) { a, c ->
        val limit = (a.i("limit") ?: 12).coerceIn(1, 30)
        val cat = a.s("category")?.let { parseCategory(it) ?: return@tool fail("Categoría no válida. Opciones: ${Categories.all.joinToString { k -> k.id }}.") }
        val onlySet = a.b("only_my_set") == true
        val q = a.s("query")
        val base: List<Exercise> = if (q != null) c.repo.search(q, 60).map { it.ex } else c.repo.exercises.sortedBy { it.name }
        val rows = base.filter { (cat == null || it.category == cat) && (!onlySet || c.repo.inSet(it.id)) }
        ok("Buscó ejercicios" + (q?.let { ": «$it»" } ?: "")) {
            put("total_matches", rows.size)
            arr("exercises", rows.take(limit).map { exerciseRow(it, c) })
        }
    },

    tool(
        "get_exercise",
        "Detalle de un ejercicio: músculos, descripción, mi plan (series, repeticiones, descanso, días, notas) y sus estadísticas (mejor serie, 1RM estimado, volumen y últimas sesiones).",
        S.obj(listOf("exercise" to S.str("Id o nombre del ejercicio.")), listOf("exercise")),
    ) { a, c ->
        val ex = c.findExercise(a.s("exercise").orEmpty())
            ?: return@tool fail("No encontré ese ejercicio. Opciones parecidas: ${c.suggestionText(a.s("exercise").orEmpty())}.")
        val r = c.repo
        val cfg = r.config(ex.id)
        val st = Stats.forExercise(r.logs, ex.id, r.formula, r.zone)
        ok("Revisó «${ex.name}»") {
            put("id", ex.id)
            put("name", ex.name)
            put("category", ex.category)
            put("equipment", ex.equipment)
            put("level", ex.level)
            put("mechanic", ex.mechanic)
            strs("primary_muscles", ex.primary.map { it.label })
            strs("secondary_muscles", ex.secondary.map { it.label })
            put("description", ex.description)
            put("custom", ex.custom)
            putJsonObject("my_plan") {
                put("in_my_set", cfg.inSet)
                put("sets", cfg.sets)
                put("reps", cfg.repsLabel)
                put("rest_sec", cfg.restSec)
                put("days", buildJsonArrayOf(cfg.days.sorted()))
                put("days_label", Weekdays.label(cfg.days))
                put("notes", cfg.notes)
                put("favorite", cfg.favorite)
                put("target_weight", c.disp(cfg.targetWeightKg))
            }
            putJsonObject("stats") {
                put("weight_unit", c.units)
                put("total_sets", st.totalSets)
                put("sessions", st.sessions.size)
                put("total_volume", c.disp(st.totalVolume))
                put("best_weight", c.disp(st.bestWeight))
                put("best_estimated_1rm", c.disp(st.bestE1rm))
                st.bestSet?.let { put("best_set", "${c.disp(it.weightKg)} × ${it.reps}") }
                st.last?.let { put("last_set_date", dateOf(it.ts, c).toString()) }
                arr("recent_sessions", st.sessions.takeLast(6).reversed().map { s ->
                    buildJsonObject {
                        put("date", s.date.toString())
                        put("sets", s.sets.joinToString(", ") { "${c.disp(it.weightKg)}×${it.reps}" })
                        put("volume", c.disp(s.volume))
                    }
                })
            }
        }
    },

    tool(
        "list_routines",
        "Todas las rutinas con sus días, cantidad de ejercicios y duración aproximada.",
    ) { _, c ->
        ok("Revisó tus rutinas") { arr("routines", c.repo.routines.map { routineJson(it, c, detail = false) }) }
    },

    tool(
        "get_routine",
        "Detalle de una rutina: ejercicios en orden con series, repeticiones y descanso.",
        S.obj(listOf("routine" to S.str("Id o nombre de la rutina.")), listOf("routine")),
    ) { a, c ->
        val r = c.findRoutine(a.s("routine").orEmpty()) ?: return@tool fail("No encontré esa rutina. Rutinas: ${c.repo.routines.joinToString { it.name }}.")
        ok("Revisó la rutina «${r.name}»") { put("routine", routineJson(r, c, detail = true)) }
    },

    tool(
        "get_stats",
        "Estadísticas generales. kind='summary': totales y semana en curso; 'weekly': entrenamientos, series y volumen por semana; 'muscles': series por músculo en los últimos días; 'records': mejores marcas por ejercicio y últimos récords.",
        S.obj(
            listOf(
                "kind" to S.str("Tipo de estadística.", listOf("summary", "weekly", "muscles", "records")),
                "weeks" to S.int("Semanas a mostrar en 'weekly' (1-26, por defecto 8)."),
                "days" to S.int("Días a considerar en 'muscles' (1-90, por defecto 30)."),
            ),
            listOf("kind"),
        ),
    ) { a, c ->
        val r = c.repo
        val today = r.today()
        when (a.s("kind")) {
            "summary" -> {
                val agg = r.aggregates()
                val days = Stats.daysThisWeek(r.logs, r.mondayFirst, today, r.zone)
                ok("Revisó tus estadísticas") {
                    put("weight_unit", c.units)
                    put("training_days", agg.workoutDays)
                    put("total_sets", agg.totalSets)
                    put("total_reps", agg.totalReps)
                    put("total_volume", c.disp(agg.totalVolume))
                    put("distinct_exercises", agg.distinctExercises)
                    put("categories_trained", agg.categoriesTrained)
                    put("personal_records", agg.prCount)
                    put("heaviest_weight", c.disp(agg.maxWeight))
                    put("current_streak_weeks", agg.currentStreak)
                    put("best_streak_weeks", agg.bestStreak)
                    put("days_this_week", days.size)
                    put("weekly_goal", r.weeklyGoal)
                }
            }
            "weekly" -> {
                val n = (a.i("weeks") ?: 8).coerceIn(1, 26)
                ok("Revisó tus semanas") {
                    put("weight_unit", c.units)
                    arr("weeks", Stats.weeks(r.logs, n, r.mondayFirst, today, r.zone).map { w ->
                        buildJsonObject {
                            put("week_start", w.start.toString())
                            put("workouts", w.workouts)
                            put("sets", w.sets)
                            put("volume", c.disp(w.volume))
                        }
                    })
                }
            }
            "muscles" -> {
                val n = (a.i("days") ?: 30).coerceIn(1, 90)
                val load = Stats.muscleLoad(r.logs, r.exById, n, today, r.zone)
                ok("Revisó los músculos trabajados") {
                    put("days", n)
                    arr("muscles", load.sets.entries.sortedByDescending { it.value }.map { (m, v) ->
                        buildJsonObject { put("muscle", m.label); put("weighted_sets", Math.round(v * 10) / 10.0) }
                    })
                    strs("not_trained", app.fitstat.data.Muscle.entries.filter { it !in load.sets.keys }.map { it.label })
                }
            }
            "records" -> {
                val best = Stats.bestSets(r.logs, r.formula).sortedByDescending { Stats.score(it.second.weightKg, it.second.reps, r.formula) }.take(15)
                val recent = Stats.recordEvents(r.logs, r.formula).take(8)
                ok("Revisó tus récords") {
                    put("weight_unit", c.units)
                    arr("best_sets", best.map { (id, s) ->
                        buildJsonObject {
                            put("exercise", r.exercise(id)?.name ?: id)
                            put("weight", c.disp(s.weightKg))
                            put("reps", s.reps)
                            put("date", dateOf(s.ts, c).toString())
                        }
                    })
                    arr("recent_records", recent.map { e ->
                        buildJsonObject {
                            put("exercise", r.exercise(e.exerciseId)?.name ?: e.exerciseId)
                            put("weight", c.disp(e.weightKg))
                            put("reps", e.reps)
                            put("date", dateOf(e.ts, c).toString())
                        }
                    })
                }
            }
            else -> fail("kind debe ser summary, weekly, muscles o records.")
        }
    },

    tool(
        "list_logs",
        "Series registradas, de la más reciente a la más antigua. Útil para ver qué entrenó el usuario y cuándo.",
        S.obj(
            listOf(
                "exercise" to S.str("Id o nombre del ejercicio (opcional)."),
                "days" to S.int("Solo los últimos N días (opcional)."),
                "limit" to S.int("Máximo de series (1-100, por defecto 40)."),
            ),
        ),
    ) { a, c ->
        val r = c.repo
        val ex = a.s("exercise")?.let { c.findExercise(it) ?: return@tool fail("No encontré el ejercicio '$it'.") }
        val limit = (a.i("limit") ?: 40).coerceIn(1, 100)
        val from = a.i("days")?.let { r.today().minusDays((it - 1).toLong().coerceAtLeast(0)) }
        val rows = r.logs.asSequence()
            .filter { ex == null || it.exerciseId == ex.id }
            .filter { from == null || !dateOf(it.ts, c).isBefore(from) }
            .sortedByDescending { it.ts }.take(limit).toList()
        ok("Revisó tu registro de series") {
            put("weight_unit", c.units)
            put("count", rows.size)
            arr("logs", rows.map { l ->
                buildJsonObject {
                    put("id", l.id)
                    put("date", dateOf(l.ts, c).toString())
                    put("exercise", r.exercise(l.exerciseId)?.name ?: l.exerciseId)
                    put("weight", c.disp(l.weightKg))
                    put("reps", l.reps)
                    if (l.note.isNotBlank()) put("note", l.note)
                }
            })
        }
    },

    tool(
        "list_notes",
        "Anotaciones del usuario (título, texto, si están fijadas y el ejercicio al que pertenecen).",
        S.obj(listOf("query" to S.str("Texto a buscar en título o contenido (opcional)."), "pinned_only" to S.bool("Solo las fijadas."))),
    ) { a, c ->
        val q = a.s("query")?.let { Search.norm(it) }
        val rows = c.repo.notes.filter { n ->
            (a.b("pinned_only") != true || n.pinned) && (q == null || Search.norm(n.title + " " + n.body).contains(q))
        }.sortedByDescending { it.updatedAt }
        ok("Revisó tus anotaciones") {
            arr("notes", rows.take(40).map { n ->
                buildJsonObject {
                    put("id", n.id)
                    put("title", n.title)
                    put("body", n.body.take(600))
                    put("pinned", n.pinned)
                    n.exerciseId?.let { id -> put("exercise", c.repo.exercise(id)?.name ?: id) }
                    put("updated", dateOf(n.updatedAt, c).toString())
                }
            })
        }
    },

    tool(
        "get_workout_status",
        "Estado del entrenamiento guiado en curso: rutina, minutos transcurridos, series registradas en esta sesión, ejercicio actual y lista de ejercicios. Dice si no hay ninguno.",
    ) { _, c ->
        val w = c.host.workoutStatus()
        if (w == null) {
            ok("Revisó el entrenamiento en curso") { put("in_progress", false) }
        } else {
            ok("Revisó el entrenamiento en curso") {
                put("in_progress", !w.finished)
                put("finished_showing_summary", w.finished)
                put("routine", w.routine ?: "Entrenamiento libre")
                put("minutes", w.minutes)
                put("sets_logged", w.sets)
                w.current?.let { put("current_exercise", it) }
                put("resting", w.resting)
                strs("exercises", w.exercises)
                if (w.skipped.isNotEmpty()) strs("skipped", w.skipped)
            }
        }
    },

    tool("list_reminders", "Recordatorios de entrenamiento con hora, días y si están activos.") { _, c ->
        ok("Revisó tus recordatorios") {
            put("reminders_enabled", c.repo.prefs.bool("reminders.enabled"))
            arr("reminders", c.repo.reminders.map { m ->
                buildJsonObject {
                    put("id", m.id)
                    put("title", m.title)
                    put("time", Fmt.time(m.hour, m.minute))
                    put("days", buildJsonArrayOf(m.days.sorted()))
                    put("days_label", Weekdays.label(m.days))
                    put("enabled", m.enabled)
                    m.routineId?.let { id -> put("routine", c.repo.routine(id)?.name ?: id) }
                }
            })
        }
    },

    tool(
        "list_milestones",
        "Hitos (logros) con su avance.",
        S.obj(listOf("filter" to S.str("Cuáles mostrar.", listOf("all", "done", "pending")))),
    ) { a, c ->
        val all = c.repo.milestoneStates()
        val rows = when (a.s("filter")) {
            "done" -> all.filter { it.done }
            "pending" -> all.filter { !it.done }
            else -> all
        }
        ok("Revisó tus hitos") {
            put("unlocked", all.count { it.done })
            put("total", all.size)
            arr("milestones", rows.map { m ->
                buildJsonObject {
                    put("title", m.def.title)
                    put("group", m.def.group)
                    put("description", m.def.desc)
                    put("done", m.done)
                    put("progress_percent", (m.progress * 100).toInt().coerceAtMost(100))
                }
            })
        }
    },
)
