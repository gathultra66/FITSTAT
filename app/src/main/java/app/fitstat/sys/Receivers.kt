package app.fitstat.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import app.fitstat.FitstatApp
import app.fitstat.ui.app.finishRestIfDue

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

/** Suena la alarma de un recordatorio, se pospone, toca refrescar los widgets a medianoche, o termina el descanso. */
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
            RestAlarm.ACTION_END -> {
                // Si la app no está viva no hay descanso que terminar: no se crea todo su estado solo para mirar.
                val state = app.stateOrNull ?: return
                val pending = goAsync()
                if (state.finishRestIfDue(ctx)) state.connectors.rest.onRestEnd()
                // Se mantiene el receptor un momento para que el teléfono no vuelva a dormirse antes de que arranque la música.
                Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, 3_000L)
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
