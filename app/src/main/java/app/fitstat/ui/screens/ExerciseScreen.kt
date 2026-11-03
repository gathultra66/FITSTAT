package app.fitstat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.delay

/** Pantalla de un ejercicio: foto grande, mi set, plan, estadísticas, historial, notas y músculos. */
@Composable
fun ExerciseScreen(id: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val ex = repo.exById[id]
    if (ex == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val cfg = repo.config(id)
    val logs = repo.logs
    val formula = repo.formula
    val zone = repo.zone
    val stats = remember(logs, id, formula, zone) { Stats.forExercise(logs, id, formula, zone) }
    val levels = remember(ex) {
        HashMap<Muscle, Int>().apply {
            ex.primary.forEach { put(it, 2) }
            ex.secondary.forEach { putIfAbsent(it, 1) }
        }
    }
    val inRoutines = repo.routines.filter { r -> r.items.any { it.exerciseId == id } }
    val related = repo.notes.filter { it.exerciseId == id }.sortedByDescending { it.updatedAt }

    fun menu() = app.overlays.sheet { close ->
        SheetBody(ex.name, subtitle = "Opciones del ejercicio") {
            Column(Modifier.clip(Fs.shapes.inner).background(Fs.colors.surface2)) {
                ListRow("Registrar serie", icon = Ic.Plus, onClick = { close(); app.overlays.logSet(ex) })
                Divider()
                ListRow("Agregar a una rutina", icon = Ic.Routines, onClick = { close(); app.addToRoutineSheet(listOf(id)) })
                Divider()
                ListRow("Escribir una anotación", icon = Ic.NotebookPen, onClick = { close(); app.overlays.editNote(exerciseId = id) })
                Divider()
                ListRow(if (cfg.favorite) "Quitar de favoritos" else "Marcar como favorito", icon = Ic.Star, onClick = {
                    close(); repo.updateConfig(id) { it.copy(favorite = !it.favorite) }
                })
                if (repo.logsOf(id).isNotEmpty()) {
                    Divider()
                    ListRow("Borrar mi historial de este ejercicio", icon = Ic.Trash, iconTint = Fs.colors.bad, iconBg = Fs.colors.bad.a(0.14f), onClick = {
                        close()
                        app.overlays.confirm("¿Borrar historial?", "Se eliminarán ${Fmt.plural(repo.logsOf(id).size, "serie", "series")} de «${ex.name}». Podrás deshacerlo justo después.", "Borrar", danger = true) {
                            val ids = repo.logsOf(id).map { it.id }.toSet()
                            repo.update("Historial borrado") { d -> d.copy(logs = d.logs.filterNot { it.id in ids }) }
                            app.toastUndo("Historial borrado")
                        }
                    })
                }
                if (ex.custom) {
                    Divider()
                    ListRow("Editar ejercicio", icon = Ic.Edit, onClick = { close(); app.overlays.editExercise(existing = ex) })
                    Divider()
                    ListRow("Eliminar ejercicio", icon = Ic.Trash, iconTint = Fs.colors.bad, iconBg = Fs.colors.bad.a(0.14f), onClick = {
                        close()
                        app.overlays.confirm("¿Eliminar «${ex.name}»?", "Se quitará de tu set y de tus rutinas. Tus series registradas se conservan.", "Eliminar", danger = true) {
                            repo.deleteCustomExercise(id)
                            nav.popWhile { it is Route.Exercise && it.id == id || it is Route.Muscles && it.id == id }
                            app.toastUndo("Ejercicio eliminado")
                        }
                    })
                }
            }
        }
    }

    ScreenList(
        title = ex.name,
        subtitle = ex.categoryName() + if (ex.equipment.isNotBlank()) " · ${ex.equipment}" else "",
        onBack = { nav.back() },
        actions = {
            RoundButton(Ic.Star, { repo.updateConfig(id) { it.copy(favorite = !it.favorite) } }, tint = if (cfg.favorite) c.warn else c.textDim)
            RoundButton(Ic.MoreV, { menu() })
        },
    ) {
        item("hero") { ExerciseHero(ex, Modifier.enter(0)) { nav.push(Route.Muscles(id)) } }
        item("set") { SetCard(ex, cfg, Modifier.enter(1)) { app.toggleSet(ex) } }
        item("actions") {
            Row(Modifier.fillMaxWidth().enter(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FsButton("Registrar serie", { app.overlays.logSet(ex) }, Modifier.weight(1f), icon = Ic.Plus, height = 50.dp)
                FsButton("A una rutina", { app.addToRoutineSheet(listOf(id)) }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.Routines, height = 50.dp)
            }
        }
        item("plan") { PlanCard(ex, cfg, Modifier.enter(3)) }
        item("stats") { StatsCard(ex, stats, Modifier.enter(4)) }
        if (!stats.isEmpty) item("history") { HistoryCard(ex, stats, Modifier.enter(5)) }
        item("notes") { NotesCard(ex, cfg, related, Modifier.enter(6)) }
        item("muscles") { MusclesCard(ex, levels, Modifier.enter(7)) { nav.push(Route.Muscles(id)) } }
        item("info") { InfoCard(ex, Modifier.enter(8)) }
        item("routines") { RoutinesCard(ex, inRoutines, Modifier.enter(9)) }
    }
}

// ───────────────────────── Foto grande

@Composable
private fun ExerciseHero(ex: Exercise, modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    Box(
        modifier.fillMaxWidth().aspectRatio(1.25f).clip(Fs.shapes.card).background(c.surface2).tappable(onClick, pressScale = 0.99f),
    ) {
        ExerciseAnimated(ex, Modifier.fillMaxSize(), reqWidth = 900)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(0.30f), 0.28f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(0.72f))))
        Row(
            Modifier.align(Alignment.TopEnd).padding(12.dp).clip(Fs.shapes.pill).background(Color.Black.copy(0.48f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Ic.Body, size = 17.dp, tint = Color.White)
            Txt("Ver músculos", style = Fs.type.smallStrong, color = Color.White, maxLines = 1)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val head = ex.primary.joinToString(" · ") { it.label }.ifBlank { ex.categoryName() }
            Txt(head, style = Fs.type.h3, color = Color.White, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in listOf(ex.equipment.ifBlank { ex.categoryName() }, ex.level, ex.mechanic).filter { it.isNotBlank() && !head.contains(it, ignoreCase = true) }.take(3)) {
                    Txt(
                        t, Modifier.clip(Fs.shapes.pill).background(Color.White.copy(0.20f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        Fs.type.caption, Color.White, maxLines = 1,
                    )
                }
            }
        }
    }
}

// ───────────────────────── Mi set

@Composable
private fun SetCard(ex: Exercise, cfg: ExerciseConfig, modifier: Modifier, onToggle: () -> Unit) {
    val c = Fs.colors
    Crossfade(cfg.inSet, modifier, animationSpec = Fs.motion.tween(280), label = "setCard") { on ->
        if (on) {
            Card(accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SetBadge(size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Txt("En tu set", style = Fs.type.h2, color = c.onAccent)
                        Txt(
                            "${cfg.sets} × ${cfg.repsLabel}${if (ex.isCardio) " min" else ""}" + if (cfg.days.isEmpty()) " · sin días fijos" else " · " + Weekdays.label(cfg.days),
                            style = Fs.type.small, color = c.onAccentDim, maxLines = 2,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(Fs.shapes.pill).background(c.onAccent.a(0.16f)).tappable(onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Ic.Minus, size = 18.dp, tint = c.onAccent, style = "line", weight = 2.6f)
                    Txt("Quitar de mi set", style = Fs.type.smallStrong, color = c.onAccent)
                }
            }
        } else {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Ic.ListPlus, size = 46.dp)
                    Column(Modifier.weight(1f)) {
                        Txt("Mi set de ejercicios", style = Fs.type.h3)
                        Txt("Agrégalo si lo haces seguido y tenlo siempre a la mano.", style = Fs.type.small, color = c.textDim)
                    }
                }
                Spacer(Modifier.height(12.dp))
                FsButton("Agregar a mi set", onToggle, Modifier.fillMaxWidth(), icon = Ic.Plus)
            }
        }
    }
}

