package app.fitstat.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.fitstat.FitstatApp

/** Ejecuta [work] fuera del hilo principal sin que el sistema mate el receptor a medias. */
private fun BroadcastReceiver.background(work: () -> Unit) {
    val pending = goAsync()
    Thread {
        try {
            work()
        } catch (_: Throwable) {
        } finally {
            pending.finish()
        }
    }.start()
}

/** Suena la alarma de un recordatorio, se pospone, o toca refrescar los widgets a medianoche. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as? FitstatApp ?: return
        val repo = app.repo
        when (intent.action) {
            Reminders.ACTION_FIRE -> {
                val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
                background {
                    Reminders.fire(ctx, repo, id)
                    Reminders.scheduleAll(ctx, repo)
                }
            }
            Notifier.ACTION_SNOOZE -> {
                val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
                NotificationManagerCompat.from(ctx).cancel(id.hashCode())
                Reminders.snooze(ctx, id, repo.prefs.int("reminders.snooze").coerceIn(5, 180), repo.now())
            }
            Widgets.ACTION_REFRESH -> background {
                Widgets.refreshAll(ctx)
                Widgets.scheduleMidnight(ctx)
            }
        }
    }
}

/** Después de reiniciar el teléfono, actualizar la app o cambiar la hora: vuelve a programar todo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as? FitstatApp ?: return
        background {
            Notifier.ensureChannel(ctx)
            Reminders.scheduleAll(ctx, app.repo)
            AppIcon.apply(ctx, app.repo.prefs)
            Widgets.scheduleMidnight(ctx)
            Widgets.refreshAll(ctx)
        }
    }
}
