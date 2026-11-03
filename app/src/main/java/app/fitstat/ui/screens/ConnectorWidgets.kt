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
import app.fitstat.conn.MusicApps
import app.fitstat.conn.MusicState
import app.fitstat.conn.musicOpen
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

// ───────────────────────── Música (Namida, Spotify, YouTube Music)

/** Widget de música: portada y controles de la app que suena (Namida, Spotify o YouTube Music). Sin conectar, invita a conectar una. */
@Composable
internal fun MusicWidget(size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val c = Fs.colors
    val hub = app.connectors
    val status = hub.musicStatus()
    val s = hub.music.state
    val appName = hub.musicName()
    val anyOn = Connectors.all.any { it.id in MusicApps.ids && hub.enabled(it.id) }
    val connect = { app.nav.push(if (anyOn) Route.Connector(hub.musicCurrent()) else Route.SettingsPage(Pages.CONNECTORS)) }
    val openNamida = { if (!hub.platform.open(musicOpen(hub.musicCurrent()))) connect() }

    // Sin conectar, o falta algo: un atajo al conector.
    if (status != ConnStatus.Ready) {
        // En el tamaño mediano (media fila) no cabe el texto largo: ahí va corto y sin flecha.
        val compact = size == HomeSize.Medium
        val msg = when {
            compact -> if (anyOn) appName else "Música"
            status == ConnStatus.Off -> "Conecta tu música"
            status == ConnStatus.NeedsAccess -> "Falta un permiso"
            else -> "$appName no está instalada"
        }
        val sub = when (status) {
            ConnStatus.Off -> if (compact) "Toca para conectar" else "Controla tu música desde aquí"
            ConnStatus.NeedsAccess -> if (compact) "Falta un permiso" else "Toca para darlo"
            else -> if (compact) "No está instalada" else "Instálala para usarla"
        }
        if (size == HomeSize.Small) {
            Card(modifier.testTag("music_widget"), onClick = connect, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = 10.dp, vertical = Fs.ui.pad(12))) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    IconBadge(Ic.Music, size = 40.dp)
                    Txt("Música", style = Fs.type.smallStrong, maxLines = 1)
                    Txt(if (status == ConnStatus.Off) "Conectar" else "Revisar", style = Fs.type.caption, color = c.textDim, maxLines = 1)
                }
            }
        } else {
            Card(modifier.testTag("music_widget"), onClick = connect, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = Fs.ui.pad(14), vertical = Fs.ui.pad(12))) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Ic.Music, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Txt(msg, style = Fs.type.bodyStrong, maxLines = 1)
                        Txt(sub, style = Fs.type.caption, color = c.textDim, maxLines = 2)
                    }
                    if (!compact) Icon(Ic.ChevronRight, size = 20.dp, tint = c.textFaint)
                }
            }
        }
        return
    }

    // Conectada, pero la música no está sonando o no tiene sesión abierta.
    if (!s.connected || !s.hasTrack) {
        Card(modifier.testTag("music_widget"), onClick = openNamida, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = Fs.ui.pad(if (size == HomeSize.Small) 10 else 14), vertical = Fs.ui.pad(12))) {
            if (size == HomeSize.Small) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    IconBadge(Ic.Music, size = 40.dp)
                    Txt(appName, style = Fs.type.smallStrong, maxLines = 1)
                    Txt("Abrir", style = Fs.type.caption, color = c.textDim, maxLines = 1)
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Ic.Music, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Txt("Nada sonando", style = Fs.type.bodyStrong, maxLines = 1)
                        Txt("Abre $appName y dale play: aparecerá aquí.", style = Fs.type.caption, color = c.textDim, maxLines = 2)
                    }
                    Icon(Ic.External, size = 18.dp, tint = c.textFaint)
                }
            }
        }
        return
    }

    when (size) {
        HomeSize.Small -> Card(modifier.testTag("music_widget"), shape = Fs.shapes.inner, padding = PaddingValues(8.dp), behind = Modifier.musicFx("music")) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                Box(Modifier.tappable(openNamida, pressScale = 0.96f)) { MusicCover(s.art, 62.dp, Fs.shapes.chip, Modifier.coverShake()) }
                Txt(s.title, style = Fs.type.caption, color = c.text, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                Box(
                    Modifier.size(38.dp).tappable({ if (s.playing) hub.music.pause() else hub.music.play() }).clip(Fs.shapes.round).background(c.accent).testTag("music_toggle"),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (s.playing) Ic.Pause else Ic.Play, size = 20.dp, tint = c.onAccent) }
            }
        }
        HomeSize.Medium -> Card(modifier.testTag("music_widget"), shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(12)), behind = Modifier.musicFx("music")) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.tappable(openNamida, pressScale = 0.985f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MusicCover(s.art, 52.dp, Fs.shapes.chip, Modifier.coverShake())
                    Column(Modifier.weight(1f)) {
                        Txt(s.title, style = Fs.type.smallStrong, maxLines = 2)
                        Txt(s.artist.ifBlank { s.album }, style = Fs.type.caption, color = c.textDim, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                MusicControls(s, app, big = false, modifier = Modifier.fillMaxWidth())
            }
        }
        HomeSize.Large -> Card(modifier.testTag("music_widget"), shape = Fs.shapes.card, behind = Modifier.musicFx("music")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.tappable(openNamida, pressScale = 0.97f)) { MusicCover(s.art, 104.dp, Fs.shapes.inner, Modifier.coverShake()) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Label("${Connectors.def(s.source)?.name ?: appName} · ${if (s.playing) "sonando" else "en pausa"}")
                        Txt(s.title, style = Fs.type.h3, maxLines = 2)
                        Txt(s.artist.ifBlank { s.album }, style = Fs.type.small, color = c.textDim, maxLines = 1)
                    }
                    MusicControls(s, app, big = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ───────────────────────── Sueño

/** Widget de sueño: la última noche contra tu meta y la tendencia de la semana. */
@Composable
internal fun SleepWidget(size: HomeSize, modifier: Modifier) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val today = repo.today()
    val zone = repo.zone
    val goal = repo.sleepGoal
    val nights = remember(repo.sleep, zone) { repo.sleepNights() }
    val latest = remember(nights, today) { Sleep.latest(nights, today) }
    val open = { app.nav.push(Route.Sleep) }

    if (latest == null) {
        // Sin datos de anoche: invita a empezar (o dice que falta anoche).
        val hasAny = nights.isNotEmpty()
        if (size == HomeSize.Small) {
            Card(modifier.testTag("sleep_widget"), onClick = open, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = 10.dp, vertical = Fs.ui.pad(12))) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    IconBadge(Ic.Moon, size = 40.dp)
                    Txt("Sueño", style = Fs.type.smallStrong, maxLines = 1)
                    Txt(if (hasAny) "Sin anoche" else "Empezar", style = Fs.type.caption, color = c.textDim, maxLines = 1)
                }
            }
        } else {
            Card(modifier.testTag("sleep_widget"), onClick = open, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = Fs.ui.pad(14), vertical = Fs.ui.pad(12))) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Ic.Moon, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Txt("Sueño", style = Fs.type.bodyStrong, maxLines = 1)
                        Txt(
                            if (hasAny) "No tengo datos de anoche. Toca para ver tus noches." else "Conecta un reloj, estima con tu teléfono o registra una noche.",
                            style = Fs.type.caption, color = c.textDim, maxLines = 3,
                        )
                    }
                    Icon(Ic.ChevronRight, size = 20.dp, tint = c.textFaint)
                }
            }
        }
        return
    }

    val s = latest.main
    val asleep = Sleep.asleepMs(s)
    val goalMs = Sleep.goalMs(goal)
    val frac = (asleep.toFloat() / goalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val met = asleep >= goalMs
    when (size) {
        HomeSize.Small -> Card(modifier.testTag("sleep_widget"), onClick = open, shape = Fs.shapes.inner, padding = PaddingValues(horizontal = 12.dp, vertical = Fs.ui.pad(12))) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.Moon, size = 16.dp, tint = c.accentText)
                    Label("Sueño")
                }
                Column {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        BigNumber(Sleep.fmtShort(asleep), style = Fs.type.h1)
                        Txt("h", Modifier.padding(bottom = 3.dp), Fs.type.caption, c.textDim, maxLines = 1)
                    }
                    Txt(if (s.source == SleepSrc.PHONE) "estimado" else nightTitle(latest.date, today).lowercase(), style = Fs.type.caption, color = c.textDim, maxLines = 1)
                }
                ChunkyProgress(frac, height = 8.dp)
            }
        }
        HomeSize.Medium -> Card(modifier.testTag("sleep_widget"), onClick = open, shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(14))) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.Moon, size = 18.dp, tint = c.accentText)
                    Label(nightTitle(latest.date, today), Modifier.weight(1f))
                    if (s.source != SleepSrc.HEALTH) Txt(SleepSrc.short(s.source).lowercase(), style = Fs.type.caption, color = c.textFaint, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BigNumber(Sleep.fmtShort(asleep), style = Fs.type.title)
                    Txt("h", Modifier.padding(bottom = 4.dp), Fs.type.small, c.textDim, maxLines = 1)
                }
                ChunkyProgress(frac, height = 12.dp)
                Txt(
                    if (met) "¡Meta cumplida!" else "Meta ${Fmt.num(goal, 1)} h · ${Fmt.hourMinute(s.start, zone)} → ${Fmt.hourMinute(s.end, zone)}",
                    style = Fs.type.caption, color = if (met) c.good else c.textDim, maxLines = 1,
                )
            }
        }
        HomeSize.Large -> Card(modifier.testTag("sleep_widget"), onClick = open) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Moon, size = 18.dp, tint = c.accentText)
                Spacer(Modifier.width(6.dp))
                Label("Sueño · ${nightTitle(latest.date, today)}", Modifier.weight(1f))
                SourcePill(s)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigNumber(Sleep.fmtShort(asleep), style = Fs.type.display)
                Txt("h dormidas", Modifier.padding(bottom = 7.dp).weight(1f), Fs.type.body, c.textDim, maxLines = 1)
                Txt("${Fmt.hourMinute(s.start, zone)} → ${Fmt.hourMinute(s.end, zone)}", Modifier.padding(bottom = 7.dp), Fs.type.smallStrong, c.text, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            ChunkyProgress(frac, height = 14.dp)
            Txt(
                if (met) "¡Meta cumplida! (${Fmt.num(goal, 1)} h)" else "Faltaron ${Sleep.fmt(goalMs - asleep)} para tu meta de ${Fmt.num(goal, 1)} h",
                Modifier.padding(top = 6.dp), Fs.type.small, if (met) c.good else c.textDim, maxLines = 1,
            )
            if (Sleep.hasStages(s)) {
                Spacer(Modifier.height(12.dp))
                StagesBlock(s)
            }
            if (nights.size >= 2) {
                Spacer(Modifier.height(14.dp))
                NightBars(nights.take(7), goal, labels = true, height = 70.dp)
            }
        }
    }
}