// ───────────────────────── Plan

@Composable
private fun PlanRow(title: String, desc: String? = null, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Txt(title, style = Fs.type.bodyStrong, maxLines = 1)
            if (desc != null) Txt(desc, style = Fs.type.caption, color = Fs.colors.textDim, maxLines = 2)
        }
        content()
    }
}

@Composable
private fun PlanCard(ex: Exercise, cfg: ExerciseConfig, modifier: Modifier) {
    val repo = LocalRepo.current
    val c = Fs.colors
    val units = repo.units
    val cardio = ex.isCardio
    val stepW = remember { repo.prefs.str("stats.step").toDoubleOrNull() ?: 2.5 }
    fun upd(f: (ExerciseConfig) -> ExerciseConfig) = repo.updateConfig(ex.id, null, f)
    val range = cfg.repsMax > cfg.repsMin

    Card(modifier) {
        SectionTitle("Mi plan", trailing = {
            Txt("${cfg.sets} × ${cfg.repsLabel}${if (cardio) " min" else ""}", style = Fs.type.smallStrong, color = c.accentText)
        })
        Spacer(Modifier.height(6.dp))
        if (!cardio) {
            PlanRow("Series", "Cuántas haces") {
                Stepper(cfg.sets.toString(), { upd { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } }, { upd { it.copy(sets = (it.sets + 1).coerceAtMost(12)) } })
            }
        }
        PlanRow(if (cardio) "Minutos" else if (range) "Repeticiones (desde)" else "Repeticiones", if (cardio) "Duración habitual" else "Por serie") {
            val stepR = if (cardio) 5 else 1
            Stepper(
                cfg.repsMin.toString(),
                { upd { val nm = (it.repsMin - stepR).coerceAtLeast(1); it.copy(repsMin = nm, repsMax = if (it.repsMax > 0) maxOf(it.repsMax, nm + 1) else 0) } },
                { upd { val nm = (it.repsMin + stepR).coerceAtMost(if (cardio) 300 else 100); it.copy(repsMin = nm, repsMax = if (it.repsMax > 0) maxOf(it.repsMax, nm + 1) else 0) } },
            )
        }
        if (!cardio) {
            PlanRow("Usar rango", "Por ejemplo 8–12 repeticiones") {
                Toggle(range, { on -> upd { it.copy(repsMax = if (on) it.repsMin + 4 else 0) } })
            }
            AnimatedVisibility(range, enter = expandVertically(Fs.motion.tween(240)) + fadeIn(Fs.motion.tween(240)), exit = shrinkVertically(Fs.motion.tween(200)) + fadeOut(Fs.motion.tween(160))) {
                PlanRow("Hasta", "Repeticiones máximas") {
                    Stepper(
                        cfg.repsMax.toString(),
                        { upd { it.copy(repsMax = (it.repsMax - 1).coerceAtLeast(it.repsMin + 1)) } },
                        { upd { it.copy(repsMax = (it.repsMax + 1).coerceAtMost(100)) } },
                    )
                }
            }
            PlanRow("Descanso", "Entre series") {
                Stepper(cfg.restSec.toString(), { upd { it.copy(restSec = (it.restSec - 15).coerceAtLeast(15)) } }, { upd { it.copy(restSec = (it.restSec + 15).coerceAtMost(600)) } }, unit = "s")
            }
            PlanRow("Peso objetivo", if (ex.isBodyweight) "Peso extra (0 = solo tu cuerpo)" else "Con el que quieres trabajar") {
                Stepper(
                    Fmt.num(Fmt.toDisplay(cfg.targetWeightKg, units), 1),
                    { upd { it.copy(targetWeightKg = Fmt.fromDisplay((Fmt.toDisplay(it.targetWeightKg, units) - stepW).coerceAtLeast(0.0), units)) } },
                    { upd { it.copy(targetWeightKg = Fmt.fromDisplay((Fmt.toDisplay(it.targetWeightKg, units) + stepW).coerceAtMost(1000.0), units)) } },
                    unit = Fmt.unit(units),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HairLine()
        Spacer(Modifier.height(12.dp))
        Txt("Días de la semana que lo entrenas", style = Fs.type.bodyStrong)
        Spacer(Modifier.height(8.dp))
        WeekdayPicker(cfg.days, { d -> upd { it.copy(days = if (d in it.days) it.days - d else it.days + d) } }, weekStartsMonday = repo.mondayFirst)
        Txt(
            if (cfg.days.isEmpty()) "Elige los días y te lo mostraré en «Hoy» cuando toque." else "Aparecerá en Inicio los días: " + Weekdays.label(cfg.days) + ".",
            Modifier.padding(top = 8.dp), Fs.type.caption, c.textDim,
        )
    }
}

// ───────────────────────── Estadísticas

private val ranges = listOf("4w" to "4 sem", "8w" to "8 sem", "12w" to "12 sem", "6m" to "6 m", "all" to "Todo")

@Composable
private fun StatsCard(ex: Exercise, stats: Stats.ExStats, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val units = repo.units
    val today = remember { repo.today() }
    val cardio = ex.isCardio
    val weighted = stats.bestWeight > 0.0
    val metrics = remember(cardio, weighted) {
        when {
            cardio -> listOf("reps" to "Minutos")
            weighted -> listOf("volume" to "Volumen", "weight" to "Peso máx.", "e1rm" to "1RM est.", "reps" to "Reps")
            else -> listOf("reps" to "Reps")
        }
    }
    val prefMetric = repo.prefs.str("stats.metric")
    var metric by rememberSaveable(ex.id) { mutableStateOf(if (metrics.any { it.first == prefMetric }) prefMetric else metrics.first().first) }
    if (metrics.none { it.first == metric }) metric = metrics.first().first
    var range by rememberSaveable { mutableStateOf(repo.prefs.str("stats.range")) }
    val style = Fs.ui.chartStyle

    Card(modifier) {
        SectionTitle("Mis estadísticas", trailing = {
            if (!stats.isEmpty) Txt(Fmt.plural(stats.sessions.size, "sesión", "sesiones"), style = Fs.type.caption, color = c.textDim)
        })
        if (stats.isEmpty) {
            EmptyState(
                "Aún sin registros", "Registra tu primera serie y aquí verás tu progreso: gráficas, récords y volumen.",
                mood = BotMood.Idle, actionLabel = "Registrar serie", onAction = { app.overlays.logSet(ex) }, botSize = 84.dp,
            )
            return@Card
        }
        Spacer(Modifier.height(10.dp))
        val bestRepsSet = remember(stats) { stats.sessions.flatMap { it.sets }.maxByOrNull { it.reps } }
        val tiles = buildList {
            when {
                cardio -> {
                    add(Triple("Mejor sesión", "${stats.sessions.maxOfOrNull { it.reps } ?: 0}", "min"))
                    add(Triple("Tiempo total", "${stats.sessions.sumOf { it.reps }}", "min"))
                }
                weighted -> {
                    add(Triple("Mejor serie", Fmt.weightNum(stats.bestSet?.weightKg ?: stats.bestWeight, units), Fmt.unit(units) + (stats.bestSet?.let { " × ${it.reps}" } ?: "")))
                    add(Triple("1RM estimado", Fmt.weightNum(stats.bestE1rm, units), Fmt.unit(units)))
                    add(Triple("Volumen total", Fmt.compactVolume(stats.totalVolume, units), Fmt.unit(units)))
                }
                else -> {
                    add(Triple("Mejor serie", "${bestRepsSet?.reps ?: 0}", "reps"))
                    add(Triple("Reps totales", Fmt.group(stats.sessions.sumOf { it.reps }.toLong()), ""))
                }
            }
            add(Triple("Series totales", Fmt.group(stats.totalSets.toLong()), ""))
            stats.last?.let { add(Triple("Última vez", Fmt.relativeDay(Fmt.date(it.ts, repo.zone), today), "")) }
        }
        tiles.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, t) in row.withIndex()) {
                    StatTile(t.first, t.second, Modifier.weight(1f), unit = t.third.ifBlank { null }, accent = r == 0 && i == 0, color = c.surface2)
                }
            }
        }

        // Controles de la gráfica
        if (metrics.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, label) in metrics) Pill(label, selected = metric == k, onClick = { metric = k })
            }
            Spacer(Modifier.height(10.dp))
        }
        Segmented(ranges.map { it.second }, ranges.indexOfFirst { it.first == range }.coerceAtLeast(0), { range = ranges[it].first }, Modifier.fillMaxWidth(), height = 38.dp)
        Spacer(Modifier.height(12.dp))

        val from = Stats.rangeStart(range, today)
        val points = remember(stats, metric, range, units) {
            Stats.series(stats.sessions, metric, from).map { p ->
                val s = stats.sessions.first { it.date == p.date }
                val v = if (metric == "reps") p.value else Fmt.toDisplay(p.value, units)
                val top = s.sets.maxByOrNull { Stats.score(it.weightKg, it.reps, repo.formula) } ?: s.sets.first()
                ChartPoint(Fmt.dateShort(p.date), v, p.isRecord, "${Fmt.plural(s.sets.size, "serie", "series")} · " + describeSet(top.weightKg, top.reps, ex, units))
            }
        }
        val unitLabel = when {
            metric == "reps" -> if (cardio) "min" else "reps"
            metric == "volume" -> Fmt.unit(units)
            else -> Fmt.unit(units)
        }
        StatChart(points, Modifier.fillMaxWidth(), style = style, height = 190.dp, format = { Fmt.num(it, 1) }, unit = unitLabel, emptyText = "Sin sesiones en este periodo")

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Gráfica", Modifier.weight(1f))
            for ((k, ic) in listOf("bars" to Ic.Bars, "line" to Ic.Lines, "area" to Ic.Area, "dots" to Ic.Grid, "ring" to Ic.Pie)) {
                RoundButton(
                    ic, { repo.setSetting("stats.chart", k) }, size = 34.dp,
                    tint = if (style == k) c.onAccent else c.textDim, bg = if (style == k) c.accent else c.surface2, border = style != k,
                )
            }
        }
        val best = stats.bestSet
        if (weighted && best != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(Fs.shapes.chip).background(c.warn.a(0.12f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.Trophy, size = 22.dp, tint = c.warn)
                Column(Modifier.weight(1f)) {
                    Txt("Récord personal", style = Fs.type.smallStrong)
                    Txt(describeSet(best.weightKg, best.reps, ex, units) + " · " + Fmt.relativeDay(Fmt.date(best.ts, repo.zone), today), style = Fs.type.small, color = c.textDim, maxLines = 2)
                }
            }
        }
    }
}

