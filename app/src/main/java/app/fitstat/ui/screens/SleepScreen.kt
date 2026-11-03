package app.fitstat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.Connectors
import app.fitstat.data.*
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.launch
import java.time.LocalDate

// ───────────────────────── Colores de las fases

@Composable
internal fun stageColor(t: String, onAccent: Boolean = false): Color {
    val c = Fs.colors
    return when (t) {
        SleepStageType.AWAKE -> c.warn
        SleepStageType.REM -> c.good
        // Sobre la tarjeta de color de acento, el «ligero» en acento se perdía: ahí va claro.
        SleepStageType.LIGHT -> if (onAccent) c.onAccent.a(0.7f) else c.accent.a(0.55f)
        SleepStageType.DEEP -> c.accent2
        else -> if (onAccent) c.onAccent.a(0.7f) else c.accent.a(0.8f)
    }
}

internal fun nightTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Anoche"
    today.minusDays(1) -> "Antenoche"
    else -> Fmt.dateMid(date)
}

// ───────────────────────── Pantalla

@Composable
fun SleepScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val hub = app.connectors
    val scope = rememberCoroutineScope()
    val today = repo.today()
    val zone = repo.zone
    val goal = repo.sleepGoal
    val nights = remember(repo.sleep, zone) { repo.sleepNights() }
    var range by remember { mutableIntStateOf(0) }
    val count = listOf(7, 14, 30)[range]
    val shown = nights.take(count)
    val summary = remember(shown, goal, zone) { Sleep.summary(shown, goal, zone) }
    val latest = remember(nights, today) { Sleep.latest(nights, today) }

    ScreenList(
        "Sueño", subtitle = if (nights.isEmpty()) "Cómo duermes" else "Meta: ${Fmt.num(goal, 1)} h por noche", onBack = { app.nav.back() },
        actions = {
            if (hub.sleepSources().isNotEmpty()) {
                RoundButton(Ic.Refresh, {
                    scope.launch {
                        val r = hub.syncSleep()
                        app.overlays.toast(if (r.newNights > 0) Fmt.plural(r.newNights, "noche nueva", "noches nuevas") else if (r.ok) "Todo al día" else r.message.take(80))
                    }
                }, Modifier.testTag("sleep_sync"))
            }
            RoundButton(Ic.Plus, { openSleepEntry(app) }, Modifier.testTag("sleep_add"))
        },
    ) {
        if (nights.isEmpty()) {
            item("empty") { Box(Modifier.enter(0)) { SleepEmpty(app) } }
        } else {
            latest?.let { n -> item("last") { Box(Modifier.enter(0)) { LastNightCard(n, today, goal) } } }
                ?: item("nolast") {
                    Card(Modifier.enter(0)) {
                        Txt("Todavía no hay datos de anoche", style = Fs.type.bodyStrong)
                        Txt("Si tienes un reloj o pulsera, abre su app para que sincronice; o estima la noche con tu teléfono.", Modifier.padding(top = 2.dp), Fs.type.small, c.textDim)
                    }
                }
            item("range") { Segmented(listOf("7 noches", "14 noches", "30 noches"), range, { range = it }, Modifier.testTag("sleep_range")) }
            item("summary") { Box(Modifier.enter(1)) { SleepSummaryCard(summary, shown, goal, count) } }
            item("listhead") { SectionTitle("Noches") }
            items(shown.size, key = { "n${shown[it].main.id}" }) { i ->
                val n = shown[i]
                NightRow(n, today) { openNightSheet(app, n, today) }
            }
            item("foot") { SleepFooter() }
        }
        if (nights.isEmpty()) item("foot0") { SleepFooter() }
    }
}

@Composable
private fun SleepFooter() {
    val c = Fs.colors
    Txt(
        "El sueño no es un diagnóstico médico. Las fases solo aparecen si un reloj o pulsera las registró; lo estimado con el teléfono son solo horas.",
        Modifier.fillMaxWidth().padding(horizontal = 8.dp), Fs.type.caption, c.textFaint,
    )
}

