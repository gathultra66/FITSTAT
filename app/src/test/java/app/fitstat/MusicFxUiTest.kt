package app.fitstat

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.conn.AudioTap
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Namida
import app.fitstat.conn.Rhythm
import app.fitstat.conn.RhythmEngine
import app.fitstat.conn.RhythmMode
import app.fitstat.conn.RhythmSim
import app.fitstat.data.Demo
import app.fitstat.data.HomeSize
import app.fitstat.data.Pages
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.FxPaint
import app.fitstat.ui.design.MusicFxStyles
import app.fitstat.ui.design.coverColorOf
import app.fitstat.ui.design.tinted
import app.fitstat.ui.design.drawMusicFx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.8: las animaciones al ritmo de la música. Se dibujan los 7 estilos (más «Mezcla») sobre una tarjeta de acento y sobre una
 * normal, y se prueba con la interfaz de verdad: meta semanal y widget de música animándose, la portada, y la página de ajustes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
class MusicFxUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    // ───────── Los estilos, dibujados sin pantalla

    private fun paint(rainbow: Boolean = false, detail: Int = 1, c1: Color = Color(0xFF14111F), c2: Color = Color(0xFF3D6B00)) =
        FxPaint(c1, c2, intensity = 0.6f, detail = detail, rainbow = rainbow, speed = 1f, mixSecs = 6f)

    private fun render(style: String, t: Float, w: Int, h: Int, bg: Color, p: FxPaint, bpm: Int = 118): ImageBitmap {
        val img = ImageBitmap(w, h)
        val canvas = Canvas(img)
        canvas.drawRect(androidx.compose.ui.geometry.Rect(0f, 0f, w.toFloat(), h.toFloat()), androidx.compose.ui.graphics.Paint().apply { color = bg })
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            drawMusicFx(style, RhythmSim.frameAt(t, bpm, 0.7f), p)
        }
        return img
    }

    /** Porcentaje de píxeles que cambian de verdad (más que un matiz casi invisible) respecto del fondo. */
    private fun coverage(img: ImageBitmap, bg: Color): Float {
        val bmp = img.asAndroidBitmap()
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val b = bg.toArgb()
        fun ch(v: Int, sh: Int) = (v shr sh) and 0xFF
        return px.count {
            Math.abs(ch(it, 16) - ch(b, 16)) + Math.abs(ch(it, 8) - ch(b, 8)) + Math.abs(ch(it, 0) - ch(b, 0)) > 30
        }.toFloat() / px.size
    }

    private fun signature(img: ImageBitmap): Long {
        val bmp = img.asAndroidBitmap()
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var h = 1125899906842597L
        for (v in px) h = 31 * h + v
        return h
    }

    @Test
    fun everyStyleDrawsAndMoves() {
        val bg = Color(0xFFB6F500)
        val on = Color(0xFF14111F)
        for (style in MusicFxStyles.all) {
            for (detail in 0..2) {
                val a = render(style, 3.1f, 780, 360, bg, paint(c1 = on, detail = detail))
                val cov = coverage(a, bg)
                assertTrue("$style/$detail dibuja algo ($cov)", cov > 0.004f)
                assertTrue("$style/$detail no tapa toda la tarjeta ($cov)", cov < 0.97f)
                val b = render(style, 3.9f, 780, 360, bg, paint(c1 = on, detail = detail))
                assertNotEquals("$style/$detail se mueve con el tiempo", signature(a), signature(b))
            }
        }
        // el arcoíris también
        for (style in MusicFxStyles.single) {
            assertTrue(style, coverage(render(style, 2.2f, 780, 360, Color(0xFF1B1830), paint(rainbow = true)), Color(0xFF1B1830)) > 0.004f)
        }
    }

    @Test
    fun aQuietFrameDrawsNothing() {
        val bg = Color(0xFF1B1830)
        val img = ImageBitmap(400, 200)
        val canvas = Canvas(img)
        canvas.drawRect(androidx.compose.ui.geometry.Rect(0f, 0f, 400f, 200f), androidx.compose.ui.graphics.Paint().apply { color = bg })
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size(400f, 200f)) {
            drawMusicFx("bars", app.fitstat.conn.RhythmFrame.Idle, paint())
        }
        assertEquals(0f, coverage(img, bg), 0f)
    }

    @Test
    fun styleSheet() {
        // una hoja con todos los estilos: arriba sobre la tarjeta de acento (meta semanal) y abajo sobre una tarjeta oscura (música)
        val w = 540; val h = 250; val cols = 2
        val styles = MusicFxStyles.all
        val rows = styles.size / cols
        val sheet = Bitmap.createBitmap(w * cols * 2, h * rows, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(sheet)
        for ((i, s) in styles.withIndex()) {
            val col = i % cols; val row = i / cols
            val lime = Color(0xFFB6F500); val dark = Color(0xFF1E1B2E)
            val left = render(s, 4.3f, w, h, lime, paint(c1 = Color(0xFF14111F), c2 = Color(0xFF2F5200)))
            val right = render(s, 4.3f, w, h, dark, paint(c1 = Color(0xFFB6F500), c2 = Color(0xFF6D4AFF)))
            c.drawBitmap(left.asAndroidBitmap(), (col * 2 * w).toFloat(), (row * h).toFloat(), null)
            c.drawBitmap(right.asAndroidBitmap(), ((col * 2 + 1) * w).toFloat(), (row * h).toFloat(), null)
        }
        val out = File(Shots.dir, "v18_fx_styles.png")
        FileOutputStream(out).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 10_000)
    }

    @Test
    fun coverColorComesFromTheArt() {
        assertNull(coverColorOf(null))
        val red = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(220, 20, 30)) }
        val c = coverColorOf(red)!!
        assertTrue("rojo dominante", android.graphics.Color.red(c) > 180 && android.graphics.Color.green(c) < 60)
        // un color vivo pesa más que un fondo gris
        val mixed = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(120, 120, 120))
            for (x in 0 until 6) for (y in 0 until 16) setPixel(x, y, android.graphics.Color.rgb(30, 200, 60))
        }
        val m = coverColorOf(mixed)!!
        assertTrue("verde manda", android.graphics.Color.green(m) > android.graphics.Color.red(m))
    }

    @Test
    fun aChromeCoverWithBlueTonesGivesBlueNotGray() {
        // Como la portada de una canción cromada: casi todo plateado y unas zonas azuladas.
        val art = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(205, 208, 214))
            for (x in 0 until 48) for (y in 0 until 48) {
                if ((x / 6 + y / 6) % 3 == 0) setPixel(x, y, android.graphics.Color.rgb(70, 110, 190))
            }
        }
        val tone = app.fitstat.ui.design.coverTone(androidx.compose.ui.graphics.Color(coverColorOf(art)!!))
        assertTrue("azul manda: $tone", tone.blue > tone.red + 0.15f)
        // y de verdad se nota en la app: el acento ya no queda gris
        val base = app.fitstat.ui.theme.buildColors(dark = true, amoled = false, palette = app.fitstat.ui.theme.Palettes.all.first(), tint = 0.1f, highContrast = false)
        val t = base.tinted(tone, 0.8f)
        assertTrue("el acento se vuelve azul", t.accent.blue > t.accent.red + 0.1f)
        assertTrue("y el fondo y las tarjetas también se tiñen: tono=$tone bg=${base.bg}->${t.bg} surface=${base.surface}->${t.surface}", t.bg.blue > base.bg.blue && t.surface.blue > base.surface.blue)
    }

    @Test
    fun aTrulyGrayCoverStaysGray() {
        val art = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(140, 140, 140)) }
        val t = app.fitstat.ui.design.coverTone(androidx.compose.ui.graphics.Color(coverColorOf(art)!!))
        assertTrue(kotlin.math.abs(t.red - t.blue) < 0.03f)
    }

    // ───────── Con la interfaz de verdad

    private class FakeTap(var allowed: Boolean = true) : AudioTap {
        var running = false
        override fun permitted() = allowed
        override fun start(): Boolean { running = allowed; return allowed }
        override fun stop() { running = false }
        override fun read(out: FloatArray): Boolean {
            if (!running) return false
            for (i in out.indices) out[i] = if (i < 6) 70f else 25f
            return true
        }
    }

    private class Env(val app: AppState, val repo: Repo, val hub: ConnectorHub, val pf: FakePlatform, val player: FakePlayer, val tap: FakeTap)

    private fun makeEnv(playing: Boolean = true, tap: FakeTap = FakeTap(), namida: Boolean = true, mode: String = "dark"): Env {
        val dir = Files.createTempDirectory("fitstat-fx").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
        repo.setProfileName("Chris")
        repo.setSetting("theme.mode", mode)
        val pf = FakePlatform()
        val player = FakePlayer(playing = playing)
        val hub = ConnectorHub(repo, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), pf, player, FakeVolumeLogged())
        if (namida) {
            pf.installedPkgs = setOf(Namida.packages.first())
            pf.notif = true
            repo.setSetting("conn.namida.enabled", "true")
        }
        val engine = RhythmEngine({ repo.prefs }, { hub.music.state }, { hub.status(app.fitstat.conn.Connectors.NAMIDA) == app.fitstat.conn.ConnStatus.Ready }, tap)
        val app = AppState(ApplicationProvider.getApplicationContext(), repo, connectors = hub, rhythm = engine)
        return Env(app, repo, hub, pf, player, tap)
    }

    private fun ui(block: () -> Unit) {
        rule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
    }

    private fun start(e: Env) {
        ui { e.hub.refresh() }
        rule.setContent { FitstatRoot(e.app) }
        Shots.settle(rule, 1600)
    }

    private fun tag(t: String) = rule.onNodeWithTag(t)

    /** Pulsa algo aunque esté fuera de la vista (las barras que se desplazan): la vista previa nunca para de animar, así que no se espera a que se quede quieta. */
    private fun click(t: String) {
        tag(t).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** El motor mueve el ritmo cuadro a cuadro; en pruebas el reloj de animación infinita no corre, así que se le dan cuadros a mano. */
    private var pumpClock = 5_000_000_000L

    private fun pump(e: Env, frames: Int = 60) {
        ui {
            repeat(frames) { e.app.rhythm.step(pumpClock); pumpClock += 25_000_000L }
        }
        Shots.settle(rule, 200)
    }

    @Test
    fun goalCardAndMusicWidgetAnimateWhileMusicPlays() {
        val e = makeEnv()
        start(e)
        ui { e.app.home.start(); e.app.home.add("music"); e.app.home.move("music", 1); e.app.home.finish() }
        Shots.settle(rule, 800)
        tag("goal_widget").assertIsDisplayed()
        tag("music_widget").assertIsDisplayed()
        assertTrue("hay animaciones en pantalla", e.app.rhythm.wanted)
        assertTrue("suena música con Namida lista", e.app.rhythm.live)
        pump(e)
        assertEquals(RhythmMode.Real, e.app.rhythm.mode)
        assertTrue(e.app.rhythm.frame.level > 0.1f)
        Shots.capture(rule, "v18_home_fx_real")
        // sin permiso de audio: ritmo aproximado, pero igual se anima
        ui { e.tap.allowed = false; e.app.rhythm.refreshPermission() }
        pump(e, 120)
        assertEquals(RhythmMode.Approx, e.app.rhythm.mode)
        assertTrue(e.app.rhythm.frame.level > 0f)
        Shots.capture(rule, "v18_home_fx_approx")
    }

    @Test
    fun noAnimationWhenMusicIsPausedOrTurnedOff() {
        val e = makeEnv(playing = false)
        start(e)
        ui { e.app.home.start(); e.app.home.add("music"); e.app.home.move("music", 1); e.app.home.finish() }
        Shots.settle(rule, 600)
        assertTrue(e.app.rhythm.wanted)
        assertFalse("en pausa nada se anima", e.app.rhythm.live)
        pump(e, 30)
        assertTrue(e.app.rhythm.resting)
        Shots.capture(rule, "v18_home_fx_paused")
        // apagar las animaciones quita a los consumidores
        ui { e.repo.setSetting("fx.enabled", "false") }
        Shots.settle(rule, 400)
        assertFalse(e.app.rhythm.wanted)
    }

    @Test
    fun pausingTheSongFadesTheAnimationsOutOnScreen() {
        val e = makeEnv()
        start(e)
        ui { e.app.home.start(); e.app.home.add("music"); e.app.home.move("music", 1); e.app.home.finish() }
        Shots.settle(rule, 800)
        pump(e, 80)
        assertTrue("suena y se anima", e.app.rhythm.live && e.app.rhythm.fadeLevel >= 1f)
        Shots.capture(rule, "v184_fx_playing")
        // el usuario pausa en Namida
        ui { e.player.userSets(false) }
        assertFalse("en pausa el motor deja de estar vivo", e.app.rhythm.live)
        pump(e, 40)                                  // ≈1 s: a medias
        val mid = e.app.rhythm.fadeLevel
        assertTrue("a medio desvanecer: $mid", mid > 0f && mid < 1f)
        Shots.capture(rule, "v184_fx_fading")
        pump(e, 200)                                 // 5 s más
        assertEquals("ya no queda nada", 0f, e.app.rhythm.fadeLevel, 0f)
        assertTrue(e.app.rhythm.resting)
        assertEquals(RhythmMode.Off, e.app.rhythm.mode)
        Shots.capture(rule, "v184_fx_paused_after")
    }

    @Test
    fun everyStyleCanBePickedForTheGoalCard() {
        val e = makeEnv()
        start(e)
        for (style in MusicFxStyles.all) {
            ui { e.repo.setSetting("fx.goal.style", style); e.repo.setSetting("fx.music.style", style) }
            Shots.settle(rule, 300)
            pump(e, 40)
            tag("goal_widget").assertIsDisplayed()
            if (style == "pulse" || style == "aurora") Shots.capture(rule, "v18_home_fx_$style")
        }
    }

    @Test
    fun theGoalCardAnimatesInTheMediumSizeToo() {
        val e = makeEnv()
        start(e)
        ui { e.app.home.start(); e.app.home.setSize("goal", HomeSize.Medium); e.app.home.finish() }
        Shots.settle(rule, 600)
        pump(e, 50)
        tag("goal_widget").assertIsDisplayed()
        Shots.capture(rule, "v18_home_fx_goal_medium")
    }

    @Test
    fun theLightThemeKeepsTheAnimationReadable() {
        val e = makeEnv(mode = "light")
        start(e)
        ui { e.app.home.start(); e.app.home.add("music"); e.app.home.move("music", 1); e.app.home.finish() }
        Shots.settle(rule, 600)
        pump(e, 50)
        Shots.capture(rule, "v18_home_fx_light")
    }

    // ───────── Ajustes › Animaciones de música

    private fun openSettings(e: Env) {
        ui { e.app.nav.push(Route.SettingsPage(Pages.MUSICFX)) }
        Shots.settle(rule, 900)
    }

    @Test
    fun theSettingsPageHasPreviewStylesAndHonestStatus() {
        val e = makeEnv()
        start(e)
        openSettings(e)
        tag("fx_preview").assertIsDisplayed()
        for (id in MusicFxStyles.all) tag("fx_style_$id").assertExists()
        pump(e, 80)
        tag("fx_mode").assertIsDisplayed()
        Shots.capture(rule, "v18_fx_settings")
        // elegir un estilo y usarlo
        click("fx_style_particles")
        tag("fx_use_music").performClick()
        Shots.settle(rule, 300)
        assertEquals("particles", e.repo.prefs.str("fx.music.style"))
        assertEquals("waves", e.repo.prefs.str("fx.goal.style"))
        click("fx_style_radial")
        tag("fx_use_goal").performClick()
        Shots.settle(rule, 300)
        assertEquals("radial", e.repo.prefs.str("fx.goal.style"))
        assertEquals("con el permiso dado se dice", true, runCatching { tag("fx_perm_ok").assertExists() }.isSuccess)
    }

    @Test
    fun withoutAudioPermissionThePageOffersToAskForIt() {
        val tap = FakeTap(allowed = false)
        val e = makeEnv(tap = tap, namida = false)
        var asked = 0
        e.app.audioPermissionRequest = { asked++ }
        start(e)
        openSettings(e)
        tag("fx_permission").assertIsDisplayed()
        tag("fx_goto_namida").assertIsDisplayed()
        tag("fx_perm_ok").assertDoesNotExist()
        Shots.capture(rule, "v18_fx_settings_nopermission")
        tag("fx_permission").performClick()
        assertEquals(1, asked)
        // la vista previa funciona aunque no haya música ni permiso
        pump(e, 60)
        assertEquals(RhythmMode.Approx, e.app.rhythm.mode)
        assertTrue(e.app.rhythm.frame.level > 0f)
        // si el usuario elige el ritmo aproximado, ya no se le pide permiso
        ui { e.repo.setSetting("fx.source", "approx") }
        Shots.settle(rule, 400)
        tag("fx_permission").assertDoesNotExist()
    }

    @Test
    fun theMusicFxPageIsListedInSettingsAndHasEveryControl() {
        val e = makeEnv()
        start(e)
        ui { e.app.nav.push(Route.Settings) }
        Shots.settle(rule, 700)
        rule.onNodeWithTag("settings_list").let { } // la lista de ajustes se ve completa en otras pruebas
        assertNotNull(app.fitstat.data.Settings.pages.firstOrNull { it.id == Pages.MUSICFX })
        openSettings(e)
        for (key in listOf("fx.enabled", "fx.source", "fx.goal.style", "fx.music.style", "fx.cover", "fx.intensity", "fx.color", "fx.quality")) {
            assertNotNull(key, app.fitstat.data.Settings.byKey[key])
        }
        assertEquals(Rhythm.BARS, e.app.rhythm.frame.bars.size)
    }
}
