package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

private val nameIdeas = listOf("Empuje", "Tirón", "Pierna", "Torso", "Full body", "Brazos", "Cardio", "Glúteo y pierna")

/** Hoja para crear (o editar) una rutina: nombre, ícono, color, días y nota. */
fun AppState.routineSheet(existing: Routine? = null, preset: List<String> = emptyList(), onDone: ((Routine) -> Unit)? = null) {
    overlays.sheet { close -> RoutineEditorBody(existing, preset, close, onDone) }
}

@Composable
private fun RoutineEditorBody(existing: Routine?, preset: List<String>, close: () -> Unit, onDone: ((Routine) -> Unit)?) {
    val repo = LocalRepo.current
    val app = LocalApp.current
    val c = Fs.colors
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember { mutableStateOf(existing?.icon ?: "Dumbbell") }
    var color by remember { mutableIntStateOf(existing?.colorIdx ?: (repo.routines.size % Repo.RoutineColors)) }
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    SheetBody(if (existing == null) "Nueva rutina" else "Editar rutina", subtitle = "Agrupa los ejercicios que haces juntos.") {
        FsTextField(name, { name = it.take(40) }, placeholder = "Nombre de la rutina", leading = Ic.Routines)
        if (existing == null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in nameIdeas) Pill(n, soft = true, height = 30.dp, onClick = { name = n })
            }
        }

        Label("Ícono")
        ChipFlow(spacing = 10.dp) {
            for (n in routineIcons) {
                val on = icon.equals(n, true)
                IconBadge(
                    icOf(n, Ic.Dumbbell), size = 46.dp, tint = if (on) c.onAccent else c.textDim, bg = if (on) c.accent else c.surface2,
                    onClick = { icon = n },
                )
            }
        }

        Label("Color")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until Repo.RoutineColors) ColorSwatch(routineColor(i), color == i, { color = i }, size = 36.dp)
        }

        Label("Días que la entrenas")
        WeekdayPicker(days, { d -> days = if (d in days) days - d else days + d }, weekStartsMonday = repo.mondayFirst)

        FsTextField(note, { note = it.take(300) }, placeholder = "Nota (opcional)", singleLine = false, minLines = 2, leading = Ic.Note)

        FsButton(if (existing == null) "Crear rutina" else "Guardar cambios", {
            if (existing == null) {
                val r = repo.createRoutine(name, icon, color, preset, days, note.trim())
                close()
                if (onDone != null) onDone(r) else {
                    app.toastUndo("Rutina «${r.name}» creada")
                    app.nav.push(Route.RoutineDetail(r.id))
                }
            } else {
                repo.updateRoutine(existing.id, "Rutina editada") { it.copy(name = name.trim().ifEmpty { it.name }, icon = icon, colorIdx = color, days = days, note = note.trim()) }
                close()
                app.overlays.toast("Cambios guardados", icon = Ic.Check)
            }
        }, Modifier.fillMaxWidth(), icon = Ic.Check)
    }
}

/** Hoja para agregar uno o varios ejercicios a una rutina existente (o crear una nueva con ellos). */
fun AppState.addToRoutineSheet(exerciseIds: List<String>) {
    overlays.sheet { close ->
        val c = Fs.colors
        SheetBody("Agregar a una rutina", subtitle = if (exerciseIds.size == 1) repo.exById[exerciseIds.first()]?.name else "${exerciseIds.size} ejercicios") {
            Column(Modifier.clip(Fs.shapes.inner).background(c.surface2)) {
                for ((i, r) in repo.routines.withIndex()) {
                    val has = exerciseIds.all { id -> r.items.any { it.exerciseId == id } }
                    ListRow(
                        r.name, desc = if (has) "Ya está en esta rutina" else Fmt.plural(r.items.size, "ejercicio", "ejercicios"),
                        icon = icOf(r.icon, Ic.Dumbbell), iconTint = routineColor(r.colorIdx), iconBg = routineColor(r.colorIdx).soft(),
                        onClick = if (has) null else {
                            {
                                repo.addToRoutine(r.id, exerciseIds)
                                close()
                                overlays.toast("Agregado a «${r.name}»", "Ver", Ic.Check) { nav.push(Route.RoutineDetail(r.id)) }
                            }
                        },
                        trailing = if (has) ({ SetBadge() }) else null,
                    )
                    if (i < repo.routines.lastIndex) Divider()
                }
                if (repo.routines.isNotEmpty()) Divider()
                ListRow("Crear rutina nueva", icon = Ic.Plus, onClick = { close(); routineSheet(preset = exerciseIds) })
            }
        }
    }
}
