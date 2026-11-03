package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.conn.ConnOpen
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.Connectors
import app.fitstat.conn.MusicApps
import app.fitstat.conn.MusicState
import app.fitstat.conn.musicOpen
import app.fitstat.conn.RhythmMode
import app.fitstat.conn.RhythmWhy
import app.fitstat.data.Pages
import app.fitstat.data.Settings
import app.fitstat.data.SType
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs

/** Nombre de un estilo de animación (para mostrarlo). */
internal fun fxStyleLabel(id: String): String =
    (Settings.FX_STYLES.options.firstOrNull { it.value == id }?.label) ?: id

/**
 * Arriba de Ajustes › Animaciones de música: una vista previa en vivo con todos los estilos, de dónde sale el ritmo
 * (audio real o aproximado, dicho con claridad) y el permiso de audio.
 */
@Composable
internal fun MusicFxSection() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val eng = app.rhythm
    val on = repo.prefs.bool("fx.enabled")
    var style by rememberSaveable { mutableStateOf(repo.prefs.str("fx.goal.style")) }
    val musicOn = app.connectors.musicReady()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.testTag("fx_preview_card"), padding = PaddingValues(Fs.ui.pad(14))) {
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(Fs.shapes.inner).background(c.surface2).testTag("fx_preview")
                    .then(if (on) Modifier.musicFx("goal", styleOverride = style) else Modifier),
                contentAlignment = Alignment.BottomStart,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.Music, size = 16.dp, tint = c.textDim)
                    Txt(if (on) "Vista previa · ${fxStyleLabel(style)}" else "Las animaciones están apagadas", style = Fs.type.smallStrong, color = c.textDim, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in Settings.FX_STYLES.options) {
                    Pill(o.label, Modifier.testTag("fx_style_${o.value}"), selected = style == o.value, onClick = { style = o.value })
                }
            }
            val hint = Settings.FX_STYLES.options.firstOrNull { it.value == style }?.hint.orEmpty()
            if (hint.isNotBlank()) Txt(hint, Modifier.padding(top = 8.dp), Fs.type.caption, c.textDim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FsButton(
                    "En la meta", { repo.setSetting("fx.goal.style", style); app.toastUndo("Meta semanal: ${fxStyleLabel(style)}") },
                    Modifier.weight(1f).testTag("fx_use_goal"), kind = BtnKind.Secondary, height = 44.dp,
                )
                FsButton(
                    "En la música", { repo.setSetting("fx.music.style", style); app.toastUndo("Widget de música: ${fxStyleLabel(style)}") },
                    Modifier.weight(1f).testTag("fx_use_music"), kind = BtnKind.Secondary, height = 44.dp,
                )
            }
        }

        // De dónde sale el ritmo.
        Card(Modifier.testTag("fx_source_card"), padding = PaddingValues(Fs.ui.pad(14))) {
            val (title, tone) = when {
                !on -> "Apagadas" to c.textDim
                eng.mode == RhythmMode.Real -> "Ritmo real: leyendo el audio del teléfono" to c.good
                eng.mode == RhythmMode.Approx && eng.why == RhythmWhy.None -> "Ritmo aproximado (vista previa)" to c.accentText
                eng.mode == RhythmMode.Approx -> "Ritmo aproximado" to c.warn
                else -> "Esperando música" to c.textDim
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ic.Activity, size = 18.dp, tint = tone)
                Txt(title, Modifier.weight(1f).testTag("fx_mode"), Fs.type.bodyStrong, tone, maxLines = 2)
            }
            if (on && eng.mode == RhythmMode.Approx && eng.why != RhythmWhy.None) {
                Txt(eng.why.label, Modifier.padding(top = 4.dp), Fs.type.small, c.textDim)
            }
            Txt(
                "Aproximado: late a un tempo fijo (lo eliges abajo) y no sigue la canción exacta. " +
                    "Real: mide qué tan fuerte suena cada rango de frecuencias del audio del teléfono; no graba ni guarda nada. " +
                    "Android no siempre deja leer el audio de otra app: si no se puede, se usa el aproximado.",
                Modifier.padding(top = 8.dp), Fs.type.caption, c.textDim,
            )
            if (!musicOn) {
                Spacer(Modifier.height(10.dp))
                Txt("Para verlas con tu música, conecta Namida, Spotify o YouTube Music. La vista previa funciona siempre.", style = Fs.type.small, color = c.textDim)
                Spacer(Modifier.height(8.dp))
                FsButton("Ir a Conectores", { app.nav.push(Route.SettingsPage(Pages.CONNECTORS)) }, Modifier.fillMaxWidth().testTag("fx_goto_namida"), kind = BtnKind.Secondary, icon = Ic.Plug, height = 44.dp)
            }
            if (eng.supportsReal && repo.prefs.str("fx.source") != "approx") {
                Spacer(Modifier.height(10.dp))
                if (eng.permission) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Ic.CircleCheck, size = 18.dp, tint = c.good)
                        Txt("Permiso de audio concedido", Modifier.testTag("fx_perm_ok"), Fs.type.smallStrong, c.good)
                    }
                } else {
                    FsButton(
                        "Dar permiso de audio", { app.audioPermissionRequest?.invoke() ?: app.connectors.platform.open(ConnOpen.AppSettings) },
                        Modifier.fillMaxWidth().testTag("fx_permission"), icon = Ic.Mic, height = 46.dp,
                    )
                    Txt("Sin él se usa el ritmo aproximado. Si el sistema ya no te lo pregunta, concédelo en los ajustes de la app.", Modifier.padding(top = 6.dp), Fs.type.caption, c.textFaint, align = TextAlign.Start)
                }
            }
        }
    }
}
