package app.fitstat.conn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer

/**
 * De dónde sale el audio para las animaciones de la música. Es una interfaz para poder probar la lógica sin Android.
 */
interface AudioTap {
    /** El usuario dio el permiso de audio. */
    fun permitted(): Boolean

    /** Empieza a leer lo que suena en el teléfono. false si el sistema no lo deja. */
    fun start(): Boolean

    fun stop()

    /** Llena [out] con la magnitud de cada banda (graves → agudos). false si no hay lectura. */
    fun read(out: FloatArray): Boolean
}

/**
 * Lee la mezcla de audio del teléfono con el Visualizer de Android (sesión global). Necesita el permiso de audio y
 * no todos los teléfonos lo permiten ni todas las versiones de Android lo entregan: si falla, la app usa el ritmo aproximado.
 * No graba nada ni guarda audio: solo mide qué tan fuerte suena cada rango de frecuencias.
 */
class VisualizerTap(private val ctx: Context) : AudioTap {
    private var viz: Visualizer? = null
    private var buf = ByteArray(0)

    override fun permitted(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun start(): Boolean {
        if (viz != null) return true
        if (!permitted()) return false
        return try {
            val v = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = 512.coerceIn(range[0], range[1])
            v.enabled = true
            viz = v
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun stop() {
        val v = viz ?: return
        viz = null
        runCatching { v.enabled = false }
        runCatching { v.release() }
    }

    override fun read(out: FloatArray): Boolean {
        val v = viz ?: return false
        return try {
            val n = v.captureSize
            if (buf.size != n) buf = ByteArray(n)
            if (v.getFft(buf) != Visualizer.SUCCESS) false else Rhythm.bandsFromFft(buf, v.samplingRate, out)
        } catch (_: Throwable) {
            false
        }
    }
}
