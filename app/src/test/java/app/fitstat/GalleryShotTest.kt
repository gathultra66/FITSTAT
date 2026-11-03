package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Prefs
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.FitstatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xhdpi")
class GalleryShotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Gallery() {
        val c = Fs.colors
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LedText("FITSTAT > RAP-200A", dot = 3.dp, gap = 1.dp, lit = c.textDim)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Txt("Hoy", Modifier.weight(1f), Fs.type.h2)
                    CountBadge(2)
                }
                Txt("Pecho y tríceps", style = Fs.type.h3)
                Txt("12:30 - 12:50", style = Fs.type.small, color = c.textDim)
            }
            Card(accent = true) {
                Txt("Meta semanal", style = Fs.type.label)
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt("03", style = Fs.type.display)
                    Txt(" / 4 días", Modifier.padding(bottom = 6.dp), Fs.type.small)
                }
                ChunkyProgress(0.75f, Modifier.padding(top = 10.dp), track = c.onAccent.copy(alpha = 0.18f), fill = androidx.compose.ui.graphics.SolidColor(c.onAccent))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).background(Color(0xFFD9D9DD), Fs.shapes.card).padding(16.dp), contentAlignment = Alignment.Center) {
                    Orb(size = 150.dp, ic = Ic.Dumbbell)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitBot(size = 130.dp, mood = BotMood.Idle)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FitBot(size = 52.dp, mood = BotMood.Thinking)
                        FitBot(size = 52.dp, mood = BotMood.Happy)
                        FitBot(size = 52.dp, mood = BotMood.Typing)
                        FitBot(size = 52.dp, mood = BotMood.Sleepy)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FsButton("Iniciar", {}, Modifier.weight(1f), icon = Ic.Play)
                FsButton("Editar", {}, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.Edit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Pecho", selected = true)
                Pill("Espalda")
                Pill("Pierna", icon = Ic.Muscle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Toggle(true, {})
                Toggle(false, {})
                Stepper("3", {}, {})
            }
            Segmented(listOf("Barras", "Línea", "Área", "LED"), 1, {})
            FsSlider(0.6f, {}, 0f, 1f)
            WeekdayPicker(setOf(1, 3, 5), {})
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Ic.Home, Ic.Dumbbell, Ic.Routines, Ic.Bot, Ic.Settings, Ic.Trophy, Ic.Flame, Ic.Timer).forEach { IconBadge(it, size = 38.dp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MuscleFigure(mapOf(app.fitstat.data.Muscle.CHEST to 2, app.fitstat.data.Muscle.TRICEPS to 1, app.fitstat.data.Muscle.SHOULDERS to 1), Modifier.weight(1f).height(260.dp))
                MuscleFigure(mapOf(app.fitstat.data.Muscle.LATS to 2, app.fitstat.data.Muscle.BICEPS to 1), Modifier.weight(1f).height(260.dp), back = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ring(0.7f, size = 80.dp) { Txt("70%", style = Fs.type.smallStrong) }
                SegmentedProgress(3, 5, Modifier.weight(1f))
            }
        }
    }

    private fun shot(name: String, settings: Map<String, String> = emptyMap()) {
        rule.setContent {
            FitstatTheme(Prefs(settings)) {
                AppBackground { Gallery() }
            }
        }
        Shots.shot(rule, name, 900)
    }

    @Test fun galleryDark() = shot("gallery_dark")
    @Test fun galleryLight() = shot("gallery_light", mapOf("theme.mode" to "light", "theme.palette" to "menta"))
}
