package app.fitstat.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import app.fitstat.data.HomeLayout
import app.fitstat.data.HomeSize
import app.fitstat.data.HomeSlot
import app.fitstat.data.HomeWidgetDef
import app.fitstat.data.LayoutPage
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.PageEditor
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ───────────────────────── Arrastre y geometría

/**
 * Geometría de la cuadrícula del inicio y estado del arrastre. Las posiciones están en el sistema
 * del contenedor ([rects]); el dedo se guarda en la raíz de la ventana ([pointerRoot]) para que no
 * se pierda cuando la lista se desplaza sola.
 */
@Stable
class HomeDnd {
    /** Widget que se está arrastrando (null si ninguno). */
    var id by mutableStateOf<String?>(null)
        private set
    var pointerRoot by mutableStateOf(Offset.Zero)
    var grab by mutableStateOf(Offset.Zero)
    var originInRoot by mutableStateOf(Offset.Zero)

    /** Sube cuando cambia el acomodo; solo se escribe (nunca se lee en el acomodo, para no entrar en bucle). */
    private val versionState = mutableIntStateOf(0)
    private var versionCounter = 0
    val version: Int get() = versionState.intValue

    val rects = HashMap<String, IntRect>()

    /** Widget con el que se acaba de tomar lugar: no se vuelve a intercambiar con él hasta que el dedo salga de su recuadro. */
    private var lastTarget: String? = null

    fun bump() {
        versionCounter++
        versionState.intValue = versionCounter
    }

    fun begin(id: String, localPointer: Offset) {
        val r = rects[id] ?: return
        this.id = id
        grab = localPointer - Offset(r.left.toFloat(), r.top.toFloat())
        pointerRoot = originInRoot + localPointer
        lastTarget = null
    }

    fun end() {
        id = null
        lastTarget = null
    }

    fun hit(p: Offset, except: String? = null): String? {
        for ((k, r) in rects) {
            if (k != except && p.x >= r.left && p.x < r.right && p.y >= r.top && p.y < r.bottom) return k
        }
        return null
    }

    /**
     * Decide a dónde se mueve el widget arrastrado según lo que hay bajo el dedo.
     * Devuelve el id del widget con el que hay que tomar el lugar, o null si no toca moverse;
     * [END] si el dedo pasó del último widget.
     */
    fun target(): String? {
        val me = id ?: return null
        val p = pointerRoot - originInRoot
        val t = hit(p, except = me)
        if (t != null) return if (t == lastTarget) null else t
        // Entre widgets (o sobre el propio): se libera el último, así se puede volver a cruzar.
        lastTarget = null
        val bottom = rects.values.maxOfOrNull { it.bottom } ?: return null
        return if (p.y > bottom + 6f) END else null
    }

    fun swapped(target: String) {
        lastTarget = target
    }

    companion object {
        const val END = "\u0000end"
    }
}

// ───────────────────────── Cuadrícula

/**
 * Cuadrícula de [HomeLayout.COLUMNS] columnas: cada hijo ocupa las columnas de [spans] y se acomoda en orden,
 * pasando a la siguiente fila cuando ya no cabe. En una fila con varios widgets todos miden lo mismo de alto.
 * Es un solo contenedor plano para que, al reordenar, cada widget conserve su estado.
 */
