package app.fitstat.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.fitstat.data.Reminder
import app.fitstat.data.Repo
import java.time.Instant
import java.time.ZoneId

/** Programa los recordatorios con el despertador del sistema (AlarmManager). */
object Reminders {
    const val EXTRA_ID = "rid"
    const val EXTRA_ONCE = "once"
    const val ACTION_FIRE = "app.fitstat.action.REMINDER"
    private const val PREFS = "fitstat_alarms"
    private const val KEY_IDS = "ids"

    /**
     * Próximo momento (ms) en que debe sonar [r] después de [now], o null si no tiene días.
     * Función pura: se puede probar sin Android.
     */
    fun nextTrigger(r: Reminder, now: Long, zone: ZoneId): Long? {
        if (r.days.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (add in 0..7) {
            val d = today.plusDays(add.toLong())
            if (d.dayOfWeek.value !in r.days) continue
            val at = d.atTime(r.hour.coerceIn(0, 23), r.minute.coerceIn(0, 59)).atZone(zone).toInstant().toEpochMilli()
            if (at > now) return at
        }
        return null
    }

    private fun pending(ctx: Context, key: String, action: String, id: String, once: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, key.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id).putExtra(EXTRA_ONCE, once),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** ¿Puede la app poner alarmas exactas? (en Android 12+ depende de un permiso especial) */
    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun setAlarm(ctx: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Cancela lo anterior y vuelve a programar todos los recordatorios activos. */
    @Synchronized
    fun scheduleAll(ctx: Context, repo: Repo) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (id in sp.getStringSet(KEY_IDS, emptySet()).orEmpty()) am.cancel(pending(ctx, "r:$id", ACTION_FIRE, id))
        val ids = HashSet<String>()
        if (repo.prefs.bool("reminders.enabled")) {
            val now = repo.now()
            for (r in repo.reminders) {
                if (!r.enabled) continue
                val at = nextTrigger(r, now, repo.zone) ?: continue
                setAlarm(ctx, am, at, pending(ctx, "r:${r.id}", ACTION_FIRE, r.id))
                ids += r.id
            }
        }
        sp.edit().putStringSet(KEY_IDS, ids).apply()
    }

    /** Pospone un recordatorio [minutes] minutos (alarma única). */
    fun snooze(ctx: Context, id: String, minutes: Int, now: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        setAlarm(ctx, am, now + minutes * 60_000L, pending(ctx, "s:$id", ACTION_FIRE, id, once = true))
    }

    /** Lo que ocurre cuando suena la alarma de [id]. */
    fun fire(ctx: Context, repo: Repo, id: String): Boolean {
        val r = repo.reminders.firstOrNull { it.id == id } ?: return false
        if (!r.enabled || !repo.prefs.bool("reminders.enabled")) return false
        if (repo.prefs.bool("reminders.skip_done")) {
            val today = repo.today()
            if (repo.logs.any { app.fitstat.data.Fmt.date(it.ts, repo.zone) == today }) return false
        }
        return Notifier.show(ctx, r, repo)
    }
}
