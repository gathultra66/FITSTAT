package app.fitstat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.PageEditor
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs

/** Los datos que usan los widgets del inicio, calculados una vez (los comparten el inicio y las demás pantallas). */
@Composable
fun rememberHomeData(): HomeData {
    val repo = LocalRepo.current
    val today = repo.today()
    val logs = repo.logs
    val mondayFirst = repo.mondayFirst
    val goal = repo.weeklyGoal
    val zone = repo.zone
    val exById = repo.exById
    val days = remember(logs) { Stats.workoutDays(logs, zone) }
    val weekDays = remember(logs, today, mondayFirst) { Stats.daysThisWeek(logs, mondayFirst, today, zone) }
    val streak = remember(days, goal, mondayFirst, today) { Stats.streakWeeks(days, goal, mondayFirst, today) }
    val plan = remember(repo.data, today) { Stats.todayPlan(repo.data, exById, today) }
    val records = remember(logs) { Stats.recordEvents(logs, repo.formula).sortedByDescending { it.ts }.take(3) }
    val pinned = repo.notes.filter { it.pinned }.sortedByDescending { it.updatedAt }.take(3)
    val next = remember(repo.logs, repo.routines, repo.notes, repo.unlocked, repo.reminders, repo.configs) { Milestones.next(repo.milestoneStates()) }
    return HomeData(today, logs, goal, streak, weekDays, days, mondayFirst, repo.units, plan, records, pinned, next)
}

/**
 * Una pantalla cuya distribución se puede editar (inicio, Ejercicios, Rutinas): una cuadrícula de bloques que se mantiene
 * pulsada —en un contenedor o en cualquier parte de la pantalla— para mover, achicar, agrandar, quitar y agregar.
 * Además de los bloques propios de la pantalla ([native]), acepta los widgets del inicio. La distribución se guarda al pulsar «Listo».
 */
@Composable
fun EditablePage(
    editor: PageEditor,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    /** Lo que va arriba de la cuadrícula (por ejemplo, la bienvenida). Se oculta al editar si [hideInEdit]. */
    above: LazyListScope.(editing: Boolean) -> Unit = {},
    /** Si false, una pantalla sin bloques no muestra el aviso de «vacía» (porque la pantalla ya dice algo mejor). */
    emptyHint: Boolean = true,
    /** Si un bloque propio de la pantalla tiene algo que mostrar (los que no, se ocultan fuera del modo de edición). */
    nativeVisible: (String) -> Boolean = { true },
    homeData: @Composable () -> HomeData = { rememberHomeData() },
    native: @Composable (id: String, size: HomeSize, modifier: Modifier) -> Unit,
) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val hap = Fs.haptics
    val editing = editor.active
    val listState = rememberLazyListState()
    val dnd = remember { HomeDnd() }

    // Al terminar de editar se guarda todo de una vez (un solo «Deshacer»).
    val done = {
        if (editor.finish()) app.toastUndo(if (editor.page == LayoutPage.HOME) "Inicio personalizado" else "Distribución de ${editor.title} guardada")
    }
    // Si se sale de la pantalla en plena edición (otra pestaña, FITBOT…), lo hecho se conserva.
    DisposableEffect(editor) {
        onDispose { if (editor.active) editor.finish() }
    }
    BackHandler(enabled = editing && !app.overlays.hasOverlay) { done() }
    // Al agregar un bloque, la lista baja para que se vea dónde quedó.
    LaunchedEffect(editor.addedTick) {
        if (editor.addedTick > 0) {
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.animateScrollToItem(last)
        }
    }

    val slots = if (editing) editor.slots else repo.pageSlots(editor.page)
    val hasHub = slots.any { HomeLayout.def(it.id)?.page == null }
    val data = if (hasHub) homeData() else null

    // Los bloques «sin datos» no se muestran; al editar sí, para poder moverlos o quitarlos.
    val shown = slots.filter { s ->
        val def = HomeLayout.def(s.id) ?: return@filter false
        if (HomeLayout.isRoutineBlock(s.id) && repo.routines.none { it.id == s.id.removePrefix(HomeLayout.ROUTINE_PREFIX) }) return@filter false
        when {
            editing || !def.hideWhenEmpty -> true
            def.page == null -> data?.hasContent(s.id) ?: true
            else -> nativeVisible(s.id)
        }
    }

    ScreenList(
        title = if (editing) "Editar ${editor.title.lowercase()}" else title,
        subtitle = if (editing) "Mueve, cambia de tamaño o quita bloques" else subtitle,
        modifier = modifier,
        state = listState,
        // Mantener pulsada la pantalla (no solo un contenedor) abre la edición.
        listModifier = Modifier.longPressToEdit(enabled = !editing) { hap.heavy(); editor.start() },
        actions = {
            if (editing) FsButton("Listo", { done() }, Modifier.testTag("${editor.tag}_done"), icon = Ic.Check, height = 44.dp)
            else actions()
        },
    ) {
        above(editing)
        if (shown.isEmpty()) {
            if (emptyHint) item("empty") { HomeEmpty(editor, editing) }
        } else {
            item("grid") {
                PageGrid(editor, shown, editing, dnd, listState) { id, size, mod ->
                    if (HomeLayout.def(id)?.page == null) {
                        if (data != null) HomeWidget(id, size, data, mod)
                    } else native(id, size, mod)
                }
            }
        }
        if (editing) {
            item("editbar") { HomeEditBar(editor, Modifier.padding(top = 4.dp)) }
        }
    }
}
