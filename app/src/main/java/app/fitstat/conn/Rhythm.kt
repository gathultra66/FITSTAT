package app.fitstat.conn

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Una lectura del ritmo de la música en un instante: cuánta energía hay (en general y en graves, medios y agudos),
 * el latido (1 justo al golpe y baja a 0), el espectro en [Rhythm.BARS] bandas y cuándo fueron los últimos latidos.
 * Todos los valores van de 0 a 1.
 */
class RhythmFrame(
    /** Reloj del ritmo, en segundos. */
    val time: Float,
    val level: Float,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val beat: Float,
    val bars: FloatArray,
    /** Los últimos latidos (el más reciente primero), en el mismo reloj que [time]; [Rhythm.NONE] si no hay. */
    val beats: FloatArray,
    /** true si sale del audio de verdad; false si es el ritmo aproximado. */
    val real: Boolean,
) {
    /** Sin nada que dibujar. */
    val quiet: Boolean get() = level < 0.004f && beat < 0.004f

    companion object {
        val Idle = RhythmFrame(0f, 0f, 0f, 0f, 0f, 0f, FloatArray(Rhythm.BARS), FloatArray(Rhythm.BEATS) { Rhythm.NONE }, false)
    }
}

object Rhythm {
    const val BARS = 32
    const val BEATS = 4
    const val NONE = -100f

    private const val F_MIN = 45f
    private const val F_MAX = 14000f

    /**
     * Pasa el espectro que entrega el sistema (Visualizer.getFft: bytes con signo, [parte real, parte imaginaria] por cada
     * frecuencia) a [out].size bandas repartidas en escala logarítmica, de graves a agudos. Los valores quedan en magnitud
     * lineal (0 a ~180). [samplingRateMilliHz] es la frecuencia de muestreo en milihertz, como la da el sistema.
     */
    fun bandsFromFft(fft: ByteArray, samplingRateMilliHz: Int, out: FloatArray): Boolean {
        val n = fft.size
        if (n < 64 || out.isEmpty()) return false
        val half = n / 2
        val sr = if (samplingRateMilliHz > 8_000_000) samplingRateMilliHz / 1000f else 44100f
        val binW = sr / n
        val fMax = min(F_MAX, sr / 2f * 0.95f)
        val ratio = fMax / F_MIN
        val bands = out.size
        fun mag(k: Int): Float {
            val re = fft[2 * k].toInt().toFloat()
            val im = fft[2 * k + 1].toInt().toFloat()
            return sqrt(re * re + im * im)
        }
        for (i in 0 until bands) {
            val lo = F_MIN * ratio.pow(i / bands.toFloat()) / binW
            val hi = F_MIN * ratio.pow((i + 1) / bands.toFloat()) / binW
            val k0 = ceil(lo).toInt().coerceIn(1, half - 1)
            val k1 = floor(hi).toInt().coerceIn(1, half - 1)
            out[i] = if (k1 >= k0 && hi - lo >= 1f) {
                var s = 0f
                for (k in k0..k1) s += mag(k)
                s / (k1 - k0 + 1)
            } else {
                mag(((lo + hi) / 2f).roundToInt().coerceIn(1, half - 1))
            }
        }
        return true
    }
}

/**
 * Convierte lo que se oye (espectro por bandas) en un [RhythmFrame]: ajusta el volumen solo (lo que suena bajito también se ve),
 * suaviza (sube rápido y baja despacio) y detecta los golpes de los graves.
 */
class RhythmAnalyzer(private val bands: Int = Rhythm.BARS) {
    private val smooth = FloatArray(bands)
    private var peak = FLOOR
    private var bassAvg = 0f
    private var lastBeat = -10f
    private var beatEnv = 0f
    private var warm = false
    private val beatTimes = FloatArray(Rhythm.BEATS) { Rhythm.NONE }

    /** Segundos seguidos sin recibir sonido (todo en cero: el sistema no está entregando audio, o hay silencio). */
    var silentFor = 0f
        private set

    fun reset() {
        smooth.fill(0f)
        peak = FLOOR
        bassAvg = 0f
        lastBeat = -10f
        beatEnv = 0f
        warm = false
        beatTimes.fill(Rhythm.NONE)
        silentFor = 0f
    }