// ───────────────────────── Historial

@Composable
private fun HistoryCard(ex: Exercise, stats: Stats.ExStats, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val units = repo.units
    val today = remember { repo.today() }
    val sessions = remember(stats) { stats.sessions.asReversed() }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var openDay by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (showAll) sessions.take(60) else sessions.take(4)

    Card(modifier) {
        SectionTitle("Historial", trailing = { Txt(Fmt.plural(sessions.size, "sesión", "sesiones"), style = Fs.type.caption, color = c.textDim) })
        Spacer(Modifier.height(6.dp))
        Column(Modifier.animateContentSize(Fs.motion.tween(260))) {
            for (s in shown) {
                val key = s.date.toString()
                val open = openDay == key
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().tappable({ openDay = if (open) null else key }, pressScale = 0.99f).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(Fmt.dateMid(s.date), style = Fs.type.bodyStrong, maxLines = 1)
                            Txt(Fmt.relativeDay(s.date, today), style = Fs.type.caption, color = c.textDim)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Txt(Fmt.plural(s.sets.size, "serie", "series"), style = Fs.type.smallStrong, maxLines = 1)
                            Txt(
                                when {
                                    ex.isCardio -> "${s.reps} min"
                                    s.volume > 0 -> Fmt.volume(s.volume, units)
                                    else -> "${s.reps} reps"
                                },
                                style = Fs.type.caption, color = c.textDim, maxLines = 1,
                            )
                        }
                        Icon(if (open) Ic.ChevronUp else Ic.ChevronDown, size = 18.dp, tint = c.textFaint)
                    }
                    AnimatedVisibility(open, enter = expandVertically(Fs.motion.tween(240)) + fadeIn(Fs.motion.tween(240)), exit = shrinkVertically(Fs.motion.tween(200)) + fadeOut(Fs.motion.tween(140))) {
                        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.sets.forEachIndexed { i, set ->
                                Row(
                                    Modifier.fillMaxWidth().clip(Fs.shapes.chip).background(c.surface2).padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CountBadge(i + 1, size = 24.dp)
                                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                        Txt(describeSet(set.weightKg, set.reps, ex, units), style = Fs.type.bodyStrong, maxLines = 1)
                                        val e = Stats.e1rm(set.weightKg, set.reps, repo.formula)
                                        val extra = listOfNotNull(if (e > 0 && !ex.isCardio) "1RM ≈ " + Fmt.weight(e, units) else null, Fmt.hourMinute(set.ts, repo.zone), set.note.ifBlank { null })
                                        Txt(extra.joinToString(" · "), style = Fs.type.caption, color = c.textDim, maxLines = 2)
                                    }
                                    RoundButton(Ic.Edit, { app.overlays.logSet(ex, editing = set) }, size = 36.dp, bg = Color.Transparent, border = false, tint = c.textDim)
                                    RoundButton(Ic.Trash, {
                                        repo.deleteLog(set.id)
                                        app.toastUndo("Serie eliminada")
                                    }, size = 36.dp, bg = Color.Transparent, border = false, tint = c.textDim)
                                }
                            }
                        }
                    }
                }
                HairLine()
            }
        }
        if (sessions.size > 4) {
            Spacer(Modifier.height(6.dp))
            FsButton(if (showAll) "Ver menos" else "Ver todo el historial", { showAll = !showAll }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost, height = 42.dp)
        }
    }
}

