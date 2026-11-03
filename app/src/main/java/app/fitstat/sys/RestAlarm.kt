package app.fitstat.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Despierta al teléfono cuando termina el descanso entre series.
 *
 * Al empezar el descanso FITSTAT pausa la música, y con ella se va lo único que mantenía despierto al teléfono: con la
 * pantalla apagada se duerme y los `delay` de la app se congelan, así que el descanso "terminaba" mucho después de las
 * 0:00 y la música no volvía. Una alarma del sistema sí despierta al teléfono a la hora. La atiende [ReminderReceiver].
 */
object RestAlarm {
    const val ACTION_END = "app.fitstat.action.REST_END"
    private const val REQUEST = 0x5E57

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQUEST,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Programa (o mueve) la alarma para la hora [at] (ms, reloj de pared). Solo hay una a la vez. */
    fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        Reminders.setAlarm(ctx, am, at, pending(ctx))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx))
    }

    /**
     * Mantiene la alarma al día: [restEndsAt] da el momento en que termina el descanso (0 = no hay descanso).
     * Al empezar, alargar o acortar el descanso la mueve; al saltarlo, terminarlo o cancelar el entrenamiento la quita.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watch(ctx: Context, scope: CoroutineScope, restEndsAt: () -> Long) {
        scope.launch {
            snapshotFlow(restEndsAt).collectLatest { end ->
                runCatching { if (end > 0L) schedule(ctx, end) else cancel(ctx) }
            }
        }
    }
}
