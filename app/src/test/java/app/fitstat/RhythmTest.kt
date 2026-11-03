package app.fitstat

import app.fitstat.conn.AudioTap
import app.fitstat.conn.MusicState
import app.fitstat.conn.Rhythm
import app.fitstat.conn.RhythmAnalyzer
import app.fitstat.conn.RhythmEngine
import app.fitstat.conn.RhythmMode
import app.fitstat.conn.RhythmSim
import app.fitstat.conn.RhythmWhy
import app.fitstat.data.Prefs
import app.fitstat.data.Settings
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** v1.8: el ritmo de la música para las animaciones (análisis del audio, ritmo aproximado y el motor). */
class RhythmTest {
    // ───────── Del espectro a las bandas

    /** Un espectro como el de Visualizer.getFft con toda la energía en la frecuencia [hz]. */
    private fun fftWithTone(hz: Float, amp: Int = 100, n: Int = 512, sr: Int = 44100): ByteArray {
        val b = ByteArray(n)
        val k = Math.round(hz / (sr.toFloat() / n)).coerceIn(1, n / 2 - 1)
        b[2 * k] = amp.toByte()
        return b
    }

    @Test fun bandsPutEnergyWhereTheToneIs() {
        val out = FloatArray(Rhythm.BARS)
        assertTrue(Rhythm.bandsFromFft(fftWithTone(100f), 44_100_000, out))
        val low = out.indices.maxByOrNull { out[it] }!!
        assertTrue("100 Hz debe caer en los graves (banda $low)", low < Rhythm.BARS / 4)
        assertTrue(Rhythm.bandsFromFft(fftWithTone(6000f), 44_100_000, out))
        val high = out.indices.maxByOrNull { out[it] }!!
        assertTrue("6 kHz debe caer en los agudos (banda $high)", high > Rhythm.BARS * 2 / 3)
    }

    @Test fun bandsHandleSilenceAndBadInput() {
        val out = FloatArray(Rhythm.BARS) { 9f }
        assertTrue(Rhythm.bandsFromFft(ByteArray(512), 44_100_000, out))
        assertTrue(out.all { it == 0f })
        assertFalse("muy corto", Rhythm.bandsFromFft(ByteArray(8), 44_100_000, out))
        // frecuencia de muestreo rara: usa 44.1 kHz y no se rompe
        assertTrue(Rhythm.bandsFromFft(fftWithTone(200f), 0, out))
        assertTrue(out.all { it >= 0f && it.isFinite() })
    }

    // ───────── Análisis

    private fun frameOf(a: RhythmAnalyzer, lowAmp: Float, t: Float, dt: Float = 0.025f): app.fitstat.conn.RhythmFrame {
        val raw = FloatArray(Rhythm.BARS) { i -> if (i < 6) lowAmp else lowAmp * 0.3f }
        return a.update(raw, dt, t, 0.6f)
    }

    @Test fun analyzerDetectsKicksAndNotSteadyNoise() {
        val a = RhythmAnalyzer()
        var t = 0f
        // un bombo fuerte cada 0.5 s sobre un fondo bajito
        var beats = 0
        var lastBeat = 0f
        repeat(160) { i ->
            val onKick = i % 20 == 0
            val f = frameOf(a, if (onKick) 80f else 12f, t)
            if (f.beat > 0.99f) { beats++; lastBeat = t }
            t += 0.025f
        }
        assertTrue("debe encontrar los golpes (encontró $beats)", beats in 6..9)
        // un sonido parejo no es ritmo
        val b = RhythmAnalyzer()
        t = 0f
        var steady = 0
        repeat(160) { if (frameOf(b, 40f, t).beat > 0.99f) steady++; t += 0.025f }
        assertTrue("sin golpes no hay latidos (hubo $steady)", steady <= 1)
    }

