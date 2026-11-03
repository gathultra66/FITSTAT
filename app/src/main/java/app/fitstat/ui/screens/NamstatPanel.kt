package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.Connectors
import app.fitstat.conn.LinkResult
import app.fitstat.conn.NsLinkState
import app.fitstat.conn.NsPlaylists
import app.fitstat.conn.NsQueue
import app.fitstat.conn.NsSearch
import app.fitstat.conn.NsStats
import app.fitstat.conn.NsTrack
import app.fitstat.data.Pages
import app.fitstat.data.Repo
import app.fitstat.data.Settings
import app.fitstat.ui.app.AppState
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────────────────── NAMSTAT: el panel completo del conector (Enlace T-A-T)

/** «3:07» o «1:02:09». */
internal fun msClock(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun androidx.compose.foundation.lazy.LazyListScope.namstatItems(app: AppState, repo: Repo, status: ConnStatus) {
    if (!repo.prefs.bool("conn.${Connectors.NAMSTAT}.enabled")) return
    item("ns_link") { Box(Modifier.enter(1)) { NsLinkCard(app) } }
    if (status == ConnStatus.Ready) {
        item("ns_now") { Box(Modifier.enter(2)) { NsNowCard(app) } }
        item("ns_queue") { Box(Modifier.enter(3)) { NsQueueCard(app) } }
        item("ns_lists") { Box(Modifier.enter(4)) { NsListsCard(app) } }
        item("ns_search") { Box(Modifier.enter(5)) { NsSearchCard(app) } }
        item("ns_stats") { Box(Modifier.enter(6)) { NsStatsCard(app) } }
    }
    item("ns_settings") {
        Box(Modifier.enter(7)) {
            SettingsGroup("Descansos y entrenamiento", Settings.specsOf(Pages.CONNECTORS).filter { it.key.startsWith("conn.namstat.") && it.key != "conn.namstat.enabled" })
        }
    }
    item("ns_widget") {
        Box(Modifier.enter(8)) { AddHomeWidgetCard("music", "Widget de música", "Pon en el inicio la carátula y los botones de play, pausa, siguiente y anterior de NAMSTAT.") }
    }
    item("ns_notes") {
        val c = Fs.colors
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("Cómo funciona", style = Fs.type.smallStrong, color = c.textDim)
            Txt("• FITSTAT y NAMSTAT hablan por el Enlace T-A-T: un canal local entre las dos apps, sin internet. NAMSTAT solo contesta a FITSTAT (puedes apagarlo en NAMSTAT › Ajustes › Aplicación).", style = Fs.type.caption, color = c.textFaint)
            Txt("• La portada y el «ahora suena» básico siguen viniendo de la sesión de reproducción de Android, así que se pide el «Acceso a notificaciones» como con las otras apps.", style = Fs.type.caption, color = c.textFaint)
            Txt("• Si NAMSTAT está cerrado, las órdenes (reproducir una lista, buscar y poner una canción…) lo abren un momento y vuelven a FITSTAT.", style = Fs.type.caption, color = c.textFaint)
            Txt("• Al volver del descanso, el volumen sube dentro de NAMSTAT; el volumen del teléfono no se toca.", style = Fs.type.caption, color = c.textFaint)
        }
    }
}

/** Corre una orden y avisa si NAMSTAT no pudo (o tuvo que abrirse). */
private fun runLink(app: AppState, scope: kotlinx.coroutines.CoroutineScope, block: suspend () -> LinkResult) {
    scope.launch {
        val r = block()
        if (!r.ok) app.overlays.toast(r.message.ifEmpty { "NAMSTAT no pudo hacerlo." })
        else if (r.json["opened"] != null) app.overlays.toast(r.message)
    }
}

