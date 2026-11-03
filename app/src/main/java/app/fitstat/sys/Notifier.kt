package app.fitstat.sys

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fitstat.MainActivity
import app.fitstat.R
import app.fitstat.data.Fmt
import app.fitstat.data.Reminder
import app.fitstat.data.Repo
import app.fitstat.ui.theme.resolvePalette
import androidx.compose.ui.graphics.toArgb

/** Notificaciones de recordatorio: canal, texto y botones (Abrir y Posponer). */
object Notifier {
    const val CHANNEL = "recordatorios"
    const val ACTION_SNOOZE = "app.fitstat.action.SNOOZE"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Recordatorios de entrenamiento", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos para que no se te pase tu entrenamiento."
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    /** ¿Puede la app mostrar notificaciones ahora mismo? */
    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Intención para abrir la app en una pantalla (la misma que usan widgets y accesos directos). */
    fun openIntent(ctx: Context, route: String): Intent =
        Intent(ctx, MainActivity::class.java)
            .setAction("app.fitstat.action.OPEN")
            .setData(android.net.Uri.parse("fitstat://open/" + android.net.Uri.encode(route)))
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openPending(ctx: Context, route: String, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code, openIntent(ctx, route), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private val phrases = listOf(
        "Es hora de entrenar. ¡Tú puedes!",
        "Tu yo de mañana te lo agradecerá.",
        "Un rato de fierro y listo.",
        "Hoy suma. Aunque sea una serie.",
        "Constancia gana a motivación.",
        "FITBOT ya calentó. ¿Y tú?",
    )

    /** Texto por defecto cuando el recordatorio no trae mensaje propio. */
    fun defaultBody(repo: Repo, routineId: String?): String {
        val routine = routineId?.let { repo.routine(it) }
        if (routine != null) {
            val n = routine.items.size
            return "${routine.name}" + if (n > 0) " · " + Fmt.plural(n, "ejercicio", "ejercicios") else ""
        }
        return phrases[repo.today().dayOfYear % phrases.size]
    }

    /** Muestra la notificación de un recordatorio. Devuelve false si no se pudo (sin permiso). */
    fun show(ctx: Context, r: Reminder, repo: Repo): Boolean {
        ensureChannel(ctx)
        if (!canPost(ctx)) return false
        val routine = r.routineId?.let { repo.routine(it) }
        val body = r.body.ifBlank { defaultBody(repo, r.routineId) }
        val route = if (routine != null) "rutina:${routine.id}" else "inicio"
        val code = r.id.hashCode()
        val open = openPending(ctx, route, code)
        val snoozeMin = repo.prefs.int("reminders.snooze").coerceIn(5, 180)
        val snooze = PendingIntent.getBroadcast(
            ctx, code xor 0x5107,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra(Reminders.EXTRA_ID, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val accent = runCatching { resolvePalette(repo.prefs, ctx).accent.toArgb() }.getOrDefault(0xFF9B8CF5.toInt())
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fitstat)
            .setContentTitle(r.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(accent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, if (routine != null) "Empezar" else "Abrir", open)
            .addAction(0, "Posponer $snoozeMin min", snooze)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(code, n)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