    @Test fun analyzerRiseFastFallSlowAndStayInRange() {
        val a = RhythmAnalyzer()
        var t = 0f
        repeat(10) { frameOf(a, 80f, t); t += 0.025f }
        val up = frameOf(a, 80f, t).level
        assertTrue(up in 0.2f..1f)
        t += 0.025f
        val f1 = frameOf(a, 0f, t)
        assertTrue("baja despacio, no de golpe", f1.level > up * 0.5f)
        repeat(60) { t += 0.025f; frameOf(a, 0f, t) }
        assertTrue(frameOf(a, 0f, t + 0.025f).level < 0.1f)
        val fr = frameOf(a, 80f, t + 0.05f)
        assertTrue(fr.bars.all { it in 0f..1f } && fr.level in 0f..1f && fr.bass in 0f..1f && fr.mid in 0f..1f && fr.treble in 0f..1f)
        assertTrue(fr.real)
    }

    @Test fun analyzerAdjustsToQuietMusicButNotToNoise() {
        // música bajita (pero por encima del ruido) se ve casi igual que fuerte
        val quiet = RhythmAnalyzer(); val loud = RhythmAnalyzer()
        var t = 0f
        var q = 0f; var l = 0f
        repeat(120) { q = frameOf(quiet, 20f, t).level; l = frameOf(loud, 100f, t).level; t += 0.025f }
        assertTrue("el volumen se compensa ($q vs $l)", q > l * 0.6f)
        // ruido de fondo mínimo no se amplifica
        val noise = RhythmAnalyzer()
        t = 0f
        var n = 0f
        repeat(120) { n = frameOf(noise, 2f, t).level; t += 0.025f }
        assertTrue("el ruido casi no se ve ($n)", n < 0.2f)
    }

    @Test fun analyzerCountsSilence() {
        val a = RhythmAnalyzer()
        val zeros = FloatArray(Rhythm.BARS)
        var t = 0f
        repeat(120) { a.update(zeros, 0.025f, t, 0.5f); t += 0.025f }
        assertTrue(a.silentFor > 2.5f)
        a.update(FloatArray(Rhythm.BARS) { 30f }, 0.025f, t, 0.5f)
        assertEquals(0f, a.silentFor, 0.0001f)
    }

    // ───────── Ritmo aproximado

    @Test fun simIsDeterministicBoundedAndKeepsTheTempo() {
        val a = RhythmSim.frameAt(3.37f, 120, 0.6f)
        val b = RhythmSim.frameAt(3.37f, 120, 0.6f)
        assertArrayEquals(a.bars, b.bars, 0f)
        assertEquals(a.beat, b.beat, 0f)
        assertFalse(a.real)
        var t = 0f
        while (t < 30f) {
            val f = RhythmSim.frameAt(t, 120, 0.8f)
            assertTrue(f.bars.all { it in 0f..1f } && f.level in 0f..1f && f.beat in 0f..1f)
            t += 0.016f
        }
        // a 120 bpm hay un golpe cada 0.5 s (el primero de cada compás es el más fuerte): al medio tiempo ya bajó
        assertTrue(RhythmSim.frameAt(2.0f, 120, 0.6f).beat > 0.95f)
        assertTrue(RhythmSim.frameAt(1.0f, 120, 0.6f).beat in 0.7f..0.8f)
        assertTrue(RhythmSim.frameAt(2.25f, 120, 0.6f).beat < 0.25f)
        assertEquals(1.0f, RhythmSim.frameAt(1.2f, 120, 0.6f).beats[0], 0.001f)
        assertEquals(0.5f, RhythmSim.frameAt(1.2f, 120, 0.6f).beats[1], 0.001f)
    }

    @Test fun simBassIsStrongerOnTheBeatAndCalmIsQuiet() {
        val on = RhythmSim.frameAt(2.0f, 100, 0.6f)
        val off = RhythmSim.frameAt(2.0f + 0.5f * 60f / 100f, 100, 0.6f)
        assertTrue(on.bass > off.bass)
        val calm = RhythmSim.frameAt(2.0f, 100, 0.6f, calm = true)
        assertTrue(calm.level < on.level * 0.5f)
        assertEquals(0f, calm.beat, 0f)
        assertTrue(calm.beats.all { it == Rhythm.NONE })
    }

