package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Connectors
import app.fitstat.conn.HealthAvail
import app.fitstat.conn.Namida
import app.fitstat.data.Demo
import app.fitstat.data.HomeSize
import app.fitstat.data.Pages
import app.fitstat.data.Repo
import app.fitstat.data.ScreenEvent
import app.fitstat.data.SleepSession
import app.fitstat.data.SleepSrc
import app.fitstat.data.SleepStage
import app.fitstat.data.SleepStageType
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.7: las pantallas nuevas con la interfaz de verdad (Robolectric): la lista de conectores, el recorrido de cada uno hasta
 * dejarlo listo, el widget de música, el sueño y su registro a mano. Guarda capturas para revisarlas a ojo.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
class ConnectorsUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val today = LocalDate.of(2026, 10, 28)
    private val now = today.atTime(18, 40).atZone(zone).toInstant().toEpochMilli()
    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private class Env(val app: AppState, val repo: Repo, val hub: ConnectorHub, val pf: FakePlatform, val player: FakePlayer)

    private fun makeEnv(demo: Boolean = true, playing: Boolean = true): Env {
        val dir = Files.createTempDirectory("fitstat-connui").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        if (demo) {
            repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
            repo.setProfileName("Chris")
        }
        repo.setSetting("theme.mode", "dark")
        val pf = FakePlatform()
        val player = FakePlayer(playing = playing)
        val hub = ConnectorHub(repo, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), pf, player, FakeVolumeLogged())
        val app = AppState(ApplicationProvider.getApplicationContext(), repo, connectors = hub)
        return Env(app, repo, hub, pf, player)
    }

    private fun ui(block: () -> Unit) {
        rule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
    }

    private fun start(e: Env) {
        rule.setContent { FitstatRoot(e.app) }
        Shots.settle(rule, 1600)
    }

    private fun go(e: Env, route: Route) {
        ui { e.app.nav.push(route) }
        Shots.settle(rule, 800)
    }

    private fun tag(t: String) = rule.onNodeWithTag(t)
    private fun text(t: String, substring: Boolean = false) = rule.onNodeWithText(t, substring = substring)

    /** Noches de reloj con fases, una por cada día hacia atrás desde hoy. */
    private fun watchNights(count: Int): List<SleepSession> = (0 until count).map { i ->
        val wake = today.minusDays(i.toLong())
        val start = at(wake.minusDays(1), 21 + (i % 3), 40)
        val end = at(wake, 6 + (i % 2), 30)
        val h = 3600_000L
        SleepSession(
            "hc_$wake", start, end, SleepSrc.HEALTH,
            stages = listOf(
                SleepStage(SleepStageType.LIGHT, start, start + 3 * h),
                SleepStage(SleepStageType.DEEP, start + 3 * h, start + 4 * h + h / 2),
                SleepStage(SleepStageType.REM, start + 4 * h + h / 2, start + 6 * h),
                SleepStage(SleepStageType.AWAKE, start + 6 * h, start + 6 * h + 15 * 60_000L),
                SleepStage(SleepStageType.LIGHT, start + 6 * h + 15 * 60_000L, end),
            ),
            origin = "com.fitbit",
        )
    }

    // ───────── Ajustes › Conectores

    @Test
    fun connectorsPageListsEveryConnectorAndOpensThem() {
        val e = makeEnv()
        start(e)
        go(e, Route.SettingsPage(Pages.CONNECTORS))
        for (d in Connectors.all) {
            tag("conn_${d.id}").assertExists()
            text(d.name).assertExists()
        }
        text("Conecta tu entorno").assertExists()
        Shots.capture(rule, "v17_connectors")
        tag("conn_namida").performClick()
        Shots.settle(rule, 700)
        assertEquals(Route.Connector("namida"), e.app.nav.top)
    }

    @Test
    fun connectorsHaveTheirOwnEntryInSettings() {
        val e = makeEnv()
        start(e)
        go(e, Route.Settings)
        text("Conectores").assertExists()
        Shots.capture(rule, "v17_settings_index")
    }

    // ───────── Namida

    @Test
    fun namidaWalksFromOffToReadyAndShowsItsOptions() {
        val e = makeEnv(playing = true)
        e.pf.installedPkgs = setOf(Namida.packages.first())
        start(e)
        ui { e.hub.refresh() }
        go(e, Route.Connector("namida"))
        tag("conn_connect").assertExists()
        Shots.capture(rule, "v17_namida_off")

        tag("conn_connect").performClick()
        Shots.settle(rule, 600)
        assertTrue(e.repo.prefs.bool("conn.namida.enabled"))
        assertEquals(ConnStatus.NeedsAccess, e.hub.status(Connectors.NAMIDA))
        assertEquals(listOf(ConnOpen.NotificationAccess), e.pf.opened)
        tag("conn_grant").assertExists()
        Shots.capture(rule, "v17_namida_needs_access")

        ui { e.pf.notif = true; e.hub.refresh() }
        Shots.settle(rule, 700)
        tag("conn_grant").assertDoesNotExist()
        tag("conn_disconnect").assertExists()
        for (t in listOf("Pausar la música en el descanso", "Reanudar al terminar el descanso", "Subida gradual del volumen", "Volumen con el que arranca")) text(t).assertExists()
        Shots.capture(rule, "v17_namida_ready")

        // los botones de la tarjeta «Ahora en Namida» llegan al reproductor
        tag("music_toggle").performClick()
        assertEquals(1, e.player.pauses)
        tag("music_next").performClick()
        tag("music_prev").performClick()
        assertEquals(1, e.player.nexts)
        assertEquals(1, e.player.prevs)

        // las dos funciones se apagan por separado y la subida de volumen desaparece si ya no se reanuda
        text("Pausar la música en el descanso").performClick()
        Shots.settle(rule, 400)
        assertFalse(e.repo.prefs.bool("conn.namida.pause_rest"))
        assertTrue(e.repo.prefs.bool("conn.namida.resume_rest"))
        text("Subida gradual del volumen").assertExists()
        text("Reanudar al terminar el descanso").performClick()
        Shots.settle(rule, 400)
        assertFalse(e.repo.prefs.bool("conn.namida.resume_rest"))
        text("Subida gradual del volumen").assertDoesNotExist()
        Shots.capture(rule, "v17_namida_options_off")

        tag("conn_disconnect").performClick()
        Shots.settle(rule, 500)
        assertFalse(e.repo.prefs.bool("conn.namida.enabled"))
        tag("conn_connect").assertExists()
    }

    @Test
    fun namidaNotInstalledSaysSoInsteadOfAskingForPermission() {
        val e = makeEnv()
        start(e)
        go(e, Route.Connector("namida"))
        tag("conn_connect").performClick()
        Shots.settle(rule, 600)
        assertEquals(ConnStatus.Missing, e.hub.status(Connectors.NAMIDA))
        text("No encuentro Namida instalada en este teléfono.").assertExists()
        tag("conn_grant").assertDoesNotExist()
        Shots.capture(rule, "v17_namida_missing")
    }

    @Test
    fun theWidgetInvitesToConnectWhenNamidaIsOff() {
        val e = makeEnv()
        start(e)
        ui { e.app.home.start(); e.app.home.add("music"); e.app.home.move("music", 0); e.app.home.finish() }
        Shots.settle(rule, 800)
        tag("music_widget").assertExists()
        tag("music_toggle").assertDoesNotExist()
        Shots.capture(rule, "v17_music_widget_off")
        tag("music_widget").performClick()
        Shots.settle(rule, 600)
        // v1.8.2: con ninguna app de música conectada, el widget lleva a la lista de conectores para elegir cuál
        assertEquals(Route.SettingsPage(Pages.CONNECTORS), e.app.nav.top)
    }

    @Test
    fun theMusicWidgetControlsNamidaInEverySize() {
        val e = makeEnv(playing = false)
        e.pf.installedPkgs = setOf(Namida.packages.first())
        e.pf.notif = true
        e.repo.setSetting("conn.namida.enabled", "true")
        start(e)
        ui { e.hub.refresh(); e.app.home.start(); e.app.home.add("music"); e.app.home.move("music", 0); e.app.home.finish() }
        Shots.settle(rule, 800)
        var plays = 0
        for (size in HomeSize.entries) {
            ui { e.app.home.start(); e.app.home.setSize("music", size); e.app.home.finish() }
            Shots.settle(rule, 700)
            tag("music_widget").assertExists()
            tag("music_toggle").assertExists().performClick()
            plays++
            assertEquals("play con el widget ${size.label}", plays, e.player.plays + e.player.pauses)
            Shots.capture(rule, "v17_music_widget_${size.id}")
            if (size != HomeSize.Small) {
                tag("music_next").performClick()
                tag("music_prev").performClick()
            }
        }
        assertEquals(2, e.player.nexts)
        assertEquals(2, e.player.prevs)
    }

    // ───────── Health Connect y teléfono

    @Test
    fun healthConnectExplainsWhatIsMissingAndThenSyncs() {
        val e = makeEnv()
        start(e)
        go(e, Route.Connector("health"))
        tag("conn_connect").performClick()
        Shots.settle(rule, 600)
        assertEquals(ConnStatus.Missing, e.hub.status(Connectors.HEALTH))
        text("Este teléfono no tiene Health Connect.").assertExists()
        Shots.capture(rule, "v17_health_missing")

        // Health Connect instalado pero sin permiso: el botón pide el permiso desde la app
        var asked = 0
        e.hub.healthRequest = { asked++ }
        ui { e.pf.health = HealthAvail.Available; e.hub.refresh() }
        Shots.settle(rule, 700)
        tag("conn_grant").assertExists().performClick()
        assertEquals(1, asked)
        Shots.capture(rule, "v17_health_needs_access")

        // con el permiso dado, sincronizar trae las noches
        ui { e.pf.granted = true; e.pf.healthSleep = watchNights(5); e.hub.refresh() }
        Shots.settle(rule, 700)
        tag("conn_grant").assertDoesNotExist()
        tag("conn_sync").assertIsEnabled().performClick()
        Shots.settle(rule, 900)
        assertEquals(5, e.repo.sleep.size)
        assertEquals(1, e.pf.healthReads)
        Shots.capture(rule, "v17_health_ready")
    }

    @Test
    fun phoneConnectorEstimatesHoursAndSaysItIsAnEstimate() {
        val e = makeEnv()
        start(e)
        go(e, Route.Connector("phone"))
        text("Es solo una estimación de horas", substring = true).assertExists()
        tag("conn_connect").performClick()
        Shots.settle(rule, 600)
        assertEquals(listOf(ConnOpen.UsageAccess), e.pf.opened)
        tag("conn_grant").assertExists()
        text("Mínimo sin tocar el teléfono").assertExists()
        Shots.capture(rule, "v17_phone_needs_access")

        ui {
            e.pf.usage = true
            e.pf.events = (1..3).flatMap { i ->
                val d = today.minusDays(i.toLong())
                listOf(ScreenEvent(at(d, 23, 40), false), ScreenEvent(at(d.plusDays(1), 7, 10), true))
            }
            e.hub.refresh()
        }
        Shots.settle(rule, 700)
        tag("conn_sync").assertIsEnabled().performClick()
        Shots.settle(rule, 900)
        assertEquals(3, e.repo.sleep.size)
        assertTrue(e.repo.sleep.all { it.source == SleepSrc.PHONE && it.stages.isEmpty() })
        Shots.capture(rule, "v17_phone_ready")
    }

    // ───────── Sueño

    @Test
    fun emptySleepOffersTheThreeWaysToStart() {
        val e = makeEnv()
        start(e)
        go(e, Route.Sleep)
        text("Aún no hay noches").assertExists()
        tag("sleep_health").assertExists()
        tag("sleep_phone").assertExists()
        tag("sleep_add").assertExists()
        tag("sleep_sync").assertDoesNotExist()
        Shots.capture(rule, "v17_sleep_empty")
        tag("sleep_phone").performClick()
        Shots.settle(rule, 600)
        assertEquals(Route.Connector("phone"), e.app.nav.top)
    }

    @Test
    fun aNightCanBeLoggedByHandFromTheSleepScreen() {
        val e = makeEnv()
        start(e)
        go(e, Route.Sleep)
        tag("sleep_add").performClick()
        Shots.settle(rule, 800)
        tag("sleep_bed").assertExists()
        tag("sleep_wake").assertExists()
        text("Son 8 h de sueño.").assertExists()
        Shots.capture(rule, "v17_sleep_entry")
        tag("sleep_save").performClick()
        Shots.settle(rule, 900)
        val s = e.repo.sleep.single()
        assertEquals(SleepSrc.MANUAL, s.source)
        assertEquals(at(today.minusDays(1), 23), s.start)
        assertEquals(at(today, 7), s.end)
        assertFalse(e.app.overlays.hasOverlay)
        tag("sleep_range").assertExists()
        text("Aún no hay noches").assertDoesNotExist()
        Shots.capture(rule, "v17_sleep_one_night")
    }

    @Test
    fun sleepWithWatchDataShowsStagesAndTheTrend() {
        val e = makeEnv()
        e.repo.mergeSleep(watchNights(10))
        e.repo.setSetting("conn.health.enabled", "true")
        e.pf.health = HealthAvail.Available
        e.pf.granted = true
        start(e)
        ui { e.hub.refresh() }
        Shots.settle(rule, 500)
        go(e, Route.Sleep)
        tag("sleep_range").assertExists()
        tag("sleep_sync").assertExists()
        text("Profundo", substring = true).assertExists()
        Shots.capture(rule, "v17_sleep_watch")
        text("14 noches").performClick()
        Shots.settle(rule, 600)
        Shots.capture(rule, "v17_sleep_watch_14")
        // tocar «actualizar» sincroniza con el reloj
        tag("sleep_sync").performClick()
        Shots.settle(rule, 900)
        assertEquals(1, e.pf.healthReads)
    }

    @Test
    fun sleepWidgetOpensTheSleepScreenInEverySize() {
        val e = makeEnv()
        e.repo.mergeSleep(watchNights(8))
        start(e)
        for (size in HomeSize.entries) {
            ui { e.app.home.start(); e.app.home.setSize("sleep", size); e.app.home.finish() }
            Shots.settle(rule, 700)
            tag("sleep_widget").assertExists().assertIsDisplayed()
            Shots.capture(rule, "v17_sleep_widget_${size.id}")
        }
        tag("sleep_widget").performClick()
        Shots.settle(rule, 600)
        assertEquals(Route.Sleep, e.app.nav.top)
    }

    @Test
    fun sleepWidgetWithoutDataInvitesToStart() {
        val e = makeEnv()
        start(e)
        tag("sleep_widget").assertExists()
        Shots.capture(rule, "v17_sleep_widget_empty")
        tag("sleep_widget").performClick()
        Shots.settle(rule, 600)
        assertEquals(Route.Sleep, e.app.nav.top)
    }

    @Test
    fun lightThemeAndBigFontsStillFit() {
        val e = makeEnv()
        e.repo.setSetting("theme.mode", "light")
        e.repo.mergeSleep(watchNights(6))
        e.repo.setSetting("conn.namida.enabled", "true")
        e.pf.installedPkgs = setOf(Namida.packages.first())
        e.pf.notif = true
        start(e)
        ui { e.hub.refresh(); e.app.home.start(); e.app.home.add("music"); e.app.home.move("music", 0); e.app.home.finish() }
        Shots.settle(rule, 800)
        Shots.capture(rule, "v17_light_home")
        go(e, Route.Connector("namida"))
        Shots.capture(rule, "v17_light_namida")
        go(e, Route.Sleep)
        Shots.capture(rule, "v17_light_sleep")
    }

    // ───────── v1.7.1

    private fun transitionLeavesTheNavBarVisible(skin: String) {
        val e = makeEnv()
        e.repo.setSetting("skin.id", skin)
        start(e)
        ui { e.app.nav.switchTab(Route.Exercises) }
        Shots.settle(rule, 170)   // a mitad del efecto
        val shot = Shots.capture(rule, "v171_transition_$skin")
        val bmp = android.graphics.BitmapFactory.decodeFile(shot.path)
        // La barra de abajo (últimos ~9 % de la pantalla) tiene que verse completa: hay algo más que el fondo liso.
        val y0 = (bmp.height * 0.915f).toInt()
        val y1 = (bmp.height * 0.975f).toInt()
        val colors = HashSet<Int>()
        for (y in y0 until y1 step 3) for (x in 0 until bmp.width step 3) colors += bmp.getPixel(x, y)
        assertTrue("$skin: la barra de navegación quedó tapada por la transición (${colors.size} colores)", colors.size > 40)
    }

    @Test fun militechTransitionDoesNotCoverTheNavigationBar() = transitionLeavesTheNavBarVisible("acero")

    @Test fun arasakaTransitionDoesNotCoverTheNavigationBar() = transitionLeavesTheNavBarVisible("carmesi")
}