// ───────────────────────── Sin datos

@Composable
private fun SleepEmpty(app: AppState) {
    val c = Fs.colors
    val hub = app.connectors
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FitBot(size = 84.dp, mood = BotMood.Sleepy)
            Txt("Aún no hay noches", style = Fs.type.h2, align = TextAlign.Center)
            Txt(
                "Hay tres maneras de llenar esto. Con un reloj o pulsera verás hasta las fases; sin reloj, FITSTAT estima las horas con el uso del teléfono; y siempre puedes registrar una noche a mano.",
                style = Fs.type.small, color = c.textDim, align = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FsButton(
                if (hub.status(Connectors.HEALTH) == ConnStatus.Off) "Conectar mi reloj (Health Connect)" else "Revisar Health Connect",
                { app.nav.push(Route.Connector(Connectors.HEALTH)) }, Modifier.fillMaxWidth().testTag("sleep_health"), icon = Ic.HeartPulse, height = 48.dp,
            )
            FsButton(
                if (hub.status(Connectors.PHONE) == ConnStatus.Off) "Estimar con mi teléfono" else "Revisar la estimación",
                { app.nav.push(Route.Connector(Connectors.PHONE)) }, Modifier.fillMaxWidth().testTag("sleep_phone"), kind = BtnKind.Secondary, icon = Ic.Phone, height = 48.dp,
            )
            FsButton("Registrar a mano", { openSleepEntry(app) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.Edit, height = 48.dp)
        }
    }
}

// ───────────────────────── Última noche

@Composable
private fun LastNightCard(n: SleepNight, today: LocalDate, goal: Double) {
    val repo = LocalRepo.current
    val c = Fs.colors
    val zone = repo.zone
    val s = n.main
    val asleep = Sleep.asleepMs(s)
    val goalMs = Sleep.goalMs(goal)
    val frac = (asleep.toFloat() / goalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(nightTitle(n.date, today), Modifier.weight(1f), color = c.onAccentDim)
            SourcePill(s, onAccent = true)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigNumber(Sleep.fmtShort(asleep), style = Fs.type.display, color = c.onAccent)
            Txt("h dormidas", Modifier.padding(bottom = 7.dp), Fs.type.body, c.onAccentDim, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        ChunkyProgress(frac, height = 18.dp, track = c.onAccent.a(0.18f), fill = androidx.compose.ui.graphics.SolidColor(c.onAccent))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("${Fmt.hourMinute(s.start, zone)} → ${Fmt.hourMinute(s.end, zone)}", style = Fs.type.smallStrong, color = c.onAccent)
            Txt(
                if (asleep >= goalMs) "¡Meta cumplida!" else "Faltaron ${Sleep.fmt(goalMs - asleep)} para tu meta",
                style = Fs.type.small, color = c.onAccentDim, maxLines = 1,
            )
        }
        if (Sleep.hasStages(s)) {
            Spacer(Modifier.height(16.dp))
            StagesBlock(s, onAccent = true)
        } else if (s.source == SleepSrc.PHONE) {
            Txt("Estimado con el uso del teléfono: son solo horas, sin fases.", Modifier.padding(top = 10.dp), Fs.type.caption, c.onAccentDim)
        }
        if (n.extra.isNotEmpty()) {
            Txt("Más descansos ese día: " + n.extra.joinToString { Sleep.fmt(Sleep.durationMs(it)) }, Modifier.padding(top = 8.dp), Fs.type.caption, c.onAccentDim)
        }
    }
}

@Composable
internal fun SourcePill(s: SleepSession, onAccent: Boolean = false) {
    val c = Fs.colors
    val fg = if (onAccent) c.onAccent else c.accentText
    val bg = if (onAccent) c.onAccent.a(0.16f) else c.accentSoft
    val ic = when (s.source) {
        SleepSrc.HEALTH -> Ic.Watch
        SleepSrc.PHONE -> Ic.Phone
        else -> Ic.Edit
    }
    Row(Modifier.clip(Fs.shapes.pill).background(bg).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(ic, size = 13.dp, tint = fg)
        Txt(SleepSrc.short(s.source), style = Fs.type.caption, color = fg, maxLines = 1)
    }
}

