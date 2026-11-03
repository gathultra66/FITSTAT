package app.fitstat.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatos de texto: números, pesos y fechas en español de México. */
object Fmt {
    private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val monthsLong = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val days = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    const val KG_TO_LB = 2.2046226218

    /** Número sin ceros inútiles: 82.5, 80, 0.25. */
    fun num(d: Double, dec: Int = 1): String {
        if (d.isNaN() || d.isInfinite()) return "0"
        val p = Math.pow(10.0, dec.toDouble())
        val r = Math.round(d * p) / p
        return if (r == Math.floor(r) && abs(r) < 1e12) r.toLong().toString() else "%.${dec}f".format(java.util.Locale.US, r).trimEnd('0').trimEnd('.')
    }

    fun group(n: Long): String {
        val s = abs(n).toString()
        val sb = StringBuilder()
        for ((i, ch) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        return (if (n < 0) "-" else "") + sb
    }

    fun toDisplay(kg: Double, units: String): Double = if (units == "lb") kg * KG_TO_LB else kg
    fun fromDisplay(v: Double, units: String): Double = if (units == "lb") v / KG_TO_LB else v
    fun unit(units: String): String = if (units == "lb") "lb" else "kg"

    fun weightNum(kg: Double, units: String): String {
        val v = toDisplay(kg, units)
        return num(v, if (units == "lb") 0.coerceAtLeast(if (v < 20) 1 else 0) else 1)
    }

    fun weight(kg: Double, units: String): String = weightNum(kg, units) + " " + unit(units)

    /** Volumen total: 850 kg, 12,340 kg, 1.2 M kg. */
    fun volume(kg: Double, units: String): String {
        val v = toDisplay(kg, units)
        val u = unit(units)
        return when {
            v >= 1_000_000 -> num(v / 1_000_000, 2) + " M $u"
            v >= 100_000 -> group(Math.round(v / 1000)) + "k $u"
            else -> group(Math.round(v)) + " $u"
        }
    }

    fun compactVolume(kg: Double, units: String): String {
        val v = toDisplay(kg, units)
        return when {
            v >= 1_000_000 -> num(v / 1_000_000, 1) + "M"
            v >= 10_000 -> num(v / 1000, 1) + "k"
            else -> group(Math.round(v))
        }
    }

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun date(ts: Long, zone: ZoneId = zone()): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    fun hourMinute(ts: Long, zone: ZoneId = zone()): String {
        val t = Instant.ofEpochMilli(ts).atZone(zone)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    fun dateShort(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"
    fun dateMid(d: LocalDate): String = "${days[d.dayOfWeek.value - 1].take(3).replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} ${months[d.monthValue - 1]}"
    fun dateLong(d: LocalDate): String =
        "${days[d.dayOfWeek.value - 1].replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} de ${monthsLong[d.monthValue - 1]}"
    fun monthName(m: Int): String = monthsLong[(m - 1).coerceIn(0, 11)]
    fun monthShort(m: Int): String = months[(m - 1).coerceIn(0, 11)]
    fun dayName(dow: DayOfWeek): String = days[dow.value - 1]

    fun relativeDay(d: LocalDate, today: LocalDate): String {
        val diff = java.time.temporal.ChronoUnit.DAYS.between(d, today).toInt()
        return when {
            diff == 0 -> "Hoy"
            diff == 1 -> "Ayer"
            diff == -1 -> "Mañana"
            diff in 2..6 -> "Hace $diff días"
            diff in 7..13 -> "Hace 1 semana"
            diff in 14..27 -> "Hace ${diff / 7} semanas"
            else -> dateShort(d)
        }
    }

    fun duration(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
    }

    fun time(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    fun percent(v: Float): String {
        val p = (v * 100f).roundToInt()
        // Nunca muestra 100% si todavía falta algo.
        return "${if (v < 1f && p >= 100) 99 else p}%"
    }

    fun plural(n: Int, one: String, many: String): String = if (n == 1) "$n $one" else "$n $many"
}
