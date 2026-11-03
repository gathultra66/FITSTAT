package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppBotHost
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import app.fitstat.ui.app.botCancelWorkout
import app.fitstat.ui.app.botFinishWorkout
import app.fitstat.ui.app.cancelWorkout
import app.fitstat.ui.app.closeWorkout
import app.fitstat.ui.app.completeWorkout
import app.fitstat.ui.app.sessionSets
import app.fitstat.ui.app.startWorkout
import app.fitstat.ui.app.workoutActive
import app.fitstat.ui.screens.requestCancelWorkout
import app.fitstat.ui.screens.requestFinishWorkout
import app.fitstat.ui.screens.showEndWorkoutSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * v1.1: salir de un entrenamiento. La pantalla sigue en pantalla durante su animación de salida;
 * antes, al cancelar, esa pantalla volvía a crear la sesión y la barra "Entrenamiento en curso" reaparecía.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1100dp-xhdpi")
class WorkoutFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    private fun makeApp(): AppState {
        val dir = Files.createTempDirectory("fitstat-flow").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
        repo.setProfileName("Chris")
        repo.setSetting("theme.mode", "dark")
        return AppState(ApplicationProvider.getApplicationContext(), repo)
    }

    private fun ui(block: () -> Unit) {
        rule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
    }

    private fun begin(app: AppState, sets: Int) {
        rule.setContent { FitstatRoot(app) }
        Shots.settle(rule, 1200)
        ui { app.startWorkout("demo_r1") }
        Shots.settle(rule, 800)
        assertTrue("debe estar la pantalla de entrenamiento", app.nav.top is Route.Workout)
        ui {
            val s = app.workout!!
            repeat(sets) { i ->
                val r = app.repo.logSet(s.exercises.first(), 60.0 + i, 8, "demo_r1")
                s.logIds.add(r.log.id)
            }
        }
        Shots.settle(rule, 300)
    }

    @Test
    fun cancelDiscardsTheSessionAndItDoesNotComeBack() {
        val app = makeApp()
        val before = app.repo.logs.size
        begin(app, 2)
        assertEquals(before + 2, app.repo.logs.size)
        assertEquals(2, app.sessionSets(app.workout!!))

        ui { assertEquals(2, app.cancelWorkout(true)) }
        Shots.settle(rule, 1600) // la pantalla vieja termina su animación de salida

        assertNull("la sesión no debe volver a crearse", app.workout)
        assertFalse(app.workoutActive)
        assertEquals(Route.Home, app.nav.top)
        assertEquals("las series de la sesión se borran", before, app.repo.logs.size)
    }

    @Test
    fun cancelCanKeepTheSets() {
        val app = makeApp()
        val before = app.repo.logs.size
        begin(app, 2)
        ui { assertEquals(0, app.cancelWorkout(false)) }
        Shots.settle(rule, 1600)
        assertNull(app.workout)
        assertEquals(Route.Home, app.nav.top)
        assertEquals(before + 2, app.repo.logs.size)
    }

    @Test
    fun finishThenDoneDoesNotLeaveAnotherSession() {
        val app = makeApp()
        begin(app, 1)
        ui { app.completeWorkout(app.workout!!) }
        Shots.settle(rule, 800)
        assertTrue(app.workout!!.done)
        assertFalse("ya terminado no cuenta como en curso", app.workoutActive)
        ui { app.closeWorkout() }
        Shots.settle(rule, 1600)
        assertNull(app.workout)
        assertEquals(Route.Home, app.nav.top)
    }

    @Test
    fun cancelRemovesTheWorkoutScreenEvenWhenAnotherScreenIsOnTop() {
        val app = makeApp()
        begin(app, 1)
        ui { app.nav.push(Route.Exercise(app.workout!!.exercises.first())) }
        Shots.settle(rule, 600)
        ui { app.cancelWorkout(true) }
        Shots.settle(rule, 1600)
        assertNull(app.workout)
        assertTrue(app.nav.stack.none { it is Route.Workout })
        assertTrue(app.nav.top is Route.Exercise)
    }

    @Test
    fun botCanSeeFinishAndCancelTheWorkout() {
        val app = makeApp()
        val host = AppBotHost(app)
        assertNull(host.workoutStatus())
        assertNull(host.finishWorkout())
        assertNull(host.cancelWorkout(true))

        begin(app, 3)
        val st = host.workoutStatus()
        assertNotNull(st)
        assertEquals(3, st!!.sets)
        assertFalse(st.finished)
        assertTrue(host.workoutActive())

        // Finalizar conserva las series y deja el resumen.
        var msg = ""
        ui { msg = host.finishWorkout().orEmpty() }
        Shots.settle(rule, 800)
        assertTrue(msg, msg.contains("3 series"))
        assertTrue(app.workout!!.done)
        assertFalse(host.workoutActive())
        val kept = app.repo.logs.size

        // Un entrenamiento nuevo y cancelado por FITBOT borra solo sus series.
        ui { app.closeWorkout() }
        Shots.settle(rule, 1200)
        ui { app.startWorkout("demo_r1") }
        Shots.settle(rule, 800)
        ui {
            val s = app.workout!!
            val r = app.repo.logSet(s.exercises.first(), 70.0, 5, "demo_r1")
            s.logIds.add(r.log.id)
        }
        assertEquals(kept + 1, app.repo.logs.size)
        ui { msg = host.cancelWorkout(true).orEmpty() }
        Shots.settle(rule, 1600)
        assertTrue(msg, msg.contains("1 serie"))
        assertNull(app.workout)
        assertEquals(kept, app.repo.logs.size)
        assertNull("sin entrenamiento no hay nada que cancelar", host.cancelWorkout(true))
    }

    @Test
    fun finishWithoutSetsJustClosesTheEmptySession() {
        val app = makeApp()
        val host = AppBotHost(app)
        begin(app, 0)
        var msg = ""
        ui { msg = host.finishWorkout().orEmpty() }
        Shots.settle(rule, 1600)
        assertTrue(msg, msg.contains("vacío"))
        assertNull(app.workout)
        assertEquals(Route.Home, app.nav.top)
    }

    @Test
    fun endSheetAndConfirmationsAreShown() {
        val app = makeApp()
        begin(app, 2)
        ui { app.showEndWorkoutSheet() }
        Shots.shot(rule, "v11_end_sheet", 700)
        assertTrue(app.overlays.hasOverlay)
        ui { app.overlays.closeTop() }
        Shots.settle(rule, 500)

        ui { app.requestCancelWorkout() }
        Shots.shot(rule, "v11_cancel_confirm", 600)
        assertTrue(app.overlays.hasOverlay)
        ui { app.overlays.closeTop() }
        Shots.settle(rule, 500)
        assertNotNull("cerrar el aviso no cancela nada", app.workout)

        ui { app.requestFinishWorkout() } // faltan ejercicios → pide confirmar
        Shots.settle(rule, 600)
        assertTrue(app.overlays.hasOverlay)
        ui { app.overlays.closeTop() }
        Shots.settle(rule, 400)
        assertFalse(app.workout!!.done)
    }

    @Test
    fun miniBarHasItsOwnCloseButton() {
        val app = makeApp()
        begin(app, 2)
        ui { app.nav.switchTab(Route.Home) }
        Shots.shot(rule, "v11_minibar", 900)
        assertTrue(app.workoutActive)
        rule.onNodeWithContentDescription("Cancelar o finalizar el entrenamiento").assertExists()
        rule.onNodeWithContentDescription("Cancelar o finalizar el entrenamiento").performClick()
        Shots.settle(rule, 600)
        assertTrue("la ✕ de la barra abre las opciones", app.overlays.hasOverlay)
    }

    @Test
    fun backWithoutSetsAsksWhatToDo() {
        val app = makeApp()
        begin(app, 0)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        Shots.shot(rule, "v11_leave_sheet", 700)
        assertTrue("debe abrir la hoja de salida", app.overlays.hasOverlay)
        assertNotNull("seguir en la pantalla no cancela nada", app.workout)
        assertTrue(app.nav.top is Route.Workout)
    }

    @Test
    fun backWithSetsJustMinimizes() {
        val app = makeApp()
        begin(app, 1)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        Shots.settle(rule, 900)
        assertFalse(app.overlays.hasOverlay)
        assertNotNull(app.workout)
        assertEquals(Route.Home, app.nav.top)
        assertTrue("sigue en curso en la barra", app.workoutActive)
    }
}
