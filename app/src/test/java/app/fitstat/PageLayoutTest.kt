package app.fitstat

import app.fitstat.bot.BotHost
import app.fitstat.bot.ToolCtx
import app.fitstat.bot.allTools
import app.fitstat.data.*
import app.fitstat.ui.app.PageEditor
import app.fitstat.ui.app.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

/** v1.8: la distribución de Ejercicios y Rutinas se edita como la del inicio, y ahí caben los widgets del inicio. */
class PageLayoutTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = java.time.LocalDate.of(2026, 10, 28).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()

    private fun repo(dir: File = Files.createTempDirectory("fitstat-pages").toFile()) =
        Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }

    private fun ids(l: List<HomeSlot>) = l.map { it.id }

    // ───────── Catálogo y reglas

    @Test fun exercisesDefaultMatchesTheOldScreen() {
        val d = HomeLayout.default(LayoutPage.EXERCISES)
        val i = ids(d)
        assertEquals("ex_search", i.first())
        assertEquals("ex_set", i[1])
        assertEquals("ex_create", i.last())
        val cats = d.filter { it.id.startsWith(HomeLayout.CAT_PREFIX) }
        assertEquals(Categories.all.size, cats.size)
        assertTrue("las categorías van de dos en dos como siempre", cats.all { it.size == "m" })
        assertEquals("l", d.first { it.id == "ex_search" }.size)
    }

    @Test fun routinesDefaultHasOneCardPerRoutineThenFreeWorkout() {
        val d = HomeLayout.default(LayoutPage.ROUTINES, listOf("a", "b"))
        assertEquals(listOf("rt:a", "rt:b", "rt_free"), ids(d))
        assertTrue(d.all { it.size == "l" })
        assertEquals(listOf("rt_free"), ids(HomeLayout.default(LayoutPage.ROUTINES)))
    }

    @Test fun everyBlockIsConsistent() {
        val seen = HashSet<String>()
        for (d in HomeLayout.all + HomeLayout.natives) {
            assertTrue("id repetido ${d.id}", seen.add(d.id))
            assertTrue("${d.id}: el tamaño por defecto debe estar entre los que admite", d.default in d.sizes)
            assertNotNull(HomeLayout.def(d.id))
        }
        assertNull(HomeLayout.def("rt:"))
        assertNull(HomeLayout.def("zzz"))
        assertTrue(HomeLayout.def("rt:abc")!!.page == LayoutPage.ROUTINES)
        assertFalse("las rutinas no se quitan", HomeLayout.def("rt:abc")!!.removable)
    }

    @Test fun hubWidgetsFitEverywhereButOwnBlocksOnlyInTheirPage() {
        val mix = listOf(HomeSlot("goal", "l"), HomeSlot("ex_set", "l"), HomeSlot("rt:x", "l"), HomeSlot("cat:pecho", "m"), HomeSlot("rt_free", "l"))
        assertEquals(listOf("goal"), ids(HomeLayout.sanitize(mix, LayoutPage.HOME)))
        assertEquals(listOf("goal", "ex_set", "cat:pecho"), ids(HomeLayout.sanitize(mix, LayoutPage.EXERCISES)))
        assertEquals(listOf("goal", "rt:x", "rt_free"), ids(HomeLayout.sanitize(mix, LayoutPage.ROUTINES)))
    }

    @Test fun sanitizeFixesSizesPerBlock() {
        // una categoría no admite «pequeño»; el buscador solo «grande»
        val out = HomeLayout.sanitize(listOf(HomeSlot("cat:pecho", "s"), HomeSlot("ex_search", "m"), HomeSlot("ex_set", "zzz")), LayoutPage.EXERCISES)
        assertEquals(listOf("m", "l", "l"), out.map { it.size })
    }

    @Test fun addValidatesThePage() {
        val base = HomeLayout.default(LayoutPage.EXERCISES).filter { it.id != "ex_create" && it.id != "ex_set" }
        assertEquals("ex_create", HomeLayout.add(base, "ex_create", LayoutPage.EXERCISES).last().id)
        assertEquals("un widget del inicio entra", "goal", HomeLayout.add(base, "goal", LayoutPage.EXERCISES).last().id)
        assertEquals("un bloque de otra pantalla no", base, HomeLayout.add(base, "rt_free", LayoutPage.EXERCISES))
        assertEquals("las tarjetas de rutina se ponen solas", base, HomeLayout.add(base, "rt:a", LayoutPage.ROUTINES))
        assertEquals("ya está", base, HomeLayout.add(base, "ex_search", LayoutPage.EXERCISES))
    }

    @Test fun missingListsOwnBlocksThenHubWidgets() {
        val have = listOf(HomeSlot("ex_search", "l"))
        val m = HomeLayout.missing(have, LayoutPage.EXERCISES)
        val first = m.first()
        assertEquals(LayoutPage.EXERCISES, first.page)
        val firstHub = m.indexOfFirst { it.page == null }
        assertTrue(m.take(firstHub).all { it.page == LayoutPage.EXERCISES })
        assertTrue(m.drop(firstHub).all { it.page == null })
        assertEquals(HomeLayout.all.size, m.count { it.page == null })
        assertTrue("el inicio solo ofrece widgets", HomeLayout.missing(emptyList(), LayoutPage.HOME).all { it.page == null })
        // de Rutinas solo se ofrece el entrenamiento libre (las rutinas ya están todas)
        assertEquals(listOf("rt_free"), HomeLayout.missing(emptyList(), LayoutPage.ROUTINES).filter { it.page != null }.map { it.id })
    }

    @Test fun routineBlocksMoveAndResizeButNeverGoAway() {
        val l = listOf(HomeSlot("rt:a", "l"), HomeSlot("rt:b", "l"))
        assertEquals(l, HomeLayout.remove(l, "rt:a"))
        assertEquals(listOf("rt:b", "rt:a"), ids(HomeLayout.move(l, "rt:a", 1)))
        assertEquals("m", HomeLayout.resize(l, "rt:a", -1).first().size)
        assertNull("no hay más chico que mediano", HomeLayout.nextSize(HomeSlot("rt:a", "m"), -1))
    }

    @Test fun routinesFollowTheRoutinesThatExist() {
        val stored = listOf(HomeSlot("rt:b", "m"), HomeSlot("goal", "l"), HomeSlot("rt:gone", "l"), HomeSlot("rt:a", "l"), HomeSlot("rt_free", "l"))
        // la borrada desaparece; la nueva (c) queda junto a las otras
        val out = HomeLayout.withRoutines(stored, listOf("a", "b", "c"))
        assertEquals(listOf("rt:b", "goal", "rt:a", "rt:c", "rt_free"), ids(out))
        assertEquals("m", out.first { it.id == "rt:b" }.size)
        // sin tarjetas de rutina, la primera va antes del entrenamiento libre
        assertEquals(listOf("goal", "rt:a", "rt_free"), ids(HomeLayout.withRoutines(listOf(HomeSlot("goal", "l"), HomeSlot("rt_free", "l")), listOf("a"))))
        // y sin entrenamiento libre, al final
        assertEquals(listOf("goal", "rt:a"), ids(HomeLayout.withRoutines(listOf(HomeSlot("goal", "l")), listOf("a"))))
        assertEquals(stored.filter { it.id != "rt:gone" }, HomeLayout.withRoutines(stored, listOf("a", "b")))
    }

    // ───────── Repo

    @Test fun repoStoresEachPageSeparately() {
        val dir = Files.createTempDirectory("fitstat-pages").toFile()
        val r = repo(dir)
        assertFalse(r.pageCustomized(LayoutPage.EXERCISES))
        assertEquals(ids(HomeLayout.default(LayoutPage.EXERCISES)), ids(r.pageSlots(LayoutPage.EXERCISES)))
        r.setPageLayout(LayoutPage.EXERCISES, listOf(HomeSlot("cat:pecho", "l"), HomeSlot("goal", "m"), HomeSlot("rt_free", "l")))
        assertEquals("rt_free no va en Ejercicios", listOf("cat:pecho", "goal"), ids(r.pageSlots(LayoutPage.EXERCISES)))
        assertTrue(r.pageCustomized(LayoutPage.EXERCISES))
        assertFalse("el inicio y Rutinas no se tocan", r.pageCustomized(LayoutPage.HOME) || r.pageCustomized(LayoutPage.ROUTINES))
        assertEquals(ids(HomeLayout.default()), ids(r.homeSlots))
        r.flush()
        val r2 = repo(dir)
        assertEquals(listOf("cat:pecho", "goal"), ids(r2.pageSlots(LayoutPage.EXERCISES)))
        assertEquals("Ejercicios personalizado", r.undo())
        assertEquals(ids(HomeLayout.default(LayoutPage.EXERCISES)), ids(r.pageSlots(LayoutPage.EXERCISES)))
        // null = volver a «nunca personalizada»
        r2.setPageLayout(LayoutPage.ROUTINES, listOf(HomeSlot("music", "m")))
        assertTrue(r2.pageCustomized(LayoutPage.ROUTINES))
        r2.setPageLayout(LayoutPage.ROUTINES, null)
        assertFalse(r2.pageCustomized(LayoutPage.ROUTINES))
        // por la puerta del inicio también llega
        r2.setPageLayout(LayoutPage.HOME, listOf(HomeSlot("notes", "l")))
        assertEquals(listOf("notes"), ids(r2.homeSlots))
    }

    @Test fun routinesPageGrowsAndShrinksWithTheRoutines() {
        val r = repo()
        assertEquals(listOf("rt_free"), ids(r.pageSlots(LayoutPage.ROUTINES)))
        val a = r.createRoutine("Pecho", exerciseIds = emptyList())
        val b = r.createRoutine("Pierna", exerciseIds = emptyList())
        assertEquals(listOf("rt:${a.id}", "rt:${b.id}", "rt_free"), ids(r.pageSlots(LayoutPage.ROUTINES)))
        // el usuario acomoda: pone el widget de racha arriba y Pierna antes que Pecho
        r.setPageLayout(LayoutPage.ROUTINES, listOf(HomeSlot("streak", "m"), HomeSlot("rt:${b.id}", "m"), HomeSlot("rt:${a.id}", "l"), HomeSlot("rt_free", "l")))
        val c = r.createRoutine("Espalda", exerciseIds = emptyList())
        assertEquals(listOf("streak", "rt:${b.id}", "rt:${a.id}", "rt:${c.id}", "rt_free"), ids(r.pageSlots(LayoutPage.ROUTINES)))
        r.deleteRoutine(a.id)
        assertEquals(listOf("streak", "rt:${b.id}", "rt:${c.id}", "rt_free"), ids(r.pageSlots(LayoutPage.ROUTINES)))
        assertEquals("m", r.pageSlots(LayoutPage.ROUTINES).first { it.id == "rt:${b.id}" }.size)
    }

    @Test fun oldDataWithoutPageLayoutsLoads() {
        val dir = Files.createTempDirectory("fitstat-pages").toFile()
        File(dir, "data.json").writeText("""{"schema":1,"homeLayout":[{"id":"goal","size":"l"}]}""")
        val r = repo(dir)
        assertTrue(r.data.pageLayouts.isEmpty())
        assertEquals(listOf("goal"), ids(r.homeSlots))
        assertEquals(ids(HomeLayout.default(LayoutPage.EXERCISES)), ids(r.pageSlots(LayoutPage.EXERCISES)))
    }

    // ───────── Editor

    @Test fun editorWorksOnItsOwnPageAndUndoesAtOnce() {
        val r = repo()
        val e = PageEditor(r, LayoutPage.EXERCISES)
        assertEquals("exercises", e.tag)
        assertEquals("Ejercicios", e.title)
        e.start()
        assertTrue(e.remove("ex_create"))
        assertTrue(e.move("cat:pierna", 0))
        assertTrue(e.resize("cat:pecho", +1))
        assertTrue("un widget del inicio entra", e.add("streak"))
        assertFalse("uno de Rutinas no", e.add("rt_free"))
        assertNull("nada se guarda hasta terminar", r.data.pageLayouts[LayoutPage.EXERCISES])
        assertTrue(e.finish())
        val s = r.pageSlots(LayoutPage.EXERCISES)
        assertEquals("cat:pierna", s.first().id)
        assertEquals("streak", s.last().id)
        assertEquals("l", s.first { it.id == "cat:pecho" }.size)
        assertFalse("ex_create" in ids(s))
        assertEquals("el inicio sigue igual", ids(HomeLayout.default()), ids(r.homeSlots))
        assertEquals("Ejercicios personalizado", r.undo())
        assertEquals(ids(HomeLayout.default(LayoutPage.EXERCISES)), ids(r.pageSlots(LayoutPage.EXERCISES)))
    }

    @Test fun editorResetGoesBackToThePageDefault() {
        val r = repo()
        r.createRoutine("Pecho", exerciseIds = emptyList())
        val e = PageEditor(r, LayoutPage.ROUTINES)
        e.start()
        assertFalse("las tarjetas de rutina no se quitan", e.remove(e.slots.first().id))
        assertTrue(e.add("goal"))
        assertTrue(e.reset())
        assertEquals(ids(r.defaultSlots(LayoutPage.ROUTINES)), ids(e.slots))
        assertFalse("nada que guardar", e.finish())
    }

    // ───────── FITBOT

    private class Host(override val repo: Repo) : BotHost {
        override fun currentScreen() = "home"
        override fun navigate(route: Route) {}
        override fun startWorkout(routineId: String?, exerciseIds: List<String>) {}
        override fun workoutActive() = false
    }

    private fun run(host: Host, name: String, json: String) =
        allTools.first { it.name == name }.run(Json.parseToJsonElement(json) as JsonObject, ToolCtx(host))

    @Test fun botReadsAndEditsExercisesAndRoutines() {
        val r = repo()
        val a = r.createRoutine("Pecho y tríceps", exerciseIds = emptyList())
        val b = r.createRoutine("Pierna", exerciseIds = emptyList())
        val host = Host(r)

        val read = run(host, "get_home_layout", """{"page":"ejercicios"}""")
        assertTrue(read.ok)
        assertEquals("exercises", read.json["page"]!!.jsonPrimitive.content)
        assertEquals(HomeLayout.default(LayoutPage.EXERCISES).size, read.json["layout"]!!.jsonArray.size)
        val avail = read.json["available_to_add"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertTrue("goal" in avail && "music" in avail)

        // Ejercicios: por id, por nombre de categoría y con un widget del inicio
        val out = run(host, "edit_home", """{"page":"ejercicios","remove":["ex_create"],"move":[{"id":"Pierna","position":1}],"resize":[{"id":"cat:pecho","size":"l"}],"add":["racha"]}""")
        assertTrue(out.json.toString(), out.ok)
        val ex = r.pageSlots(LayoutPage.EXERCISES)
        assertEquals("cat:pierna", ex.first().id)
        assertEquals("streak", ex.last().id)
        assertFalse("ex_create" in ids(ex))
        assertEquals("l", ex.first { it.id == "cat:pecho" }.size)
        assertEquals("el inicio no cambió", ids(HomeLayout.default()), ids(r.homeSlots))

        // Rutinas: por nombre de rutina
        val rt = run(host, "edit_home", """{"page":"rutinas","move":[{"id":"Pierna","position":1}],"resize":[{"id":"Pecho","size":"m"}],"add":["goal"]}""")
        assertTrue(rt.json.toString(), rt.ok)
        val slots = r.pageSlots(LayoutPage.ROUTINES)
        assertEquals("rt:${b.id}", slots.first().id)
        assertEquals("m", slots.first { it.id == "rt:${a.id}" }.size)
        assertEquals("goal", slots.last().id)
        val layout = run(host, "get_home_layout", """{"page":"rutinas"}""").json["layout"]!!.jsonArray
        assertEquals("el título de la tarjeta es el nombre de la rutina", "Pierna", layout.first().jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(false, layout.first().jsonObject["removable"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun botCannotRemoveARoutineCardNorUseAnUnknownPage() {
        val r = repo()
        r.createRoutine("Pecho", exerciseIds = emptyList())
        val host = Host(r)
        val rm = run(host, "edit_home", """{"page":"rutinas","remove":["Pecho"]}""")
        assertFalse(rm.ok)
        assertTrue(rm.json["notes"]!!.jsonArray.first().jsonPrimitive.content.contains("no se quita"))
        assertFalse(run(host, "edit_home", """{"page":"nada","reset":true}""").ok)
        assertFalse(run(host, "get_home_layout", """{"page":"nada"}""").ok)
        // un bloque de otra pantalla se avisa
        val wrong = run(host, "edit_home", """{"page":"ejercicios","add":["rt_free"]}""")
        assertFalse(wrong.ok)
        // sin page, sigue siendo el inicio
        val home = run(host, "edit_home", """{"remove":["heatmap"]}""")
        assertTrue(home.ok)
        assertEquals("home", home.json["page"]!!.jsonPrimitive.content)
        assertFalse(r.homeSlots.any { it.id == "heatmap" })
    }
}
