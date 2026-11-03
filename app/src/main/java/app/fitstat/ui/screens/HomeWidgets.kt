package app.fitstat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.app.startWorkout
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import java.time.LocalDate

/**
 * Datos que usan los widgets del inicio. Se calculan una sola vez en [HomeScreen] y se reparten a todos.
 */
class HomeData(
    val today: LocalDate,
    val logs: List<SetLog>,
    val goal: Int,
    val streak: Int,
    val weekDays: Set<LocalDate>,
    val days: Set<LocalDate>,
    val mondayFirst: Boolean,
    val units: String,
    val plan: Stats.TodayPlan,
    val records: List<Stats.RecordEvent>,
    val pinned: List<Note>,
    val next: MilestoneState?,
) {
    /** False si el widget no tiene nada que mostrar (y está marcado para ocultarse sin datos). */
    fun hasContent(id: String): Boolean = when (id) {
        "records" -> records.isNotEmpty()
        "milestone" -> next != null
        "pinned" -> pinned.isNotEmpty()
        else -> true
    }
}

/** Dibuja el widget [id] con el [size] que le toca. El contenido se adapta al ancho: pequeño, mediano o grande. */
@Composable
fun HomeWidget(id: String, size: HomeSize, d: HomeData, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    when (id) {
        "goal" -> GoalWidget(d, size, modifier)
        "today" -> TodayWidget(d, size, modifier)
        "train" -> TrainWidget(d, size, modifier)
        "fitbot" -> FitbotWidget(d, size, modifier)
        "progress" -> ShortcutWidget("Progreso", Ic.Stats, null, "Gráficas, récords y volumen", size, modifier) { app.nav.push(Route.Progress) }
        "milestones" -> {
            val done = repo.unlocked.size
            ShortcutWidget("Hitos", Ic.Trophy, "$done/${Milestones.all.size}", "$done de ${Milestones.all.size} logrados", size, modifier) { app.nav.push(Route.Milestones) }
        }
        "notes" -> {
            val n = repo.notes.size
            ShortcutWidget("Notas", Ic.NotebookPen, n.takeIf { it > 0 }?.toString(), if (n == 0) "Tus anotaciones del gym" else Fmt.plural(n, "anotación", "anotaciones"), size, modifier) { app.nav.push(Route.Notes) }
        }
        "streak" -> StreakWidget(d, size, modifier)
        "volume" -> VolumeWidget(d, size, modifier)
        "records" -> RecordsWidget(d, size, modifier)
        "milestone" -> MilestoneWidget(d, size, modifier)
        "muscles" -> MusclesWidget(d, modifier)
        "heatmap" -> HeatmapWidget(d, modifier)
        "pinned" -> PinnedWidget(d, size, modifier)
        "sleep" -> SleepWidget(size, modifier)
        "music" -> MusicWidget(size, modifier)
    }
}

// ───────────────────────── Meta semanal

