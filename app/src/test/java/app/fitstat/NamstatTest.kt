package app.fitstat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.fitstat.bot.BotHost
import app.fitstat.bot.ToolCtx
import app.fitstat.bot.ToolOutcome
import app.fitstat.bot.allTools
import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Connectors
import app.fitstat.conn.LinkResult
import app.fitstat.conn.MusicRestCoordinator
import app.fitstat.conn.NamstatClient
import app.fitstat.conn.NamstatLink
import app.fitstat.conn.NsLinkState
import app.fitstat.conn.Namstat
import app.fitstat.data.Prefs
import app.fitstat.data.Repo
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.ZoneId

/** NAMSTAT un NAMSTAT de mentira: contesta como el de verdad (ver `tat_bridge.dart`) y anota lo que le piden. */
internal class FakeNamstat : NamstatLink {
    var running = true
    var hasLink = true
    var enabled = true
    var proto = 1
    var libraryReady = true
    val calls = ArrayList<Pair<String, JsonObject>>()
    val launched = ArrayList<Triple<String, JsonObject, Boolean>>()
    var failFade = false

    val track = """{"key":"k1","title":"Canción uno","artist":"Artista","album":"Álbum","year":2019,"genre":"Rock","format":"FLAC","duration_ms":200000,"fav":false,"plays":7}"""
    var stateJson = """{"ok":true,"proto":1,"playing":true,"buffering":false,"position_ms":30000,"duration_ms":200000,"speed":1.0,"shuffle":false,"repeat":"off","volume":0.8,"sleep_ms":null,"sleep_end":false,"source":"FAVORITAS","session":42,"queue_index":0,"queue_size":3,"track":$track,"next":[{"key":"k2","title":"Dos","artist":"B","album":"C","year":2020,"genre":"","format":"MP3","duration_ms":1000,"fav":true,"plays":0}],"library_ready":true,"library_size":120}"""

    fun count(m: String) = calls.count { it.first == m }
    fun last(m: String) = calls.last { it.first == m }.second

    override suspend fun call(method: String, args: JsonObject): LinkResult {
        calls += method to args
        if (!hasLink) return LinkResult.fail(LinkResult.ERR_NO_LINK, "sin enlace")
        if (!running) return LinkResult.fail(LinkResult.ERR_NOT_RUNNING, "NAMSTAT está cerrado")
        val j: String = when (method) {
            "hello" -> """{"ok":true,"proto":$proto,"app":"namstat","version":"1.2.0","enabled":$enabled,"capabilities":["state","queue","fade"]}"""
            "state" -> if (!enabled) """{"ok":false,"error":"disabled","message":"apagado"}""" else stateJson
            "queue" -> """{"ok":true,"index":0,"size":3,"from":0,"items":[$track]}"""
            "playlists" -> """{"ok":true,"favorites":4,"items":[{"id":"p1","name":"Gym","count":12,"duration_ms":2400000}]}"""
            "stats" -> """{"ok":true,"today_ms":600000,"week_ms":[1,2,3,4,5,6,7],"week_hours":2.5,"streak":3,"tracks":120,"albums":10,"artists":8,"favorites":4,"playlists":1,"top":[$track]}"""
            "search" -> """{"ok":true,"total":1,"items":[$track]}"""
            "play_fade" -> if (failFade) """{"ok":false,"error":"empty_queue","message":"No hay cola"}""" else """{"ok":true}"""
            "play_playlist" -> if (!libraryReady) """{"ok":false,"error":"library_not_ready","message":"cargando"}""" else """{"ok":true,"count":12,"name":"Gym"}"""
            else -> """{"ok":true}"""
        }
        val o = Json.parseToJsonElement(j).jsonObject
        return LinkResult(o["ok"]!!.jsonPrimitive.boolean, o["error"]?.jsonPrimitive?.content.orEmpty(), o["message"]?.jsonPrimitive?.content.orEmpty(), o)
    }

    override fun launch(method: String, args: JsonObject, back: Boolean): Boolean { launched += Triple(method, args, back); return true }
}

private class NsHost(override val repo: Repo, val hub: ConnectorHub?) : BotHost {
    override fun currentScreen() = "home"
    override fun navigate(route: Route) {}
    override fun startWorkout(routineId: String?, exerciseIds: List<String>) {}
    override fun workoutActive() = false
    override fun connectors() = hub
}

private fun callTool(host: BotHost, json: String): ToolOutcome =
    allTools.first { it.name == "namstat" }.run(Json.parseToJsonElement(json) as JsonObject, ToolCtx(host))

