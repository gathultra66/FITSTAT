package app.fitstat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.*
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.delay

// ───────────────────────── Plan de cada ejercicio dentro de la sesión

private class ExPlan(val sets: Int, val repsLabel: String, val repsLo: Int, val restSec: Int)

private fun planOf(repo: Repo, routine: Routine?, id: String): ExPlan {
    val cfg = repo.config(id)
    val item = routine?.items?.firstOrNull { it.exerciseId == id }
    return if (item != null) {
        ExPlan(repo.itemSets(item).coerceAtLeast(1), repo.itemRepsLabel(item), repo.itemReps(item).first.coerceAtLeast(1), cfg.restSec)
    } else {
        ExPlan(cfg.sets.coerceAtLeast(1), cfg.repsLabel, cfg.repsMin.coerceAtLeast(1), cfg.restSec)
    }
}

private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

private fun bestSetOf(sets: List<SetLog>, formula: String): SetLog? = sets.maxByOrNull { Stats.score(it.weightKg, it.reps, formula) }

// ───────────────────────── Reloj

/** Hora actual que se refresca cada [periodMs]. Léela solo en composables pequeños para no redibujar toda la pantalla. */
@Composable
fun rememberNow(periodMs: Long = 250L): State<Long> {
    val repo = LocalRepo.current
    return produceState(repo.now(), periodMs) {
        while (true) {
            value = repo.now()
            delay(periodMs)
        }
    }
}

@Composable
private fun ElapsedTime(s: WorkoutSession, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val now = rememberNow(250L)
    val sec by remember(s) { derivedStateOf { ((now.value - s.startedAt) / 1000L).toInt().coerceAtLeast(0) } }
    BigNumber(Fmt.duration(sec), modifier, style, color)
}

// ───────────────────────── Pantalla

