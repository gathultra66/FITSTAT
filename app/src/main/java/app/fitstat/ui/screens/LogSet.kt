package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

/** Hoja para registrar una serie (o corregir una ya guardada). */
fun Overlays.logSet(ex: Exercise, routineId: String? = null, editing: SetLog? = null, onLogged: (LogResult?) -> Unit = {}) {
    sheet { close -> LogSetBody(ex, routineId, editing, close, onLogged) }
}

@Composable
private fun LogSetBody(ex: Exercise, routineId: String?, editing: SetLog?, close: () -> Unit, onLogged: (LogResult?) -> Unit) {
    val repo = LocalRepo.current
    val app = LocalApp.current
    val c = Fs.colors
    val units = repo.units
    val cardio = ex.isCardio
    val last = remember { repo.lastSet(ex.id) }
    val cfg = remember { repo.config(ex.id) }
    val step = remember { repo.prefs.str("stats.step").toDoubleOrNull() ?: 2.5 }
    val startKg = editing?.weightKg ?: last?.weightKg ?: cfg.targetWeightKg
    var w by remember { mutableDoubleStateOf(Math.round(Fmt.toDisplay(startKg, units) * 100.0) / 100.0) }
    var reps by remember { mutableDoubleStateOf((editing?.reps ?: last?.reps ?: cfg.repsMin).toDouble()) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var count by remember { mutableIntStateOf(0) }

    fun save(keepOpen: Boolean) {
        val kg = if (cardio) 0.0 else Math.round(Fmt.fromDisplay(w, units) * 100.0) / 100.0
        val r = reps.toInt().coerceAtLeast(1)
        if (editing != null) {
            repo.updateLog(editing.id, kg, r)
            close()
            app.toastUndo("Serie corregida")
            onLogged(null)
            return
        }
        val res = repo.logSet(ex.id, kg, r, routineId, note)
        onLogged(res)
        val text = "Serie guardada · " + describeSet(kg, r, ex, units)
        if (res.record == null && res.milestones.isEmpty()) app.overlays.toast(text, "Deshacer", Ic.Check) { repo.undo() }
        if (keepOpen) { count++; note = "" } else close()
    }

    SheetBody(ex.name, subtitle = if (editing != null) "Corregir serie" else if (count > 0) "Registrar otra serie · $count guardada${if (count == 1) "" else "s"}" else "Registrar serie") {
        if (last != null && editing == null) {
            val d = Fmt.date(last.ts, repo.zone)
            Row(
                Modifier.fillMaxWidth().clip(Fs.shapes.chip).background(c.surface2).tappable({
                    w = Math.round(Fmt.toDisplay(last.weightKg, units) * 100.0) / 100.0; reps = last.reps.toDouble()
                }).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.RotateCcw, size = 18.dp, tint = c.accentText)
                Column(Modifier.weight(1f)) {
                    Txt("Última vez: " + describeSet(last.weightKg, last.reps, ex, units), style = Fs.type.smallStrong, maxLines = 1)
                    Txt(Fmt.relativeDay(d, repo.today()), style = Fs.type.caption, color = c.textDim)
                }
                Txt("Usar", style = Fs.type.smallStrong, color = c.accentText)
            }
        }

        if (!cardio) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(if (ex.isBodyweight) "Peso extra (opcional)" else "Peso")
                NumberStepper(w, { w = it }, step = step, decimals = 2, max = 2000.0, unit = Fmt.unit(units), zeroLabel = if (ex.isBodyweight) "Peso corporal" else null)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in listOf(-10, -5, 5, 10)) {
                        Pill((if (d > 0) "+" else "−") + kotlin.math.abs(d), soft = true, height = 30.dp, onClick = { w = (w + d).coerceIn(0.0, 2000.0) })
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(if (cardio) "Minutos" else "Repeticiones")
            NumberStepper(reps, { reps = it }, step = if (cardio) 5.0 else 1.0, min = 1.0, max = 999.0, decimals = 0, unit = if (cardio) "min" else null)
            if (!cardio) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in listOf(5, 6, 8, 10, 12, 15, 20)) Pill("$n", selected = reps.toInt() == n, soft = true, height = 30.dp, onClick = { reps = n.toDouble() })
                }
            }
        }

        if (!cardio && w > 0.0) {
            val e = Stats.e1rm(Fmt.fromDisplay(w, units), reps.toInt(), repo.formula)
            if (e > 0) Txt("1RM estimado ≈ " + Fmt.weight(e, units), Modifier.fillMaxWidth(), Fs.type.small, c.textDim, align = TextAlign.Center)
        }

        if (editing == null) FsTextField(note, { note = it.take(120) }, placeholder = "Nota de esta serie (opcional)", leading = Ic.Note)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FsButton(if (editing != null) "Guardar cambios" else "Guardar serie", { save(false) }, Modifier.weight(1f), icon = Ic.Check)
            if (editing == null) FsButton("Guardar y otra", { save(true) }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.Plus)
        }
    }
}

/** "60 kg × 10", "12 reps", "30 min". */
fun describeSet(kg: Double, reps: Int, ex: Exercise, units: String): String = when {
    ex.isCardio -> "$reps min"
    kg > 0 -> Fmt.weight(kg, units) + " × " + reps
    else -> "$reps reps"
}
