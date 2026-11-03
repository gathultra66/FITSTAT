package app.fitstat.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Cálculos de estadísticas a partir del registro de series. Todo el peso se guarda en kg. */
object Stats {
    fun e1rm(weight: Double, reps: Int, formula: String = "epley"): Double {
        if (weight <= 0.0 || reps <= 0) return 0.0
        if (reps == 1) return weight
        return if (formula == "brzycki") {
            if (reps >= 37) weight else weight * 36.0 / (37.0 - reps)
        } else {
            weight * (1.0 + reps / 30.0)
        }
    }

    /** Puntaje de una serie para detectar récords: 1RM estimado, o las repeticiones si no hubo peso (peso corporal, cardio). */
    fun score(weight: Double, reps: Int, formula: String = "epley"): Double =
        if (weight > 0.0) e1rm(weight, reps, formula) else reps.toDouble()

    class Session(val date: LocalDate, val sets: List<SetLog>, formula: String) {
        val volume: Double = sets.sumOf { it.volume }
        val maxWeight: Double = sets.maxOfOrNull { it.weightKg } ?: 0.0
        val topE1rm: Double = sets.maxOfOrNull { e1rm(it.weightKg, it.reps, formula) } ?: 0.0
        val reps: Int = sets.sumOf { it.reps }
        val ts: Long = sets.maxOfOrNull { it.ts } ?: 0L
    }

    class ExStats(
        val sessions: List<Session>,
        val totalSets: Int,
        val totalVolume: Double,
        val bestWeight: Double,
        val bestE1rm: Double,
        val bestSet: SetLog?,
        val last: SetLog?,
        val bestReps: Int = 0,
    ) {
        val lastSession: Session? get() = sessions.lastOrNull()
        val isEmpty: Boolean get() = totalSets == 0
    }

    class Point(val date: LocalDate, val value: Double, val isRecord: Boolean = false)

    fun sessions(logs: List<SetLog>, exerciseId: String, formula: String, zone: ZoneId = Fmt.zone()): List<Session> =
        logs.asSequence().filter { it.exerciseId == exerciseId }
            .groupBy { Fmt.date(it.ts, zone) }
            .toSortedMap()
            .map { (d, s) -> Session(d, s.sortedBy { it.ts }, formula) }

    fun forExercise(logs: List<SetLog>, exerciseId: String, formula: String, zone: ZoneId = Fmt.zone()): ExStats {
        val mine = logs.filter { it.exerciseId == exerciseId }
        val ses = sessions(mine, exerciseId, formula, zone)
        var bestE = 0.0
        var bestSet: SetLog? = null
        for (s in mine) {
            val e = e1rm(s.weightKg, s.reps, formula)
            if (e > bestE) { bestE = e; bestSet = s }
        }
        return ExStats(
            sessions = ses,
            totalSets = mine.size,
            totalVolume = mine.sumOf { it.volume },
            bestWeight = mine.maxOfOrNull { it.weightKg } ?: 0.0,
            bestE1rm = bestE,
            bestSet = bestSet,
            last = mine.maxByOrNull { it.ts },
            bestReps = mine.maxOfOrNull { it.reps } ?: 0,
        )
    }

    fun rangeStart(range: String, today: LocalDate): LocalDate? = when (range) {
        "4w" -> today.minusDays(27)
        "8w" -> today.minusDays(55)
        "12w" -> today.minusDays(83)
        "6m" -> today.minusMonths(6)
        else -> null
    }

    fun metricValue(s: Session, metric: String): Double = when (metric) {
        "weight" -> s.maxWeight
        "e1rm" -> s.topE1rm
        "reps" -> s.reps.toDouble()
        else -> s.volume
    }

    /** Serie de puntos (una por sesión) para la gráfica de un ejercicio; marca los récords. */
    fun series(sessions: List<Session>, metric: String, from: LocalDate?): List<Point> {
        var best = 0.0
        val out = ArrayList<Point>()
        for (s in sessions) {
            val v = metricValue(s, metric)
            val rec = v > best && best > 0.0
            if (v > best) best = v
            if (from == null || !s.date.isBefore(from)) out.add(Point(s.date, v, rec))
        }
        return out
    }

    // ───────────────────────── Semanas y racha

    fun weekStart(d: LocalDate, mondayFirst: Boolean): LocalDate =
        d.with(TemporalAdjusters.previousOrSame(if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))

    fun workoutDays(logs: List<SetLog>, zone: ZoneId = Fmt.zone()): Set<LocalDate> =
        logs.mapTo(HashSet()) { Fmt.date(it.ts, zone) }