@Composable
fun WorkoutScreen(r: Route.Workout) {
    val app = LocalApp.current
    val cur = app.workout
    val live = if (cur != null && cur.routineId == r.routineId) cur else null
    // Mientras la pantalla se cierra (animación de salida) la sesión ya no existe: se sigue mostrando la última
    // para que no parpadee, pero NUNCA se crea otra (así se cancelaba y reaparecía la barra de "en curso").
    val lastShown = remember { arrayOfNulls<WorkoutSession>(1) }
    if (live != null) lastShown[0] = live
    val s = live ?: lastShown[0]
    val onStack = r in app.nav.stack
    LaunchedEffect(r.routineId, live == null, onStack) { if (live == null && onStack) app.ensureWorkout(r.routineId, r.exercises) }
    if (s == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val view = LocalView.current
    val keep = Fs.prefs.bool("train.keep_awake")
    val done = s.done
    DisposableEffect(keep, done) {
        view.keepScreenOn = keep && !done
        onDispose { view.keepScreenOn = false }
    }
    if (done) {
        BackHandler(enabled = app.workout === s) { app.closeWorkout() }
        SummaryScreen(s)
    } else {
        ActiveWorkout(s)
    }
}

// ───────────────────────── Terminar o cancelar (pantalla, barra y FITBOT usan lo mismo)

private fun pendingExercises(app: AppState, s: WorkoutSession): Int {
    val repo = app.repo
    val routine = s.routineId?.let { repo.routine(it) }
    val ids = s.logIds.toSet()
    val byEx = repo.logs.filter { it.id in ids }.groupBy { it.exerciseId }
    return s.exercises.count { id -> id !in s.skipped && (byEx[id]?.size ?: 0) < planOf(repo, routine, id).sets }
}

/** Finaliza el entrenamiento: guarda lo hecho y muestra el resumen (pide confirmar si faltan ejercicios). */
fun AppState.requestFinishWorkout() {
    val s = workout ?: return
    if (s.done) return
    if (sessionSets(s) == 0) {
        requestCancelWorkout()
        return
    }
    val go = {
        completeWorkout(s)
        openWorkout()
    }
    val missing = pendingExercises(this, s)
    if (missing > 0) {
        overlays.confirm(
            "¿Terminar ahora?",
            "Todavía te ${if (missing == 1) "falta un ejercicio" else "faltan $missing ejercicios"} por completar. Lo que ya guardaste se conserva.",
            "Terminar", onConfirm = go,
        )
    } else go()
}

/** Cancela el entrenamiento (borra las series de esta sesión) después de confirmar. */
fun AppState.requestCancelWorkout() {
    val s = workout ?: return
    if (s.done) {
        closeWorkout()
        return
    }
    val n = sessionSets(s)
    overlays.confirm(
        "¿Cancelar este entrenamiento?",
        if (n == 0) "Todavía no registraste ninguna serie, así que no se pierde nada."
        else "Se ${if (n == 1) "borra la serie que registraste" else "borran las $n series que registraste"} en esta sesión. Podrás deshacerlo justo después.",
        "Sí, cancelar", danger = true, cancelLabel = "Seguir",
    ) { cancelWorkout(true) }
}

/** Hoja con las tres salidas: finalizar, cancelar o seguir. */
fun AppState.showEndWorkoutSheet() {
    val s = workout ?: return
    if (s.done) return
    val n = sessionSets(s)
    overlays.sheet { close ->
        SheetBody("Entrenamiento en curso", subtitle = if (n == 0) "Todavía no registras ninguna serie" else "${Fmt.plural(n, "serie registrada", "series registradas")} hasta ahora") {
            Column(Modifier.clip(Fs.shapes.inner).background(Fs.colors.surface2)) {
                ListRow(
                    "Finalizar entrenamiento", desc = if (n == 0) "No hay nada que guardar" else "Guarda lo que hiciste y muestra el resumen",
                    icon = Ic.Check, onClick = { close(); requestFinishWorkout() },
                )
                Divider()
                ListRow(
                    "Cancelar entrenamiento", desc = if (n == 0) "Se descarta, no se pierde nada" else "Descarta la sesión y borra sus series",
                    icon = Ic.Trash, iconTint = Fs.colors.bad, iconBg = Fs.colors.bad.a(0.14f),
                    onClick = { close(); requestCancelWorkout() },
                )
                Divider()
                ListRow("Seguir entrenando", icon = Ic.Play, onClick = { close() })
            }
        }
    }
}

/** Al salir de un entrenamiento sin series registradas: ¿minimizar o cancelar? (suele ser un toque por accidente). */
private fun AppState.showLeaveWorkoutSheet() {
    overlays.sheet { close ->
        SheetBody("¿Salir del entrenamiento?", subtitle = "Todavía no registraste ninguna serie") {
            Column(Modifier.clip(Fs.shapes.inner).background(Fs.colors.surface2)) {
                ListRow(
                    "Cancelar entrenamiento", desc = "Se descarta, no se pierde nada", icon = Ic.Trash,
                    iconTint = Fs.colors.bad, iconBg = Fs.colors.bad.a(0.14f),
                    onClick = { close(); cancelWorkout(false) },
                )
                Divider()
                ListRow(
                    "Seguir en segundo plano", desc = "Queda la barra «Entrenamiento en curso» para volver", icon = Ic.ArrowDown,
                    onClick = { close(); nav.back() },
                )
                Divider()
                ListRow("Quedarme aquí", icon = Ic.Play, onClick = { close() })
            }
        }
    }
}

@Composable
private fun ActiveWorkout(s: WorkoutSession) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val mo = Fs.motion
    val routine = s.routineId?.let { repo.routine(it) }
    val title = routine?.name ?: "Entrenamiento libre"

    val ids = s.exercises.toList()
    val logIdSet = s.logIds.toSet()
    val logs = repo.logs
    val configs = repo.configs
    val sessionSets = remember(logs, logIdSet) { logs.filter { it.id in logIdSet }.sortedBy { it.ts } }
    val byEx = remember(sessionSets) { sessionSets.groupBy { it.exerciseId } }
    val plans = remember(ids, routine, configs) { ids.associateWith { planOf(repo, routine, it) } }
    val idx = if (ids.isEmpty()) 0 else s.index.coerceIn(0, ids.lastIndex)
    val ex = ids.getOrNull(idx)?.let { repo.exById[it] }
    val plan = ex?.let { plans[it.id] }
    val doneCur = ex?.let { byEx[it.id] }.orEmpty()
    val totalPlanned = ids.sumOf { plans[it]?.sets ?: 0 }
    val totalDone = ids.sumOf { minOf(byEx[it]?.size ?: 0, plans[it]?.sets ?: 0) }

    fun pending(id: String): Boolean = id !in s.skipped && (byEx[id]?.size ?: 0) < (plans[id]?.sets ?: 1)

    /** Siguiente ejercicio con series por hacer (da la vuelta a la lista). */
    fun nextPending(from: Int): Int? {
        val n = ids.size
        for (k in 1 until n) {
            val j = (from + k) % n
            if (pending(ids[j])) return j
        }
        return null
    }

    val nextIdx = nextPending(idx)
    val nextName = nextIdx?.let { repo.exById[ids[it]]?.name }
    val curDone = plan != null && ex != null && doneCur.size >= plan.sets && ex.id !in s.more
    val restHint = when {
        ex == null || plan == null -> ""
        !curDone -> "Serie ${doneCur.size + 1} de ${maxOf(plan.sets, doneCur.size + 1)} · ${ex.name}"
        nextName != null -> "Sigue: $nextName"
        else -> "¡Ya terminaste todo lo planeado!"
    }
    val showRest = s.resting || s.restOverAt > 0L

    fun back() {
        if (sessionSets.isEmpty()) app.showLeaveWorkoutSheet() else nav.back()
    }

    fun add() {
        app.overlays.pickExercises("Agregar al entrenamiento", exclude = ids.toSet(), actionLabel = "Agregar") { picked ->
            val fresh = picked.filter { it !in s.exercises }
            s.exercises.addAll(fresh)
            if (fresh.isNotEmpty() && ids.isEmpty()) s.index = 0
        }
    }

    fun skip(id: String) {
        if (id !in s.skipped) s.skipped.add(id)
        nextPending(idx)?.let { s.index = it }
        app.overlays.toast("Ejercicio saltado", "Deshacer", Ic.Undo) { s.skipped.remove(id) }
    }

    fun remove(id: String) {
        val pos = s.exercises.indexOf(id)
        if (pos < 0) return
        s.exercises.removeAt(pos)
        s.skipped.remove(id)
        s.more.remove(id)
        if (s.index >= s.exercises.size) s.index = (s.exercises.size - 1).coerceAtLeast(0) else if (pos < s.index) s.index--
    }

    fun toEnd(id: String) {
        if (s.exercises.remove(id)) s.exercises.add(id)
    }

    fun menu(e: Exercise) {
        app.overlays.sheet { close ->
            SheetBody(e.name, subtitle = "Opciones del ejercicio") {
                Column(Modifier.clip(Fs.shapes.inner).background(Fs.colors.surface2)) {
                    ListRow("Ver ejercicio y mis marcas", icon = Ic.Dumbbell, onClick = { close(); nav.push(Route.Exercise(e.id)) })
                    Divider()
                    ListRow("Saltar por ahora", icon = Ic.SkipForward, onClick = { close(); skip(e.id) })
                    Divider()
                    ListRow("Mandar al final", icon = Ic.ArrowDown, onClick = { close(); toEnd(e.id) })
                    Divider()
                    ListRow("Quitar de este entrenamiento", icon = Ic.Trash, iconTint = Fs.colors.bad, iconBg = Fs.colors.bad.a(0.14f), onClick = { close(); remove(e.id) })
                }
            }
        }
    }

    // Un entrenamiento libre vacío arranca pidiendo los ejercicios.
    LaunchedEffect(s) {
        if (ids.isEmpty() && !s.autoPicked) {
            s.autoPicked = true
            app.overlays.pickExercises("¿Qué vas a entrenar?", actionLabel = "Empezar") { picked ->
                s.exercises.addAll(picked.filter { it !in s.exercises })
                s.index = 0
            }
        }
    }

    BackHandler(enabled = app.workout === s && !app.overlays.hasOverlay) { back() }

    ScreenList(
        title = title,
        subtitle = if (ids.isEmpty()) "Elige tus ejercicios" else "Ejercicio ${idx + 1} de ${ids.size}",
        onBack = { back() },
        actions = {
            RoundButton(Ic.Close, { app.showEndWorkoutSheet() }, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            Pill("Finalizar", selected = true, icon = Ic.Check, height = 40.dp, onClick = { app.requestFinishWorkout() })
        },
    ) {
        item("session") { SessionCard(s, title, totalDone, totalPlanned, Modifier.enter(0)) }
        if (showRest) item("rest") { RestCard(s, restHint, Modifier.animateItem()) }
        if (ex == null || plan == null) {
            item("empty") {
                EmptyState(
                    "Aún no hay ejercicios", "Agrega los ejercicios que vas a hacer hoy y empieza a registrar tus series.",
                    Modifier.enter(1), mood = BotMood.Happy, actionLabel = "Elegir ejercicios", onAction = { add() },
                )
            }
        } else {
            item("current") {
                AnimatedContent(
                    targetState = ex.id,
                    modifier = Modifier.enter(1).fillMaxWidth(),
                    transitionSpec = {
                        val dir = if (ids.indexOf(targetState) >= ids.indexOf(initialState)) 1 else -1
                        (fadeIn(mo.tween(320, 60)) + slideInHorizontally(mo.tween(380)) { dir * it / 5 }) togetherWith
                            (fadeOut(mo.tween(180)) + slideOutHorizontally(mo.tween(300)) { -dir * it / 5 })
                    },
                    label = "currentExercise",
                ) { exerciseId ->
                    val e = repo.exById[exerciseId]
                    val p = plans[exerciseId]
                    if (e != null && p != null) {
                        CurrentExerciseCard(
                            s, e, p, byEx[exerciseId].orEmpty(), nextName, nextIdx == null,
                            onMenu = { menu(e) },
                            onNext = { nextIdx?.let { s.index = it } },
                            onFinish = { app.requestFinishWorkout() },
                            onUndoLast = { byEx[exerciseId]?.lastOrNull()?.let { deleteSessionSet(app, s, it) } },
                            onSkip = { skip(exerciseId) },
                        )
                    }
                }
            }
            if (doneCur.isNotEmpty()) item("logged") { LoggedSetsCard(s, ex, doneCur, Modifier.animateItem()) }
        }
        if (ids.isNotEmpty()) item("queue") { QueueRow(s, ids, idx, byEx, plans, Modifier.enter(2)) { add() } }
        item("footer") {
            Column(Modifier.enter(3).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                FsButton("Cancelar entrenamiento", { app.requestCancelWorkout() }, kind = BtnKind.Ghost, icon = Ic.Trash, height = 44.dp)
            }
        }
    }
}

private fun deleteSessionSet(app: AppState, s: WorkoutSession, l: SetLog) {
    val repo = app.repo
    repo.deleteLog(l.id)
    s.logIds.remove(l.id)
    app.overlays.toast("Serie eliminada", "Deshacer", Ic.Undo) {
        repo.undo()
        if (repo.logs.any { it.id == l.id } && l.id !in s.logIds) s.logIds.add(l.id)
    }
}

// ───────────────────────── Tarjeta del tiempo total

@Composable
private fun SessionCard(s: WorkoutSession, title: String, done: Int, planned: Int, modifier: Modifier) {
    val c = Fs.colors
    val progress = if (planned <= 0) 0f else (done.toFloat() / planned).coerceIn(0f, 1f)
    Card(modifier, accent = true) {
        LedBanner(title.uppercase().take(14) + " > EN CURSO", lit = c.onAccent.a(0.72f))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            ElapsedTime(s, Fs.type.display, c.onAccent)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Txt("$done / $planned", style = Fs.type.h2, color = c.onAccent, maxLines = 1)
                Txt("series", style = Fs.type.caption, color = c.onAccentDim, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChunkyProgress(progress, height = 20.dp, track = c.onAccent.a(0.18f), fill = SolidColor(c.onAccent))
    }
}

// ───────────────────────── Descanso

@Composable
private fun RestCard(s: WorkoutSession, hint: String, modifier: Modifier) {
    val c = Fs.colors
    val repo = LocalRepo.current
    val resting = s.resting
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RestRing(s)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(if (resting) "DESCANSO" else "¡LISTO!", color = if (resting) c.textDim else c.accentText)
                Txt(if (resting) "Respira y toma agua" else "A la siguiente serie", style = Fs.type.h3, maxLines = 2)
                if (hint.isNotBlank()) Txt(hint, style = Fs.type.small, color = c.textDim, maxLines = 2)
            }
            if (!resting) FitBot(size = 50.dp, mood = BotMood.Happy)
        }
        Spacer(Modifier.height(12.dp))
        if (resting) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("−15 s", soft = true, onClick = { s.addRest(-15, repo.now()) })
                Pill("+15 s", soft = true, onClick = { s.addRest(15, repo.now()) })
                Spacer(Modifier.weight(1f))
                FsButton("Saltar", { s.skipRest() }, kind = BtnKind.Secondary, height = 40.dp, icon = Ic.SkipForward)
            }
        } else {
            FsButton("Entendido", { s.restOverAt = 0L }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, height = 44.dp)
        }
    }
}