/** v1.9: el conector de NAMSTAT por el Enlace T-A-T. */
@OptIn(ExperimentalCoroutinesApi::class)
class NamstatTest {
    private fun newRepo() = Repo(Files.createTempDirectory("fitstat-ns").toFile(), CoroutineScope(SupervisorJob() + Dispatchers.Default), { 1_000_000L }) { ZoneId.of("UTC") }

    private fun hubOf(scope: CoroutineScope, link: FakeNamstat, player: FakePlayer = FakePlayer(playing = true, source = Connectors.NAMSTAT), repo: Repo = newRepo(), vararg s: Pair<String, String>): Pair<ConnectorHub, FakePlatform> {
        val pf = FakePlatform().also { it.installedPkgs = setOf("com.namstat.player"); it.notif = true }
        repo.setSetting("conn.namstat.enabled", "true")
        for ((k, v) in s) repo.setSetting(k, v)
        return ConnectorHub(repo, scope, pf, player, FakeVolumeLogged(), link = link) to pf
    }

    // ───────── Conector

    @Test fun namstatIsAMusicConnectorWithItsOwnPackage() {
        assertEquals("NAMSTAT", Connectors.def("namstat")!!.name)
        assertEquals("namstat", Connectors.find("NAMSTAT")!!.id)
        assertEquals("namstat", app.fitstat.conn.MusicApps.byPackage("com.namstat.player")!!.id)
        assertEquals(listOf("com.namstat.player"), Namstat.packages)
        assertEquals(ConnOpenOf("namstat"), ConnOpen.Namstat)
    }

    private fun ConnOpenOf(id: String) = app.fitstat.conn.musicOpen(id)

    @Test fun statusFollowsInstalledAndNotificationAccess() = runTest {
        val link = FakeNamstat()
        val (h, pf) = hubOf(backgroundScope, link)
        assertEquals(ConnStatus.Ready, h.status("namstat"))
        pf.installedPkgs = emptySet(); h.refresh()
        assertEquals(ConnStatus.Missing, h.status("namstat"))
        assertTrue(h.missing("namstat")!!.contains("NAMSTAT"))
        pf.installedPkgs = setOf("com.namstat.player"); pf.notif = false; h.refresh()
        assertEquals(ConnStatus.NeedsAccess, h.status("namstat"))
    }

    @Test fun sharedAudioLinksBecomeArtRequests() {
        val u = app.fitstat.conn.NamstatPackage.artUri("content://media/external/audio/media/1234")
        assertTrue(u, u.startsWith("content://com.namstat.player.tat/art?"))
        val q = u.substringAfter('?').split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals("content://media/external/audio/media/1234", q["u"])
        assertEquals("640", q["size"])
        assertEquals("lo que no es de MediaStore no se toca", "file:///x/cover.jpg", app.fitstat.conn.NamstatPackage.artUri("file:///x/cover.jpg"))
    }

    // ───────── Estado y lecturas

    @Test fun helloAndStateAreParsed() = runTest {
        val link = FakeNamstat()
        val ns = NamstatClient(link, backgroundScope) { 5_000L }
        assertEquals(NsLinkState.Linked, ns.hello())
        assertEquals("1.2.0", ns.version)
        assertTrue(ns.has("queue"))
        val s = ns.refresh()!!
        assertTrue(s.playing)
        assertEquals("Canción uno", s.track!!.title)
        assertEquals(2019, s.track!!.year)
        assertEquals("Rock", s.track!!.genre)
        assertEquals(7, s.track!!.plays)
        assertEquals(0.8, s.volume, 1e-9)
        assertNull(s.sleepMs)
        assertEquals(42, s.session)
        assertEquals("Dos", s.next.single().title)
        assertTrue(s.next.single().fav)
    }

    @Test fun positionAdvancesBetweenPolls() = runTest {
        val link = FakeNamstat()
        var t = 1_000L
        val ns = NamstatClient(link, backgroundScope) { t }
        ns.refresh()
        assertEquals(30_000L, ns.positionNow())
        t += 4_000L
        assertEquals(34_000L, ns.positionNow())
        link.stateJson = link.stateJson.replace("\"playing\":true", "\"playing\":false")
        ns.refresh()
        t += 10_000L
        assertEquals("en pausa no avanza", 30_000L, ns.positionNow())
    }

