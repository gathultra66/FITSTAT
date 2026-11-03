package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.HomeLayout
import app.fitstat.data.LayoutPage
import app.fitstat.data.Pages
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.PageEditor
import app.fitstat.ui.app.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.8: Ejercicios y Rutinas se editan igual que el inicio (toque largo en un contenedor o en la pantalla), y aceptan los widgets del inicio.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
class PageEditUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    private fun makeApp(mode: String = "dark"): AppState {
        val dir = Files.createTempDirectory("fitstat-pageedit").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
        repo.setProfileName("Chris")
        repo.setSetting("theme.mode", mode)
        return AppState(ApplicationProvider.getApplicationContext(), repo)
    }

    private fun ui(block: () -> Unit) {
        rule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
    }

    private fun start(app: AppState, tab: Route = Route.Home) {
        rule.setContent { FitstatRoot(app) }
        Shots.settle(rule, 1200)
        if (tab != Route.Home) {
            ui { app.nav.switchTab(tab) }
            Shots.settle(rule, 900)
        }
    }

    private fun tag(t: String) = rule.onNodeWithTag(t)
    private fun ids(e: PageEditor) = e.slots.map { it.id }

    private fun center(t: String): Offset {
        val b = tag(t).fetchSemanticsNode().boundsInRoot
        return Offset((b.left + b.right) / 2f, (b.top + b.bottom) / 2f)
    }

    private fun hold(t: String, ms: Long = 900) {
        tag(t).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(ms)
        tag(t).performTouchInput { up() }
        Shots.settle(rule, 400)
    }

    /** Mantiene apretado un punto cualquiera de la pantalla (donde no hay un contenedor). */
    private fun holdAt(p: Offset, ms: Long = 900) {
        rule.onRoot().performTouchInput { down(p) }
        rule.mainClock.advanceTimeBy(ms)
        rule.onRoot().performTouchInput { up() }
        Shots.settle(rule, 400)
    }

    private fun drag(from: String, to: String, holdMs: Long = 0) {
        val delta = (center(to) - center(from)) / 14f
        tag(from).performTouchInput { down(center) }
        if (holdMs > 0) rule.mainClock.advanceTimeBy(holdMs)
        repeat(14) {
            tag(from).performTouchInput { moveBy(delta) }
            rule.mainClock.advanceTimeBy(48)
        }
        tag(from).performTouchInput { up() }
        Shots.settle(rule, 700)
    }

    private val firstCat get() = HomeLayout.catId(app.fitstat.data.Categories.all.first().id)

    // ───────── Ejercicios

    @Test
    fun longPressOnAContainerOpensTheExercisesEditor() {
        val app = makeApp()
        start(app, Route.Exercises)
        assertFalse(app.exercisesLayout.active)
        tag("exercises_slot_$firstCat").assertIsDisplayed()
        tag("exercises_done").assertDoesNotExist()
        Shots.capture(rule, "v18_exercises_normal")
        hold("exercises_slot_$firstCat")
        assertTrue("el toque largo abre el editor de Ejercicios", app.exercisesLayout.active)
        assertFalse("no toca el del inicio", app.home.active)
        assertEquals("no abre ninguna categoría al soltar", Route.Exercises, app.nav.top)
        tag("exercises_done").assertIsDisplayed()
        tag("exercises_remove_$firstCat").assertIsDisplayed()
        tag("exercises_grip_$firstCat").assertIsDisplayed()
        Shots.capture(rule, "v18_exercises_edit")
    }

    @Test
    fun longPressOnTheScreenBesideAContainerOpensTheEditorToo() {
        val app = makeApp()
        start(app, Route.Exercises)
        // el margen de la pantalla, a la altura de una categoría: no es de ningún contenedor
        val r = tag("exercises_slot_$firstCat").fetchSemanticsNode().boundsInRoot
        assertTrue("hay margen a la izquierda del contenedor", r.left > 10f)
        holdAt(Offset(r.left / 2f, (r.top + r.bottom) / 2f))
        assertTrue("mantener en la pantalla (no en un contenedor) abre el editor", app.exercisesLayout.active)
        assertEquals(Route.Exercises, app.nav.top)
    }

    @Test
    fun exercisesRemoveResizeAddAHubWidgetAndUndo() {
        val app = makeApp()
        start(app, Route.Exercises)
        val before = app.repo.pageSlots(LayoutPage.EXERCISES)
        ui { app.exercisesLayout.start() }
        Shots.settle(rule, 400)

        // quitar «Crear ejercicio»
        tag("exercises_remove_ex_create").performClick()
        Shots.settle(rule, 400)
        tag("exercises_slot_ex_create").assertDoesNotExist()

        // una categoría mediana se agranda a grande
        val small = tag("exercises_slot_$firstCat").fetchSemanticsNode().size.width
        tag("exercises_grow_$firstCat").performClick()
        Shots.settle(rule, 500)
        assertEquals("l", app.exercisesLayout.slots.first { it.id == firstCat }.size)
        assertTrue(tag("exercises_slot_$firstCat").fetchSemanticsNode().size.width > small)

        // agregar un widget del inicio (la lista baja hasta el botón)
        repeat(3) {
            tag("exercises_slot_ex_search").performTouchInput { swipeUp() }
            Shots.settle(rule, 400)
        }
        Shots.capture(rule, "v18_exercises_edit_bottom")
        tag("exercises_add").performClick()
        Shots.settle(rule, 800)
        tag("exercises_add_ex_create").assertExists()
        tag("exercises_add_train").assertExists()
        Shots.capture(rule, "v18_exercises_add_sheet")
        tag("exercises_add_train").performClick()
        Shots.settle(rule, 800)
        assertEquals(ids(app.exercisesLayout).toString(), "train", ids(app.exercisesLayout).last())
        tag("exercises_slot_train").assertExists()

        // nada se guardó hasta «Listo»
        assertEquals(before, app.repo.pageSlots(LayoutPage.EXERCISES))
        tag("exercises_done").performClick()
        Shots.settle(rule, 700)
        assertFalse(app.exercisesLayout.active)
        val saved = app.repo.pageSlots(LayoutPage.EXERCISES).map { it.id }
        assertTrue("train" in saved)
        assertFalse("ex_create" in saved)
        tag("exercises_slot_train").assertExists()
        // el inicio no se tocó
        assertEquals(HomeLayout.default().map { it.id }, app.repo.homeSlots.map { it.id })
        Shots.capture(rule, "v18_exercises_after_done")

        // un solo «Deshacer»
        assertEquals("Ejercicios personalizado", app.repo.undo())
        Shots.settle(rule, 500)
        assertEquals(before, app.repo.pageSlots(LayoutPage.EXERCISES))
    }

    @Test
    fun draggingReordersTheExercisesPage() {
        val app = makeApp()
        start(app, Route.Exercises)
        ui { app.exercisesLayout.start() }
        Shots.settle(rule, 400)
        val cats = ids(app.exercisesLayout).filter { it.startsWith("cat:") }
        val a = cats[0]; val b = cats[cats.size - 1]
        assertTrue(ids(app.exercisesLayout).indexOf(a) < ids(app.exercisesLayout).indexOf(b))
        // la lista es larga: el destino puede quedar fuera de la vista, se baja un poco antes de arrastrar con el cuerpo
        drag("exercises_grip_${cats[0]}", "exercises_slot_${cats[2]}")
        val order = ids(app.exercisesLayout)
        assertTrue("la primera categoría debe haber pasado a la tercera: $order", order.indexOf(cats[0]) > order.indexOf(cats[2]))
        assertEquals(HomeLayout.default(LayoutPage.EXERCISES, emptyList()).map { it.id }.sorted(), order.sorted())
        tag("exercises_done").performClick()
        Shots.settle(rule, 500)
        assertEquals(order, app.repo.pageSlots(LayoutPage.EXERCISES).map { it.id })
    }

    @Test
    fun resetRestoresTheDefaultExercisesLayout() {
        val app = makeApp()
        start(app, Route.Exercises)
        ui { app.exercisesLayout.start(); app.exercisesLayout.remove("ex_search"); app.exercisesLayout.add("goal") }
        Shots.settle(rule, 400)
        tag("exercises_slot_goal").assertExists()
        tag("exercises_slot_ex_search").assertDoesNotExist()
        ui { app.exercisesLayout.reset() }
        Shots.settle(rule, 500)
        assertEquals(HomeLayout.default(LayoutPage.EXERCISES, emptyList()).map { it.id }, ids(app.exercisesLayout))
        tag("exercises_slot_ex_search").assertExists()
    }

    // ───────── Rutinas

    @Test
    fun routinesEditorOpensFromAContainerAndKeepsRoutinesInPlace() {
        val app = makeApp()
        start(app, Route.Routines)
        val r = app.repo.routines.first()
        val t = "routines_slot_rt:${r.id}"
        tag(t).assertIsDisplayed()
        Shots.capture(rule, "v18_routines_normal")
        hold(t)
        assertTrue(app.routinesLayout.active)
        assertEquals("sigue en Rutinas, no abre la rutina", Route.Routines, app.nav.top)
        tag("routines_done").assertIsDisplayed()
        // una rutina no se quita (sería borrar la rutina): sin botón de quitar; sí se puede mover y cambiar de tamaño
        tag("routines_remove_rt:${r.id}").assertDoesNotExist()
        tag("routines_grip_rt:${r.id}").assertIsDisplayed()
        Shots.capture(rule, "v18_routines_edit")
        // se le agrega un widget del inicio, entre las rutinas
        ui { app.routinesLayout.add("progress") }
        Shots.settle(rule, 500)
        tag("routines_slot_progress").assertExists()
        tag("routines_done").performClick()
        Shots.settle(rule, 600)
        assertTrue("progress" in app.repo.pageSlots(LayoutPage.ROUTINES).map { it.id })
        assertEquals("todas las rutinas siguen ahí", app.repo.routines.map { "rt:${it.id}" }.sorted(),
            app.repo.pageSlots(LayoutPage.ROUTINES).map { it.id }.filter { it.startsWith("rt:") }.sorted())
        Shots.capture(rule, "v18_routines_after_done")
    }

    @Test
    fun longPressOnTheEmptyPartOfTheRoutinesScreenOpensTheEditor() {
        val app = makeApp()
        start(app, Route.Routines)
        assertFalse(app.routinesLayout.active)
        // debajo de la última tarjeta no hay nada: la pantalla (no un contenedor) recibe el toque largo
        val last = app.repo.routines.last().id
        val b = tag("routines_slot_rt:$last").fetchSemanticsNode().boundsInRoot
        val rootH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val y = ((b.bottom + rootH * 0.80f) / 2f).coerceAtMost(rootH * 0.78f)
        holdAt(Offset(b.left + 40f, y.coerceAtLeast(b.bottom + 24f)))
        assertTrue("el toque largo en la pantalla abre el editor", app.routinesLayout.active)
        assertEquals(Route.Routines, app.nav.top)
    }

    @Test
    fun theRoutineMenuIsReachableWithoutALongPress() {
        val app = makeApp()
        start(app, Route.Routines)
        val r = app.repo.routines.first()
        tag("routine_menu_${r.id}").performClick()
        Shots.settle(rule, 800)
        assertTrue(app.overlays.hasOverlay)
        onNodeText("Opciones de la rutina").assertExists()
        Shots.capture(rule, "v18_routines_menu")
        assertFalse("el menú no activa el editor", app.routinesLayout.active)
    }

    @Test
    fun aNewRoutineAppearsInTheCustomRoutinesLayoutAndDeletedOnesLeave() {
        val app = makeApp()
        start(app, Route.Routines)
        ui { app.routinesLayout.start(); app.routinesLayout.add("progress"); app.routinesLayout.finish() }
        Shots.settle(rule, 500)
        val before = app.repo.pageSlots(LayoutPage.ROUTINES).map { it.id }
        val n = app.repo.routines.size
        ui { app.repo.createRoutine("Nueva de prueba") }
        Shots.settle(rule, 700)
        val after = app.repo.pageSlots(LayoutPage.ROUTINES).map { it.id }
        assertEquals(n + 1, app.repo.routines.size)
        assertEquals("la rutina nueva aparece en la pantalla personalizada", before.size + 1, after.size)
        assertTrue("progress" in after)
        val newId = app.repo.routines.first { it.name == "Nueva de prueba" }.id
        tag("routines_slot_rt:$newId").assertExists()
        ui { app.repo.deleteRoutine(newId) }
        Shots.settle(rule, 600)
        assertEquals(before, app.repo.pageSlots(LayoutPage.ROUTINES).map { it.id })
    }

    // ───────── Inicio y ajustes

    @Test
    fun longPressOnTheEmptyPartOfTheHomeOpensItsEditor() {
        val app = makeApp()
        start(app)
        // el hueco entre dos widgets pequeños de la misma fila
        val small = listOf("progress", "milestones", "notes").filter { runCatching { tag("home_slot_$it").fetchSemanticsNode() }.isSuccess }
        val l = tag("home_slot_${small[0]}").fetchSemanticsNode().boundsInRoot
        val r = tag("home_slot_${small[1]}").fetchSemanticsNode().boundsInRoot
        holdAt(Offset((l.right + r.left) / 2f, (l.top + l.bottom) / 2f))
        assertTrue(app.home.active)
        assertEquals(Route.Home, app.nav.top)
    }

    @Test
    fun settingsOffersToEditEveryPage() {
        val app = makeApp()
        start(app)
        ui { app.nav.switchTab(Route.Settings); app.nav.push(Route.SettingsPage(Pages.HOME)) }
        Shots.settle(rule, 1000)
        onNodeText("Editar el inicio").assertExists()
        onNodeText("Editar ejercicios").assertExists()
        onNodeText("Editar rutinas").assertExists()
        Shots.capture(rule, "v18_settings_layouts")
        onNodeText("Editar ejercicios").performClick()
        Shots.settle(rule, 900)
        assertEquals(Route.Exercises, app.nav.top)
        assertTrue(app.exercisesLayout.active)
        tag("exercises_done").performClick()
        Shots.settle(rule, 400)
        assertFalse(app.exercisesLayout.active)
        assertNotEquals(Route.Home, app.nav.top)
    }

    @Test
    fun leavingThePageWhileEditingKeepsTheChanges() {
        val app = makeApp()
        start(app, Route.Exercises)
        ui { app.exercisesLayout.start(); app.exercisesLayout.remove("ex_search") }
        Shots.settle(rule, 400)
        ui { app.nav.switchTab(Route.Routines) }
        Shots.settle(rule, 800)
        assertFalse(app.exercisesLayout.active)
        assertFalse("ex_search" in app.repo.pageSlots(LayoutPage.EXERCISES).map { it.id })
    }

    private fun onNodeText(t: String) = rule.onNodeWithText(t)
}