// ───────────────────────── Fases

/** Gráfica de la noche por fases y el reparto de cada una. */
@Composable
internal fun StagesBlock(s: SleepSession, onAccent: Boolean = false) {
    val c = Fs.colors
    val zone = LocalRepo.current.zone
    val totals = remember(s) { Sleep.stageTotals(s) }
    val dur = Sleep.durationMs(s).coerceAtLeast(1)
    val lanes = listOf(SleepStageType.AWAKE, SleepStageType.REM, SleepStageType.LIGHT, SleepStageType.DEEP).filter { totals.containsKey(it) }
    val laneColors = lanes.associateWith { stageColor(it, onAccent) }
    val track = if (onAccent) c.onAccent.a(0.12f) else c.surface2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height((lanes.size.coerceAtLeast(1) * 20).dp)) {
            val laneH = size.height / lanes.size.coerceAtLeast(1)
            val bar = laneH * 0.62f
            for ((i, t) in lanes.withIndex()) {
                val y = i * laneH + (laneH - bar) / 2f
                drawRoundRect(track, Offset(0f, y), Size(size.width, bar), CornerRadius(bar / 2f))
            }
            for (st in s.stages) {
                val i = lanes.indexOf(st.t).takeIf { it >= 0 } ?: continue
                val x0 = ((st.s - s.start).coerceAtLeast(0L).toFloat() / dur) * size.width
                val x1 = ((st.e - s.start).coerceAtMost(dur).toFloat() / dur) * size.width
                if (x1 <= x0) continue
                val y = i * laneH + (laneH - bar) / 2f
                drawRoundRect(laneColors.getValue(st.t), Offset(x0, y), Size((x1 - x0).coerceAtLeast(2f), bar), CornerRadius(bar / 2f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(Fmt.hourMinute(s.start, zone), style = Fs.type.caption, color = if (onAccent) c.onAccentDim else c.textFaint)
            Txt(Fmt.hourMinute(s.end, zone), style = Fs.type.caption, color = if (onAccent) c.onAccentDim else c.textFaint)
        }
        ChipFlow(Modifier.padding(top = 4.dp), spacing = 8.dp) {
            for (t in lanes) {
                val ms = totals.getValue(t)
                Row(
                    Modifier.clip(Fs.shapes.pill).background(if (onAccent) c.onAccent.a(0.14f) else c.surface2).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(9.dp).clip(Fs.shapes.round).background(laneColors.getValue(t)))
                    Txt("${SleepStageType.label(t)} ${Sleep.fmt(ms)} · ${(ms * 100f / dur).toInt()}%", style = Fs.type.caption, color = if (onAccent) c.onAccent else c.text, maxLines = 1)
                }
            }
        }
    }
}

// ───────────────────────── Resumen y gráfica