@Composable
private fun GoalWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val c = Fs.colors
    val done = d.weekDays.size
    val open = { app.nav.push(Route.Progress) }
    if (size == HomeSize.Large) {
        val ws = Stats.weekStart(d.today, d.mondayFirst)
        Card(modifier.testTag("goal_widget"), onClick = open, accent = true, behind = Modifier.musicFx("goal", onAccent = true)) {
            LedBanner("${Fs.skin.brand} > SEMANA", lit = c.onAccent.a(0.72f))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val n = animatedValue(done.toFloat()).toInt()
                BigNumber("%02d".format(n), style = Fs.type.display, color = c.onAccent)
                Txt("  / ${d.goal} días", Modifier.padding(bottom = 7.dp).weight(1f), Fs.type.body, c.onAccentDim, maxLines = 1)
                if (d.streak > 0) StreakPill(d.streak, Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            ChunkyProgress((done.toFloat() / d.goal.coerceAtLeast(1)).coerceAtMost(1f), height = 22.dp, track = c.onAccent.a(0.18f), fill = SolidColor(c.onAccent))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 7) {
                    val day = ws.plusDays(i.toLong())
                    val did = day in d.weekDays
                    val isToday = day == d.today
                    Box(
                        Modifier.size(34.dp).clip(Fs.shapes.round)
                            .background(if (did) c.onAccent else c.onAccent.a(0.14f))
                            .then(if (isToday && !did) Modifier.border(1.5.dp, c.onAccent, Fs.shapes.round) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (did) Icon(Ic.Check, size = 16.dp, tint = c.accent2, style = "line", weight = 3f)
                        else Txt(Weekdays.short(day.dayOfWeek.value), style = Fs.type.smallStrong, color = c.onAccent.a(if (day.isAfter(d.today)) 0.5f else 0.85f))
                    }
                }
            }
        }
    } else {
        // Mediano: sin letrero LED ni días (así cabe al lado de otro widget).
        Card(modifier.testTag("goal_widget"), onClick = open, accent = true, padding = PaddingValues(Fs.ui.pad(14)), behind = Modifier.musicFx("goal", onAccent = true)) {
            Txt("${Fs.skin.brand} > SEMANA", style = Fs.type.label, color = c.onAccent.a(0.72f), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val n = animatedValue(done.toFloat()).toInt()
                BigNumber("%02d".format(n), style = Fs.type.title, color = c.onAccent)
                Txt("/ ${d.goal} días", Modifier.padding(bottom = 4.dp), Fs.type.small, c.onAccentDim, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            ChunkyProgress((done.toFloat() / d.goal.coerceAtLeast(1)).coerceAtMost(1f), height = 14.dp, track = c.onAccent.a(0.18f), fill = SolidColor(c.onAccent))
            Spacer(Modifier.weight(1f, fill = true))
            if (d.streak > 0) StreakPill(d.streak, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun StreakPill(streak: Int, modifier: Modifier = Modifier) {
    val c = Fs.colors
    Row(
        modifier.clip(Fs.shapes.pill).background(c.onAccent.a(0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Ic.Flame, size = 16.dp, tint = c.onAccent)
        Txt("$streak ${if (streak == 1) "semana" else "semanas"}", style = Fs.type.smallStrong, color = c.onAccent, maxLines = 1)
    }
}

// ───────────────────────── Hoy

@Composable
private fun TodayWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val plan = d.plan
    val exById = repo.exById
    val count = plan.routines.size + (if (plan.exercises.isNotEmpty()) 1 else 0)
    if (size != HomeSize.Large) {
        Card(modifier, padding = PaddingValues(Fs.ui.pad(14))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Hoy", Modifier.weight(1f), Fs.type.h2, maxLines = 1)
                if (count > 0) CountBadge(count, size = 24.dp)
            }
            Spacer(Modifier.height(8.dp))
            val first = plan.routines.firstOrNull()
            when {
                first != null -> {
                    Row(
                        Modifier.fillMaxWidth().tappable({ app.nav.push(Route.RoutineDetail(first.id)) }, pressScale = 0.985f),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconBadge(icOf(first.icon, Ic.Dumbbell), size = 36.dp, tint = routineColor(first.colorIdx), bg = routineColor(first.colorIdx).soft())
                        Column(Modifier.weight(1f)) {
                            Txt(first.name, style = Fs.type.bodyStrong, maxLines = 1)
                            Txt("${first.items.size} ej. · ~${repo.routineMinutes(first)} min", style = Fs.type.caption, color = c.textDim, maxLines = 1)
                        }
                    }
                    if (count > 1) Txt("y ${count - 1} más hoy", Modifier.padding(top = 4.dp), Fs.type.caption, c.textDim, maxLines = 1)
                    Spacer(Modifier.weight(1f, fill = true))
                    Spacer(Modifier.height(8.dp))
                    FsButton("Entrenar", { app.startWorkout(first.id) }, Modifier.fillMaxWidth(), icon = Ic.Play, height = 44.dp)
                }
                plan.exercises.isNotEmpty() -> {
                    Txt("De tu set", style = Fs.type.bodyStrong)
                    Txt(plan.exercises.take(3).joinToString(", ") { it.name } + if (plan.exercises.size > 3) "…" else "", style = Fs.type.caption, color = c.textDim, maxLines = 2)
                    Spacer(Modifier.weight(1f, fill = true))
                    Spacer(Modifier.height(8.dp))
                    FsButton("Entrenar", { app.startWorkout(null, plan.exercises.map { it.id }) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.Play, height = 44.dp)
                }
                else -> {
                    Txt("Día libre", style = Fs.type.h3)
                    Txt("Nada programado hoy.", style = Fs.type.small, color = c.textDim)
                    Spacer(Modifier.weight(1f, fill = true))
                    Spacer(Modifier.height(8.dp))
                    FsButton("Entrenar", { app.startWorkout(null) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.Play, height = 44.dp)
                }
            }
        }
        return
    }
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Hoy", Modifier.weight(1f), Fs.type.h2)
            if (count > 0) CountBadge(count)
        }
        Spacer(Modifier.height(10.dp))
        if (count == 0) {
            Txt("Día libre", style = Fs.type.h3)
            Txt("No hay nada programado hoy. Puedes entrenar igual o elegir una rutina.", Modifier.padding(top = 2.dp), Fs.type.small, c.textDim)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FsButton("Entreno libre", { app.startWorkout(null) }, Modifier.weight(1f), icon = Ic.Play, height = 48.dp)
                FsButton("Rutinas", { app.nav.push(Route.Routines) }, Modifier.weight(1f), kind = BtnKind.Secondary, height = 48.dp)
            }
        } else {
            for (r in plan.routines) {
                val exs = r.items.mapNotNull { exById[it.exerciseId] }
                Row(
                    Modifier.fillMaxWidth().tappable({ app.nav.push(Route.RoutineDetail(r.id)) }, pressScale = 0.985f).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(icOf(r.icon, Ic.Dumbbell), size = 44.dp, tint = routineColor(r.colorIdx), bg = routineColor(r.colorIdx).soft())
                    Column(Modifier.weight(1f)) {
                        Txt(r.name, style = Fs.type.h3, maxLines = 1)
                        Txt("${Fmt.plural(r.items.size, "ejercicio", "ejercicios")} · ~${repo.routineMinutes(r)} min", style = Fs.type.small, color = c.textDim, maxLines = 1)
                    }
                    PhotoStack(exs, size = 36.dp)
                }
                Spacer(Modifier.height(4.dp))
                FsButton("Iniciar ${r.name}", { app.startWorkout(r.id) }, Modifier.fillMaxWidth(), icon = Ic.Play, height = 48.dp)
                Spacer(Modifier.height(8.dp))
            }
            if (plan.exercises.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Txt("De tu set", style = Fs.type.h3)
                        Txt(plan.exercises.take(3).joinToString(", ") { it.name } + if (plan.exercises.size > 3) "…" else "", style = Fs.type.small, color = c.textDim, maxLines = 2)
                    }
                    PhotoStack(plan.exercises, size = 36.dp)
                }
                Spacer(Modifier.height(10.dp))
                FsButton("Entrenar estos", { app.startWorkout(null, plan.exercises.map { it.id }) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.Play, height = 48.dp)
            }
        }
    }
}

// ───────────────────────── Entrenar (el orbe)

@Composable
private fun TrainWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val skinned = !Fs.skin.isDefault
    // Las skins son siempre oscuras: el panel del orbe usa la superficie de la skin, no el gris claro.
    val panel = when {
        skinned -> c.surface
        c.dark -> Color(0xFFDCDCE2)
        else -> Color(0xFFEDEDF1)
    }
    val ink = if (skinned) c.text else Color(0xFF30303A)
    val inkDim = if (skinned) c.textDim else Color(0xFF5A5A66)
    val first = d.plan.routines.firstOrNull()
    val start = { if (first != null) app.startWorkout(first.id) else startSheet(app, repo) }
    val box = modifier.clip(Fs.shapes.card).background(panel)
        .then(if (skinned) Modifier.border(1.dp, c.outline, Fs.shapes.card).skinCardDecor() else Modifier)
    when (size) {
        HomeSize.Large -> Row(
            box.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Orb(size = 96.dp, ic = Ic.Play, onClick = start)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("Entrenar", style = Fs.type.h2, color = ink, upper = Fs.skin.caps, maxLines = 1)
                Txt(if (first != null) "Hoy toca ${first.name}" else "Elige una rutina o entrena libre", style = Fs.type.small, color = inkDim, maxLines = 2)
            }
        }
        else -> {
            val orb = if (size == HomeSize.Small) 76.dp else 120.dp
            Box(box.padding(if (size == HomeSize.Small) 10.dp else 14.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Orb(size = orb, ic = Ic.Play, onClick = start)
                    Txt("Entrenar", style = Fs.type.smallStrong, color = ink, upper = Fs.skin.caps, maxLines = 1)
                }
            }
        }
    }
}

