package app.fitstat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import app.fitstat.conn.MusicState
import app.fitstat.conn.RhythmEngine
import app.fitstat.data.Repo
import app.fitstat.ui.design.coverTone
import app.fitstat.ui.design.tinted
import app.fitstat.ui.theme.Palettes
import app.fitstat.ui.theme.buildColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.ZoneId

/** v1.8.3: las animaciones se desvanecen al parar la música, y la app toma el color de la portada mientras suena. */
class FadeTintTest {
    private fun newRepo(vararg s: Pair<String, String>): Repo {
        val r = Repo(Files.createTempDirectory("fitstat-fade").toFile(), CoroutineScope(SupervisorJob() + Dispatchers.Default), { 1_000_000L }) { ZoneId.of("UTC") }
        for ((k, v) in s) r.setSetting(k, v)
        return r
    }

    /** Un motor con la música que controlamos desde la prueba. */
    private class Rig(val repo: Repo) {
        var playing = true
        val engine = RhythmEngine({ repo.prefs }, { MusicState(connected = true, playing = playing, title = "Tema") }, { true }, null)
        private val off = engine.attach()
        var t = 1_000_000_000L
        /** Avanza [seconds] de reloj de animación en cuadros de 30 ms y devuelve los niveles de desvanecido. */
        fun run(seconds: Float): List<Float> {
            val out = ArrayList<Float>()
            repeat((seconds / 0.03f).toInt()) {
                engine.step(t)
                t += 30_000_000L
                out += engine.fadeLevel
            }
            return out
        }
    }

    @Test fun animationsFadeInWhenMusicStarts() {
        val rig = Rig(newRepo())
        assertEquals(0f, rig.engine.fadeLevel, 0f)
        rig.run(0.6f)
        assertTrue("ya se ve: ${rig.engine.fadeLevel}", rig.engine.fadeLevel > 0.99f)
    }

    @Test fun pausingFadesOutSlowlyAndSmoothly() {
        val rig = Rig(newRepo("fx.fade_secs" to "2.5"))
        rig.run(0.6f)
        rig.playing = false
        val levels = rig.run(1.0f)
        // baja poco a poco: a la mitad del tiempo sigue bastante visible, y nunca sube
        assertTrue("a 1 s todavía se ve: ${levels.last()}", levels.last() in 0.4f..0.75f)
        assertTrue("baja sin saltos", levels.zipWithNext().all { (a, b) -> b <= a + 1e-6f })
        assertTrue("todavía hay algo que dibujar", rig.engine.frame.level > 0f)
        rig.run(2.6f)
        assertEquals("al terminar el desvanecido ya no queda nada", 0f, rig.engine.fadeLevel, 0f)
        assertTrue(rig.engine.frame.quiet)
        assertTrue("el motor puede dormir", rig.engine.resting)
    }

    @Test fun theFadeLengthIsTheSetting() {
        val quick = Rig(newRepo("fx.fade_secs" to "1"))
        val slow = Rig(newRepo("fx.fade_secs" to "6"))
        quick.run(0.6f); slow.run(0.6f)
        quick.playing = false; slow.playing = false
        quick.run(1.0f); slow.run(1.0f)
        assertTrue("1 s se apaga antes: ${quick.engine.fadeLevel} < ${slow.engine.fadeLevel}", quick.engine.fadeLevel < slow.engine.fadeLevel)
        assertEquals(0f, quick.run(1.2f).last(), 0f)
    }

    @Test fun withTheOptionOffTheyStopAtOnce() {
        val rig = Rig(newRepo("fx.fade_out" to "false"))
        rig.run(0.6f)
        rig.playing = false
        rig.run(0.06f)
        assertEquals("sin el desvanecido se apagan de golpe", 0f, rig.engine.fadeLevel, 0f)
        rig.run(1.5f)
        assertTrue("y el movimiento se calma enseguida", rig.engine.resting)
    }

    @Test fun resumingDuringTheFadeBringsThemBack() {
        val rig = Rig(newRepo("fx.fade_secs" to "2.5"))
        rig.run(0.6f)
        rig.playing = false
        rig.run(1.0f)
        val low = rig.engine.fadeLevel
        rig.playing = true
        rig.run(0.5f)
        assertTrue("vuelve a verse: $low -> ${rig.engine.fadeLevel}", rig.engine.fadeLevel > low && rig.engine.fadeLevel > 0.95f)
    }

    @Test fun theCurveStartsAndEndsSoftly() {
        assertEquals(0f, RhythmEngine.smooth(0f), 0f)
        assertEquals(1f, RhythmEngine.smooth(1f), 0f)
        assertEquals(0.5f, RhythmEngine.smooth(0.5f), 1e-6f)
        val xs = (0..20).map { RhythmEngine.smooth(it / 20f) }
        assertTrue(xs.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("suave al inicio", RhythmEngine.smooth(0.05f) < 0.02f)
    }

    // ───────── El color de la portada

    @Test fun coverColorIsMadeReadableAsAnAccent() {
        val black = coverTone(Color.Black)
        val white = coverTone(Color.White)
        assertTrue("lo oscuro se aclara: ${black.luminance()}", black.luminance() >= 0.15f)
        assertTrue("lo blanco se oscurece: ${white.luminance()}", white.luminance() <= 0.81f)
        val mid = Color(0xFFE02060)
        assertEquals("lo normal no se toca", mid.toArgb(), coverTone(mid).toArgb())
    }

    @Test fun tintAtZeroKeepsYourColors() {
        val base = buildColors(dark = true, amoled = false, palette = Palettes.all.first(), tint = 0.1f, highContrast = false)
        val same = base.tinted(Color(0xFFE02060), 0f)
        assertEquals(base.accent.toArgb(), same.accent.toArgb())
        assertEquals(base.bg.toArgb(), same.bg.toArgb())
    }

    @Test fun tintAtFullUsesTheCoverForAccents() {
        val base = buildColors(dark = true, amoled = false, palette = Palettes.all.first(), tint = 0.1f, highContrast = false)
        val cover = Color(0xFFE02060)
        val full = base.tinted(cover, 1f)
        assertEquals(cover.toArgb(), full.accent.toArgb())
        assertTrue("el fondo solo toca un poco", full.bg != base.bg && full.bg.luminance() < 0.2f)
        // los textos siguen leyéndose sobre el acento
        val contrast = (full.accent.luminance() + 0.05f) / (full.onAccent.luminance() + 0.05f)
        val inverse = (full.onAccent.luminance() + 0.05f) / (full.accent.luminance() + 0.05f)
        assertTrue("texto legible sobre el acento: ${maxOf(contrast, inverse)}", maxOf(contrast, inverse) > 3f)
        // lo que no es acento no cambia
        assertEquals(base.text.toArgb(), full.text.toArgb())
        assertTrue("las tarjetas toman un toque y siguen oscuras", full.surface != base.surface && full.surface.luminance() < 0.2f)
    }

    @Test fun tintHalfwayIsBetweenTheTwo() {
        val base = buildColors(dark = false, amoled = false, palette = Palettes.all.first(), tint = 0.1f, highContrast = false)
        val half = base.tinted(Color(0xFF1040E0), 0.5f)
        assertTrue(half.accent != base.accent)
        assertTrue(half.accent != Color(0xFF1040E0))
        assertFalse(half.onAccent == base.onAccent && half.accentText == base.accentText && half.accent == base.accent)
    }

}
