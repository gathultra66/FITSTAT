package app.fitstat.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

// ───────────────────────── Acciones comunes sobre el set

/** Agrega o quita un ejercicio de "mi set" y avisa con la opción de deshacer. */
fun AppState.toggleSet(ex: Exercise) {
    if (repo.inSet(ex.id)) {
        repo.removeFromSet(ex.id)
        toastUndo("«${ex.name}» salió de tu set")
    } else {
        repo.addToSet(ex.id)
        overlays.toast("«${ex.name}» ya está en tu set", "Deshacer", Ic.Check) { repo.undo() }
    }
}

/** Botón redondo "+ / ✓" para meter o sacar un ejercicio de tu set. */
@Composable
fun SetQuickToggle(inSet: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val bg by animateColorAsState(if (inSet) c.accent else c.surface3, Fs.motion.tween(200), label = "sqtBg")
    Box(
        modifier.size(36.dp).clip(Fs.shapes.round).background(bg).tappable(onClick, pressScale = 0.88f),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(inSet, animationSpec = Fs.motion.tween(180), label = "sqtIcon") { on ->
            if (on) Icon(Ic.Check, size = 19.dp, tint = c.onAccent, style = "line", weight = 3f)
            else Icon(Ic.Plus, size = 19.dp, tint = c.textDim, style = "line", weight = 2.6f)
        }
    }
}

/** Barra de búsqueda falsa: al tocarla se abre el buscador con el teclado. */
@Composable
fun SearchBarButton(modifier: Modifier = Modifier, placeholder: String = "Buscar ejercicio…", hint: String? = null, onClick: () -> Unit) {
    val c = Fs.colors
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(Fs.shapes.card).background(c.surface)
            .border(1.dp, c.outline.a(0.6f), Fs.shapes.card).tappable(onClick, pressScale = 0.985f).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ic.Search, size = 22.dp, tint = c.accentText)
        Txt(placeholder, Modifier.weight(1f), Fs.type.body, c.textFaint, maxLines = 1)
        if (hint != null) Txt(hint, style = Fs.type.caption, color = c.textFaint, maxLines = 1)
    }
}

// ───────────────────────── Pestaña Ejercicios

/**
 * Ejercicios: el buscador, Mi set, una tarjeta por categoría y el atajo para crear. Cada bloque se puede mover, achicar,
 * agrandar o quitar (mantén pulsado un bloque o la pantalla), y se pueden agregar widgets del inicio.
 */
@Composable
fun ExercisesScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val exercises = repo.exercises
    val configs = repo.configs
    val setEx = remember(configs, exercises) { exercises.filter { configs[it.id]?.inSet == true } }
    val counts = remember(exercises) { exercises.groupingBy { it.category }.eachCount() }
    val setCounts = remember(setEx) { setEx.groupingBy { it.category }.eachCount() }

    EditablePage(
        editor = app.exercisesLayout,
        title = "Ejercicios",
        subtitle = "${exercises.size} ejercicios · ${Categories.all.size} categorías",
        actions = {
            RoundButton(Ic.Search, { nav.push(Route.Search()) })
            RoundButton(Ic.Plus, { app.overlays.editExercise(onSaved = { nav.push(Route.Exercise(it.id)) }) })
        },
    ) { id, size, mod ->
        when {
            id == "ex_search" -> SearchBarButton(mod, hint = "${exercises.size}") { nav.push(Route.Search()) }
            id == "ex_set" -> MySetCard(setEx, size, mod) { nav.push(Route.MySet) }
            id == "ex_create" -> CreateExerciseCard(size, mod) { app.overlays.editExercise(onSaved = { nav.push(Route.Exercise(it.id)) }) }
            id.startsWith(HomeLayout.CAT_PREFIX) -> {
                val cat = Categories.byId(id.removePrefix(HomeLayout.CAT_PREFIX))
                if (cat != null) CategoryCard(cat, counts[cat.id] ?: 0, setCounts[cat.id] ?: 0, size, mod) { nav.push(Route.Category(cat.id)) }
            }
        }
    }
}

