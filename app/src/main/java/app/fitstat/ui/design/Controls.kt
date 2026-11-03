package app.fitstat.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import kotlin.math.roundToInt

enum class BtnKind { Primary, Secondary, Ghost, Danger }

@Composable
fun FsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Primary,
    icon: Ic? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    trailingIcon: Ic? = null,
) {
    val c = Fs.colors
    val shape = Fs.shapes.button
    val ui = Fs.ui
    val fg = when (kind) {
        BtnKind.Primary -> c.onAccent
        BtnKind.Secondary -> c.text
        BtnKind.Ghost -> c.accentText
        BtnKind.Danger -> c.bad
    }.let { if (enabled) it else it.a(0.45f) }
    var base = modifier.height(height).tappable(if (enabled) onClick else null, enabled = enabled)
    base = when (kind) {
        BtnKind.Primary -> {
            val brush = if (ui.gradients) Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.12f), c.accent2)) else SolidColor(c.accent)
            base
                .then(if (ui.glow > 0.3f && enabled) Modifier.shadow((12 * ui.glow).dp, shape, clip = false, ambientColor = c.accent, spotColor = c.accent) else Modifier)
                .clip(shape)
                .background(if (enabled) brush else SolidColor(c.surface3))
                .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.a(0.35f), Color.Transparent))), shape)
        }
        BtnKind.Secondary -> base.clip(shape).background(c.surface2).border(1.dp, c.outline, shape)
        BtnKind.Ghost -> base.clip(shape)
        BtnKind.Danger -> base.clip(shape).background(c.bad.a(0.14f))
    }
    Row(
        base.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, size = 20.dp, tint = fg)
        Txt(Fs.skin.word(text), style = Fs.type.bodyStrong, color = fg, maxLines = 1, upper = Fs.skin.caps)
        if (trailingIcon != null) Icon(trailingIcon, size = 20.dp, tint = fg)
    }
}

/** Botón redondo con ícono (atrás, acciones de barra, etc.). */
@Composable
fun RoundButton(
    ic: Ic,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Fs.colors.text,
    bg: Color = Fs.colors.surface2,
    border: Boolean = true,
) {
    val c = Fs.colors
    val shape = Fs.shapes.iconShape(Fs.ui.iconContainer)
    Box(
        modifier.size(size).tappable(onClick).clip(shape).background(bg).then(if (border) Modifier.border(1.dp, c.outline.a(0.7f), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ic, size = size * 0.48f, tint = tint)
    }
}

// ───────────────────────── Interruptor

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val m = Fs.motion
    val hap = Fs.haptics
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, m.spring(0.7f, 700f), label = "toggle")
    val bg by animateColorAsState(if (checked) c.accent else c.surface3, m.tween(180), label = "toggleBg")
    Box(
        modifier.width(52.dp).height(30.dp)
            .clip(Fs.shapes.pill).background(bg)
            .pointerInput(checked) { detectTapGestures { hap.toggle(!checked); onChange(!checked) } }
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = x).size(24.dp).clip(Fs.shapes.round)
                .background(if (checked) c.onAccent else c.textDim),
        )
    }
}

// ───────────────────────── Deslizador

@Composable
fun FsSlider(
    value: Float,
    onChange: (Float) -> Unit,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    step: Float = 0f,
    onFinished: (() -> Unit)? = null,
) {
    val c = Fs.colors
    val cur by rememberUpdatedState(onChange)
    val fin by rememberUpdatedState(onFinished)
    val hap = Fs.haptics
    var dragging by remember { mutableStateOf(false) }
    val thumb by animateFloatAsState(if (dragging) 1.2f else 1f, Fs.motion.spring(0.6f, 800f), label = "thumb")
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    var lastStepped = remember { floatArrayOf(Float.NaN) }
    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .pointerInput(min, max, step) {
                fun toValue(x: Float): Float {
                    val pad = 14.dp.toPx()
                    val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    var v = min + f * (max - min)
                    if (step > 0f) v = (Math.round((v - min) / step) * step + min).coerceIn(min, max)
                    return v
                }
                fun emit(v: Float) {
                    if (v != lastStepped[0]) {
                        if (!lastStepped[0].isNaN()) hap.tick()
                        lastStepped[0] = v
                    }
                    cur(v)
                }
                detectTapGestures(onPress = { o -> dragging = true; emit(toValue(o.x)); tryAwaitRelease(); dragging = false; fin?.invoke() })
            }
            .pointerInput(min, max, step) {
                fun toValue(x: Float): Float {
                    val pad = 14.dp.toPx()
                    val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    var v = min + f * (max - min)
                    if (step > 0f) v = (Math.round((v - min) / step) * step + min).coerceIn(min, max)
                    return v
                }
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; fin?.invoke() },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    val v = toValue(change.position.x)
                    if (v != lastStepped[0]) {
                        if (!lastStepped[0].isNaN()) hap.tick()
                        lastStepped[0] = v
                    }
                    cur(v)
                }
            },
    ) {
        val pad = 14.dp.toPx()
        val h = 8.dp.toPx()
        val cy = size.height / 2f
        val w = size.width - 2 * pad
        drawRoundRect(c.surface3, Offset(pad, cy - h / 2), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        val fx = pad + w * frac
        drawRoundRect(
            Brush.horizontalGradient(listOf(c.accent2, c.accent), pad, fx.coerceAtLeast(pad + 1f)),
            Offset(pad, cy - h / 2), androidx.compose.ui.geometry.Size(fx - pad, h), androidx.compose.ui.geometry.CornerRadius(h / 2),
        )
        val r = 11.dp.toPx() * thumb
        drawCircle(c.accent.a(0.25f), r * 1.5f, Offset(fx, cy))
        drawCircle(Color.White, r, Offset(fx, cy))
        drawCircle(c.accent, r * 0.55f, Offset(fx, cy))
    }
}