// ───────────────────────── FITBOT

@Composable
private fun FitbotWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val open = { app.nav.push(Route.Bot) }
    val line = when {
        d.plan.routines.isNotEmpty() -> "Hoy toca ${d.plan.routines.first().name}. ¡Vamos!"
        d.streak >= 2 -> "¡Vas en racha de ${d.streak} semanas!"
        !repo.hasUserData -> "Hola, soy FITBOT. Puedo armar tu rutina."
        else -> "¿Te armo una rutina o reviso tu progreso?"
    }
    when (size) {
        HomeSize.Small -> Card(modifier, onClick = open, padding = PaddingValues(10.dp)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                FitBot(size = 62.dp, mood = BotMood.Idle)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("Chatear", style = Fs.type.smallStrong, color = c.accentText, maxLines = 1)
                    Icon(Ic.ArrowRight, size = 14.dp, tint = c.accentText)
                }
            }
        }
        HomeSize.Medium -> Card(modifier, onClick = open, padding = PaddingValues(Fs.ui.pad(14))) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FitBot(size = 92.dp, mood = BotMood.Idle)
            }
            Spacer(Modifier.height(6.dp))
            Txt(line, style = Fs.type.small, color = c.textDim, maxLines = 4)
            Spacer(Modifier.weight(1f, fill = true))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt("Chatear", style = Fs.type.smallStrong, color = c.accentText)
                Icon(Ic.ArrowRight, size = 16.dp, tint = c.accentText)
            }
        }
        HomeSize.Large -> Card(modifier, onClick = open) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FitBot(size = 76.dp, mood = BotMood.Idle)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(line, style = Fs.type.body, color = c.textDim, maxLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Txt("Chatear", style = Fs.type.smallStrong, color = c.accentText)
                        Icon(Ic.ArrowRight, size = 16.dp, tint = c.accentText)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Accesos rápidos (Progreso, Hitos, Notas)

@Composable
private fun ShortcutWidget(label: String, ic: Ic, badge: String?, desc: String, size: HomeSize, modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    if (size == HomeSize.Small) {
        Card(modifier, onClick = onClick, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = 10.dp, vertical = Fs.ui.pad(12))) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                IconBadge(ic, size = 40.dp)
                Txt(label, style = Fs.type.smallStrong, maxLines = 1)
                Txt(badge ?: " ", style = Fs.type.caption, color = c.textDim, maxLines = 1)
            }
        }
    } else {
        Card(modifier, onClick = onClick, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = Fs.ui.pad(14), vertical = Fs.ui.pad(12))) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(ic, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Txt(label, style = Fs.type.bodyStrong, maxLines = 1)
                    Txt(desc, style = Fs.type.caption, color = c.textDim, maxLines = if (size == HomeSize.Large) 1 else 2)
                }
                if (size == HomeSize.Large) {
                    if (badge != null) Txt(badge, style = Fs.type.smallStrong, color = c.accentText, maxLines = 1)
                    Icon(Ic.ChevronRight, size = 20.dp, tint = c.textFaint)
                }
            }
        }
    }
}