@Composable
private fun NsLinkCard(app: AppState) {
    val c = Fs.colors
    val ns = app.connectors.namstat
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ns.startPolling() }
    DisposableEffect(Unit) { onDispose { ns.stopPolling() } }
    val st = ns.linkState
    Card {
        Label("Enlace T-A-T")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(Fs.shapes.round).background(if (st == NsLinkState.Linked) c.good else if (st == NsLinkState.Unknown) c.textFaint else c.warn))
            Txt(st.label + if (st == NsLinkState.Linked && ns.version.isNotEmpty()) " · NAMSTAT ${ns.version}" else "", Modifier.weight(1f), Fs.type.bodyStrong, maxLines = 2)
        }
        val hint = when (st) {
            NsLinkState.Closed -> "NAMSTAT no está abierto. Ábrelo (puede quedarse en segundo plano) para ver la cola, las listas y las estadísticas."
            NsLinkState.NoLink -> "Esta versión de NAMSTAT no tiene el enlace. Instala NAMSTAT 1.2 o más nueva."
            NsLinkState.Disabled -> "El enlace está apagado en NAMSTAT: enciéndelo en NAMSTAT › Ajustes › Aplicación."
            NsLinkState.OldProto -> "NAMSTAT usa una versión del enlace que esta FITSTAT no entiende. Actualiza FITSTAT."
            NsLinkState.Error -> ns.message.ifEmpty { "NAMSTAT no contesta." }
            else -> ""
        }
        if (hint.isNotEmpty()) Txt(hint, Modifier.padding(top = 8.dp), Fs.type.small, c.textDim)
        if (st != NsLinkState.Linked) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FsButton("Abrir NAMSTAT", { app.connectors.platform.open(ConnOpen.Namstat) }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.External, height = 44.dp)
                FsButton("Comprobar", { scope.launch { ns.hello(); ns.refresh() } }, Modifier.weight(1f).testTag("ns_check"), kind = BtnKind.Secondary, icon = Ic.Refresh, height = 44.dp)
            }
        }
    }
}

