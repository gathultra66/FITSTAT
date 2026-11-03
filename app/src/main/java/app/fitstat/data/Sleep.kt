package app.fitstat.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** De dónde sale una noche de sueño. */
object SleepSrc {
    /** Health Connect: la registró un reloj, pulsera o app de sueño. Es la única que trae fases. */
    const val HEALTH = "health"

    /** Estimada con el uso del teléfono (el tramo más largo de la noche sin tocarlo). Solo horas, sin fases. */
    const val PHONE = "phone"
    const val MANUAL = "manual"

    fun label(src: String) = when (src) {
        HEALTH -> "Reloj o pulsera (Health Connect)"
        PHONE -> "Estimado con el uso del teléfono"
        else -> "Registrado a mano"
    }

    fun short(src: String) = when (src) {
        HEALTH -> "Reloj"
        PHONE -> "Estimado"
        else -> "A mano"
    }

    /** Si dos fuentes cuentan la misma noche, gana la de mayor prioridad (lo que tú escribes a mano manda sobre todo). */
    fun priority(src: String) = when (src) {
        MANUAL -> 4
        HEALTH -> 3
        else -> 1
    }
}

object SleepStageType {
    const val AWAKE = "awake"
    const val LIGHT = "light"
    const val DEEP = "deep"
    const val REM = "rem"
    const val SLEEP = "sleep"

    val shown = listOf(AWAKE, REM, LIGHT, DEEP)

    fun label(t: String) = when (t) {
        AWAKE -> "Despierto"
        LIGHT -> "Ligero"
        DEEP -> "Profundo"
        REM -> "REM"
        else -> "Dormido"
    }
}

/** Una noche: la sesión principal (la más larga de ese despertar) y los demás descansos de ese día. */
class SleepNight(val date: LocalDate, val main: SleepSession, val extra: List<SleepSession>)

class SleepSummary(
    val nights: Int,
    /** Promedio de tiempo dormido por noche. */
    val avgAsleepMs: Long,
    /** Hora promedio de acostarse y de despertar, en minutos desde la medianoche (null si no hay noches). */
    val avgBedMin: Int?,
    val avgWakeMin: Int?,
    val metGoal: Int,
    /** Qué tanto varía la hora de dormir (desviación estándar en minutos); null con menos de 3 noches. */
    val bedSpreadMin: Int?,
    /** Noches con fases (de un reloj). */
    val withStages: Int,
) {
    /** Etiqueta de regularidad según [bedSpreadMin]. */
    val regularity: String?
        get() = bedSpreadMin?.let { if (it <= 30) "Muy regular" else if (it <= 60) "Regular" else "Irregular" }
}

/** Una pantalla encendida (on) o apagada (!on) a cierta hora. */
class ScreenEvent(val t: Long, val on: Boolean)

/** Lógica pura del sueño: noches, promedios y la estimación con el uso del teléfono. No depende de Android. */
object Sleep {
    const val MIN_MS = 20 * 60_000L
    const val MAX_MS = 20 * 3600_000L
    private const val MERGE_GAP_MS = 60 * 60_000L
    private const val MICRO_WAKE_MS = 10 * 60_000L
    private const val PHONE_MAX_MS = 14 * 3600_000L

    fun valid(s: SleepSession): Boolean = s.end - s.start in MIN_MS..MAX_MS

    fun durationMs(s: SleepSession): Long = (s.end - s.start).coerceAtLeast(0)

    private fun clipped(st: SleepStage, s: SleepSession): Long = (minOf(st.e, s.end) - maxOf(st.s, s.start)).coerceAtLeast(0)

    /** Tiempo despierto dentro de la noche (solo se conoce si hay fases). */
    fun awakeMs(s: SleepSession): Long = s.stages.filter { it.t == SleepStageType.AWAKE }.sumOf { clipped(it, s) }.coerceAtMost(durationMs(s))

    /** Tiempo dormido: la duración menos lo que se estuvo despierto. */
    fun asleepMs(s: SleepSession): Long = durationMs(s) - awakeMs(s)

    /** Si la noche trae fases de verdad (ligero, profundo o REM), no solo «dormido». */
    fun hasStages(s: SleepSession): Boolean = s.stages.any { it.t == SleepStageType.LIGHT || it.t == SleepStageType.DEEP || it.t == SleepStageType.REM }

