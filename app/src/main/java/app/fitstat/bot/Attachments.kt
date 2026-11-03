package app.fitstat.bot

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File

/** Convierte lo que el usuario elige (imagen, archivo, audio) en un adjunto guardado dentro de la app. */
object Attachments {
    const val MAX_FILE = 8L * 1024 * 1024

    sealed interface Result {
        class Ok(val attachment: Attachment) : Result
        class Fail(val message: String) : Result
    }

    fun import(context: Context, dir: File, uri: Uri, kind: String, id: String): Result {
        val cr = context.contentResolver
        val (name, size) = displayInfo(cr, uri)
        val mime = cr.getType(uri).orEmpty()
        dir.mkdirs()
        return try {
            when (kind) {
                "image" -> {
                    val out = File(dir, "$id.jpg")
                    if (!saveScaledJpeg(context, uri, out, 1568)) return Result.Fail("No pude abrir esa imagen.")
                    Result.Ok(Attachment(id, "image", name.ifBlank { "imagen.jpg" }, "image/jpeg", out.name, out.length()))
                }
                "audio" -> {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val isMp3 = ext == "mp3" || mime.contains("mpeg") || mime.contains("mp3")
                    val isWav = ext == "wav" || mime.contains("wav")
                    if (!isMp3 && !isWav) return Result.Fail("FITBOT solo entiende audio WAV o MP3. También puedes grabar con el micrófono de la app.")
                    if (size > MAX_FILE) return Result.Fail("El audio pesa demasiado (máximo 8 MB).")
                    val out = File(dir, "$id." + if (isMp3) "mp3" else "wav")
                    copy(cr, uri, out)
                    Result.Ok(
                        Attachment(
                            id, "audio", name.ifBlank { out.name }, if (isMp3) "audio/mpeg" else "audio/wav", out.name, out.length(),
                            seconds = if (isMp3) 0 else wavSeconds(out),
                        ),
                    )
                }
                else -> {
                    if (size > MAX_FILE) return Result.Fail("El archivo pesa demasiado (máximo 8 MB).")
                    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60).ifBlank { "archivo" }
                    val out = File(dir, "${id}_$safe")
                    copy(cr, uri, out)
                    Result.Ok(Attachment(id, "file", name.ifBlank { safe }, mime.ifBlank { guessMime(safe) }, out.name, out.length()))
                }
            }
        } catch (t: Throwable) {
            Result.Fail("No pude leer el archivo.")
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "txt", "log", "md" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private fun displayInfo(cr: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = ""
        var size = 0L
        runCatching {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n).orEmpty()
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        }
        if (name.isBlank()) name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        return name to size
    }

    private fun copy(cr: ContentResolver, uri: Uri, out: File) {
        cr.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: error("sin datos")
    }

    /** Segundos de un WAV a partir de su cabecera. */
    fun wavSeconds(f: File): Int = runCatching {
        f.inputStream().use { s ->
            val h = ByteArray(44)
            if (s.read(h) < 44) return 0
            val rate = (h[24].toInt() and 0xFF) or ((h[25].toInt() and 0xFF) shl 8) or ((h[26].toInt() and 0xFF) shl 16) or ((h[27].toInt() and 0xFF) shl 24)
            val ch = (h[22].toInt() and 0xFF).coerceAtLeast(1)
            val bits = ((h[34].toInt() and 0xFF) or ((h[35].toInt() and 0xFF) shl 8)).coerceAtLeast(8)
            if (rate <= 0) 0 else ((f.length() - 44) / (rate.toLong() * ch * bits / 8)).toInt()
        }
    }.getOrDefault(0)

    /** Reduce la imagen (lado máximo [maxSide]) y la guarda como JPEG, respetando la orientación. */
    private fun saveScaledJpeg(context: Context, uri: Uri, out: File, maxSide: Int): Boolean {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val k = maxOf(w, h) / maxSide.toFloat()
                if (k > 1f) dec.setTargetSize((w / k).toInt().coerceAtLeast(1), (h / k).toInt().coerceAtLeast(1))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val cr = context.contentResolver
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > maxSide * 2) sample *= 2
            val real = BitmapFactory.Options().apply { inSampleSize = sample }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, real) } ?: return false
        }
        // Las imágenes con transparencia se pasan sobre fondo blanco para no quedar negras en JPEG.
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(0xFFFFFFFF.toInt()); drawBitmap(bmp, 0f, 0f, null) }
        out.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return true
    }
}
