package app.fitstat.ui.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fitstat.data.HomeLayout
import app.fitstat.data.HomeSize
import app.fitstat.data.HomeSlot
import app.fitstat.data.LayoutPage
import app.fitstat.data.Repo

/**
 * Edición de la distribución de una pantalla: el inicio, Ejercicios o Rutinas ([page]).
 * Mientras [active] es true la pantalla muestra [slots] (una copia de trabajo); al terminar con [finish] se guarda
 * de una sola vez, así «Deshacer» revierte toda la sesión de edición.
 */
@Stable
class PageEditor(private val repo: Repo, val page: String = LayoutPage.HOME) {
    var active by mutableStateOf(false)
        private set

    /** Copia de trabajo de la disposición mientras se edita. */
    var slots: List<HomeSlot> by mutableStateOf(emptyList())
        private set

    /** Sube cada vez que se agrega un widget (la pantalla baja para mostrarlo). */
    var addedTick by mutableIntStateOf(0)
        private set

    private var original: List<HomeSlot> = emptyList()

    /** Nombre de la pantalla («Inicio», «Ejercicios», «Rutinas»). */
    val title: String get() = LayoutPage.title(page)

    /** Prefijo de las etiquetas de prueba. */
    val tag: String get() = LayoutPage.tag(page)

    fun start() {
        if (active) return
        original = repo.pageSlots(page)
        slots = original
        active = true
    }

    /** Termina la edición y guarda lo que cambió. Devuelve true si hubo cambios. */
    fun finish(): Boolean {
        if (!active) return false
        val result = slots
        active = false
        if (result == original) return false
        repo.setPageLayout(page, result)
        return true
    }

    /** Termina la edición sin guardar nada. */
    fun cancel() {
        active = false
        slots = original
    }

    private fun change(list: List<HomeSlot>): Boolean {
        if (!active || list == slots) return false
        slots = list
        return true
    }

    fun remove(id: String): Boolean = change(HomeLayout.remove(slots, id))

    fun add(id: String): Boolean = change(HomeLayout.add(slots, id, page)).also { if (it) addedTick++ }

    /** +1 agranda y −1 achica al siguiente tamaño que admita el widget. */
    fun resize(id: String, step: Int): Boolean = change(HomeLayout.resize(slots, id, step))

    fun setSize(id: String, size: HomeSize): Boolean = change(HomeLayout.setSize(slots, id, size))

    /** Pone el bloque en la posición [to] de la lista. */
    fun move(id: String, to: Int): Boolean = change(HomeLayout.move(slots, id, to))

    /** Vuelve a la disposición de siempre de la pantalla (se guarda al terminar). */
    fun reset(): Boolean = change(repo.defaultSlots(page))
}

/** Compatibilidad: así se llamaba cuando solo editaba el inicio. */
typealias HomeEditor = PageEditor