    @Test fun queuePlaylistsStatsAndSearchAreRead() = runTest {
        val ns = NamstatClient(FakeNamstat(), backgroundScope)
        assertEquals(3, ns.queue()!!.size)
        val pl = ns.playlists()!!
        assertEquals("Gym", pl.items.single().name)
        assertEquals(4, pl.favorites)
        val st = ns.stats()!!
        assertEquals(3, st.streak)
        assertEquals(7, st.weekMs.size)
        assertEquals(2.5, st.weekHours, 1e-9)
        assertEquals("Canción uno", st.top.single().title)
        assertEquals(1, ns.search("can")!!.total)
    }

    @Test fun linkProblemsAreTold() = runTest {
        val link = FakeNamstat()
        val ns = NamstatClient(link, backgroundScope)
        link.running = false
        assertEquals(NsLinkState.Closed, ns.hello())
        assertNull(ns.refresh())
        assertNull("sin estado cuando está cerrado", ns.state)
        link.running = true; link.hasLink = false
        assertEquals(NsLinkState.NoLink, ns.hello())
        link.hasLink = true; link.enabled = false
        assertEquals(NsLinkState.Disabled, ns.hello())
        link.enabled = true; link.proto = 9
        assertEquals("un protocolo más nuevo no se entiende", NsLinkState.OldProto, ns.hello())
    }

    // ───────── Órdenes

    @Test fun commandsAreSentWithTheirArguments() = runTest {
        val link = FakeNamstat()
        val ns = NamstatClient(link, backgroundScope)
        ns.seek(61_000); assertEquals(61000, link.last("seek")["ms"]!!.jsonPrimitive.int)
        ns.volume(0.5); assertEquals("0.5", link.last("volume")["value"]!!.jsonPrimitive.content)
        ns.volume(7.0); assertEquals("el volumen se limita a 1", "1.0", link.last("volume")["value"]!!.jsonPrimitive.content)
        ns.repeat("one"); assertEquals("one", link.last("repeat")["mode"]!!.jsonPrimitive.content)
        ns.shuffle(true); assertTrue(link.last("shuffle")["on"]!!.jsonPrimitive.boolean)
        ns.sleepIn(30); assertEquals(30, link.last("sleep")["minutes"]!!.jsonPrimitive.int)
        ns.sleepAtEnd(); assertTrue(link.last("sleep")["end"]!!.jsonPrimitive.boolean)
        ns.sleepCancel(); assertTrue(link.last("sleep")["cancel"]!!.jsonPrimitive.boolean)
        ns.playPlaylist(name = "Gym", shuffle = true)
        assertEquals("Gym", link.last("play_playlist")["name"]!!.jsonPrimitive.content)
        assertTrue(link.last("play_playlist")["shuffle"]!!.jsonPrimitive.boolean)
        ns.enqueue(key = "k1", next = true); assertTrue(link.last("enqueue")["next"]!!.jsonPrimitive.boolean)
        ns.speed(9.0); assertEquals("la velocidad se limita", "3.0", link.last("speed")["value"]!!.jsonPrimitive.content)
        ns.favorite(true); assertNull("sin clave: la canción actual", link.last("favorite")["key"])
        ns.seekIndex(2); assertEquals(2, link.last("seek_index")["index"]!!.jsonPrimitive.int)
    }

    @Test fun closedNamstatIsOpenedWithTheCommand() = runTest {
        val link = FakeNamstat().also { it.running = false }
        val ns = NamstatClient(link, backgroundScope)
        val r = ns.playPlaylist(name = "Gym")
        assertTrue(r.ok)
        val (m, args, back) = link.launched.single()
        assertEquals("play_playlist", m)
        assertEquals("Gym", args["name"]!!.jsonPrimitive.content)
        assertTrue("vuelve a FITSTAT al terminar", back)
        // el volumen y el aviso de entrenamiento nunca abren NAMSTAT
        link.launched.clear()
        assertFalse(ns.volume(0.4).ok)
        assertFalse(ns.workout(true, "Pierna").ok)
        assertTrue(link.launched.isEmpty())
    }

    // ───────── Fundido con NAMSTAT

    private fun coordinator(scope: CoroutineScope, link: FakeNamstat, player: FakePlayer, vol: FakeVolumeLogged, p: Prefs) =
        MusicRestCoordinator(player, vol, { p }, scope, link = link)

    private val on = mapOf("conn.namstat.enabled" to "true")

