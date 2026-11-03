package app.fitstat.ui.screens

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.*
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

private fun progressText(s: MilestoneState): String =
    "${Fmt.group(s.value.toLong().coerceAtMost(s.def.target.toLong()))} / ${Fmt.group(s.def.target.toLong())} ${s.def.unit}"

/** Todos los hitos (logros) con su avance. */
@Composable
fun MilestonesScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val states = remember(repo.logs, repo.routines, repo.notes, repo.unlocked, repo.reminders, repo.configs) { repo.milestoneStates() }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val done = states.count { it.done }
    val next = Milestones.next(states)
    val groupsShown = Milestones.groups.filter { g -> (group == null || group == g) && states.any { it.def.group == g } }

    ScreenList(title = "Hitos", subtitle = "$done de ${states.size} logrados", onBack = { nav.back() }) {
        item("hero") {
            Card(Modifier.enter(0), accent = true) {
                LedBanner("${Fs.skin.brand} > HITOS", lit = c.onAccent.a(0.72f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber("%02d".format(animatedValue(done.toFloat()).toInt()), style = Fs.type.display, color = c.onAccent)
                    Txt("  de ${states.size}", Modifier.padding(bottom = 7.dp).weight(1f), Fs.type.body, c.onAccentDim, maxLines = 1)
                    Icon(Ic.Trophy, Modifier.padding(bottom = 8.dp), size = 28.dp, tint = c.onAccent)
                }
                Spacer(Modifier.height(12.dp))
                ChunkyProgress(done.toFloat() / states.size.coerceAtLeast(1), height = 18.dp, track = c.onAccent.a(0.18f), fill = SolidColor(c.onAccent))
            }
        }
        if (next != null) item("next") {
            Card(Modifier.enter(1), onClick = { milestoneSheet(app, next) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(icOf(next.def.icon, Ic.Trophy), size = 50.dp, tint = c.warn, bg = c.warn.a(0.16f))
                    Column(Modifier.weight(1f)) {
                        Label("Siguiente hito")
                        Txt(next.def.title, style = Fs.type.h3, maxLines = 1)
                        Txt(next.def.desc, style = Fs.type.small, color = c.textDim, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(12.dp))
                ChunkyProgress(next.progress, height = 12.dp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Txt(progressText(next), Modifier.weight(1f), Fs.type.caption, c.textDim, maxLines = 1)
                    Txt(Fmt.percent(next.progress), style = Fs.type.caption, color = c.accentText)
                }
            }
        }
        item("filters") {
            Row(Modifier.enter(2).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Todos", selected = group == null, soft = true, height = 34.dp, onClick = { group = null })
                for (g in Milestones.groups) {
                    val n = states.count { it.def.group == g && it.done }
                    val t = states.count { it.def.group == g }
                    if (t > 0) Pill("$g $n/$t", selected = group == g, soft = true, height = 34.dp, onClick = { group = if (group == g) null else g })
                }
            }
        }
        for (g in groupsShown) {
            val list = states.filter { it.def.group == g }
            item("h_$g") { SectionLabel(g.uppercase()) }
            items(list.size, key = { "m_" + list[it].def.id }) { i ->
                MilestoneRow(list[i]) { milestoneSheet(app, list[i]) }
            }
        }
    }
}

@Composable
private fun MilestoneRow(s: MilestoneState, onClick: () -> Unit) {
    val repo = LocalRepo.current
    val c = Fs.colors
    val tint = if (s.done) c.warn else c.textFaint
    Card(
        Modifier.then(if (s.done) Modifier.border(1.dp, c.warn.a(0.45f), Fs.shapes.inner) else Modifier),
        onClick = onClick, shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(12)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icOf(s.def.icon, Ic.Trophy), size = 46.dp, tint = tint, bg = if (s.done) c.warn.a(0.16f) else c.surface3)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(s.def.title, style = Fs.type.bodyStrong, color = if (s.done) c.text else c.textDim, maxLines = 1)
                Txt(s.def.desc, style = Fs.type.small, color = c.textDim, maxLines = 2)
                if (!s.done) {
                    Spacer(Modifier.height(4.dp))
                    ChunkyProgress(s.progress, height = 6.dp)
                    Txt(progressText(s), style = Fs.type.caption, color = c.textFaint, maxLines = 1)
                } else {
                    val at = s.unlockedAt
                    if (at != null) Txt("Logrado el " + Fmt.dateShort(Fmt.date(at, repo.zone)), style = Fs.type.caption, color = c.warn, maxLines = 1)
                }
            }
            if (s.done) Icon(Ic.CheckCheck, size = 22.dp, tint = c.warn, style = "line", weight = 3f)
        }
    }
}

private fun milestoneSheet(app: AppState, s: MilestoneState) {
    app.overlays.sheet { close ->
        val repo = app.repo
        val c = Fs.colors
        val at = s.unlockedAt
        SheetBody(s.def.title, subtitle = s.def.group) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(92.dp).clip(Fs.shapes.round).background(if (s.done) c.warn.a(0.16f) else c.surface3),
                    contentAlignment = Alignment.Center,
                ) { Icon(icOf(s.def.icon, Ic.Trophy), size = 44.dp, tint = if (s.done) c.warn else c.textFaint) }
                Txt(s.def.desc, style = Fs.type.body, color = c.textDim, align = TextAlign.Center)
                ChunkyProgress(s.progress, Modifier.fillMaxWidth(), height = 14.dp)
                Txt(progressText(s), style = Fs.type.smallStrong, color = c.textDim)
                if (s.done && at != null) {
                    Txt("¡Logrado el ${Fmt.dateLong(Fmt.date(at, repo.zone))}!", style = Fs.type.bodyStrong, color = c.warn, align = TextAlign.Center)
                }
            }
            FsButton("Cerrar", close, Modifier.fillMaxWidth(), kind = BtnKind.Secondary)
        }
    }
}
