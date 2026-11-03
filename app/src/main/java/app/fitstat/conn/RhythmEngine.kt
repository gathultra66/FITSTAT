package app.fitstat.conn

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fitstat.data.Prefs
import kotlin.math.min

/** De dónde sale el ritmo que se está mostrando. */
enum class RhythmMode { Off, Real, Approx }

/** Por qué el ritmo es aproximado (para decírselo al usuario con honestidad). */
enum class RhythmWhy(val label: String) {
    None(""),
    Chosen("Elegiste el ritmo aproximado."),
    NoPermission("Falta el permiso de audio."),
    Blocked("El sistema no está entregando el audio de la música a FITSTAT."),
    Unsupported("Este teléfono no permite leer el audio."),
}

/**
 * El ritmo de la música para las animaciones. Lee el audio del teléfono (si hay permiso y el sistema lo deja) o, si no,
 * produce un ritmo aproximado a un tempo fijo. Lo mueve [step] una vez por cuadro mientras haya algo que lo use ([attach]).
 * El resultado está en [frame] (se lee al dibujar, sin volver a componer nada).
 */
@Stable
class RhythmEngine(
    private val prefs: () -> Prefs,
    private val music: () -> MusicState,
    /** El conector de la música está listo (Namida conectada con sus permisos). */
    private val ready: () -> Boolean,
    private val tap: AudioTap? = null,
) {
    var frame: RhythmFrame by mutableStateOf(RhythmFrame.Idle)
        private set
    var mode: RhythmMode by mutableStateOf(RhythmMode.Off)
        private set
    var why: RhythmWhy by mutableStateOf(RhythmWhy.None)
        private set

    /** El usuario dio el permiso de audio (se actualiza con [refreshPermission]). */
    var permission: Boolean by mutableStateOf(tap?.permitted() == true)
        private set

    /**
     * Cuánto se ven las animaciones (0 = apagadas, 1 = normal). Sube al sonar la música y, si el ajuste «Desvanecer» está
     * encendido, baja poco a poco al pausarla o terminarla (en vez de cortarse).
     */
    var fadeLevel: Float by mutableFloatStateOf(0f)
        private set

    /** Color dominante de la carátula (ARGB), o null si no hay. Lo pone la interfaz cuando cambia la carátula. */
    var coverColor: Int? by mutableStateOf(null)

    private var consumers by mutableIntStateOf(0)
    private var previews by mutableIntStateOf(0)

    private val analyzer = RhythmAnalyzer()
    private val raw = FloatArray(Rhythm.BARS)
    private var tapOn = false
    private var last = -1f
    private var clock = 0f
    private var blockedUntil = -1f

    val supportsReal: Boolean get() = tap != null

    /** La carátula de lo que suena. */
    val art: android.graphics.Bitmap? get() = music().art

    fun refreshPermission() {
        permission = tap?.permitted() == true
    }

    /** Algo que se dibuja con el ritmo se muestra: mientras haya alguno, el motor trabaja. Devuelve cómo soltarlo. */
    fun attach(preview: Boolean = false): () -> Unit {
        if (preview) previews++ else consumers++
        var released = false
        return {
            if (!released) {
                released = true
                if (preview) previews-- else consumers--
            }
        }
    }

    private val enabled: Boolean get() = prefs().bool("fx.enabled")

    /** Hay quien lo necesita y las animaciones están encendidas. */
    val wanted: Boolean get() = enabled && (consumers > 0 || previews > 0)

    private val playing: Boolean get() = ready() && music().let { it.connected && it.playing }

    /** Hay algo que animar: la música suena, es una vista previa, o el usuario pidió fondo suave con la música en pausa. */
    val live: Boolean
        get() = wanted && (playing || previews > 0 || (prefs().bool("fx.idle") && ready() && consumers > 0))

    /** Ya no queda nada que dibujar (el motor puede dormir hasta que [live] vuelva a ser true). */
    val resting: Boolean get() = frame.quiet && fadeLevel <= 0f

    fun begin() {
        last = -1f
        analyzer.reset()
        blockedUntil = -1f
    }

    fun end() {
        stopTap()
        frame = RhythmFrame.Idle
        mode = RhythmMode.Off
        fadeLevel = 0f
    }

    private fun stopTap() {
        if (tapOn) {
            tap?.stop()
            tapOn = false
        }
    }

    /** Un cuadro. [nanos]: la hora del cuadro (la del reloj de animación). */
    fun step(nanos: Long) {
        val p = prefs()
        val now = nanos / 1_000_000_000f
        if (last < 0f) last = now
        val dt = now - last
        val minDt = when (p.str("fx.quality")) { "low" -> 1f / 20f; "high" -> 0f; else -> 1f / 40f }
        if (dt < minDt - 0.004f) return
        last = now
        val d = dt.coerceIn(0.001f, 0.1f)
        clock += d
        if (!live) {
            stopTap()
            val fadeOut = p.bool("fx.fade_out") && fadeLevel > 0f
            if (fadeOut) {
                // Se apagan poco a poco: el nivel baja en [fx.fade_secs] y el movimiento se calma mientras tanto.
                val secs = p.float("fx.fade_secs").coerceIn(0.5f, 6f)
                fadeLevel = (fadeLevel - d / secs).coerceAtLeast(0f)
                frame = frame.faded(d, clock, tau = FADE_CALM)
            } else {
                fadeLevel = 0f
                frame = frame.faded(d, clock)
            }
            if (fadeLevel <= 0f && frame.quiet) {
                mode = RhythmMode.Off
                frame = RhythmFrame.Idle
            }
            return
        }
        fadeLevel = min(1f, fadeLevel + d / FADE_IN)
        val sens = p.int("fx.sens") / 100f
        val chosenApprox = p.str("fx.source") == "approx"
        val wantReal = playing && !chosenApprox
        var f: RhythmFrame? = null
        var reason = RhythmWhy.None
        if (chosenApprox) {
            reason = RhythmWhy.Chosen
        } else if (wantReal) {
            val t = tap
            when {
                t == null -> reason = RhythmWhy.Unsupported
                !t.permitted() -> { permission = false; reason = RhythmWhy.NoPermission }
                clock < blockedUntil -> reason = RhythmWhy.Blocked
                else -> {
                    permission = true
                    if (!tapOn) tapOn = t.start()
                    if (tapOn && t.read(raw)) {
                        val r = analyzer.update(raw, d, clock, sens)
                        if (analyzer.silentFor > SILENT_LIMIT) {
                            // El sistema entrega ceros: mejor el ritmo aproximado un rato y volver a intentar.
                            blockedUntil = clock + RETRY
                            analyzer.reset()
                            reason = RhythmWhy.Blocked
                        } else {
                            f = r
                        }
                    } else {
                        blockedUntil = clock + RETRY
                        stopTap()
                        reason = RhythmWhy.Blocked
                    }
                }
            }
        }
        if (f != null) {
            mode = RhythmMode.Real
            why = RhythmWhy.None
        } else {
            stopTap()
            f = RhythmSim.frameAt(clock, p.int("fx.bpm"), sens, calm = !playing && previews == 0)
            mode = RhythmMode.Approx
            why = reason
        }
        frame = f
    }

    companion object {
        /** Segundos que tarda en verse del todo al empezar a sonar la música. */
        const val FADE_IN = 0.3f

        /** Constante de tiempo con la que se calma el movimiento mientras se desvanece. */
        const val FADE_CALM = 0.8f

        /** Curva suave del desvanecido (sin corte al inicio ni al final). */
        fun smooth(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Segundos sin sonido antes de dar por bloqueado el audio. */
        const val SILENT_LIMIT = 2.5f

        /** Segundos de ritmo aproximado antes de volver a intentar leer el audio. */
        const val RETRY = 8f
    }
}
