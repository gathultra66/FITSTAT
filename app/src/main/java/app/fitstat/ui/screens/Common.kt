package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.Palettes
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix

// ───────────────────────── Hojas

/** Estructura estándar de una hoja inferior: título, subtítulo y contenido que se desliza si es largo. */
@Composable
fun SheetBody(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Txt(title, style = Fs.type.h1, maxLines = 2)
        if (subtitle != null) Txt(subtitle, Modifier.padding(top = 2.dp), Fs.type.small, Fs.colors.textDim)
        Spacer(Modifier.height(14.dp))
        if (scroll) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        } else {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Hoja con una lista de opciones (una sola respuesta). */
fun Overlays.pick(title: String, options: List<Opt>, current: String, subtitle: String? = null, onPick: (String) -> Unit) {
    sheet { close ->
        SheetBody(title, subtitle = subtitle) {
            for (o in options) OptionRow(o.label, o.hint, o.value == current) { onPick(o.value); close() }
        }
    }
}

@Composable
fun OptionRow(label: String, hint: String = "", selected: Boolean, onClick: () -> Unit) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick, pressScale = 0.985f).clip(Fs.shapes.inner)
            .background(if (selected) c.accent.a(0.16f) else c.surface2)
            .border(1.dp, if (selected) c.accent.a(0.7f) else c.outline.a(0.6f), Fs.shapes.inner)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, style = Fs.type.bodyStrong, color = if (selected) c.accentText else c.text)
            if (hint.isNotEmpty()) Txt(hint, style = Fs.type.small, color = c.textDim)
        }
        if (selected) Icon(Ic.Check, size = 20.dp, tint = c.accentText, style = "line", weight = 2.6f)
    }
}

// ───────────────────────── Texto resaltado

fun highlighted(text: String, ranges: List<IntRange>, hi: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (r in ranges) {
        if (r.first >= text.length) break
        if (r.first > last) append(text.substring(last, r.first))
        val end = minOf(r.last + 1, text.length)
        withStyle(SpanStyle(color = hi, fontWeight = FontWeight.ExtraBold)) { append(text.substring(r.first, end)) }
        last = end
    }
    if (last < text.length) append(text.substring(last))
}

// ───────────────────────── Filas de ejercicio

@Composable
fun SetBadge(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val c = Fs.colors
    Box(modifier.size(size).clip(Fs.shapes.round).background(c.accent), contentAlignment = Alignment.Center) {
        Icon(Ic.Check, size = size * 0.6f, tint = c.onAccent, style = "line", weight = 3f)
    }
}

/** Fila de ejercicio: foto pequeña, nombre, descripción corta y una marca si está en tu set. */
@Composable
fun ExerciseRow(
    ex: Exercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    inSet: Boolean = false,
    showDesc: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    val c = Fs.colors
    val size = (58 * Fs.ui.thumbScale).dp
    Card(modifier, onClick = onClick, onLongClick = onLongClick, shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(10))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseThumb(ex, size, shape = Fs.shapes.chip)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val name = if (query.isBlank()) AnnotatedString(ex.name) else highlighted(ex.name, Search.highlight(ex.name, query), c.accentText)
                Txt(name, style = Fs.type.bodyStrong, maxLines = 1)
                if (subtitle != null) Txt(subtitle, style = Fs.type.small, color = c.accentText, maxLines = 1)
                if (showDesc) Txt(ex.description, style = Fs.type.small, color = c.textDim, maxLines = 2)
            }
            if (trailing != null) trailing() else if (inSet) SetBadge()
        }
    }
}

// ───────────────────────── Colores e íconos de rutina

val routineIcons: List<String> = listOf("Dumbbell", "Bolt", "Footprints", "Flame", "Zap", "Heart", "Trophy", "Target", "Bike", "Muscle", "Activity", "Mountain")

fun routineColor(idx: Int): Color = Palettes.all[((idx % Palettes.all.size) + Palettes.all.size) % Palettes.all.size].accent

// ───────────────────────── Selector de hora

@Composable
private fun TimePickerBody(h0: Int, m0: Int, close: () -> Unit, title: String, onPick: (Int, Int) -> Unit) {
    val c = Fs.colors
    var h by remember { mutableIntStateOf(h0) }
    var m by remember { mutableIntStateOf(m0) }
    SheetBody(title) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(Ic.ChevronUp, { h = (h + 1) % 24 }, size = 46.dp)
                Txt("%02d".format(h), Modifier.width(92.dp), Fs.type.display, align = androidx.compose.ui.text.style.TextAlign.Center)
                RoundButton(Ic.ChevronDown, { h = (h + 23) % 24 }, size = 46.dp)
            }
            Txt(":", Modifier.padding(horizontal = 4.dp), Fs.type.display, c.textFaint)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(Ic.ChevronUp, { m = (m + 5) % 60 }, size = 46.dp)
                Txt("%02d".format(m), Modifier.width(92.dp), Fs.type.display, align = androidx.compose.ui.text.style.TextAlign.Center)
                RoundButton(Ic.ChevronDown, { m = (m + 55) % 60 }, size = 46.dp)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, hh) in listOf("Mañana" to 7, "Mediodía" to 12, "Tarde" to 18, "Noche" to 20)) {
                Pill("$label ${"%02d".format(hh)}:00", selected = h == hh && m == 0, onClick = { h = hh; m = 0 })
            }
        }
        FsButton("Listo", { onPick(h, m); close() }, Modifier.fillMaxWidth())
    }
}

