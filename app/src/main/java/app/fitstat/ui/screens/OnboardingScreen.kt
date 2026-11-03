package app.fitstat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.*
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.Palettes
import app.fitstat.ui.theme.a

private const val STEPS = 4

/** Bienvenida de la primera vez: presenta a FITBOT, pide lo básico y deja elegir el estilo. */
@Composable
fun OnboardingScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val m = Fs.motion
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(repo.profile.name) }
    var goal by rememberSaveable { mutableIntStateOf(repo.weeklyGoal) }
    var units by rememberSaveable { mutableStateOf(repo.units) }
    var demo by rememberSaveable { mutableStateOf(false) }

    fun finish() {
        if (name.isNotBlank()) repo.setProfileName(name)
        if (demo) repo.replaceAll(Demo.build(repo.data, repo.now(), repo.zone, repo.formula))
        repo.setSetting("train.goal", goal.toString())
        repo.setSetting("stats.units", units)
        repo.finishOnboarding()
        app.celebrate()
    }

    Column(Modifier.fillMaxSize().padding(top = statusBarTop()).navigationBarsPadding()) {
        // Indicador de pasos
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until STEPS) {
                val on = i <= step
                Box(
                    Modifier.weight(if (i == step) 2f else 1f).height(6.dp).clip(Fs.shapes.pill)
                        .background(if (on) c.accent else c.surface3),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState >= initialState) 1 else -1
                (fadeIn(m.tween(320, 80)) + slideInHorizontally(m.tween(380)) { dir * it / 4 }) togetherWith
                    (fadeOut(m.tween(160)) + slideOutHorizontally(m.tween(300)) { -dir * it / 4 })
            },
            label = "onboardingStep",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (s) {
                    0 -> WelcomePage()
                    1 -> BasicsPage(name, { name = it }, goal, { goal = it }, units, { units = it })
                    2 -> StylePage()
                    else -> StartPage(demo) { demo = it }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) FsButton("Atrás", { step-- }, kind = BtnKind.Secondary, modifier = Modifier.width(110.dp))
            FsButton(
                when (step) {
                    0 -> "Empezar"
                    STEPS - 1 -> "Entrar a FITSTAT"
                    else -> "Siguiente"
                },
                { if (step < STEPS - 1) step++ else finish() },
                Modifier.weight(1f), trailingIcon = if (step < STEPS - 1) Ic.ArrowRight else null, icon = if (step == STEPS - 1) Ic.Rocket else null,
            )
        }
    }
}

// ───────────────────────── Páginas

@Composable
private fun WelcomePage() {
    val c = Fs.colors
    val bot = rememberMascotController()
    Spacer(Modifier.height(8.dp))
    LedBanner("HOLA > SOY ${Fs.skin.info.callsign}", lit = c.accentText, maxPitch = 7.dp)
    Spacer(Modifier.height(10.dp))
    FitBot(size = 190.dp, mood = BotMood.Happy, controller = bot, onTap = { bot.poke() })
    Spacer(Modifier.height(10.dp))
    Txt("Bienvenido a FITSTAT", style = Fs.type.title, align = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Txt(
        "Tu lugar para guardar tus estadísticas del gym, rutinas, hitos, anotaciones y recordatorios.",
        style = Fs.type.body, color = c.textDim, align = TextAlign.Center,
    )
    Spacer(Modifier.height(22.dp))
    Feature(Ic.Stats, "Registra y mejora", "Guarda cada serie y mira tu progreso en gráficas, calendario y mapa muscular.")
    Spacer(Modifier.height(10.dp))
    Feature(Ic.Dumbbell, "176 ejercicios", "En 10 categorías, con fotos y los músculos resaltados. Crea los tuyos si falta alguno.")
    Spacer(Modifier.height(10.dp))
    Feature(Ic.Bot, "FITBOT, tu asistente", "Conecta tu clave de OpenRouter y pídele que arme rutinas, cambie ajustes o revise tus marcas.")
}

@Composable
private fun Feature(ic: Ic, title: String, text: String) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(ic, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Txt(title, style = Fs.type.bodyStrong)
            Txt(text, style = Fs.type.small, color = c.textDim)
        }
    }
}