    // ───────── Motor

    private class FakeTap(var allowed: Boolean = true, var startOk: Boolean = true) : AudioTap {
        var started = 0
        var stopped = 0
        var level = 60f
        var silent = false
        var running = false
        override fun permitted() = allowed
        override fun start(): Boolean { started++; running = startOk; return startOk }
        override fun stop() { stopped++; running = false }
        override fun read(out: FloatArray): Boolean {
            if (!running) return false
            for (i in out.indices) out[i] = if (silent) 0f else if (i < 6) level else level * 0.4f
            return true
        }
    }

    private class Env(
        val prefs: MutableMap<String, String> = HashMap(),
        var playing: Boolean = true,
        var ready: Boolean = true,
        val tap: FakeTap? = FakeTap(),
    ) {
        val engine = RhythmEngine(
            prefs = { Prefs(prefs) },
            music = { MusicState(connected = true, playing = playing, title = "Tema") },
            ready = { ready },
            tap = tap,
        )
        var nanos = 1_000_000_000L
        fun frames(n: Int, stepMs: Long = 25) {
            repeat(n) { engine.step(nanos); nanos += stepMs * 1_000_000L }
        }
    }

    @Test fun engineIsAsleepUntilSomethingUsesIt() {
        val e = Env()
        assertFalse(e.engine.wanted)
        assertFalse(e.engine.live)
        val off = e.engine.attach()
        assertTrue(e.engine.wanted)
        assertTrue("suena música y Namida está lista", e.engine.live)
        off()
        assertFalse(e.engine.wanted)
        off() // soltar dos veces no resta dos
        val a = e.engine.attach(); val b = e.engine.attach()
        a(); assertTrue(e.engine.wanted)
        b(); assertFalse(e.engine.wanted)
    }

    @Test fun engineOnlyAnimatesWithMusicOrPreviewOrIdleSetting() {
        val e = Env(playing = false)
        e.engine.attach()
        assertFalse("en pausa no hay nada que animar", e.engine.live)
        e.prefs["fx.idle"] = "true"
        assertTrue("con el fondo suave sí", e.engine.live)
        e.prefs["fx.idle"] = "false"
        e.ready = false
        e.playing = true
        assertFalse("sin Namida lista no", e.engine.live)
        e.engine.attach(preview = true)
        assertTrue("la vista previa siempre", e.engine.live)
        e.prefs["fx.enabled"] = "false"
        assertFalse("apagadas, nada", e.engine.live || e.engine.wanted)
    }

    @Test fun engineUsesRealAudioWhenAllowed() {
        val e = Env()
        e.engine.attach()
        e.frames(20)
        assertEquals(RhythmMode.Real, e.engine.mode)
        assertEquals(RhythmWhy.None, e.engine.why)
        assertTrue(e.engine.frame.real)
        assertTrue(e.engine.frame.level > 0.1f)
        assertEquals(1, e.tap!!.started)
        assertTrue(e.engine.permission)
    }

    @Test fun engineFallsBackToApproximateAndSaysWhy() {
        // sin permiso
        val a = Env(tap = FakeTap(allowed = false))
        a.engine.attach(); a.frames(20)
        assertEquals(RhythmMode.Approx, a.engine.mode)
        assertEquals(RhythmWhy.NoPermission, a.engine.why)
        assertFalse(a.engine.permission)
        assertFalse(a.engine.frame.real)
        assertTrue("aun así se anima", a.engine.frame.level > 0f)
        // el sistema no deja arrancar la captura
        val b = Env(tap = FakeTap(startOk = false))
        b.engine.attach(); b.frames(20)
        assertEquals(RhythmMode.Approx, b.engine.mode)
        assertEquals(RhythmWhy.Blocked, b.engine.why)
        // este teléfono no tiene captura
        val c = Env(tap = null)
        c.engine.attach(); c.frames(20)
        assertEquals(RhythmWhy.Unsupported, c.engine.why)
        assertFalse(c.engine.supportsReal)
        // el usuario eligió el aproximado: ni se toca el audio
        val d = Env()
        d.prefs["fx.source"] = "approx"
        d.engine.attach(); d.frames(20)
        assertEquals(RhythmMode.Approx, d.engine.mode)
        assertEquals(RhythmWhy.Chosen, d.engine.why)
        assertEquals(0, d.tap!!.started)
    }

