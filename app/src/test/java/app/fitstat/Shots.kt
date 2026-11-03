package app.fitstat

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** Utilidades para sacar capturas PNG de la UI dentro de Robolectric (gráficos NATIVE). */
object Shots {
    val dir: File = File(System.getProperty("fitstat.shots") ?: "shots").apply { mkdirs() }

    /** Avanza el reloj de animaciones [ms] milisegundos (de a fotograma) y deja que Android dibuje. */
    fun settle(rule: ComposeRule, ms: Long = 600) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (t < ms) {
            rule.mainClock.advanceTimeBy(16)
            t += 16
        }
        // Unos cuadros más para que Android alcance a dibujar el último estado.
        repeat(6) {
            rule.mainClock.advanceTimeBy(16)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        }
    }

    fun capture(rule: ComposeRule, name: String): File {
        val activity: Activity = rule.activity
        val view = activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        var status = -1
        PixelCopy.request(activity.window, bmp, { status = it }, Handler(Looper.getMainLooper()))
        shadowOf(Looper.getMainLooper()).idle()
        check(status == PixelCopy.SUCCESS) { "PixelCopy falló: $status" }
        val out = File(dir, "$name.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    fun shot(rule: ComposeRule, name: String, ms: Long = 600): File {
        settle(rule, ms)
        return capture(rule, name)
    }
}