// ───────────────────────── Racha y volumen

@Composable
private fun StreakWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val best = remember(d.logs, d.goal, d.mondayFirst) { Stats.bestStreakWeeks(Stats.workoutDays(d.logs, repo.zone), d.goal, d.mondayFirst) }
    val weeks = remember(d.logs, d.today, d.mondayFirst) { Stats.weeks(d.logs, 8, d.mondayFirst, d.today, repo.zone) }
    val small = size == HomeSize.Small
    Card(
        modifier, onClick = { app.nav.push(Route.Progress) }, shape = Fs.shapes.inner,
        padding = if (small) PaddingValues(horizontal = 12.dp, vertical = Fs.ui.pad(12)) else PaddingValues(Fs.ui.pad(16)),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.Flame, size = 18.dp, tint = c.warn)
                    Label("Racha")
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BigNumber(animatedValue(d.streak.toFloat()).toInt().toString(), style = if (small) Fs.type.h1 else Fs.type.title)
                    Txt(if (small) "sem" else if (d.streak == 1) "semana" else "semanas", Modifier.padding(bottom = 4.dp), Fs.type.caption, c.textDim, maxLines = 1)
                }
                Txt("Mejor: $best", Modifier.padding(top = 2.dp), Fs.type.caption, c.textDim, maxLines = 1)
            }
            if (size == HomeSize.Large) {
                // Las últimas 8 semanas: llena si cumpliste la meta.
                val goal = d.goal.coerceAtLeast(1)
                val on = c.accent
                val off = c.textFaint.a(0.45f)
                Canvas(Modifier.size(width = 116.dp, height = 46.dp)) {
                    val n = weeks.size.coerceAtLeast(1)
                    val gap = 5.dp.toPx()
                    val w = (this.size.width - gap * (n - 1)) / n
                    for ((i, wk) in weeks.withIndex()) {
                        val frac = (wk.workouts.toFloat() / goal).coerceIn(0f, 1f)
                        val h = (this.size.height * frac).coerceAtLeast(5.dp.toPx())
                        drawRoundRect(
                            if (wk.workouts >= goal) on else off,
                            Offset(i * (w + gap), this.size.height - h), Size(w, h), CornerRadius(w / 2f, w / 2f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val weeks = remember(d.logs, d.today, d.mondayFirst) { Stats.weeks(d.logs, 8, d.mondayFirst, d.today, repo.zone) }
    val small = size == HomeSize.Small
    Card(
        modifier, onClick = { app.nav.push(Route.Progress) }, shape = Fs.shapes.inner,
        padding = if (small) PaddingValues(horizontal = 12.dp, vertical = Fs.ui.pad(12)) else PaddingValues(Fs.ui.pad(16)),
    ) {
        Label(if (small) "Volumen" else "Volumen semanal")
        val v = weeks.lastOrNull()?.volume ?: 0.0
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BigNumber(Fmt.compactVolume(v, d.units), style = if (small) Fs.type.h1 else Fs.type.title)
            Txt(Fmt.unit(d.units), Modifier.padding(bottom = 4.dp), Fs.type.caption, c.textDim, maxLines = 1)
        }
        Spacer(Modifier.weight(1f, fill = true))
        Sparkline(weeks.map { it.volume }, Modifier.fillMaxWidth().height(if (size == HomeSize.Large) 52.dp else if (small) 24.dp else 32.dp).padding(top = 4.dp))
    }
}

// ───────────────────────── Récords recientes

@Composable
private fun RecordsWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val large = size == HomeSize.Large
    val items = d.records.take(if (large) 3 else 2)
    Card(modifier, padding = if (large) PaddingValues(Fs.ui.pad(16)) else PaddingValues(Fs.ui.pad(14))) {
        SectionTitle(if (large) "Récords recientes" else "Récords", action = if (large) "Ver todo" else null, onAction = { app.nav.push(Route.Progress) })
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Txt("Aún no hay récords. Registra series y tus mejores marcas aparecerán aquí.", style = Fs.type.small, color = c.textDim)
        }
        for (r in items) {
            val ex = repo.exById[r.exerciseId] ?: continue
            Row(
                Modifier.fillMaxWidth().tappable({ app.nav.push(Route.Exercise(ex.id)) }, pressScale = 0.985f).padding(vertical = if (large) 6.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 10.dp),
            ) {
                ExerciseThumb(ex, if (large) 44.dp else 36.dp, shape = Fs.shapes.round)
                Column(Modifier.weight(1f)) {
                    Txt(ex.name, style = if (large) Fs.type.bodyStrong else Fs.type.smallStrong, maxLines = 1)
                    Txt(
                        (if (r.weightKg > 0) Fmt.weight(r.weightKg, d.units) + " × " else "") + r.reps + " " + ex.repsUnit +
                            (if (large) " · " + Fmt.relativeDay(Fmt.date(r.ts, repo.zone), d.today) else ""),
                        style = if (large) Fs.type.small else Fs.type.caption, color = c.textDim, maxLines = 1,
                    )
                }
                if (large) Icon(Ic.Star, size = 22.dp, tint = c.warn)
            }
        }
    }
}

// ───────────────────────── Próximo hito

@Composable
private fun MilestoneWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val c = Fs.colors
    val next = d.next
    val large = size == HomeSize.Large
    Card(modifier, onClick = { app.nav.push(Route.Milestones) }, padding = if (large) PaddingValues(Fs.ui.pad(16)) else PaddingValues(Fs.ui.pad(14))) {
        if (next == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Ic.Trophy, size = if (large) 48.dp else 40.dp, tint = c.warn, bg = c.warn.a(0.16f))
                Column(Modifier.weight(1f)) {
                    Label("Hitos")
                    Txt("¡Lograste todos los hitos!", style = if (large) Fs.type.h3 else Fs.type.bodyStrong, maxLines = 2)
                }
            }
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 14.dp else 10.dp)) {
            IconBadge(icOf(next.def.icon, Ic.Trophy), size = if (large) 48.dp else 40.dp, tint = c.warn, bg = c.warn.a(0.16f))
            Column(Modifier.weight(1f)) {
                Label("Próximo hito")
                Txt(next.def.title, style = if (large) Fs.type.h3 else Fs.type.bodyStrong, maxLines = if (large) 1 else 2)
                if (large) Txt(next.def.desc, style = Fs.type.small, color = c.textDim, maxLines = 2)
            }
        }
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(12.dp))
        ChunkyProgress(next.progress, height = if (large) 12.dp else 10.dp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Txt("${Fmt.group(next.value.toLong())} / ${Fmt.group(next.def.target.toLong())} ${next.def.unit}", Modifier.weight(1f), Fs.type.caption, c.textDim, maxLines = 1)
            Txt(Fmt.percent(next.progress), style = Fs.type.caption, color = c.accentText)
        }
    }
}