@Composable
private fun NsNowCard(app: AppState) {
    val c = Fs.colors
    val ns = app.connectors.namstat
    val scope = rememberCoroutineScope()
    val s = ns.state
    // La barra avanza sola entre consulta y consulta.
    var pos by remember { mutableStateOf(0L) }
    LaunchedEffect(s) { while (true) { pos = ns.positionNow(); delay(400) } }
    var drag by remember { mutableStateOf<Float?>(null) }
    Card {
        Label("Control de NAMSTAT")
        Spacer(Modifier.height(10.dp))
        val t = s?.track
        if (s == null || t == null) {
            Txt(if (s != null && !s.libraryReady) "NAMSTAT está cargando tu biblioteca…" else "No hay nada en la cola. Elige una lista, busca una canción o abre NAMSTAT.", style = Fs.type.small, color = c.textDim)
            return@Card
        }
        Txt(t.title, style = Fs.type.bodyStrong, maxLines = 2)
        Txt(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), style = Fs.type.small, color = c.textDim, maxLines = 2)
        val meta = listOfNotNull(t.year.takeIf { it > 0 }?.toString(), t.genre.takeIf { it.isNotBlank() }, t.format.takeIf { it.isNotBlank() }, if (t.plays > 0) "${t.plays} reproducciones" else null)
        if (meta.isNotEmpty()) Txt(meta.joinToString(" · "), Modifier.padding(top = 2.dp), Fs.type.caption, c.textFaint, maxLines = 2)
        Txt(
            (if (s.playing) "Sonando" else if (s.buffering) "Cargando" else "En pausa") + (if (s.source.isNotBlank()) " · ${s.source}" else "") + (if (s.queueSize > 0) " · ${s.queueIndex + 1} de ${s.queueSize}" else ""),
            Modifier.padding(top = 4.dp), Fs.type.caption, c.accentText, maxLines = 2,
        )
        Spacer(Modifier.height(10.dp))
        val dur = s.durationMs.coerceAtLeast(1L).toFloat()
        FsSlider(drag ?: pos.toFloat(), { drag = it }, 0f, dur, Modifier.fillMaxWidth().testTag("ns_seek"), onFinished = {
            val to = (drag ?: pos.toFloat()).toLong()
            drag = null
            runLink(app, scope) { ns.seek(to) }
        })
        Row(Modifier.fillMaxWidth()) {
            Txt(msClock((drag ?: pos.toFloat()).toLong()), style = Fs.type.caption, color = c.textDim)
            Spacer(Modifier.weight(1f))
            Txt(msClock(s.durationMs), style = Fs.type.caption, color = c.textDim)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(Ic.SkipBack, { runLink(app, scope) { ns.previous() } }, Modifier.testTag("ns_prev"), size = 44.dp)
            Box(
                Modifier.size(54.dp).tappable({ runLink(app, scope) { ns.toggle() } }).clip(Fs.shapes.round).background(c.accent).testTag("ns_toggle"),
                contentAlignment = Alignment.Center,
            ) { Icon(if (s.playing) Ic.Pause else Ic.Play, size = 25.dp, tint = c.onAccent) }
            RoundButton(Ic.SkipForward, { runLink(app, scope) { ns.next() } }, Modifier.testTag("ns_next"), size = 44.dp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (s.shuffle) "Aleatorio: sí" else "Aleatorio: no", s.shuffle, { runLink(app, scope) { ns.shuffle(!s.shuffle) } }, Modifier.weight(1f).testTag("ns_shuffle"))
            Chip(when (s.repeat) { "all" -> "Repetir: todo"; "one" -> "Repetir: una"; else -> "Repetir: no" }, s.repeat != "off", { runLink(app, scope) { ns.repeat() } }, Modifier.weight(1f).testTag("ns_repeat"))
            Chip(if (t.fav) "Favorita ♥" else "Favorita", t.fav, { runLink(app, scope) { ns.favorite(!t.fav) } }, Modifier.weight(1f).testTag("ns_fav"))
        }
        Spacer(Modifier.height(12.dp))
        var vol by remember(s.volume) { mutableStateOf(s.volume.toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.Volume, size = 20.dp, tint = c.textDim)
            FsSlider(vol, { vol = it; scope.launch { ns.volume(it.toDouble()) } }, 0f, 1f, Modifier.weight(1f).testTag("ns_volume"))
            Txt("${(vol * 100).toInt()}%", Modifier.width(44.dp), Fs.type.caption, c.textDim)
        }
        Txt("Es el volumen de NAMSTAT; el del teléfono no cambia.", Modifier.padding(top = 2.dp), Fs.type.caption, c.textFaint)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("Velocidad", Modifier.weight(1f), Fs.type.small, c.textDim)
            Stepper("%.2f".format(s.speed).trimEnd('0').trimEnd('.'), { runLink(app, scope) { ns.speed((s.speed - 0.05).coerceAtLeast(0.5)) } }, { runLink(app, scope) { ns.speed((s.speed + 0.05).coerceAtMost(2.0)) } }, unit = "×")
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.Moon, size = 18.dp, tint = c.textDim)
            val left = s.sleepMs
            Txt(
                when { s.sleepEnd -> "Se pausa al terminar la canción"; left != null -> "Se pausa en ${msClock(left)}"; else -> "Temporizador de sueño" },
                Modifier.weight(1f), Fs.type.small, if (left != null || s.sleepEnd) c.accentText else c.textDim, maxLines = 2,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("15 min", false, { runLink(app, scope) { ns.sleepIn(15) } }, Modifier.weight(1f))
            Chip("30 min", false, { runLink(app, scope) { ns.sleepIn(30) } }, Modifier.weight(1f))
            Chip("60 min", false, { runLink(app, scope) { ns.sleepIn(60) } }, Modifier.weight(1f))
            Chip("Al final", s.sleepEnd, { runLink(app, scope) { ns.sleepAtEnd() } }, Modifier.weight(1f))
            if (s.sleepMs != null || s.sleepEnd) Chip("Quitar", false, { runLink(app, scope) { ns.sleepCancel() } }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fs.colors
    Box(
        modifier.height(40.dp).clip(Fs.shapes.pill).background(if (on) c.accent else c.surface2).tappable(onClick, pressScale = 0.96f),
        contentAlignment = Alignment.Center,
    ) { Txt(text, Modifier.padding(horizontal = 6.dp), Fs.type.caption, if (on) c.onAccent else c.textDim, maxLines = 1) }
}

@Composable
private fun TrackRow(t: NsTrack, trailing: @Composable RowScope.() -> Unit = {}, onClick: (() -> Unit)? = null) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.tappable(onClick, pressScale = 0.985f) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(t.title, style = Fs.type.smallStrong, maxLines = 1)
            Txt(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), style = Fs.type.caption, color = c.textDim, maxLines = 1)
        }
        if (t.durationMs > 0) Txt(msClock(t.durationMs), style = Fs.type.caption, color = c.textFaint)
        trailing()
    }
}

