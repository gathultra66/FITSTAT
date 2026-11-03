package app.fitstat

import app.fitstat.data.*
import app.fitstat.sys.WidgetKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

class DataTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = java.time.LocalDate.of(2026, 10, 28).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()

    private fun repo(dir: File = Files.createTempDirectory("fitstat").toFile(), clock: () -> Long = { now }) =
        Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), clock) { zone }

    // ───────── Ajustes

    @Test fun settingsDefaultsAreConsistent() {
        val keys = HashSet<String>()
        for (s in Settings.all) {
            assertTrue("clave repetida ${s.key}", keys.add(s.key))
            assertEquals("el valor por defecto de ${s.key} no es válido", s.default, Settings.normalize(s, s.default))
            assertNotNull("la página ${s.page} de ${s.key} no existe", Settings.pages.firstOrNull { it.id == s.page })
            s.visibleIf?.let { (k, _) -> assertTrue("visibleIf de ${s.key} apunta a $k", Settings.byKey.containsKey(k)) }
        }
        assertTrue("debe haber 70 ajustes o más", Settings.all.size >= 70)
    }

    @Test fun settingsNormalize() {
        val speed = Settings.byKey["motion.speed"]!!
        assertEquals("200", Settings.normalize(speed, "999"))
        assertEquals("50", Settings.normalize(speed, "1"))
        assertNull(Settings.normalize(speed, "rápido"))
        val mode = Settings.byKey["theme.mode"]!!
        assertEquals("amoled", Settings.normalize(mode, "Negro AMOLED"))
        assertNull(Settings.normalize(mode, "morado"))
        assertEquals("true", Settings.normalize(Settings.byKey["motion.enabled"]!!, "sí"))
        assertEquals("#FF8800", Settings.normalize(Settings.byKey["theme.custom"]!!, "ff8800"))
        assertEquals("07:05", Settings.normalize(Settings.byKey["reminders.time"]!!, "7:05"))
        assertEquals("0.7", Settings.normalize(Settings.byKey["bot.temperature"]!!, "0.74"))
    }

    @Test fun changelogClaimsMatchReality() {
        val text = Changelog.entries.first { it.version == "1.0" }.sections.flatMap { it.second }.joinToString("\n")
        fun choices(key: String) = (Settings.byKey[key]!!.type as SType.Choice).options.size
        // la v1.0 contó 176 ejercicios; el catálogo creció después (v1.7) y el historial conserva lo que se dijo entonces
        assertTrue("ejercicios: ${Catalog.all.size}", text.contains("176 ejercicios") && Catalog.all.size >= 176)
        assertTrue("hitos: ${Milestones.all.size}", text.contains("${Milestones.all.size} hitos"))
        assertTrue("paletas: ${choices("theme.palette")}", text.contains("${choices("theme.palette")} paletas"))
        assertTrue("tipografías: ${choices("type.font") - 1}", text.contains("${choices("type.font") - 1} tipografías"))
        assertTrue("gráficas: ${choices("stats.chart")}", text.contains("Gráficas en ${choices("stats.chart")} estilos"))
        assertTrue("widgets: ${WidgetKind.values().size}", text.contains("${WidgetKind.values().size} widgets"))
        assertTrue("más de 70 ajustes: ${Settings.all.size}", Settings.all.size > 70 && text.contains("más de 70 ajustes"))
    }

    // ───────── Catálogo

    @Test fun catalogIsValid() {
        assertEquals(10, Categories.all.size)
        val ids = HashSet<String>()
        for (e in Catalog.all) {
            assertTrue("id repetido ${e.id}", ids.add(e.id))
            assertNotNull("categoría de ${e.id}", Categories.byId(e.category))
            assertTrue("sin músculo principal ${e.id}", e.primary.isNotEmpty())
            assertTrue("sin descripción ${e.id}", e.description.length > 10)
            for (i in 0 until e.images) assertTrue("falta foto ${e.id}_$i", File("src/main/assets/ex/${e.id}_$i.jpg").exists())
        }
        assertTrue(Catalog.all.size >= 150)
        for (c in Categories.all) assertTrue("categoría vacía ${c.id}", Catalog.inCategory(c.id).isNotEmpty())
    }

    // ───────── Buscador

    @Test fun searchPredictive() {
        val idx = Search.index(Catalog.all)
        fun top(q: String) = Search.search(idx, q, 5).map { it.ex.name }
        assertEquals("Press de banca con barra", top("press ban").first())
        assertEquals("Press de banca con barra", top("PRESS BANCA").first())
        assertTrue(top("sentadila").any { it.startsWith("Sentadilla") })      // error de dedo
        assertTrue(top("jalon").any { it.contains("Jalón") })                  // sin acento
        assertTrue(top("dominada").first().startsWith("Dominadas"))
        assertTrue(Search.search(idx, "pecho", 50).size >= 10)                 // por categoría / músculo
        assertTrue(Search.search(idx, "bench press", 5).isNotEmpty())          // por alias en inglés
        assertTrue(Search.search(idx, "xqzw", 5).isEmpty())
        assertTrue(Search.search(idx, "", 5).isEmpty())
        val hits = Search.search(idx, "sentadilla con b")
        val ghost = Search.complete(hits, "sentadilla con b")
        assertNotNull(ghost)
        assertEquals("arra", ghost)
        val g2 = Search.complete(Search.search(idx, "press de ban"), "press de ban")
        assertEquals("ca con barra", g2)
        assertNull(Search.complete(hits, "sentadilla "))
        val r = Search.highlight("Press de banca con barra", "banca pre")
        assertEquals(2, r.size)
    }

    @Test fun levenshtein() {
        assertEquals(0, Search.lev("banca", "banca"))
        assertEquals(1, Search.lev("banca", "bnaca"))   // transposición
        assertEquals(1, Search.lev("banca", "banc"))
        assertEquals(3, Search.lev("abc", "xyz"))
    }

    // ───────── Estadísticas

    @Test fun e1rmFormulas() {
        assertEquals(100.0, Stats.e1rm(100.0, 1), 0.001)
        assertEquals(133.33, Stats.e1rm(100.0, 10, "epley"), 0.01)
        assertEquals(133.33, Stats.e1rm(100.0, 10, "brzycki"), 0.01)
        assertEquals(0.0, Stats.e1rm(0.0, 10), 0.0)
        assertEquals(8.0, Stats.score(0.0, 8), 0.0)
    }

    @Test fun streaksAndWeeks() {
        val today = java.time.LocalDate.of(2026, 10, 28) // miércoles
        val mondays = listOf(
            java.time.LocalDate.of(2026, 10, 26), java.time.LocalDate.of(2026, 10, 27), // semana actual: 2 días
            java.time.LocalDate.of(2026, 10, 19), java.time.LocalDate.of(2026, 10, 20), java.time.LocalDate.of(2026, 10, 22),
            java.time.LocalDate.of(2026, 10, 12), java.time.LocalDate.of(2026, 10, 13), java.time.LocalDate.of(2026, 10, 15),
        ).toSet()
        assertEquals(2, Stats.streakWeeks(mondays, 3, true, today))   // esta semana aún no llega a 3; 2 semanas previas sí
        assertEquals(3, Stats.streakWeeks(mondays, 2, true, today))
        assertEquals(2, Stats.bestStreakWeeks(mondays, 3, true))
        assertEquals(0, Stats.streakWeeks(emptySet(), 3, true, today))
        assertEquals(java.time.LocalDate.of(2026, 10, 26), Stats.weekStart(today, true))
        assertEquals(java.time.LocalDate.of(2026, 10, 25), Stats.weekStart(today, false))
    }

    // ───────── Repo

    @Test fun repoLogsRecordsAndUndo() {
        var t = now
        val r = repo(clock = { t })
        assertFalse(r.hasUserData)
        r.addToSet("press_de_banca_con_barra")
        assertTrue(r.inSet("press_de_banca_con_barra"))
        val a = r.logSet("press_de_banca_con_barra", 60.0, 8)
        assertNull("la primera serie no es récord", a.record)
        assertTrue("debe desbloquear 'Primer entrenamiento'", a.milestones.any { it.id == "w1" })
        t += 60_000
        val b = r.logSet("press_de_banca_con_barra", 62.5, 8)
        assertNotNull("62.5 supera 60", b.record)
        t += 60_000
        val c = r.logSet("press_de_banca_con_barra", 50.0, 8)
        assertNull(c.record)
        assertEquals(3, r.logs.size)
        assertEquals("Serie registrada", r.undo())
        assertEquals(2, r.logs.size)
        val st = Stats.forExercise(r.logs, "press_de_banca_con_barra", "epley", zone)
        assertEquals(62.5, st.bestWeight, 0.0)
        assertEquals(1, st.sessions.size)
        assertEquals(2, st.sessions.first().sets.size)
    }

    @Test fun repoSettingsAndPersistence() {
        val dir = Files.createTempDirectory("fitstat").toFile()
        val r = repo(dir)
        assertTrue(r.setSetting("theme.palette", "Menta").isSuccess)
        assertEquals("menta", r.prefs.str("theme.palette"))
        assertTrue(r.setSetting("theme.palette", "morado").isFailure)
        assertTrue(r.setSetting("no.existe", "1").isFailure)
        r.setSetting("motion.speed", "150")
        assertEquals(150, r.prefs.int("motion.speed"))
        r.setSetting("motion.speed", "100")             // vuelve al valor por defecto: se quita del mapa
        assertFalse(r.data.settings.containsKey("motion.speed"))
        val routine = r.createRoutine("Pierna", exerciseIds = listOf("sentadilla_con_barra", "prensa_de_pierna"), days = setOf(1, 4))
        r.logSet("sentadilla_con_barra", 100.0, 5, routine.id)
        r.saveNote(Note("", "Hola", "Prueba"))
        r.flush()
        val r2 = repo(dir)
        assertEquals("menta", r2.prefs.str("theme.palette"))
        assertEquals(1, r2.routines.size)
        assertEquals(2, r2.routines[0].items.size)
        assertEquals(1, r2.logs.size)
        assertEquals(1, r2.notes.size)
    }

    @Test fun repoCustomExerciseAndRoutines() {
        val r = repo()
        val e = r.saveCustomExercise(Exercise("", "  Mi ejercicio raro ", "pecho", "Mancuernas", primary = listOf(Muscle.CHEST), description = "Prueba"))
        assertTrue(e.custom)
        assertTrue(r.exercises.any { it.id == e.id })
        assertEquals("Mi ejercicio raro", r.exercise(e.id)?.name)
        assertTrue(r.search("raro").any { it.ex.id == e.id })
        val rt = r.createRoutine("Mix", exerciseIds = listOf(e.id, "plancha"))
        r.moveInRoutine(rt.id, 0, 1)
        assertEquals("plancha", r.routine(rt.id)!!.items[0].exerciseId)
        r.addToRoutine(rt.id, listOf("plancha", "lagartijas"))
        assertEquals(3, r.routine(rt.id)!!.items.size)
        r.deleteCustomExercise(e.id)
        assertNull(r.exercise(e.id))
        assertEquals(2, r.routine(rt.id)!!.items.size)
        val copy = r.duplicateRoutine(rt.id)!!
        assertEquals("Mix (copia)", copy.name)
        r.deleteRoutine(rt.id)
        assertNull(r.routine(rt.id))
        assertEquals("Rutina eliminada", r.undo())
        assertNotNull(r.routine(rt.id))
    }

    @Test fun backupRoundTrip() {
        val a = repo()
        a.replaceAll(Demo.build(a.data, now, zone))
        assertTrue(a.logs.size > 200)
        assertTrue(a.unlocked.isNotEmpty())
        val json = a.exportJson()
        assertFalse(json.contains("sk-or-"))
        val csv = a.exportCsv()
        assertTrue(csv.lines().size > 200)
        val b = repo()
        val res = b.importJson(json)
        assertTrue(res.isSuccess)
        assertEquals(a.logs.size, b.logs.size)
        assertEquals(a.routines, b.routines)
        assertTrue(b.importJson("esto no es json").isFailure)
        assertTrue(b.importJson("{\"app\":\"OTRA\",\"data\":{}}").isFailure)
    }

    @Test fun demoStats() {
        val r = repo()
        r.replaceAll(Demo.build(r.data, now, zone))
        val today = r.today()
        val days = Stats.workoutDays(r.logs, zone)
        assertTrue("días ${days.size}", days.size in 25..60)
        val streak = Stats.streakWeeks(days, 4, true, today)
        assertTrue("racha $streak", streak >= 0)
        val load = Stats.muscleLoad(r.logs, r.exById, 7, today, zone)
        assertTrue(load.levels.isNotEmpty())
        val heat = Stats.heat(r.logs, 12, true, today, zone)
        assertEquals(12 * 7, heat.cells.size)
        assertTrue(heat.max >= 1)
        val states = r.milestoneStates()
        assertEquals(Milestones.all.size, states.size)
        assertTrue(states.count { it.done } >= 5)
        assertNotNull(Milestones.next(states))
        val recs = Stats.recordEvents(r.logs, "epley")
        assertTrue(recs.isNotEmpty())
        val plan = Stats.todayPlan(r.data, r.exById, java.time.LocalDate.of(2026, 10, 28)) // miércoles = Pierna
        assertEquals(1, plan.routines.size)
        assertEquals("Pierna", plan.routines[0].name)
    }

    @Test fun formatting() {
        assertEquals("82.5", Fmt.num(82.5))
        assertEquals("80", Fmt.num(80.0))
        assertEquals("1,234", Fmt.group(1234))
        assertEquals("82.5 kg", Fmt.weight(82.5, "kg"))
        assertEquals("100 lb", Fmt.weight(45.36, "lb"))
        assertEquals("28 oct", Fmt.dateShort(java.time.LocalDate.of(2026, 10, 28)))
        assertEquals("Miércoles 28 de octubre", Fmt.dateLong(java.time.LocalDate.of(2026, 10, 28)))
        assertEquals("1:05", Fmt.duration(65))
        assertEquals("Lun a Vie", Weekdays.label(setOf(1, 2, 3, 4, 5)))
    }
}
