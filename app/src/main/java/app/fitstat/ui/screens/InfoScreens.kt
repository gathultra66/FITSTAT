package app.fitstat.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.ChangelogEntry
import app.fitstat.data.Changelog
import app.fitstat.data.Demo
import app.fitstat.data.Fmt
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.BotMood
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.Card
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.GroupCard
import app.fitstat.ui.design.HairLine
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.IconBadge
import app.fitstat.ui.design.Label
import app.fitstat.ui.design.LedBanner
import app.fitstat.ui.design.ScreenList
import app.fitstat.ui.design.StatTile
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.enter
import app.fitstat.ui.design.rememberMascotController
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ═════════════════════════════════════════════════════════════════════════════
// Registro de cambios
// ═════════════════════════════════════════════════════════════════════════════

/** Ajustes › Registro de cambios: cada versión de la app con lo que trajo. */
@Composable
fun ChangelogScreen() {
    val app = LocalApp.current
    val c = Fs.colors
    val entries = Changelog.entries
    var open by rememberSaveable { mutableIntStateOf(0) }
    val total = entries.sumOf { e -> e.sections.sumOf { it.second.size } }

    ScreenList("Registro de cambios", subtitle = "Versión actual ${Changelog.latest.version}", onBack = { app.nav.back() }) {
        item("hero") {
            Card(Modifier.enter(0), accent = true) {
                LedBanner("${Fs.skin.brand} V${Changelog.latest.version}", lit = c.onAccent.a(0.72f))
                Spacer(Modifier.height(12.dp))
                Txt(Changelog.latest.title, style = Fs.type.h1, color = c.onAccent, maxLines = 2)
                Txt(Changelog.latest.date, style = Fs.type.small, color = c.onAccentDim, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Txt(
                    "${Fmt.plural(entries.size, "versión", "versiones")} · ${Fmt.plural(total, "mejora", "mejoras")}",
                    style = Fs.type.caption, color = c.onAccentDim, maxLines = 1,
                )
            }
        }
        itemsIndexed(entries, key = { _, e -> "v" + e.version }) { i, e ->
            VersionCard(e, expanded = open == i, index = i + 1, onToggle = { open = if (open == i) -1 else i })
        }
        item("end") {
            Txt(
                "Aquí irá apareciendo cada actualización: 1.6, 1.7…",
                Modifier.fillMaxWidth().padding(vertical = 8.dp).enter(entries.size + 1),
                Fs.type.small, c.textFaint, align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VersionCard(e: ChangelogEntry, expanded: Boolean, index: Int, onToggle: () -> Unit) {
    val c = Fs.colors
    val m = Fs.motion
    val rot by animateFloatAsState(if (expanded) 180f else 0f, m.tween(280), label = "chevron")
    Card(Modifier.enter(index), onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clip(Fs.shapes.pill).background(c.accent).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Txt("v${e.version}", style = Fs.type.smallStrong, color = c.onAccent, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Txt(e.title, style = Fs.type.h3, maxLines = 1)
                Txt(e.date, style = Fs.type.small, color = c.textDim, maxLines = 1)
            }
            Icon(Ic.ChevronDown, Modifier.rotate(rot), size = 22.dp, tint = c.textDim)
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically(m.tween(320)) + fadeIn(m.tween(260)),
            exit = shrinkVertically(m.tween(260)) + fadeOut(m.tween(160)),
        ) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((title, items) in e.sections) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Label(title, color = c.accentText)
                        for (t in items) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(Fs.shapes.round).background(c.accent))
                                Txt(t, Modifier.weight(1f), Fs.type.small, c.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Acerca de
// ═════════════════════════════════════════════════════════════════════════════

private class Credit(val name: String, val license: String, val use: String)

private val credits = listOf(
    Credit("Lucide", "Licencia ISC", "Los íconos de toda la app"),
    Credit("free-exercise-db", "Dominio público (Unlicense)", "Fotos, descripciones y músculos de los ejercicios"),
    Credit("react-body-highlighter", "Licencia MIT", "La silueta del cuerpo para resaltar músculos"),
    Credit("Google Fonts", "Licencia OFL", "Inter, DM Sans, Manrope, Outfit, Sora, Lexend, Space Grotesk, Chakra Petch y JetBrains Mono"),
    Credit("Jetpack Compose y Kotlin", "Apache 2.0", "La base con la que está hecha la interfaz"),
    Credit("OpenRouter", "Servicio externo", "Conecta a FITBOT con el modelo de IA que elijas"),
)

/** Ajustes › Acerca de: la mascota, la versión, atajos y créditos. */
@Composable
fun AboutScreen() {
    val app = LocalApp.current
    val c = Fs.colors
    val bot = rememberMascotController()
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val mood = when {
        taps == 0 -> BotMood.Idle
        taps % 7 == 0 -> BotMood.Love
        else -> BotMood.Happy
    }

    ScreenList("Acerca de", onBack = { app.nav.back() }) {
        item("hero") {
            Card(Modifier.enter(0)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FitBot(size = 150.dp, mood = mood, controller = bot, onTap = { taps++; bot.poke() })
                    LedBanner(Fs.skin.brand, Modifier.fillMaxWidth().padding(top = 4.dp), lit = c.accentText)
                    Txt("Versión ${Changelog.latest.version}", style = Fs.type.bodyStrong, maxLines = 1)
                    Txt(
                        if (taps >= 7) "¡Cuidado, que me da cosquillas!" else "Toca a FITBOT, le encanta que lo saluden.",
                        style = Fs.type.small, color = c.textDim, align = TextAlign.Center,
                    )
                }
            }
        }
        item("what") {
            Card(Modifier.enter(1)) {
                Txt("Tu gym, en tu bolsillo", style = Fs.type.h3)
                Spacer(Modifier.height(6.dp))
                Txt(
                    "FITSTAT guarda tus series, rutinas, hitos y anotaciones del gym, te recuerda entrenar y deja que FITBOT, " +
                        "tu asistente, consulte y cambie la app por ti. Todo vive en este teléfono: sin cuentas, sin anuncios y sin rastreadores.",
                    style = Fs.type.small, color = c.textDim,
                )
            }
        }
        item("links") {
            Row(Modifier.enter(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FsButton("Registro de cambios", { app.nav.push(Route.Changelog) }, Modifier.weight(1f), icon = Ic.ClipboardList, height = 50.dp)
                FsButton("Mis datos", { app.nav.push(Route.DataTools) }, Modifier.weight(1f), icon = Ic.Database, height = 50.dp, kind = BtnKind.Secondary)
            }
        }
        item("credits") {
            GroupCard(Modifier.enter(3), title = "Créditos y licencias") {
                Column {
                    credits.forEachIndexed { i, cr ->
                        if (i > 0) HairLine(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.fillMaxWidth().padding(horizontal = Fs.ui.pad(16), vertical = Fs.ui.pad(12)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(cr.name, Modifier.weight(1f), Fs.type.bodyStrong, maxLines = 1)
                                Txt(cr.license, style = Fs.type.caption, color = c.accentText, maxLines = 1)
                            }
                            Txt(cr.use, style = Fs.type.small, color = c.textDim)
                        }
                    }
                }
            }
        }
        item("foot") {
            Txt(
                "Hecho con cariño para entrenar mejor. FITSTAT no da consejos médicos: si tienes una lesión o duda, consulta a un profesional.",
                Modifier.fillMaxWidth().padding(vertical = 6.dp).enter(4), Fs.type.caption, c.textFaint, align = TextAlign.Center,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Datos: copias de seguridad, importación y borrado
// ═════════════════════════════════════════════════════════════════════════════

private suspend fun writeText(ctx: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    }.getOrDefault(false)
}

private suspend fun readText(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val bytes = s.readBytes()
            if (bytes.size > 40_000_000) null else bytes.toString(Charsets.UTF_8)
        }
    }.getOrNull()
}

@Composable
private fun DataRow(ic: Ic, title: String, sub: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = Fs.colors
    val tint = if (danger) c.bad else c.accentText
    Row(
        Modifier.fillMaxWidth().tappable(onClick, pressScale = 0.99f).padding(horizontal = Fs.ui.pad(16), vertical = Fs.ui.pad(14)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(ic, size = 42.dp, tint = tint, bg = tint.a(0.14f))
        Column(Modifier.weight(1f)) {
            Txt(title, style = Fs.type.bodyStrong, color = if (danger) c.bad else c.text, maxLines = 1)
            Txt(sub, style = Fs.type.small, color = c.textDim, maxLines = 2)
        }
        Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
    }
}

/** Ajustes › Datos: exportar, importar, cargar ejemplos y borrar (todo con Deshacer). */
@Composable
fun DataScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Fs.colors
    val day = repo.today().toString()

    fun fail(text: String) = app.overlays.toast(text, icon = Ic.Alert)

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            if (writeText(ctx, uri, repo.exportJson())) app.overlays.toast("Copia guardada", icon = Ic.CircleCheck) else fail("No pude guardar el archivo.")
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            if (writeText(ctx, uri, repo.exportCsv())) app.overlays.toast("Series exportadas", icon = Ic.CircleCheck) else fail("No pude guardar el archivo.")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = readText(ctx, uri)
            if (text == null) {
                fail("No pude leer ese archivo.")
                return@launch
            }
            val go = {
                repo.importJson(text).fold(
                    onSuccess = { summary -> app.toastUndo("Copia importada: $summary") },
                    onFailure = { e -> fail(e.message ?: "No pude importar la copia.") },
                )
            }
            if (repo.hasUserData) {
                app.overlays.confirm(
                    "¿Reemplazar tus datos?",
                    "La copia sustituirá lo que tienes ahora. Podrás deshacerlo justo después.",
                    "Importar",
                ) { go() }
            } else go()
        }
    }

    ScreenList("Datos", subtitle = "Copias de seguridad y limpieza", onBack = { app.nav.back() }) {
        item("tiles") {
            Column(Modifier.enter(0), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Series", Fmt.group(repo.logs.size.toLong()), Modifier.weight(1f), accent = true, sub = "registradas")
                    StatTile("Rutinas", Fmt.group(repo.routines.size.toLong()), Modifier.weight(1f), sub = "creadas")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Anotaciones", Fmt.group(repo.notes.size.toLong()), Modifier.weight(1f), sub = "del gym")
                    StatTile("Hitos", Fmt.group(repo.unlocked.size.toLong()), Modifier.weight(1f), sub = "logrados")
                }
            }
        }
        item("backup") {
            GroupCard(Modifier.enter(1), title = "Copia de seguridad") {
                Column {
                    DataRow(Ic.Download, "Guardar copia completa", "Series, rutinas, notas y ajustes en un archivo", { saveJson.launch("fitstat-copia-$day.json") })
                    HairLine(Modifier.padding(horizontal = 16.dp))
                    DataRow(Ic.Table, "Exportar series (CSV)", "Para abrirlas en Excel u hojas de cálculo", { saveCsv.launch("fitstat-series-$day.csv") })
                    HairLine(Modifier.padding(horizontal = 16.dp))
                    DataRow(Ic.Upload, "Importar una copia", "Reemplaza tus datos con los de un archivo de FITSTAT", { open.launch(arrayOf("*/*")) })
                }
            }
        }
        if (!repo.hasUserData) item("sample") {
            Card(Modifier.enter(2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Ic.Sparkles, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Txt("¿Quieres ver la app llena?", style = Fs.type.h3, maxLines = 1)
                        Txt("Carga semanas de entrenamientos de ejemplo. Se pueden deshacer.", style = Fs.type.small, color = c.textDim)
                    }
                }
                Spacer(Modifier.height(12.dp))
                FsButton("Cargar datos de ejemplo", {
                    repo.replaceAll(Demo.build(repo.data, repo.now(), repo.zone, repo.formula), "Datos de ejemplo cargados")
                    app.toastUndo("Datos de ejemplo cargados")
                }, Modifier.fillMaxWidth(), icon = Ic.Sparkles, height = 48.dp)
            }
        }
        item("danger") {
            GroupCard(Modifier.enter(3), title = "Limpieza") {
                Column {
                    DataRow(Ic.Messages, "Borrar la conversación con FITBOT", "Quita los mensajes y archivos del chat", {
                        app.overlays.confirm("¿Borrar el chat?", "FITBOT olvidará esta conversación. Tus datos y ajustes no cambian.", "Borrar", danger = true) {
                            app.chat.clear()
                            app.overlays.toast("Chat borrado", icon = Ic.CircleCheck)
                        }
                    }, danger = true)
                    HairLine(Modifier.padding(horizontal = 16.dp))
                    DataRow(Ic.Eraser, "Borrar mis datos", "Series, rutinas y notas. Conserva tus ajustes", {
                        app.overlays.confirm("¿Borrar tus datos?", "Se irán tus series, rutinas, notas y hitos. Podrás deshacerlo justo después.", "Borrar", danger = true) {
                            repo.eraseAll(keepSettings = true)
                            app.toastUndo("Datos borrados")
                        }
                    }, danger = true)
                    HairLine(Modifier.padding(horizontal = 16.dp))
                    DataRow(Ic.Trash, "Borrar todo y restablecer", "Datos y personalización vuelven a como al inicio", {
                        app.overlays.confirm("¿Borrar absolutamente todo?", "También se restablecen los ajustes. Podrás deshacerlo justo después.", "Borrar todo", danger = true) {
                            repo.eraseAll(keepSettings = false)
                            app.toastUndo("Todo borrado")
                        }
                    }, danger = true)
                }
            }
        }
        item("foot") {
            Txt(
                "Todo se guarda solo en este teléfono. Las copias no incluyen tu clave de OpenRouter: tendrás que pegarla de nuevo si cambias de teléfono.",
                Modifier.fillMaxWidth().padding(vertical = 6.dp).enter(4), Fs.type.caption, c.textFaint, align = TextAlign.Center,
            )
        }
    }
}
