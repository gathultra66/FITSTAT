package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.HomeLayout
import app.fitstat.data.HomeSlot
import app.fitstat.data.Pages
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.6: el inicio se personaliza manteniendo pulsado un widget (mover, achicar, agrandar, quitar, agregar),
 * y el selector de modelo del chat de FITBOT pasa al encabezado, junto al recuadro de las opciones.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
class HomeEditTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    private fun makeApp(mode: String = "dark", demo: Boolean = true): AppState {
        val dir = Files.createTempDirectory("fitstat-homeedit").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        if (demo) {
            repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
            repo.setProfileName("Chris")
        }
        repo.setSetting("theme.mode", mode)
        return AppState(ApplicationProvider.getApplicationContext(), repo)
    }

    private fun ui(block: () -> Unit) {
        rule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
    }

    private fun start(app: AppState) {
        rule.setContent { FitstatRoot(app) }
        Shots.settle(rule, 1600)
    }

    private fun ids(app: AppState) = app.home.slots.map { it.id }

    private fun tag(t: String) = rule.onNodeWithTag(t)

    private fun center(t: String): Offset {
        val b = tag(t).fetchSemanticsNode().boundsInRoot
        return Offset((b.left + b.right) / 2f, (b.top + b.bottom) / 2f)
    }

    /** Dedo apretado [ms] milisegundos sobre el widget (el reloj de pruebas no avanza solo). */
    private fun hold(t: String, ms: Long = 900) {
        tag(t).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(ms)
        tag(t).performTouchInput { up() }
        Shots.settle(rule, 400)
    }

    /** Arrastra desde el centro de [from] hasta el centro de [to], en varios pasos (con [holdMs] de toque largo antes). */
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

    // ───────── Entrar a editar con un toque largo

    @Test
    fun longPressOnAWidgetOpensEditMode() {
        val app = makeApp()
        start(app)
        assertFalse(app.home.active)
        tag("home_slot_train").assertIsDisplayed()
        tag("home_done").assertDoesNotExist()
        Shots.capture(rule, "v16_home_normal")

        hold("home_slot_train")
        assertTrue("el toque largo debe abrir la edición", app.home.active)
        assertEquals("no debe abrir ninguna pantalla al soltar", Route.Home, app.nav.top)
        assertEquals(1, app.nav.stack.size)
        tag("home_done").assertIsDisplayed()
        tag("home_remove_train").assertIsDisplayed()
        tag("home_grip_train").assertIsDisplayed()
        Shots.capture(rule, "v16_home_edit")
    }

    @Test
    fun aQuickTapStillOpensTheWidget() {
        val app = makeApp()
        start(app)
        tag("home_slot_milestones").performClick()
        Shots.settle(rule, 600)
        assertFalse(app.home.active)
        assertEquals(Route.Milestones, app.nav.top)
    }

    @Test
    fun tapsDoNothingWhileEditing() {
        val app = makeApp()
        start(app)
        ui { app.home.start() }
        Shots.settle(rule, 400)
        tag("home_slot_milestones").performClick()
        Shots.settle(rule, 600)
        assertEquals(Route.Home, app.nav.top)
        assertTrue(app.home.active)
    }

    // ───────── Quitar, cambiar de tamaño, terminar y deshacer

    @Test
    fun removeResizeDoneAndUndo() {
        val app = makeApp()
        start(app)
        val before = app.repo.homeSlots
        ui { app.home.start() }
        Shots.settle(rule, 400)

        tag("home_remove_notes").performClick()
        Shots.settle(rule, 500)
        tag("home_slot_notes").assertDoesNotExist()
        assertFalse("notes" in ids(app))

        // «Progreso» es pequeño: se agranda a mediano y a grande, y ya no crece más
        tag("home_grow_progress").performClick()
        Shots.settle(rule, 300)
        assertEquals("m", app.home.slots.first { it.id == "progress" }.size)
        tag("home_grow_progress").performClick()
        Shots.settle(rule, 300)
        assertEquals("l", app.home.slots.first { it.id == "progress" }.size)
        tag("home_grow_progress").performClick()
        Shots.settle(rule, 300)
        assertEquals("l", app.home.slots.first { it.id == "progress" }.size)
        tag("home_shrink_progress").performClick()
        Shots.settle(rule, 300)
        assertEquals("m", app.home.slots.first { it.id == "progress" }.size)
        Shots.capture(rule, "v16_home_edit_resized")

        // Nada se guardó todavía
        assertEquals(before, app.repo.homeSlots)
        tag("home_done").performClick()
        Shots.settle(rule, 600)
        assertFalse(app.home.active)
        assertFalse("notes" in app.repo.homeSlots.map { it.id })
        assertEquals("m", app.repo.homeSlots.first { it.id == "progress" }.size)
        tag("home_slot_notes").assertDoesNotExist()
        tag("home_done").assertDoesNotExist()
        Shots.capture(rule, "v16_home_after_done")

        // Un solo «Deshacer» devuelve todo
        assertEquals("Inicio personalizado", app.repo.undo())
        Shots.settle(rule, 500)
        assertEquals(before, app.repo.homeSlots)
        tag("home_slot_notes").assertExists()
    }

    @Test
    fun resizeChangesTheLayout() {
        val app = makeApp()
        start(app)
        val small = tag("home_slot_progress").fetchSemanticsNode().size.width
        ui { app.home.start() }
        Shots.settle(rule, 300)
        tag("home_grow_progress").performClick()
        Shots.settle(rule, 500)
        val medium = tag("home_slot_progress").fetchSemanticsNode().size.width
        tag("home_grow_progress").performClick()
        Shots.settle(rule, 500)
        val large = tag("home_slot_progress").fetchSemanticsNode().size.width
        assertTrue("pequeño < mediano < grande: $small $medium $large", small < medium && medium < large)
        // pequeño = 2 de 6 columnas, grande = toda la fila
        val full = tag("home_slot_goal").fetchSemanticsNode().size.width
        assertEquals(full, large)
        assertTrue(small * 3 < full + 60)
    }

    // ───────── Mover

    @Test
    fun draggingTheHandleReordersWidgets() {
        val app = makeApp()
        start(app)
        ui { app.home.start() }
        Shots.settle(rule, 400)
        val beforeIdx = ids(app).indexOf("progress")
        val streakIdx = ids(app).indexOf("streak")
        assertTrue(beforeIdx < streakIdx)
        drag("home_grip_progress", "home_slot_streak")
        val order = ids(app)
        assertTrue("progress debe haber pasado a streak: $order", order.indexOf("progress") > order.indexOf("streak"))
        assertEquals("los demás siguen ahí", HomeLayout.default().map { it.id }.sorted(), order.sorted())
        Shots.capture(rule, "v16_home_edit_moved")
        tag("home_done").performClick()
        Shots.settle(rule, 500)
        assertEquals(order, app.repo.homeSlots.map { it.id })
    }

    @Test
    fun holdingAWidgetAndDraggingReordersToo() {
        val app = makeApp()
        start(app)
        ui { app.home.start() }
        Shots.settle(rule, 400)
        // «Entrenar» arriba, «Racha» más abajo: se arrastra con toque largo en el cuerpo del widget
        drag("home_slot_streak", "home_slot_today", holdMs = 900)
        val order = ids(app)
        assertTrue("streak debe haber subido sobre today: $order", order.indexOf("streak") < order.indexOf("today"))
    }

    @Test
    fun swipingInEditModeStillScrollsTheList() {
        val app = makeApp()
        start(app)
        ui { app.home.start() }
        Shots.settle(rule, 400)
        val before = ids(app)
        // un deslizamiento rápido (sin mantener) no debe mover widgets
        tag("home_slot_today").performTouchInput { swipeUp() }
        Shots.settle(rule, 400)
        assertEquals(before, ids(app))
    }

    // ───────── Agregar

    @Test
    fun addSheetListsOnlyMissingWidgets() {
        val app = makeApp()
        start(app)
        ui { app.home.start(); app.home.remove("heatmap"); app.home.remove("muscles") }
        Shots.settle(rule, 500)
        // con 16 widgets el botón «Agregar» queda abajo del todo, detrás de la barra: se baja la lista como haría el usuario
        tag("home_slot_today").performTouchInput { swipeUp() }
        Shots.settle(rule, 500)
        tag("home_add").performClick()
        Shots.settle(rule, 800)
        tag("home_add_heatmap").assertExists()
        tag("home_add_muscles").assertExists()
        tag("home_add_goal").assertDoesNotExist()
        Shots.capture(rule, "v16_home_add_sheet")
        tag("home_add_heatmap").performClick()
        Shots.settle(rule, 800)
        assertEquals("heatmap", app.home.slots.last().id)
        tag("home_slot_heatmap").assertExists()
        assertFalse(app.overlays.hasOverlay)
    }

    @Test
    fun emptyHomeOffersToAddWidgets() {
        val app = makeApp()
        start(app)
        ui { app.repo.setHomeLayout(emptyList()) }
        Shots.settle(rule, 600)
        onNodeText("Tu inicio está vacío").assertExists()
        Shots.capture(rule, "v16_home_empty")
        tag("home_empty_add").performClick()
        Shots.settle(rule, 800)
        assertTrue(app.home.active)
        tag("home_add_train").assertExists()
        tag("home_add_train").performClick()
        Shots.settle(rule, 600)
        assertEquals(listOf("train"), ids(app))
        tag("home_done").performClick()
        Shots.settle(rule, 500)
        assertEquals(listOf("train"), app.repo.homeSlots.map { it.id })
    }

    // ───────── Salir

    @Test
    fun backButtonFinishesEditingAndKeepsChanges() {
        val app = makeApp()
        start(app)
        ui { app.home.start(); app.home.remove("heatmap") }
        Shots.settle(rule, 400)
        ui { rule.activity.onBackPressedDispatcher.onBackPressed() }
        Shots.settle(rule, 600)
        assertFalse(app.home.active)
        assertFalse("heatmap" in app.repo.homeSlots.map { it.id })
        assertEquals("sigue en el inicio", Route.Home, app.nav.top)
    }

    @Test
    fun leavingTheScreenWhileEditingKeepsChanges() {
        val app = makeApp()
        start(app)
        ui { app.home.start(); app.home.remove("muscles") }
        Shots.settle(rule, 400)
        ui { app.nav.switchTab(Route.Exercises) }
        Shots.settle(rule, 1200)
        assertFalse(app.home.active)
        assertFalse("muscles" in app.repo.homeSlots.map { it.id })
    }

    @Test
    fun resetGoesBackToTheDefaultLayout() {
        val app = makeApp()
        start(app)
        ui { app.repo.setHomeLayout(listOf(HomeSlot("notes", "l"), HomeSlot("goal", "m"))) }
        Shots.settle(rule, 600)
        ui { app.home.start() }
        Shots.settle(rule, 300)
        tag("home_reset").performClick()
        Shots.settle(rule, 600)
        assertEquals(HomeLayout.default().map { it.id }, ids(app))
        tag("home_done").performClick()
        Shots.settle(rule, 400)
        assertEquals(HomeLayout.default().map { it.id }, app.repo.homeSlots.map { it.id })
    }

    // ───────── Datos de antes de v1.6

    @Test
    fun oldHiddenCardsStayHiddenUntilCustomized() {
        val dir = Files.createTempDirectory("fitstat-homeold").toFile()
        // datos de la v1.5.x: tenía apagadas «Actividad» y «Músculos de la semana»
        File(dir, "data.json").writeText("""{"schema":1,"profile":{"name":"Chris","onboarded":true},"settings":{"home.show_heatmap":"false","home.show_muscles":"false"}}""")
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        val app = AppState(ApplicationProvider.getApplicationContext(), repo)
        start(app)
        assertNull(repo.data.homeLayout)
        tag("home_slot_goal").assertExists()
        tag("home_slot_heatmap").assertDoesNotExist()
        tag("home_slot_muscles").assertDoesNotExist()
        // al editar, siguen disponibles para agregarlos
        ui { app.home.start() }
        Shots.settle(rule, 400)
        tag("home_add").performClick()
        Shots.settle(rule, 800)
        tag("home_add_heatmap").assertExists()
        tag("home_add_muscles").assertExists()
    }

    // ───────── Ajustes

    @Test
    fun settingsHomePageHasTheEditor() {
        val app = makeApp()
        start(app)
        ui { app.nav.switchTab(Route.Settings); app.nav.push(Route.SettingsPage(Pages.HOME)) }
        Shots.settle(rule, 1000)
        onNodeText("Editar el inicio").assertExists()
        onNodeText("Restablecer el inicio").assertExists()
        Shots.capture(rule, "v16_settings_home")
        onNodeText("Editar el inicio").performClick()
        Shots.settle(rule, 1000)
        assertTrue(app.home.active)
        assertEquals(Route.Home, app.nav.top)
        tag("home_done").assertIsDisplayed()
    }

    // ───────── FITBOT: selector de modelo en el encabezado

    @Test
    fun chatHeaderHasTheModelButtonNextToTheMenu() {
        val app = makeApp()
        start(app)
        ui { app.nav.switchTab(Route.Bot) }
        Shots.settle(rule, 1400)
        tag("bot_model").assertIsDisplayed()
        tag("bot_menu").assertIsDisplayed()
        val m = tag("bot_model").fetchSemanticsNode().boundsInRoot
        val k = tag("bot_menu").fetchSemanticsNode().boundsInRoot
        assertTrue("el selector va a la izquierda del menú", m.right <= k.left + 1f)
        assertEquals("misma altura (los dos son recuadros iguales)", m.height, k.height, 1f)
        assertEquals("misma fila", m.top, k.top, 1f)
        Shots.capture(rule, "v16_bot_header")
        tag("bot_model").performClick()
        Shots.settle(rule, 800)
        assertTrue("debe abrir el selector de modelos", app.overlays.hasOverlay)
        Shots.capture(rule, "v16_bot_model_picker")
    }

    private fun onNodeText(text: String) = rule.onNodeWithText(text, substring = false)
}