    @Test fun resumeUsesNamstatsOwnFadeAndLeavesThePhoneVolumeAlone() = runTest {
        val link = FakeNamstat()
        val player = FakePlayer(playing = true, source = Connectors.NAMSTAT)
        val vol = FakeVolumeLogged()
        val c = coordinator(backgroundScope, link, player, vol, Prefs(on + ("conn.namstat.fade" to "8") + ("conn.namstat.fade_start" to "20")))
        c.onRestStart()
        assertEquals(1, player.pauses)
        player.s = player.s.let { app.fitstat.conn.MusicState(it.connected, false, it.title, it.artist, it.album, it.art, it.artKey, it.durationMs, it.source) }
        c.onRestEnd()
        runCurrent()
        assertEquals(1, link.count("play_fade"))
        assertEquals(20, link.last("play_fade")["from"]!!.jsonPrimitive.int)
        assertEquals(8, link.last("play_fade")["secs"]!!.jsonPrimitive.int)
        assertEquals("el volumen del teléfono no se toca", listOf(12), vol.history)
        assertEquals("NAMSTAT hizo el fundido: FITSTAT no le da play", 0, player.plays)
        assertFalse(c.fading)
    }

    @Test fun ifTheLinkFailsTheFadeFallsBackToThePhoneVolume() = runTest {
        val link = FakeNamstat().also { it.failFade = true }
        val player = FakePlayer(playing = true, source = Connectors.NAMSTAT)
        val vol = FakeVolumeLogged()
        val c = coordinator(backgroundScope, link, player, vol, Prefs(on + ("conn.namstat.fade" to "4") + ("conn.namstat.fade_start" to "20")))
        c.onRestStart()
        c.onRestEnd()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(1, player.plays)
        assertEquals("sube por el volumen del teléfono hasta donde estaba", 12, vol.level)
        assertTrue(vol.history.size > 2)
    }

    @Test fun otherAppsKeepTheirPhoneVolumeFade() = runTest {
        val link = FakeNamstat()
        val player = FakePlayer(playing = true, source = "spotify")
        val vol = FakeVolumeLogged()
        val c = coordinator(backgroundScope, link, player, vol, Prefs(mapOf("conn.spotify.enabled" to "true", "conn.spotify.fade" to "3", "conn.spotify.fade_start" to "20")))
        c.onRestStart(); c.onRestEnd()
        advanceTimeBy(5_000); runCurrent()
        assertEquals("Spotify no usa el enlace", 0, link.count("play_fade"))
        assertEquals(1, player.plays)
    }

    // ───────── Entrenamiento

    @Test fun workoutIsToldToNamstatAndTheListStartsOnlyIfNothingPlays() = runTest {
        val link = FakeNamstat()
        val repo = newRepo()
        val quiet = FakePlayer(playing = false, source = Connectors.NAMSTAT)
        val (h, _) = hubOf(backgroundScope, link, quiet, repo, "conn.namstat.workout_list" to "Gym", "conn.namstat.workout_shuffle" to "true")
        var name by mutableStateOf<String?>(null)
        h.watchWorkout { name }
        runCurrent()
        name = "Pierna"; Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(1, link.count("workout"))
        assertTrue(link.last("workout")["on"]!!.jsonPrimitive.boolean)
        assertEquals("Pierna", link.last("workout")["name"]!!.jsonPrimitive.content)
        assertEquals("Gym", link.last("play_playlist")["name"]!!.jsonPrimitive.content)
        assertTrue(link.last("play_playlist")["shuffle"]!!.jsonPrimitive.boolean)
        name = null; Snapshot.sendApplyNotifications(); runCurrent()
        assertFalse(link.calls.last { it.first == "workout" }.second["on"]!!.jsonPrimitive.boolean)
    }

    @Test fun workoutListDoesNotInterruptMusicThatIsPlaying() = runTest {
        val link = FakeNamstat()
        val (h, _) = hubOf(backgroundScope, link, FakePlayer(playing = true, source = Connectors.NAMSTAT), newRepo(), "conn.namstat.workout_list" to "Gym")
        assertFalse(h.startWorkoutList())
        assertEquals(0, link.count("play_playlist"))
    }

    @Test fun noWorkoutListMeansNothingStarts() = runTest {
        val link = FakeNamstat()
        val (h, _) = hubOf(backgroundScope, link, FakePlayer(playing = false, source = Connectors.NAMSTAT))
        assertFalse(h.startWorkoutList())
        assertEquals(0, link.count("play_playlist"))
    }

