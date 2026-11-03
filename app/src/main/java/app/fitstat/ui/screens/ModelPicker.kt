package app.fitstat.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.bot.ModelInfo
import app.fitstat.ui.app.AppState
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.FsTextField
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.Pill
import app.fitstat.ui.design.RoundButton
import app.fitstat.ui.design.Segmented
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.Fonts
import app.fitstat.ui.theme.a
import kotlinx.coroutines.launch

/** Abre la hoja para elegir con qué modelo piensa FITBOT. */
fun AppState.openModelPicker(initialFlags: Set<String> = emptySet()) {
    overlays.sheet { close -> ModelPickerSheet(this, close, initialFlags) }
}

private val sortNames = listOf("Sugeridos", "Nuevos", "Baratos", "A–Z")

private val flagDefs = listOf(
    Triple("free", "Gratis", Ic.Gift),
    Triple("reasoning", "Razonan", Ic.Brain),
    Triple("images", "Imágenes", Ic.Image),
    Triple("audio", "Audio", Ic.Mic),
    Triple("files", "Archivos", Ic.File),
    Triple("tools", "Herramientas", Ic.Wrench),
)

internal fun filterModels(all: List<ModelInfo>, query: String, flags: Set<String>, sort: Int): List<ModelInfo> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val filtered = all.filter { m ->
        if ("free" in flags && !m.free) return@filter false
        if ("reasoning" in flags && !m.reasoning) return@filter false
        if ("images" in flags && !m.images) return@filter false
        if ("audio" in flags && !m.audio) return@filter false
        if ("files" in flags && !m.files) return@filter false
        if ("tools" in flags && !m.tools) return@filter false
        if (tokens.isEmpty()) true else {
            val hay = (m.name + " " + m.id).lowercase()
            tokens.all { it in hay }
        }
    }
    return when (sort) {
        1 -> filtered.sortedByDescending { it.created }
        2 -> filtered.sortedBy { (it.promptPerMillion ?: 9999.0) + (it.completionPerMillion ?: 9999.0) }
        3 -> filtered.sortedBy { it.name.lowercase() }
        else -> filtered.sortedWith(compareBy<ModelInfo> { rank(it) }.thenByDescending { it.created })
    }
}

/** Primero lo gratis o barato que sabe usar herramientas (lo que FITBOT necesita), luego el resto. */
private fun rank(m: ModelInfo): Int = when {
    !m.tools -> 3
    m.free -> 0
    (m.promptPerMillion ?: 99.0) <= 1.0 -> 1
    else -> 2
}

private fun ago(ts: Long): String {
    if (ts <= 0) return ""
    val min = ((System.currentTimeMillis() - ts) / 60_000L).coerceAtLeast(0)
    return when {
        min < 1 -> "ahora mismo"
        min < 60 -> "hace $min min"
        min < 60 * 24 -> "hace ${min / 60} h"
        else -> "hace ${min / (60 * 24)} d"
    }
}

/** Íconos de lo que admite un modelo: razonar, imágenes, audio, archivos y herramientas. */
@Composable
internal fun CapabilityIcons(m: ModelInfo, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = Fs.colors.accentText) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (m.reasoning) Icon(Ic.Brain, size = 15.dp, tint = tint)
        if (m.images) Icon(Ic.Image, size = 15.dp, tint = tint)
        if (m.audio) Icon(Ic.Mic, size = 15.dp, tint = tint)
        if (m.files) Icon(Ic.File, size = 15.dp, tint = tint)
        if (m.tools && m.known) Icon(Ic.Wrench, size = 15.dp, tint = tint)
    }
}

/** Botón-píldora que muestra el modelo actual. */
@Composable
internal fun ModelChip(m: ModelInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fs.colors
    Row(
        modifier.tappable(onClick, pressScale = 0.96f).clip(Fs.shapes.round).background(c.surface2).border(1.dp, c.outline.a(0.7f), Fs.shapes.round)
            .padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Ic.Cpu, size = 16.dp, tint = c.accentText)
        Txt(m.name, Modifier.weight(1f, fill = false), Fs.type.smallStrong, c.text, maxLines = 1)
        CapabilityIcons(m, tint = c.textDim)
        Icon(Ic.ChevronDown, size = 14.dp, tint = c.textDim)
    }
}

