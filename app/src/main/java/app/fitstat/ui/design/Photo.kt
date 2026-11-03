package app.fitstat.ui.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.data.Exercise
import app.fitstat.data.Muscle
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.mix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Carga y trata las fotos de ejercicios (duotono, blanco y negro o color). */
object Photos {
    private val cache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun stream(ctx: Context, ex: Exercise, frame: Int): java.io.InputStream? = try {
        if (ex.custom) {
            ex.customImage?.let { File(File(ctx.filesDir, "custom"), it).takeIf { f -> f.exists() }?.inputStream() }
        } else {
            ctx.assets.open("ex/${ex.id}_$frame.jpg")
        }
    } catch (_: Throwable) {
        null
    }

    private fun decode(ctx: Context, ex: Exercise, frame: Int, reqWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream(ctx, ex, frame)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }
        return stream(ctx, ex, frame)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Tabla de 256 tonos de gris -> color ARGB. */
    private fun lut(mode: String, accent: Color): IntArray {
        val ink = Color(0xFF0C0B12)
        val mid = accent.mix(ink, 0.30f)
        val high = accent.mix(Color.White, 0.88f)
        return IntArray(256) { i ->
            val l = ((i - 10) / 225f).coerceIn(0f, 1f)
            val col = when (mode) {
                "mono" -> Color(0xFF08080B).mix(Color(0xFFF4F4F8), l)
                else -> if (l < 0.5f) ink.mix(mid, l / 0.5f) else mid.mix(high, (l - 0.5f) / 0.5f)
            }
            col.toArgb()
        }
    }

    private fun treat(bmp: Bitmap, mode: String, accent: Color): Bitmap {
        if (mode == "color") return bmp
        val table = lut(mode, accent)
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = (r * 54 + g * 183 + b * 19) shr 8
            px[i] = table[l]
        }
        val out = if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    suspend fun load(ctx: Context, ex: Exercise, frame: Int, reqWidth: Int, mode: String, accent: Color): ImageBitmap? {
        val key = "${ex.id}#${ex.customImage.orEmpty()}#$frame#$mode#${if (mode == "duotone") accent.toArgb() else 0}#$reqWidth"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val raw = decode(ctx, ex, frame, reqWidth) ?: return@withContext null
            val out = treat(raw, mode, accent).asImageBitmap()
            cache.put(key, out)
            out
        }
    }

    fun clear() = cache.evictAll()
}

/** Un cuadro de foto de un ejercicio, con fundido al aparecer. */
@Composable
fun ExercisePhoto(
    ex: Exercise,
    modifier: Modifier = Modifier,
    frame: Int = 0,
    reqWidth: Int = 480,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    val ctx = LocalContext.current
    val mode = Fs.ui.photoMode
    val accent = Fs.colors.accent
    val img by produceState<ImageBitmap?>(null, ex.id, ex.customImage, frame, reqWidth, mode, if (mode == "duotone") accent else Color.Unspecified) {
        value = Photos.load(ctx, ex, frame.coerceAtMost((ex.images - 1).coerceAtLeast(0)), reqWidth, mode, accent)
    }
    val a by animateFloatAsState(if (img != null) 1f else 0f, Fs.motion.tween(260), label = "photoIn")
    Box(modifier.background(Fs.colors.surface3)) {
        val bmp = img
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize().alpha(a), contentScale = contentScale, alignment = alignment)
        } else if (ex.images == 0) {
            ExercisePlaceholder(ex, Modifier.fillMaxSize())
        }
    }
}

/** Foto que alterna entre los dos cuadros del movimiento (inicio y final). */
@Composable
fun ExerciseAnimated(
    ex: Exercise,
    modifier: Modifier = Modifier,
    reqWidth: Int = 640,
    intervalMs: Long = 1300,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var frame by remember { mutableIntStateOf(0) }
    val m = Fs.motion
    LaunchedEffect(ex.id, m.enabled) {
        if (ex.images > 1 && m.enabled) {
            while (true) {
                delay(intervalMs)
                frame = 1 - frame
            }
        }
    }
    Crossfade(frame, modifier, animationSpec = m.tween(520), label = "frames") { f ->
        ExercisePhoto(ex, Modifier.fillMaxSize(), frame = f, reqWidth = reqWidth, contentScale = contentScale)
    }
}

/** Miniatura cuadrada/redonda de un ejercicio. */
@Composable
fun ExerciseThumb(ex: Exercise, size: Dp, modifier: Modifier = Modifier, shape: Shape = Fs.shapes.chip, frame: Int = 0) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 2).roundToPx() }
    ExercisePhoto(ex, modifier.size(size).clip(shape), frame = frame, reqWidth = px.coerceIn(160, 640))
}

/** Si el ejercicio no trae foto (ejercicios propios), se dibuja el músculo principal. */
@Composable
fun ExercisePlaceholder(ex: Exercise, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val levels = HashMap<Muscle, Int>().apply {
        ex.primary.forEach { put(it, 2) }
        ex.secondary.forEach { putIfAbsent(it, 1) }
    }
    val back = ex.primary.any { it.isBack && !it.isFront } && ex.primary.none { it.isFront && !it.isBack }
    Box(modifier.background(c.surface2), contentAlignment = Alignment.Center) {
        MuscleFigure(levels, back = back, modifier = Modifier.fillMaxSize().let { it }, padding = 0.1f)
    }
}