// ───────────────────────── Segmentado

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
) {
    val c = Fs.colors
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(Fs.shapes.pill).background(c.surface2).padding(3.dp)) {
        val n = options.size.coerceAtLeast(1)
        val cell = maxWidth / n
        val x by animateDpAsState(cell * selected.coerceIn(0, n - 1), Fs.motion.spring(0.8f, 600f), label = "seg")
        Box(
            Modifier.offset(x = x).width(cell).fillMaxHeight()
                .clip(Fs.shapes.pill)
                .background(Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.1f), c.accent2))),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.width(cell).fillMaxHeight().tappable(onClick = { onSelect(i) }, pressScale = 0.96f, role = androidx.compose.ui.semantics.Role.Tab),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(label, style = Fs.type.smallStrong, color = if (i == selected) c.onAccent else c.textDim, maxLines = 1)
                }
            }
        }
    }
}

// ───────────────────────── Contador +/-

@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    big: Boolean = false,
) {
    val c = Fs.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundButton(Ic.Minus, onMinus, size = if (big) 52.dp else 40.dp)
        Row(Modifier.width(if (big) 96.dp else 64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Txt(value, style = if (big) Fs.type.title else Fs.type.h2, maxLines = 1)
            if (unit != null) Txt(unit, Modifier.padding(start = 3.dp, bottom = 3.dp), Fs.type.caption, c.textDim, maxLines = 1)
        }
        RoundButton(Ic.Plus, onPlus, size = if (big) 52.dp else 40.dp)
    }
}

// ───────────────────────── Campos de texto

@Composable
fun FsTextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: Ic? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
    ghost: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    password: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = Fs.type.body,
) {
    val c = Fs.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) c.accent else c.outline.a(0.7f), Fs.motion.tween(160), label = "tfBorder")
    val shape = Fs.shapes.inner
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        textStyle = textStyle.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onSearch = { onDone?.invoke() }, onSend = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
        decorationBox = { inner ->
            Row(
                Modifier.clip(shape).background(c.surface2).border(1.5.dp, border, shape)
                    .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 12.dp)
                    .then(if (singleLine) Modifier.height(50.dp) else Modifier),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leading != null) Icon(leading, Modifier.padding(top = if (singleLine) 0.dp else 2.dp), size = 20.dp, tint = if (focused) c.accentText else c.textDim)
                Box(Modifier.weight(1f), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) Txt(placeholder, style = textStyle, color = c.textFaint, maxLines = 1)
                    if (ghost != null && ghost.isNotEmpty() && value.isNotEmpty()) {
                        val ann = androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent)) { append(value) }
                            withStyle(androidx.compose.ui.text.SpanStyle(color = c.textFaint)) { append(ghost) }
                        }
                        Txt(ann, style = textStyle, maxLines = 1)
                    }
                    inner()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

// ───────────────────────── Selectores pequeños

@Composable
fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier, weekStartsMonday: Boolean = true) {
    val c = Fs.colors
    val order = if (weekStartsMonday) listOf(1, 2, 3, 4, 5, 6, 7) else listOf(7, 1, 2, 3, 4, 5, 6)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        order.forEach { d ->
            val on = d in selected
            val bg by animateColorAsState(if (on) c.accent else c.surface2, Fs.motion.tween(160), label = "wd")
            Box(
                Modifier.size(40.dp).tappable({ onToggle(d) }, pressScale = 0.9f).clip(Fs.shapes.round).background(bg)
                    .then(if (on) Modifier else Modifier.border(1.dp, c.outline.a(0.7f), Fs.shapes.round)),
                contentAlignment = Alignment.Center,
            ) {
                Txt(app.fitstat.data.Weekdays.short(d), style = Fs.type.smallStrong, color = if (on) c.onAccent else c.textDim)
            }
        }
    }
}

@Composable
fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = Fs.colors
    Box(modifier.size(size).tappable(onClick, pressScale = 0.9f), contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size(size).border(2.dp, color, Fs.shapes.round))
        Box(Modifier.size(size - (if (selected) 8.dp else 0.dp)).clip(Fs.shapes.round).background(color))
        if (selected) Icon(Ic.Check, size = size * 0.42f, tint = if (color.luminance() > 0.4f) Color(0xFF14111F) else Color.White, style = "line", weight = 2.6f)
    }
}
