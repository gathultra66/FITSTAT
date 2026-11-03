package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.fitstat.data.Prefs
import app.fitstat.data.SkinInfo
import app.fitstat.data.Skins
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.design.AppBackground
import app.fitstat.ui.design.BotMood
import app.fitstat.ui.design.Card
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.Label
import app.fitstat.ui.design.SkinLogo
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.FitstatTheme
import app.fitstat.ui.theme.Palettes
import app.fitstat.ui.theme.a

/** Galería de skins: cada una se ve con su propio diseño. Tocar una la activa al instante. */
@Composable
internal fun SkinsSection() {
    val repo = LocalRepo.current
    val active = Skins.byId(repo.prefs.str("skin.id"))
    Column(verticalArrangement = Arrangement.spacedBy(Fs.ui.pad(12))) {
        for (info in Skins.all) {
            SkinTile(info, selected = info.id == active.id) { if (info.id != active.id) repo.setSetting("skin.id", info.id) }
        }
        if (active.id != Skins.DEFAULT) SkinIdCard(active)
    }
}

@Composable
private fun SkinTile(info: SkinInfo, selected: Boolean, onSelect: () -> Unit) {
    val base = LocalRepo.current.prefs
    // La ficha se dibuja con el tema de esa skin (y con tus personalizaciones de ella).
    val prefs = remember(base.raw, info.id) { Prefs(base.raw + ("skin.id" to info.id)) }
    FitstatTheme(prefs) {
        val c = Fs.colors
        val shape = Fs.shapes.card
        Box(
            Modifier.fillMaxWidth().clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline, shape)
                .tappable(onSelect, pressScale = 0.985f),
        ) {
            AppBackground(Modifier.height(138.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(138.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FitBot(size = 96.dp, mood = BotMood.Idle, animated = selected)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (info.id != Skins.DEFAULT) SkinLogo(info.id, size = 26.dp)
                            Txt(info.name, Modifier.weight(1f, fill = false), Fs.type.h2, maxLines = 1, upper = Fs.skin.caps)
                            if (selected) {
                                Box(Modifier.size(22.dp).clip(Fs.shapes.round).background(c.accent), contentAlignment = Alignment.Center) {
                                    Icon(Ic.Check, size = 14.dp, tint = c.onAccent)
                                }
                            }
                        }
                        Txt(info.tagline, style = Fs.type.small, color = c.textDim, maxLines = 3)
                        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val dots = if (info.accents.isEmpty()) Palettes.all.take(5).map { it.accent } else info.accents.map { androidx.compose.ui.graphics.Color(it.a1) }
                            for (col in dots) Box(Modifier.size(12.dp).clip(Fs.shapes.round).background(col))
                        }
                    }
                }
            }
        }
    }
}

/** Ficha de la skin activa: logo, mascota y división. */
@Composable
private fun SkinIdCard(info: SkinInfo) {
    val c = Fs.colors
    val prefs = LocalRepo.current.prefs
    Card(accent = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SkinLogo(info.id, size = 64.dp, color = c.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(info.brand, style = Fs.type.h2, maxLines = 1)
                Label(info.unit)
                Txt("Mascota: ${info.callsign.lowercase().replaceFirstChar { it.uppercase() }}", Modifier.padding(top = 4.dp), Fs.type.small, c.textDim, maxLines = 1)
                Txt(
                    if (prefs.bool("skin.icon_sync")) "Ícono de la app: el de esta skin" else "Ícono de la app: el que elegiste",
                    style = Fs.type.small, color = c.textDim, maxLines = 1,
                )
            }
        }
    }
}

/** Aviso en Tema, Formas, Tipografía y Animaciones cuando una skin los está controlando. */
@Composable
internal fun SkinNote() {
    val sk = Fs.skin
    if (sk.isDefault) return
    val c = Fs.colors
    Card(color = c.accentSoft) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Ic.Layers, size = 22.dp, tint = c.accentText)
            Txt(
                "La skin ${sk.info.name} controla estos ajustes (siempre en modo oscuro). Para usar los tuyos, elige «FITSTAT» en Skins.",
                Modifier.weight(1f), Fs.type.small, c.text,
            )
        }
    }
}
