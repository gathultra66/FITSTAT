package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.*
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private class DaySummary(val date: LocalDate, val sets: List<SetLog>, val exercises: Int, val volume: Double)

private fun compactNum(v: Double): String = when {
    v >= 1_000_000 -> Fmt.num(v / 1_000_000, 1) + "M"
    v >= 10_000 -> Fmt.num(v / 1000, 0) + "k"
    v >= 1000 -> Fmt.num(v / 1000, 1) + "k"
    else -> Fmt.num(v, 0)
}

/** Estadísticas generales: semanas, calendario, músculos, categorías, récords e historial. */
@Composable
fun ProgressScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val units = repo.units
    val logs = repo.logs
    val today = rememberToday()
    val zone = repo.zone
    val mondayFirst = repo.mondayFirst
    val goal = repo.weeklyGoal
    val exById = repo.exById
    val formula = repo.formula
    val data = repo.data
    val agg = remember(data, today) { repo.aggregates() }

    var metric by rememberSaveable { mutableIntStateOf(0) }
    val defWeeks = remember {
        when (repo.prefs.str("stats.range")) {
            "4w" -> 4
            "12w" -> 12
            "6m" -> 26
            "all" -> 0
            else -> 8
        }
    }
    var rangeSel by rememberSaveable { mutableIntStateOf(defWeeks) }
    var muscleDays by rememberSaveable { mutableIntStateOf(30) }
    var shownDays by rememberSaveable { mutableIntStateOf(8) }
    var allRecords by rememberSaveable { mutableStateOf(false) }

    val firstDay = remember(logs, zone) { logs.minOfOrNull { it.ts }?.let { Fmt.date(it, zone) } }
    val weeksCount = if (rangeSel > 0) rangeSel else {
        val n = firstDay?.let { ChronoUnit.WEEKS.between(Stats.weekStart(it, mondayFirst), today) + 1 } ?: 8L
        n.toInt().coerceIn(4, 52)
    }
    val weeks = remember(logs, weeksCount, mondayFirst, today) { Stats.weeks(logs, weeksCount, mondayFirst, today, zone) }
    val points = remember(weeks, metric, units) {
        weeks.map { w ->
            ChartPoint(
                Fmt.dateShort(w.start),
                when (metric) {
                    0 -> w.workouts.toDouble()
                    1 -> w.sets.toDouble()
                    else -> Fmt.toDisplay(w.volume, units)
                },
            )
        }
    }
    val vals = points.map { it.value }
    val avg = if (vals.isEmpty()) 0.0 else vals.average()
    val bestV = vals.maxOrNull() ?: 0.0
    val thisWeek = vals.lastOrNull() ?: 0.0
    val weekDays = remember(logs, mondayFirst, today) { Stats.daysThisWeek(logs, mondayFirst, today, zone) }
    val heat = remember(logs, mondayFirst, today) { Stats.heat(logs, 26, mondayFirst, today, zone) }
    val load = remember(logs, muscleDays, today, exById) { Stats.muscleLoad(logs, exById, muscleDays, today, zone) }
    val topMuscles = remember(load) { load.sets.entries.sortedByDescending { it.value }.take(8) }
    val catSets = remember(logs, muscleDays, today, exById) {
        val from = today.minusDays((muscleDays - 1).toLong())
        val m = HashMap<String, Int>()
        for (l in logs) {
            if (Fmt.date(l.ts, zone).isBefore(from)) continue
            val e = exById[l.exerciseId] ?: continue
            m[e.category] = (m[e.category] ?: 0) + 1
        }
        m
    }
    val bests = remember(logs, formula) { Stats.bestSets(logs, formula).sortedByDescending { it.second.ts } }
    val days = remember(logs, zone, exById) {
        logs.groupBy { Fmt.date(it.ts, zone) }.entries.sortedByDescending { it.key }.map { (d, ls) ->
            DaySummary(d, ls.sortedBy { it.ts }, ls.map { it.exerciseId }.distinct().size, ls.filter { exById[it.exerciseId]?.isCardio != true }.sumOf { it.volume })
        }
    }
    val pulse = rememberPulse()
    val metricUnit = when (metric) {
        0 -> "días"
        1 -> "series"
        else -> Fmt.unit(units)
    }

    ScreenList(
        title = "Progreso",
        subtitle = if (logs.isEmpty()) "Todavía sin datos" else "${Fmt.plural(agg.workoutDays, "día", "días")} entrenando",
        onBack = { nav.back() },
    ) {
        if (logs.isEmpty()) {
            item("empty") {
                EmptyState(
                    "Aquí vivirá tu progreso",
                    "Registra tu primera serie y verás tus semanas, el calendario, los músculos que trabajas y tus récords.",
                    Modifier.enter(0), mood = BotMood.Sleepy, actionLabel = "Empezar a entrenar", onAction = { startSheet(app, repo) },
                )
            }
            if (!repo.hasUserData) item("demo") {
                FsButton(
                    "Ver con datos de ejemplo",
                    { repo.replaceAll(Demo.build(repo.data, repo.now(), repo.zone, repo.formula), "Datos de ejemplo cargados"); app.toastUndo("Datos de ejemplo cargados") },
                    Modifier.enter(1).fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.Sparkles,
                )
            }
        } else {
            item("hero") {
                Card(Modifier.enter(0), accent = true) {
                    LedBanner("${Fs.skin.brand} > STATS", lit = c.onAccent.a(0.72f))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        BigNumber(animatedValue(agg.workoutDays.toFloat()).toInt().toString(), style = Fs.type.display, color = c.onAccent)
                        Txt("  días entrenados", Modifier.padding(bottom = 7.dp).weight(1f), Fs.type.body, c.onAccentDim, maxLines = 1)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroChip(Ic.Flame, "Racha: ${agg.currentStreak} sem")
                        HeroChip(Ic.Trophy, "Mejor: ${agg.bestStreak} sem")
                    }
                    Spacer(Modifier.height(14.dp))
                    ChunkyProgress((weekDays.size.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f), height = 16.dp, track = c.onAccent.a(0.18f), fill = androidx.compose.ui.graphics.SolidColor(c.onAccent))
                    Txt("Esta semana: ${weekDays.size} de $goal días", Modifier.padding(top = 6.dp), Fs.type.caption, c.onAccentDim, maxLines = 1)
                }
            }
            item("tiles") {
                Column(Modifier.enter(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Series", Fmt.group(agg.totalSets.toLong()), Modifier.weight(1f))
                        StatTile("Repeticiones", Fmt.group(agg.totalReps.toLong()), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Volumen total", Fmt.volume(agg.totalVolume, units), Modifier.weight(1f))
                        StatTile("Récords", "${agg.prCount}", Modifier.weight(1f), sub = "marcas personales")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Ejercicios", "${agg.distinctExercises}", Modifier.weight(1f), sub = "distintos")
                        StatTile("Categorías", "${agg.categoriesTrained}/${Categories.all.size}", Modifier.weight(1f), sub = "entrenadas")
                    }
                }
            }
            item("weekly") {
                Card(Modifier.enter(2)) {
                    SectionTitle("Por semana")
                    Spacer(Modifier.height(10.dp))
                    Segmented(listOf("Días", "Series", "Volumen"), metric, { metric = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, v) in listOf("4 sem" to 4, "8 sem" to 8, "12 sem" to 12, "6 meses" to 26, "Todo" to 0)) {
                            Pill(label, selected = rangeSel == v, soft = true, height = 32.dp, onClick = { rangeSel = v })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    StatChart(
                        points, Modifier.fillMaxWidth(), height = 200.dp, unit = metricUnit,
                        format = { v -> if (metric == 2) compactNum(v) else Fmt.num(v, 0) },
                        maxValue = if (metric == 0) 7.0 else null,
                        emptyText = "Sin datos en este periodo",
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniStat("Promedio", if (metric == 2) compactNum(avg) else Fmt.num(avg, 1))
                        MiniStat("Mejor semana", if (metric == 2) compactNum(bestV) else Fmt.num(bestV, 0))
                        MiniStat("Esta semana", if (metric == 2) compactNum(thisWeek) else Fmt.num(thisWeek, 0))
                    }
                }
            }
            item("heat") {
                Card(Modifier.enter(3)) {
                    SectionTitle("Calendario de actividad", trailing = {
                        Txt("26 semanas", style = Fs.type.caption, color = c.textDim)
                    })
                    Spacer(Modifier.height(10.dp))
                    Heatmap(heat, mondayFirst, today = today)
                }
            }
            item("muscles") {
                Card(Modifier.enter(4)) {
                    SectionTitle("Músculos y categorías")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, d) in listOf("7 días" to 7, "30 días" to 30, "90 días" to 90)) {
                            Pill(label, selected = muscleDays == d, soft = true, height = 32.dp, onClick = { muscleDays = d })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (load.levels.isEmpty()) {
                        Txt("No hay series en este periodo.", Modifier.padding(vertical = 16.dp), Fs.type.small, c.textDim)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MuscleFigure(load.levels, Modifier.weight(1f).height(230.dp), pulse = pulse, heat = load.heat)
                            MuscleFigure(load.levels, Modifier.weight(1f).height(230.dp), back = true, pulse = pulse, heat = load.heat)
                        }
                        Spacer(Modifier.height(8.dp))
                        val maxV = topMuscles.firstOrNull()?.value ?: 1f
                        for ((m, v) in topMuscles) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Txt(m.label, Modifier.width(112.dp), Fs.type.small, c.textDim, maxLines = 1)
                                ChunkyProgress(v / maxV, Modifier.weight(1f), height = 8.dp)
                                Txt(Fmt.num(v.toDouble(), 1), Modifier.width(36.dp), Fs.type.caption, c.textDim, align = TextAlign.End, maxLines = 1)
                            }
                        }
                    }
                    if (catSets.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Label("Series por categoría")
                        Spacer(Modifier.height(6.dp))
                        val total = catSets.values.sum().coerceAtLeast(1)
                        val maxC = catSets.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                        for (k in Categories.all) {
                            val n = catSets[k.id] ?: continue
                            Row(
                                Modifier.fillMaxWidth().tappable({ nav.push(Route.Category(k.id)) }, pressScale = 0.985f).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Txt(k.name, Modifier.width(96.dp), Fs.type.small, c.text, maxLines = 1)
                                ChunkyProgress(n.toFloat() / maxC, Modifier.weight(1f), height = 8.dp)
                                Txt("$n · ${Fmt.percent(n.toFloat() / total)}", Modifier.width(66.dp), Fs.type.caption, c.textDim, align = TextAlign.End, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (bests.isNotEmpty()) item("records") {
                Card(Modifier.enter(5)) {
                    SectionTitle("Mejores marcas", trailing = { Txt("${bests.size} ejercicios", style = Fs.type.caption, color = c.textDim) })
                    Spacer(Modifier.height(6.dp))
                    val shown = if (allRecords) bests else bests.take(6)
                    for ((id, set) in shown) {
                        val ex = exById[id] ?: continue
                        Row(
                            Modifier.fillMaxWidth().tappable({ nav.push(Route.Exercise(ex.id)) }, pressScale = 0.985f).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ExerciseThumb(ex, 44.dp, shape = Fs.shapes.round)
                            Column(Modifier.weight(1f)) {
                                Txt(ex.name, style = Fs.type.bodyStrong, maxLines = 1)
                                Txt(
                                    describeSet(set.weightKg, set.reps, ex, units) + " · " + Fmt.relativeDay(Fmt.date(set.ts, zone), today),
                                    style = Fs.type.small, color = c.textDim, maxLines = 1,
                                )
                            }
                            Icon(Ic.Star, size = 22.dp, tint = c.warn)
                        }
                    }
                    if (bests.size > 6) {
                        FsButton(
                            if (allRecords) "Ver menos" else "Ver las ${bests.size}", { allRecords = !allRecords },
                            Modifier.fillMaxWidth(), kind = BtnKind.Ghost, height = 42.dp,
                        )
                    }
                }
            }
            item("history") {
                Card(Modifier.enter(6)) {
                    SectionTitle("Historial", trailing = { Txt(Fmt.plural(days.size, "día", "días"), style = Fs.type.caption, color = c.textDim) })
                    Spacer(Modifier.height(6.dp))
                    for (d in days.take(shownDays)) {
                        val names = d.sets.map { it.exerciseId }.distinct().mapNotNull { exById[it]?.name }
                        Row(
                            Modifier.fillMaxWidth().tappable({ daySheet(app, d.date) }, pressScale = 0.985f).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Txt(d.date.dayOfMonth.toString(), style = Fs.type.h2, maxLines = 1)
                                Label(Fmt.monthShort(d.date.monthValue))
                            }
                            Column(Modifier.weight(1f)) {
                                Txt(names.take(3).joinToString(", ") + if (names.size > 3) "…" else "", style = Fs.type.bodyStrong, maxLines = 1)
                                Txt(
                                    "${Fmt.plural(d.sets.size, "serie", "series")}" + (if (d.volume > 0) " · " + Fmt.volume(d.volume, units) else ""),
                                    style = Fs.type.small, color = c.textDim, maxLines = 1,
                                )
                            }
                            Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
                        }
                    }
                    if (days.size > shownDays) {
                        FsButton("Ver más días", { shownDays += 10 }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost, height = 42.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroChip(ic: Ic, text: String) {
    val c = Fs.colors
    Row(
        Modifier.clip(Fs.shapes.pill).background(c.onAccent.a(0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(ic, size = 16.dp, tint = c.onAccent)
        Txt(text, style = Fs.type.smallStrong, color = c.onAccent, maxLines = 1)
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BigNumber(value, style = Fs.type.h2)
        Label(label)
    }
}

// ───────────────────────── Detalle de un día

private fun daySheet(app: AppState, day: LocalDate) {
    app.overlays.sheet { close -> DaySheetBody(app, day, close) }
}

@Composable
private fun DaySheetBody(app: AppState, day: LocalDate, close: () -> Unit) {
    val repo = app.repo
    val c = Fs.colors
    val units = repo.units
    val zone = repo.zone
    val sets = repo.logs.filter { Fmt.date(it.ts, zone) == day }.sortedBy { it.ts }
    val groups = sets.groupBy { it.exerciseId }
    SheetBody(Fmt.dateLong(day), subtitle = "${Fmt.plural(sets.size, "serie", "series")} · ${Fmt.plural(groups.size, "ejercicio", "ejercicios")}") {
        if (sets.isEmpty()) Txt("Ya no quedan series en este día.", Modifier.padding(vertical = 12.dp), Fs.type.small, c.textDim)
        for ((id, g) in groups) {
            val ex = repo.exById[id] ?: continue
            Column(Modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.surface2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth().tappable({ close(); app.nav.push(Route.Exercise(id)) }, pressScale = 0.985f),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExerciseThumb(ex, 38.dp, shape = Fs.shapes.round)
                    Txt(ex.name, Modifier.weight(1f), Fs.type.bodyStrong, maxLines = 1)
                    Icon(Ic.ChevronRight, size = 16.dp, tint = c.textFaint)
                }
                for ((i, l) in g.withIndex()) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CountBadge(i + 1, size = 24.dp)
                        Txt(describeSet(l.weightKg, l.reps, ex, units), Modifier.weight(1f), Fs.type.small, c.text, maxLines = 1)
                        Txt(Fmt.hourMinute(l.ts, zone), style = Fs.type.caption, color = c.textDim)
                    }
                }
            }
        }
        if (sets.isNotEmpty()) {
            FsButton(
                "Eliminar este día", {
                    app.overlays.confirm("¿Eliminar este día?", "Se borran las ${sets.size} series del ${Fmt.dateMid(day)}.", "Eliminar", danger = true) {
                        repo.update("Día eliminado") { d -> d.copy(logs = d.logs.filterNot { Fmt.date(it.ts, zone) == day }) }
                        close()
                        app.toastUndo("Día eliminado")
                    }
                },
                Modifier.fillMaxWidth(), kind = BtnKind.Danger, icon = Ic.Trash, height = 46.dp,
            )
        }
    }
}
