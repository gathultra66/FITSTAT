package app.fitstat.sys

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.fitstat.FitstatApp
import app.fitstat.R
import app.fitstat.data.Prefs
import app.fitstat.ui.theme.resolvePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Mantiene viva a FITSTAT mientras hay un entrenamiento en curso (con una notificación fija que muestra el descanso).
 *
 * Sin esto, al salir a otra app Android deja a FITSTAT en segundo plano y, en cuanto puede, la congela o la cierra
 * (sobre todo en Android 14+ y en marcas que ahorran batería de más). Congelada no ve el fin del descanso, no le da
 * play a la música a su hora (o la deja a medio subir el volumen), Android ignora sus vibraciones y se pierde el
 * entrenamiento si la cierra. Con un servicio en primer plano la app sigue despierta hasta que terminas.
 */
class WorkoutService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val info = WorkoutKeeper.current
        try {
            // Siempre se pasa a primer plano primero: un servicio arrancado así que se detiene antes de hacerlo hace que
            // Android cierre la app. Si el entrenamiento ya terminó (se canceló en ese instante), se detiene enseguida.
            WorkoutKeeper.ensureChannel(this)
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, WorkoutKeeper.NOTE_ID, WorkoutKeeper.build(this, info ?: WorkoutKeeper.Info("FITSTAT", 0L, false, null, 0)), type)
            WorkoutKeeper.foreground = true
        } catch (_: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (info == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        WorkoutKeeper.running = false
        WorkoutKeeper.foreground = false
        super.onDestroy()
    }
}

/** Arranca, actualiza y detiene [WorkoutService] según el entrenamiento en curso. */
object WorkoutKeeper {
    const val CHANNEL = "entrenamiento"
    const val NOTE_ID = 0x5E55

    /** Lo que muestra la notificación. */
    data class Info(
        val name: String,
        /** Hora en que termina el descanso (0 = no hay descanso). */
        val restEndsAt: Long,
        /** El descanso ya llegó a cero y falta la siguiente serie. */
        val restOver: Boolean,
        val exercise: String?,
        val sets: Int,
    )

    @Volatile var current: Info? = null
        private set

    /** Se pidió arrancar el servicio. */
    @Volatile internal var running = false

    /** El servicio ya está en primer plano (antes de eso no se puede detener desde fuera sin que Android cierre la app). */
    @Volatile internal var foreground = false

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Entrenamiento en curso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Muestra el descanso mientras entrenas y mantiene a FITSTAT despierta para avisarte y volver a poner tu música."
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    /** Texto de la notificación (lógica pura, para las pruebas). */
    fun texts(i: Info, timeOf: (Long) -> String): Pair<String, String> = when {
        i.restEndsAt > 0L -> "Descansando · ${i.name}" to "Termina a las ${timeOf(i.restEndsAt)}" + (i.exercise?.let { " · sigue $it" } ?: "")
        i.restOver -> "¡A la siguiente serie!" to (i.exercise ?: i.name)
        else -> i.name to listOfNotNull(i.exercise, if (i.sets > 0) "${i.sets} ${if (i.sets == 1) "serie" else "series"}" else null)
            .joinToString(" · ").ifEmpty { "Entrenamiento en curso" }
    }

    fun build(ctx: Context, i: Info): Notification {
        val (title, body) = texts(i) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
        val prefs: Prefs? = (ctx.applicationContext as? FitstatApp)?.repo?.prefs
        val accent = runCatching { resolvePalette(prefs!!, ctx).accent.toArgb() }.getOrDefault(0xFF9B8CF5.toInt())
        val open = PendingIntent.getActivity(ctx, NOTE_ID, RestReturn.openIntent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fitstat)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(accent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (i.restEndsAt > 0L) {
            // Cuenta regresiva del descanso, la lleva el sistema (no hay que actualizar la notificación cada segundo).
            b.setWhen(i.restEndsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }
        return b.build()
    }

    private fun intent(ctx: Context) = Intent(ctx, WorkoutService::class.java)

    private fun update(ctx: Context, i: Info?) {
        current = i
        if (i == null) {
            // Si todavía no llega a primer plano, se detiene solo al arrancar (ver WorkoutService.onStartCommand).
            if (running && foreground) {
                runCatching { ctx.stopService(intent(ctx)) }
                running = false
                foreground = false
            }
            return
        }
        if (!running) {
            // Solo se puede arrancar con la app a la vista (Android 12+): el entrenamiento empieza siempre dentro de FITSTAT.
            running = runCatching { ContextCompat.startForegroundService(ctx, intent(ctx)) }.isSuccess
            return
        }
        try {
            NotificationManagerCompat.from(ctx).notify(NOTE_ID, build(ctx, i))
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones el servicio sigue funcionando, solo que no se ve.
        } catch (_: Throwable) {
        }
    }

    /** Sigue al entrenamiento en curso: [info] da lo que se muestra (null = no hay entrenamiento). */
    fun watch(ctx: Context, scope: CoroutineScope, info: () -> Info?) {
        val app = ctx.applicationContext
        scope.launch {
            snapshotFlow(info).distinctUntilChanged().collect { i -> update(app, i) }
        }
    }
}