@Composable
private fun BasicsPage(
    name: String, onName: (String) -> Unit,
    goal: Int, onGoal: (Int) -> Unit,
    units: String, onUnits: (String) -> Unit,
) {
    val c = Fs.colors
    Spacer(Modifier.height(12.dp))
    FitBot(size = 110.dp, mood = BotMood.Idle)
    Spacer(Modifier.height(8.dp))
    Txt("Cuéntame de ti", style = Fs.type.title, align = TextAlign.Center)
    Txt("Todo se puede cambiar después en Ajustes.", Modifier.padding(top = 4.dp), Fs.type.small, c.textDim, align = TextAlign.Center)
    Spacer(Modifier.height(22.dp))

    Card(Modifier.fillMaxWidth()) {
        Label("¿Cómo te llamas? (opcional)")
        Spacer(Modifier.height(8.dp))
        FsTextField(name, { onName(it.take(24)) }, placeholder = "Tu nombre", leading = Ic.User, imeAction = ImeAction.Done)
    }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) {
        Label("¿Cuántos días a la semana quieres entrenar?")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (n in 1..7) {
                val on = n == goal
                Box(
                    Modifier.size(40.dp).clip(Fs.shapes.round).background(if (on) c.accent else c.surface2)
                        .border(1.dp, if (on) c.accent else c.outline, Fs.shapes.round).tappable({ onGoal(n) }, pressScale = 0.9f),
                    contentAlignment = Alignment.Center,
                ) { Txt("$n", style = Fs.type.bodyStrong, color = if (on) c.onAccent else c.text, maxLines = 1) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) {
        Label("Unidad de peso")
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("Kilogramos (kg)", "Libras (lb)"), if (units == "lb") 1 else 0, { onUnits(if (it == 1) "lb" else "kg") }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StylePage() {
    val repo = LocalRepo.current
    val c = Fs.colors
    val palette = repo.prefs.str("theme.palette")
    val mode = repo.prefs.str("theme.mode")
    Spacer(Modifier.height(12.dp))
    FitBot(size = 110.dp, mood = BotMood.Love)
    Spacer(Modifier.height(8.dp))
    Txt("Elige tu estilo", style = Fs.type.title, align = TextAlign.Center)
    Txt("Verás el cambio al instante. Hay muchísimas más opciones en Ajustes.", Modifier.padding(top = 4.dp), Fs.type.small, c.textDim, align = TextAlign.Center)
    Spacer(Modifier.height(22.dp))

    Card(Modifier.fillMaxWidth()) {
        Label("Color principal")
        Spacer(Modifier.height(12.dp))
        ChipFlow(spacing = 12.dp) {
            for (p in Palettes.all) ColorSwatch(p.accent, selected = palette == p.id, onClick = { repo.setSetting("theme.palette", p.id) }, size = 46.dp)
        }
        Spacer(Modifier.height(8.dp))
        Txt(Palettes.byId(palette)?.label ?: "Color", style = Fs.type.small, color = c.textDim)
    }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) {
        Label("Modo")
        Spacer(Modifier.height(10.dp))
        val modes = listOf("dark" to "Oscuro", "light" to "Claro", "amoled" to "AMOLED", "system" to "Sistema")
        Segmented(modes.map { it.second }, modes.indexOfFirst { it.first == mode }.coerceAtLeast(0), { repo.setSetting("theme.mode", modes[it].first) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StartPage(demo: Boolean, onDemo: (Boolean) -> Unit) {
    val c = Fs.colors
    Spacer(Modifier.height(12.dp))
    FitBot(size = 120.dp, mood = BotMood.Happy)
    Spacer(Modifier.height(8.dp))
    Txt("¿Cómo empezamos?", style = Fs.type.title, align = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    Choice(
        Ic.Sparkle, "Desde cero", "Tu app vacía, lista para que agregues tus ejercicios y rutinas.",
        selected = !demo, onClick = { onDemo(false) },
    )
    Spacer(Modifier.height(10.dp))
    Choice(
        Ic.Rocket, "Con datos de ejemplo", "Tres rutinas y dos meses de entrenamientos de muestra para explorar. Se pueden borrar en un toque.",
        selected = demo, onClick = { onDemo(true) },
    )
    Spacer(Modifier.height(18.dp))
    Row(
        Modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.accentSoft).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ic.Key, size = 22.dp, tint = c.accentText)
        Txt(
            "Para chatear con FITBOT pega tu clave de OpenRouter en Ajustes › FITBOT. Tú eliges el modelo.",
            Modifier.weight(1f), Fs.type.small, c.text,
        )
    }
}

@Composable
private fun Choice(ic: Ic, title: String, text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick, pressScale = 0.985f).clip(Fs.shapes.inner)
            .background(if (selected) c.accent.a(0.14f) else c.surface)
            .border(1.5.dp, if (selected) c.accent else c.outline.a(0.6f), Fs.shapes.inner).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(ic, size = 46.dp)
        Column(Modifier.weight(1f)) {
            Txt(title, style = Fs.type.h3, color = if (selected) c.accentText else c.text)
            Txt(text, style = Fs.type.small, color = c.textDim)
        }
        Box(
            Modifier.size(24.dp).clip(Fs.shapes.round).background(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                .border(2.dp, if (selected) c.accent else c.outline, Fs.shapes.round),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Ic.Check, size = 15.dp, tint = c.onAccent, style = "line", weight = 3f) }
    }
}