@Composable
private fun SleepSummaryCard(sm: SleepSummary, nights: List<SleepNight>, goal: Double, count: Int) {
    val c = Fs.colors
    Card {
        Label("Promedio de ${Fmt.plural(sm.nights, "noche", "noches")}")
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BigNumber(Sleep.fmtShort(sm.avgAsleepMs), style = Fs.type.title)
            Txt("h por noche", Modifier.padding(bottom = 4.dp), Fs.type.small, c.textDim, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        NightBars(nights, goal, labels = count <= 14)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Duermes", sm.avgBedMin?.let { Sleep.clock(it) } ?: "—", Modifier.weight(1f))
            MiniStat("Despiertas", sm.avgWakeMin?.let { Sleep.clock(it) } ?: "—", Modifier.weight(1f))
            MiniStat("Meta", "${sm.metGoal}/${sm.nights}", Modifier.weight(1f))
        }
        sm.regularity?.let {
            Txt("Regularidad: $it (tu hora de dormir varía ±${sm.bedSpreadMin} min).", Modifier.padding(top = 10.dp), Fs.type.small, c.textDim)
        }
        if (sm.withStages > 0 && sm.withStages < sm.nights) Txt("${sm.withStages} de ${sm.nights} noches traen fases (las demás son estimadas o a mano).", Modifier.padding(top = 4.dp), Fs.type.caption, c.textFaint)
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    val c = Fs.colors
    Column(modifier.clip(Fs.shapes.inner).background(c.surface2).padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(label, style = Fs.type.caption, color = c.textDim, maxLines = 1)
        Txt(value, style = Fs.type.bodyStrong, maxLines = 1)
    }
}

/** Barras de las últimas noches (la más antigua a la izquierda) con una línea en la meta. */
@Composable
internal fun NightBars(nights: List<SleepNight>, goal: Double, labels: Boolean, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 96.dp) {
    val c = Fs.colors
    val ordered = remember(nights) { nights.sortedBy { it.date } }
    val goalMs = Sleep.goalMs(goal)
    val top = maxOf(goalMs * 5 / 4, ordered.maxOfOrNull { Sleep.asleepMs(it.main) } ?: 0L).coerceAtLeast(1)
    val on = c.accent
    val off = c.accent.a(0.42f)
    val line = c.textFaint
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = ordered.size.coerceAtLeast(1)
            val gap = if (n > 14) 3.dp.toPx() else 6.dp.toPx()
            val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
            for ((i, nt) in ordered.withIndex()) {
                val v = Sleep.asleepMs(nt.main)
                val h = (size.height * v.toFloat() / top).coerceAtLeast(4.dp.toPx())
                drawRoundRect(if (v >= goalMs) on else off, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(minOf(w / 2f, 6.dp.toPx())))
            }
            val gy = size.height - size.height * goalMs.toFloat() / top
            drawLine(line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        if (labels && ordered.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (nt in ordered) Txt(Weekdays.short(nt.date.dayOfWeek.value), Modifier.weight(1f), Fs.type.caption, c.textFaint, maxLines = 1, align = TextAlign.Center)
            }
        }
        Txt("Línea punteada: tu meta de ${Fmt.num(goal, 1)} h", style = Fs.type.caption, color = c.textFaint)
    }
}

// ───────────────────────── Lista de noches

@Composable
private fun NightRow(n: SleepNight, today: LocalDate, onClick: () -> Unit) {
    val repo = LocalRepo.current
    val c = Fs.colors
    val zone = repo.zone
    val s = n.main
    val asleep = Sleep.asleepMs(s)
    val met = asleep >= Sleep.goalMs(repo.sleepGoal)
    Card(onClick = onClick, shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(12))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(if (met) Ic.CircleCheck else Ic.Moon, size = 40.dp, tint = if (met) c.good else c.accentText, bg = (if (met) c.good else c.accent).a(0.16f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(nightTitle(n.date, today), style = Fs.type.bodyStrong, maxLines = 1)
                Txt("${Fmt.hourMinute(s.start, zone)} → ${Fmt.hourMinute(s.end, zone)}", style = Fs.type.small, color = c.textDim, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(Sleep.fmt(asleep), style = Fs.type.bodyStrong, maxLines = 1)
                SourcePill(s)
            }
        }
    }
}

// ───────────────────────── Hojas