    @Test fun engineNoticesASilentSystemAndRetriesLater() {
        val e = Env()
        e.tap!!.silent = true
        e.engine.attach()
        e.frames(40)                       // 1 s: aún espera
        assertEquals(RhythmMode.Real, e.engine.mode)
        e.frames(100)                      // más de 2.5 s de ceros → aproximado
        assertEquals(RhythmMode.Approx, e.engine.mode)
        assertEquals(RhythmWhy.Blocked, e.engine.why)
        assertTrue(e.engine.frame.level > 0f)
        // el sistema empieza a entregar audio: tras la espera vuelve al real
        e.tap.silent = false
        e.frames(400)
        assertEquals(RhythmMode.Real, e.engine.mode)
    }

    @Test fun engineFadesOutWhenMusicStopsAndReleasesTheTap() {
        val e = Env()
        e.engine.attach()
        e.frames(20)
        assertTrue(e.engine.frame.level > 0.1f)
        e.playing = false
        e.frames(5)
        assertTrue("se apaga poco a poco", e.engine.frame.level > 0f)
        assertTrue("suelta el audio desde el primer momento", e.tap!!.stopped >= 1 && !e.tap.running)
        e.frames(120)                      // el desvanecido dura fx.fade_secs (2,5 s por defecto) + margen
        assertTrue(e.engine.resting)
        assertEquals(RhythmMode.Off, e.engine.mode)
    }

    @Test fun enginePreviewWorksWithoutMusic() {
        val e = Env(playing = false, ready = false)
        e.engine.attach(preview = true)
        e.frames(30)
        assertEquals(RhythmMode.Approx, e.engine.mode)
        assertTrue(e.engine.frame.level > 0.05f)
        assertTrue("la vista previa lleva latidos", e.engine.frame.beats.any { it > Rhythm.NONE / 2 })
    }

    @Test fun engineQualityThrottlesFrames() {
        fun distinct(q: String): Int {
            val e = Env(prefs = hashMapOf("fx.quality" to q, "fx.source" to "approx"))
            e.engine.attach()
            val seen = HashSet<Float>()
            repeat(120) { e.engine.step(e.nanos); e.nanos += 8_000_000L; seen += e.engine.frame.time }
            return seen.size
        }
        val low = distinct("low"); val normal = distinct("normal"); val high = distinct("high")
        assertTrue("ahorro < normal < alta ($low, $normal, $high)", low < normal && normal < high)
    }

    @Test fun settingsSchemaIsConsistent() {
        val specs = Settings.specsOf(app.fitstat.data.Pages.MUSICFX)
        assertTrue(specs.size >= 14)
        // los estilos de las dos tarjetas existen y son al menos 5 (más «mezcla»)
        val styles = Settings.FX_STYLES.options.map { it.value }
        assertTrue(styles.size >= 6 && "mix" in styles)
        assertEquals(7, styles.count { it != "mix" })
        assertTrue(Settings.default("fx.goal.style") in styles && Settings.default("fx.music.style") in styles)
        // los valores se limitan a su rango
        val bpm = Settings.byKey["fx.bpm"]!!
        assertEquals("180", Settings.normalize(bpm, "999"))
        assertEquals("60", Settings.normalize(bpm, "1"))
        assertNull(Settings.normalize(Settings.byKey["fx.color"]!!, "violeta"))
        assertEquals("rainbow", Settings.normalize(Settings.byKey["fx.color"]!!, "rainbow"))
        // todo el que depende de otro apunta a uno que existe
        for (s in specs) s.visibleIf?.let { (k, _) -> assertNotNull("${s.key} depende de $k", Settings.byKey[k]) }
        assertTrue(abs(sin(PI.toFloat() * 0f)) < 1e-6)
    }
}
