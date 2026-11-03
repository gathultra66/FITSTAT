package app.fitstat.conn

import android.graphics.Bitmap
import app.fitstat.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Lo que suena ahora en el reproductor conectado. */
class MusicState(
    /** Hay una sesión de reproducción abierta en el reproductor (Namida está abierto y se puede controlar). */
    val connected: Boolean = false,
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: Bitmap? = null,
    /** Identifica la carátula actual (para no recargarla). */
    val artKey: String = "",
    val durationMs: Long = 0L,
    /** Id del conector de la app que suena (namida, spotify, ytmusic). Vacío = no se sabe (se trata como Namida). */
    val source: String = "",
) {
    val hasTrack: Boolean get() = title.isNotBlank()

    companion object {
        val None = MusicState()
    }
}

/** El reproductor de música que FITSTAT controla (Namida, Spotify o YouTube Music). Es una interfaz para poder probar la lógica sin Android. */
interface MusicPlayer {
    val state: MusicState
    fun play()
    fun pause()
    fun next()
    fun previous()
}

/** El volumen de la música del teléfono, en pasos enteros (0..[max]). */
interface VolumeControl {
    val max: Int
    var level: Int
}

/** Recuerda a disco que FITSTAT está subiendo el volumen, para poder dejarlo bien si la app se cierra a la mitad. */
interface FadeStore {
    /** Volumen al que se estaba subiendo (0 = no hay subida pendiente). */
    var pendingTarget: Int

    class Memory : FadeStore {
        override var pendingTarget: Int = 0
    }
}

/**
 * La música durante el descanso entre series:
 *  - al empezar el descanso, si la música estaba sonando, la pausa (y recuerda que fue FITSTAT quien la pausó);
 *  - al terminar, si sigue pausada por FITSTAT, la reanuda con el volumen bajo y lo sube poco a poco hasta el de antes.
 * Las dos cosas se pueden apagar en los ajustes del conector. Nunca toca la música que tú pausaste o pusiste.
 */
class MusicRestCoordinator(
    private val player: MusicPlayer,
    private val volume: VolumeControl,
    private val prefs: () -> Prefs,
    private val scope: CoroutineScope,
    private val store: FadeStore = FadeStore.Memory(),
    /** El Enlace T-A-T con NAMSTAT: si está, NAMSTAT sube su propio volumen al reanudar (sin tocar el del teléfono). */
    private val link: NamstatLink? = null,
) {
    /** FITSTAT pausó la música y todavía no la ha reanudado. */
    var pausedByUs: Boolean = false
        private set

    /** Hay una subida de volumen en curso. */
    var fading: Boolean = false
        private set

    private var fadeJob: Job? = null
    private var fadeTarget = 0

    /** Al arrancar la app: si se cerró en plena subida de volumen, deja el volumen donde estaba. */
    fun recover() {
        val t = store.pendingTarget
        if (t > 0) {
            runCatching { if (volume.level < t) volume.level = t.coerceAtMost(volume.max) }
            store.pendingTarget = 0
        }
    }

    /** Conector de la app que estaba sonando cuando FITSTAT la pausó (sus ajustes mandan al reanudar). */
    private var pausedSource = Connectors.NAMIDA

    private fun idOf(s: MusicState) = s.source.ifEmpty { Connectors.NAMIDA }

    private fun on(p: Prefs, id: String) = p.bool("conn.$id.enabled")

    fun onRestStart() {
        val p = prefs()
        val s = player.state
        val id = idOf(s)
        if (!on(p, id) || !p.bool("conn.$id.pause_rest")) return
        if (pausedByUs) return
        finishFade(restore = true)
        if (!s.connected || !s.playing) return
        pausedByUs = true
        pausedSource = id
        player.pause()
    }

    fun onRestEnd() {
        if (!pausedByUs) return
        pausedByUs = false
        val p = prefs()
        val id = pausedSource
        if (!on(p, id) || !p.bool("conn.$id.resume_rest")) return
        val s = player.state
        // Si la sesión ya no está, o tú le diste play durante el descanso, no hay nada que reanudar.
        if (!s.connected || s.playing) return
        val secs = p.int("conn.$id.fade")
        val startPct = p.int("conn.$id.fade_start")
        // NAMSTAT: el fundido lo hace NAMSTAT con su volumen. Si el enlace no contesta, se hace como con las demás apps.
        if (id == Connectors.NAMSTAT && link != null && secs > 0) {
            fading = true
            fadeTarget = 0
            fadeJob = scope.launch {
                val r = runCatching { link.call("play_fade", obj("from" to startPct, "secs" to secs)) }.getOrNull()
                if (r != null && r.ok) done() else systemFade(secs, startPct)
            }
            return
        }
        val target = volume.level
        val start = (target * startPct / 100.0).roundToInt().coerceAtLeast(if (startPct > 0) 1 else 0)
        if (secs <= 0 || target <= 1 || start >= target) {
            player.play()
            return
        }
        fading = true
        fadeTarget = target
        store.pendingTarget = target
        fadeJob = scope.launch { fade(start, target, secs * 1000L) }
    }

    /** Reanuda con la subida de volumen del teléfono (lo que se hace con las apps sin enlace). */
    private suspend fun systemFade(secs: Int, startPct: Int) {
        val target = volume.level
        val start = (target * startPct / 100.0).roundToInt().coerceAtLeast(if (startPct > 0) 1 else 0)
        if (secs <= 0 || target <= 1 || start >= target) {
            player.play()
            done()
            return
        }
        fadeTarget = target
        store.pendingTarget = target
        fade(start, target, secs * 1000L)
    }

    private suspend fun fade(start: Int, target: Int, totalMs: Long) {
        volume.level = start
        var last = volume.level
        player.play()
        val per = (totalMs / (target - last).coerceAtLeast(1)).coerceAtLeast(50L)
        var stuck = 0
        while (last < target && stuck < 3) {
            delay(per)
            // Si tocaste el volumen mientras subía, se respeta lo que pusiste.
            if (volume.level != last) break
            volume.level = last + 1
            val now = volume.level
            stuck = if (now > last) 0 else stuck + 1
            last = now
        }
        done()
    }

    private fun done() {
        fading = false
        fadeTarget = 0
        store.pendingTarget = 0
    }

    /** Corta la subida de volumen. Con [restore] deja el volumen en el que tenías antes. */
    private fun finishFade(restore: Boolean) {
        if (!fading) return
        fadeJob?.cancel()
        if (restore && fadeTarget > 0) runCatching { volume.level = fadeTarget }
        done()
    }

    /** Se cancela todo (por ejemplo, al desconectar el conector): no reanuda nada. */
    fun reset() {
        finishFade(restore = true)
        pausedByUs = false
    }
}
