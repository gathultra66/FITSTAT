package app.fitstat

import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnPlatform
import app.fitstat.conn.HealthAvail
import app.fitstat.conn.MusicPlayer
import app.fitstat.conn.MusicState
import app.fitstat.conn.VolumeControl
import app.fitstat.data.ScreenEvent
import app.fitstat.data.SleepSession

// Dobles de prueba de los conectores: un reproductor, un volumen y un teléfono de mentira que cuentan lo que les piden.

/** Un reproductor de mentira que cuenta lo que le piden. */
internal class FakePlayer(playing: Boolean = false, connected: Boolean = true, source: String = "") : MusicPlayer {
    var s = MusicState(connected = connected, playing = playing, title = if (connected) "Canción" else "", artist = "Artista", source = source)
    override val state: MusicState get() = s
    var plays = 0
    var pauses = 0
    var nexts = 0
    var prevs = 0
    var syncs = 0
    /** Cuántas órdenes de pausa / play ignora la app antes de hacer caso (como una app que se está acomodando). */
    var ignorePauses = 0
    var ignorePlays = 0
    /** Lo que de verdad pasa en la app cuando FITSTAT vuelve a preguntar (para simular avisos que se perdieron). */
    var onSync: (() -> Unit)? = null
    private fun set(p: Boolean) { s = MusicState(s.connected, p, s.title, s.artist, s.album, s.art, s.artKey, s.durationMs, s.source) }
    override fun play() { plays++; if (ignorePlays > 0) ignorePlays-- else set(true) }
    override fun pause() { pauses++; if (ignorePauses > 0) ignorePauses-- else set(false) }
    override fun sync() { syncs++; onSync?.invoke() }
    override fun next() { nexts++ }
    override fun previous() { prevs++ }
    /** El usuario toca play o pausa él mismo. */
    fun userSets(p: Boolean) = set(p)
    fun close() { s = MusicState.None }
    /** Ahora la sesión es de otra app (por ejemplo, la que se pausó se cerró y quedó otra). */
    fun switchTo(source: String, playing: Boolean) { s = MusicState(true, playing, "Otra", "Otro", source = source) }
}

internal class FakeVolumeLogged(max: Int = 15, start: Int = 12) : VolumeControl {
    override val max = max
    private var v = start
    val history = arrayListOf(start)
    override var level: Int
        get() = v
        set(x) { v = x.coerceIn(0, max); history += v }
}

internal class FakePlatform : ConnPlatform {
    var installedPkgs: Set<String> = emptySet()
    var notif = false
    var usageOk = true
    var usage = false
    var health = HealthAvail.Unavailable
    var granted = false
    var healthSleep: List<SleepSession> = emptyList()
    var healthError: Throwable? = null
    var events: List<ScreenEvent> = emptyList()
    val opened = ArrayList<ConnOpen>()
    var healthReads = 0
    var screenReads = 0

    override fun installed(pkg: String) = pkg in installedPkgs
    override fun notificationAccess() = notif
    override fun usageSupported() = usageOk
    override fun usageAccess() = usage
    override fun healthAvail() = health
    override suspend fun healthGranted() = granted
    override suspend fun readHealthSleep(fromMs: Long, toMs: Long): List<SleepSession> {
        healthReads++
        healthError?.let { throw it }
        return healthSleep
    }
    override suspend fun screenEvents(fromMs: Long, toMs: Long): List<ScreenEvent> {
        screenReads++
        return events
    }
    override fun open(what: ConnOpen): Boolean { opened += what; return true }
}
