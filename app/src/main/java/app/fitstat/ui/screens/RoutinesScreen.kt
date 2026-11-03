package app.fitstat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.app.startWorkout
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix

// ───────────────────────── Pestaña Rutinas

/**
 * Rutinas: una tarjeta por rutina y el atajo al entrenamiento libre. Igual que el inicio: mantén pulsada una tarjeta
 * (o la pantalla) para moverlas, cambiarles el tamaño y agregar widgets del inicio. Las opciones de cada rutina (iniciar,
 * editar, duplicar, eliminar) están en el botón ⋮ de su tarjeta.
 */
@Composable
fun RoutinesScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val routines = repo.routines
    val configs = repo.configs
    val todayDow = remember { repo.today().dayOfWeek.value }

    EditablePage(
        editor = app.routinesLayout,
        title = "Rutinas",
        subtitle = if (routines.isEmpty()) "Tus entrenamientos" else Fmt.plural(routines.size, "rutina", "rutinas"),
        actions = { RoundButton(Ic.Plus, { app.routineSheet() }) },
        // Sin rutinas, la pantalla invita a crear la primera (y no avisa «vacía» aparte).
        emptyHint = routines.isNotEmpty(),
        nativeVisible = { id -> id != "rt_free" || routines.isNotEmpty() },
        above = {
            if (routines.isEmpty()) {
                item("empty") {
                    EmptyState(
                        "Crea tu primera rutina",
                        "Una rutina junta los ejercicios que haces el mismo día. Puedes tener todas las que quieras y con los ejercicios que quieras.",
                        Modifier.enter(0), mood = BotMood.Happy, actionLabel = "Crear rutina", onAction = { app.routineSheet() },
                    )
                }
                if (configs.values.any { it.inSet }) item("fromSet") {
                    FsButton(
                        "Crear una con mi set", {
                            val r = repo.createRoutine("Mi set", exerciseIds = repo.setIds)
                            app.toastUndo("Rutina «${r.name}» creada")
                            nav.push(Route.RoutineDetail(r.id))
                        },
                        Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.ListPlus,
                    )
                }
            }
        },
    ) { id, size, mod ->
        if (id == "rt_free") {
            FreeWorkoutCard(size, mod) { app.startWorkout(null) }
        } else if (HomeLayout.isRoutineBlock(id)) {
            val r = routines.firstOrNull { it.id == id.removePrefix(HomeLayout.ROUTINE_PREFIX) }
            if (r != null) RoutineCard(r, size, todayDow in r.days, mod)
        }
    }
}

@Composable
private fun FreeWorkoutCard(size: HomeSize, modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    if (size == HomeSize.Large) {
        Card(modifier, onClick = onClick, shape = Fs.shapes.inner) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(Ic.Zap, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Txt("Entrenamiento libre", style = Fs.type.bodyStrong)
                    Txt("Sin rutina: elige los ejercicios sobre la marcha.", style = Fs.type.small, color = c.textDim)
                }
                Icon(Ic.ChevronRight, size = 20.dp, tint = c.textFaint)
            }
        }
    } else {
        Card(modifier, onClick = onClick, shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(14))) {
            IconBadge(Ic.Zap, size = 40.dp)
            Spacer(Modifier.height(8.dp))
            Txt("Entrenamiento libre", style = Fs.type.bodyStrong, maxLines = 2)
            Txt("Sin rutina, sobre la marcha.", style = Fs.type.caption, color = c.textDim, maxLines = 2)
        }
    }
}