@Composable
private fun NsQueueCard(app: AppState) {
    val c = Fs.colors
    val ns = app.connectors.namstat
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<NsQueue?>(null) }
    val cur = ns.state?.track
    // Mientras está abierta, se vuelve a pedir cuando cambia la canción.
    LaunchedEffect(open, cur?.key) { if (open) queue = ns.queue(0, 60) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Cola", Modifier.weight(1f))
            Txt(if (open) "Ocultar" else "Ver toda", Modifier.tappable({ open = !open }).padding(6.dp).testTag("ns_queue_toggle"), Fs.type.smallStrong, c.accentText)
        }
        val s = ns.state
        if (!open) {
            val next = s?.next.orEmpty()
            if (next.isEmpty()) Txt("No hay más canciones después de esta.", Modifier.padding(top = 6.dp), Fs.type.small, c.textDim)
            for (t in next) TrackRow(t)
        } else {
            val q = queue
            if (q == null) Txt("Cargando…", Modifier.padding(top = 6.dp), Fs.type.small, c.textDim)
            else {
                Txt("${q.size} canciones", Modifier.padding(bottom = 4.dp), Fs.type.caption, c.textFaint)
                q.items.forEachIndexed { i, t ->
                    val idx = q.from + i
                    TrackRow(t, onClick = { runLink(app, scope) { ns.seekIndex(idx) } }, trailing = {
                        if (idx == q.index) Icon(Ic.Play, size = 16.dp, tint = c.accentText)
                    })
                }
                if (q.size > q.from + q.items.size) Txt("… y ${q.size - q.from - q.items.size} más", Modifier.padding(top = 4.dp), Fs.type.caption, c.textFaint)
            }
        }
    }
}

@Composable
private fun NsListsCard(app: AppState) {
    val c = Fs.colors
    val ns = app.connectors.namstat
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<NsPlaylists?>(null) }
    LaunchedEffect(ns.linkState) { if (ns.linkState == NsLinkState.Linked) data = ns.playlists() }
    Card {
        Label("Reproducir")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FsButton("Favoritas${data?.let { " (${it.favorites})" } ?: ""}", { runLink(app, scope) { ns.playFavorites() } }, Modifier.weight(1f).testTag("ns_play_favs"), kind = BtnKind.Secondary, icon = Ic.Heart, height = 44.dp)
            FsButton("Recientes", { runLink(app, scope) { ns.playRecent() } }, Modifier.weight(1f).testTag("ns_play_recent"), kind = BtnKind.Secondary, icon = Ic.Clock, height = 44.dp)
        }
        val items = data?.items.orEmpty()
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("Tus listas")
            for (p in items) {
                Row(
                    Modifier.fillMaxWidth().tappable({ runLink(app, scope) { ns.playPlaylist(id = p.id, name = p.name) } }, pressScale = 0.985f).padding(vertical = 8.dp).testTag("ns_list_${p.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(p.name, style = Fs.type.smallStrong, maxLines = 1)
                        Txt("${p.count} canciones · ${msClock(p.durationMs)}", style = Fs.type.caption, color = c.textDim, maxLines = 1)
                    }
                    Icon(Ic.Play, size = 18.dp, tint = c.accentText)
                }
            }
        } else if (data != null) {
            Txt("Todavía no tienes listas en NAMSTAT.", Modifier.padding(top = 8.dp), Fs.type.small, c.textDim)
        }
    }
}

