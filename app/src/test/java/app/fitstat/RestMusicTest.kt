package app.fitstat

import app.fitstat.conn.Connectors
import app.fitstat.conn.FadeStore
import app.fitstat.conn.MusicRescue
import app.fitstat.conn.MusicRestCoordinator
import app.fitstat.conn.MusicState
import app.fitstat.data.Changelog
import app.fitstat.data.Pages
import app.fitstat.data.Prefs
import app.fitstat.data.SType
import app.fitstat.data.Settings
import app.fitstat.sys.WorkoutKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.6: la música en el descanso con FITSTAT fuera de la vista. Android congela o cierra la app en segundo plano y se
 * pierden avisos del reproductor, así que antes de pausar/reanudar se vuelve a preguntar, se revisa que la orden sí se
 * haya hecho (y se repite si la app no hizo caso), y se guarda qué se pausó para que la alarma lo reanude si la app murió.
 * También: el ajuste «Volver a FITSTAT al terminar el descanso» y la notificación del entrenamiento.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestMusicTest {

    private fun prefs(vararg kv: Pair<String, String>) = Prefs(mapOf("conn.namida.enabled" to "true", "conn.namida.fade" to "0") + kv)

    private fun coordinator(scope: CoroutineScope, player: FakePlayer, p: Prefs = prefs(), store: FadeStore = FadeStore.Memory(), vol: FakeVolumeLogged = FakeVolumeLogged()) =
        MusicRestCoordinator(player, vol, { p }, scope, store)

    // ───────── Lo que se sabe de la música puede estar viejo

    @Test fun asksAgainWhatIsPlayingBeforePausing() = runTest {
        // FITSTAT estuvo congelada: cree que no suena nada, pero sí suena.
        val player = FakePlayer(playing = false)
        player.onSync = { player.userSets(true) }
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        assertTrue("preguntó de nuevo", player.syncs >= 1)
        assertEquals("y sí pausó", 1, player.pauses)
        assertTrue(c.pausedByUs)
    }

    @Test fun asksAgainBeforeResuming() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        // Durante el descanso tú le diste play desde la app de música, pero el aviso no llegó a FITSTAT.
        player.onSync = { player.userSets(true) }
        val before = player.plays
        c.onRestEnd()
        assertEquals("no manda play de más: ya sonaba", before, player.plays)
    }

    // ───────── La orden no siempre se cumple a la primera

    @Test fun aPauseTheAppIgnoredIsSentAgain() = runTest {
        val player = FakePlayer(playing = true).also { it.ignorePauses = 1 }
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        assertEquals(1, player.pauses)
        assertTrue("la app no hizo caso", player.state.playing)
        advanceTimeBy(MusicRestCoordinator.CHECK_RETRY_MS + 300); runCurrent()
        assertEquals("se repitió la pausa", 2, player.pauses)
        assertFalse(player.state.playing)
        advanceTimeBy(10_000); runCurrent()
        assertEquals("y ya no insiste", 2, player.pauses)
    }

    @Test fun aPlayTheAppIgnoredIsSentAgain() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        player.ignorePlays = 1
        c.onRestEnd()
        assertEquals(1, player.plays)
        assertFalse(player.state.playing)
        advanceTimeBy(MusicRestCoordinator.CHECK_RETRY_MS + 300); runCurrent()
        assertEquals("se repitió el play", 2, player.plays)
        assertTrue(player.state.playing)
    }

    @Test fun retriesGiveUpAfterTwoMoreTries() = runTest {
        val player = FakePlayer(playing = true).also { it.ignorePauses = 100 }
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        advanceTimeBy(20_000); runCurrent()
        assertEquals("1 + 2 reintentos, sin quedarse en un ciclo", 3, player.pauses)
    }

    @Test fun ifYouPressPlayRightAfterThePauseItIsRespected() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        assertFalse(player.state.playing)
        advanceTimeBy(MusicRestCoordinator.CHECK_STEP_MS + 50); runCurrent()
        player.userSets(true)   // la pausa sí se hizo; luego tú le diste play
        advanceTimeBy(10_000); runCurrent()
        assertEquals("FITSTAT no te la vuelve a pausar", 1, player.pauses)
        assertTrue(player.state.playing)
    }

    @Test fun ifYouPauseRightAfterTheResumeItIsRespected() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        c.onRestEnd()
        advanceTimeBy(MusicRestCoordinator.CHECK_STEP_MS + 50); runCurrent()
        player.userSets(false)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, player.plays)
    }

    @Test fun aNewRestStopsTheResumeCheck() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        player.ignorePlays = 100
        c.onRestEnd()
        // empieza otro descanso antes de que se revise el play: no hay que darle play
        c.onRestStart(120_000L)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, player.plays)
    }

    // ───────── No reanudar otra app

    @Test fun anotherAppIsNotResumedInsteadOfTheOneThatWasPaused() = runTest {
        val player = FakePlayer(playing = true, source = Connectors.NAMIDA)
        val c = coordinator(backgroundScope, player, prefs("conn.spotify.enabled" to "true"))
        c.onRestStart(60_000L)
        player.switchTo(Connectors.SPOTIFY, playing = false)   // Namida se cerró; Spotify estaba en pausa
        c.onRestEnd()
        assertEquals("no le da play a Spotify", 0, player.plays)
    }

    // ───────── Lo que se guarda para la alarma

    @Test fun whatWasPausedIsSavedUntilTheRestEnds() = runTest {
        val store = FadeStore.Memory()
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player, store = store)
        c.onRestStart(90_000L)
        assertEquals(Connectors.NAMIDA, store.pausedSource)
        assertEquals(90_000L, store.pausedUntil)
        c.onRestStart(105_000L)   // «+15 s»
        assertEquals("se mueve la hora", 105_000L, store.pausedUntil)
        assertEquals(1, player.pauses)
        c.onRestEnd()
        assertEquals("", store.pausedSource)
        assertEquals(0L, store.pausedUntil)
    }

    @Test fun nothingIsSavedIfNothingWasPaused() = runTest {
        val store = FadeStore.Memory().also { it.pausedSource = Connectors.NAMIDA; it.pausedUntil = 5L }
        val c = coordinator(backgroundScope, FakePlayer(playing = false), store = store)
        c.onRestStart(90_000L)
        assertEquals("lo viejo se borra y no se guarda nada nuevo", "", store.pausedSource)
        assertEquals(0L, store.pausedUntil)
    }

    @Test fun resetForgetsWhatWasSaved() = runTest {
        val store = FadeStore.Memory()
        val c = coordinator(backgroundScope, FakePlayer(playing = true), store = store)
        c.onRestStart(90_000L)
        c.reset()
        assertEquals("", store.pausedSource)
    }

    @Test fun theAlarmOnlyRescuesARestThatJustEnded() {
        val end = 1_000_000L
        assertTrue(MusicRescue.shouldResume("namida", end, end, enabled = true, resume = true))
        assertTrue("un poco tarde, sí", MusicRescue.shouldResume("namida", end, end + 60_000L, enabled = true, resume = true))
        assertFalse("antes de tiempo, no", MusicRescue.shouldResume("namida", end, end - 30_000L, enabled = true, resume = true))
        assertFalse("mucho después, no", MusicRescue.shouldResume("namida", end, end + 30 * 60_000L, enabled = true, resume = true))
        assertFalse("conector apagado", MusicRescue.shouldResume("namida", end, end, enabled = false, resume = true))
        assertFalse("reanudar apagado", MusicRescue.shouldResume("namida", end, end, enabled = true, resume = false))
        assertFalse("nada pausado", MusicRescue.shouldResume("", end, end, enabled = true, resume = true))
    }

    @Test fun anAppWithoutTheNewCallsStillWorks() = runTest {
        // Un reproductor que no sabe volver a preguntar (sync por defecto) funciona como antes.
        val plain = object : app.fitstat.conn.MusicPlayer {
            var st = MusicState(connected = true, playing = true, title = "x")
            override val state get() = st
            override fun play() { st = MusicState(true, true, "x") }
            override fun pause() { st = MusicState(true, false, "x") }
            override fun next() {}
            override fun previous() {}
        }
        val c = MusicRestCoordinator(plain, FakeVolumeLogged(), { prefs() }, backgroundScope)
        c.onRestStart()
        assertFalse(plain.state.playing)
        c.onRestEnd()
        assertTrue(plain.state.playing)
    }

    // ───────── Ajuste y notificación

    @Test fun returnToFitstatIsASettingOnByDefault() {
        val s = Settings.byKey["train.rest_return"]!!
        assertEquals(Pages.TRAIN, s.page)
        assertEquals(SType.Toggle, s.type)
        assertEquals("true", s.default)
        assertTrue(s.title.contains("Volver a FITSTAT"))
    }

    @Test fun workoutNotificationTexts() {
        val time = { _: Long -> "10:42" }
        val resting = WorkoutKeeper.texts(WorkoutKeeper.Info("Pierna", 123L, false, "Sentadilla", 3), time)
        assertEquals("Descansando · Pierna", resting.first)
        assertEquals("Termina a las 10:42 · sigue Sentadilla", resting.second)
        val over = WorkoutKeeper.texts(WorkoutKeeper.Info("Pierna", 0L, true, "Sentadilla", 3), time)
        assertEquals("¡A la siguiente serie!", over.first)
        val working = WorkoutKeeper.texts(WorkoutKeeper.Info("Entrenamiento libre", 0L, false, null, 1), time)
        assertEquals("Entrenamiento libre" to "1 serie", working)
        val empty = WorkoutKeeper.texts(WorkoutKeeper.Info("Pierna", 0L, false, null, 0), time)
        assertEquals("Entrenamiento en curso", empty.second)
    }

    @Test fun changelogAnnouncesTheReturnToFitstat() {
        val e = Changelog.entries.first { it.version == "1.9.6" }
        val text = e.sections.flatMap { it.second }.joinToString("\n")
        assertTrue(text.contains("Volver a FITSTAT al terminar el descanso"))
        assertTrue(text.contains(Settings.byKey["train.rest_return"]!!.title))
        assertTrue(text.contains("Mostrar sobre otras apps"))
    }

    @Test fun changelogAnnouncesThisVersion() {
        val e = Changelog.entries.first()
        assertEquals(BuildConfig.VERSION_NAME, e.version)
        assertEquals("1.9.7", e.version)
        val text = e.sections.flatMap { it.second }.joinToString("\n")
        assertTrue(text.contains("pantalla apagada"))
        assertTrue(text.contains("optimización de batería"))
    }

    // ───────── v1.9.7: la alarma del fin reintenta el play

    @Test fun theAlarmRetriesThePlayRightAway() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player)
        c.onRestStart(60_000L)
        player.ignorePlays = 1   // FITSTAT estaba en segundo plano: la app de música no hizo caso
        c.onRestEnd()
        assertEquals(1, player.plays)
        assertFalse(player.state.playing)
        c.retryNow()   // suena la alarma del fin
        assertEquals("se reintenta sin esperar", 2, player.plays)
        assertTrue(player.state.playing)
        advanceTimeBy(10_000); runCurrent()
        assertEquals("y ya no insiste", 2, player.plays)
    }

    @Test fun theAlarmDoesNotPlayIfNothingIsPending() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player)
        c.retryNow()
        assertEquals("no había nada que reanudar", 0, player.plays)
        c.onRestStart(60_000L)
        c.retryNow()
        assertEquals("en pleno descanso no se le da play", 0, player.plays)
        c.onRestEnd()
        advanceTimeBy(1_000); runCurrent()   // ya sonó: se dejó de revisar
        player.userSets(false)               // tú la pausaste después
        c.retryNow()
        assertEquals("se respeta tu pausa", 1, player.plays)
    }

    @Test fun theAlarmDoesNotPlayAnotherApp() = runTest {
        val player = FakePlayer(playing = true, source = Connectors.NAMIDA)
        val c = coordinator(backgroundScope, player, prefs("conn.spotify.enabled" to "true"))
        c.onRestStart(60_000L)
        player.ignorePlays = 1
        c.onRestEnd()
        player.switchTo(Connectors.SPOTIFY, playing = false)
        c.retryNow()
        assertEquals(1, player.plays)
    }
}
