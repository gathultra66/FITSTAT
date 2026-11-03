package app.fitstat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.bot.ActionRecord
import app.fitstat.bot.ChatMessage
import app.fitstat.data.Demo
import app.fitstat.data.Pages
import app.fitstat.data.Reminder
import app.fitstat.data.Repo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route
import app.fitstat.ui.screens.openModelPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/** Capturas de FITBOT y de todas las pantallas de Ajustes (incluye widgets, recordatorios, ícono y datos). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
class SystemShotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()

    private val sampleModels = """{"data":[
      {"id":"xiaomi/mimo-v2.6-flash","name":"Xiaomi: MiMo V2.6 Flash","description":"Rápido y económico, con herramientas y razonamiento.","context_length":262144,
       "architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0.00000009","completion":"0.00000029"},
       "supported_parameters":["tools","reasoning","include_reasoning","temperature"],"created":1780000000},
      {"id":"google/gemini-2.5-flash","name":"Google: Gemini 2.5 Flash","description":"Multimodal: imágenes, audio y archivos.","context_length":1048576,
       "architecture":{"input_modalities":["text","image","file","audio"],"output_modalities":["text"]},"pricing":{"prompt":"0.0000003","completion":"0.0000025"},
       "supported_parameters":["tools","reasoning","include_reasoning","temperature"],"created":1750000000},
      {"id":"openai/gpt-5-mini","name":"OpenAI: GPT-5 Mini","description":"Equilibrado.","context_length":400000,
       "architecture":{"input_modalities":["text","image","file"],"output_modalities":["text"]},"pricing":{"prompt":"0.00000025","completion":"0.000002"},
       "supported_parameters":["tools","reasoning","include_reasoning"],"created":1754000000},
      {"id":"meta-llama/llama-3.3-70b-instruct:free","name":"Meta: Llama 3.3 70B Instruct (free)","description":"Gratis, solo texto.","context_length":131072,
       "architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"},
       "supported_parameters":["tools","temperature"],"created":1733000000},
      {"id":"deepseek/deepseek-chat-v3.1","name":"DeepSeek: DeepSeek V3.1","description":"Muy económico.","context_length":163840,
       "architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0.00000015","completion":"0.00000075"},
       "supported_parameters":["tools","reasoning","include_reasoning"],"created":1755000000},
      {"id":"mistralai/mistral-small-3.2-24b-instruct","name":"Mistral: Mistral Small 3.2 24B","description":"Pequeño y veloz.","context_length":131072,
       "architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},"pricing":{"prompt":"0.00000006","completion":"0.00000018"},
       "supported_parameters":["tools","temperature"],"created":1750500000}
    ]}"""

    private fun makeApp(mode: String, withKey: Boolean = true, chat: Boolean = false): AppState {
        val dir = Files.createTempDirectory("fitstat-shots").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
        repo.setProfileName("Chris")
        repo.setSetting("theme.mode", mode)
        repo.saveReminder(Reminder("rem1", "Hora de entrenar", "Hoy toca Pierna", 18, 0, setOf(1, 3, 5), true, "demo_r1"))
        repo.saveReminder(Reminder("rem2", "Pesarte y medirte", "", 8, 30, setOf(7), true, null))
        repo.saveReminder(Reminder("rem3", "Estirar 10 minutos", "", 22, 0, setOf(1, 2, 3, 4, 5, 6, 7), false, null))
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = AppState(ctx, repo)
        if (withKey) app.keys.save("sk-or-v1-0123456789abcdef0123456789abcdef")
        val botDir = File(ctx.filesDir, "bot").apply { mkdirs() }
        File(botDir, "models.json").writeText(sampleModels)
        runBlocking { app.models.loadCached() }
        if (chat) seedChat(app)
        return app
    }

    private fun seedChat(app: AppState) {
        val m = "xiaomi/mimo-v2.6-flash"
        app.chat.add(ChatMessage("c1", "user", "Crea una rutina de pierna para los martes con 5 ejercicios y avísame a las 6 de la tarde", ts = now - 900_000))
        app.chat.add(
            ChatMessage(
                "c2", "assistant",
                "¡Listo, Chris! Armé **Pierna (martes)** con 5 ejercicios y te pondré un recordatorio a las **18:00** cada martes.\n\n" +
                    "- Sentadilla con barra\n- Prensa de pierna\n- Peso muerto rumano\n- Curl femoral\n- Elevación de talones\n\n¿Quieres que ajuste las series o los pesos?",
                actions = listOf(
                    ActionRecord("create_routine", "Creó la rutina «Pierna (martes)» con 5 ejercicios", write = true),
                    ActionRecord("create_reminder", "Recordatorio: martes 18:00", write = true),
                ),
                ts = now - 890_000, model = m, checkpoint = 0, tokens = 1840,
            ),
        )
        app.chat.add(ChatMessage("c3", "user", "¿Cuánto levanté en press de banca esta semana?", ts = now - 300_000))
        app.chat.add(
            ChatMessage(
                "c4", "assistant",
                "Esta semana hiciste **4 series** de press de banca. Tu mejor serie fue **80 kg × 6** (récord personal 🎉). El volumen total fue de **1 840 kg**.",
                reasoning = "Reviso las series de la semana en curso y calculo el volumen.",
                actions = listOf(ActionRecord("get_stats", "Consultó estadísticas de Press de banca")),
                ts = now - 290_000, model = m, tokens = 920,
            ),
        )
    }

    private fun tour(prefix: String, mode: String, steps: List<Pair<String, (AppState) -> Unit>>, chat: Boolean = false) {
        val app = makeApp(mode, chat = chat)
        rule.setContent { FitstatRoot(app) }
        Shots.shot(rule, "${prefix}_home", 1600)
        for ((name, go) in steps) {
            rule.runOnUiThread { go(app); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            Shots.shot(rule, "${prefix}_$name", 1200)
            println("SHOT $prefix/$name -> stack=" + app.nav.stack.map { it.key })
        }
    }

    @Test
    fun settingsDark() = tour(
        "sys_dark", "dark",
        listOf(
            "settings" to { a -> a.nav.switchTab(Route.Settings) },
            "tema" to { a -> a.nav.push(Route.SettingsPage(Pages.THEME)) },
            "formas" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.SHAPES)) },
            "tipografia" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.TYPE)) },
            "animaciones" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.MOTION)) },
            "estadisticas" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.STATS)) },
            "inicio" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.HOME)) },
            "entrenamiento" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.TRAIN)) },
        ),
    )

    @Test
    fun systemPagesDark() = tour(
        "sys_dark", "dark",
        listOf(
            "bot_settings" to { a -> a.nav.switchTab(Route.Settings); a.nav.push(Route.SettingsPage(Pages.BOT)) },
            "widgets" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.WIDGETS)) },
            "reminders" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.REMINDERS)) },
            "icon" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.ICON)) },
            "changelog" to { a -> a.nav.pop(); a.nav.push(Route.Changelog) },
            "about" to { a -> a.nav.pop(); a.nav.push(Route.About) },
            "data" to { a -> a.nav.pop(); a.nav.push(Route.DataTools) },
        ),
    )

    @Test
    fun botDark() = tour(
        "bot_dark", "dark",
        listOf(
            "chat" to { a -> a.nav.switchTab(Route.Bot) },
            "models" to { a -> a.openModelPicker() },
        ),
        chat = true,
    )

    @Test
    fun botEmptyDark() = tour(
        "bot_empty", "dark",
        listOf("chat" to { a -> a.nav.switchTab(Route.Bot) }),
    )

    @Test
    fun lightPages() = tour(
        "sys_light", "light",
        listOf(
            "settings" to { a -> a.nav.switchTab(Route.Settings) },
            "widgets" to { a -> a.nav.push(Route.SettingsPage(Pages.WIDGETS)) },
            "reminders" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.REMINDERS)) },
            "icon" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.ICON)) },
            "bot_settings" to { a -> a.nav.pop(); a.nav.push(Route.SettingsPage(Pages.BOT)) },
            "chat" to { a -> a.nav.switchTab(Route.Bot) },
        ),
        chat = true,
    )
}