@Composable
private fun RestRing(s: WorkoutSession) {
    val c = Fs.colors
    val now by rememberNow(100L)
    if (s.resting) {
        val left = (s.restEndsAt - now).coerceAtLeast(0L)
        val total = s.restTotalMs.coerceAtLeast(1L)
        Ring((left.toFloat() / total).coerceIn(0f, 1f), size = 112.dp, stroke = 11.dp, animate = false) {
            BigNumber(Fmt.duration(((left + 999L) / 1000L).toInt()), style = Fs.type.h2)
        }
    } else {
        Ring(1f, size = 112.dp, stroke = 11.dp, color = c.good) {
            Icon(Ic.Check, size = 40.dp, tint = c.good, style = "line", weight = 3f)
        }
    }
}

// ───────────────────────── Ejercicio actual

@Composable
private fun CurrentExerciseCard(
    s: WorkoutSession,
    ex: Exercise,
    plan: ExPlan,
    done: List<SetLog>,
    nextName: String?,
    isLastPending: Boolean,
    onMenu: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onUndoLast: () -> Unit,
    onSkip: () -> Unit,
) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val haptics = Fs.haptics
    val units = repo.units
    val prefs = repo.prefs
    val cardio = ex.isCardio
    val logs = repo.logs
    val prev = remember(logs, ex.id, s.startedAt) { logs.asSequence().filter { it.exerciseId == ex.id && it.ts < s.startedAt }.maxByOrNull { it.ts } }
    val cfg = repo.config(ex.id)
    val last = done.lastOrNull() ?: prev
    val w = s.draftW[ex.id] ?: round2(Fmt.toDisplay(last?.weightKg ?: cfg.targetWeightKg, units))
    val reps = s.draftR[ex.id] ?: (last?.reps ?: plan.repsLo).coerceAtLeast(1)
    val n = done.size
    val finished = n >= plan.sets && ex.id !in s.more
    val stepW = prefs.str("stats.step").toDoubleOrNull() ?: 2.5

    fun log() {
        val kg = if (cardio) 0.0 else round2(Fmt.fromDisplay(w, units))
        val r = reps.coerceAtLeast(1)
        val res = repo.logSet(ex.id, kg, r, s.routineId)
        s.logIds.add(res.log.id)
        if (res.record != null) s.records.add(ex.name + ": " + describeSet(kg, r, ex, units))
        haptics.confirm()
        s.restOverAt = 0L
        val allDone = isLastPending && ex.id !in s.more && n + 1 >= plan.sets
        if (prefs.bool("train.autorest") && !allDone) s.startRest(plan.restSec, repo.now())
    }

    Card(padding = PaddingValues(Fs.ui.pad(14))) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.7f).clip(Fs.shapes.inner).background(c.surface2)
                .tappable({ app.nav.push(Route.Exercise(ex.id)) }, pressScale = 0.99f),
        ) {
            ExerciseAnimated(ex, Modifier.fillMaxSize(), reqWidth = 800)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(0.34f), 0.3f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(0.76f))))
            Txt(
                if (finished) "Completo" else if (cardio) "Bloque ${n + 1}" else "Serie ${n + 1} de ${maxOf(plan.sets, n + 1)}",
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(Fs.shapes.pill).background(if (finished) c.good else c.accent).padding(horizontal = 12.dp, vertical = 5.dp),
                Fs.type.smallStrong, if (finished) Color.White else c.onAccent, maxLines = 1,
            )
            RoundButton(Ic.MoreV, onMenu, Modifier.align(Alignment.TopEnd).padding(8.dp), size = 38.dp, tint = Color.White, bg = Color.Black.copy(0.45f), border = false)
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(ex.name, style = Fs.type.h2, color = Color.White, maxLines = 2)
                Txt(ex.musclesText().ifBlank { ex.categoryName() }, style = Fs.type.small, color = Color.White.copy(0.82f), maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        SegmentedProgress(minOf(n, plan.sets), plan.sets, height = 10.dp)
        Spacer(Modifier.height(6.dp))
        Txt(
            if (cardio) "Objetivo: ${plan.sets} × ${plan.repsLabel} min" else "Objetivo: ${plan.sets} × ${plan.repsLabel} reps · descanso ${plan.restSec} s",
            style = Fs.type.caption, color = c.textDim, maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))

        if (finished) {
            FinishedPanel(ex, done, nextName, isLastPending, onNext, onFinish) { s.more.add(ex.id) }
        } else {
            if (prefs.bool("train.show_prev") && prev != null) {
                Row(
                    Modifier.fillMaxWidth().clip(Fs.shapes.chip).background(c.surface2).tappable({
                        s.draftW[ex.id] = round2(Fmt.toDisplay(prev.weightKg, units)); s.draftR[ex.id] = prev.reps
                    }).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Ic.RotateCcw, size = 18.dp, tint = c.accentText)
                    Column(Modifier.weight(1f)) {
                        Txt("Última vez: " + describeSet(prev.weightKg, prev.reps, ex, units), style = Fs.type.smallStrong, maxLines = 1)
                        Txt(Fmt.relativeDay(Fmt.date(prev.ts, repo.zone), repo.today()), style = Fs.type.caption, color = c.textDim, maxLines = 1)
                    }
                    Txt("Usar", style = Fs.type.smallStrong, color = c.accentText)
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                if (!cardio) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label(if (ex.isBodyweight) "Peso extra" else "Peso")
                        NumberStepper(
                            w, { s.draftW[ex.id] = it }, step = stepW, decimals = 2, max = 2000.0, unit = Fmt.unit(units),
                            zeroLabel = if (ex.isBodyweight) "Corporal" else null, stacked = true,
                        )
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(if (cardio) "Minutos" else "Repeticiones")
                    NumberStepper(
                        reps.toDouble(), { s.draftR[ex.id] = it.toInt() }, step = if (cardio) 5.0 else 1.0, min = 1.0, max = 999.0,
                        decimals = 0, unit = if (cardio) "min" else "reps", stacked = true,
                    )
                }
            }
            if (!cardio && w > 0.0) {
                val e = Stats.e1rm(Fmt.fromDisplay(w, units), reps, repo.formula)
                if (e > 0) Txt("1RM estimado ≈ " + Fmt.weight(e, units), Modifier.fillMaxWidth().padding(top = 8.dp), Fs.type.caption, c.textDim, align = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            FsButton(
                if (cardio) "Registrar cardio" else "Completar serie ${n + 1}", { log() }, Modifier.fillMaxWidth(),
                height = 58.dp, icon = Ic.Check,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (n > 0) FsButton("Deshacer", onUndoLast, Modifier.weight(1f), kind = BtnKind.Ghost, icon = Ic.Undo, height = 42.dp)
                FsButton("Saltar", onSkip, Modifier.weight(1f), kind = BtnKind.Ghost, icon = Ic.SkipForward, height = 42.dp)
            }
        }
    }
}

