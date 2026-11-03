package app.fitstat.data

import kotlinx.coroutines.delay

/**
 * Espera hasta que el reloj del teléfono llegue a [end] (en ms, la misma hora que da [now]).
 *
 * Un solo `delay(end - now())` solo cuenta el tiempo con el teléfono despierto: si la pantalla se apaga y el teléfono
 * se duerme, el `delay` se congela y termina mucho después de la hora, aunque el reloj ya la haya pasado. Por eso se
 * espera en tramos cortos y en cada uno se vuelve a mirar el reloj: al despertar, el primer tramo ya ve que la hora
 * pasó y la espera termina en menos de [stepMs].
 *
 * Termina cuando cualquiera de los dos relojes (el de los `delay` o el de [now]) dice que ya llegó la hora. Si
 * coinciden (lo normal, y en las pruebas con tiempo simulado), termina exactamente en [end].
 */
suspend fun awaitWallClock(end: Long, now: () -> Long, stepMs: Long = 500L) {
    val step = stepMs.coerceAtLeast(1L)
    var left = end - now()
    while (left > 0L) {
        val chunk = minOf(left, step)
        delay(chunk)
        left = minOf(left - chunk, end - now())
    }
}