    /** Milisegundos en cada fase (solo las que tienen algo). */
    fun stageTotals(s: SleepSession): Map<String, Long> {
        val m = LinkedHashMap<String, Long>()
        for (t in listOf(SleepStageType.AWAKE, SleepStageType.REM, SleepStageType.LIGHT, SleepStageType.DEEP, SleepStageType.SLEEP)) {
            val v = s.stages.filter { it.t == t }.sumOf { clipped(it, s) }
            if (v > 0) m[t] = v
        }
        return m
    }

    /** Códigos de fase de Health Connect (SleepSessionRecord.STAGE_TYPE_*) → nuestras fases. */
    fun stageFromHealth(code: Int): String = when (code) {
        1, 3, 7 -> SleepStageType.AWAKE // despierto, fuera de la cama, despierto en la cama
        4 -> SleepStageType.LIGHT
        5 -> SleepStageType.DEEP
        6 -> SleepStageType.REM
        else -> SleepStageType.SLEEP // 2 = dormido sin detalle, 0 = desconocido
    }

    // ───────────────────────── Noches

    private fun overlapMs(a: SleepSession, b: SleepSession): Long = (minOf(a.end, b.end) - maxOf(a.start, b.start)).coerceAtLeast(0)

    /** Quita las sesiones que cuentan la misma noche desde otra fuente (se queda la de mayor prioridad). */
    fun dedupe(all: List<SleepSession>): List<SleepSession> {
        val ordered = all.filter { valid(it) }.sortedWith(compareByDescending<SleepSession> { SleepSrc.priority(it.source) }.thenByDescending { durationMs(it) })
        val kept = ArrayList<SleepSession>()
        for (s in ordered) {
            val dup = kept.any { k -> overlapMs(s, k) * 2 > minOf(durationMs(s), durationMs(k)) }
            if (!dup) kept += s
        }
        return kept.sortedBy { it.start }
    }