@Composable
private fun CreateExerciseCard(size: HomeSize, modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    if (size == HomeSize.Large) {
        Card(modifier, onClick = onClick, shape = Fs.shapes.inner) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(Ic.Plus, size = 46.dp)
                Column(Modifier.weight(1f)) {
                    Txt("¿No está el que buscas?", style = Fs.type.bodyStrong)
                    Txt("Crea tu propia plantilla de ejercicio con foto, músculos y notas.", style = Fs.type.small, color = c.textDim)
                }
                Icon(Ic.ChevronRight, size = 20.dp, tint = c.textFaint)
            }
        }
    } else {
        Card(modifier, onClick = onClick, shape = Fs.shapes.inner, padding = PaddingValues(Fs.ui.pad(14))) {
            IconBadge(Ic.Plus, size = 40.dp)
            Spacer(Modifier.height(8.dp))
            Txt("Crear ejercicio", style = Fs.type.bodyStrong, maxLines = 1)
            Txt("Tu propia plantilla, con foto y músculos.", style = Fs.type.caption, color = c.textDim, maxLines = 3)
        }
    }
}

@Composable
private fun MySetCard(setEx: List<Exercise>, size: HomeSize, modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    val sub = if (setEx.isEmpty()) "Aún no agregas ejercicios. Toca para empezar." else "${Fmt.plural(setEx.size, "ejercicio", "ejercicios")} que haces seguido"
    if (size == HomeSize.Large) {
        Card(modifier, onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Txt("Mi set", style = Fs.type.h2)
                    Txt(sub, style = Fs.type.small, color = c.textDim, maxLines = 2)
                }
                if (setEx.isNotEmpty()) PhotoStack(setEx, size = 44.dp, max = 3)
                else IconBadge(Ic.ListPlus, size = 48.dp)
            }
        }
    } else {
        Card(modifier, onClick = onClick, padding = PaddingValues(Fs.ui.pad(14))) {
            Txt("Mi set", style = Fs.type.h2, maxLines = 1)
            Txt(sub, style = Fs.type.caption, color = c.textDim, maxLines = 3)
            Spacer(Modifier.weight(1f, fill = true))
            Spacer(Modifier.height(8.dp))
            if (setEx.isNotEmpty()) PhotoStack(setEx, size = 36.dp, max = 3)
            else IconBadge(Ic.ListPlus, size = 40.dp)
        }
    }
}

@Composable
private fun CategoryCard(cat: Category, total: Int, inSet: Int, size: HomeSize, modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    val levels = remember(cat.id) { cat.muscles.associateWith { 2 } }
    val pulse = rememberPulse()
    Card(modifier, onClick = onClick, padding = PaddingValues(Fs.ui.pad(14))) {
        Box(Modifier.fillMaxWidth().height(if (size == HomeSize.Large) 150.dp else 116.dp).clip(Fs.shapes.chip).background(c.surface2), contentAlignment = Alignment.Center) {
            MuscleFigure(levels, Modifier.fillMaxSize(), back = cat.back, pulse = pulse, padding = 0.06f)
        }
        Spacer(Modifier.height(10.dp))
        Txt(cat.name, style = Fs.type.h3, maxLines = 1)
        Txt(cat.tagline, style = Fs.type.caption, color = c.textDim, maxLines = 2)
        Spacer(Modifier.weight(1f, fill = true))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("$total", style = Fs.type.smallStrong, color = c.text)
            Txt("ejercicios", Modifier.weight(1f), Fs.type.caption, c.textDim, maxLines = 1)
            if (inSet > 0) {
                Row(
                    Modifier.clip(Fs.shapes.pill).background(c.accentSoft).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Ic.Check, size = 11.dp, tint = c.accentText, style = "line", weight = 3f)
                    Txt("$inSet", style = Fs.type.caption, color = c.accentText)
                }
            }
        }
    }
}

// ───────────────────────── Categoría

