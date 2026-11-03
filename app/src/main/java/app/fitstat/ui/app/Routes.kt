package app.fitstat.ui.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/** Todas las pantallas de la app. [key] identifica la pantalla (para recordar su posición de desplazamiento). */
sealed class Route(val key: String) {
    data object Home : Route("home")
    data object Exercises : Route("exercises")
    data object Routines : Route("routines")
    data object Bot : Route("bot")
    data object Settings : Route("settings")

    data class Category(val id: String) : Route("cat:$id")
    data class Exercise(val id: String) : Route("ex:$id")
    data class Muscles(val id: String) : Route("mus:$id")
    data class Search(val q: String = "") : Route("search")
    data object MySet : Route("myset")
    data class RoutineDetail(val id: String) : Route("rt:$id")
    data class Workout(val routineId: String?, val exercises: List<String> = emptyList()) : Route("wk:${routineId ?: "free"}")
    data object Progress : Route("progress")
    data object Milestones : Route("milestones")
    data object Notes : Route("notes")
    data class SettingsPage(val page: String) : Route("sp:$page")
    data object Changelog : Route("changelog")
    data object About : Route("about")
    data object DataTools : Route("data")

    /** Detalle de un conector (Namida, Health Connect…). */
    data class Connector(val id: String) : Route("cn:$id")

    /** Tu sueño: la última noche, tendencias y registro a mano. */
    data object Sleep : Route("sleep")

    companion object {
        val tabs = listOf(Home, Exercises, Routines, Bot, Settings)

        fun tabOf(id: String): Route = when (id) {
            "exercises" -> Exercises
            "routines" -> Routines
            "bot" -> Bot
            "settings" -> Settings
            else -> Home
        }

        /** Nombres que FITBOT puede usar para abrir pantallas. */
        val names: List<String> = listOf(
            "inicio", "ejercicios", "rutinas", "fitbot", "ajustes", "progreso", "hitos", "notas", "mi_set", "buscar",
            "registro_de_cambios", "datos", "acerca_de", "ejercicio:<id>", "categoria:<id>", "musculos:<id>", "rutina:<id>",
            "entrenar:<id de rutina o vacío>", "ajustes:<página>", "sueno", "conectores", "conector:<id>",
        )

        fun fromName(name: String, arg: String? = null): Route? {
            val n = name.trim().lowercase().replace(' ', '_')
            val base = n.substringBefore(':')
            val a = arg ?: name.trim().substringAfter(':', "").trim().ifBlank { null }
            return when (base) {
                "inicio", "home" -> Home
                "ejercicios", "exercises" -> Exercises
                "rutinas", "routines" -> Routines
                "fitbot", "chat", "bot" -> Bot
                "ajustes", "configuracion", "settings" -> if (a != null) SettingsPage(a) else Settings
                "progreso", "estadisticas", "stats" -> Progress
                "hitos", "logros", "milestones" -> Milestones
                "notas", "anotaciones", "notes" -> Notes
                "mi_set", "set" -> MySet
                "buscar", "busqueda", "search" -> Search(a ?: "")
                "registro_de_cambios", "changelog", "log" -> Changelog
                "datos", "data" -> DataTools
                "acerca_de", "about" -> About
                "ejercicio", "exercise" -> a?.let { Exercise(it) }
                "categoria", "category" -> a?.let { Category(it) }
                "musculos", "muscles" -> a?.let { Muscles(it) }
                "rutina", "routine" -> a?.let { RoutineDetail(it) }
                "entrenar", "workout" -> Workout(a)
                "sueno", "sueño", "dormir", "sleep" -> Sleep
                "conectores", "connectors" -> SettingsPage(app.fitstat.data.Pages.CONNECTORS)
                "conector", "connector" -> a?.let { app.fitstat.conn.Connectors.find(it)?.let { d -> Connector(d.id) } }
                else -> null
            }
        }
    }
}

/** Pila de pantallas. El primer elemento es la pestaña actual. */
@Stable
class Nav(private val start: Route) {
    val stack = mutableStateListOf<Route>(start)
    var direction by mutableIntStateOf(0)
        private set

    val top: Route get() = stack.last()
    val tab: Route get() = stack.first()
    val canBack: Boolean get() = stack.size > 1 || tab != start

    fun push(r: Route) {
        if (r in Route.tabs) { switchTab(r); return }
        if (top == r) return
        direction = 1
        stack.add(r)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        direction = -1
        stack.removeAt(stack.size - 1)
        return true
    }

    fun switchTab(t: Route) {
        if (stack.size == 1 && stack[0] == t) return
        direction = 0
        stack.clear()
        stack.add(t)
    }

    /** Atrás: cierra la pantalla, o vuelve a la pestaña inicial. Devuelve false si ya no hay nada que cerrar. */
    fun back(): Boolean {
        if (pop()) return true
        if (tab != start) { switchTab(start); return true }
        return false
    }

    /** Quita de la pila (sin tocar la pestaña) las pantallas que cumplan [pred], estén donde estén. */
    fun removeWhere(pred: (Route) -> Boolean): Boolean {
        var any = false
        for (i in stack.lastIndex downTo 1) {
            if (pred(stack[i])) {
                stack.removeAt(i)
                any = true
            }
        }
        if (any) direction = -1
        return any
    }

    /** Quita de la pila las pantallas que apuntan a algo que ya no existe. */
    fun popWhile(pred: (Route) -> Boolean) {
        while (stack.size > 1 && pred(top)) pop()
    }
}