@Composable
fun ModelPickerSheet(app: AppState, close: () -> Unit, initialFlags: Set<String> = emptySet()) {
    val c = Fs.colors
    val m = Fs.motion
    val repo = app.repo
    val cat = app.models
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var flags by remember { mutableStateOf(initialFlags) }
    var sort by remember { mutableIntStateOf(0) }
    val current = repo.prefs.str("bot.model")

    LaunchedEffect(Unit) {
        if (cat.models.isEmpty()) cat.loadCached()
        if (cat.isStale) cat.refresh(app.keys.key.ifBlank { null })
    }

    val list = remember(cat.models, query, flags, sort) { filterModels(cat.models, query, flags, sort) }
    val typed = query.trim()
    val manual = typed.contains('/') && !typed.contains(' ') && cat.models.none { it.id == typed }

    fun choose(id: String, name: String) {
        repo.setSetting("bot.model", id)
        close()
        app.overlays.toast("FITBOT ahora usa $name", icon = Ic.Cpu)
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Txt("Modelo de FITBOT", style = Fs.type.h1, maxLines = 1)
                val status = when {
                    cat.loading && cat.models.isEmpty() -> "Descargando la lista de OpenRouter…"
                    cat.loading -> "Actualizando…"
                    cat.models.isEmpty() -> "Sin lista de modelos"
                    else -> "${list.size} de ${cat.models.size} modelos · ${ago(cat.updatedAt)}"
                }
                Txt(status, Modifier.padding(top = 2.dp), Fs.type.small, c.textDim, maxLines = 1)
            }
            val tr = rememberInfiniteTransition(label = "refresh")
            val spin by if (cat.loading && m.enabled) tr.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin") else remember { mutableStateOf(0f) }
            Box(Modifier.graphicsLayer { rotationZ = spin }) {
                RoundButton(Ic.Refresh, { if (!cat.loading) scope.launch { cat.refresh(app.keys.key.ifBlank { null }) } }, size = 42.dp)
            }
        }
        Box(Modifier.padding(top = 12.dp)) {
            FsTextField(
                query, { query = it }, placeholder = "Buscar o escribir un ID (openai/gpt-4o-mini)", leading = Ic.Search,
                trailing = if (query.isNotEmpty()) ({ Icon(Ic.Close, Modifier.tappable({ query = "" }), size = 18.dp, tint = c.textDim) }) else null,
            )
        }
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Todos", selected = flags.isEmpty(), onClick = { flags = emptySet() }, height = 32.dp)
            for ((id, label, ic) in flagDefs) {
                Pill(label, selected = id in flags, onClick = { flags = if (id in flags) flags - id else flags + id }, icon = ic, height = 32.dp)
            }
        }
        Box(Modifier.padding(top = 10.dp)) { Segmented(sortNames, sort, { sort = it }, height = 38.dp) }

        if (cat.error != null) {
            Txt(
                if (cat.models.isEmpty()) cat.error.orEmpty() else "No pude actualizar la lista (${cat.error}). Uso la última guardada.",
                Modifier.padding(top = 10.dp), Fs.type.small, c.warn,
            )
        }

        LazyColumn(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (manual) {
                item("manual") {
                    ModelRowShell(selected = current == typed, onClick = { choose(typed, typed) }) {
                        Txt("Usar «$typed»", style = Fs.type.bodyStrong, color = c.accentText, maxLines = 1)
                        Txt("ID escrito a mano. Si no existe, OpenRouter responderá con un error.", style = Fs.type.small, color = c.textDim)
                    }
                }
            }
            if (cat.models.none { it.id == current } && current.isNotBlank() && query.isBlank() && flags.isEmpty()) {
                item("current") {
                    ModelRowShell(selected = true, onClick = { close() }) {
                        Txt("Actual: $current", style = Fs.type.bodyStrong, color = c.accentText, maxLines = 1)
                        Txt("No está en la lista descargada.", style = Fs.type.small, color = c.textDim)
                    }
                }
            }
            items(list, key = { it.id }) { mi ->
                ModelRowShell(selected = mi.id == current, onClick = { choose(mi.id, mi.name) }) {
                    Txt(mi.name, style = Fs.type.bodyStrong, color = if (mi.id == current) c.accentText else c.text, maxLines = 1)
                    Txt(mi.id, style = Fs.type.caption.copy(fontFamily = Fonts.mono()), color = c.textFaint, maxLines = 1)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val price = mi.priceLabel
                        if (price.isNotEmpty()) Txt(price, style = Fs.type.caption, color = if (mi.free) c.good else c.textDim, maxLines = 1)
                        if (mi.contextLabel.isNotEmpty()) Txt(mi.contextLabel + " ctx", style = Fs.type.caption, color = c.textDim, maxLines = 1)
                        CapabilityIcons(mi)
                    }
                }
            }
            if (list.isEmpty() && !manual && !cat.loading) {
                item("empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt(
                            if (cat.models.isEmpty()) "No hay modelos para mostrar. Revisa tu conexión." else "Ningún modelo cumple esos filtros.",
                            style = Fs.type.small, color = c.textDim, align = TextAlign.Center,
                        )
                        if (cat.models.isEmpty()) FsButton("Reintentar", { scope.launch { cat.refresh(app.keys.key.ifBlank { null }) } }, kind = BtnKind.Secondary, height = 44.dp)
                    }
                }
            }
        }
        Txt(
            "Para enviar imágenes, archivos o audio, FITBOT necesita un modelo que los admita.",
            Modifier.padding(top = 10.dp), Fs.type.caption, c.textFaint, align = TextAlign.Center,
        )
    }
}

@Composable
private fun ModelRowShell(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick, pressScale = 0.985f).clip(Fs.shapes.inner)
            .background(if (selected) c.accent.a(0.16f) else c.surface2)
            .border(1.dp, if (selected) c.accent.a(0.7f) else c.outline.a(0.6f), Fs.shapes.inner)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) { content() }
        if (selected) Icon(Ic.Check, size = 20.dp, tint = c.accentText, style = "line", weight = 2.6f)
    }
}
