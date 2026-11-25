package app.fitstat.sys

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fitstat.MainActivity
import app.fitstat.R

/**
 * «Volver a FITSTAT al terminar el descanso» (Ajustes › Entrenamiento, encendido de fábrica).
 *
 * Si el descanso llega a cero mientras estás en otra app, el teléfono vibra suave y FITSTAT se abre sola en el
 * entrenamiento. Desde Android 10, una app que no se ve solo puede abrirse sola si tiene el permiso «Mostrar sobre otras
 * apps»; sin él (o si la marca del teléfono lo bloquea igual) queda una notificación para volver con un toque.
 */
object RestReturn {
    const val EXTRA_WORKOUT = "open_workout"
    const val ACTION_WORKOUT = "app.fitstat.action.WORKOUT"
    const val CHANNEL = "fin_descanso"
    private const val NOTE_ID = 0x5E56
    private const val UI_PREFS = "fitstat_ui"
    private const val KEY_ASKED = "rest_return_asked"
    private const val KEY_BATTERY_ASKED = "battery_asked"

    /** Intención que abre FITSTAT directo en el entrenamiento en curso. */
    fun openIntent(ctx: Context): Intent =
        Intent(ctx, MainActivity::class.java)
            .setAction(ACTION_WORKOUT)
            .putExtra(EXTRA_WORKOUT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** ¿Android deja que FITSTAT se abra sola desde segundo plano? */
    fun mayLaunch(ctx: Context): Boolean = Build.VERSION.SDK_INT < 29 || runCatching { Settings.canDrawOverlays(ctx) }.getOrDefault(false)

    /** Abre la pantalla del sistema donde se da «Mostrar sobre otras apps». */
    fun openPermission(ctx: Context): Boolean = runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /** Hay que explicar el permiso (una sola vez): la función está encendida, falta el permiso y no se ha preguntado. */
    fun shouldAsk(ctx: Context, enabled: Boolean): Boolean =
        enabled && !mayLaunch(ctx) && !ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(ctx: Context) {
        ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** ¿La pantalla está encendida? (Con la pantalla apagada el aviso es la vibración normal, no la suave.) */
    fun screenOn(ctx: Context): Boolean =
        runCatching { ctx.getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull() ?: true

    // ───────── Optimización de batería

    /**
     * ¿FITSTAT está fuera de la optimización de batería? Así Android no la duerme con la pantalla apagada (respeta su
     * «mantener despierto» y sus alarmas) y puede despertar a la app de música desde segundo plano para reanudarla.
     */
    fun batteryFree(ctx: Context): Boolean =
        runCatching { ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) }.getOrNull() ?: false

    /** Abre el aviso del sistema para quitar la optimización de batería (o la lista, si ese aviso no existe). */
    fun openBattery(ctx: Context): Boolean = runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.recoverCatching {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Hay que explicar lo de la batería (una sola vez). */
    fun shouldAskBattery(ctx: Context): Boolean =
        !batteryFree(ctx) && !ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BATTERY_ASKED, false)

    fun markBatteryAsked(ctx: Context) {
        ctx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BATTERY_ASKED, true).apply()
    }

    /**
     * El descanso terminó y FITSTAT no se ve: de regreso a la app (con [buzz], además, la vibración suave). [visible]
     * dice si FITSTAT ya está a la vista (para no dejar la notificación de respaldo si sí se abrió).
     */
    fun bringBack(ctx: Context, buzz: Boolean = true, visible: () -> Boolean) {
        if (buzz) softBuzz(ctx)
        val app = ctx.applicationContext
        val allowed = mayLaunch(app)
        runCatching { app.startActivity(openIntent(app)) }
        Handler(Looper.getMainLooper()).postDelayed({
            if (visible()) return@postDelayed
            // Con la pantalla apagada y el permiso dado, FITSTAT ya quedó abierta detrás del bloqueo: se ve al desbloquear.
            val screenOn = runCatching { app.getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull() ?: true
            if (!allowed || screenOn) notifyFallback(app, allowed)
        }, 2_500L)
    }

    /** Quita el aviso de respaldo (al volver a FITSTAT o al empezar otro descanso). */
    fun clear(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTE_ID) }
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Fin del descanso", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Si FITSTAT no pudo abrirse sola al terminar el descanso, te deja este aviso para volver con un toque."
            // La vibración suave ya la hace FITSTAT: el aviso no suena ni vibra otra vez.
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun notifyFallback(ctx: Context, allowed: Boolean) {
        if (!Notifier.canPost(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(ctx, NOTE_ID, openIntent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = if (allowed) "Toca para volver a tu entrenamiento." else "Toca para volver. Para que FITSTAT se abra sola, permite «Mostrar sobre otras apps»."
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fitstat)
            .setContentTitle("Terminó tu descanso")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTE_ID, n) }
    }

    // ───────── Vibración

    /** Atributos de alarma: Android no deja vibrar a una app en segundo plano con otros (los ignora sin avisar). */
    @Suppress("DEPRECATION")
    internal val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    internal fun vibrator(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    /** Dos toquecitos suaves (mucho más leves que el aviso normal del descanso). */
    fun softBuzz(ctx: Context) {
        try {
            val v = vibrator(ctx) ?: return
            if (!v.hasVibrator()) return
            val effect = when {
                v.hasAmplitudeControl() -> VibrationEffect.createWaveform(longArrayOf(0, 35, 120, 35), intArrayOf(0, 70, 0, 70), -1)
                Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                else -> VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            @Suppress("DEPRECATION")
            v.vibrate(effect, alarmAttrs)
        } catch (_: Throwable) {
        }
    }
}