    @Test fun workoutSyncCanBeTurnedOff() = runTest {
        val link = FakeNamstat()
        val (h, _) = hubOf(backgroundScope, link, FakePlayer(playing = true, source = Connectors.NAMSTAT), newRepo(), "conn.namstat.workout_sync" to "false")
        var name by mutableStateOf<String?>(null)
        h.watchWorkout { name }
        runCurrent()
        name = "Pierna"; Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(0, link.count("workout"))
    }

    // ───────── FITBOT

    @Test fun botNeedsTheConnectorOn() = runTest {
        val repo = newRepo()
        val pf = FakePlatform().also { it.installedPkgs = setOf("com.namstat.player"); it.notif = true }
        val h = ConnectorHub(repo, backgroundScope, pf, FakePlayer(), FakeVolumeLogged(), link = FakeNamstat())
        val r = callTool(NsHost(repo, h), """{"action":"state"}""")
        assertFalse(r.ok)
        assertTrue(r.json["error"]!!.jsonPrimitive.content.contains("connect_connector"))
    }

    @Test fun botReadsAndControlsNamstat() = runTest {
        val link = FakeNamstat()
        val repo = newRepo()
        val (h, _) = hubOf(backgroundScope, link, repo = repo)
        val host = NsHost(repo, h)
        val st = callTool(host, """{"action":"state"}""")
        assertTrue(st.ok)
        assertEquals("Canción uno", st.json["track"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(80, st.json["volume_percent"]!!.jsonPrimitive.int)
        assertEquals(1, st.json["up_next"]!!.jsonArray.size)
        val pl = callTool(host, """{"action":"playlists"}""")
        assertEquals("Gym", pl.json["playlists"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(callTool(host, """{"action":"stats"}""").ok)
        assertTrue(callTool(host, """{"action":"search","query":"can"}""").ok)
        assertTrue(callTool(host, """{"action":"volume","value":35}""").ok)
        assertEquals("0.35", link.last("volume")["value"]!!.jsonPrimitive.content)
        assertTrue(callTool(host, """{"action":"play_album","query":"Álbum","shuffle":true}""").ok)
        assertEquals("Álbum", link.last("play_album")["name"]!!.jsonPrimitive.content)
        assertTrue(callTool(host, """{"action":"sleep","minutes":20}""").ok)
        assertEquals(20, link.last("sleep")["minutes"]!!.jsonPrimitive.int)
        assertTrue(callTool(host, """{"action":"seek","seconds":90}""").ok)
        assertEquals(90000, link.last("seek")["ms"]!!.jsonPrimitive.int)
        assertTrue(callTool(host, """{"action":"seek_index","index":3}""").ok)
        assertEquals("la cola cuenta desde 1 para el usuario", 2, link.last("seek_index")["index"]!!.jsonPrimitive.int)
    }

    @Test fun botReportsMissingArgumentsAndBadActions() = runTest {
        val repo = newRepo()
        val (h, _) = hubOf(backgroundScope, FakeNamstat(), repo = repo)
        val host = NsHost(repo, h)
        assertFalse(callTool(host, """{"action":"baila"}""").ok)
        assertFalse(callTool(host, """{"action":"play_playlist"}""").ok)
        assertFalse(callTool(host, """{"action":"seek"}""").ok)
        assertFalse(callTool(host, """{"action":"sleep"}""").ok)
        assertFalse(callTool(host, "{}").ok)
    }

    @Test fun botTellsWhenNamstatIsClosedOrTooOld() = runTest {
        val link = FakeNamstat()
        val repo = newRepo()
        val (h, _) = hubOf(backgroundScope, link, repo = repo)
        val host = NsHost(repo, h)
        link.running = false
        assertTrue(callTool(host, """{"action":"state"}""").json["error"]!!.jsonPrimitive.content.contains("cerrado"))
        val play = callTool(host, """{"action":"play_playlist","query":"Gym"}""")
        assertTrue("las órdenes de reproducir abren NAMSTAT", play.ok)
        assertEquals(1, link.launched.size)
        link.running = true; link.hasLink = false
        assertTrue(callTool(host, """{"action":"state"}""").json["error"]!!.jsonPrimitive.content.contains("1.2"))
    }

    @Test fun botSeesNamstatInTheConnectorList() = runTest {
        val repo = newRepo()
        val (h, _) = hubOf(backgroundScope, FakeNamstat(), repo = repo)
        val r = allTools.first { it.name == "list_connectors" }.run(JsonObject(emptyMap()), ToolCtx(NsHost(repo, h)))
        val ns = r.json["connectors"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == "namstat" }
        assertEquals("ready", ns["status"]!!.jsonPrimitive.content)
        assertNotNull(ns["settings"])
    }
}
