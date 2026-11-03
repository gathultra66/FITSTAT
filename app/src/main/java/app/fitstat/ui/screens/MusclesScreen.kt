package app.fitstat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

/** Explicaciones cortas de cada músculo, para la pantalla de detalle. */
object MuscleInfo {
    class Info(val region: String, val what: String, val tip: String)

    val all: Map<Muscle, Info> = mapOf(
        Muscle.NECK to Info("Cuello", "Flexiona, extiende y gira la cabeza.", "Casi no se trabaja de forma directa; ayuda a estabilizar la cabeza en press y dominadas."),
        Muscle.TRAPS to Info("Entre el cuello y la mitad de la espalda", "Eleva y junta los omóplatos y estabiliza el hombro.", "Se activa en encogimientos, remos y peso muerto. Evita subir los hombros hacia las orejas en los press."),
        Muscle.SHOULDERS to Info("Deltoides: frontal, lateral y posterior", "Levanta el brazo al frente, a los lados y hacia atrás.", "Combina un press con elevaciones laterales y posteriores para un hombro redondo y sano."),
        Muscle.CHEST to Info("Pectoral mayor y menor", "Junta los brazos al frente y empuja el peso lejos del cuerpo.", "Cambia el ángulo del banco: inclinado carga más la parte alta; plano, el centro."),
        Muscle.BICEPS to Info("Parte frontal del brazo", "Dobla el codo y gira la palma hacia arriba.", "Controla la bajada y evita balancear el torso para que el trabajo sea del bíceps."),
        Muscle.TRICEPS to Info("Parte trasera del brazo", "Estira el codo; es cerca de dos tercios del grosor del brazo.", "Mantén los codos quietos y cerca del cuerpo en las extensiones."),
        Muscle.FOREARMS to Info("Antebrazos", "Mueven la muñeca y los dedos y dan fuerza de agarre.", "Trabajan en casi todos los jalones y remos; si el agarre se te acaba antes que el músculo, usa straps."),
        Muscle.ABS to Info("Recto abdominal y oblicuos", "Flexiona y rota el tronco y lo mantiene estable.", "Respira controlado y no tires del cuello. Un core firme mejora casi todos tus ejercicios."),
        Muscle.LATS to Info("Dorsal ancho", "Baja y lleva el brazo hacia atrás, como en un jalón o una dominada.", "Inicia el movimiento con los codos, no con las manos."),
        Muscle.MID_BACK to Info("Romboides y trapecio medio", "Junta los omóplatos y mantiene los hombros hacia atrás.", "Aprieta los omóplatos al final de cada remo y haz una pausa corta."),
        Muscle.LOWER_BACK to Info("Erectores de la columna", "Extiende y estabiliza la columna baja.", "Mantén la espalda neutra; si se redondea, baja el peso."),
        Muscle.GLUTES to Info("Glúteo mayor", "Extiende la cadera: empuja hacia arriba y hacia adelante.", "Empuja con los talones y aprieta al final del movimiento."),
        Muscle.QUADS to Info("Parte frontal del muslo", "Estira la rodilla (cuatro cabezas).", "Rodillas alineadas con la punta de los pies; sube sin que se te junten."),
        Muscle.HAMSTRINGS to Info("Parte trasera del muslo", "Dobla la rodilla y ayuda a extender la cadera.", "Baja lento y con control para sentir más el músculo."),
        Muscle.ADDUCTORS to Info("Cara interna del muslo", "Junta las piernas y estabiliza la cadera.", "Se activan en sentadillas amplias y estocadas; no los descuides si tienes rodillas sensibles."),
        Muscle.ABDUCTORS to Info("Glúteo medio y cara externa de la cadera", "Separa las piernas y estabiliza la pelvis al caminar o correr.", "Mejoran el equilibrio y la estabilidad de la rodilla."),
        Muscle.CALVES to Info("Gemelos y sóleo", "Levanta el talón y empuja el pie contra el piso.", "Rango completo: baja el talón al máximo y sube lo más alto que puedas."),
    )

    fun of(m: Muscle): Info = all[m] ?: Info(m.label, "", "")
}

private fun sideOf(m: Muscle, current: Boolean): Boolean = when {
    m.isBack && !m.isFront -> true
    m.isFront && !m.isBack -> false
    else -> current
}