@Composable
private fun FinishedPanel(
    ex: Exercise,
    done: List<SetLog>,
    nextName: String?,
    isLast: Boolean,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onMore: () -> Unit,
) {
    val repo = LocalRepo.current
    val c = Fs.colors
    val best = bestSetOf(done, repo.formula)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(54.dp).clip(Fs.shapes.round).background(c.good.a(0.18f)), contentAlignment = Alignment.Center) {
            Icon(Ic.CheckCheck, size = 28.dp, tint = c.good, style = "line", weight = 3f)
        }
        Txt("¡Ejercicio completo!", style = Fs.type.h2, align = TextAlign.Center)
        if (best != null) {
            Txt(
                "${done.size} ${if (done.size == 1) "serie" else "series"} · mejor: ${describeSet(best.weightKg, best.reps, ex, repo.units)}",
                style = Fs.type.small, color = c.textDim, align = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!isLast && nextName != null) {
            FsButton("Siguiente: ${nextName.take(26)}", onNext, Modifier.fillMaxWidth(), height = 54.dp, trailingIcon = Ic.ArrowRight)
        } else {
            FsButton("Terminar entrenamiento", onFinish, Modifier.fillMaxWidth(), height = 54.dp, icon = Ic.Trophy)
        }
        FsButton("Hacer una serie más", onMore, Modifier.fillMaxWidth(), kind = BtnKind.Ghost, icon = Ic.Plus, height = 42.dp)
    }
}