@Composable
fun HomeFlow(
    spans: List<Int>,
    ids: List<String>,
    dnd: HomeDnd,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier.onGloballyPositioned { dnd.originInRoot = it.positionInRoot() }) { measurables, constraints ->
        val g = gap.roundToPx()
        val width = constraints.maxWidth
        val colW = (width - g * (HomeLayout.COLUMNS - 1)) / HomeLayout.COLUMNS.toFloat()
        fun widthOf(span: Int) = (span * colW + (span - 1) * g).roundToInt()
        val n = minOf(measurables.size, spans.size, ids.size)
        val rows = HomeLayout.pack(spans.take(n))
        val placeables = arrayOfNulls<Placeable>(n)
        val xs = IntArray(n)
        val ys = IntArray(n)
        var y = 0
        for ((ri, row) in rows.withIndex()) {
            val widths = row.map { widthOf(spans[it].coerceIn(1, HomeLayout.COLUMNS)) }
            // Con varios widgets en la fila, todos igualan su alto al del más alto (si alguno no lo permite, cada quien mide lo suyo).
            val heights = if (row.size > 1) row.mapIndexed { k, i -> runCatching { measurables[i].minIntrinsicHeight(widths[k]) }.getOrNull() } else emptyList()
            val equal = row.size > 1 && heights.all { it != null }
            val rowH = if (equal) heights.maxOf { it ?: 0 } else 0
            var col = 0
            for ((k, i) in row.withIndex()) {
                val span = spans[i].coerceIn(1, HomeLayout.COLUMNS)
                val c = if (equal) Constraints.fixed(widths[k], rowH)
                else Constraints(minWidth = widths[k], maxWidth = widths[k], minHeight = 0, maxHeight = Constraints.Infinity)
                placeables[i] = measurables[i].measure(c)
                xs[i] = (col * (colW + g)).roundToInt()
                ys[i] = y
                col += span
            }
            val h = if (equal) rowH else row.maxOf { placeables[it]?.height ?: 0 }
            y += h + (if (ri < rows.lastIndex) g else 0)
        }
        layout(width, y) {
            var changed = false
            val live = HashSet<String>()
            for (i in 0 until n) {
                val p = placeables[i] ?: continue
                p.place(xs[i], ys[i])
                val r = IntRect(xs[i], ys[i], xs[i] + p.width, ys[i] + p.height)
                live += ids[i]
                if (dnd.rects[ids[i]] != r) {
                    dnd.rects[ids[i]] = r
                    changed = true
                }
            }
            if (dnd.rects.keys.retainAll(live)) changed = true
            if (changed) dnd.bump()
        }
    }
}

/** Los widgets que cambian de lugar se deslizan a su nuevo sitio en vez de saltar. */
@Composable
private fun Modifier.animatePlacement(enabled: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val motion = Fs.motion
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    val spec = motion.spring<IntOffset>(0.82f, 520f)
    val on = enabled && motion.enabled
    return this
        .onPlaced { c ->
            val p = c.positionInParent().round()
            if (anim == null) anim = Animatable(p, IntOffset.VectorConverter)
            target = p
        }
        .offset {
            val a = anim ?: return@offset IntOffset.Zero
            if (!on) {
                if (a.targetValue != target) scope.launch { a.snapTo(target) }
                IntOffset.Zero
            } else {
                if (a.targetValue != target) scope.launch { a.animateTo(target, spec) }
                a.value - target
            }
        }
}

// ───────────────────────── Gestos

/**
 * Mantener pulsado (un contenedor o cualquier parte de la pantalla) abre la edición. Se vigila antes que los hijos
 * (pasada Initial) y, al disparar, se «come» el resto del gesto: si no, al soltar el contenedor abriría su pantalla.
 * Mientras [enabled] es false no hace nada (durante la edición se arrastra en vez de abrir).
 */
@Composable
internal fun Modifier.longPressToEdit(enabled: Boolean = true, onLongPress: () -> Unit): Modifier {
    val callback = rememberUpdatedState(onLongPress)
    val on = rememberUpdatedState(enabled)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!on.value) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            val fired = try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeout
                        if (!ch.pressed) return@withTimeout
                        if ((ch.position - down.position).getDistance() > slop) return@withTimeout
                    }
                }
                false
            } catch (_: PointerEventTimeoutCancellationException) {
                true
            }
            if (fired) {
                callback.value()
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.forEach { it.consume() }
                    if (ev.changes.none { it.pressed }) break
                }
            }
        }
    }
}

/**
 * Reordenar en el modo de edición: se agarra un widget con un toque largo, o al instante desde su asa (arriba a la derecha).
 */
@Composable
private fun Modifier.homeReorder(enabled: Boolean, dnd: HomeDnd, onMove: () -> Unit, onDrop: () -> Unit): Modifier {
    if (!enabled) return this
    val hap = Fs.haptics
    val handlePx = with(LocalDensity.current) { 52.dp.toPx() }
    val move = rememberUpdatedState(onMove)
    val drop = rememberUpdatedState(onDrop)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val hit = dnd.hit(down.position) ?: return@awaitEachGesture
            val r = dnd.rects[hit] ?: return@awaitEachGesture
            val onHandle = down.position.x >= r.right - handlePx && down.position.y <= r.top + handlePx
            val start = (
                if (onHandle) awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                else awaitLongPressOrCancellation(down.id)
                ) ?: return@awaitEachGesture
            dnd.begin(hit, start.position)
            hap.heavy()
            try {
                drag(start.id) { change ->
                    change.consume()
                    dnd.pointerRoot = dnd.originInRoot + change.position
                    move.value()
                }
            } finally {
                dnd.end()
            }
            drop.value()
        }
    }
}

