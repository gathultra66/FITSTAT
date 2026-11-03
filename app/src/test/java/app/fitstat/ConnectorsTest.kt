package app.fitstat

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.fitstat.bot.BotHost
import app.fitstat.bot.Prompt
import app.fitstat.bot.ModelInfo
import app.fitstat.bot.ToolCtx
import app.fitstat.bot.ToolOutcome
import app.fitstat.bot.allTools
import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnPlatform
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Connectors
import app.fitstat.conn.FadeStore
import app.fitstat.conn.HealthAvail
import app.fitstat.conn.MusicPlayer
import app.fitstat.conn.MusicRestCoordinator
import app.fitstat.conn.MusicState
import app.fitstat.conn.Namida
import app.fitstat.conn.VolumeControl
import app.fitstat.data.*
import app.fitstat.ui.app.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

// ───────────────────────── Dobles de prueba

private val ZONE = ZoneId.of("America/Mexico_City")
private val TODAY = LocalDate.of(2026, 10, 28)
private val NOW = TODAY.atTime(19, 0).atZone(ZONE).toInstant().toEpochMilli()
private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(ZONE).toInstant().toEpochMilli()

private fun newRepo() = Repo(Files.createTempDirectory("fitstat-conn").toFile(), CoroutineScope(SupervisorJob() + Dispatchers.Default), { NOW }) { ZONE }

private fun prefs(vararg kv: Pair<String, String>) = Prefs(mapOf("conn.namida.enabled" to "true") + kv)

private class ToolHost(override val repo: Repo, val hub: ConnectorHub?) : BotHost {
    val navigated = ArrayList<Route>()
    override fun currentScreen() = "home"
    override fun navigate(route: Route) { navigated += route }
    override fun startWorkout(routineId: String?, exerciseIds: List<String>) {}
    override fun workoutActive() = false
    override fun connectors() = hub
}

private fun call(host: BotHost, tool: String, json: String = "{}"): ToolOutcome =
    allTools.first { it.name == tool }.run(Json.parseToJsonElement(json) as JsonObject, ToolCtx(host))

private val ToolOutcome.err: String get() = json["error"]?.jsonPrimitive?.content ?: ""