    class Week(val start: LocalDate, val workouts: Int, val sets: Int, val volume: Double)

    /** Resumen de las últimas [count] semanas, de la más antigua a la actual. */
    fun weeks(logs: List<SetLog>, count: Int, mondayFirst: Boolean, today: LocalDate, zone: ZoneId = Fmt.zone()): List<Week> {
        val cur = weekStart(today, mondayFirst)
        val first = cur.minusWeeks((count - 1).toLong())
        val byDay = HashMap<LocalDate, MutableList<SetLog>>()
        for (l in logs) byDay.getOrPut(Fmt.date(l.ts, zone)) { ArrayList() }.add(l)
        return (0 until count).map { i ->
            val ws = first.plusWeeks(i.toLong())
            var w = 0
            var s = 0
            var v = 0.0
            for (k in 0 until 7) {
                val l = byDay[ws.plusDays(k.toLong())] ?: continue
                w++
                s += l.size
                v += l.sumOf { it.volume }
            }
            Week(ws, w, s, v)
        }
    }

    fun daysThisWeek(logs: List<SetLog>, mondayFirst: Boolean, today: LocalDate, zone: ZoneId = Fmt.zone()): Set<LocalDate> {
        val ws = weekStart(today, mondayFirst)
        val we = ws.plusDays(6)
        return workoutDays(logs, zone).filterTo(HashSet()) { !it.isBefore(ws) && !it.isAfter(we) }
    }

    private fun weekCounts(days: Set<LocalDate>, mondayFirst: Boolean): Map<LocalDate, Int> =
        days.groupingBy { weekStart(it, mondayFirst) }.eachCount()

    /** Semanas seguidas cumpliendo la meta. La semana en curso cuenta si ya la cumpliste. */
    fun streakWeeks(days: Set<LocalDate>, goal: Int, mondayFirst: Boolean, today: LocalDate): Int {
        if (days.isEmpty() || goal <= 0) return 0
        val counts = weekCounts(days, mondayFirst)
        var ws = weekStart(today, mondayFirst)
        var n = 0
        if ((counts[ws] ?: 0) >= goal) n++
        ws = ws.minusWeeks(1)
        var guard = 0
        while ((counts[ws] ?: 0) >= goal && guard++ < 600) {
            n++
            ws = ws.minusWeeks(1)
        }
        return n
    }

    fun bestStreakWeeks(days: Set<LocalDate>, goal: Int, mondayFirst: Boolean): Int {
        if (days.isEmpty() || goal <= 0) return 0
        val counts = weekCounts(days, mondayFirst)
        val weeks = counts.keys.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (w in weeks) {
            val ok = (counts[w] ?: 0) >= goal
            if (!ok) { run = 0; prev = w; continue }
            run = if (prev != null && prev.plusWeeks(1) == w && run > 0) run + 1 else 1
            prev = w
            if (run > best) best = run
        }
        return best
    }

    // ───────────────────────── Mapa de calor

    /** Series por día de las últimas [weeks] semanas. Índice = semana*7 + posición del día en la semana. -1 = futuro. */
    class Heat(val start: LocalDate, val weeks: Int, val cells: IntArray) {
        fun at(week: Int, day: Int): Int = cells[week * 7 + day]
        val max: Int get() = cells.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    fun heat(logs: List<SetLog>, weeks: Int, mondayFirst: Boolean, today: LocalDate, zone: ZoneId = Fmt.zone()): Heat {
        val cur = weekStart(today, mondayFirst)
        val start = cur.minusWeeks((weeks - 1).toLong())
        val cells = IntArray(weeks * 7)
        val perDay = HashMap<LocalDate, Int>()
        for (l in logs) {
            val d = Fmt.date(l.ts, zone)
            if (!d.isBefore(start)) perDay[d] = (perDay[d] ?: 0) + 1
        }
        for (w in 0 until weeks) for (k in 0 until 7) {
            val d = start.plusDays((w * 7 + k).toLong())
            cells[w * 7 + k] = if (d.isAfter(today)) -1 else perDay[d] ?: 0
        }
        return Heat(start, weeks, cells)
    }

    // ───────────────────────── Músculos

    class MuscleLoad(val levels: Map<Muscle, Int>, val heat: Map<Muscle, Float>, val sets: Map<Muscle, Float>)

