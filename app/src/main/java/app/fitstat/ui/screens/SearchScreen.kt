package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.delay

private val popularIds = listOf(
    "press_de_banca_con_barra", "sentadilla_con_barra", "peso_muerto", "dominadas", "press_militar_de_pie",
    "remo_con_barra", "prensa_de_pierna", "hip_thrust_con_barra", "jalon_al_pecho_agarre_ancho", "plancha",
)

/**
 * Buscador de ejercicios con sistema predictivo:
 *  - completa en gris lo que escribes (ghost text),
 *  - sugiere ejercicios mientras tecleas,
 *  - tolera errores de ortografía y acentos,
 *  - si no existe, permite crear la plantilla.
 */
@Composable
fun SearchScreen(initial: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf(initial) }
    var catFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        delay(160)
        runCatching { focus.requestFocus() }
    }
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling) { if (scrolling) focusManager.clearFocus() }

    val exercises = repo.exercises
    val configs = repo.configs
    val hits = remember(query, exercises, configs) { if (query.isBlank()) emptyList() else repo.search(query, 60) }
    val ghost = remember(hits, query) { Search.complete(hits, query) }
    val filtered = remember(hits, catFilter) { if (catFilter == null) hits else hits.filter { it.ex.category == catFilter } }
    val catCounts = remember(hits) { hits.groupingBy { it.ex.category }.eachCount() }
    val predictions = remember(hits) { hits.take(6).map { it.ex } }
    val setEx = remember(configs, exercises) { exercises.filter { configs[it.id]?.inSet == true } }

    fun open(ex: Exercise) {
        repo.rememberSearch(if (query.isBlank()) ex.name else query)
        nav.push(Route.Exercise(ex.id))
    }

    Column(Modifier.fillMaxSize().padding(top = statusBarTop())) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundButton(Ic.ChevronLeft, { nav.back() })
            FsTextField(
                query, { query = it; catFilter = null }, Modifier.weight(1f).focusRequester(focus),
                placeholder = "Buscar ejercicio…", leading = Ic.Search, imeAction = ImeAction.Search, ghost = ghost,
                onDone = {
                    if (!ghost.isNullOrEmpty()) query += ghost
                    else {
                        if (query.isNotBlank()) repo.rememberSearch(query)
                        focusManager.clearFocus()
                    }
                },
                trailing = {
                    if (query.isNotEmpty()) RoundButton(Ic.Close, { query = ""; catFilter = null }, size = 30.dp, bg = c.surface3, border = false, tint = c.textDim)
                },
            )
        }
        // Predicciones: nombres de ejercicios que podrías estar buscando
        if (predictions.isNotEmpty() && (predictions.size > 1 || !predictions.first().name.equals(query.trim(), true))) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (ex in predictions) Pill(ex.name, icon = Ic.ArrowUpRight, soft = true, onClick = { query = ex.name })
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (query.isBlank()) {
                // ── Estado inicial: recientes, categorías y sugerencias
                val history = repo.searchHistory
                if (history.isNotEmpty()) item("recent") {
                    Column(Modifier.enter(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Búsquedas recientes", action = "Borrar", onAction = { repo.clearSearchHistory() })
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (h in history) Pill(h, icon = Ic.Clock, soft = true, onClick = { query = h })
                        }
                    }
                }
                item("cats") {
                    Column(Modifier.enter(1), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Explorar por categoría")
                        ChipFlow { for (k in Categories.all) Pill(k.name, onClick = { nav.push(Route.Category(k.id)) }) }
                    }
                }
                val suggestions = if (setEx.isNotEmpty()) setEx.take(6) else popularIds.mapNotNull { repo.exById[it] }.take(6)
                item("sug_title") { SectionTitle(if (setEx.isNotEmpty()) "En tu set" else "Populares", Modifier.enter(2)) }
                items(suggestions, key = { "s_" + it.id }) { ex ->
                    ExerciseRow(
                        ex, onClick = { open(ex) }, showDesc = false, subtitle = listOf(ex.categoryName(), ex.equipment).filter { it.isNotBlank() }.joinToString(" · "),
                        trailing = { SetQuickToggle(configs[ex.id]?.inSet == true, { app.toggleSet(ex) }) },
                    )
                }
            } else if (hits.isEmpty()) {
                // ── Sin resultados: crear plantilla
                item("none") {
                    Card(Modifier.enter(0)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FitBot(size = 92.dp, mood = BotMood.Sleepy)
                            Txt("No encontré «${query.trim()}»", style = Fs.type.h2, align = TextAlign.Center)
                            Txt(
                                "Revisa la ortografía o crea tu propia plantilla de ejercicio con el nombre, los músculos y una foto.",
                                style = Fs.type.small, color = c.textDim, align = TextAlign.Center,
                            )
                            FsButton(
                                "Crear «${query.trim().take(28)}»",
                                { app.overlays.editExercise(presetName = query.trim().replaceFirstChar { it.uppercase() }, onSaved = { nav.push(Route.Exercise(it.id)) }) },
                                Modifier.fillMaxWidth(), icon = Ic.Plus,
                            )
                        }
                    }
                }
            } else {
                // ── Resultados
                if (catCounts.size > 1) item("filters") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Todos ${hits.size}", selected = catFilter == null, soft = true, height = 32.dp, onClick = { catFilter = null })
                        for (k in Categories.all) {
                            val n = catCounts[k.id] ?: continue
                            Pill("${k.name} $n", selected = catFilter == k.id, soft = true, height = 32.dp, onClick = { catFilter = if (catFilter == k.id) null else k.id })
                        }
                    }
                }
                items(filtered, key = { it.ex.id }) { h ->
                    val on = configs[h.ex.id]?.inSet == true
                    ExerciseRow(
                        h.ex, onClick = { open(h.ex) }, query = query,
                        trailing = { SetQuickToggle(on, { app.toggleSet(h.ex) }) },
                        subtitle = h.ex.categoryName() + if (h.ex.equipment.isNotBlank()) " · ${h.ex.equipment}" else "",
                    )
                }
                item("create") {
                    Card(
                        onClick = { app.overlays.editExercise(presetName = query.trim().replaceFirstChar { it.uppercase() }, onSaved = { nav.push(Route.Exercise(it.id)) }) },
                        shape = Fs.shapes.inner,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconBadge(Ic.Plus, size = 42.dp)
                            Column(Modifier.weight(1f)) {
                                Txt("¿No es el que buscas?", style = Fs.type.bodyStrong)
                                Txt("Crea tu propio ejercicio «${query.trim().take(24)}»", style = Fs.type.small, color = c.textDim, maxLines = 2)
                            }
                            Icon(Ic.ChevronRight, size = 20.dp, tint = c.textFaint)
                        }
                    }
                }
            }
        }
    }
}