/**
 * v1.7: conectores. La música durante el descanso (con tiempo simulado), el estado de cada conector y sus permisos,
 * la sincronización del sueño y las herramientas de FITBOT para manejarlo todo desde el chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorsTest {

    // ───────── Música durante el descanso

    private fun coordinator(
        scope: CoroutineScope, player: MusicPlayer, vol: VolumeControl, p: Prefs = prefs(), store: FadeStore = FadeStore.Memory(),
    ) = MusicRestCoordinator(player, vol, { p }, scope, store)

    @Test fun pausesWhenRestStartsAndResumesWhenItEnds() = runTest {
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged()
        val c = coordinator(backgroundScope, player, vol, prefs("conn.namida.fade" to "0"))
        c.onRestStart()
        assertEquals(1, player.pauses)
        assertFalse(player.state.playing)
        assertTrue(c.pausedByUs)
        c.onRestEnd()
        assertEquals(1, player.plays)
        assertTrue(player.state.playing)
        assertFalse(c.pausedByUs)
        assertEquals("sin subida de volumen, el volumen no se toca", listOf(12), vol.history)
    }

    @Test fun neverTouchesMusicThatWasNotPlaying() = runTest {
        val player = FakePlayer(playing = false)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged())
        c.onRestStart()
        assertEquals(0, player.pauses)
        assertFalse(c.pausedByUs)
        c.onRestEnd()
        assertEquals("no reanuda lo que tú tenías en pausa", 0, player.plays)
    }

    @Test fun doesNothingWhenNamidaIsClosed() = runTest {
        val player = FakePlayer(playing = true, connected = false)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged())
        c.onRestStart()
        assertEquals(0, player.pauses)
        assertFalse(c.pausedByUs)
    }

    @Test fun connectorOffMeansNothingHappens() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged(), Prefs(mapOf("conn.namida.enabled" to "false")))
        c.onRestStart()
        assertEquals(0, player.pauses)
        c.onRestEnd()
        assertEquals(0, player.plays)
    }

    @Test fun pausingCanBeTurnedOffOnItsOwn() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged(), prefs("conn.namida.pause_rest" to "false"))
        c.onRestStart()
        assertEquals(0, player.pauses)
        assertTrue("la música sigue sonando", player.state.playing)
        c.onRestEnd()
        assertEquals(0, player.plays)
    }

    @Test fun resumingCanBeTurnedOffOnItsOwn() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged(), prefs("conn.namida.resume_rest" to "false"))
        c.onRestStart()
        assertEquals("pausa sí", 1, player.pauses)
        c.onRestEnd()
        assertEquals("pero no reanuda", 0, player.plays)
        assertFalse(c.pausedByUs)
        // y no queda «pendiente» para el siguiente descanso
        player.userSets(true)
        c.onRestStart()
        assertEquals(2, player.pauses)
    }

    @Test fun volumeRisesGraduallyToWhereItWas() = runTest {
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged(max = 15, start = 12)
        val store = FadeStore.Memory()
        // 6 s de subida, empezando en 10 % del volumen (12 → 1)
        val c = coordinator(backgroundScope, player, vol, prefs("conn.namida.fade" to "6", "conn.namida.fade_start" to "10"), store)
        c.onRestStart()
        c.onRestEnd()
        runCurrent()
        assertEquals("suena de inmediato", 1, player.plays)
        assertEquals("empieza bajito", 1, vol.level)
        assertTrue(c.fading)
        assertEquals("si la app se cierra ahora, sabe a qué volumen volver", 12, store.pendingTarget)
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue("a media subida: ${vol.level}", vol.level in 4..9)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(12, vol.level)
        assertFalse(c.fading)
        assertEquals(0, store.pendingTarget)
        // sube de uno en uno, sin saltos ni retrocesos
        val steps = vol.history.drop(1)   // el primer valor es el volumen inicial, antes de bajarlo
        assertEquals(steps.sorted(), steps)
        assertEquals(1, steps.first())
        assertTrue(steps.zipWithNext().all { (a, b) -> b - a == 1 })
        assertEquals(12, steps.last())
    }

    @Test fun zeroSecondsMeansFullVolumeRightAway() = runTest {
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged()
        val c = coordinator(backgroundScope, player, vol, prefs("conn.namida.fade" to "0"))
        c.onRestStart(); c.onRestEnd(); runCurrent()
        assertEquals(1, player.plays)
        assertEquals(12, vol.level)
        assertFalse(c.fading)
    }

    @Test fun anAlmostSilentVolumeIsNotFaded() = runTest {
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged(start = 1)
        val c = coordinator(backgroundScope, player, vol)
        c.onRestStart(); c.onRestEnd(); runCurrent()
        assertEquals(1, player.plays)
        assertEquals(1, vol.level)
        assertFalse(c.fading)
    }

    @Test fun userTouchingTheVolumeStopsTheFade() = runTest {
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged()
        val c = coordinator(backgroundScope, player, vol)
        c.onRestStart(); c.onRestEnd(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        val mid = vol.level
        assertTrue(mid in 2..11)
        vol.level = 3   // el usuario baja el volumen con el botón del teléfono
        advanceTimeBy(10_000); runCurrent()
        assertEquals("se respeta lo que puso el usuario", 3, vol.level)
        assertFalse(c.fading)
    }

    @Test fun aNewRestDuringTheFadeRestoresTheVolumeAndPausesAgain() = runTest {
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged()
        val store = FadeStore.Memory()
        val c = coordinator(backgroundScope, player, vol, store = store)
        c.onRestStart(); c.onRestEnd(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertTrue(c.fading)
        c.onRestStart()   // siguiente serie: otro descanso
        assertFalse(c.fading)
        assertEquals("deja el volumen donde estaba antes de pausar", 12, vol.level)
        assertEquals(0, store.pendingTarget)
        assertEquals(2, player.pauses)
        assertTrue(c.pausedByUs)
        advanceTimeBy(20_000); runCurrent()
        assertEquals("la subida cancelada no sigue en segundo plano", 12, vol.level)
    }

    @Test fun ifYouPressPlayDuringRestNothingIsResumed() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged())
        c.onRestStart()
        player.userSets(true)   // tú le diste play
        c.onRestEnd()
        assertEquals(0, player.plays)
        assertFalse(c.fading)
    }

    @Test fun ifNamidaClosesDuringRestNothingIsResumed() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged())
        c.onRestStart()
        player.close()
        c.onRestEnd()
        assertEquals(0, player.plays)
    }

    @Test fun endingARestTwiceResumesOnlyOnce() = runTest {
        val player = FakePlayer(playing = true)
        val c = coordinator(backgroundScope, player, FakeVolumeLogged(), prefs("conn.namida.fade" to "0"))
        c.onRestStart()
        c.onRestEnd()
        c.onRestEnd()
        assertEquals(1, player.plays)
        // y empezar dos veces seguidas pausa una sola vez
        player.userSets(true)
        c.onRestStart(); c.onRestStart()
        assertEquals(2, player.pauses)
    }

    @Test fun recoverFixesTheVolumeAfterTheAppWasKilledMidFade() = runTest {
        val store = FadeStore.Memory().also { it.pendingTarget = 10 }
        val vol = FakeVolumeLogged(start = 3)
        coordinator(backgroundScope, FakePlayer(), vol, store = store).recover()
        assertEquals(10, vol.level)
        assertEquals(0, store.pendingTarget)
        // si ya estaba más alto (el usuario lo subió), no lo baja
        store.pendingTarget = 8
        coordinator(backgroundScope, FakePlayer(), vol, store = store).recover()
        assertEquals(10, vol.level)
    }

    @Test fun resetCancelsEverything() = runTest {
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged()
        val c = coordinator(backgroundScope, player, vol)
        c.onRestStart()
        c.reset()
        assertFalse(c.pausedByUs)
        c.onRestEnd()
        assertEquals(0, player.plays)
        // también corta una subida en curso y deja el volumen bien
        player.userSets(true)
        c.onRestStart(); c.onRestEnd(); runCurrent(); advanceTimeBy(500); runCurrent()
        c.reset()
        assertFalse(c.fading)
        assertEquals(12, vol.level)
    }

    // ───────── Estado y permisos de cada conector

    private fun hub(scope: CoroutineScope, platform: FakePlatform, player: FakePlayer = FakePlayer(), repo: Repo = newRepo(), vol: VolumeControl = FakeVolumeLogged()) =
        ConnectorHub(repo, scope, platform, player, vol)

    @Test fun connectorListIsComplete() {
        assertEquals(listOf("namida", "spotify", "ytmusic", "namstat", "health", "phone"), Connectors.all.map { it.id })
        for (d in Connectors.all) {
            assertTrue(d.about.length > 40)
            assertEquals("ícono de ${d.id}", app.fitstat.ui.design.icOf(d.icon, app.fitstat.ui.design.Ic.Circle), app.fitstat.ui.design.icOf(d.icon, app.fitstat.ui.design.Ic.Moon))
            // (si el nombre no existiera, los dos «respaldos» distintos darían íconos distintos; con uno que existe dan el mismo)
            assertEquals("conn.${d.id}.enabled", d.enabledKey)
            assertNotNull("ajuste que lo enciende", Settings.byKey[d.enabledKey])
            assertEquals("false", Settings.byKey[d.enabledKey]!!.default)
        }
        assertEquals("health", Connectors.find("Health Connect")?.id)
        assertEquals("health", Connectors.find("mi reloj")?.id)
        assertEquals("namida", Connectors.find("Namida")?.id)
        assertEquals("namida", Connectors.find("la música")?.id)
        assertEquals("phone", Connectors.find("uso del teléfono")?.id)
        assertEquals("spotify", Connectors.find("Spotify")?.id)
        assertEquals("ytmusic", Connectors.find("youtube music")?.id)
        assertEquals("ytmusic", Connectors.find("YT Music")?.id)
        assertNull(Connectors.find("tidal"))
        assertNull(Connectors.find(""))
        assertEquals("Settings page", Pages.CONNECTORS, Settings.pages.first { it.id == Pages.CONNECTORS }.id)
    }

    @Test fun namidaStatusFollowsInstallAndAccess() = runTest {
        val pf = FakePlatform()
        val h = hub(backgroundScope, pf)
        runCurrent()
        assertEquals("apagado hasta que lo conectes", ConnStatus.Off, h.status(Connectors.NAMIDA))
        h.enable(Connectors.NAMIDA)
        assertEquals("sin Namida instalada", ConnStatus.Missing, h.status(Connectors.NAMIDA))
        assertNotNull(h.missing(Connectors.NAMIDA))
        pf.installedPkgs = setOf(Namida.packages.first())
        h.refresh()
        assertEquals(ConnStatus.NeedsAccess, h.status(Connectors.NAMIDA))
        assertTrue(h.missing(Connectors.NAMIDA)!!.contains("notificaciones"))
        pf.notif = true
        h.refresh()
        assertEquals(ConnStatus.Ready, h.status(Connectors.NAMIDA))
        assertNull(h.missing(Connectors.NAMIDA))
        h.disconnect(Connectors.NAMIDA)
        assertEquals(ConnStatus.Off, h.status(Connectors.NAMIDA))
    }

    @Test fun connectOpensTheRightSystemScreen() = runTest {
        val pf = FakePlatform().also { it.installedPkgs = setOf(Namida.packages.first()); it.health = HealthAvail.Available; it.usage = false }
        val h = hub(backgroundScope, pf)
        runCurrent()
        assertEquals(ConnStatus.NeedsAccess, h.connect(Connectors.NAMIDA))
        assertEquals(listOf(ConnOpen.NotificationAccess), pf.opened)
        h.connect(Connectors.PHONE)
        assertEquals(ConnOpen.UsageAccess, pf.opened.last())
        // Health Connect pide el permiso a través de la actividad
        var asked = 0
        h.healthRequest = { asked++ }
        h.connect(Connectors.HEALTH); runCurrent()
        assertEquals(1, asked)
        // si ya está todo, no abre nada
        pf.notif = true; h.refresh()
        val n = pf.opened.size
        assertEquals(ConnStatus.Ready, h.connect(Connectors.NAMIDA))
        assertEquals(n, pf.opened.size)
    }

    @Test fun healthConnectMissingOrNeedsInstall() = runTest {
        val pf = FakePlatform()
        val h = hub(backgroundScope, pf)
        h.enable(Connectors.HEALTH)
        assertEquals(ConnStatus.Missing, h.status(Connectors.HEALTH))
        assertTrue(h.missing(Connectors.HEALTH)!!.contains("no tiene Health Connect"))
        pf.health = HealthAvail.NeedsInstall
        h.refresh()
        assertTrue(h.missing(Connectors.HEALTH)!!.contains("instalarse"))
        h.requestAccess(Connectors.HEALTH)
        assertEquals(ConnOpen.HealthInstall, pf.opened.last())
        pf.health = HealthAvail.Available
        h.refresh(); runCurrent()
        assertEquals(ConnStatus.NeedsAccess, h.status(Connectors.HEALTH))
        pf.granted = true
        h.refresh(); runCurrent()
        assertEquals(ConnStatus.Ready, h.status(Connectors.HEALTH))
    }

    @Test fun phoneUsageNeedsAndroid9AndAccess() = runTest {
        val pf = FakePlatform().also { it.usageOk = false }
        val h = hub(backgroundScope, pf)
        h.enable(Connectors.PHONE)
        assertEquals(ConnStatus.Missing, h.status(Connectors.PHONE))
        pf.usageOk = true; h.refresh()
        assertEquals(ConnStatus.NeedsAccess, h.status(Connectors.PHONE))
        pf.usage = true; h.refresh()
        assertEquals(ConnStatus.Ready, h.status(Connectors.PHONE))
    }

    // ───────── Vigilar el descanso del entrenamiento

    private fun TestScopeHub(scope: CoroutineScope, repo: Repo, player: FakePlayer, vol: VolumeControl): Pair<ConnectorHub, FakePlatform> {
        val pf = FakePlatform().also { it.installedPkgs = setOf(Namida.packages.first()); it.notif = true }
        repo.setSetting("conn.namida.enabled", "true")
        return ConnectorHub(repo, scope, pf, player, vol) to pf
    }

    @Test fun restWatcherPausesWhenRestStartsAndResumesWhenItEnds() = runTest {
        val repo = newRepo()
        val player = FakePlayer(playing = true)
        val vol = FakeVolumeLogged()
        val (h, _) = TestScopeHub(backgroundScope, repo, player, vol)
        var restEnd by mutableLongStateOf(0L)
        h.watchRest({ restEnd }, { testScheduler.currentTime })
        runCurrent()
        assertEquals("sin descanso no pasa nada", 0, player.pauses)

        restEnd = testScheduler.currentTime + 30_000
        Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals("pausa al empezar el descanso", 1, player.pauses)
        advanceTimeBy(29_000); runCurrent()
        assertEquals(0, player.plays)
        advanceTimeBy(1_500); runCurrent()
        assertEquals("reanuda al terminar", 1, player.plays)
        assertTrue("con el volumen bajo para subirlo poco a poco", vol.level < 12)
        advanceTimeBy(8_000); runCurrent()
        assertEquals(12, vol.level)

        // el entrenamiento limpia el descanso al empezar la siguiente serie: no vuelve a reanudar
        restEnd = 0L
        Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(1, player.plays)
        assertEquals(1, player.pauses)
    }

    @Test fun skippingTheRestResumesImmediately() = runTest {
        val repo = newRepo()
        val player = FakePlayer(playing = true)
        val (h, _) = TestScopeHub(backgroundScope, repo, player, FakeVolumeLogged())
        var restEnd by mutableLongStateOf(0L)
        h.watchRest({ restEnd }, { testScheduler.currentTime })
        runCurrent()
        restEnd = testScheduler.currentTime + 90_000
        Snapshot.sendApplyNotifications(); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, player.pauses)
        assertEquals(0, player.plays)
        restEnd = 0L   // «Saltar descanso» o cancelar el entrenamiento
        Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals("no espera a que se acabe el tiempo", 1, player.plays)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(1, player.plays)
    }

    @Test fun extendingTheRestMovesTheResumeAndResumesOnce() = runTest {
        val repo = newRepo()
        val player = FakePlayer(playing = true)
        val (h, _) = TestScopeHub(backgroundScope, repo, player, FakeVolumeLogged())
        var restEnd by mutableLongStateOf(0L)
        h.watchRest({ restEnd }, { testScheduler.currentTime })
        runCurrent()
        restEnd = testScheduler.currentTime + 30_000
        Snapshot.sendApplyNotifications(); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        restEnd += 15_000   // «+15 s»
        Snapshot.sendApplyNotifications(); runCurrent()
        advanceTimeBy(21_000); runCurrent()   // 31 s: pasó el final original (30 s) pero no el nuevo (45 s)
        assertEquals("todavía descansando", 0, player.plays)
        advanceTimeBy(15_000); runCurrent()   // 46 s
        assertEquals(1, player.plays)
        assertEquals("una sola pausa", 1, player.pauses)
    }

    @Test fun disconnectingDuringRestDoesNotResume() = runTest {
        val repo = newRepo()
        val player = FakePlayer(playing = true)
        val (h, _) = TestScopeHub(backgroundScope, repo, player, FakeVolumeLogged())
        var restEnd by mutableLongStateOf(0L)
        h.watchRest({ restEnd }, { testScheduler.currentTime })
        runCurrent()
        restEnd = testScheduler.currentTime + 20_000
        Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(1, player.pauses)
        h.disconnect(Connectors.NAMIDA)
        advanceTimeBy(25_000); runCurrent()
        assertEquals(0, player.plays)
    }

    // ───────── Sueño: sincronizar

    private val H = 3600_000L

    private fun healthNight(id: String = "hc_${TODAY}") =
        SleepSession(id, at(TODAY.minusDays(1), 23, 10), at(TODAY, 6, 50), SleepSrc.HEALTH, listOf(SleepStage(SleepStageType.DEEP, at(TODAY.minusDays(1), 23, 30), at(TODAY, 1, 0))))

    /** Tres madrugadas con 4 h 30 min sin tocar el teléfono (de la 1:00 a las 5:30). */
    private fun shortEvents() = (1..3).flatMap { i ->
        val d = TODAY.minusDays(i.toLong())
        listOf(ScreenEvent(at(d, 1, 0), false), ScreenEvent(at(d, 5, 30), true))
    }

    private fun phoneEvents() = (1..3).flatMap { i ->
        val d = TODAY.minusDays(i.toLong())
        listOf(ScreenEvent(at(d, 23, 40), false), ScreenEvent(at(d.plusDays(1), 7, 10), true))
    }

    @Test fun syncNeedsAConnectedSource() = runTest {
        val h = hub(backgroundScope, FakePlatform())
        val r = h.syncSleep()
        assertFalse(r.ok)
        assertTrue(r.message.contains("Conectores"))
        assertEquals(emptyList<String>(), h.sleepSources())
    }

    @Test fun syncBringsNightsFromHealthConnect() = runTest {
        val pf = FakePlatform().also { it.health = HealthAvail.Available; it.granted = true; it.healthSleep = listOf(healthNight()) }
        val repo = newRepo()
        val h = hub(backgroundScope, pf, repo = repo)
        h.enable(Connectors.HEALTH)
        val r = h.syncSleep()
        assertTrue(r.message, r.ok)
        assertEquals(1, r.newNights)
        assertEquals(1, pf.healthReads)
        assertEquals(1, repo.sleep.size)
        assertTrue("trae fases", Sleep.hasStages(repo.sleep.single()))
        // sincronizar otra vez no duplica nada
        assertEquals(0, h.syncSleep().newNights)
        assertEquals(1, repo.sleep.size)
    }

    @Test fun grantingTheHealthPermissionAndSyncingRightAwayWorks() = runTest {
        // Justo después de dar el permiso, la primera sincronización no debe decir «no hay conector listo».
        val pf = FakePlatform().also { it.health = HealthAvail.Available; it.granted = false; it.healthSleep = listOf(healthNight()) }
        val h = hub(backgroundScope, pf)
        h.enable(Connectors.HEALTH)
        runCurrent()
        assertEquals(ConnStatus.NeedsAccess, h.status(Connectors.HEALTH))
        pf.granted = true
        val r = h.syncSleep()   // sin pasar por refresh() antes
        assertTrue(r.message, r.ok)
        assertEquals(1, r.newNights)
        assertEquals(ConnStatus.Ready, h.status(Connectors.HEALTH))
    }

    @Test fun syncEstimatesFromThePhone() = runTest {
        val pf = FakePlatform().also { it.usage = true; it.events = phoneEvents() }
        val repo = newRepo()
        val h = hub(backgroundScope, pf, repo = repo)
        h.enable(Connectors.PHONE)
        val r = h.syncSleep()
        assertTrue(r.message, r.ok)
        assertEquals(3, r.newNights)
        assertTrue(repo.sleep.all { it.source == SleepSrc.PHONE && it.stages.isEmpty() })
        assertTrue(r.message.contains("estimadas"))
    }

    @Test fun phoneGapSettingChangesWhatCounts() = runTest {
        val pf = FakePlatform().also { it.usage = true; it.events = shortEvents() }   // tramos de 4.5 h
        val repo = newRepo()
        val h = hub(backgroundScope, pf, repo = repo)
        h.enable(Connectors.PHONE)
        assertEquals("con el mínimo de siempre (3 h) cuentan", 3, h.syncSleep().newNights)
        val strict = newRepo()
        strict.setSetting("sleep.phone_gap", "5")   // exige 5 h sin tocar el teléfono
        val h2 = hub(backgroundScope, pf, repo = strict)
        h2.enable(Connectors.PHONE)
        assertEquals(0, h2.syncSleep().newNights)
        assertTrue(strict.sleep.isEmpty())
    }

    @Test fun aFailingSourceDoesNotStopTheOthers() = runTest {
        val pf = FakePlatform().also {
            it.health = HealthAvail.Available; it.granted = true; it.healthError = IllegalStateException("sin conexión con Health Connect")
            it.usage = true; it.events = phoneEvents()
        }
        val repo = newRepo()
        val h = hub(backgroundScope, pf, repo = repo)
        h.enable(Connectors.HEALTH); h.enable(Connectors.PHONE)
        val r = h.syncSleep()
        assertTrue(r.ok)
        assertEquals(3, r.newNights)
        assertTrue(r.message.contains("no pude leer"))
    }

    @Test fun manualNightBeatsAutomaticOnes() = runTest {
        val pf = FakePlatform().also { it.health = HealthAvail.Available; it.granted = true; it.healthSleep = listOf(healthNight()) }
        val repo = newRepo()
        val h = hub(backgroundScope, pf, repo = repo)
        h.enable(Connectors.HEALTH)
        repo.addSleep(at(TODAY.minusDays(1), 22, 45), at(TODAY, 6, 30), "yo sé cuándo dormí")
        h.syncSleep()
        val n = repo.sleepNights().single()
        assertEquals(SleepSrc.MANUAL, n.main.source)
    }

    @Test fun autoSyncRespectsTheSettingAndWaitsBetweenRuns() = runTest {
        val pf = FakePlatform().also { it.health = HealthAvail.Available; it.granted = true; it.healthSleep = listOf(healthNight()) }
        val repo = newRepo()
        val h = hub(backgroundScope, pf, repo = repo)
        h.enable(Connectors.HEALTH)
        runCurrent()
        repo.setSetting("sleep.auto_sync", "false")
        h.autoSync(); runCurrent()
        assertEquals("apagado: no sincroniza solo", 0, pf.healthReads)
        h.autoSync(force = true); runCurrent()
        assertEquals("pero a mano sí", 1, pf.healthReads)
        repo.setSetting("sleep.auto_sync", "true")
        h.autoSync(); runCurrent()
        assertEquals("recién actualizado: espera", 1, pf.healthReads)
    }

    // ───────── FITBOT: herramientas de conectores y sueño

    private fun toolHost(configure: (FakePlatform) -> Unit = {}, scope: CoroutineScope, player: FakePlayer = FakePlayer()): Pair<ToolHost, FakePlatform> {
        val pf = FakePlatform().also(configure)
        val repo = newRepo()
        return ToolHost(repo, hub(scope, pf, player, repo)) to pf
    }

    @Test fun listConnectorsDescribesEachOne() = runTest {
        val (host, _) = toolHost(scope = backgroundScope)
        val out = call(host, "list_connectors")
        assertTrue(out.ok)
        val list = out.json["connectors"]!!.jsonArray.map { it.jsonObject }
        assertEquals(Connectors.all.map { it.id }, list.map { it["id"]!!.jsonPrimitive.content })
        assertTrue(list.all { it["status"]!!.jsonPrimitive.content == "off" && !it["connected"]!!.jsonPrimitive.boolean })
        // las opciones del conector de Namida van incluidas para que FITBOT sepa cambiarlas
        val keys = list.first { it["id"]!!.jsonPrimitive.content == "namida" }["settings"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content }
        assertTrue(keys.toString(), "conn.namida.pause_rest" in keys && "conn.namida.resume_rest" in keys && "conn.namida.fade" in keys)
    }

    @Test fun connectConnectorEnablesAndSendsUserToGrantPermission() = runTest {
        val (host, pf) = toolHost({ it.installedPkgs = setOf(Namida.packages.first()) }, backgroundScope)
        val out = call(host, "connect_connector", """{"connector":"Namida"}""")
        assertTrue(out.json.toString(), out.ok)
        assertEquals("needs_permission", out.json["status"]!!.jsonPrimitive.content)
        assertTrue(out.json.containsKey("next_step"))
        assertEquals("abre la pantalla del conector para que el usuario dé el permiso", listOf<Route>(Route.Connector("namida")), host.navigated)
        assertTrue(host.hub!!.enabled("namida"))
        pf.notif = true
        host.hub.refresh()
        val ready = call(host, "connect_connector", """{"connector":"namida"}""")
        assertEquals("ready", ready.json["status"]!!.jsonPrimitive.content)
        assertEquals("ya no hay nada que abrir", 1, host.navigated.size)
        // desconectar
        val off = call(host, "connect_connector", """{"connector":"namida","connect":false}""")
        assertEquals("off", off.json["status"]!!.jsonPrimitive.content)
        assertFalse(host.hub.enabled("namida"))
    }

    @Test fun connectConnectorRejectsUnknownOnes() = runTest {
        val (host, _) = toolHost(scope = backgroundScope)
        val out = call(host, "connect_connector", """{"connector":"tidal"}""")
        assertFalse(out.ok)
        assertTrue(out.err.contains("namida"))
        assertTrue(host.navigated.isEmpty())
    }

    @Test fun connectorToolsExplainWhenThereAreNoConnectors() {
        val host = ToolHost(newRepo(), null)
        for (t in listOf("list_connectors", "connect_connector", "get_music", "music_control", "sync_sleep")) {
            val out = call(host, t, """{"connector":"namida","action":"play"}""")
            assertFalse("$t sin conectores", out.ok)
            assertTrue(out.err.contains("no están disponibles"))
        }
        // y el sueño sigue funcionando sin ellos
        assertTrue(call(host, "get_sleep").ok)
    }

    @Test fun musicToolsReadAndControlNamida() = runTest {
        val player = FakePlayer(playing = true)
        val (host, pf) = toolHost({ it.installedPkgs = setOf(Namida.packages.first()); it.notif = true }, backgroundScope, player)
        // sin conectar no controla nada
        val notYet = call(host, "music_control", """{"action":"pause"}""")
        assertFalse(notYet.ok)
        assertEquals(0, player.pauses)
        host.hub!!.enable("namida")
        val now = call(host, "get_music")
        assertTrue(now.ok)
        assertEquals("ready", now.json["connector_status"]!!.jsonPrimitive.content)
        assertEquals("Canción", now.json["title"]!!.jsonPrimitive.content)
        assertTrue(now.json["playing"]!!.jsonPrimitive.boolean)
        assertTrue(call(host, "music_control", """{"action":"pause"}""").ok)
        assertEquals(1, player.pauses)
        assertTrue(call(host, "music_control", """{"action":"toggle"}""").ok)
        assertEquals(1, player.plays)
        call(host, "music_control", """{"action":"next"}""")
        call(host, "music_control", """{"action":"anterior"}""")
        assertEquals(1, player.nexts); assertEquals(1, player.prevs)
        assertFalse(call(host, "music_control", """{"action":"bailar"}""").ok)
        // Namida cerrada: lo dice claro en vez de fingir
        player.close()
        val closed = call(host, "music_control", """{"action":"play"}""")
        assertFalse(closed.ok)
        assertTrue(closed.err.contains("abierta"))
        // sin permiso
        pf.notif = false; host.hub.refresh()
        assertTrue(call(host, "music_control", """{"action":"play"}""").err.contains("notificaciones"))
    }

    @Test fun logSleepWritesAManualNight() = runTest {
        val (host, _) = toolHost(scope = backgroundScope)
        val out = call(host, "log_sleep", """{"bedtime":"11:30 pm","wake_time":"7:00","wake_day":"hoy","note":"buen sueño"}""")
        assertTrue(out.json.toString(), out.ok)
        val s = host.repo.sleep.single()
        assertEquals(SleepSrc.MANUAL, s.source)
        assertEquals("buen sueño", s.note)
        assertEquals(at(TODAY.minusDays(1), 23, 30), s.start)
        assertEquals(at(TODAY, 7), s.end)
        assertEquals("7 h 30 min", out.json["asleep"]!!.jsonPrimitive.content)
        // ayer
        assertTrue(call(host, "log_sleep", """{"bedtime":"00:30","wake_time":"08:00","wake_day":"ayer"}""").ok)
        assertEquals(2, host.repo.sleep.size)
    }

    @Test fun logSleepRejectsNonsense() = runTest {
        val (host, _) = toolHost(scope = backgroundScope)
        assertFalse(call(host, "log_sleep", "{}").ok)
        assertFalse(call(host, "log_sleep", """{"bedtime":"nunca","wake_time":"07:00"}""").ok)
        assertFalse(call(host, "log_sleep", """{"bedtime":"23:00","wake_time":"07:00","wake_day":"el siglo pasado"}""").ok)
        assertFalse("10 minutos no es una noche", call(host, "log_sleep", """{"bedtime":"06:50","wake_time":"07:00"}""").ok)
        assertFalse("futuro", call(host, "log_sleep", """{"bedtime":"23:00","wake_time":"07:00","wake_day":"2026-11-05"}""").ok)
        assertTrue(host.repo.sleep.isEmpty())
    }

    @Test fun getSleepSaysWhatIsEstimatedAndOnlyShowsRealStages() = runTest {
        val (host, _) = toolHost(scope = backgroundScope)
        val empty = call(host, "get_sleep")
        assertTrue(empty.ok)
        assertEquals(0, empty.json["nights"]!!.jsonArray.size)
        assertTrue("explica cómo empezar", empty.json.containsKey("how_to_start"))

        host.repo.mergeSleep(listOf(healthNight(), SleepSession("phone_${TODAY.minusDays(1)}", at(TODAY.minusDays(2), 23, 50), at(TODAY.minusDays(1), 7, 20), SleepSrc.PHONE)))
        val out = call(host, "get_sleep", """{"days":7}""")
        val nights = out.json["nights"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, nights.size)
        val watch = nights[0]
        assertEquals("health", watch["source"]!!.jsonPrimitive.content)
        assertTrue("el reloj sí trae fases", watch.containsKey("deep_minutes"))
        assertFalse(watch["estimated"]!!.jsonPrimitive.boolean)
        val est = nights[1]
        assertTrue(est["estimated"]!!.jsonPrimitive.boolean)
        assertFalse("lo estimado con el teléfono nunca trae fases", est.containsKey("deep_minutes") || est.containsKey("rem_minutes"))
        assertEquals(1, out.json["nights_with_stages"]!!.jsonPrimitive.content.toInt())
        assertTrue(out.json.containsKey("average_asleep"))
        assertEquals(8.0, out.json["goal_hours"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test fun deleteSleepOnlyRemovesManualNights() = runTest {
        val (host, _) = toolHost(scope = backgroundScope)
        host.repo.mergeSleep(listOf(healthNight()))
        assertFalse("la del reloj no se puede borrar (volvería al actualizar)", call(host, "delete_sleep", """{"wake_day":"hoy"}""").ok)
        assertEquals(1, host.repo.sleep.size)
        call(host, "log_sleep", """{"bedtime":"22:00","wake_time":"06:00","wake_day":"ayer"}""")
        assertEquals(2, host.repo.sleep.size)
        assertTrue(call(host, "delete_sleep", """{"wake_day":"ayer"}""").ok)
        assertEquals(1, host.repo.sleep.size)
        assertEquals(SleepSrc.HEALTH, host.repo.sleep.single().source)
        val tool = allTools.first { it.name == "delete_sleep" }
        assertTrue("pide confirmación", tool.destructive(JsonObject(emptyMap())))
    }

    @Test fun syncSleepToolStartsTheSyncOrExplainsWhyNot() = runTest {
        val (host, pf) = toolHost({ it.health = HealthAvail.Available; it.granted = true; it.healthSleep = listOf(healthNight()) }, backgroundScope)
        val none = call(host, "sync_sleep")
        assertFalse(none.ok)
        assertTrue(none.err.contains("connect_connector"))
        host.hub!!.enable(Connectors.HEALTH)
        runCurrent()
        val out = call(host, "sync_sleep")
        assertTrue(out.json.toString(), out.ok)
        runCurrent()
        assertEquals(1, pf.healthReads)
        assertEquals(1, host.repo.sleep.size)
    }

    @Test fun botSettingsToolsKnowTheConnectorOptions() {
        for (k in listOf("conn.namida.enabled", "conn.namida.pause_rest", "conn.namida.resume_rest", "conn.namida.fade", "conn.namida.fade_start", "conn.health.enabled", "conn.phone.enabled", "sleep.goal", "sleep.auto_sync", "sleep.phone_gap")) {
            assertNotNull("ajuste $k", Settings.byKey[k])
        }
        // las dos funciones de Namida se apagan por separado y la subida de volumen depende de reanudar
        val pause = Settings.byKey["conn.namida.pause_rest"]!!
        val resume = Settings.byKey["conn.namida.resume_rest"]!!
        assertEquals("true", pause.default); assertEquals("true", resume.default)
        assertEquals("conn.namida.enabled", pause.visibleIf!!.first)
        assertEquals("conn.namida.resume_rest", Settings.byKey["conn.namida.fade"]!!.visibleIf!!.first)
        assertEquals("6", Settings.byKey["conn.namida.fade"]!!.default)
        assertEquals("10", Settings.byKey["conn.namida.fade_start"]!!.default)
        assertTrue(Settings.specsOf(Pages.CONNECTORS).any { it.key == "conn.namida.pause_rest" })
    }

    @Test fun fitbotPromptKnowsEveryConnectorAndStaysShort() = runTest {
        val (host, _) = toolHost(scope = backgroundScope)
        val p = Prompt.system(host, ModelInfo(id = "test/model", name = "Test", parameters = setOf("tools")))
        for (d in Connectors.all) assertTrue("el prompt no menciona ${d.id}", p.contains(d.id) && p.contains(d.name))
        assertTrue(p.contains("get_sleep") && p.contains("log_sleep") && p.contains("NO hay fases"))
        assertTrue("el prompt no debería pasar de ~14 000 caracteres (${p.length})", p.length < 14_000)
    }

    @Test fun everyConnectorToolIsRegistered() {
        val names = allTools.map { it.name }.toSet()
        for (t in listOf("list_connectors", "connect_connector", "get_music", "music_control", "get_sleep", "log_sleep", "delete_sleep", "sync_sleep"))
            assertTrue("falta la herramienta $t", t in names)
        assertEquals("sin nombres repetidos", allTools.size, names.size)
    }

    // ───────── Rutas

    @Test fun connectorRoutesAreReachableByName() {
        assertEquals(Route.Sleep, Route.fromName("sueño"))
        assertEquals(Route.Sleep, Route.fromName("sleep"))
        assertEquals(Route.Connector("namida"), Route.fromName("conector:namida"))
        assertEquals("cn:namida", Route.Connector("namida").key)
        assertEquals("sleep", Route.Sleep.key)
    }

    // ───────── El changelog dice la verdad

    @Test fun changelogAnnouncesVersion17() {
        val e = Changelog.entries.first { it.version == "1.7" }
        assertEquals("1.7", e.version)
        val text = e.sections.flatMap { it.second }.joinToString("\n")
        assertTrue("ejercicios: ${Catalog.all.size}", text.contains("${Catalog.all.size} ejercicios"))
        assertTrue("widgets: ${HomeLayout.all.size}", text.contains("${HomeLayout.all.size} widgets"))
        assertTrue(text.contains("Health Connect") && text.contains("Namida") && text.contains("estimado"))
        assertEquals("1.7", e.version)
        val titles = e.sections.map { it.first }
        assertTrue(listOf("Sueño", "Conectores", "Namida", "Ejercicios", "FITBOT").all { it in titles })
    }
}