// ───────────────────────── Series de hoy

@Composable
private fun LoggedSetsCard(s: WorkoutSession, ex: Exercise, sets: List<SetLog>, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val units = repo.units
    Card(modifier) {
        SectionTitle("Series de hoy", trailing = { Txt(ex.name, Modifier.widthIn(max = 180.dp), Fs.type.caption, c.textDim, maxLines = 1) })
        Spacer(Modifier.height(6.dp))
        for ((i, l) in sets.withIndex()) {
            Row(
                Modifier.fillMaxWidth().tappable({ app.overlays.logSet(ex, s.routineId, editing = l) }, pressScale = 0.985f).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CountBadge(i + 1, size = 30.dp)
                Column(Modifier.weight(1f)) {
                    Txt(describeSet(l.weightKg, l.reps, ex, units), style = Fs.type.bodyStrong, maxLines = 1)
                    Txt(Fmt.hourMinute(l.ts, repo.zone), style = Fs.type.caption, color = c.textDim, maxLines = 1)
                }
                RoundButton(Ic.Trash, { deleteSessionSet(app, s, l) }, size = 36.dp, tint = c.bad, bg = c.bad.a(0.12f), border = false)
            }
        }
    }
}

// ───────────────────────── Lista de ejercicios de la sesión

@Composable
private fun QueueRow(
    s: WorkoutSession,
    ids: List<String>,
    idx: Int,
    byEx: Map<String, List<SetLog>>,
    plans: Map<String, ExPlan>,
    modifier: Modifier,
    onAdd: () -> Unit,
) {
    val repo = LocalRepo.current
    val c = Fs.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Ejercicios de hoy", action = "Agregar", onAction = onAdd)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
            itemsIndexed(ids, key = { _, id -> id }) { i, id ->
                val e = repo.exById[id]
                if (e != null) {
                    val n = byEx[id]?.size ?: 0
                    val p = plans[id]?.sets ?: 1
                    val current = i == idx
                    val complete = n >= p
                    val skipped = id in s.skipped && !complete
                    Column(
                        Modifier.width(76.dp).tappable({ s.index = i }, pressScale = 0.95f),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(68.dp)) {
                            ExerciseThumb(e, 68.dp, Modifier.graphicsLayer { alpha = if (skipped) 0.4f else 1f })
                            if (current) Box(Modifier.matchParentSize().border(2.5.dp, c.accent, Fs.shapes.chip))
                            if (complete) {
                                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(22.dp).clip(Fs.shapes.round).background(c.accent), contentAlignment = Alignment.Center) {
                                    Icon(Ic.Check, size = 14.dp, tint = c.onAccent, style = "line", weight = 3f)
                                }
                            } else if (n > 0) {
                                Txt(
                                    "$n/$p", Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(Fs.shapes.pill).background(Color.Black.copy(0.62f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    Fs.type.caption, Color.White, maxLines = 1,
                                )
                            } else if (skipped) {
                                Icon(Ic.SkipForward, Modifier.align(Alignment.Center), size = 22.dp, tint = c.text)
                            }
                        }
                        Txt(e.name, style = Fs.type.caption, color = if (current) c.text else c.textDim, maxLines = 2, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Resumen

@Composable
private fun SummaryScreen(s: WorkoutSession) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val units = repo.units
    val routine = s.routineId?.let { repo.routine(it) }
    val title = routine?.name ?: "Entrenamiento libre"
    val logIdSet = s.logIds.toSet()
    val logs = repo.logs
    val exById = repo.exById
    val sets = remember(logs, logIdSet) { logs.filter { it.id in logIdSet }.sortedBy { it.ts } }
    val groups = remember(sets) { sets.groupBy { it.exerciseId } }
    val exs = remember(groups, exById) { groups.keys.mapNotNull { exById[it] } }
    val volume = remember(sets, exById) { sets.filter { exById[it.exerciseId]?.isCardio != true }.sumOf { it.volume } }
    val levels = remember(exs) {
        HashMap<Muscle, Int>().apply {
            for (e in exs) {
                e.primary.forEach { put(it, 2) }
                e.secondary.forEach { putIfAbsent(it, 1) }
            }
        }
    }
    val durationSec = ((s.finishedAt - s.startedAt) / 1000L).toInt().coerceAtLeast(0)
    var back by rememberSaveable { mutableStateOf(bestSide(levels)) }
    val pulse = rememberPulse()
    val today = rememberToday()
    val weekDays = remember(logs, today, repo.mondayFirst) { Stats.daysThisWeek(logs, repo.mondayFirst, today, repo.zone) }
    val goal = repo.weeklyGoal
    val line = when {
        s.records.isNotEmpty() -> "¡Rompiste ${Fmt.plural(s.records.size, "récord", "récords")}!"
        weekDays.size >= goal -> "¡Cumpliste tu meta de la semana!"
        else -> "Llevas ${weekDays.size} de $goal días esta semana."
    }

    ScreenList(title = "Resumen", subtitle = title, onBack = { app.closeWorkout() }) {
        item("hero") {
            Card(Modifier.enter(0), accent = true) {
                LedBanner(title.uppercase().take(14) + " > LISTO", lit = c.onAccent.a(0.72f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(92.dp).clip(Fs.shapes.round).background(Color.Black.copy(0.22f)), contentAlignment = Alignment.Center) {
                        FitBot(size = 80.dp, mood = BotMood.Love)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Txt("¡Entrenamiento completo!", style = Fs.type.h1, color = c.onAccent, maxLines = 2)
                        Txt(line, style = Fs.type.small, color = c.onAccentDim, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(Fmt.duration(durationSec), style = Fs.type.display, color = c.onAccent)
                    Txt("  duración", Modifier.padding(bottom = 7.dp), Fs.type.body, c.onAccentDim, maxLines = 1)
                }
            }
        }
        item("tiles") {
            Column(Modifier.enter(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Series", "${sets.size}", Modifier.weight(1f))
                    StatTile("Ejercicios", "${exs.size}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Volumen", if (volume > 0) Fmt.volume(volume, units) else "—", Modifier.weight(1f))
                    StatTile("Récords", "${s.records.size}", Modifier.weight(1f), accent = s.records.isNotEmpty())
                }
            }
        }
        if (s.records.isNotEmpty()) item("records") {
            Card(Modifier.enter(2)) {
                SectionTitle("Récords de hoy")
                Spacer(Modifier.height(6.dp))
                for (r in s.records) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Ic.Trophy, size = 20.dp, tint = c.warn)
                        Txt(r, Modifier.weight(1f), Fs.type.bodyStrong, maxLines = 2)
                    }
                }
            }
        }
        if (levels.isNotEmpty()) item("muscles") {
            Card(Modifier.enter(3)) {
                SectionTitle("Músculos trabajados")
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("Frente", "Espalda"), if (back) 1 else 0, { back = it == 1 }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(340.dp).clip(Fs.shapes.inner).background(c.surface2), contentAlignment = Alignment.Center) {
                    MuscleFigure(levels, Modifier.fillMaxSize(), back = back, pulse = pulse, padding = 0.05f)
                }
            }
        }
        if (exs.isNotEmpty()) item("recap") {
            Card(Modifier.enter(4)) {
                SectionTitle("Lo que hiciste")
                Spacer(Modifier.height(6.dp))
                for (e in exs) {
                    val g = groups[e.id].orEmpty()
                    val best = bestSetOf(g, repo.formula)
                    val isRecord = s.records.any { it.startsWith(e.name + ":") }
                    Row(
                        Modifier.fillMaxWidth().tappable({ nav.push(Route.Exercise(e.id)) }, pressScale = 0.985f).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExerciseThumb(e, 46.dp, shape = Fs.shapes.round)
                        Column(Modifier.weight(1f)) {
                            Txt(e.name, style = Fs.type.bodyStrong, maxLines = 1)
                            Txt(
                                "${Fmt.plural(g.size, "serie", "series")}" + (if (best != null) " · mejor: " + describeSet(best.weightKg, best.reps, e, units) else ""),
                                style = Fs.type.small, color = c.textDim, maxLines = 1,
                            )
                        }
                        if (isRecord) Icon(Ic.Star, size = 22.dp, tint = c.warn)
                    }
                }
            }
        }
        item("buttons") {
            Column(Modifier.enter(5), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FsButton("Listo", { app.closeWorkout() }, Modifier.fillMaxWidth(), icon = Ic.Check)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FsButton("Mi progreso", { app.closeWorkout(); nav.push(Route.Progress) }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.Stats, height = 46.dp)
                    FsButton("Anotar algo", { app.overlays.editNote() }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.NotebookPen, height = 46.dp)
                }
            }
        }
    }
}

// ───────────────────────── Barra de "entrenamiento en curso"

/** Barra que aparece sobre el menú inferior mientras hay un entrenamiento sin terminar. */
@Composable
fun WorkoutMiniBar(app: AppState, modifier: Modifier = Modifier) {
    val s = app.workout ?: return
    val c = Fs.colors
    val repo = app.repo
    val routine = s.routineId?.let { repo.routine(it) }
    val m = Fs.motion
    val dot = if (m.loops) {
        val tr = rememberInfiniteTransition(label = "miniDot")
        val v by tr.animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(m.ms(900)), RepeatMode.Reverse),
            label = "miniDotV",
        )
        v
    } else 1f
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp)
            .clip(Fs.shapes.round).background(c.accent)
            .tappable({ app.openWorkout() }, pressScale = 0.98f)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = dot }.clip(Fs.shapes.round).background(c.onAccent))
        Column(Modifier.weight(1f)) {
            Txt(if (s.resting) "Descansando…" else "Entrenamiento en curso", style = Fs.type.smallStrong, color = c.onAccent, maxLines = 1)
            Txt(routine?.name ?: "Entrenamiento libre", style = Fs.type.caption, color = c.onAccentDim, maxLines = 1)
        }
        ElapsedTime(s, Fs.type.h3, c.onAccent)
        Box(
            Modifier.size(34.dp).clip(Fs.shapes.round).background(c.onAccent.a(0.16f))
                .semantics { contentDescription = "Cancelar o finalizar el entrenamiento" }
                .tappable({ app.showEndWorkoutSheet() }, pressScale = 0.88f),
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.Close, size = 18.dp, tint = c.onAccent) }
    }
}
