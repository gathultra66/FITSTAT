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

    /**
     * Vuelve a preguntarle al sistema qué suena. Con FITSTAT en segundo plano Android puede congelar la app y algunos
     * avisos de la sesión de reproducción se pierden, así que antes de pausar o reanudar se mira de nuevo.
     */
    fun sync() {}
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

    /**
     * La música que FITSTAT pausó en este descanso: id del conector (vacío = ninguna) y a qué hora termina el descanso.
     * Se guarda a disco para que, si Android cierra FITSTAT durante el descanso, la alarma del fin pueda reanudarla igual.
     */
    var pausedSource: String
    var pausedUntil: Long

    class Memory : FadeStore {
        override var pendingTarget: Int = 0
        override var pausedSource: String = ""
        override var pausedUntil: Long = 0L
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

    /** Revisa que la pausa o el play que se mandó sí se haya hecho (y lo repite si la app no hizo caso). */
    private var checkJob: Job? = null

    /**
     * Empieza el descanso: pausa la música si estaba sonando. [endsAt] es la hora (reloj del teléfono) en que termina;
     * se guarda junto con la app pausada para poder reanudarla aunque Android cierre FITSTAT a media pausa.
     */
    fun onRestStart(endsAt: Long = 0L) {
        val p = prefs()
        if (pausedByUs) {
            // Descanso alargado o vuelto a empezar: solo se mueve la hora guardada.
            if (endsAt > 0L) store.pausedUntil = endsAt
            return
        }
        // Un descanso nuevo: si se estaba revisando el play del anterior, ya no hay que insistir.
        checkJob?.cancel()
        // Lo guardado de un descanso anterior ya no vale (por ejemplo, si la app se cerró a la mitad).
        clearSaved()
        // Lo que se sabe de la música puede estar viejo si FITSTAT estuvo en segundo plano: se pregunta de nuevo.
        player.sync()
        val s = player.state
        val id = idOf(s)
        if (!on(p, id) || !p.bool("conn.$id.pause_rest")) return
        finishFade(restore = true)
        if (!s.connected || !s.playing) return
        pausedByUs = true
        pausedSource = id
        store.pausedSource = id
        store.pausedUntil = endsAt
        player.pause()
        confirm(wantPlaying = false, id = id)
    }

    fun onRestEnd() {
        if (!pausedByUs) return
        pausedByUs = false
        clearSaved()
        val p = prefs()
        val id = pausedSource
        if (!on(p, id) || !p.bool("conn.$id.resume_rest")) return
        player.sync()
        val s = player.state
        // Si la sesión ya no está, o tú le diste play durante el descanso, no hay nada que reanudar.
        if (!s.connected || s.playing) return
        // Si ahora la sesión es de otra app (la que se pausó se cerró), no se le da play a esa otra.
        if (idOf(s) != id) return
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
            confirm(wantPlaying = true, id = id)
            return
        }
        val target = volume.level
        val start = (target * startPct / 100.0).roundToInt().coerceAtLeast(if (startPct > 0) 1 else 0)
        if (secs <= 0 || target <= 1 || start >= target) {
            player.play()
            confirm(wantPlaying = true, id = id)
            return
        }
        fading = true
        fadeTarget = target
        store.pendingTarget = target
        fadeJob = scope.launch { fade(start, target, secs * 1000L) }
        confirm(wantPlaying = true, id = id)
    }

    private fun clearSaved() {
        if (store.pausedSource.isNotEmpty()) store.pausedSource = ""
        if (store.pausedUntil != 0L) store.pausedUntil = 0L
    }

    /**
     * Algunas apps no hacen caso a una orden si llega justo cuando se están acomodando (o si FITSTAT estaba congelado y
     * la orden se quedó en el camino). Se mira cada [CHECK_STEP_MS] si la música ya quedó como se pidió; si en
     * [CHECK_RETRY_MS] no ha cambiado ni una vez, se vuelve a preguntar al sistema y se repite la orden (hasta 2 veces).
     * En cuanto se ve el cambio se deja de vigilar, así que si después tú le das play o pausa, se respeta.
     */
    private fun confirm(wantPlaying: Boolean, id: String) {
        checkJob?.cancel()
        checkJob = scope.launch {
            var retries = 0
            var waited = 0L
            while (true) {
                delay(CHECK_STEP_MS)
                waited += CHECK_STEP_MS
                // Ya no hace falta: empezó otro descanso, terminó este o se canceló todo.
                if (wantPlaying == pausedByUs) return@launch
                if (player.state.playing == wantPlaying) return@launch
                if (waited < CHECK_RETRY_MS) continue
                player.sync()
                val s = player.state
                if (!s.connected || idOf(s) != id || s.playing == wantPlaying) return@launch
                if (retries >= 2) return@launch
                retries++
                waited = 0L
                if (wantPlaying) player.play() else player.pause()
            }
        }
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
        checkJob?.cancel()
        pausedByUs = false
        clearSaved()
    }

    companion object {
        const val CHECK_STEP_MS = 250L
        const val CHECK_RETRY_MS = 1_500L
    }
}