fun Overlays.pickTime(title: String, hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    sheet { close -> TimePickerBody(hour, minute, close, title, onPick) }
}

// ───────────────────────── Selector de ejercicios (varios a la vez)

@Composable
private fun ExercisePickerBody(title: String, exclude: Set<String>, actionLabel: String, close: () -> Unit, onDone: (List<String>) -> Unit) {
    val repo = LocalRepo.current
    val c = Fs.colors
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    val all = repo.exercises
    val list = remember(query, cat, all, exclude) {
        val base = if (query.isBlank()) all else repo.search(query, 80).map { it.ex }
        base.filter { it.id !in exclude && (cat == null || it.category == cat) }
    }
    val inSetIds = repo.setIds.toSet()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Txt(title, style = Fs.type.h1)
        Spacer(Modifier.height(12.dp))
        FsTextField(query, { query = it }, placeholder = "Buscar ejercicio…", leading = Ic.Search, imeAction = androidx.compose.ui.text.input.ImeAction.Search)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Todos", selected = cat == null, onClick = { cat = null })
            for (k in Categories.all) Pill(k.name, selected = cat == k.id, onClick = { cat = if (cat == k.id) null else k.id })
        }
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) {
            Txt("No hay ejercicios con ese filtro.", Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally), Fs.type.small, c.textDim)
        }
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { ex ->
                val on = ex.id in picked
                ExerciseRow(
                    ex, onClick = { if (on) picked.remove(ex.id) else picked.add(ex.id) }, query = query, showDesc = false,
                    subtitle = if (ex.id in inSetIds) "En tu set" else null,
                    trailing = {
                        Box(
                            Modifier.size(26.dp).clip(Fs.shapes.round).background(if (on) c.accent else Color.Transparent)
                                .border(2.dp, if (on) c.accent else c.outline, Fs.shapes.round),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Ic.Check, size = 16.dp, tint = c.onAccent, style = "line", weight = 3f) }
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FsButton(
            if (picked.isEmpty()) "Elige uno o más" else "$actionLabel (${picked.size})",
            { onDone(picked.toList()); close() }, Modifier.fillMaxWidth(), enabled = picked.isNotEmpty(),
        )
    }
}

fun Overlays.pickExercises(title: String, exclude: Set<String> = emptySet(), actionLabel: String = "Agregar", onDone: (List<String>) -> Unit) {
    sheet { close -> ExercisePickerBody(title, exclude, actionLabel, close, onDone) }
}

// ───────────────────────── Utilidades

fun Exercise.categoryName(): String = Categories.byId(category)?.name ?: category.replaceFirstChar { it.uppercase() }

/** Texto corto con los músculos principales: "Pecho · Hombros". */
fun Exercise.musclesText(): String = primary.joinToString(" · ") { it.label }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Label(text, modifier.padding(start = 6.dp, top = 6.dp), color = Fs.colors.textFaint)
}

@Composable
fun rememberToday(): java.time.LocalDate {
    val repo = LocalRepo.current
    return remember { repo.today() }
}

/** Mezcla un color de rutina con el fondo para usarlo como fondo suave. */
fun Color.soft(): Color = this.a(0.18f)

@Composable
fun Divider() = HairLine(Modifier.padding(horizontal = 16.dp))

/** Fila genérica de ajustes/listas: ícono, título, descripción y contenido a la derecha. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    desc: String? = null,
    icon: Ic? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    iconTint: Color = Fs.colors.accentText,
    iconBg: Color = Fs.colors.accentSoft,
    chevron: Boolean = onClick != null && trailing == null,
) {
    val c = Fs.colors
    Row(
        modifier.fillMaxWidth().tappable(onClick, pressScale = 0.99f).padding(horizontal = Fs.ui.pad(16), vertical = Fs.ui.pad(13)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconBadge(icon, size = 40.dp, tint = iconTint, bg = iconBg)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, style = Fs.type.bodyStrong, maxLines = 2)
            if (desc != null) Txt(desc, style = Fs.type.small, color = c.textDim, maxLines = 3)
        }
        if (trailing != null) trailing()
        if (chevron) Icon(Ic.ChevronRight, size = 20.dp, tint = c.textFaint)
    }
}
