package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/** v1.8.1: la app en horizontal y en tablet (columna centrada, sin estirar). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    private fun makeApp(): AppState {
        val dir = Files.createTempDirectory("fitstat-tablet").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
        repo.setProfileName("Chris")
        repo.setSetting("theme.mode", "dark")
        return AppState(ApplicationProvider.getApplicationContext(), repo)
    }

    private fun tour(prefix: String) {
        val app = makeApp()
        rule.setContent { FitstatRoot(app) }
        Shots.settle(rule, 1200)
        Shots.capture(rule, "v181_${prefix}_home")
        val w = rule.onNodeWithTag("home_slot_goal").fetchSemanticsNode().size.width
        val root = rule.onRoot().fetchSemanticsNode().size.width
        assertTrue("el contenido no se estira ($w de $root)", w <= root)
        for ((name, r) in listOf("exercises" to Route.Exercises, "routines" to Route.Routines, "bot" to Route.Bot, "settings" to Route.Settings)) {
            rule.runOnUiThread { app.nav.switchTab(r); Snapshot.sendApplyNotifications() }
            Shots.settle(rule, 800)
            Shots.capture(rule, "v181_${prefix}_$name")
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLandscape() = tour("tab_land")

    @Test
    @Config(sdk = [35], qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun tabletPortrait() = tour("tab_port")

    @Test
    @Config(sdk = [35], qualifiers = "w820dp-h380dp-land-xhdpi")
    fun phoneLandscape() = tour("phone_land")
}
