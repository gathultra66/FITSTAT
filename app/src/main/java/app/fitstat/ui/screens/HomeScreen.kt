package app.fitstat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.app.startWorkout
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs

/**
 * Inicio: una cuadrícula de widgets que el usuario acomoda a su gusto. Mantener pulsado un widget (o la pantalla) abre la edición
 * (mover, achicar, agrandar, quitar y agregar); la disposición se guarda al pulsar «Listo».
 */
@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val prefs = Fs.prefs
    val data = rememberHomeData()

    val name = repo.profile.name
    val showGreeting = prefs.bool("home.greeting")
    val title = if (showGreeting && name.isNotBlank()) "Hola, $name" else if (showGreeting) "Hola" else "Inicio"

    EditablePage(
        editor = app.home,
        title = title,
        subtitle = Fmt.dateLong(data.today),
        homeData = { data },
        actions = {
            RoundButton(Ic.Search, { nav.push(Route.Search()) })
            FitBot(size = 46.dp, mood = if (data.streak >= 2) BotMood.Happy else BotMood.Idle, onTap = { nav.push(Route.Bot) })
        },
        above = { editing ->
            if (!repo.hasUserData && !editing) {
                item("welcome") { WelcomeCard(Modifier.enter(0)) }
            }
        },
        native = { _, _, _ -> },
    )
}

// ───────────────────────── Bienvenida (sin datos todavía)

@Composable
private fun WelcomeCard(modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FitBot(size = 72.dp, mood = BotMood.Happy)
            Column(Modifier.weight(1f)) {
                Txt("¡Empecemos!", style = Fs.type.h2)
                Txt("Arma tu set de ejercicios, crea una rutina y registra tu primera serie.", style = Fs.type.small, color = c.textDim)
            }
        }
        Spacer(Modifier.height(14.dp))
        for ((i, t) in listOf("Elige los ejercicios que haces" to Route.Exercises, "Crea tu primera rutina" to Route.Routines, "Pídele ayuda a FITBOT" to Route.Bot).withIndex()) {
            Row(
                Modifier.fillMaxWidth().tappable({ app.nav.push(t.second) }, pressScale = 0.985f).padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CountBadge(i + 1, size = 28.dp)
                Txt(t.first, Modifier.weight(1f), Fs.type.bodyStrong)
                Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
            }
        }
        Spacer(Modifier.height(8.dp))
        FsButton("Cargar datos de ejemplo", { repo.replaceAll(Demo.build(repo.data, repo.now(), repo.zone, repo.formula), "Datos de ejemplo cargados"); app.toastUndo("Datos de ejemplo cargados") },
            Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.Sparkles, height = 46.dp)
    }
}

// ───────────────────────── Empezar a entrenar

/** Hoja para empezar a entrenar: elegir rutina o entrenamiento libre. */
fun startSheet(app: app.fitstat.ui.app.AppState, repo: Repo) {
    app.overlays.sheet { close ->
        SheetBody("¿Qué vas a entrenar?") {
            for (r in repo.routines) {
                ListRow(
                    r.name, desc = "${Fmt.plural(r.items.size, "ejercicio", "ejercicios")} · ~${repo.routineMinutes(r)} min",
                    icon = icOf(r.icon, Ic.Dumbbell), iconTint = routineColor(r.colorIdx), iconBg = routineColor(r.colorIdx).soft(),
                    onClick = { close(); app.startWorkout(r.id) },
                    modifier = Modifier.clip(Fs.shapes.inner).background(Fs.colors.surface2),
                )
            }
            ListRow(
                "Entrenamiento libre", desc = "Elige los ejercicios sobre la marcha.", icon = Ic.Zap,
                onClick = { close(); app.startWorkout(null) }, modifier = Modifier.clip(Fs.shapes.inner).background(Fs.colors.surface2),
            )
        }
    }
}

/** Valor que sube y baja suavemente (0..1) para animar el resplandor de los músculos. */
@Composable
fun rememberPulse(): Float {
    val m = Fs.motion
    if (!m.loops) return 0f
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val v by tr.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(m.ms(1800), easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulseV",
    )
    return v
}