@Composable
private fun NsSearchCard(app: AppState) {
    val c = Fs.colors
    val ns = app.connectors.namstat
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<NsSearch?>(null) }
    val go = { val text = q.trim(); if (text.isNotEmpty()) scope.launch { res = ns.search(text, 8) } }
    Card {
        Label("Buscar en tu biblioteca")
        Spacer(Modifier.height(8.dp))
        FsTextField(q, { q = it }, Modifier.testTag("ns_search_field"), placeholder = "Canción, artista o álbum", leading = Ic.Search, imeAction = androidx.compose.ui.text.input.ImeAction.Search, onDone = { go() })
        Spacer(Modifier.height(8.dp))
        FsButton("Buscar", { go() }, Modifier.fillMaxWidth().testTag("ns_search_go"), kind = BtnKind.Secondary, icon = Ic.Search, height = 44.dp)
        val r = res
        if (r != null) {
            Spacer(Modifier.height(8.dp))
            if (r.items.isEmpty()) Txt("No encontré nada con eso.", style = Fs.type.small, color = c.textDim)
            else {
                Txt("${r.total} resultados", style = Fs.type.caption, color = c.textFaint)
                for (t in r.items) TrackRow(t, onClick = { runLink(app, scope) { ns.playTrack(t.key) } }, trailing = {
                    Txt("Siguiente", Modifier.tappable({ runLink(app, scope) { ns.enqueue(key = t.key, next = true) } }).padding(6.dp), Fs.type.caption, c.accentText)
                })
                FsButton("Reproducir todo lo encontrado", { runLink(app, scope) { ns.playSearch(q.trim()) } }, Modifier.fillMaxWidth().padding(top = 6.dp), kind = BtnKind.Secondary, icon = Ic.Play, height = 44.dp)
            }
        }
    }
}

@Composable
private fun NsStatsCard(app: AppState) {
    val c = Fs.colors
    val ns = app.connectors.namstat
    var st by remember { mutableStateOf<NsStats?>(null) }
    LaunchedEffect(ns.linkState) { if (ns.linkState == NsLinkState.Linked) st = ns.stats() }
    val s = st ?: return
    Card {
        Label("Tu escucha en NAMSTAT")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NsFigure("Hoy", msClock(s.todayMs), Modifier.weight(1f))
            NsFigure("Semana", "%.1f h".format(s.weekHours), Modifier.weight(1f))
            NsFigure("Racha", "${s.streak} d", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Txt("${s.tracks} canciones · ${s.albums} álbumes · ${s.artists} artistas · ${s.favorites} favoritas · ${s.playlists} listas", style = Fs.type.caption, color = c.textDim, maxLines = 3)
        if (s.top.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Label("Lo más escuchado")
            for (t in s.top) TrackRow(t, trailing = { Txt("${t.plays}×", style = Fs.type.caption, color = c.accentText) })
        }
    }
}

@Composable
private fun NsFigure(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Fs.colors
    Column(modifier.clip(Fs.shapes.inner).background(c.surface2).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(value, style = Fs.type.h3, maxLines = 1)
        Txt(label, style = Fs.type.caption, color = c.textDim, maxLines = 1)
    }
}