/** Mientras se arrastra cerca del borde de arriba o de abajo, la lista se desplaza sola. */
@Composable
private fun HomeAutoScroll(dnd: HomeDnd, listState: LazyListState, onTick: () -> Unit) {
    val density = LocalDensity.current
    val windowH = LocalWindowInfo.current.containerSize.height
    val topEdge = with(density) { (statusBarTop() + 72.dp + 24.dp).toPx() }
    val bottomEdge = with(density) { windowH - (LocalNavSpace.current + 24.dp).toPx() }
    val zone = with(density) { 64.dp.toPx() }
    val maxSpeed = with(density) { 18.dp.toPx() }
    val tick = rememberUpdatedState(onTick)
    val dragging = dnd.id != null
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (isActive && dnd.id != null) {
            withFrameNanos { }
            val y = dnd.pointerRoot.y
            val dy = when {
                y < topEdge -> -((topEdge - y) / zone).coerceIn(0f, 1f) * maxSpeed
                y > bottomEdge -> ((y - bottomEdge) / zone).coerceIn(0f, 1f) * maxSpeed
                else -> 0f
            }
            if (dy != 0f) listState.scrollBy(dy)
            tick.value()
        }
    }
}

// ───────────────────────── Un widget en la cuadrícula

@Composable
private fun HomeSlotBox(
    slot: HomeSlot,
    def: HomeWidgetDef,
    index: Int,
    tag: String,
    editing: Boolean,
    dnd: HomeDnd,
    onRemove: () -> Unit,
    onResize: (Int) -> Unit,
    content: @Composable (id: String, size: HomeSize, modifier: Modifier) -> Unit,
) {
    val size = HomeLayout.size(slot)
    val shape = if (def.inner) Fs.shapes.inner else Fs.shapes.card
    val dragging = dnd.id == slot.id
    Box(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                if (dnd.id == slot.id) {
                    dnd.version // se vuelve a calcular cuando cambia el acomodo
                    val r = dnd.rects[slot.id]
                    if (r != null) {
                        translationX = dnd.pointerRoot.x - dnd.grab.x - dnd.originInRoot.x - r.left
                        translationY = dnd.pointerRoot.y - dnd.grab.y - dnd.originInRoot.y - r.top
                    }
                    scaleX = 1.04f
                    scaleY = 1.04f
                    shadowElevation = 28f
                    this.shape = shape
                    clip = false
                }
            }
            .animatePlacement(enabled = !dragging)
            .testTag("${tag}_slot_${slot.id}"),
    ) {
        // El mismo widget en los dos modos (así conserva su estado); en edición no responde a toques y se ve atenuado.
        CompositionLocalProvider(LocalTapsDisabled provides editing) {
            Box(
                Modifier.fillMaxSize().then(
                    if (editing) Modifier.graphicsLayer { alpha = 0.5f }
                    else Modifier.enter(index),
                ),
            ) { content(slot.id, size, Modifier.fillMaxSize()) }
        }
        if (editing) EditOverlay(slot, def, shape, tag, onRemove, onResize)
    }
}

@Composable
private fun BoxScope.EditOverlay(slot: HomeSlot, def: HomeWidgetDef, shape: androidx.compose.ui.graphics.Shape, tag: String, onRemove: () -> Unit, onResize: (Int) -> Unit) {
    val c = Fs.colors
    // Capa que se queda con los toques (lo de abajo —gráficas, mascota…— no reacciona mientras se edita)
    // y que atenúa el widget para que los controles se lean bien encima.
    Box(
        Modifier.matchParentSize().clip(shape).background(c.bg.a(0.34f)).border(1.5.dp, c.accent.a(0.65f), shape).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val e = awaitPointerEvent()
                } while (e.changes.any { it.pressed })
            }
        },
    )
    if (def.removable) {
        EditButton(Ic.Close, c.bad, Color.White, "Quitar ${blockTitle(slot.id)}", true, Modifier.align(Alignment.TopStart).padding(5.dp).testTag("${tag}_remove_${slot.id}"), onRemove)
    }
    // Asa para arrastrar (el gesto lo recibe el contenedor; esto es solo la señal visual).
    Box(
        Modifier.align(Alignment.TopEnd).padding(5.dp).size(EDIT_BTN).clip(Fs.shapes.round).background(c.surface3.a(0.95f)).testTag("${tag}_grip_${slot.id}"),
        contentAlignment = Alignment.Center,
    ) { Icon(Ic.Grip, size = 17.dp, tint = c.text) }
    if (def.sizes.size > 1) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(5.dp).clip(Fs.shapes.pill).background(c.surface3.a(0.95f)).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EditButton(
                Ic.Minimize, Color.Transparent, c.text, "Achicar ${blockTitle(slot.id)}", HomeLayout.canResize(slot, -1),
                Modifier.testTag("${tag}_shrink_${slot.id}"),
            ) { onResize(-1) }
            EditButton(
                Ic.Maximize, Color.Transparent, c.text, "Agrandar ${blockTitle(slot.id)}", HomeLayout.canResize(slot, 1),
                Modifier.testTag("${tag}_grow_${slot.id}"),
            ) { onResize(1) }
        }
    }
}