    fun muscleLoad(logs: List<SetLog>, exById: Map<String, Exercise>, days: Int, today: LocalDate, zone: ZoneId = Fmt.zone()): MuscleLoad {
        val from = today.minusDays((days - 1).toLong())
        val score = HashMap<Muscle, Float>()
        for (l in logs) {
            val d = Fmt.date(l.ts, zone)
            if (d.isBefore(from) || d.isAfter(today)) continue
            val ex = exById[l.exerciseId] ?: continue
            for (m in ex.primary) score[m] = (score[m] ?: 0f) + 1f
            for (m in ex.secondary) score[m] = (score[m] ?: 0f) + 0.5f
        }
        val max = score.values.maxOrNull() ?: 0f
        val levels = HashMap<Muscle, Int>()
        val heat = HashMap<Muscle, Float>()
        for ((m, v) in score) {
            levels[m] = when {
                v >= 6f -> 2
                v >= 1.5f -> 1
                else -> 0
            }
            heat[m] = if (max > 0f) (v / max).coerceIn(0f, 1f) else 0f
        }
        return MuscleLoad(levels, heat, score)
    }

    // ───────────────────────── Récords

    class RecordEvent(val ts: Long, val exerciseId: String, val weightKg: Double, val reps: Int, val e1rm: Double)

    /** Todos los récords (mejor 1RM estimado) en orden del más reciente al más antiguo. */
    fun recordEvents(logs: List<SetLog>, formula: String): List<RecordEvent> {
        val best = HashMap<String, Double>()
        val out = ArrayList<RecordEvent>()
        for (l in logs.sortedBy { it.ts }) {
            val e = score(l.weightKg, l.reps, formula)
            if (e <= 0.0) continue
            val prev = best[l.exerciseId]
            if (prev == null) {
                best[l.exerciseId] = e
            } else if (e > prev + 0.0001) {
                best[l.exerciseId] = e
                out.add(RecordEvent(l.ts, l.exerciseId, l.weightKg, l.reps, e))
            }
        }
        out.reverse()
        return out
    }

    /** Mejor serie por ejercicio, de la más reciente a la más antigua. */
    fun bestSets(logs: List<SetLog>, formula: String): List<Pair<String, SetLog>> {
        val best = HashMap<String, Pair<Double, SetLog>>()
        for (l in logs) {
            val e = score(l.weightKg, l.reps, formula)
            val cur = best[l.exerciseId]
            if (cur == null || e > cur.first) best[l.exerciseId] = e to l
        }
        return best.map { it.key to it.value.second }.sortedByDescending { it.second.ts }
    }

    // ───────────────────────── Plan de hoy

    class TodayPlan(val routines: List<Routine>, val exercises: List<Exercise>)

    fun todayPlan(data: AppData, exById: Map<String, Exercise>, today: LocalDate): TodayPlan {
        val dow = today.dayOfWeek.value
        val routines = data.routines.filter { dow in it.days }
        val covered = routines.flatMap { r -> r.items.map { it.exerciseId } }.toSet()
        val exs = data.configs.values
            .filter { it.inSet && dow in it.days && it.exerciseId !in covered }
            .mapNotNull { exById[it.exerciseId] }
        return TodayPlan(routines, exs)
    }

    // ───────────────────────── Totales (para hitos)

    class Aggregates(
        val workoutDays: Int,
        val totalSets: Int,
        val totalVolume: Double,
        val distinctExercises: Int,
        val categoriesTrained: Int,
        val prCount: Int,
        val bestStreak: Int,
        val currentStreak: Int,
        val maxWeight: Double,
        val totalReps: Int,
    )

    fun aggregates(
        data: AppData,
        exById: Map<String, Exercise>,
        goal: Int,
        mondayFirst: Boolean,
        today: LocalDate,
        formula: String,
        zone: ZoneId = Fmt.zone(),
    ): Aggregates {
        val logs = data.logs
        val days = workoutDays(logs, zone)
        val cats = HashSet<String>()
        for (l in logs) exById[l.exerciseId]?.let { cats.add(it.category) }
        return Aggregates(
            workoutDays = days.size,
            totalSets = logs.size,
            totalVolume = logs.sumOf { it.volume },
            distinctExercises = logs.mapTo(HashSet()) { it.exerciseId }.size,
            categoriesTrained = cats.size,
            prCount = recordEvents(logs, formula).size,
            bestStreak = bestStreakWeeks(days, goal, mondayFirst),
            currentStreak = streakWeeks(days, goal, mondayFirst, today),
            maxWeight = logs.maxOfOrNull { it.weightKg } ?: 0.0,
            totalReps = logs.sumOf { it.reps },
        )
    }
}