@Composable
fun CategoryScreen(id: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val cat = Categories.byId(id)
    if (cat == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val all = repo.exercises
    val configs = repo.configs
    val exs = remember(all, id) { all.filter { it.category == id } }
    val equipment = remember(exs) { exs.groupingBy { it.equipment }.eachCount().entries.filter { it.key.isNotBlank() }.sortedByDescending { it.value }.take(7).map { it.key } }
    var equip by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf("name") }
    var onlySet by rememberSaveable { mutableStateOf(false) }
    val list = remember(exs, equip, sort, onlySet, configs) {
        exs.filter { (equip == null || it.equipment == equip) && (!onlySet || configs[it.id]?.inSet == true) }
            .let { l ->
                when (sort) {
                    "set" -> l.sortedWith(compareByDescending<Exercise> { configs[it.id]?.inSet == true }.thenBy { it.name })
                    "level" -> l.sortedWith(compareBy<Exercise> { levelRank(it.level) }.thenBy { it.name })
                    else -> l.sortedBy { it.name }
                }
            }
    }
    val inSetCount = exs.count { configs[it.id]?.inSet == true }
    val levels = remember(id) { cat.muscles.associateWith { 2 } }
    val pulse = rememberPulse()

    ScreenList(
        title = cat.name, subtitle = cat.tagline, onBack = { nav.back() },
        actions = {
            RoundButton(Ic.Search, { nav.push(Route.Search()) })
            RoundButton(Ic.Plus, { app.overlays.editExercise(presetCategory = id, onSaved = { nav.push(Route.Exercise(it.id)) }) })
        },
    ) {
        item("hero") {
            Card(Modifier.enter(0)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1.1f).height(150.dp).clip(Fs.shapes.chip).background(c.surface2), contentAlignment = Alignment.Center) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            MuscleFigure(levels, Modifier.weight(1f).fillMaxHeight(), back = false, pulse = pulse, padding = 0.05f)
                            MuscleFigure(levels, Modifier.weight(1f).fillMaxHeight(), back = true, pulse = pulse, padding = 0.05f)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label("Resumen")
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            BigNumber(exs.size.toString(), style = Fs.type.title)
                            Txt("ejercicios", Modifier.padding(bottom = 4.dp), Fs.type.caption, c.textDim)
                        }
                        Txt("$inSetCount en tu set", style = Fs.type.smallStrong, color = c.accentText)
                        Txt(cat.muscles.joinToString(" · ") { it.label }, style = Fs.type.caption, color = c.textDim, maxLines = 3)
                    }
                }
            }
        }
        item("filters") {
            Column(Modifier.enter(1), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Todos", selected = equip == null, onClick = { equip = null })
                    for (e in equipment) Pill(e, selected = equip == e, onClick = { equip = if (equip == e) null else e })
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("Orden")
                    Pill("A–Z", selected = sort == "name", soft = true, height = 30.dp, onClick = { sort = "name" })
                    Pill("Mi set primero", selected = sort == "set", soft = true, height = 30.dp, onClick = { sort = "set" })
                    Pill("Nivel", selected = sort == "level", soft = true, height = 30.dp, onClick = { sort = "level" })
                    Pill("Solo mi set", selected = onlySet, soft = true, height = 30.dp, icon = Ic.Check, onClick = { onlySet = !onlySet })
                }
            }
        }
        if (list.isEmpty()) {
            item("empty") {
                EmptyState(
                    "Nada por aquí", if (onlySet) "Aún no tienes ejercicios de ${cat.name.lowercase()} en tu set." else "Ningún ejercicio coincide con el filtro.",
                    Modifier.enter(2), mood = BotMood.Sleepy, actionLabel = "Quitar filtros", onAction = { equip = null; onlySet = false },
                )
            }
        }
        items(list, key = { it.id }) { ex ->
            val on = configs[ex.id]?.inSet == true
            ExerciseRow(
                ex, onClick = { nav.push(Route.Exercise(ex.id)) },
                subtitle = listOf(ex.equipment, ex.level).filter { it.isNotBlank() }.joinToString(" · "),
                trailing = { SetQuickToggle(on, { app.toggleSet(ex) }) },
                onLongClick = { app.toggleSet(ex) },
            )
        }
    }
}

