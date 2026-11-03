package app.fitstat.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ───────────────────────── Fondo de la app

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Fs.colors
    val kind = Fs.ui.background
    Box(
        modifier.fillMaxSize().background(c.bg).drawBehind {
            when (kind) {
                "gradient" -> {
                    drawRect(
                        Brush.radialGradient(listOf(c.accent.a(if (c.dark) 0.16f else 0.18f), Color.Transparent), Offset(size.width * 0.95f, -size.height * 0.02f), size.width * 1.05f),
                    )
                    drawRect(
                        Brush.radialGradient(listOf(c.accent2.a(if (c.dark) 0.07f else 0.08f), Color.Transparent), Offset(-size.width * 0.1f, size.height * 0.55f), size.width * 0.9f),
                    )
                }
                "dots" -> {
                    val step = 15.dp.toPx()
                    val r = 1.1.dp.toPx()
                    var y = step / 2f
                    while (y < size.height) {
                        val fade = (1f - (y / size.height) * 0.85f).coerceIn(0.1f, 1f)
                        var x = step / 2f
                        while (x < size.width) {
                            drawCircle(c.text.a(0.07f * fade), r, Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                    drawRect(Brush.radialGradient(listOf(c.accent.a(0.10f), Color.Transparent), Offset(size.width * 0.9f, 0f), size.width))
                }
                "grid" -> {
                    val step = 36.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(c.text.a(0.045f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                    var y = 0f
                    while (y < size.height) { drawLine(c.text.a(0.045f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                }
                else -> {}
            }
        },
        content = {
            if (!Fs.skin.isDefault) SkinBackdrop()
            content()
        },
    )
}

// ───────────────────────── Entrada escalonada

/** Pantalla actual (para recordar qué entradas ya se animaron y no repetirlas al volver). */
val LocalScreenKey = compositionLocalOf { "" }

object EntranceMemory {
    val seen = HashSet<String>()
    fun reset() = seen.clear()
}

@Composable
fun Modifier.enter(index: Int = 0, dy: Dp = 18.dp): Modifier {
    val m = Fs.motion
    val key = LocalScreenKey.current + ":" + index
    val already = remember(key) { EntranceMemory.seen.contains(key) }
    if (!m.enabled || !m.entrance || already || index > 9) return this
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        EntranceMemory.seen.add(key)
        p.animateTo(1f, m.tween(520, m.stagger(index), FastOutSlowInEasing))
    }
    val px = with(LocalDensity.current) { dy.toPx() }
    val sk = Fs.skin
    if (sk.fxLevel(m) > 0f) {
        // Cada skin entra a su manera: Carmesí parpadea como una pantalla que enciende; Acero se desliza de lado como una persiana.
        val carmesi = sk.isCarmesi
        return this.graphicsLayer {
            val v = p.value
            if (carmesi) {
                val flick = if (v < 1f && ((v * 36f).toInt() % 2 == 1)) 0.55f else 1f
                alpha = v * flick
                translationX = (1f - v) * -px * 0.8f
            } else {
                alpha = v
                translationX = (1f - v) * px * 1.3f
            }
        }
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * px
    }
}

// ───────────────────────── Superposiciones: hojas, diálogos y avisos

@Stable
class SheetEntry(val id: Int, val content: @Composable (close: () -> Unit) -> Unit) {
    var visible by mutableStateOf(false)
}

@Stable
class DialogEntry(val id: Int, val content: @Composable (close: () -> Unit) -> Unit) {
    var visible by mutableStateOf(false)
}

@Stable
class ToastData(val id: Int, val text: String, val action: String?, val onAction: (() -> Unit)?, val icon: Ic?)

@Stable
class Overlays {
    internal val sheets: SnapshotStateList<SheetEntry> = mutableStateListOf()
    internal val dialogs: SnapshotStateList<DialogEntry> = mutableStateListOf()
    var toast by mutableStateOf<ToastData?>(null)
        internal set
    private var counter = 0
    internal var scopeClose: ((Any) -> Unit)? = null

    fun sheet(content: @Composable (close: () -> Unit) -> Unit) {
        val e = SheetEntry(++counter, content)
        sheets.add(e)
        e.visible = true
    }

    fun dialog(content: @Composable (close: () -> Unit) -> Unit) {
        val e = DialogEntry(++counter, content)
        dialogs.add(e)
        e.visible = true
    }

    fun toast(text: String, action: String? = null, icon: Ic? = null, onAction: (() -> Unit)? = null) {
        toast = ToastData(++counter, text, action, onAction, icon)
    }

    val hasOverlay: Boolean get() = sheets.any { it.visible } || dialogs.any { it.visible }

    /** Cierra la superposición de más arriba. Devuelve true si había alguna. */
    fun closeTop(): Boolean {
        dialogs.lastOrNull { it.visible }?.let { it.visible = false; scopeClose?.invoke(it); return true }
        sheets.lastOrNull { it.visible }?.let { it.visible = false; scopeClose?.invoke(it); return true }
        return false
    }

    fun confirm(
        title: String,
        message: String,
        confirmLabel: String = "Aceptar",
        danger: Boolean = false,
        cancelLabel: String = "Cancelar",
        onConfirm: () -> Unit,
    ) {
        dialog { close ->
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(title, style = Fs.type.h2)
                Txt(message, style = Fs.type.body, color = Fs.colors.textDim)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FsButton(cancelLabel, { close() }, Modifier.weight(1f), kind = BtnKind.Secondary, height = 48.dp)
                    FsButton(confirmLabel, { close(); onConfirm() }, Modifier.weight(1f), kind = if (danger) BtnKind.Danger else BtnKind.Primary, height = 48.dp)
                }
            }
        }
    }
}

val LocalOverlays = staticCompositionLocalOf { Overlays() }

/** Ancho máximo del contenido: en pantallas más anchas (tablet, teléfono acostado) la app es una columna centrada. */
val ContentMaxWidth = 760.dp

/** Márgenes laterales del sistema (barra de navegación o recorte de pantalla al acostar el teléfono). */
@Composable
fun sideInsets(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)

/** Altura que deja libre la barra de navegación inferior (para el relleno de las listas). */
val LocalNavSpace = compositionLocalOf { 0.dp }

/** Dibuja las hojas, diálogos y avisos por encima de la app. Va en la raíz. */
@Composable
fun OverlayHost(state: Overlays, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val m = Fs.motion
    val scope = rememberCoroutineScope()
    state.scopeClose = { entry ->
        scope.launch {
            delay(m.ms(300).toLong())
            when (entry) {
                is SheetEntry -> state.sheets.remove(entry)
                is DialogEntry -> state.dialogs.remove(entry)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        // Hojas inferiores
        for (e in state.sheets.toList()) {
            key(e.id) {
                val close = remember(e) { { e.visible = false; state.scopeClose?.invoke(e); Unit } }
                AnimatedVisibility(e.visible, Modifier.fillMaxSize(), enter = fadeIn(m.tween(220)), exit = fadeOut(m.tween(240))) {
                    Box(
                        Modifier.fillMaxSize().background(c.scrim).pointerInput(Unit) { detectTapGestures { close() } },
                    )
                }
                AnimatedVisibility(
                    e.visible, Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(m.tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(m.tween(200)),
                    exit = slideOutVertically(m.tween(260)) { it } + fadeOut(m.tween(240)),
                ) {
                    SheetPanel(close) { e.content(close) }
                }
            }
        }
        // Diálogos
        for (e in state.dialogs.toList()) {
            key(e.id) {
                val close = remember(e) { { e.visible = false; state.scopeClose?.invoke(e); Unit } }
                AnimatedVisibility(e.visible, Modifier.fillMaxSize(), enter = fadeIn(m.tween(200)), exit = fadeOut(m.tween(220))) {
                    Box(Modifier.fillMaxSize().background(c.scrim).pointerInput(Unit) { detectTapGestures { close() } })
                }
                AnimatedVisibility(
                    e.visible, Modifier.align(Alignment.Center),
                    enter = fadeIn(m.tween(220)) + androidx.compose.animation.scaleIn(m.tween(260), initialScale = 0.92f),
                    exit = fadeOut(m.tween(180)) + androidx.compose.animation.scaleOut(m.tween(200), targetScale = 0.95f),
                ) {
                    Box(
                        Modifier.padding(horizontal = 28.dp).fillMaxWidth().clip(Fs.shapes.card).background(c.surface)
                            .border(1.dp, c.outline, Fs.shapes.card).pointerInput(Unit) { detectTapGestures { } },
                    ) { e.content(close) }
                }
            }
        }
        // Aviso
        ToastHost(state, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SheetPanel(close: () -> Unit, content: @Composable () -> Unit) {
    val c = Fs.colors
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxWidth()
            .offset { IntOffset(0, drag.value.roundToInt().coerceAtLeast(0)) }
            .clip(Fs.shapes.sheet).background(c.surface)
            .border(1.dp, c.outline.a(0.8f), Fs.shapes.sheet)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.ime)
            .heightIn(max = 760.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { scope.launch { if (drag.value > dismissAt) close() else drag.animateTo(0f) } },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { change, dy -> change.consume(); scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(Fs.shapes.pill).background(c.textFaint.a(0.6f)))
        }
        Box(Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) { content() }
    }
}

@Composable
private fun ToastHost(state: Overlays, modifier: Modifier) {
    val c = Fs.colors
    val m = Fs.motion
    val t = state.toast
    val nav = LocalNavSpace.current
    LaunchedEffect(t?.id) {
        if (t != null) {
            delay(if (t.action != null) 5500 else 3200)
            if (state.toast?.id == t.id) state.toast = null
        }
    }
    AnimatedVisibility(
        t != null, modifier.padding(bottom = nav + 12.dp, start = 16.dp, end = 16.dp).widthIn(max = 560.dp),
        enter = slideInVertically(m.spring(0.8f)) { it } + fadeIn(m.tween(180)),
        exit = slideOutVertically(m.tween(200)) { it } + fadeOut(m.tween(180)),
    ) {
        val d = t ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().clip(Fs.shapes.toast).background(if (c.dark) c.surface3 else Color(0xFF1B1B22))
                .border(1.dp, if (Fs.skin.isDefault) Color.White.a(0.10f) else c.accent.a(0.45f), Fs.shapes.toast).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (d.icon != null) Icon(d.icon, size = 20.dp, tint = c.accent)
            Txt(d.text, Modifier.weight(1f).padding(vertical = 6.dp), Fs.type.small, if (c.dark) c.text else Color.White, maxLines = 3)
            if (d.action != null) {
                Box(
                    Modifier.height(36.dp).tappable({ d.onAction?.invoke(); state.toast = null }).clip(Fs.shapes.pill).background(c.accent.a(0.18f)).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(d.action, style = Fs.type.smallStrong, color = c.accent, maxLines = 1) }
            }
        }
    }
}

// ───────────────────────── Estructura de pantalla

@Composable
fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val sk = Fs.skin
    val shown = sk.word(title)
    val tag = if (subtitle == null) sk.tag(title) else null
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) RoundButton(Ic.ChevronLeft, onBack)
        Column(Modifier.weight(1f)) {
            // Los títulos largos bajan de tamaño para no cortarse tanto.
            val ts = when {
                shown.length <= 14 -> Fs.type.h1
                shown.length <= 20 -> Fs.type.h2
                else -> Fs.type.h3
            }
            Txt(shown, style = ts, maxLines = 1, upper = sk.caps)
            if (subtitle != null) Txt(subtitle, style = Fs.type.small, color = Fs.colors.textDim, maxLines = 1)
            else if (tag != null) Txt(tag, style = Fs.type.caption, color = Fs.colors.accentText, maxLines = 1)
        }
        actions()
    }
}

/**
 * Pantalla con encabezado fijo y lista que se desliza por debajo.
 * El relleno inferior deja espacio para la barra de navegación.
 */
@Composable
fun ScreenList(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    verticalGap: Dp = 14.dp,
    bottomExtra: Dp = 24.dp,
    showHeader: Boolean = true,
    /** Para la lista misma (por ejemplo, un gesto sobre toda la pantalla). */
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val c = Fs.colors
    val top = statusBarTop()
    val headerH = if (showHeader) 72.dp else 8.dp
    val navSpace = LocalNavSpace.current
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().then(listModifier),
            state = state,
            contentPadding = PaddingValues(start = Fs.ui.pad(16), end = Fs.ui.pad(16), top = top + headerH, bottom = navSpace + bottomExtra),
            verticalArrangement = Arrangement.spacedBy(Fs.ui.pad(verticalGap.value)),
            content = content,
        )
        if (showHeader) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(c.bg.a(0.97f), c.bg.a(0.9f), Color.Transparent)))
                    .padding(top = top),
            ) {
                ScreenHeader(title, subtitle = subtitle, onBack = onBack, actions = actions)
            }
        }
    }
}
