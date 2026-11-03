package app.fitstat

import app.fitstat.data.*
import app.fitstat.bot.BotHost
import app.fitstat.bot.ToolCtx
import app.fitstat.bot.allTools
import app.fitstat.ui.app.HomeEditor
import app.fitstat.ui.app.Route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

/** v1.6: inicio personalizable (lógica de la disposición y edición con un solo «Deshacer»). */
class HomeLayoutTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = java.time.LocalDate.of(2026, 10, 28).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()

    private fun repo(dir: File = Files.createTempDirectory("fitstat-home").toFile()) =
        Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }

    private fun ids(l: List<HomeSlot>) = l.map { it.id }

    // ───────── Catálogo

    @Test fun catalogIsConsistent() {
        val seen = HashSet<String>()
        for (d in HomeLayout.all) {
            assertTrue("id repetido ${d.id}", seen.add(d.id))
            assertTrue("${d.id}: el tamaño por defecto debe estar entre los que admite", d.default in d.sizes)
            assertEquals("${d.id}: tamaños de menor a mayor", d.sizes.sortedBy { it.span }, d.sizes)
            assertEquals("${d.id}: sin tamaños repetidos", d.sizes.size, d.sizes.toSet().size)
            assertNotEquals("${d.id}: el ícono ${d.icon} no existe", app.fitstat.ui.design.Ic.Circle, app.fitstat.ui.design.icOf(d.icon))
            d.legacyKey?.let { k -> assertTrue("${d.id}: ajuste antiguo $k", k.startsWith("home.show_") && Settings.byKey[k] == null) }
        }
        // la disposición de siempre trae todo menos lo que se agrega a mano (el widget de música)
        assertEquals(HomeLayout.all.filter { !it.optIn }.map { it.id }, ids(HomeLayout.default()))
        assertEquals(listOf("music"), HomeLayout.all.filter { it.optIn }.map { it.id })
    }

    @Test fun optInWidgetsAreOfferedButNotPlacedByDefault() {
        assertFalse("music" in ids(HomeLayout.default()))
        assertFalse("music" in ids(HomeLayout.legacyDefault(emptyMap())))
        assertTrue("sleep" in ids(HomeLayout.default()))
        // se ofrece para agregar y se puede agregar como cualquier otro
        assertEquals(listOf("music"), HomeLayout.missing(HomeLayout.default()).map { it.id })
        val l = HomeLayout.add(HomeLayout.default(), "music")
        assertEquals("music", l.last().id)
        assertEquals("m", l.last().size)
        assertEquals(emptyList<String>(), HomeLayout.missing(l).map { it.id })
        // y una disposición guardada que lo trae se respeta tal cual
        assertTrue("music" in ids(HomeLayout.effective(l, emptyMap())))
    }

    @Test fun spansAreSmallMediumLarge() {
        assertEquals(2, HomeSize.Small.span)
        assertEquals(3, HomeSize.Medium.span)
        assertEquals(6, HomeSize.Large.span)
        assertTrue(HomeLayout.COLUMNS % HomeSize.Small.span == 0 && HomeLayout.COLUMNS % HomeSize.Medium.span == 0)
        assertEquals(HomeSize.Medium, HomeSize.of("m"))
        assertNull(HomeSize.of("x"))
    }

    // ───────── Acomodo

    @Test fun packFillsRows() {
        assertEquals(emptyList<List<Int>>(), HomeLayout.pack(emptyList()))
        // 2+2+2 | 3+3 | 6 | 3 (el de 3 no cabe tras un 6)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4), listOf(5), listOf(6)), HomeLayout.pack(listOf(2, 2, 2, 3, 3, 6, 3)))
        // 3+2 deja un hueco de 1; el siguiente 2 no cabe y baja
        assertEquals(listOf(listOf(0, 1), listOf(2)), HomeLayout.pack(listOf(3, 2, 2)))
        // los tamaños raros se acotan a 1..6 y nunca se pierde un widget
        val rows = HomeLayout.pack(listOf(0, 9, -3, 4, 4))
        assertEquals(5, rows.sumOf { it.size })
        assertTrue(rows.all { it.isNotEmpty() })
    }

    @Test fun defaultLayoutRowsLookRight() {
        val spans = HomeLayout.default().map { HomeLayout.size(it).span }
        val rows = HomeLayout.pack(spans)
        // Meta y Hoy van a lo ancho; Entrenar + FITBOT comparten fila; los tres atajos también; Racha + Volumen también.
        val byRow = rows.map { r -> r.map { HomeLayout.default()[it].id } }
        assertEquals(listOf("goal"), byRow[0])
        assertEquals(listOf("today"), byRow[1])
        assertEquals(listOf("train", "fitbot"), byRow[2])
        assertEquals(listOf("progress", "milestones", "notes"), byRow[3])
        assertEquals(listOf("streak", "volume"), byRow[4])
    }

    // ───────── Operaciones

    @Test fun resizeDoesNotWrapAround() {
        val l = listOf(HomeSlot("progress", "s"), HomeSlot("goal", "l"))
        assertEquals("m", HomeLayout.resize(l, "progress", +1)[0].size)
        assertEquals("l", HomeLayout.resize(HomeLayout.resize(l, "progress", +1), "progress", +1)[0].size)
        // en el límite no hace nada
        assertSame(l, HomeLayout.resize(l, "progress", -1))
        assertSame(l, HomeLayout.resize(l, "goal", +1))
        // «Meta semanal» solo admite mediano y grande
        assertEquals("m", HomeLayout.resize(l, "goal", -1)[1].size)
        assertFalse(HomeLayout.canResize(l[0], -1))
        assertTrue(HomeLayout.canResize(l[0], +1))
        // un widget que solo admite un tamaño no cambia
        val one = HomeSlot("heatmap", "l")
        assertFalse(HomeLayout.canResize(one, +1) || HomeLayout.canResize(one, -1))
        // id desconocido: no pasa nada
        assertSame(l, HomeLayout.resize(l, "nada", +1))
    }

    @Test fun setSizePicksClosestSupported() {
        val l = listOf(HomeSlot("muscles", "l"), HomeSlot("goal", "l"))
        assertEquals("l", HomeLayout.setSize(l, "muscles", HomeSize.Small)[0].size) // solo admite grande
        assertEquals("m", HomeLayout.setSize(l, "goal", HomeSize.Small)[1].size)    // el más parecido que admite
    }

    @Test fun moveReordersAndClamps() {
        val l = listOf("a", "b", "c", "d").map { HomeSlot(it) }
        assertEquals(listOf("b", "c", "a", "d"), ids(HomeLayout.move(l, "a", 2)))
        assertEquals(listOf("d", "a", "b", "c"), ids(HomeLayout.move(l, "d", 0)))
        assertEquals(listOf("b", "c", "d", "a"), ids(HomeLayout.move(l, "a", 99)))
        assertSame(l, HomeLayout.move(l, "a", 0))
        assertSame(l, HomeLayout.move(l, "zzz", 1))
    }

    @Test fun addRemoveAndMissing() {
        var l = HomeLayout.default()
        l = HomeLayout.remove(l, "heatmap")
        l = HomeLayout.remove(l, "streak")
        assertEquals(listOf("streak", "heatmap", "music"), HomeLayout.missing(l).map { it.id })
        l = HomeLayout.add(l, "heatmap")
        assertEquals("heatmap", l.last().id)
        assertEquals("l", l.last().size)
        assertSame(l, HomeLayout.add(l, "heatmap"))   // no se repite
        assertSame(l, HomeLayout.add(l, "nada"))      // ni se agrega lo que no existe
        assertEquals(listOf("streak", "music"), HomeLayout.missing(l).map { it.id })
    }

    @Test fun sanitizeCleansBadData() {
        val dirty = listOf(
            HomeSlot("goal", "s"),        // no admite pequeño → el más cercano
            HomeSlot("goal", "l"),        // repetido
            HomeSlot("fantasma", "m"),    // no existe
            HomeSlot("train", "??"),      // tamaño inválido → el de siempre
            HomeSlot("notes", "l"),
        )
        val clean = HomeLayout.sanitize(dirty)
        assertEquals(listOf("goal", "train", "notes"), ids(clean))
        assertEquals(listOf("m", "m", "l"), clean.map { it.size })
        assertEquals(clean, HomeLayout.sanitize(clean)) // idempotente
    }

    // ───────── Migración de «Tarjetas visibles» (antes de v1.6)

    @Test fun legacyTogglesAreRespected() {
        assertEquals(ids(HomeLayout.default()), ids(HomeLayout.legacyDefault(emptyMap())))
        val off = HomeLayout.legacyDefault(mapOf("home.show_goal" to "false", "home.show_heatmap" to "false", "home.show_streak" to "false"))
        assertFalse("goal" in ids(off))
        assertFalse("heatmap" in ids(off))
        // «Racha y volumen» era un solo interruptor
        assertFalse("streak" in ids(off) || "volume" in ids(off))
        // lo que no tenía interruptor siempre estaba
        assertTrue(listOf("train", "fitbot", "progress", "milestones", "notes").all { it in ids(off) })
        // lo que dejó prendido sigue ahí
        assertEquals(HomeLayout.default().size - 4, off.size)
    }

    @Test fun effectiveUsesStoredLayoutWhenCustomized() {
        val stored = listOf(HomeSlot("notes", "m"), HomeSlot("goal", "l"))
        // si ya personalizó, los ajustes viejos no cuentan
        assertEquals(stored, HomeLayout.effective(stored, mapOf("home.show_goal" to "false")))
        assertEquals(emptyList<HomeSlot>(), HomeLayout.effective(emptyList(), emptyMap()))   // vacío a propósito
    }

    // ───────── Repo y edición

    @Test fun repoStoresLayoutAndUndoRestoresIt() {
        val dir = Files.createTempDirectory("fitstat-home").toFile()
        val r = repo(dir)
        assertNull(r.data.homeLayout)
        assertEquals(ids(HomeLayout.default()), ids(r.homeSlots))
        r.setHomeLayout(listOf(HomeSlot("goal", "m"), HomeSlot("notes", "l")))
        assertEquals(listOf("goal", "notes"), ids(r.homeSlots))
        r.flush()
        val r2 = repo(dir)
        assertEquals(listOf("goal", "notes"), ids(r2.homeSlots))
        assertEquals(listOf("m", "l"), r2.homeSlots.map { it.size })
        assertEquals("Inicio personalizado", r.undo())
        assertEquals(ids(HomeLayout.default()), ids(r.homeSlots))
        // null = volver a «nunca personalizado»
        r.setHomeLayout(null)
        assertNull(r.data.homeLayout)
    }

    @Test fun repoOldDataWithoutLayoutLoads() {
        val dir = Files.createTempDirectory("fitstat-home").toFile()
        // datos guardados por la v1.5.x: sin «homeLayout»
        File(dir, "data.json").writeText("""{"schema":1,"settings":{"home.show_records":"false"}}""")
        val r = repo(dir)
        assertNull(r.data.homeLayout)
        assertFalse("records" in ids(r.homeSlots))
        assertTrue("goal" in ids(r.homeSlots))
    }

    @Test fun editorCommitsOnceAndUndoRevertsAll() {
        val r = repo()
        val e = HomeEditor(r)
        assertFalse(e.active)
        assertFalse("sin editar no se puede cambiar nada", e.remove("goal"))
        e.start()
        assertTrue(e.active)
        assertTrue(e.remove("heatmap"))
        assertTrue(e.remove("muscles"))
        assertTrue(e.resize("train", +1))
        assertTrue(e.move("notes", 0))
        assertEquals("notes", e.slots.first().id)
        // nada se guarda hasta terminar
        assertNull(r.data.homeLayout)
        val before = r.homeSlots
        assertTrue(e.finish())
        assertFalse(e.active)
        assertEquals(e.slots.map { it.id }, r.homeSlots.map { it.id })
        assertEquals("notes", r.homeSlots.first().id)
        assertFalse("heatmap" in ids(r.homeSlots))
        assertEquals("l", r.homeSlots.first { it.id == "train" }.size)
        // un solo Deshacer revierte toda la sesión
        assertEquals("Inicio personalizado", r.undo())
        assertEquals(before, r.homeSlots)
    }

    @Test fun editorCancelAndNoChange() {
        val r = repo()
        val e = HomeEditor(r)
        e.start()
        e.remove("goal")
        e.cancel()
        assertFalse(e.active)
        assertNull(r.data.homeLayout)
        e.start()
        assertFalse("terminar sin cambios no guarda nada", e.finish())
        assertNull(r.data.homeLayout)
    }

    @Test fun editorAddResetAndEmpty() {
        val r = repo()
        val e = HomeEditor(r)
        e.start()
        ids(HomeLayout.default()).forEach { e.remove(it) }
        assertTrue(e.slots.isEmpty())
        val tick = e.addedTick
        assertTrue(e.add("fitbot"))
        assertEquals(tick + 1, e.addedTick)
        assertFalse("ya está", e.add("fitbot"))
        assertTrue(e.finish())
        assertEquals(listOf("fitbot"), ids(r.homeSlots))
        // un inicio vacío es válido y se conserva
        e.start()
        e.remove("fitbot")
        e.finish()
        assertEquals(emptyList<HomeSlot>(), r.homeSlots)
        assertNotNull(r.data.homeLayout)
        // restablecer
        e.start()
        assertTrue(e.reset())
        e.finish()
        assertEquals(ids(HomeLayout.default()), ids(r.homeSlots))
    }

    @Test fun changelogAnnouncesVersion16() {
        // la v1.6 contó 14 widgets: es lo que decía entonces y se queda así en el historial
        val e = Changelog.entries.first { it.version == "1.6" }
        val text = e.sections.flatMap { it.second }.joinToString("\n")
        assertTrue(text.contains("14 widgets"))
        assertTrue(text.contains("selector de modelo"))
    }


    // ───────── FITBOT: get_home_layout y edit_home

    private class Host(override val repo: Repo) : BotHost {
        var saved = 0
        override fun currentScreen() = "home"
        override fun navigate(route: Route) {}
        override fun startWorkout(routineId: String?, exerciseIds: List<String>) {}
        override fun workoutActive() = false
        override fun saveHomeEdit() { saved++ }
    }

    private fun runTool(r: Repo, host: Host, name: String, json: String): app.fitstat.bot.ToolOutcome =
        allTools.first { it.name == name }.run(Json.parseToJsonElement(json) as JsonObject, ToolCtx(host))

    @Test fun botCanReadAndEditTheHome() {
        val r = repo()
        val host = Host(r)
        val read = runTool(r, host, "get_home_layout", "{}")
        assertTrue(read.ok)
        assertEquals(HomeLayout.default().size, read.json["layout"]!!.jsonArray.size)
        // el de música no viene puesto, pero FITBOT sabe que se puede agregar
        assertEquals(listOf("music"), read.json["available_to_add"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })

        val out = runTool(
            r, host, "edit_home",
            """{"remove":["heatmap","Mapa muscular"],"resize":[{"id":"progress","size":"grande"},{"id":"streak","size":"s"}],"move":[{"id":"notes","position":1}]}""",
        )
        assertTrue(out.json.toString(), out.ok)
        assertEquals("el asistente guarda lo que el usuario llevaba editando", 1, host.saved)
        val now = r.homeSlots
        assertFalse(now.any { it.id == "heatmap" || it.id == "muscles" })
        assertEquals("notes", now.first().id)
        assertEquals("l", now.first { it.id == "progress" }.size)
        assertEquals("s", now.first { it.id == "streak" }.size)
        // se puede deshacer de un golpe
        assertNotNull(r.undo())
        assertEquals(HomeLayout.default().map { it.id }, r.homeSlots.map { it.id })

        // agregar lo que falta y restablecer
        runTool(r, host, "edit_home", """{"remove":["racha"]}""")
        assertFalse(r.homeSlots.any { it.id == "streak" })
        val list = runTool(r, host, "get_home_layout", "{}")
        assertEquals(listOf("streak", "music"), list.json["available_to_add"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        assertTrue(runTool(r, host, "edit_home", """{"add":["streak"]}""").ok)
        assertEquals("streak", r.homeSlots.last().id)
        assertTrue(runTool(r, host, "edit_home", """{"reset":true,"remove":["notes"]}""").ok)
        assertEquals(HomeLayout.default().size - 1, r.homeSlots.size)
        // el widget de música se agrega desde el chat como cualquier otro
        assertTrue(runTool(r, host, "edit_home", """{"add":["music"]}""").ok)
        assertEquals("music", r.homeSlots.last().id)
    }

    @Test fun botEditHomeRejectsNonsenseWithoutBreakingAnything() {
        val r = repo()
        val host = Host(r)
        assertFalse("sin cambios", runTool(r, host, "edit_home", "{}").ok)
        val bad = runTool(r, host, "edit_home", """{"remove":["no existe"],"resize":[{"id":"goal","size":"enorme"}]}""")
        assertFalse(bad.ok)
        assertTrue(bad.json["notes"]!!.jsonArray.size >= 2)
        assertNull("no se guardó nada", r.data.homeLayout)
        // un tamaño que el widget no admite usa el más parecido y avisa
        val near = runTool(r, host, "edit_home", """{"resize":[{"id":"muscles","size":"s"}]}""")
        assertFalse("muscles solo admite grande: no hay cambio", near.ok)
        val partial = runTool(r, host, "edit_home", """{"remove":["goal","zzz"]}""")
        assertTrue(partial.ok)
        assertEquals(1, partial.json["notes"]!!.jsonArray.size)
        assertFalse(r.homeSlots.any { it.id == "goal" })
    }

    @Test fun userSettingsDoNotListRemovedHomeToggles() {
        assertTrue(Settings.all.none { it.key.startsWith("home.show_") })
        assertTrue("más de 70 ajustes", Settings.all.size > 70)
    }
}