// ───────────────────────── Mapa muscular

@Composable
private fun MusclesWidget(d: HomeData, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val load = remember(d.logs, d.today) { Stats.muscleLoad(d.logs, repo.exById, 7, d.today, repo.zone) }
    val top = load.sets.entries.sortedByDescending { it.value }.take(4)
    Card(modifier, onClick = { app.nav.push(Route.Progress) }) {
        SectionTitle("Músculos de la semana", trailing = { Txt("últimos 7 días", style = Fs.type.caption, color = c.textDim) })
        Spacer(Modifier.height(6.dp))
        if (load.levels.isEmpty()) {
            Txt("Registra series y aquí verás qué músculos trabajaste.", Modifier.padding(vertical = 14.dp), Fs.type.small, c.textDim)
        } else {
            val pulse = rememberPulse()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleFigure(load.levels, Modifier.weight(1f).height(210.dp), pulse = pulse, heat = load.heat)
                MuscleFigure(load.levels, Modifier.weight(1f).height(210.dp), back = true, pulse = pulse, heat = load.heat)
            }
            Spacer(Modifier.height(6.dp))
            val maxV = top.firstOrNull()?.value ?: 1f
            for ((m, v) in top) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt(m.label, Modifier.width(104.dp), Fs.type.small, c.textDim, maxLines = 1)
                    ChunkyProgress(v / maxV, Modifier.weight(1f), height = 8.dp)
                    Txt(Fmt.num(v.toDouble(), 1), Modifier.width(32.dp), Fs.type.caption, c.textDim, align = TextAlign.End, maxLines = 1)
                }
            }
        }
    }
}