// ───────────────────────── Anotaciones

@Composable
private fun NotesCard(ex: Exercise, cfg: ExerciseConfig, related: List<Note>, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    var text by remember(ex.id) { mutableStateOf(cfg.notes) }
    var focused by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(text)

    // Guarda mientras escribes (con una pequeña pausa) y al salir de la pantalla.
    LaunchedEffect(text) {
        if (text != repo.config(ex.id).notes) {
            delay(600)
            repo.updateConfig(ex.id) { it.copy(notes = text) }
        }
    }
    DisposableEffect(ex.id) {
        onDispose { if (latest != repo.config(ex.id).notes) repo.updateConfig(ex.id) { it.copy(notes = latest) } }
    }
    // Si FITBOT u otra pantalla cambian la nota mientras no editas, se actualiza aquí.
    LaunchedEffect(cfg.notes) { if (!focused && cfg.notes != text) text = cfg.notes }

    Card(modifier) {
        SectionTitle("Mis anotaciones", trailing = {
            Txt(if (text.isBlank()) "Para recordarte algo" else "Se guarda solo", style = Fs.type.caption, color = c.textDim)
        })
        Spacer(Modifier.height(8.dp))
        Box(Modifier.onFocusChanged { focused = it.hasFocus }) {
            FsTextField(
                text, { text = it.take(1500) }, placeholder = "Ej.: Asiento en la posición 4, agarre medio, bajar lento 3 segundos…",
                singleLine = false, minLines = 4, leading = Ic.NotebookPen,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (h in listOf("Ajuste: ", "Agarre: ", "Tempo: ", "Técnica: ", "Meta: ")) {
                Pill(h.trim().trimEnd(':'), soft = true, height = 30.dp, icon = Ic.Plus, onClick = { text = (if (text.isBlank() || text.endsWith("\n")) text else "$text\n") + h })
            }
        }
        if (related.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(8.dp))
            Label("Anotaciones vinculadas")
            for (n in related.take(3)) {
                Row(
                    Modifier.fillMaxWidth().tappable({ app.overlays.editNote(existing = n) }, pressScale = 0.985f).padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.width(4.dp).height(30.dp).clip(Fs.shapes.pill).background(routineColor(n.colorIdx)))
                    Column(Modifier.weight(1f)) {
                        Txt(n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty().ifBlank { "Sin título" } }, style = Fs.type.bodyStrong, maxLines = 1)
                        Txt(Fmt.dateShort(Fmt.date(n.updatedAt, repo.zone)), style = Fs.type.caption, color = c.textDim)
                    }
                    Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FsButton("Nueva anotación sobre este ejercicio", { app.overlays.editNote(exerciseId = ex.id) }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost, icon = Ic.Plus, height = 42.dp)
    }
}

// ───────────────────────── Músculos

@Composable
private fun MusclesCard(ex: Exercise, levels: Map<Muscle, Int>, modifier: Modifier, onOpen: () -> Unit) {
    val c = Fs.colors
    val pulse = rememberPulse()
    Card(modifier, onClick = onOpen) {
        SectionTitle("Músculos que trabaja", action = "Ver detalle", onAction = onOpen)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(120.dp).height(190.dp).clip(Fs.shapes.chip).background(c.surface2), contentAlignment = Alignment.Center) {
                MuscleFigure(levels, Modifier.fillMaxSize(), back = bestSide(levels), pulse = pulse, padding = 0.06f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ex.primary.isNotEmpty()) {
                    Label("Principales")
                    ChipFlow(spacing = 6.dp) { for (m in ex.primary) Pill(m.label, selected = true, height = 30.dp) }
                }
                if (ex.secondary.isNotEmpty()) {
                    Label("Secundarios")
                    ChipFlow(spacing = 6.dp) { for (m in ex.secondary) Pill(m.label, soft = true, height = 30.dp) }
                }
                Txt("Toca la foto del ejercicio o aquí para ver el mapa completo de frente y espalda.", style = Fs.type.caption, color = c.textDim)
            }
        }
    }
}

