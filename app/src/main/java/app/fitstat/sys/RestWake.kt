package app.fitstat.sys

import android.content.Context
import android.os.PowerManager

/**
 * Mantiene despierto al procesador del teléfono (no la pantalla) mientras dura el descanso y unos segundos después.
 *
 * Con la pantalla apagada y la música en pausa nada detiene al teléfono de dormirse, y dormido no corre ningún reloj
 * de la app: el descanso solo terminaba si la alarma del sistema llegaba a tiempo (y Android o el ahorro de batería de
 * la marca la pueden retrasar). Así el fin del descanso, la vibración y la música llegan a su hora. Los segundos de
 * después dan tiempo a que la música vuelva a sonar (y a partir de ahí la propia música mantiene despierto al teléfono).
 * Gasta muy poco: solo dura lo que el descanso.
 */
object RestWake {
    const val TAG = "fitstat:descanso"

    /** Lo más que se mantiene despierto de una vez, por si algo falla y nadie lo suelta. */
    private const val MAX_MS = 15 * 60_000L

    /** Margen después del fin del descanso para reanudar la música. */
    const val AFTER_MS = 15_000L

    internal var lock: PowerManager.WakeLock? = null

    /** Mantiene despierto [ms] milisegundos desde ahora (reemplaza lo anterior). */
    @Synchronized
    fun hold(ctx: Context, ms: Long) {
        try {
            val wl = lock ?: ctx.applicationContext.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
                ?.apply { setReferenceCounted(false) }
                ?.also { lock = it }
                ?: return
            wl.acquire(ms.coerceIn(1_000L, MAX_MS))
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun release() {
        try {
            lock?.takeIf { it.isHeld }?.release()
        } catch (_: Throwable) {
        }
    }
}
