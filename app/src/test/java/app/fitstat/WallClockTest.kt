package app.fitstat

import app.fitstat.data.awaitWallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.2: el descanso termina a su hora aunque el teléfono se duerma. Con la pantalla apagada los `delay` se congelan
 * pero el reloj de pared sigue, y la espera debe ponerse al corriente en cuanto el teléfono despierta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WallClockTest {

    @Test fun endsExactlyAtTheEndWhenBothClocksAgree() = runTest {
        awaitWallClock(end = 30_000L, now = { currentTime })
        assertEquals(30_000L, currentTime)
    }

    @Test fun doesNotWaitWhenTheEndAlreadyPassed() = runTest {
        awaitWallClock(end = 0L, now = { currentTime })
        assertEquals(0L, currentTime)
    }

    @Test fun endsRightAfterWakingUpWhenThePhoneSlept() = runTest {
        var asleep = 0L   // lo que avanzó el reloj de pared mientras los delay estaban congelados
        val job = launch { awaitWallClock(end = 150_000L, now = { currentTime + asleep }) }
        advanceTimeBy(10_000); runCurrent()
        assertTrue("sigue esperando mientras no llegue la hora", job.isActive)
        asleep = 200_000L   // el teléfono durmió: el reloj ya pasó el final, pero los delay no se enteraron
        advanceTimeBy(1_000); runCurrent()
        assertTrue("termina enseguida al despertar, no 140 s después", job.isCompleted)
        assertEquals(11_000L, currentTime)
    }

    @Test fun aStuckClockDoesNotHangTheWait() = runTest {
        awaitWallClock(end = 5_000L, now = { 0L })
        assertEquals(5_000L, currentTime)
    }
}
