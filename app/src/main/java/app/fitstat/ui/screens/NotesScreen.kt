package app.fitstat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.fitstat.data.*
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

/** Hoja para escribir o editar una anotación. */
fun Overlays.editNote(existing: Note? = null, exerciseId: String? = null) {
    sheet { close -> NoteEditorBody(existing, exerciseId, close) }
}

@Composable
private fun NoteEditorBody(existing: Note?, exerciseId: String?, close: () -> Unit) {
    val repo = LocalRepo.current
    val app = LocalApp.current
    val c = Fs.colors
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember { mutableStateOf(existing?.body.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.colorIdx ?: 0) }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var linked by remember { mutableStateOf(existing?.exerciseId ?: exerciseId) }
    val linkedEx = linked?.let { repo.exById[it] }
    val valid = title.isNotBlank() || body.isNotBlank()

    SheetBody(if (existing == null) "Nueva anotación" else "Anotación", subtitle = "Apuntes del gym: metas, técnica, molestias, ideas…") {
        FsTextField(title, { title = it.take(80) }, placeholder = "Título", leading = Ic.Note)
        FsTextField(body, { body = it.take(4000) }, placeholder = "Escribe lo que quieras recordar…", singleLine = false, minLines = 6)

        Label("Color")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until Repo.RoutineColors) ColorSwatch(routineColor(i), color == i, { color = i }, size = 34.dp)
        }

        Row(
            Modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.surface2).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Ic.Pin, size = 20.dp, tint = c.accentText)
            Column(Modifier.weight(1f)) {
                Txt("Fijar en Inicio", style = Fs.type.bodyStrong)
                Txt("Se muestra arriba y en la pantalla de inicio.", style = Fs.type.caption, color = c.textDim)
            }
            Toggle(pinned, { pinned = it })
        }

        if (linkedEx != null) {
            Row(
                Modifier.fillMaxWidth().clip(Fs.shapes.inner).background(c.surface2).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExerciseThumb(linkedEx, 44.dp)
                Column(Modifier.weight(1f)) {
                    Label("Vinculada a")
                    Txt(linkedEx.name, style = Fs.type.bodyStrong, maxLines = 1)
                }
                RoundButton(Ic.Close, { linked = null }, size = 34.dp, bg = c.surface3, border = false, tint = c.textDim)
            }
        } else {
            FsButton("Vincular a un ejercicio", {
                app.overlays.pickExercises("Vincular a un ejercicio", actionLabel = "Vincular") { ids -> linked = ids.firstOrNull() }
            }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost, icon = Ic.Link, height = 44.dp)
        }

        FsButton("Guardar", {
            repo.saveNote(
                Note(
                    id = existing?.id.orEmpty(), title = title, body = body, pinned = pinned, colorIdx = color,
                    createdAt = existing?.createdAt ?: 0L, exerciseId = linked,
                ),
            )
            close()
            app.overlays.toast("Anotación guardada", icon = Ic.Check)
        }, Modifier.fillMaxWidth(), enabled = valid, icon = Ic.Check)
        if (existing != null) {
            FsButton("Eliminar anotación", {
                app.overlays.confirm("¿Eliminar esta anotación?", "Podrás deshacerlo justo después.", "Eliminar", danger = true) {
                    repo.deleteNote(existing.id)
                    close()
                    app.toastUndo("Anotación eliminada")
                }
            }, Modifier.fillMaxWidth(), kind = BtnKind.Danger, icon = Ic.Trash)
        }
    }
}

@Composable
fun NotesScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val nav = app.nav
    val c = Fs.colors
    val notes = repo.notes
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val shown = remember(notes, query, filter) {
        val q = Search.norm(query)
        notes.filter { n ->
            (filter == "all" || (filter == "pinned" && n.pinned) || (filter == "linked" && n.exerciseId != null)) &&
                (q.isEmpty() || Search.norm(n.title + " " + n.body).contains(q))
        }.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }

    ScreenList(
        title = "Anotaciones", subtitle = if (notes.isEmpty()) "Tus apuntes del gym" else Fmt.plural(notes.size, "nota", "notas"),
        onBack = { nav.back() },
        actions = { RoundButton(Ic.Plus, { app.overlays.editNote() }) },
    ) {
        if (notes.isEmpty()) {
            item("empty") {
                EmptyState(
                    "Sin anotaciones", "Guarda aquí tus metas, ajustes de máquinas, molestias o cualquier idea del gym.",
                    Modifier.enter(0), mood = BotMood.Happy, actionLabel = "Escribir la primera", onAction = { app.overlays.editNote() },
                )
            }
            return@ScreenList
        }
        item("search") { FsTextField(query, { query = it }, Modifier.enter(0), placeholder = "Buscar en mis notas…", leading = Ic.Search) }
        item("filters") {
            Row(Modifier.fillMaxWidth().enter(1).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Todas", selected = filter == "all", onClick = { filter = "all" })
                Pill("Fijadas", selected = filter == "pinned", icon = Ic.Pin, onClick = { filter = "pinned" })
                Pill("De ejercicios", selected = filter == "linked", icon = Ic.Dumbbell, onClick = { filter = "linked" })
            }
        }
        if (shown.isEmpty()) item("none") { Txt("No hay notas con ese filtro.", Modifier.padding(16.dp), Fs.type.small, c.textDim) }
        items(shown, key = { it.id }) { n ->
            val ex = n.exerciseId?.let { repo.exById[it] }
            Card(Modifier, onClick = { app.overlays.editNote(existing = n) }, shape = Fs.shapes.inner) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(5.dp).height(52.dp).clip(Fs.shapes.pill).background(routineColor(n.colorIdx)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Txt(n.title.ifBlank { "Sin título" }, style = Fs.type.h3, maxLines = 1)
                        if (n.body.isNotBlank()) Txt(n.body, style = Fs.type.small, color = c.textDim, maxLines = 4)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Txt(Fmt.relativeDay(Fmt.date(n.updatedAt, repo.zone), repo.today()), style = Fs.type.caption, color = c.textFaint)
                            if (ex != null) {
                                Row(
                                    Modifier.clip(Fs.shapes.pill).background(c.accentSoft).tappable({ nav.push(Route.Exercise(ex.id)) }, pressScale = 0.95f).padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Ic.Dumbbell, size = 11.dp, tint = c.accentText)
                                    Txt(ex.name, Modifier.widthIn(max = 140.dp), Fs.type.caption, c.accentText, maxLines = 1)
                                }
                            }
                        }
                    }
                    RoundButton(
                        Ic.Pin, { repo.saveNote(n.copy(pinned = !n.pinned)) }, size = 34.dp,
                        tint = if (n.pinned) c.onAccent else c.textFaint, bg = if (n.pinned) c.accent else c.surface2, border = !n.pinned,
                    )
                }
            }
        }
    }
}