private fun openNightSheet(app: AppState, n: SleepNight, today: LocalDate) {
    app.overlays.sheet { close ->
        val repo = LocalRepo.current
        val c = Fs.colors
        val zone = repo.zone
        val s = n.main
        SheetBody(nightTitle(n.date, today), subtitle = SleepSrc.label(s.source)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("Dormiste", Fmt.hourMinute(s.start, zone), Modifier.weight(1f))
                MiniStat("Despertaste", Fmt.hourMinute(s.end, zone), Modifier.weight(1f))
                MiniStat("Total", Sleep.fmt(Sleep.asleepMs(s)), Modifier.weight(1f))
            }
            if (Sleep.awakeMs(s) > 0) Txt("Despierto durante la noche: ${Sleep.fmt(Sleep.awakeMs(s))}", style = Fs.type.small, color = c.textDim)
            if (Sleep.hasStages(s)) StagesBlock(s)
            if (s.source == SleepSrc.PHONE) Txt("Estimación: el tramo más largo de esa noche sin usar el teléfono. No es una medición.", style = Fs.type.caption, color = c.textFaint)
            if (s.origin.isNotBlank()) Txt("Registrado por: ${s.origin}", style = Fs.type.caption, color = c.textFaint)
            if (s.note.isNotBlank()) Txt(s.note, style = Fs.type.small, color = c.textDim)
            if (s.source == SleepSrc.MANUAL) {
                FsButton("Eliminar esta noche", { close(); if (repo.deleteSleep(s.id)) app.toastUndo("Noche eliminada") }, Modifier.fillMaxWidth(), kind = BtnKind.Danger, icon = Ic.Trash, height = 46.dp)
            } else {
                Txt("Esta noche viene de ${if (s.source == SleepSrc.HEALTH) "tu reloj" else "la estimación del teléfono"}: si la borraras, volvería en la siguiente actualización. Para corregirla, registra la noche a mano (lo que escribes tú manda).", style = Fs.type.caption, color = c.textFaint)
            }
        }
    }
}

/** Hoja para registrar una noche a mano. */
fun openSleepEntry(app: AppState) {
    app.overlays.sheet { close ->
        val repo = LocalRepo.current
        val c = Fs.colors
        var bed by remember { mutableStateOf(23 to 0) }
        var wake by remember { mutableStateOf(7 to 0) }
        var day by remember { mutableIntStateOf(0) }
        val today = repo.today()
        SheetBody("Registrar una noche", subtitle = "Lo que escribes aquí manda sobre lo estimado") {
            Txt("¿Qué día te despertaste?", style = Fs.type.smallStrong, color = c.textDim)
            Segmented(listOf("Hoy", "Ayer", "Anteayer"), day, { day = it }, Modifier.testTag("sleep_day"))
            TimeRow("Te dormiste a las", bed, Modifier.testTag("sleep_bed")) { app.overlays.pickTime("Hora de dormir", bed.first, bed.second) { h, m -> bed = h to m } }
            TimeRow("Despertaste a las", wake, Modifier.testTag("sleep_wake")) { app.overlays.pickTime("Hora de despertar", wake.first, wake.second) { h, m -> wake = h to m } }
            val (start, end) = sleepEntryTimes(today, day, bed, wake, repo.zone)
            Txt("Son ${Sleep.fmt(end - start)} de sueño.", style = Fs.type.small, color = c.textDim)
            FsButton(
                "Guardar",
                {
                    repo.addSleep(start, end).fold(
                        onSuccess = { close(); app.toastUndo("Noche registrada") },
                        onFailure = { app.overlays.toast(it.message ?: "No se pudo guardar") },
                    )
                },
                Modifier.fillMaxWidth().testTag("sleep_save"), height = 50.dp,
            )
        }
    }
}

/** Convierte "me desperté hoy/ayer/anteayer a las X, y me dormí a las Y" en dos momentos. */
internal fun sleepEntryTimes(today: LocalDate, daysAgo: Int, bed: Pair<Int, Int>, wake: Pair<Int, Int>, zone: java.time.ZoneId): Pair<Long, Long> =
    Sleep.span(today.minusDays(daysAgo.toLong()), bed, wake, zone)

@Composable
private fun TimeRow(label: String, t: Pair<Int, Int>, modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    Row(
        modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.surface2).tappable(onClick, pressScale = 0.985f).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, Modifier.weight(1f), Fs.type.bodyStrong, maxLines = 1)
        Txt(Fmt.time(t.first, t.second), style = Fs.type.h3, color = c.accentText, maxLines = 1)
    }
}