/** La tarjeta de una rutina: grande (ancho completo) o mediana (media fila). */
@Composable
private fun RoutineCard(r: Routine, size: HomeSize, today: Boolean, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val nav = app.nav
    val exs = r.items.mapNotNull { repo.exById[it.exerciseId] }
    val col = routineColor(r.colorIdx)
    val summary = if (r.items.isEmpty()) "Sin ejercicios todavía" else "${Fmt.plural(r.items.size, "ejercicio", "ejercicios")} · ~${repo.routineMinutes(r)} min"
    if (size == HomeSize.Large) {
        Card(modifier.testTag("routine_${r.id}"), onClick = { nav.push(Route.RoutineDetail(r.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(icOf(r.icon, Ic.Dumbbell), size = 52.dp, tint = col, bg = col.soft())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(r.name, Modifier.weight(1f, fill = false), Fs.type.h2, maxLines = 1)
                        if (today) Txt("HOY", Modifier.clip(Fs.shapes.pill).background(c.accent).padding(horizontal = 8.dp, vertical = 2.dp), Fs.type.caption, c.onAccent)
                    }
                    Txt(summary, style = Fs.type.small, color = c.textDim, maxLines = 1)
                }
                RoundButton(Ic.Play, { app.startWorkout(r.id) }, size = 46.dp, tint = c.onAccent, bg = c.accent, border = false)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(Weekdays.label(r.days), Modifier.weight(1f), Fs.type.caption, c.textDim, maxLines = 1)
                if (exs.isNotEmpty()) PhotoStack(exs, size = 34.dp, max = 4)
                RoundButton(Ic.MoreV, { routineMenu(app, r) }, Modifier.testTag("routine_menu_${r.id}"), size = 34.dp, bg = c.surface3, border = false, tint = c.textDim)
            }
        }
    } else {
        Card(modifier.testTag("routine_${r.id}"), onClick = { nav.push(Route.RoutineDetail(r.id)) }, padding = PaddingValues(Fs.ui.pad(14))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(icOf(r.icon, Ic.Dumbbell), size = 40.dp, tint = col, bg = col.soft())
                Column(Modifier.weight(1f)) {
                    Txt(r.name, style = Fs.type.bodyStrong, maxLines = 2)
                    if (today) Txt("HOY", Modifier.padding(top = 2.dp).clip(Fs.shapes.pill).background(c.accent).padding(horizontal = 7.dp, vertical = 1.dp), Fs.type.caption, c.onAccent)
                }
            }
            Spacer(Modifier.height(6.dp))
            Txt(summary, style = Fs.type.caption, color = c.textDim, maxLines = 2)
            Spacer(Modifier.weight(1f, fill = true))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exs.isNotEmpty()) PhotoStack(exs, size = 28.dp, max = 2)
                Spacer(Modifier.weight(1f))
                RoundButton(Ic.MoreV, { routineMenu(app, r) }, Modifier.testTag("routine_menu_${r.id}"), size = 36.dp, bg = c.surface3, border = false, tint = c.textDim)
                RoundButton(Ic.Play, { app.startWorkout(r.id) }, size = 40.dp, tint = c.onAccent, bg = c.accent, border = false)
            }
        }
    }
}

private fun routineMenu(app: app.fitstat.ui.app.AppState, r: Routine) {
    val repo = app.repo
    app.overlays.sheet { close ->
        SheetBody(r.name, subtitle = "Opciones de la rutina") {
            Column(Modifier.clip(Fs.shapes.inner).background(Fs.colors.surface2)) {
                ListRow("Iniciar", icon = Ic.Play, onClick = { close(); app.startWorkout(r.id) })
                Divider()
                ListRow("Editar", icon = Ic.Edit, onClick = { close(); app.routineSheet(existing = r) })
                Divider()
                ListRow("Duplicar", icon = Ic.Copy, onClick = {
                    close(); repo.duplicateRoutine(r.id)?.let { app.toastUndo("Copia creada: «${it.name}»") }
                })
                Divider()
                ListRow("Eliminar", icon = Ic.Trash, iconTint = Fs.colors.bad, iconBg = Fs.colors.bad.a(0.14f), onClick = {
                    close()
                    app.overlays.confirm("¿Eliminar «${r.name}»?", "Los ejercicios y tus series registradas no se borran.", "Eliminar", danger = true) {
                        repo.deleteRoutine(r.id)
                        app.nav.popWhile { it is Route.RoutineDetail && it.id == r.id }
                        app.toastUndo("Rutina eliminada")
                    }
                })
            }
        }
    }
}