// ───────────────────────── Calendario de actividad

@Composable
private fun HeatmapWidget(d: HomeData, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val heat = remember(d.logs, d.today, d.mondayFirst) { Stats.heat(d.logs, 12, d.mondayFirst, d.today, repo.zone) }
    Card(modifier, onClick = { app.nav.push(Route.Progress) }) {
        SectionTitle("Actividad", trailing = {
            Txt(Fmt.plural(d.days.count { !it.isBefore(heat.start) }, "día", "días") + " en 12 semanas", style = Fs.type.caption, color = c.textDim)
        })
        Spacer(Modifier.height(10.dp))
        Heatmap(heat, d.mondayFirst, today = d.today)
    }
}

// ───────────────────────── Anotaciones fijadas

@Composable
private fun PinnedWidget(d: HomeData, size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val c = Fs.colors
    val large = size == HomeSize.Large
    val shown = d.pinned.take(if (large) 3 else 2)
    Card(modifier, onClick = { app.nav.push(Route.Notes) }, padding = if (large) PaddingValues(Fs.ui.pad(16)) else PaddingValues(Fs.ui.pad(14))) {
        SectionTitle(if (large) "Anotaciones fijadas" else "Notas fijadas", action = if (large) "Ver todas" else null, onAction = { app.nav.push(Route.Notes) })
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Txt("Fija una nota importante y la verás aquí.", style = Fs.type.small, color = c.textDim)
        }
        for (nt in shown) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 3.dp).width(4.dp).height(34.dp).clip(Fs.shapes.pill).background(routineColor(nt.colorIdx)))
                Column(Modifier.weight(1f)) {
                    Txt(nt.title.ifBlank { "Sin título" }, style = Fs.type.bodyStrong, maxLines = 1)
                    Txt(nt.body.lineSequence().firstOrNull().orEmpty(), style = Fs.type.small, color = c.textDim, maxLines = 1)
                }
            }
        }
    }
}
