package app.fitstat.ui.screens

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorDef
import app.fitstat.conn.Connectors
import app.fitstat.conn.MusicApps
import app.fitstat.conn.MusicState
import app.fitstat.conn.musicOpen
import app.fitstat.conn.HealthAvail
import app.fitstat.data.*
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.launch

// ───────────────────────── Lista de conectores (Ajustes › Conectores)

@Composable
internal fun ConnectorsSection() {
    val app = LocalApp.current
    val c = Fs.colors
    val hub = app.connectors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Ic.Plug, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Txt("Conecta tu entorno", style = Fs.type.h3)
                    Txt(
                        "Apps y servicios que FITSTAT puede usar para hacer más cosas. FITBOT conoce todos: pídele «conecta Spotify» o «apaga la música en los descansos» y lo hace por ti.",
                        Modifier.padding(top = 2.dp), Fs.type.small, c.textDim,
                    )
                }
            }
        }
        for ((i, g) in Connectors.all.groupBy { it.group }.entries.withIndex()) {
            Box(Modifier.enter(i + 1)) {
                GroupCard(title = g.key) {
                    Column {
                        g.value.forEachIndexed { j, d ->
                            if (j > 0) HairLine(Modifier.padding(start = 72.dp))
                            ConnectorRow(d, hub.status(d.id)) { app.nav.push(Route.Connector(d.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectorRow(d: ConnectorDef, status: ConnStatus, onClick: () -> Unit) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick, pressScale = 0.985f).padding(horizontal = 16.dp, vertical = 12.dp).testTag("conn_${d.id}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icOf(d.icon), size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(d.name, style = Fs.type.bodyStrong, maxLines = 1)
            Txt(d.tagline, style = Fs.type.small, color = c.textDim, maxLines = 2)
            StatusPill(status, Modifier.padding(top = 3.dp))
        }
        Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
    }
}

@Composable
internal fun StatusPill(status: ConnStatus, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val col = when (status) {
        ConnStatus.Ready -> c.good
        ConnStatus.NeedsAccess -> c.warn
        ConnStatus.Missing -> c.bad
        ConnStatus.Off -> c.textFaint
    }
    Row(
        modifier.clip(Fs.shapes.pill).background(col.a(0.16f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(Fs.shapes.round).background(col))
        Txt(status.label, style = Fs.type.caption, color = if (status == ConnStatus.Off) c.textDim else col, maxLines = 1)
    }
}

// ───────────────────────── Detalle de un conector

@Composable
fun ConnectorScreen(id: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val hub = app.connectors
    val d = Connectors.def(id)
    if (d == null) {
        ScreenList("Conector", onBack = { app.nav.back() }) { item("none") { EmptyState("No existe ese conector", "Vuelve a la lista de conectores.", actionLabel = "Ver conectores", onAction = { app.nav.back() }) } }
        return
    }
    val status = hub.status(id)
    val scope = rememberCoroutineScope()
    ScreenList(d.name, subtitle = d.tagline, onBack = { app.nav.back() }) {
        item("status") {
            Box(Modifier.enter(0)) {
                Card(accent = status == ConnStatus.Ready) {
                    val on = status == ConnStatus.Ready
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(icOf(d.icon), size = 48.dp, tint = if (on) c.onAccent else c.accentText, bg = if (on) c.onAccent.a(0.16f) else c.accentSoft)
                        Column(Modifier.weight(1f)) {
                            Txt(d.name, style = Fs.type.h2, color = if (on) c.onAccent else c.text, maxLines = 1)
                            if (on) Txt(status.label, style = Fs.type.smallStrong, color = c.onAccentDim) else StatusPill(status, Modifier.padding(top = 3.dp))
                        }
                    }
                    Txt(d.about, Modifier.padding(top = 12.dp), Fs.type.small, if (on) c.onAccentDim else c.textDim)
                    hub.missing(id)?.let { Txt(it, Modifier.padding(top = 10.dp), Fs.type.smallStrong, if (on) c.onAccent else c.warn) }
                    Spacer(Modifier.height(14.dp))
                    ConnectActions(d, status, hub, app)
                }
            }
        }
        when (id) {
            Connectors.NAMIDA, Connectors.SPOTIFY, Connectors.YTMUSIC -> musicItems(app, repo, status, id)
            Connectors.NAMSTAT -> {
                musicItems(app, repo, status, id, settings = false)
                namstatItems(app, repo, status)
            }
            Connectors.HEALTH -> sleepConnectorItems(app, repo, status, health = true)
            Connectors.PHONE -> sleepConnectorItems(app, repo, status, health = false)
        }
        item("bot") {
            Txt(
                "FITBOT puede conectar, desconectar y configurar este conector. Pídeselo en el chat.",
                Modifier.fillMaxWidth().padding(horizontal = 8.dp), Fs.type.caption, c.textFaint,
            )
        }
    }
}

@Composable
private fun ConnectActions(d: ConnectorDef, status: ConnStatus, hub: app.fitstat.conn.ConnectorHub, app: AppState) {
    val onAccent = status == ConnStatus.Ready
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (status) {
            ConnStatus.Off -> FsButton("Conectar", { hub.connect(d.id) }, Modifier.fillMaxWidth().testTag("conn_connect"), icon = Ic.Plug, height = 50.dp)
            ConnStatus.NeedsAccess -> {
                FsButton(
                    if (d.id == Connectors.HEALTH) "Dar permiso en Health Connect" else "Dar permiso",
                    { hub.requestAccess(d.id) }, Modifier.fillMaxWidth().testTag("conn_grant"), icon = Ic.Lock, height = 50.dp,
                )
                if (MusicApps.isMusic(d.id) && Build.VERSION.SDK_INT >= 33) RestrictedHint(app)
                if (d.id == Connectors.HEALTH) FsButton("Abrir Health Connect", { app.connectors.platform.open(ConnOpen.HealthSettings) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, height = 46.dp)
            }
            ConnStatus.Missing -> {
                if (d.id == Connectors.HEALTH && hub.snapshot.health == HealthAvail.NeedsInstall) {
                    FsButton("Instalar o actualizar Health Connect", { hub.platform.open(ConnOpen.HealthInstall) }, Modifier.fillMaxWidth(), icon = Ic.Download, height = 50.dp)
                }
            }
            ConnStatus.Ready -> {}
        }
        if (status != ConnStatus.Off) {
            FsButton(
                "Desconectar", { hub.disconnect(d.id); app.overlays.toast("${d.name} desconectado") },
                Modifier.fillMaxWidth().testTag("conn_disconnect"), kind = if (onAccent) BtnKind.Secondary else BtnKind.Secondary, height = 46.dp,
            )
        }
    }
}

@Composable
private fun RestrictedHint(app: AppState) {
    val c = Fs.colors
    Column(Modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.surface2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("¿Android dice «Ajuste restringido»?", style = Fs.type.smallStrong)
        Txt(
            "Pasa con apps instaladas fuera de Play Store. Entra a Ajustes › Apps › FITSTAT, toca ⋮ (arriba a la derecha) › «Permitir ajustes restringidos» y vuelve a intentarlo.",
            style = Fs.type.caption, color = c.textDim,
        )
        FsButton("Abrir ajustes de FITSTAT", { app.connectors.platform.open(ConnOpen.AppSettings) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, height = 40.dp)
    }
}

// ───────────────────────── Música (Namida, Spotify, YouTube Music)

private fun androidx.compose.foundation.lazy.LazyListScope.musicItems(app: AppState, repo: Repo, status: ConnStatus, id: String, settings: Boolean = true) {
    val name = Connectors.def(id)?.name ?: "la música"
    item("now") {
        Box(Modifier.enter(1)) { MusicNow(app, status, id) }
    }
    if (repo.prefs.bool("conn.$id.enabled") && settings) {
        item("settings") {
            Box(Modifier.enter(2)) {
                SettingsGroup("Qué hace con tus descansos", Settings.specsOf(Pages.CONNECTORS).filter { it.key.startsWith("conn.$id.") && it.key != "conn.$id.enabled" })
            }
        }
        item("widget") {
            Box(Modifier.enter(3)) { AddHomeWidgetCard("music", "Widget de música", "Pon en el inicio la carátula y los botones de play, pausa, siguiente y anterior.") }
        }
        item("notes") {
            val c = Fs.colors
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("Cómo funciona", style = Fs.type.smallStrong, color = c.textDim)
                Txt("• Solo se pausa la música que está sonando; si tú la habías pausado, no se toca.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Si le das play tú durante el descanso, FITSTAT no interviene.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Si mueves el volumen mientras sube, se respeta el que pusiste.", style = Fs.type.caption, color = c.textFaint)
                Txt("• $name debe estar abierta (puede ser en segundo plano): FITSTAT no la arranca.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Para ver qué suena, Android exige el «Acceso a notificaciones»; FITSTAT no lee ni guarda tus notificaciones.", style = Fs.type.caption, color = c.textFaint)
            }
        }
    }
}

@Composable
private fun MusicNow(app: AppState, status: ConnStatus, id: String) {
    val c = Fs.colors
    val name = Connectors.def(id)?.name ?: "la música"
    // Solo se muestra lo que suena si es de esta app (con varias conectadas, cada pantalla habla de la suya).
    val s = app.connectors.music.state.let { if (it.source.ifEmpty { Connectors.NAMIDA } == id) it else MusicState.None }
    Card {
        Label("Ahora en $name")
        Spacer(Modifier.height(10.dp))
        if (status != ConnStatus.Ready) {
            Txt("Conéctala para ver aquí lo que suena.", style = Fs.type.small, color = c.textDim)
        } else if (!s.connected || !s.hasTrack) {
            Txt("No veo música sonando. Abre $name y dale play: aparecerá aquí.", style = Fs.type.small, color = c.textDim)
            Spacer(Modifier.height(10.dp))
            FsButton("Abrir $name", { app.connectors.platform.open(musicOpen(id)) }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.External, height = 44.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicCover(s.art, 72.dp, Fs.shapes.inner)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(s.title, style = Fs.type.bodyStrong, maxLines = 2)
                    Txt(s.artist.ifBlank { s.album }, style = Fs.type.small, color = c.textDim, maxLines = 1)
                    Txt(if (s.playing) "Sonando" else "En pausa", style = Fs.type.caption, color = c.accentText, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            MusicControls(s, app, big = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Carátula de la canción (o un ícono si no hay). */
@Composable
internal fun MusicCover(art: Bitmap?, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val c = Fs.colors
    Box(modifier.size(size).clip(shape).background(c.accentSoft), contentAlignment = Alignment.Center) {
        if (art != null) {
            val img = remember(art) { art.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Ic.Music, size = size * 0.46f, tint = c.accentText)
        }
    }
}

/** Anterior, play o pausa, y siguiente. */
@Composable
internal fun MusicControls(s: MusicState, app: AppState, big: Boolean, modifier: Modifier = Modifier, tint: Color = Fs.colors.text) {
    val c = Fs.colors
    val m = app.connectors.music
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(Ic.SkipBack, { m.previous() }, Modifier.testTag("music_prev"), size = if (big) 48.dp else 44.dp, tint = tint)
        Box(
            Modifier.size(if (big) 60.dp else 54.dp).tappable({ if (s.playing) m.pause() else m.play() }).clip(Fs.shapes.round).background(c.accent).testTag("music_toggle"),
            contentAlignment = Alignment.Center,
        ) { Icon(if (s.playing) Ic.Pause else Ic.Play, size = if (big) 28.dp else 25.dp, tint = c.onAccent) }
        RoundButton(Ic.SkipForward, { m.next() }, Modifier.testTag("music_next"), size = if (big) 48.dp else 44.dp, tint = tint)
    }
}

// ───────────────────────── Poner un widget en el inicio

/** Agrega [widgetId] al inicio (si el usuario está editando el inicio, a la copia que edita). */
fun addHomeWidget(app: AppState, repo: Repo, widgetId: String): Boolean {
    if (repo.homeSlots.any { it.id == widgetId } && !app.home.active) return false
    if (app.home.active) return app.home.add(widgetId)
    repo.setHomeLayout(HomeLayout.add(repo.homeSlots, widgetId), "Widget agregado al inicio")
    return true
}

@Composable
internal fun AddHomeWidgetCard(widgetId: String, title: String, desc: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val def = HomeLayout.def(widgetId) ?: return
    val present = repo.homeSlots.any { it.id == widgetId }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icOf(def.icon), size = 42.dp)
            Column(Modifier.weight(1f)) {
                Txt(title, style = Fs.type.bodyStrong, maxLines = 1)
                Txt(desc, style = Fs.type.small, color = c.textDim, maxLines = 3)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (present) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ic.CircleCheck, size = 18.dp, tint = c.good)
                Txt("Ya está en tu inicio", Modifier.weight(1f), Fs.type.smallStrong, c.good, maxLines = 1)
                Txt("Ver", Modifier.tappable({ app.nav.switchTab(Route.Home) }).padding(8.dp), Fs.type.smallStrong, c.accentText)
            }
        } else {
            FsButton(
                "Poner en el inicio",
                { if (addHomeWidget(app, repo, widgetId)) app.toastUndo("${def.title} agregado al inicio") },
                Modifier.fillMaxWidth().testTag("add_widget_$widgetId"), kind = BtnKind.Secondary, icon = Ic.Plus, height = 46.dp,
            )
        }
    }
}

// ───────────────────────── Conectores de sueño (Health Connect y teléfono)

private fun androidx.compose.foundation.lazy.LazyListScope.sleepConnectorItems(app: AppState, repo: Repo, status: ConnStatus, health: Boolean) {
    item("sync") {
        Box(Modifier.enter(1)) { SleepSyncCard(app, repo, status, health) }
    }
    if (!health && repo.prefs.bool("conn.phone.enabled")) {
        item("gap") { Box(Modifier.enter(2)) { SettingsGroup("Estimación", Settings.specsOf(Pages.SLEEP).filter { it.key == "sleep.phone_gap" }) } }
    }
    item("widget") {
        Box(Modifier.enter(3)) { AddHomeWidgetCard("sleep", "Widget de sueño", "Tu última noche, la meta y la tendencia de la semana en el inicio.") }
    }
    item("limits") {
        val c = Fs.colors
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("Lo que sí y lo que no", style = Fs.type.smallStrong, color = c.textDim)
            if (health) {
                Txt("• Las fases (ligero, profundo, REM) dependen de tu reloj o pulsera: FITSTAT solo las lee, no las calcula.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Solo lee sueño. No escribe nada en Health Connect ni manda tus datos a ningún lado.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Sin reloj ni pulsera no habrá noches aquí: usa «Uso del teléfono» o regístralas a mano.", style = Fs.type.caption, color = c.textFaint)
            } else {
                Txt("• Es una estimación de horas, no una medición: no distingue fases ni si estabas despierto en la cama.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Si usaste el teléfono a medianoche, la noche puede salir más corta; si lo dejaste sin tocar despierto, más larga.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Android guarda unos 7 días de uso: abre FITSTAT seguido para no perder noches.", style = Fs.type.caption, color = c.textFaint)
                Txt("• Los datos se quedan en tu teléfono.", style = Fs.type.caption, color = c.textFaint)
            }
        }
    }
}

@Composable
private fun SleepSyncCard(app: AppState, repo: Repo, status: ConnStatus, health: Boolean) {
    val c = Fs.colors
    val hub = app.connectors
    val scope = rememberCoroutineScope()
    val mine = repo.sleep.count { it.source == if (health) SleepSrc.HEALTH else SleepSrc.PHONE }
    Card {
        Label("Datos de sueño")
        Spacer(Modifier.height(8.dp))
        Txt(
            when {
                status != ConnStatus.Ready -> "Cuando esté conectado, aquí verás cuándo se actualizó por última vez."
                mine == 0 && hub.lastSync == 0L -> "Todavía no se ha actualizado."
                else -> "${Fmt.plural(mine, "noche guardada", "noches guardadas")}" + if (hub.lastSync > 0) " · Actualizado a las ${Fmt.hourMinute(hub.lastSync, repo.zone)}" else ""
            },
            style = Fs.type.small, color = c.textDim,
        )
        if (hub.lastSyncMessage.isNotBlank() && status == ConnStatus.Ready) Txt(hub.lastSyncMessage, Modifier.padding(top = 4.dp), Fs.type.caption, c.textFaint)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FsButton(
                if (hub.syncing) "Actualizando…" else if (health) "Sincronizar" else "Estimar",
                {
                    scope.launch {
                        val r = hub.syncSleep()
                        app.overlays.toast(if (r.ok) (if (r.newNights > 0) "${Fmt.plural(r.newNights, "noche nueva", "noches nuevas")}" else "Todo al día") else r.message.take(80))
                    }
                },
                Modifier.weight(1f).testTag("conn_sync"), enabled = status == ConnStatus.Ready && !hub.syncing, icon = Ic.Refresh, height = 46.dp,
            )
            FsButton("Ver mi sueño", { app.nav.push(Route.Sleep) }, Modifier.weight(1f), kind = BtnKind.Secondary, icon = Ic.Moon, height = 46.dp)
        }
    }
}