private val EDIT_BTN = 30.dp

@Composable
private fun EditButton(ic: Ic, bg: Color, fg: Color, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(EDIT_BTN).semantics { contentDescription = label }
            .tappable(if (enabled) onClick else null, enabled = enabled, pressScale = 0.88f)
            .clip(Fs.shapes.round).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(ic, size = 16.dp, tint = if (enabled) fg else fg.a(0.3f)) }
}

// ───────────────────────── Pantalla de widgets

@Composable
internal fun PageGrid(
    editor: PageEditor,
    shownRaw: List<HomeSlot>,
    editing: Boolean,
    dnd: HomeDnd,
    listState: LazyListState,
    content: @Composable (id: String, size: HomeSize, modifier: Modifier) -> Unit,
) {
    val hap = Fs.haptics
    val shown = shownRaw.filter { HomeLayout.def(it.id) != null }
    val spans = shown.map { HomeLayout.size(it).span }
    val ids = shown.map { it.id }

    val tryMove = {
        val me = dnd.id
        val t = dnd.target()
        if (me != null && t != null) {
            val to = if (t == HomeDnd.END) editor.slots.lastIndex else editor.slots.indexOfFirst { it.id == t }
            if (to >= 0 && editor.move(me, to)) {
                dnd.swapped(t)
                hap.tick()
            }
        }
    }
    HomeAutoScroll(dnd, listState, tryMove)

    HomeFlow(spans, ids, dnd, Modifier.fillMaxWidth().homeReorder(editing, dnd, tryMove) {}) {
        shown.forEachIndexed { i, slot ->
            val def = HomeLayout.def(slot.id) ?: return@forEachIndexed
            key(slot.id) {
                HomeSlotBox(
                    slot, def, i, editor.tag, editing, dnd,
                    onRemove = { if (editor.remove(slot.id)) hap.tick() },
                    onResize = { step -> editor.resize(slot.id, step) },
                    content = content,
                )
            }
        }
    }
}

/** Barra de abajo en el modo de edición: agregar widgets y volver a la disposición de siempre. */
@Composable
internal fun HomeEditBar(editor: PageEditor, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val c = Fs.colors
    val tag = editor.tag
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FsButton("Agregar", { openAddWidgetSheet(app, editor) }, Modifier.weight(1f).testTag("${tag}_add"), icon = Ic.Plus, height = 48.dp)
            FsButton("Restablecer", { editor.reset() }, Modifier.weight(1f).testTag("${tag}_reset"), kind = BtnKind.Secondary, icon = Ic.RotateCcw, height = 48.dp)
        }
        Txt(
            "Arrastra el asa de un bloque (o mantenlo pulsado) para moverlo. Con − y + cambias su tamaño.",
            style = Fs.type.caption, color = c.textFaint, align = TextAlign.Center,
        )
    }
}

/** La pantalla sin ningún bloque. */
@Composable
internal fun HomeEmpty(editor: PageEditor, editing: Boolean) {
    val app = LocalApp.current
    val c = Fs.colors
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FitBot(size = 72.dp, mood = BotMood.Sleepy)
            Txt(if (editor.page == LayoutPage.HOME) "Tu inicio está vacío" else "Esta pantalla está vacía", style = Fs.type.h2, align = TextAlign.Center)
            Txt("Agrega los bloques y widgets que quieras y acomódalos a tu gusto.", style = Fs.type.small, color = c.textDim, align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            FsButton("Agregar widgets", { if (!editing) editor.start(); openAddWidgetSheet(app, editor) }, Modifier.testTag("${editor.tag}_empty_add"), icon = Ic.Plus, height = 48.dp)
        }
    }
}

/** El nombre de un bloque tal como lo ve el usuario (las tarjetas de rutina llevan el nombre de su rutina). */
@Composable
internal fun blockTitle(id: String): String {
    if (HomeLayout.isRoutineBlock(id)) {
        val rid = id.removePrefix(HomeLayout.ROUTINE_PREFIX)
        return LocalRepo.current.routines.firstOrNull { it.id == rid }?.name ?: "Rutina"
    }
    return HomeLayout.def(id)?.title ?: id
}