    /** [raw]: magnitudes por banda (ver [Rhythm.bandsFromFft]); [dt]: segundos desde la lectura anterior; [sens]: 0..1. */
    fun update(raw: FloatArray, dt: Float, now: Float, sens: Float): RhythmFrame {
        val d = dt.coerceIn(0.001f, 0.25f)
        var mx = 0f
        for (v in raw) if (v > mx) mx = v
        silentFor = if (mx < SILENCE) silentFor + d else 0f
        // El techo baja despacio: tras un pasaje fuerte, uno suave se ve más bajito, como debe.
        peak = max(max(mx, FLOOR), peak * exp(-d / 5f))
        val gain = 0.55f + 0.9f * sens.coerceIn(0f, 1f)
        val low = max(bands / 5, 1)
        var lowNow = 0f
        for (i in 0 until min(bands, raw.size)) {
            val x = ((raw[i] / peak).coerceIn(0f, 1f).pow(0.85f) * gain).coerceIn(0f, 1f)
            if (i < low) lowNow += x
            val k = if (x > smooth[i]) 1f - exp(-d / 0.035f) else 1f - exp(-d / 0.20f)
            smooth[i] += (x - smooth[i]) * k
        }
        lowNow /= low
        // Al empezar, el promedio arranca en lo que suena (si no, el primer segundo se llenaría de golpes falsos).
        if (!warm) { bassAvg = lowNow; warm = true }
        val avg = bassAvg
        bassAvg += (lowNow - bassAvg) * (1f - exp(-d / 0.9f))
        if (lowNow > avg * 1.28f + 0.05f && lowNow > 0.3f && now - lastBeat > 0.24f) {
            lastBeat = now
            beatEnv = 1f
            for (i in Rhythm.BEATS - 1 downTo 1) beatTimes[i] = beatTimes[i - 1]
            beatTimes[0] = now
        } else {
            beatEnv *= exp(-d / 0.16f)
        }
        return RhythmFrame(
            now, mean(smooth, 0, bands), mean(smooth, 0, low), mean(smooth, low, bands * 3 / 5), mean(smooth, bands * 3 / 5, bands),
            beatEnv, smooth.copyOf(), beatTimes.copyOf(), true,
        )
    }

    private fun mean(a: FloatArray, from: Int, to: Int): Float {
        if (to <= from) return 0f
        var s = 0f
        for (i in from until to) s += a[i]
        return s / (to - from)
    }

    companion object {
        /** Por debajo de esto el ruido de fondo no se amplifica. */
        const val FLOOR = 10f
        const val SILENCE = 0.6f
    }
}

/**
 * El ritmo aproximado: late a un tempo fijo (el que elige el usuario) con bombo en cada tiempo, caja en el 2 y el 4 y platillos
 * entre medias, más una variación lenta para que no se vea mecánico. No escucha la canción: solo es una animación con ritmo.
 * Es una función del tiempo: el mismo [t] da siempre el mismo resultado.
 */
object RhythmSim {
    fun frameAt(t: Float, bpm: Int, sens: Float, calm: Boolean = false): RhythmFrame {
        val b = bpm.coerceIn(40, 220)
        val beatLen = 60f / b
        val ph = t / beatLen
        val idx = floor(ph).toInt()
        val fr = ph - idx
        val inBar = ((idx % 4) + 4) % 4
        val accent = when (inBar) { 0 -> 1f; 2 -> 0.85f; else -> 0.7f }
        val kick = exp(-fr * 6f) * accent
        val snare = if (inBar == 1 || inBar == 3) exp(-fr * 8f) else 0f
        val eighth = ph * 2f
        val fr8 = eighth - floor(eighth)
        val hat = exp(-fr8 * 12f) * (if (floor(eighth).toInt() % 2 != 0) 0.8f else 0.5f)
        val section = (0.74f + 0.26f * sin(t * 0.21f + 1f)).coerceIn(0.4f, 1f)
        val gain = (0.55f + 0.9f * sens.coerceIn(0f, 1f)) * (if (calm) 0.3f else 1f)
        val bars = FloatArray(Rhythm.BARS)
        for (i in bars.indices) {
            val pos = i / (Rhythm.BARS - 1f)
            val wb = exp(-pos * 5f)
            val wm = exp(-(((pos - 0.42f) / 0.2f).pow(2)))
            val wh = ((pos - 0.55f) / 0.4f).coerceIn(0f, 1f)
            val n1 = 0.5f + 0.5f * sin(t * (1.7f + 0.23f * i) + i * 1.9f)
            val n2 = 0.5f + 0.5f * sin(t * (0.9f + 0.11f * i) + i * 0.7f)
            val v = (kick * wb * 1.1f + snare * wm * 0.9f + hat * wh * 0.8f + 0.22f * n1 * n2 * (0.6f + 0.4f * (1f - pos))) * section
            bars[i] = (v * gain).coerceIn(0f, 1f)
        }
        val beats = FloatArray(Rhythm.BEATS) { k -> if (calm) Rhythm.NONE else (idx - k) * beatLen }
        val low = Rhythm.BARS / 5
        return RhythmFrame(
            t, avg(bars, 0, Rhythm.BARS), avg(bars, 0, low), avg(bars, low, Rhythm.BARS * 3 / 5), avg(bars, Rhythm.BARS * 3 / 5, Rhythm.BARS),
            if (calm) 0f else (exp(-fr * 7f) * (if (inBar == 0) 1f else 0.75f)).coerceIn(0f, 1f), bars, beats, false,
        )
    }

    private fun avg(a: FloatArray, from: Int, to: Int): Float {
        var s = 0f
        for (i in from until to) s += a[i]
        return s / max(to - from, 1)
    }
}

/** Una lectura que se va apagando (cuando la música se pausa). */
internal fun RhythmFrame.faded(dt: Float, now: Float, tau: Float = 0.25f): RhythmFrame {
    val k = exp(-dt / tau)
    return RhythmFrame(now, level * k, bass * k, mid * k, treble * k, beat * k, FloatArray(bars.size) { bars[it] * k }, beats, real)
}