    /** Junta las sesiones de una misma noche que quedaron separadas por menos de una hora (por ejemplo, al levantarte al baño). */
    private fun cluster(sorted: List<SleepSession>): List<SleepSession> {
        val out = ArrayList<SleepSession>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && s.start - last.end <= MERGE_GAP_MS && last.source == s.source) {
                val gap = if (s.start > last.end) listOf(SleepStage(SleepStageType.AWAKE, last.end, s.start)) else emptyList()
                // El rato que estuviste levantado entre las dos cuenta como despierto, traigan fases o no.
                out[out.lastIndex] = last.copy(end = maxOf(last.end, s.end), stages = last.stages + gap + s.stages)
            } else out += s
        }
        return out
    }

    /** Las noches, de la más reciente a la más antigua. Cada una se identifica por el día en que te despertaste. */
    fun nights(all: List<SleepSession>, zone: ZoneId): List<SleepNight> {
        val clusters = cluster(dedupe(all))
        return clusters.groupBy { Instant.ofEpochMilli(it.end).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                val main = list.maxByOrNull { durationMs(it) }!!
                SleepNight(date, main, list.filter { it !== main }.sortedBy { it.start })
            }
            .sortedByDescending { it.date }
    }

    /** La noche en la que te despertaste hoy, o ayer si aún no hay de hoy. */
    fun latest(nights: List<SleepNight>, today: LocalDate): SleepNight? =
        nights.firstOrNull { it.date == today } ?: nights.firstOrNull { it.date == today.minusDays(1) }

    fun minuteOfDay(ts: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(ts).atZone(zone)
        return t.hour * 60 + t.minute
    }

    /** Promedio de horas del día que cruzan la medianoche (23:30 y 00:30 dan 00:00, no 12:00). */
    fun circularMean(minutes: List<Int>): Int? {
        if (minutes.isEmpty()) return null
        val k = 2 * Math.PI / 1440.0
        val x = minutes.sumOf { cos(it * k) }
        val y = minutes.sumOf { sin(it * k) }
        if (x == 0.0 && y == 0.0) return minutes.first()
        var a = atan2(y, x) / k
        if (a < 0) a += 1440.0
        return a.roundToInt() % 1440
    }

    private fun circularDiff(a: Int, b: Int): Int {
        var d = (a - b) % 1440
        if (d > 720) d -= 1440
        if (d < -720) d += 1440
        return d
    }

    fun summary(nights: List<SleepNight>, goalHours: Double, zone: ZoneId): SleepSummary {
        if (nights.isEmpty()) return SleepSummary(0, 0, null, null, 0, null, 0)
        val goalMs = (goalHours * 3600_000.0).toLong()
        val beds = nights.map { minuteOfDay(it.main.start, zone) }
        val wakes = nights.map { minuteOfDay(it.main.end, zone) }
        val bedMean = circularMean(beds)
        val spread = if (nights.size >= 3 && bedMean != null) {
            sqrt(beds.sumOf { val d = circularDiff(it, bedMean).toDouble(); d * d } / beds.size).roundToInt()
        } else null
        return SleepSummary(
            nights = nights.size,
            avgAsleepMs = nights.sumOf { asleepMs(it.main) } / nights.size,
            avgBedMin = bedMean,
            avgWakeMin = circularMean(wakes),
            metGoal = nights.count { asleepMs(it.main) >= goalMs },
            bedSpreadMin = spread,
            withStages = nights.count { hasStages(it.main) },
        )
    }

    // ───────────────────────── Formatos

    /** "7 h 32 min", "45 min". */
    fun fmt(ms: Long): String {
        val m = (ms / 60_000.0).roundToInt().coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0 -> "$r min"
            r == 0 -> "$h h"
            else -> "$h h $r min"
        }
    }

    /** "7:32". */
    fun fmtShort(ms: Long): String {
        val m = (ms / 60_000.0).roundToInt().coerceAtLeast(0)
        return "%d:%02d".format(m / 60, m % 60)
    }

    fun clock(minuteOfDay: Int): String = "%02d:%02d".format((minuteOfDay / 60) % 24, minuteOfDay % 60)

    fun goalMs(hours: Double): Long = (hours * 3600_000.0).toLong()

    /** «Me desperté el día [wakeDate] a las [wake] y me dormí a las [bed]» → (inicio, fin). Si te dormiste «más tarde» que la hora de despertar, fue la noche anterior. */
    fun span(wakeDate: LocalDate, bed: Pair<Int, Int>, wake: Pair<Int, Int>, zone: ZoneId): Pair<Long, Long> {
        val bedDate = if (bed.first * 60 + bed.second >= wake.first * 60 + wake.second) wakeDate.minusDays(1) else wakeDate
        fun ms(d: LocalDate, t: Pair<Int, Int>) = d.atTime(t.first, t.second).atZone(zone).toInstant().toEpochMilli()
        return ms(bedDate, bed) to ms(wakeDate, wake)
    }

    // ───────────────────────── Estimación con el uso del teléfono

    /**
     * Estima el sueño a partir de cuándo se apagó y se encendió la pantalla. Android no sabe si duermes: lo que sí sabe
     * es cuánto tiempo seguido no tocaste el teléfono. Por cada madrugada se toma el tramo sin pantalla más largo de la noche
     * (uniendo los encendidos de menos de 10 minutos, como una notificación) y se considera que dormiste ese tiempo.
     * Solo cuentan tramos de al menos [minGapMs] cuyo punto medio cae entre las 21:00 y las 11:00.
     * No distingue fases ni si estabas despierto en la cama: siempre es una estimación.
     */
    fun estimateFromScreen(events: List<ScreenEvent>, zone: ZoneId, minGapMs: Long = 3 * 3600_000L): List<SleepSession> {
        val ev = events.sortedBy { it.t }
        val offs = ArrayList<LongArray>()
        var offStart = -1L
        for (e in ev) {
            if (!e.on) {
                if (offStart < 0) offStart = e.t
            } else if (offStart >= 0) {
                offs += longArrayOf(offStart, e.t)
                offStart = -1L
            }
        }
        // Une tramos separados por un encendido corto (una notificación, mirar la hora).
        val merged = ArrayList<LongArray>()
        for (o in offs) {
            val last = merged.lastOrNull()
            if (last != null && o[0] - last[1] <= MICRO_WAKE_MS) last[1] = o[1] else merged += longArrayOf(o[0], o[1])
        }
        val best = HashMap<LocalDate, LongArray>()
        for (g in merged) {
            val len = g[1] - g[0]
            if (len < minGapMs || len > PHONE_MAX_MS) continue
            val mid = minuteOfDay((g[0] + g[1]) / 2, zone)
            val nocturnal = mid >= 21 * 60 || mid <= 11 * 60
            if (!nocturnal) continue
            val date = Instant.ofEpochMilli(g[1]).atZone(zone).toLocalDate()
            val cur = best[date]
            if (cur == null || len > cur[1] - cur[0]) best[date] = g
        }
        return best.map { (date, g) -> SleepSession(id = "phone_$date", start = g[0], end = g[1], source = SleepSrc.PHONE) }.sortedBy { it.start }
    }
}