/** Hoja con los bloques y widgets que todavía no están en la pantalla. */
fun openAddWidgetSheet(app: AppState, editor: PageEditor = app.home) {
    app.overlays.sheet { close ->
        val c = Fs.colors
        val missing = HomeLayout.missing(editor.slots, editor.page)
        val own = missing.filter { it.page != null }
        val hub = missing.filter { it.page == null }
        SheetBody(
            "Agregar a ${editor.title.lowercase()}",
            subtitle = if (editor.page == LayoutPage.HOME) "Se agrega al final; luego arrástralo adonde quieras."
            else "Bloques de esta pantalla y widgets del inicio. Se agregan al final; luego arrástralos adonde quieras.",
        ) {
            if (missing.isEmpty()) Txt("Ya tienes todo en esta pantalla.", style = Fs.type.body, color = c.textDim)
            @Composable
            fun item(d: HomeWidgetDef) {
                Row(
                    Modifier.fillMaxWidth().testTag("${editor.tag}_add_${d.id}").tappable({ if (editor.add(d.id)) close() }, pressScale = 0.985f)
                        .clip(Fs.shapes.inner).background(c.surface2).border(1.dp, c.outline.a(0.6f), Fs.shapes.inner)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(icOf(d.icon, Ic.Widgets), size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Txt(d.title, style = Fs.type.bodyStrong, maxLines = 1)
                        Txt(d.desc, style = Fs.type.small, color = c.textDim, maxLines = 2)
                        Txt("Tamaños: " + d.sizes.joinToString(" · ") { it.label }, Modifier.padding(top = 2.dp), Fs.type.caption, c.textFaint, maxLines = 1)
                    }
                    Icon(Ic.Plus, size = 20.dp, tint = c.accentText)
                }
            }
            if (own.isNotEmpty()) {
                Label("De ${editor.title.lowercase()}")
                for (d in own) item(d)
            }
            if (hub.isNotEmpty()) {
                if (own.isNotEmpty()) Label("Widgets del inicio", Modifier.padding(top = 6.dp))
                for (d in hub) item(d)
            }
        }
    }
}

/** En Ajustes › Inicio y navegación: entrar a editar cada pantalla o volver a la disposición de siempre. */
@Composable
internal fun HomeLayoutSection() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    GroupCard(title = "Distribución de las pantallas") {
        Column {
            for ((i, page) in LayoutPage.all.withIndex()) {
                if (i > 0) HairLine(Modifier.padding(horizontal = 16.dp))
                val ed = app.editorOf(page)
                val route = when (page) {
                    LayoutPage.EXERCISES -> Route.Exercises
                    LayoutPage.ROUTINES -> Route.Routines
                    else -> Route.Home
                }
                ListRow(
                    if (page == LayoutPage.HOME) "Editar el inicio" else "Editar ${ed.title.lowercase()}",
                    desc = when (page) {
                        LayoutPage.HOME -> "Mueve, achica, agranda, quita o agrega widgets. También puedes mantener pulsado cualquier widget (o la pantalla) en Inicio."
                        else -> "Acomoda los bloques a tu gusto y agrega widgets del inicio. También puedes mantener pulsado un bloque (o la pantalla) en ${ed.title}."
                    },
                    icon = if (page == LayoutPage.HOME) Ic.Widgets else if (page == LayoutPage.EXERCISES) Ic.Dumbbell else Ic.Routines,
                    onClick = { ed.start(); app.nav.switchTab(route) },
                )
            }
            HairLine(Modifier.padding(horizontal = 16.dp))
            ListRow(
                "Restablecer el inicio", desc = "Vuelve a los widgets y tamaños de siempre.", icon = Ic.RotateCcw,
                onClick = {
                    if (app.home.active) app.home.reset()
                    else {
                        repo.setHomeLayout(HomeLayout.default(), "Inicio restablecido")
                        app.toastUndo("Inicio restablecido")
                    }
                },
            )
            for (page in listOf(LayoutPage.EXERCISES, LayoutPage.ROUTINES)) {
                HairLine(Modifier.padding(horizontal = 16.dp))
                val ed = app.editorOf(page)
                ListRow(
                    "Restablecer ${ed.title.lowercase()}", desc = "Vuelve a la distribución de siempre de ${ed.title}.", icon = Ic.RotateCcw,
                    onClick = {
                        if (ed.active) ed.reset()
                        else {
                            repo.setPageLayout(page, null, "${ed.title} restablecido")
                            app.toastUndo("${ed.title} restablecido")
                        }
                    },
                )
            }
        }
    }
}
