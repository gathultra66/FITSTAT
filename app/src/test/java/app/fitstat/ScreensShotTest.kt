package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import app.fitstat.ui.app.startWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/** Recorre las pantallas principales con datos de ejemplo y guarda una captura de cada una. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
class ScreensShotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    private fun makeApp(mode: String, demo: Boolean = true): AppState {
        val dir = Files.createTempDirectory("fitstat-shots").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        if (demo) {
            repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
            repo.setProfileName("Chris")
        }
        repo.setSetting("theme.mode", mode)
        return AppState(ApplicationProvider.getApplicationContext(), repo)
    }

    private fun workoutSetup(app: AppState) {
        app.startWorkout("demo_r1")
        val s = app.workout!!
        val ex = s.exercises.first()
        val r1 = app.repo.logSet(ex, 60.0, 8, "demo_r1"); s.logIds.add(r1.log.id)
        val r2 = app.repo.logSet(ex, 62.5, 8, "demo_r1"); s.logIds.add(r2.log.id)
        s.startRest(90, app.repo.now())
    }

    private fun tour(prefix: String, mode: String, steps: List<Pair<String, (AppState) -> Unit>>) {
        val app = makeApp(mode)
        rule.setContent { FitstatRoot(app) }
        Shots.shot(rule, "${prefix}_home", 1600)
        for ((name, go) in steps) {
            rule.runOnUiThread { go(app); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            Shots.shot(rule, "${prefix}_$name", 1200)
            println("SHOT $prefix/$name -> stack=" + app.nav.stack.map { it.key })
        }
    }

    @Test
    fun tourDark() = tour(
        "dark", "dark",
        listOf(
            "exercises" to { a -> a.nav.switchTab(Route.Exercises) },
            "category" to { a -> a.nav.push(Route.Category("pecho")) },
            "exercise" to { a -> a.nav.push(Route.Exercise("press_de_banca_con_barra")) },
            "muscles" to { a -> a.nav.push(Route.Muscles("press_de_banca_con_barra")) },
            "search" to { a -> a.nav.switchTab(Route.Exercises); a.nav.push(Route.Search("pres")) },
            "routines" to { a -> a.nav.switchTab(Route.Routines) },
            "routine" to { a -> a.nav.push(Route.RoutineDetail("demo_r1")) },
            "workout" to { a -> workoutSetup(a); },
            "workout_done" to { a -> a.workout!!.skipRest(); a.workout!!.finishedAt = now + 2_700_000L },
            "home_after" to { a -> a.closeWorkoutForTest(); a.nav.switchTab(Route.Home) },
            "progress" to { a -> a.nav.push(Route.Progress) },
            "milestones" to { a -> a.nav.switchTab(Route.Home); a.nav.push(Route.Milestones) },
            "notes" to { a -> a.nav.switchTab(Route.Home); a.nav.push(Route.Notes) },
        ),
    )

    @Test
    fun tourLight() = tour(
        "light", "light",
        listOf(
            "exercise" to { a -> a.nav.push(Route.Exercise("sentadilla_con_barra")) },
            "progress" to { a -> a.nav.switchTab(Route.Home); a.nav.push(Route.Progress) },
            "workout" to { a -> a.nav.switchTab(Route.Home); workoutSetup(a) },
            "routines" to { a -> a.nav.switchTab(Route.Routines) },
        ),
    )

    @Test
    fun onboarding() {
        val app = makeApp("dark", demo = false)
        rule.setContent { FitstatRoot(app) }
        Shots.shot(rule, "onboarding_0", 1400)
    }
}

private fun AppState.closeWorkoutForTest() {
    workout = null
}