// ───────────────────────── Información

@Composable
private fun InfoCard(ex: Exercise, modifier: Modifier) {
    val c = Fs.colors
    Card(modifier) {
        SectionTitle("Sobre el ejercicio")
        Spacer(Modifier.height(6.dp))
        Txt(ex.description.ifBlank { "Sin descripción." }, style = Fs.type.body, color = c.text)
        val facts = listOf("Categoría" to ex.categoryName(), "Equipo" to ex.equipment, "Nivel" to ex.level, "Mecánica" to ex.mechanic, "Movimiento" to ex.force).filter { it.second.isNotBlank() }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            for (row in facts.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (f in row) Column(Modifier.weight(1f)) {
                        Label(f.first)
                        Txt(f.second, style = Fs.type.bodyStrong, maxLines = 1)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (ex.aliases.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Txt("También se busca como: " + ex.aliases.take(4).joinToString(", "), style = Fs.type.caption, color = c.textFaint, maxLines = 2)
        }
    }
}

// ───────────────────────── En mis rutinas

@Composable
private fun RoutinesCard(ex: Exercise, inRoutines: List<Routine>, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    Card(modifier) {
        SectionTitle("En mis rutinas")
        Spacer(Modifier.height(6.dp))
        if (inRoutines.isEmpty()) {
            Txt("Este ejercicio todavía no está en ninguna rutina.", style = Fs.type.small, color = c.textDim)
        }
        for (r in inRoutines) {
            val item = r.items.first { it.exerciseId == ex.id }
            Row(
                Modifier.fillMaxWidth().tappable({ app.nav.push(Route.RoutineDetail(r.id)) }, pressScale = 0.985f).padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(icOf(r.icon, Ic.Dumbbell), size = 38.dp, tint = routineColor(r.colorIdx), bg = routineColor(r.colorIdx).soft())
                Column(Modifier.weight(1f)) {
                    Txt(r.name, style = Fs.type.bodyStrong, maxLines = 1)
                    Txt("${repo.itemSets(item)} × ${repo.itemRepsLabel(item)}", style = Fs.type.caption, color = c.textDim)
                }
                Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
            }
        }
        Spacer(Modifier.height(6.dp))
        FsButton("Agregar a una rutina", { app.addToRoutineSheet(listOf(ex.id)) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.Plus, height = 46.dp)
    }
}
