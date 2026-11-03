package app.fitstat

import app.fitstat.data.Reminder
import app.fitstat.sys.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RemindersTest {
    private val mx = ZoneId.of("America/Mexico_City")
    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun r(hour: Int, minute: Int, vararg days: Int) = Reminder("r", "Entrena", hour = hour, minute = minute, days = days.toSet())

    @Test fun picksLaterTodayWhenTheTimeHasNotPassed() {
        // 28/oct/2026 es miércoles (3).
        val now = at(mx, 2026, 10, 28, 17, 0)
        assertEquals(at(mx, 2026, 10, 28, 18, 0), Reminders.nextTrigger(r(18, 0, 1, 3, 5), now, mx))
    }

    @Test fun skipsToTheNextConfiguredDay() {
        val now = at(mx, 2026, 10, 28, 19, 0)
        assertEquals(at(mx, 2026, 10, 30, 18, 0), Reminders.nextTrigger(r(18, 0, 1, 3, 5), now, mx))
    }

    @Test fun exactlyNowMovesToNextWeek() {
        val now = at(mx, 2026, 10, 28, 18, 0)
        assertEquals(at(mx, 2026, 11, 4, 18, 0), Reminders.nextTrigger(r(18, 0, 3), now, mx))
    }

    @Test fun oneMinuteBeforeStillFiresToday() {
        val now = at(mx, 2026, 10, 28, 17, 59)
        assertEquals(at(mx, 2026, 10, 28, 18, 0), Reminders.nextTrigger(r(18, 0, 3), now, mx))
    }

    @Test fun noDaysMeansNoAlarm() {
        assertNull(Reminders.nextTrigger(r(18, 0), at(mx, 2026, 10, 28, 12, 0), mx))
    }

    @Test fun wrapsAroundTheWeekend() {
        // Sábado 31/oct; el recordatorio es solo lunes → lunes 2/nov.
        val now = at(mx, 2026, 10, 31, 10, 0)
        assertEquals(at(mx, 2026, 11, 2, 7, 30), Reminders.nextTrigger(r(7, 30, 1), now, mx))
    }

    @Test fun keepsLocalTimeAcrossDaylightSavingChanges() {
        val ny = ZoneId.of("America/New_York")
        // El horario de verano termina el domingo 1/nov/2026: las 18:00 locales siguen siendo las 18:00.
        val now = at(ny, 2026, 10, 31, 20, 0)
        val next = Reminders.nextTrigger(r(18, 0, 7), now, ny)
        assertEquals(at(ny, 2026, 11, 1, 18, 0), next)
    }

    @Test fun clampsInvalidTimes() {
        val now = at(mx, 2026, 10, 28, 0, 0)
        assertEquals(at(mx, 2026, 10, 28, 23, 59), Reminders.nextTrigger(r(99, 99, 3), now, mx))
    }
}
