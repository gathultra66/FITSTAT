package app.fitstat.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.data.Fmt
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Botón redondo que repite la acción mientras se mantiene presionado (cada vez más rápido). */
@Composable
fun RepeatButton(ic: Ic, onStep: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val c = Fs.colors
    val haptics = Fs.haptics
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Fs.motion.spring(0.6f, 700f), label = "repeatScale")
    Box(
        modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Fs.shapes.pill).background(c.surface2).border(1.dp, c.outline.a(0.7f), Fs.shapes.pill)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptics.tick()
                    step()
                    val job = scope.launch {
                        delay(420)
                        var d = 140L
                        while (true) {
                            haptics.tick()
                            step()
                            delay(d)
                            d = (d * 0.9).toLong().coerceAtLeast(40L)
                        }
                    }
                    try { tryAwaitRelease() } finally { job.cancel(); pressed = false }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(ic, size = size * 0.42f, tint = c.text, style = "line", weight = 2.6f)
    }
}

/**
 * Selector numérico: botones − / + (mantén presionado para repetir) y el valor se puede escribir tocándolo.
 */
@Composable
fun NumberStepper(
    value: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    min: Double = 0.0,
    max: Double = 9999.0,
    decimals: Int = 1,
    unit: String? = null,
    big: Boolean = true,
    zeroLabel: String? = null,
    stacked: Boolean = false,
) {
    val c = Fs.colors
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var focused by remember { mutableStateOf(false) }
    var tf by remember { mutableStateOf(TextFieldValue(Fmt.num(value, decimals))) }
    LaunchedEffect(value, focused) { if (!focused) tf = TextFieldValue(Fmt.num(value, decimals)) }

    fun apply(v: Double) {
        val p = Math.pow(10.0, decimals.toDouble())
        change((Math.round(v.coerceIn(min, max) * p) / p))
    }

    val btn = if (big) 52.dp else 42.dp
    val fieldW = if (stacked) 132.dp else if (big) 104.dp else 76.dp

    @Composable
    fun Field() {
        Column(Modifier.width(fieldW), horizontalAlignment = Alignment.CenterHorizontally) {
            val style = (if (big) Fs.type.title else Fs.type.h2).copy(color = c.text, textAlign = TextAlign.Center)
            BasicTextField(
                value = tf,
                onValueChange = { nv ->
                    val cleaned = nv.text.replace(',', '.').filter { it.isDigit() || it == '.' }.let { s ->
                        if (s.count { ch -> ch == '.' } > 1 || (decimals == 0 && s.contains('.'))) tf.text else s
                    }.take(7)
                    tf = nv.copy(text = cleaned)
                    cleaned.toDoubleOrNull()?.let { apply(it) }
                },
                modifier = Modifier.width(fieldW).onFocusChanged { f ->
                    focused = f.isFocused
                    tf = if (f.isFocused) TextFieldValue(tf.text, TextRange(0, tf.text.length)) else TextFieldValue(Fmt.num(cur, decimals))
                },
                textStyle = style,
                singleLine = true,
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number),
            )
            if (zeroLabel != null && value <= 0.0) Txt(zeroLabel, style = Fs.type.caption, color = c.accentText, maxLines = 1, align = TextAlign.Center)
            else if (unit != null) Txt(unit, style = Fs.type.caption, color = c.textDim, maxLines = 1, align = TextAlign.Center)
        }
    }

    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RepeatButton(Ic.Minus, { apply(cur - step) }, size = btn)
                RepeatButton(Ic.Plus, { apply(cur + step) }, size = btn)
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RepeatButton(Ic.Minus, { apply(cur - step) }, size = btn)
            Field()
            RepeatButton(Ic.Plus, { apply(cur + step) }, size = btn)
        }
    }
}
