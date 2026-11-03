package app.fitstat.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.data.Exercise
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

/** Fotos de ejercicios apiladas (con "+N" al final), como el grupo de avatares de la referencia. */
@Composable
fun PhotoStack(
    exercises: List<Exercise>,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    max: Int = 3,
    ring: Color = Fs.colors.surface,
) {
    val c = Fs.colors
    val shown = exercises.take(max)
    val extra = exercises.size - shown.size
    val step = size * 0.66f
    val slots = shown.size + (if (extra > 0) 1 else 0)
    if (slots == 0) return
    Box(modifier.width(size + step * (slots - 1)).height(size)) {
        shown.forEachIndexed { i, ex ->
            Box(Modifier.offset(x = step * i).size(size).clip(Fs.shapes.round).background(ring), contentAlignment = Alignment.Center) {
                ExerciseThumb(ex, size - 5.dp, shape = Fs.shapes.round)
            }
        }
        if (extra > 0) {
            Box(
                Modifier.offset(x = step * shown.size).size(size).clip(Fs.shapes.round).background(ring),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(size - 5.dp).clip(Fs.shapes.round).background(c.accent.a(0.28f)), contentAlignment = Alignment.Center) {
                    Txt("+$extra", style = Fs.type.smallStrong, color = c.accentText, maxLines = 1)
                }
            }
        }
    }
}

/** Mensaje de pantalla vacía con FITBOT. */
@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    mood: BotMood = BotMood.Idle,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    botSize: Dp = 96.dp,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FitBot(size = botSize, mood = mood)
        Txt(title, style = Fs.type.h3, align = TextAlign.Center)
        Txt(text, style = Fs.type.small, color = Fs.colors.textDim, align = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            FsButton(actionLabel, onAction, Modifier.padding(top = 8.dp), height = 46.dp)
        }
    }
}

/** Fila de chips que salta de línea cuando no caben. */
@Composable
fun ChipFlow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
        content()
    }
}

/** Dato destacado: número grande con etiqueta (usa el estilo de "Números grandes"). */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    sub: String? = null,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    color: Color = Fs.colors.surface,
) {
    val c = Fs.colors
    val dim = if (accent) c.onAccentDim else c.textDim
    Card(modifier, onClick = onClick, accent = accent, color = color, padding = androidx.compose.foundation.layout.PaddingValues(Fs.ui.pad(14)), shape = Fs.shapes.inner) {
        Label(label, color = dim)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            BigNumber(value, style = Fs.type.h1.copy(fontSize = Fs.type.h1.fontSize * 1.1f), color = if (accent) c.onAccent else c.text)
            if (unit != null) Txt(unit, Modifier.padding(bottom = 3.dp), Fs.type.caption, dim, maxLines = 1)
        }
        if (sub != null) Txt(sub, Modifier.padding(top = 2.dp), Fs.type.caption, dim, maxLines = 1)
    }
}

/**
 * Número grande. Según el ajuste 'Números grandes' se dibuja con la fuente normal, en
 * matriz de puntos (LED) o monoespaciado.
 */
@Composable
fun BigNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = Fs.type.bigNumber,
    color: Color = Fs.content,
) {
    if (Fs.type.digitsMode == "led") {
        val px = style.fontSize.value
        val pitch = (px / 7f * 0.95f).coerceIn(2.2f, 8f)
        LedText(text, modifier, dot = (pitch * 0.76f).dp, gap = (pitch * 0.24f).dp, lit = color, glow = true)
    } else {
        Txt(text, modifier, if (Fs.type.digitsMode == "mono") style.copy(fontFamily = app.fitstat.ui.theme.Fonts.mono()) else style, color, maxLines = 1)
    }
}

/** Contenedor con título de sección de ajustes (agrupa filas dentro de una tarjeta). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Label(title, Modifier.padding(start = 8.dp), color = Fs.colors.textFaint)
        Card(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            content()
        }
    }
}
