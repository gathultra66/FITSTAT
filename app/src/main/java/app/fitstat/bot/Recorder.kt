package app.fitstat.bot

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

/**
 * Graba la voz del usuario como WAV (16 kHz, mono, 16 bits), el formato que aceptan los modelos con audio.
 * Máximo 2 minutos. Necesita el permiso del micrófono.
 */
@Stable
class Recorder(private val dir: File) {
    var recording by mutableStateOf(false)
        private set
    var seconds by mutableIntStateOf(0)
        private set

    /** Niveles recientes (0..1) para dibujar la onda mientras se graba. */
    val levels = mutableStateListOf<Float>()

    private var job: Job? = null
    private var record: AudioRecord? = null
    private var pcm: File? = null
    private var samples = 0L

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onLimit: () -> Unit = {}): Boolean {
        if (recording) return true
        val rate = RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
        } catch (_: Throwable) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        dir.mkdirs()
        val raw = File(dir, "rec_${System.nanoTime()}.pcm")
        pcm = raw
        record = rec
        samples = 0
        seconds = 0
        levels.clear()
        recording = true
        rec.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(1600)
            val bytes = ByteArray(buf.size * 2)
            raw.outputStream().buffered().use { out ->
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) break
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = buf[i].toInt()
                        sum += v.toDouble() * v
                        bytes[i * 2] = (v and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    out.write(bytes, 0, n * 2)
                    samples += n
                    val level = (sqrt(sum / n) / 6000.0).coerceIn(0.0, 1.0).toFloat()
                    withContext(Dispatchers.Main) {
                        seconds = (samples / rate).toInt()
                        levels.add(level)
                        if (levels.size > BARS) levels.removeAt(0)
                    }
                    if (samples >= MAX_SECONDS.toLong() * rate) {
                        withContext(Dispatchers.Main) { onLimit() }
                        break
                    }
                }
            }
        }
        return true
    }

    /** Termina la grabación y devuelve el adjunto (o null si no se grabó nada). */
    suspend fun finish(id: String): Attachment? {
        val raw = pcm
        stopHardware()
        job?.join()
        job = null
        if (raw == null || !raw.exists() || raw.length() < 3200) {
            raw?.delete()
            return null
        }
        return withContext(Dispatchers.IO) {
            val out = File(dir, "$id.wav")
            writeWav(raw, out)
            raw.delete()
            Attachment(id, "audio", "Nota de voz.wav", "audio/wav", out.name, out.length(), seconds = (samples / RATE).toInt().coerceAtLeast(1))
        }
    }

    fun cancel() {
        val raw = pcm
        stopHardware()
        job?.cancel()
        job = null
        raw?.delete()
    }

    private fun stopHardware() {
        recording = false
        record?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        record = null
    }

    private fun writeWav(pcmFile: File, out: File) {
        val len = pcmFile.length()
        RandomAccessFile(out, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            raf.write(le32((36 + len).toInt()))
            raf.writeBytes("WAVEfmt ")
            raf.write(le32(16))
            raf.write(le16(1))
            raf.write(le16(1))
            raf.write(le32(RATE))
            raf.write(le32(RATE * 2))
            raf.write(le16(2))
            raf.write(le16(16))
            raf.writeBytes("data")
            raf.write(le32(len.toInt()))
            pcmFile.inputStream().use { input ->
                val b = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(b)
                    if (n <= 0) break
                    raf.write(b, 0, n)
                }
            }
        }
    }

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    companion object {
        const val RATE = 16000
        const val MAX_SECONDS = 120
        const val BARS = 36
    }
}