/** Detalle de qué músculos trabaja un ejercicio, con la figura humana resaltada. */
@Composable
fun MusclesScreen(id: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val m = Fs.motion
    val ex = repo.exById[id]
    if (ex == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val levels = remember(ex) {
        HashMap<Muscle, Int>().apply {
            ex.primary.forEach { put(it, 2) }
            ex.secondary.forEach { putIfAbsent(it, 1) }
        }
    }
    val involved = remember(ex) { ex.primary + ex.secondary.filter { it !in ex.primary } }
    var back by rememberSaveable(id) { mutableStateOf(bestSide(levels)) }
    var selected by rememberSaveable(id) { mutableStateOf(involved.firstOrNull()?.name) }
    val sel = selected?.let { n -> Muscle.entries.firstOrNull { it.name == n } }
    val pulse = rememberPulse()
    val others = remember(ex, repo.exercises) {
        val p = ex.primary.toSet()
        repo.exercises.filter { it.id != ex.id && it.primary.any { m -> m in p } }
            .sortedWith(compareByDescending<Exercise> { o -> o.primary.count { it in p } }.thenBy { it.name }).take(14)
    }

    ScreenList(title = "Músculos", subtitle = ex.name, onBack = { nav.back() }) {
        item("fig") {
            Card(Modifier.enter(0)) {
                Segmented(listOf("Frente", "Espalda"), if (back) 1 else 0, { back = it == 1 }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(430.dp).clip(Fs.shapes.inner).background(c.surface2), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        back, Modifier.fillMaxSize(), label = "side",
                        transitionSpec = {
                            val dir = if (targetState) 1 else -1
                            (fadeIn(m.tween(320)) + slideInHorizontally(m.tween(320)) { dir * it / 4 }) togetherWith
                                (fadeOut(m.tween(200)) + slideOutHorizontally(m.tween(320)) { -dir * it / 4 })
                        },
                    ) { isBack ->
                        MuscleFigure(levels, Modifier.fillMaxSize(), back = isBack, pulse = pulse, focus = sel, padding = 0.05f)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(c.accent, "Principal")
                    LegendDot(c.accent.a(0.38f), "Secundario")
                }
            }
        }
        item("chips") {
            Column(Modifier.enter(1), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Toca un músculo")
                ChipFlow {
                    for (m in involved) {
                        val primary = m in ex.primary
                        Pill(
                            m.label, selected = sel == m, soft = !primary,
                            onClick = { selected = m.name; back = sideOf(m, back) },
                        )
                    }
                }
            }
        }
        item("info") {
            Card(Modifier.enter(2)) {
                AnimatedContent(
                    sel, label = "muscleInfo",
                    transitionSpec = { fadeIn(m.tween(240, 80)) togetherWith fadeOut(m.tween(140)) },
                ) { m ->
                    if (m == null) {
                        Txt("Elige un músculo para ver qué hace.", style = Fs.type.small, color = c.textDim)
                    } else {
                        val info = MuscleInfo.of(m)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(12.dp).clip(Fs.shapes.round).background(if (m in ex.primary) c.accent else c.accent.a(0.38f)))
                                Txt(m.label, Modifier.weight(1f), Fs.type.h2)
                                Pill(if (m in ex.primary) "Principal" else "Secundario", soft = true, height = 28.dp)
                            }
                            Txt(info.region, style = Fs.type.smallStrong, color = c.accentText)
                            Txt(info.what, style = Fs.type.body)
                            Row(
                                Modifier.fillMaxWidth().clip(Fs.shapes.chip).background(c.surface2).padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Ic.Lightbulb, size = 20.dp, tint = c.warn)
                                Txt(info.tip, Modifier.weight(1f), Fs.type.small, c.textDim)
                            }
                        }
                    }
                }
            }
        }
        if (others.isNotEmpty()) item("others") {
            Column(Modifier.enter(3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Más ejercicios para estos músculos")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(others, key = { it.id }) { o ->
                        Column(
                            Modifier.width(112.dp).tappable({ nav.push(Route.Exercise(o.id)) }, pressScale = 0.96f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ExerciseThumb(o, 112.dp, shape = Fs.shapes.inner)
                            Txt(o.name, style = Fs.type.caption, color = c.text, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(12.dp).clip(Fs.shapes.round).background(color))
        Txt(text, style = Fs.type.small, color = Fs.colors.textDim)
    }
}
