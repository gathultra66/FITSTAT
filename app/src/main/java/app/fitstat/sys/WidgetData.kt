package app.fitstat.sys

import app.fitstat.data.Fmt
import app.fitstat.data.Repo
import app.fitstat.data.Stats
import app.fitstat.data.Weekdays
import java.time.LocalDate

enum class DayState { Trained, TodayOpen, Planned, Missed, Future }

enum class BotFeel { Calm, Happy, Sleepy, Love }

/** Lo que muestran los widgets, ya calculado (no depende de la interfaz ni de Compose). */
class WidgetData(
    val today: LocalDate,
    val hour: Int,
    val name: String,
    val dayLabels: List<String>,
    val dayStates: List<DayState>,
    val weekCount: Int,
    val goal: Int,
    val streakWeeks: Int,
    val planTitle: String?,
    val planSub: String,
    val planRoutineId: String?,
    val doneToday: Boolean,
    val todaySets: Int,
    val records: List<RecordRow>,
    val totalRecords: Int,
    val botLine: String,
    val feel: BotFeel,
) {
    class RecordRow(val exercise: String, val weight: String, val reps: Int, val when_: String)

    val goalDone: Boolean get() = weekCount >= goal

    /** Texto corto para lectores de pantalla. */
    fun describe(kind: WidgetKind): String = when (kind) {
        WidgetKind.Today -> (planTitle ?: "Día de descanso") + ". " + (if (doneToday) "Ya entrenaste hoy. " else "") + "Llevas $weekCount de $goal días esta semana."
        WidgetKind.Bot -> "FITBOT. $botLine"
        WidgetKind.Week -> "Racha semanal: $weekCount de $goal días. $streakWeeks semanas seguidas."
        WidgetKind.Records -> if (records.isEmpty()) "Aún no tienes récords." else "Récords recientes: " + records.joinToString(", ") { it.exercise + " " + it.weight }
    }

    companion object {
        fun from(repo: Repo): WidgetData {
            val today = repo.today()
            val zone = repo.zone
            val mf = repo.mondayFirst
            val goal = repo.weeklyGoal.coerceAtLeast(1)
            val logs = repo.logs
            val days = Stats.workoutDays(logs, zone)
            val ws = Stats.weekStart(today, mf)
            val plan = Stats.todayPlan(repo.data, repo.exById, today)

            val plannedDow = HashSet<Int>()
            repo.routines.forEach { plannedDow += it.days }
            repo.configs.values.filter { it.inSet }.forEach { plannedDow += it.days }

            val week = (0 until 7).map { ws.plusDays(it.toLong()) }
            val states = week.map { d ->
                val trained = d in days
                val planned = d.dayOfWeek.value in plannedDow
                when {
                    trained -> DayState.Trained
                    d == today -> DayState.TodayOpen
                    d.isAfter(today) -> if (planned) DayState.Planned else DayState.Future
                    planned -> DayState.Missed
                    else -> DayState.Future
                }
            }
            val labels = week.map { Weekdays.short(it.dayOfWeek.value) }
            val weekCount = week.count { it in days }
            val doneToday = today in days
            val todaySets = logs.count { Fmt.date(it.ts, zone) == today }

            val planTitle: String?
            val planSub: String
            val planId: String?
            if (plan.routines.isNotEmpty()) {
                val r = plan.routines.first()
                planTitle = if (plan.routines.size > 1) "${r.name} +${plan.routines.size - 1}" else r.name
                planSub = if (r.items.isEmpty()) "Rutina vacía" else Fmt.plural(r.items.size, "ejercicio", "ejercicios")
                planId = r.id
            } else if (plan.exercises.isNotEmpty()) {
                planTitle = "Tu set de hoy"
                planSub = Fmt.plural(plan.exercises.size, "ejercicio", "ejercicios")
                planId = null
            } else {
                planTitle = null
                planSub = if (weekCount >= goal) "Meta de la semana cumplida" else "Recupérate, también cuenta"
                planId = null
            }

            val units = repo.units
            val events = Stats.recordEvents(logs, repo.formula)
            val records = events.take(4).map { e ->
                WidgetData.RecordRow(
                    exercise = repo.exercise(e.exerciseId)?.name ?: e.exerciseId,
                    weight = Fmt.weight(e.weightKg, units),
                    reps = e.reps,
                    when_ = Fmt.relativeDay(Fmt.date(e.ts, zone), today),
                )
            }
            val hour = java.time.Instant.ofEpochMilli(repo.now()).atZone(zone).hour
            val streak = Stats.streakWeeks(days, goal, mf, today)

            val feel = when {
                hour >= 23 || hour < 5 -> BotFeel.Sleepy
                doneToday && weekCount >= goal -> BotFeel.Love
                doneToday -> BotFeel.Happy
                else -> BotFeel.Calm
            }
            val line = when {
                feel == BotFeel.Sleepy -> "A dormir: el músculo crece descansando."
                doneToday && weekCount >= goal -> "¡Meta cumplida! Eres una máquina."
                doneToday -> "¡Buen trabajo hoy!"
                planTitle != null -> "Hoy toca $planTitle."
                weekCount >= goal -> "Meta de la semana cumplida."
                else -> "Te faltan ${goal - weekCount} " + (if (goal - weekCount == 1) "día" else "días") + " para tu meta."
            }
            return WidgetData(
                today = today, hour = hour, name = repo.profile.name, dayLabels = labels, dayStates = states,
                weekCount = weekCount, goal = goal, streakWeeks = streak, planTitle = planTitle, planSub = planSub,
                planRoutineId = planId, doneToday = doneToday, todaySets = todaySets, records = records,
                totalRecords = events.size, botLine = line, feel = feel,
            )
        }

        /** Datos de ejemplo para las vistas previas cuando la persona aún no ha registrado nada. */
        fun sample(today: LocalDate = LocalDate.of(2026, 10, 28)): WidgetData = WidgetData(
            today = today, hour = 18, name = "", dayLabels = Weekdays.short,
            dayStates = listOf(
                DayState.Trained, DayState.Trained, DayState.TodayOpen, DayState.Planned,
                DayState.Planned, DayState.Future, DayState.Future,
            ),
            weekCount = 2, goal = 4, streakWeeks = 3, planTitle = "Pierna", planSub = "6 ejercicios",
            planRoutineId = null, doneToday = false, todaySets = 0,
            records = listOf(
                RecordRow("Sentadilla", "100 kg", 5, "Hoy"),
                RecordRow("Press de banca", "72.5 kg", 6, "Hace 3 días"),
                RecordRow("Peso muerto", "130 kg", 3, "Hace 1 semana"),
                RecordRow("Dominadas", "10 kg", 8, "Hace 2 semanas"),
            ),
            totalRecords = 14, botLine = "Hoy toca Pierna.", feel = BotFeel.Calm,
        )
    }
}
