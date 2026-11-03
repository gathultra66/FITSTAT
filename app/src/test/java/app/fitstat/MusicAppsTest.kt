package app.fitstat

import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Connectors
import app.fitstat.conn.MusicApps
import app.fitstat.conn.MusicRestCoordinator
import app.fitstat.conn.musicOpen
import app.fitstat.data.Repo
import app.fitstat.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** v1.8.2: Spotify y YouTube Music se conectan igual que Namida, cada uno con sus ajustes. */
class MusicAppsTest {
    private fun newRepo(vararg s: Pair<String, String>): Repo {
        val r = Repo(Files.createTempDirectory("fitstat-music").toFile(), CoroutineScope(SupervisorJob() + Dispatchers.Default), { 1_000_000L }) { java.time.ZoneId.of("UTC") }
        for ((k, v) in s) r.setSetting(k, v)
        return r
    }

    @Test fun appsAndPackages() {
        assertEquals(listOf("namida", "spotify", "ytmusic", "namstat"), MusicApps.ids)
        assertEquals("spotify", MusicApps.byPackage("com.spotify.music")?.id)
        assertEquals("ytmusic", MusicApps.byPackage("com.google.android.apps.youtube.music")?.id)
        assertEquals("namida", MusicApps.byPackage("com.msob7y.namida.debug")?.id)
        assertEquals(null, MusicApps.byPackage("com.example.player"))
        assertEquals(ConnOpen.Spotify, musicOpen("spotify"))
        assertEquals(ConnOpen.YtMusic, musicOpen("ytmusic"))
        assertEquals(ConnOpen.Namida, musicOpen("namida"))
    }

    @Test fun everyMusicAppHasItsSettings() {
        for (id in MusicApps.ids) {
            for (k in listOf("enabled", "pause_rest", "resume_rest", "fade", "fade_start")) assertNotNull("conn.$id.$k", Settings.byKey["conn.$id.$k"])
            assertEquals("false", Settings.byKey["conn.$id.enabled"]!!.default)
            assertEquals("true", Settings.byKey["conn.$id.pause_rest"]!!.default)
        }
    }

    @Test fun statusPerApp() = runTest {
        val pf = FakePlatform()
        val repo = newRepo()
        val hub = ConnectorHub(repo, backgroundScope, pf, FakePlayer(), FakeVolumeLogged())
        assertEquals(ConnStatus.Off, hub.status("spotify"))
        assertEquals(ConnStatus.Off, hub.musicStatus())
        hub.enable("spotify")
        assertEquals("sin la app", ConnStatus.Missing, hub.status("spotify"))
        assertEquals("No encuentro Spotify instalada en este teléfono.", hub.missing("spotify"))
        pf.installedPkgs = setOf("com.spotify.music")
        hub.refresh()
        assertEquals(ConnStatus.NeedsAccess, hub.status("spotify"))
        assertTrue(hub.missing("spotify")!!.contains("Spotify"))
        assertEquals(ConnStatus.NeedsAccess, hub.musicStatus())
        pf.notif = true
        hub.refresh()
        assertEquals(ConnStatus.Ready, hub.status("spotify"))
        assertEquals(ConnStatus.Off, hub.status("ytmusic"))
        assertEquals(listOf("spotify"), hub.musicReadyIds())
        assertTrue(hub.musicReady())
        assertEquals("Spotify", hub.musicName())
        // YouTube Music instalado pero sin activar no cuenta
        pf.installedPkgs += "com.google.android.apps.youtube.music"
        hub.refresh()
        assertEquals(ConnStatus.Off, hub.status("ytmusic"))
        hub.enable("ytmusic")
        assertEquals(listOf("spotify", "ytmusic"), hub.musicReadyIds())
        // cada una pide el permiso en el mismo lugar
        pf.notif = false
        hub.refresh()
        assertTrue(hub.requestAccess("ytmusic"))
        assertEquals(ConnOpen.NotificationAccess, pf.opened.last())
    }

    @Test fun theCurrentAppFollowsWhatPlays() = runTest {
        val pf = FakePlatform().apply { installedPkgs = setOf("com.spotify.music", "com.google.android.apps.youtube.music"); notif = true }
        val player = FakePlayer(playing = true, source = "ytmusic")
        val hub = ConnectorHub(newRepo("conn.spotify.enabled" to "true", "conn.ytmusic.enabled" to "true"), backgroundScope, pf, player, FakeVolumeLogged())
        hub.refresh()
        assertEquals("ytmusic", hub.musicCurrent())
        assertEquals("YouTube Music", hub.musicName())
        player.s = app.fitstat.conn.MusicState(true, true, "x", "y", source = "spotify")
        assertEquals("spotify", hub.musicCurrent())
        player.close()
        assertEquals("sin música sonando, la primera lista", "spotify", hub.musicCurrent())
    }

    @Test fun disconnectingOneKeepsTheOther() = runTest {
        val pf = FakePlatform().apply { installedPkgs = setOf("com.spotify.music", "com.msob7y.namida"); notif = true }
        val hub = ConnectorHub(newRepo("conn.spotify.enabled" to "true", "conn.namida.enabled" to "true"), backgroundScope, pf, FakePlayer(), FakeVolumeLogged())
        hub.refresh()
        assertEquals(listOf("namida", "spotify"), hub.musicReadyIds())
        hub.disconnect("spotify")
        assertEquals(listOf("namida"), hub.musicReadyIds())
        assertFalse(hub.enabled("spotify"))
        assertTrue(hub.enabled("namida"))
    }

    // ───────── El descanso usa los ajustes de la app que sonaba

    @Test fun restUsesTheSettingsOfTheAppThatWasPlaying() = runTest(StandardTestDispatcher()) {
        val repo = newRepo("conn.spotify.enabled" to "true", "conn.spotify.fade" to "0", "conn.ytmusic.enabled" to "true", "conn.ytmusic.pause_rest" to "false")
        val spotify = FakePlayer(playing = true, source = "spotify")
        val vol = FakeVolumeLogged()
        val c = MusicRestCoordinator(spotify, vol, { repo.prefs }, TestScope(testScheduler))
        c.onRestStart()
        assertEquals("Spotify se pausa", 1, spotify.pauses)
        c.onRestEnd()
        runCurrent()
        assertEquals("y vuelve", 1, spotify.plays)

        // YouTube Music tiene «pausar» apagado: no se toca
        val yt = FakePlayer(playing = true, source = "ytmusic")
        val c2 = MusicRestCoordinator(yt, vol, { repo.prefs }, TestScope(testScheduler))
        c2.onRestStart()
        assertEquals(0, yt.pauses)
        assertFalse(c2.pausedByUs)

        // una app desconectada tampoco se toca, aunque suene
        val off = FakePlayer(playing = true, source = "namida")
        val c3 = MusicRestCoordinator(off, vol, { repo.prefs }, TestScope(testScheduler))
        c3.onRestStart()
        assertEquals(0, off.pauses)
    }

    @Test fun anAppWithoutSourceCountsAsNamida() = runTest(StandardTestDispatcher()) {
        val repo = newRepo("conn.namida.enabled" to "true")
        val p = FakePlayer(playing = true)
        val c = MusicRestCoordinator(p, FakeVolumeLogged(), { repo.prefs }, TestScope(testScheduler))
        c.onRestStart()
        assertEquals(1, p.pauses)
    }
}