// ───────────────────────── Detalle de una rutina

@Composable
fun RoutineScreen(id: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val r = repo.routines.firstOrNull { it.id == id }
    if (r == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val col = routineColor(r.colorIdx)
    val totalSets = r.items.sumOf { repo.itemSets(it) }
    val items = r.items
    var open by rememberSaveable(id) { mutableStateOf<String?>(null) }

    fun updItem(exId: String, f: (RoutineItem) -> RoutineItem) =
        repo.updateRoutine(id) { rt -> rt.copy(items = rt.items.map { if (it.exerciseId == exId) f(it) else it }) }

    ScreenList(
        title = r.name, subtitle = Weekdays.label(r.days), onBack = { nav.back() },
        actions = {
            RoundButton(Ic.Edit, { app.routineSheet(existing = r) })
            RoundButton(Ic.MoreV, { routineMenu(app, r) })
        },
    ) {
        item("hero") {
            val dark = col.luminance() < 0.34f
            val on = if (dark) Color.White else Color(0xFF14111F)
            Box(
                Modifier.enter(0).fillMaxWidth().clip(Fs.shapes.card)
                    .background(Brush.linearGradient(listOf(col, col.mix(Color.Black, 0.28f))))
                    .padding(Fs.ui.pad(18)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(icOf(r.icon, Ic.Dumbbell), size = 50.dp, tint = on, bg = on.a(0.16f))
                        Column(Modifier.weight(1f)) {
                            Txt(r.name, style = Fs.type.h1, color = on, maxLines = 1)
                            if (r.note.isNotBlank()) Txt(r.note, style = Fs.type.small, color = on.a(0.75f), maxLines = 2)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStat("${items.size}", if (items.size == 1) "ejercicio" else "ejercicios", on, Modifier.weight(1f))
                        HeroStat("$totalSets", "series", on, Modifier.weight(1f))
                        HeroStat("~${repo.routineMinutes(r)}", "minutos", on, Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(Fs.shapes.pill).background(on).tappable({ app.startWorkout(r.id) }, enabled = items.isNotEmpty())
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Play, size = 20.dp, tint = if (dark) col.mix(Color.Black, 0.35f) else Color.White)
                        Spacer(Modifier.width(8.dp))
                        Txt(if (items.isEmpty()) "Agrega ejercicios para empezar" else "Iniciar rutina", style = Fs.type.bodyStrong, color = if (dark) col.mix(Color.Black, 0.35f) else Color.White)
                    }
                }
            }
        }

        item("days") {
            Card(Modifier.enter(1)) {
                SectionTitle("Días", trailing = { Txt(Weekdays.label(r.days), style = Fs.type.caption, color = c.textDim) })
                Spacer(Modifier.height(8.dp))
                WeekdayPicker(r.days, { d -> repo.updateRoutine(id) { rt -> rt.copy(days = if (d in rt.days) rt.days - d else rt.days + d) } }, weekStartsMonday = repo.mondayFirst)
            }
        }

        item("exTitle") {
            SectionTitle(
                "Ejercicios", Modifier.enter(2), action = "Agregar",
                onAction = {
                    app.overlays.pickExercises("Agregar a ${r.name}", exclude = items.map { it.exerciseId }.toSet()) { ids -> repo.addToRoutine(id, ids) }
                },
            )
        }
        if (items.isEmpty()) item("emptyEx") {
            EmptyState(
                "Rutina vacía", "Agrega los ejercicios que quieras: elígelos del catálogo o desde tu set.",
                Modifier.enter(3), mood = BotMood.Idle, actionLabel = "Elegir ejercicios",
                onAction = { app.overlays.pickExercises("Agregar a ${r.name}") { ids -> repo.addToRoutine(id, ids) } }, botSize = 80.dp,
            )
        }
        itemsIndexed(items, key = { _, ri -> "i_" + ri.exerciseId }) { idx, item ->
            val ex = repo.exById[item.exerciseId]
            if (ex != null) {
                val isOpen = open == ex.id
                val sets = repo.itemSets(item)
                val (lo, hi) = repo.itemReps(item)
                val custom = item.sets > 0 || item.repsMin > 0
                Card(Modifier.enter(3 + idx), shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(10))) {
                    Row(
                        Modifier.fillMaxWidth().tappable({ open = if (isOpen) null else ex.id }, pressScale = 0.99f),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CountBadge(idx + 1, size = 24.dp)
                        ExerciseThumb(ex, 52.dp, shape = Fs.shapes.chip)
                        Column(Modifier.weight(1f)) {
                            Txt(ex.name, style = Fs.type.bodyStrong, maxLines = 1)
                            Txt(
                                "$sets × ${if (hi > lo) "$lo-$hi" else "$lo"}${if (ex.isCardio) " min" else ""}" + if (custom) " · personalizado" else "",
                                style = Fs.type.small, color = if (custom) c.accentText else c.textDim, maxLines = 1,
                            )
                        }
                        Icon(if (isOpen) Ic.ChevronUp else Ic.ChevronDown, size = 18.dp, tint = c.textFaint)
                    }
                    AnimatedVisibility(
                        isOpen,
                        enter = expandVertically(Fs.motion.tween(240)) + fadeIn(Fs.motion.tween(240)),
                        exit = shrinkVertically(Fs.motion.tween(200)) + fadeOut(Fs.motion.tween(140)),
                    ) {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HairLine()
                            if (!ex.isCardio) {
                                RoutineStepRow("Series") {
                                    Stepper(sets.toString(), { updItem(ex.id) { it.copy(sets = (sets - 1).coerceAtLeast(1)) } }, { updItem(ex.id) { it.copy(sets = (sets + 1).coerceAtMost(12)) } })
                                }
                            }
                            RoutineStepRow(if (ex.isCardio) "Minutos" else "Repeticiones") {
                                val st = if (ex.isCardio) 5 else 1
                                Stepper(
                                    if (hi > lo) "$lo-$hi" else "$lo",
                                    { val nl = (lo - st).coerceAtLeast(1); updItem(ex.id) { it.copy(repsMin = nl, repsMax = if (hi > 0) maxOf(hi, nl + 1) else 0) } },
                                    { val nl = (lo + st).coerceAtMost(300); updItem(ex.id) { it.copy(repsMin = nl, repsMax = if (hi > 0) maxOf(hi, nl + 1) else 0) } },
                                )
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoundButton(Ic.ArrowUp, { repo.moveInRoutine(id, idx, idx - 1) }, size = 38.dp)
                                RoundButton(Ic.ArrowDown, { repo.moveInRoutine(id, idx, idx + 1) }, size = 38.dp)
                                Spacer(Modifier.weight(1f))
                                if (custom) Pill("Usar valores del ejercicio", soft = true, height = 34.dp, onClick = { updItem(ex.id) { it.copy(sets = 0, repsMin = 0, repsMax = 0) } })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FsButton("Ver ejercicio", { nav.push(Route.Exercise(ex.id)) }, Modifier.weight(1f), kind = BtnKind.Secondary, height = 42.dp, icon = Ic.Eye)
                                FsButton("Quitar", {
                                    repo.removeFromRoutine(id, ex.id)
                                    app.toastUndo("«${ex.name}» salió de la rutina")
                                }, Modifier.weight(1f), kind = BtnKind.Danger, height = 42.dp, icon = Ic.Trash)
                            }
                        }
                    }
                }
            }
        }
        if (items.isNotEmpty()) item("addMore") {
            val inSetNotIn = repo.setIds.filter { sid -> items.none { it.exerciseId == sid } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FsButton("Agregar ejercicios", {
                    app.overlays.pickExercises("Agregar a ${r.name}", exclude = items.map { it.exerciseId }.toSet()) { ids -> repo.addToRoutine(id, ids) }
                }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.Plus, height = 48.dp)
                if (inSetNotIn.isNotEmpty()) FsButton("Todo mi set (${inSetNotIn.size})", {
                    repo.addToRoutine(id, inSetNotIn)
                    app.toastUndo("Agregué ${Fmt.plural(inSetNotIn.size, "ejercicio", "ejercicios")}")
                }, Modifier.weight(1f), kind = BtnKind.Ghost, icon = Ic.ListPlus, height = 48.dp)
            }
        }

        item("reminder") { RoutineReminder(r) }

        item("note") {
            var note by remember(id) { mutableStateOf(r.note) }
            LaunchedEffect(note) {
                if (note != repo.routine(id)?.note) {
                    kotlinx.coroutines.delay(600)
                    repo.updateRoutine(id) { it.copy(note = note) }
                }
            }
            Card {
                SectionTitle("Nota de la rutina")
                Spacer(Modifier.height(8.dp))
                FsTextField(note, { note = it.take(300) }, placeholder = "Calentamiento, objetivo, cómo progresar…", singleLine = false, minLines = 2, leading = Ic.Note)
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, on: Color, modifier: Modifier) {
    Column(modifier.clip(Fs.shapes.chip).background(on.a(0.14f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(value, style = Fs.type.title, color = on, maxLines = 1)
        Txt(label, style = Fs.type.caption, color = on.a(0.78f), maxLines = 1)
    }
}

@Composable
private fun RoutineStepRow(title: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(title, Modifier.weight(1f), Fs.type.bodyStrong)
        content()
    }
}

// ───────────────────────── Recordatorio de la rutina

@Composable
private fun RoutineReminder(r: Routine) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val rem = r.reminderId?.let { rid -> repo.reminders.firstOrNull { it.id == rid } }
    val on = rem?.enabled == true
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(Ic.BellRing, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Txt("Recordatorio", style = Fs.type.bodyStrong)
                Txt(
                    if (rem != null && on) "${Fmt.time(rem.hour, rem.minute)} · ${Weekdays.label(rem.days)}" else "Te aviso a la hora que elijas para entrenar.",
                    style = Fs.type.small, color = c.textDim, maxLines = 2,
                )
            }
            Toggle(on, { want ->
                if (want) {
                    app.ensureNotifications()
                    if (rem != null) repo.saveReminder(rem.copy(enabled = true))
                    else {
                        val t = repo.prefs.str("reminders.time").split(":")
                        val saved = repo.saveReminder(
                            Reminder(
                                id = "", title = r.name, body = "Hoy toca ${r.name}. ¡Vamos!",
                                hour = t.getOrNull(0)?.toIntOrNull() ?: 18, minute = t.getOrNull(1)?.toIntOrNull() ?: 0,
                                days = if (r.days.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else r.days, enabled = true, routineId = r.id,
                            ),
                        )
                        repo.updateRoutine(r.id) { it.copy(reminderId = saved.id) }
                    }
                } else if (rem != null) {
                    repo.saveReminder(rem.copy(enabled = false))
                }
            })
        }
        AnimatedVisibility(
            rem != null && on,
            enter = expandVertically(Fs.motion.tween(240)) + fadeIn(Fs.motion.tween(240)),
            exit = shrinkVertically(Fs.motion.tween(200)) + fadeOut(Fs.motion.tween(140)),
        ) {
            if (rem != null) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Hora: ${Fmt.time(rem.hour, rem.minute)}", icon = Ic.Clock, onClick = {
                        app.overlays.pickTime("Hora del recordatorio", rem.hour, rem.minute) { h, m -> repo.saveReminder(rem.copy(hour = h, minute = m)) }
                    })
                    WeekdayPicker(rem.days, { d ->
                        val nd = if (d in rem.days) rem.days - d else rem.days + d
                        if (nd.isNotEmpty()) repo.saveReminder(rem.copy(days = nd))
                    }, weekStartsMonday = repo.mondayFirst)
                }
            }
        }
    }
}
