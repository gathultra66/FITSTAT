package app.fitstat.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fitstat.bot.OpenRouterException
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.Card
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.FsTextField
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.IconBadge
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fonts
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.launch

/** Parte superior de Ajustes › FITBOT y API: la clave de OpenRouter y el modelo. */
@Composable
internal fun BotApiSection() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keys = app.keys
    val cat = app.models
    var text by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val model = app.bot.model

    LaunchedEffect(Unit) {
        if (cat.models.isEmpty()) cat.loadCached()
        if (cat.isStale) cat.refresh(keys.key.ifBlank { null })
    }

    fun check() {
        if (checking || !keys.hasKey) return
        checking = true
        result = null
        scope.launch {
            result = try {
                true to app.api.checkKey(keys.key)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OpenRouterException) {
                false to (e.message ?: "OpenRouter rechazó la clave.")
            } catch (e: Throwable) {
                false to (e.message ?: "No pude comprobar la clave.")
            }
            checking = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Fs.ui.pad(14))) {
        // ── Clave
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(Ic.Key, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Txt("Clave de OpenRouter", style = Fs.type.h3, maxLines = 1)
                    Txt(
                        if (keys.hasKey) "Guardada: ${keys.masked()}" else "Aún no hay clave",
                        style = Fs.type.small.copy(fontFamily = if (keys.hasKey) Fonts.mono() else Fs.type.small.fontFamily),
                        color = if (keys.hasKey) c.good else c.warn, maxLines = 1,
                    )
                }
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FsTextField(
                    text, { text = it; result = null },
                    placeholder = if (keys.hasKey) "Pega una clave nueva para reemplazarla" else "sk-or-v1-…",
                    leading = Ic.Lock, password = !reveal,
                    trailing = { Icon(if (reveal) Ic.EyeOff else Ic.Eye, Modifier.tappable({ reveal = !reveal }), size = 20.dp, tint = c.textDim) },
                    onDone = {
                        if (text.isNotBlank()) {
                            keys.save(text)
                            text = ""
                            check()
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (text.isNotBlank()) {
                        FsButton("Guardar y probar", { keys.save(text); text = ""; check() }, Modifier.weight(1f), icon = Ic.Check, height = 48.dp)
                    } else {
                        FsButton(
                            if (checking) "Probando…" else "Probar clave", { check() }, Modifier.weight(1f), kind = BtnKind.Secondary,
                            icon = Ic.Zap, enabled = keys.hasKey && !checking, height = 48.dp,
                        )
                    }
                    if (keys.hasKey) {
                        FsButton("Borrar", {
                            app.overlays.confirm("¿Borrar la clave?", "FITBOT dejará de responder hasta que pegues otra.", "Borrar", danger = true) {
                                keys.clear()
                                result = null
                            }
                        }, kind = BtnKind.Danger, icon = Ic.Trash, height = 48.dp)
                    }
                }
                AnimatedVisibility(result != null) {
                    val r = result
                    if (r != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(Fs.shapes.inner).background((if (r.first) c.good else c.bad).a(0.12f)).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
                        ) {
                            Icon(if (r.first) Ic.CircleCheck else Ic.Alert, size = 20.dp, tint = if (r.first) c.good else c.bad)
                            Txt(r.second, style = Fs.type.small, color = c.text)
                        }
                    }
                }
                Txt(
                    "La clave se guarda cifrada solo en este teléfono. No sale en las copias de seguridad y FITBOT no puede verla.",
                    style = Fs.type.caption, color = c.textFaint,
                )
                Row(
                    Modifier.tappable({
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/keys")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, pressScale = 0.97f).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Ic.External, size = 15.dp, tint = c.accentText)
                    Txt("Conseguir una clave en openrouter.ai/keys", style = Fs.type.smallStrong, color = c.accentText, maxLines = 1)
                }
            }
        }

        // ── Modelo
        Card(Modifier.animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(Ic.Cpu, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Txt("Modelo de FITBOT", style = Fs.type.h3, maxLines = 1)
                    Txt("Es el cerebro del asistente", style = Fs.type.small, color = c.textDim, maxLines = 1)
                }
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModelChip(model, { app.openModelPicker() }, Modifier.fillMaxWidth())
                Txt(model.id, style = Fs.type.caption.copy(fontFamily = Fonts.mono()), color = c.textFaint, maxLines = 1)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapChip(Ic.Image, "Imágenes", model.images)
                    CapChip(Ic.Mic, "Audio", model.audio)
                    CapChip(Ic.File, "Archivos", model.files)
                    CapChip(Ic.Brain, "Razona", model.reasoning)
                    CapChip(Ic.Wrench, "Herramientas", model.tools)
                }
                val price = model.priceLabel
                if (price.isNotEmpty() || model.contextLabel.isNotEmpty()) {
                    Txt(
                        listOfNotNull(price.takeIf { it.isNotEmpty() }?.let { if (it == "Gratis") it else "$it por millón de tokens" }, model.contextLabel.takeIf { it.isNotEmpty() }?.let { "$it de contexto" })
                            .joinToString(" · "),
                        style = Fs.type.small, color = c.textDim,
                    )
                }
                if (!model.known) {
                    Txt("Este modelo no aparece en la lista descargada. Se intentará usar igual.", style = Fs.type.small, color = c.warn)
                } else if (!model.tools) {
                    Txt("Este modelo no usa herramientas: FITBOT podrá conversar, pero no podrá cambiar ni consultar tu app.", style = Fs.type.small, color = c.warn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FsButton("Elegir modelo", { app.openModelPicker() }, Modifier.weight(1f), icon = Ic.Search, height = 48.dp)
                    if (repo.prefs.isCustom("bot.model")) {
                        FsButton("Recomendado", { repo.resetSetting("bot.model") }, kind = BtnKind.Secondary, icon = Ic.RotateCcw, height = 48.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapChip(ic: Ic, label: String, on: Boolean) {
    val c = Fs.colors
    Row(
        Modifier.clip(Fs.shapes.pill).background(if (on) c.good.a(0.14f) else c.surface3).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (on) ic else Ic.Close, size = 14.dp, tint = if (on) c.good else c.textFaint, style = "line", weight = 2.4f)
        Txt(label, style = Fs.type.caption, color = if (on) c.text else c.textFaint, maxLines = 1)
    }
}
