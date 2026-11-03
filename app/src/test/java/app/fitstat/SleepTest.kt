package app.fitstat

import app.fitstat.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/** v1.7: sueño. La lógica de noches, promedios y la estimación con el uso del teléfono, más el registro en el repositorio. */
class SleepTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val today = LocalDate.of(2026, 10, 28)
    private val now = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val H = 3600_000L
    private val MIN = 60_000L

    /** Una noche: te duermes la noche anterior a [wake] a las [bed] y despiertas ese día a las [w]. */
    private fun night(wake: LocalDate, bedH: Int, bedM: Int, wH: Int, wM: Int, src: String = SleepSrc.MANUAL, id: String = "n_${wake}_$src") =
        SleepSession(id, at(if (bedH >= wH) wake.minusDays(1) else wake, bedH, bedM), at(wake, wH, wM), src)

    private fun repo(dir: File = Files.createTempDirectory("fitstat-sleep").toFile()) =
        Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }

    // ───────── Fases y duración

    @Test fun healthStageCodes() {
        // 1 despierto, 2 dormido sin detalle, 3 fuera de la cama, 4 ligero, 5 profundo, 6 REM, 7 despierto en la cama
        assertEquals(SleepStageType.AWAKE, Sleep.stageFromHealth(1))
        assertEquals(SleepStageType.AWAKE, Sleep.stageFromHealth(3))
        assertEquals(SleepStageType.AWAKE, Sleep.stageFromHealth(7))
        assertEquals(SleepStageType.LIGHT, Sleep.stageFromHealth(4))
        assertEquals(SleepStageType.DEEP, Sleep.stageFromHealth(5))
        assertEquals(SleepStageType.REM, Sleep.stageFromHealth(6))
        assertEquals(SleepStageType.SLEEP, Sleep.stageFromHealth(2))
        assertEquals(SleepStageType.SLEEP, Sleep.stageFromHealth(0))
        assertEquals(SleepStageType.SLEEP, Sleep.stageFromHealth(99))
    }

    @Test fun asleepIsDurationMinusAwake() {
        val start = at(today.minusDays(1), 23)
        val end = at(today, 7)
        val s = SleepSession(
            "h1", start, end, SleepSrc.HEALTH,
            stages = listOf(
                SleepStage(SleepStageType.LIGHT, start, start + 3 * H),
                SleepStage(SleepStageType.AWAKE, start + 3 * H, start + 3 * H + 20 * MIN),
                SleepStage(SleepStageType.DEEP, start + 3 * H + 20 * MIN, start + 5 * H),
                SleepStage(SleepStageType.REM, start + 5 * H, end),
            ),
        )
        assertEquals(8 * H, Sleep.durationMs(s))
        assertEquals(20 * MIN, Sleep.awakeMs(s))
        assertEquals(8 * H - 20 * MIN, Sleep.asleepMs(s))
        assertTrue(Sleep.hasStages(s))
        val t = Sleep.stageTotals(s)
        assertEquals(3 * H, t[SleepStageType.LIGHT])
        assertEquals(20 * MIN, t[SleepStageType.AWAKE])
        assertEquals(setOf("awake", "rem", "light", "deep"), t.keys)
    }

    @Test fun stagesOutsideTheNightAreClipped() {
        val start = at(today, 1)
        val s = SleepSession("x", start, start + 4 * H, SleepSrc.HEALTH, stages = listOf(SleepStage(SleepStageType.AWAKE, start - H, start + 30 * MIN)))
        assertEquals(30 * MIN, Sleep.awakeMs(s))
        // nunca más despierto que la noche entera
        val all = SleepSession("y", start, start + 4 * H, SleepSrc.HEALTH, stages = listOf(SleepStage(SleepStageType.AWAKE, start - 5 * H, start + 9 * H)))
        assertEquals(0L, Sleep.asleepMs(all))
    }

    @Test fun plainSleepStageIsNotRealStages() {
        // Un reloj que solo dice «dormido» no trae fases: no se debe presentar como si las tuviera.
        val start = at(today, 1)
        val s = SleepSession("p", start, start + 7 * H, SleepSrc.HEALTH, stages = listOf(SleepStage(SleepStageType.SLEEP, start, start + 7 * H)))
        assertFalse(Sleep.hasStages(s))
        assertFalse(Sleep.hasStages(SleepSession("q", start, start + 7 * H, SleepSrc.PHONE)))
    }

    @Test fun validDurations() {
        val t = at(today, 1)
        assertFalse(Sleep.valid(SleepSession("a", t, t + 19 * MIN)))
        assertTrue(Sleep.valid(SleepSession("b", t, t + 20 * MIN)))
        assertTrue(Sleep.valid(SleepSession("c", t, t + 20 * H)))
        assertFalse(Sleep.valid(SleepSession("d", t, t + 20 * H + MIN)))
        assertFalse(Sleep.valid(SleepSession("e", t, t - H)))
    }

    // ───────── Noches

    @Test fun dedupePrefersManualThenWatchThenPhone() {
        val manual = night(today, 23, 30, 7, 0, SleepSrc.MANUAL)
        val watch = night(today, 23, 10, 7, 10, SleepSrc.HEALTH)
        val phone = night(today, 0, 10, 7, 30, SleepSrc.PHONE).copy(id = "phone_x")
        assertEquals(listOf(manual.id), Sleep.dedupe(listOf(phone, watch, manual)).map { it.id })
        assertEquals(listOf(watch.id), Sleep.dedupe(listOf(phone, watch)).map { it.id })
        assertEquals(listOf(phone.id), Sleep.dedupe(listOf(phone)).map { it.id })
    }

    @Test fun dedupeKeepsDifferentNights() {
        val a = night(today, 23, 30, 7, 0)
        val b = night(today.minusDays(1), 23, 30, 7, 0)
        val nap = SleepSession("nap", at(today, 14), at(today, 15), SleepSrc.MANUAL)
        assertEquals(3, Sleep.dedupe(listOf(a, b, nap)).size)
        // lo inválido se descarta
        assertEquals(1, Sleep.dedupe(listOf(a, SleepSession("bad", at(today, 2), at(today, 2) + 5 * MIN, SleepSrc.MANUAL))).size)
    }

    @Test fun nightsAreGroupedByWakeDateNewestFirst() {
        val n1 = night(today, 23, 30, 7, 0)
        val n2 = night(today.minusDays(1), 0, 30, 8, 0)
        val n3 = night(today.minusDays(3), 22, 45, 6, 30)
        val nights = Sleep.nights(listOf(n2, n3, n1), zone)
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(3)), nights.map { it.date })
        assertEquals(n1.id, nights[0].main.id)
        assertTrue(nights.all { it.extra.isEmpty() })
    }

    @Test fun nearbyRestsFromTheSameSourceBecomeOneNight() {
        val a = SleepSession("h1", at(today.minusDays(1), 23), at(today, 3), SleepSrc.HEALTH)
        val b = SleepSession("h2", at(today, 3, 30), at(today, 7), SleepSrc.HEALTH)   // te levantaste media hora
        val nights = Sleep.nights(listOf(a, b), zone)
        assertEquals(1, nights.size)
        val main = nights[0].main
        assertEquals(a.start, main.start)
        assertEquals(b.end, main.end)
        // la media hora de pie cuenta como despierto
        assertEquals(30 * MIN, Sleep.awakeMs(main))
        assertEquals(8 * H - 30 * MIN, Sleep.asleepMs(main))
    }

    @Test fun napIsAnExtraRestOfTheDay() {
        val night = night(today, 23, 30, 7, 0)
        val nap = SleepSession("nap", at(today, 14), at(today, 15, 30), SleepSrc.MANUAL)
        val n = Sleep.nights(listOf(night, nap), zone).single()
        assertEquals(night.id, n.main.id)
        assertEquals(listOf("nap"), n.extra.map { it.id })
    }

    @Test fun latestFallsBackToYesterday() {
        val nights = Sleep.nights(listOf(night(today.minusDays(1), 23, 0, 7, 0)), zone)
        assertEquals(today.minusDays(1), Sleep.latest(nights, today)?.date)
        assertNull(Sleep.latest(nights, today.plusDays(1)))
        assertNull(Sleep.latest(emptyList(), today))
    }

    // ───────── Promedios

    @Test fun circularMeanCrossesMidnight() {
        assertNull(Sleep.circularMean(emptyList()))
        assertEquals(0, Sleep.circularMean(listOf(23 * 60 + 30, 30)))          // 23:30 y 00:30 → 00:00, no 12:00
        assertEquals(23 * 60, Sleep.circularMean(listOf(22 * 60 + 30, 23 * 60 + 30)))
        assertEquals(7 * 60, Sleep.circularMean(listOf(7 * 60)))
    }

    @Test fun summaryAveragesAndRegularity() {
        val list = (0 until 5).map { night(today.minusDays(it.toLong()), 23, 0, 7, 0) }
        val sm = Sleep.summary(Sleep.nights(list, zone), 8.0, zone)
        assertEquals(5, sm.nights)
        assertEquals(8 * H, sm.avgAsleepMs)
        assertEquals(23 * 60, sm.avgBedMin)
        assertEquals(7 * 60, sm.avgWakeMin)
        assertEquals(5, sm.metGoal)
        assertEquals(0, sm.bedSpreadMin)
        assertEquals("Muy regular", sm.regularity)
        assertEquals(0, sm.withStages)
        // meta más alta: ninguna la cumple
        assertEquals(0, Sleep.summary(Sleep.nights(list, zone), 9.0, zone).metGoal)
    }

    @Test fun irregularBedtimeIsReported() {
        val beds = listOf(21, 1, 23, 3, 22)
        val list = beds.mapIndexed { i, h -> night(today.minusDays(i.toLong()), h, 0, (h + 8) % 24, 0) }
        val sm = Sleep.summary(Sleep.nights(list, zone), 8.0, zone)
        assertEquals("Irregular", sm.regularity)
        assertTrue(sm.bedSpreadMin!! > 60)
    }

    @Test fun summaryNeedsThreeNightsForRegularity() {
        val sm = Sleep.summary(Sleep.nights(listOf(night(today, 23, 0, 7, 0), night(today.minusDays(1), 23, 0, 7, 0)), zone), 8.0, zone)
        assertNull(sm.bedSpreadMin)
        assertNull(sm.regularity)
        val none = Sleep.summary(emptyList(), 8.0, zone)
        assertEquals(0, none.nights)
        assertNull(none.avgBedMin)
    }

    @Test fun formats() {
        assertEquals("7 h 32 min", Sleep.fmt(7 * H + 32 * MIN))
        assertEquals("8 h", Sleep.fmt(8 * H))
        assertEquals("45 min", Sleep.fmt(45 * MIN))
        assertEquals("0 min", Sleep.fmt(-5))
        assertEquals("7:32", Sleep.fmtShort(7 * H + 32 * MIN))
        assertEquals("0:05", Sleep.fmtShort(5 * MIN))
        assertEquals("23:05", Sleep.clock(23 * 60 + 5))
        assertEquals("00:00", Sleep.clock(0))
    }

    @Test fun spanPicksTheRightDays() {
        // «me dormí a las 23:30 y desperté a las 7:00» → la noche empezó el día anterior
        val (s1, e1) = Sleep.span(today, 23 to 30, 7 to 0, zone)
        assertEquals(at(today.minusDays(1), 23, 30), s1)
        assertEquals(at(today, 7), e1)
        // «me dormí a la 1:00 y desperté a las 7:00» → la misma fecha
        val (s2, e2) = Sleep.span(today, 1 to 0, 7 to 0, zone)
        assertEquals(at(today, 1), s2)
        assertEquals(at(today, 7), e2)
        // siesta: dormir 14:00 y despertar 15:00
        val (s3, _) = Sleep.span(today, 14 to 0, 15 to 0, zone)
        assertEquals(at(today, 14), s3)
    }

    // ───────── Estimación con el uso del teléfono

    private fun ev(day: LocalDate, h: Int, m: Int, on: Boolean) = ScreenEvent(at(day, h, m), on)

    @Test fun phoneEstimateTakesTheLongestNightGap() {
        val d0 = today.minusDays(1)
        val events = listOf(
            ev(d0, 22, 40, true),
            ev(d0, 23, 10, false),   // apagó la pantalla
            ev(today, 7, 5, true),   // la prendió al despertar
            ev(today, 7, 20, false),
            ev(today, 12, 0, true),
        )
        val est = Sleep.estimateFromScreen(events, zone)
        assertEquals(1, est.size)
        val s = est.single()
        assertEquals("phone_$today", s.id)
        assertEquals(SleepSrc.PHONE, s.source)
        assertEquals(at(d0, 23, 10), s.start)
        assertEquals(at(today, 7, 5), s.end)
        assertTrue("sin fases", s.stages.isEmpty())
        assertFalse(Sleep.hasStages(s))
    }

    @Test fun shortScreenWakeUpsDoNotSplitTheNight() {
        val d0 = today.minusDays(1)
        val events = listOf(
            ev(d0, 23, 30, false),
            ev(today, 3, 0, true),   // miró la hora 3 minutos
            ev(today, 3, 3, false),
            ev(today, 7, 30, true),
        )
        val s = Sleep.estimateFromScreen(events, zone).single()
        assertEquals(at(d0, 23, 30), s.start)
        assertEquals(at(today, 7, 30), s.end)
    }

    @Test fun longerWakeUpsDoSplitIt() {
        val d0 = today.minusDays(1)
        val events = listOf(
            ev(d0, 23, 30, false),
            ev(today, 2, 0, true),    // 30 min en el teléfono: ya es otro tramo
            ev(today, 2, 30, false),
            ev(today, 7, 30, true),
        )
        val s = Sleep.estimateFromScreen(events, zone).single()
        // se queda con el tramo más largo (02:30 → 07:30), no con el primero
        assertEquals(at(today, 2, 30), s.start)
        assertEquals(at(today, 7, 30), s.end)
    }

    @Test fun phoneEstimateIgnoresDaytimeShortAndHugeGaps() {
        val d = today
        val events = listOf(
            // siesta de día sin teléfono: 13:00 → 17:30 (punto medio 15:15: no cuenta)
            ev(d, 13, 0, false), ev(d, 17, 30, true),
            // noche con solo 2 h sin usarlo: menos del mínimo (3 h)
            ev(d, 23, 0, false), ev(d.plusDays(1), 1, 0, true),
        )
        assertTrue(Sleep.estimateFromScreen(events, zone).isEmpty())
        // con un mínimo de 2 h, esa noche sí cuenta
        assertEquals(1, Sleep.estimateFromScreen(events, zone, 2 * H).size)
        // más de 14 h seguidas: el teléfono se quedó apagado, no es sueño
        val off = listOf(ev(d, 20, 0, false), ev(d.plusDays(1), 12, 0, true))
        assertTrue(Sleep.estimateFromScreen(off, zone).isEmpty())
    }

    @Test fun phoneEstimateOnePerWakeDate() {
        val events = (3 downTo 1).flatMap { i ->
            val d = today.minusDays(i.toLong())
            listOf(ev(d, 23, 30, false), ev(d.plusDays(1), 7, 0, true))
        }
        val est = Sleep.estimateFromScreen(events.shuffled(), zone)
        assertEquals(3, est.size)
        assertEquals(est.sortedBy { it.start }, est)
        assertEquals(3, est.map { it.id }.toSet().size)
    }

    @Test fun phoneEstimateUnclosedGapIsNotANightYet() {
        // la pantalla sigue apagada (aún duermes o no has tocado el teléfono): no se inventa la hora de despertar
        val events = listOf(ev(today, 23, 30, false))
        assertTrue(Sleep.estimateFromScreen(events, zone).isEmpty())
    }

    // ───────── Repo

    @Test fun addSleepValidates() {
        val r = repo()
        val wake = at(today, 7)
        assertTrue(r.addSleep(wake - 8 * H, wake).isSuccess)
        assertTrue("fin antes del inicio", r.addSleep(wake, wake - H).isFailure)
        assertTrue("demasiado corta", r.addSleep(wake - 10 * MIN, wake).isFailure)
        assertTrue("demasiado larga", r.addSleep(wake - 21 * H, wake).isFailure)
        assertTrue("en el futuro", r.addSleep(now + 2 * H, now + 10 * H).isFailure)
        assertEquals(1, r.sleep.size)
        val s = r.sleep.single()
        assertEquals(SleepSrc.MANUAL, s.source)
        assertEquals(8 * H, Sleep.durationMs(s))
    }

    @Test fun addSleepCanBeUndoneAndDeleted() {
        val r = repo()
        val wake = at(today, 7)
        val s = r.addSleep(wake - 7 * H, wake, "buena noche").getOrThrow()
        assertEquals("buena noche", r.sleep.single().note)
        assertNotNull(r.undoLabel)
        r.undo()
        assertTrue(r.sleep.isEmpty())
        val s2 = r.addSleep(wake - 7 * H, wake).getOrThrow()
        assertTrue(r.deleteSleep(s2.id))
        assertFalse(r.deleteSleep(s2.id))
        assertTrue(r.sleep.isEmpty())
        assertNotEquals(s.id, s2.id)
    }

    @Test fun mergeSleepUpsertsWithoutUndoPoint() {
        val r = repo()
        val a = night(today, 23, 0, 7, 0, SleepSrc.HEALTH, id = "hc1")
        val before = r.undoLabel
        assertEquals(1, r.mergeSleep(listOf(a)))
        assertEquals(before, r.undoLabel)          // no deja punto de «Deshacer»
        assertEquals(0, r.mergeSleep(listOf(a)))   // igual: nada cambió
        val changed = a.copy(end = a.end + 15 * MIN)
        assertEquals(1, r.mergeSleep(listOf(changed)))
        assertEquals(1, r.sleep.size)
        assertEquals(changed.end, r.sleep.single().end)
        // lo inválido no entra
        assertEquals(0, r.mergeSleep(listOf(SleepSession("bad", a.start, a.start + 5 * MIN, SleepSrc.HEALTH))))
        assertEquals(1, r.sleep.size)
    }

    @Test fun manualWinsOverAutomaticInTheRepoView() {
        val r = repo()
        r.mergeSleep(listOf(night(today, 23, 0, 7, 0, SleepSrc.HEALTH, id = "hc1"), night(today.minusDays(1), 0, 20, 7, 40, SleepSrc.PHONE, id = "phone_1")))
        r.addSleep(at(today.minusDays(1), 23, 30), at(today, 6, 45))
        val nights = r.sleepNights()
        assertEquals(2, nights.size)
        assertEquals(SleepSrc.MANUAL, nights[0].main.source)
        assertEquals(SleepSrc.PHONE, nights[1].main.source)
    }

    @Test fun sleepSurvivesRestart() {
        val dir = Files.createTempDirectory("fitstat-sleep-save").toFile()
        val r = repo(dir)
        val wake = at(today, 7)
        r.addSleep(wake - 8 * H, wake, "persistente")
        r.mergeSleep(listOf(SleepSession("hc9", wake - 30 * H, wake - 22 * H, SleepSrc.HEALTH, stages = listOf(SleepStage(SleepStageType.DEEP, wake - 28 * H, wake - 26 * H)), origin = "com.fitbit")))
        r.flush()
        val again = repo(dir)
        assertEquals(2, again.sleep.size)
        assertEquals("persistente", again.sleep.first { it.source == SleepSrc.MANUAL }.note)
        val hc = again.sleep.first { it.id == "hc9" }
        assertEquals(SleepStageType.DEEP, hc.stages.single().t)
        assertEquals("com.fitbit", hc.origin)
    }

    @Test fun dataFromBeforeSleepStillLoads() {
        // Un data.json de la v1.6 no tiene el campo «sleep»: la app tiene que abrirlo igual y sin perder nada.
        val dir = Files.createTempDirectory("fitstat-sleep-old").toFile()
        val r = repo(dir)
        r.saveNote(Note(id = "", title = "antes", body = "una nota de antes"))
        r.flush()
        val f = File(dir, "data.json")
        val text = f.readText()
        assertTrue(text.contains("\"sleep\""))
        // Se quita el campo «sleep» como estaba en la v1.6 (quitándolo del JSON, no a golpe de texto).
        val old = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        f.writeText(kotlinx.serialization.json.JsonObject(old.filterKeys { it != "sleep" }).toString())
        assertFalse(f.readText().contains("\"sleep\""))
        val again = repo(dir)
        assertTrue(again.sleep.isEmpty())
        assertEquals(1, again.data.notes.size)
    }

    // ───────── Ajustes del sueño

    @Test fun sleepSettings() {
        val goal = Settings.byKey["sleep.goal"]!!
        assertEquals("8", goal.default)
        assertEquals("7.5", Settings.normalize(goal, "7.5"))
        assertEquals("fuera de rango se queda en el máximo", "10", Settings.normalize(goal, "14"))
        assertEquals("y en el mínimo", "5", Settings.normalize(goal, "2"))
        assertTrue(Settings.byKey["sleep.auto_sync"]!!.default == "true")
        assertEquals("3", Settings.byKey["sleep.phone_gap"]!!.default)
        assertEquals(8.0, repo().sleepGoal, 0.0)
    }

}