private fun levelRank(level: String): Int = when (level.lowercase()) {
    "principiante" -> 0
    "intermedio" -> 1
    "avanzado" -> 2
    else -> 3
}

// ───────────────────────── Mi set

@Composable
fun MySetScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val configs = repo.configs
    val exercises = repo.exercises
    val setEx = remember(configs, exercises) { exercises.filter { configs[it.id]?.inSet == true } }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(setEx, query) {
        if (query.isBlank()) setEx else {
            val ids = repo.search(query, 80).map { it.ex.id }.toSet()
            setEx.filter { it.id in ids }
        }
    }
    val grouped = remember(shown) {
        Categories.all.mapNotNull { k -> shown.filter { it.category == k.id }.sortedBy { it.name }.takeIf { it.isNotEmpty() }?.let { k to it } }
    }

    fun addMore() = app.overlays.pickExercises("Agregar a mi set", exclude = setEx.map { it.id }.toSet(), actionLabel = "Agregar") { ids ->
        ids.forEach { repo.addToSet(it) }
        app.overlays.toast("${Fmt.plural(ids.size, "ejercicio agregado", "ejercicios agregados")}", "Deshacer", Ic.Check) { repeat(ids.size) { repo.undo() } }
    }

    ScreenList(
        title = "Mi set", subtitle = if (setEx.isEmpty()) "Tus ejercicios de siempre" else Fmt.plural(setEx.size, "ejercicio", "ejercicios"),
        onBack = { nav.back() },
        actions = { RoundButton(Ic.Plus, { addMore() }) },
    ) {
        if (setEx.isEmpty()) {
            item("empty") {
                EmptyState(
                    "Tu set está vacío", "Agrega los ejercicios que haces seguido para tenerlos a la mano, con sus series, repeticiones y días.",
                    Modifier.enter(0), mood = BotMood.Happy, actionLabel = "Elegir ejercicios", onAction = { addMore() },
                )
            }
            return@ScreenList
        }
        item("search") { FsTextField(query, { query = it }, Modifier.enter(0), placeholder = "Buscar en mi set…", leading = Ic.Search) }
        if (shown.isEmpty()) item("none") { Txt("Ningún ejercicio de tu set coincide.", Modifier.padding(16.dp), Fs.type.small, c.textDim) }
        var n = 1
        for ((cat, list) in grouped) {
            val idx = n++
            item("h_${cat.id}") {
                Row(Modifier.fillMaxWidth().enter(idx).padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(cat.name, Modifier.weight(1f), color = c.textFaint)
                    CountBadge(list.size, size = 22.dp)
                }
            }
            items(list, key = { it.id }) { ex ->
                val cfg = configs[ex.id]
                val sub = remember(cfg) {
                    if (cfg == null) null
                    else "${cfg.sets} × ${cfg.repsLabel}${if (ex.isCardio) " min" else ""}" + if (cfg.days.isEmpty()) "" else " · " + Weekdays.label(cfg.days)
                }
                ExerciseRow(
                    ex, onClick = { nav.push(Route.Exercise(ex.id)) }, showDesc = false, subtitle = sub,
                    trailing = {
                        RoundButton(Ic.Close, { app.toggleSet(ex) }, size = 36.dp, bg = c.surface3, border = false, tint = c.textDim)
                    },
                )
            }
        }
        item("routine") {
            FsButton(
                "Crear una rutina con mi set", {
                    val r = repo.createRoutine("Mi set", exerciseIds = setEx.map { it.id })
                    app.toastUndo("Rutina «${r.name}» creada")
                    nav.push(Route.RoutineDetail(r.id))
                },
                Modifier.fillMaxWidth().padding(top = 8.dp), kind = BtnKind.Secondary, icon = Ic.Routines,
            )
        }
    }
}
