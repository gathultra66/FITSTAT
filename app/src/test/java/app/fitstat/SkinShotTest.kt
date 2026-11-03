package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.Pages
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import app.fitstat.ui.app.startWorkout
import app.fitstat.ui.design.AppBackground
import app.fitstat.ui.design.BotMood
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.SkinHost
import app.fitstat.ui.theme.FitstatTheme
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

/** Recorre la app con cada skin y guarda capturas, además de una galería de las mascotas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SkinShotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    private fun makeApp(skin: String, tweak: (Repo) -> Unit = {}): AppState {
        val dir = Files.createTempDirectory("fitstat-skin").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
        repo.setProfileName("Chris")
        repo.setSetting("skin.id", skin)
        tweak(repo)
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

    private fun tour(skin: String, tweak: (Repo) -> Unit = {}, tag: String = skin) {
        val app = makeApp(skin, tweak)
        rule.setContent { FitstatRoot(app) }
        Shots.shot(rule, "skin_${tag}_home", 1600)
        val steps: List<Pair<String, (AppState) -> Unit>> = listOf(
            "exercises" to { a -> a.nav.switchTab(Route.Exercises) },
            "routines" to { a -> a.nav.switchTab(Route.Routines) },
            "bot" to { a -> a.nav.switchTab(Route.Bot) },
            "settings" to { a -> a.nav.switchTab(Route.Settings) },
            "skins" to { a -> a.nav.push(Route.SettingsPage(Pages.SKIN)) },
            "theme_note" to { a -> a.nav.back(); a.nav.push(Route.SettingsPage(Pages.THEME)) },
            "workout" to { a -> a.nav.switchTab(Route.Home); workoutSetup(a) },
            "workout_done" to { a -> a.workout!!.skipRest(); a.workout!!.finishedAt = now + 2_700_000L },
            "progress" to { a -> a.workout = null; a.nav.switchTab(Route.Home); a.nav.push(Route.Progress) },
        )
        for ((name, go) in steps) {
            rule.runOnUiThread { go(app); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            Shots.shot(rule, "skin_${tag}_$name", 1500)
            println("SHOT skin_$tag/$name -> stack=" + app.nav.stack.map { it.key })
        }
    }

    @Test
    fun tourCarmesi() = tour("carmesi")

    @Test
    fun tourAcero() = tour("acero")

    @Test
    fun tourAceroCustom() = tour(
        "acero",
        tweak = { r ->
            r.setSetting("skin.acero.accent", "acido")
            r.setSetting("skin.acero.cut", "sharp")
            r.setSetting("skin.acero.bg", "grid")
            r.setSetting("skin.acero.led", "led")
        },
        tag = "acero_custom",
    )

    @Test
    fun tourCarmesiPlain() = tour(
        "carmesi",
        tweak = { r ->
            r.setSetting("skin.carmesi.accent", "custom")
            r.setSetting("skin.carmesi.custom", "#1FA2FF")
            r.setSetting("skin.carmesi.cut", "cut")
            r.setSetting("skin.carmesi.bg", "plain")
            r.setSetting("skin.carmesi.anim", "off")
            r.setSetting("skin.carmesi.words", "false")
        },
        tag = "carmesi_custom",
    )

    @Test
    fun classicSkinIsUntouched() {
        val app = makeApp("fitstat")
        rule.setContent { FitstatRoot(app) }
        Shots.shot(rule, "skin_fitstat_home", 1600)
        rule.runOnUiThread { app.nav.push(Route.SettingsPage(Pages.SKIN)); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        Shots.shot(rule, "skin_fitstat_skins", 1500)
    }

    @Test
    fun headsCarmesi() = heads("carmesi")

    @Test
    fun headsAcero() = heads("acero")

    private fun heads(skin: String) {
        run {
            val dir = Files.createTempDirectory("fitstat-heads").toFile()
            val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
            repo.setSetting("skin.id", skin)
            rule.setContent {
                FitstatTheme(repo.prefs) {
                    SkinHost {
                        AppBackground {
                            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FitBot(size = 150.dp, mood = BotMood.Idle)
                                    FitBot(size = 150.dp, mood = BotMood.Happy)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FitBot(size = 96.dp, mood = BotMood.Thinking)
                                    FitBot(size = 96.dp, mood = BotMood.Talking)
                                    FitBot(size = 96.dp, mood = BotMood.Typing)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FitBot(size = 96.dp, mood = BotMood.Sleepy)
                                    FitBot(size = 96.dp, mood = BotMood.Error)
                                    FitBot(size = 96.dp, mood = BotMood.Love)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FitBot(size = 40.dp, mood = BotMood.Idle)
                                    FitBot(size = 56.dp, mood = BotMood.Happy)
                                    FitBot(size = 72.dp, mood = BotMood.Thinking)
                                }
                                Box(Modifier.padding(4.dp))
                            }
                        }
                    }
                }
            }
            Shots.shot(rule, "skin_heads_$skin", 1200)
        }
    }
}
