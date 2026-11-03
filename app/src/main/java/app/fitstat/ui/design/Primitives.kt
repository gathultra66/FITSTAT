package app.fitstat.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.LocalContentColor
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix

// ───────────────────────── Texto

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Fs.type.body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    align: TextAlign? = null,
    upper: Boolean = false,
    softWrap: Boolean = true,
) {
    val c = if (color != Color.Unspecified) color else Fs.content
    BasicText(
        if (upper) text.uppercase() else text,
        modifier,
        style.copy(color = c, textAlign = align ?: TextAlign.Unspecified),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}

@Composable
fun Txt(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = Fs.type.body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    align: TextAlign? = null,
) {
    val c = if (color != Color.Unspecified) color else Fs.content
    BasicText(text, modifier, style.copy(color = c, textAlign = align ?: TextAlign.Unspecified), overflow = overflow, maxLines = maxLines)
}

/** Etiqueta pequeña (respeta el ajuste de mayúsculas). */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Fs.colors.textDim, maxLines: Int = 1) {
    Txt(text, modifier, Fs.type.label, color, maxLines = maxLines, upper = Fs.type.caps || Fs.skin.caps)
}

// ───────────────────────── Interacción

/** Con true, todo lo que use [tappable] deja de responder (así el inicio en modo de edición no abre pantallas). */
val LocalTapsDisabled = androidx.compose.runtime.compositionLocalOf { false }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tappable(
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    pressScale: Float = 0.97f,
    onLongClick: (() -> Unit)? = null,
    haptic: Boolean = true,
    role: Role? = Role.Button,
): Modifier {
    if (onClick == null && onLongClick == null) return this
    if (LocalTapsDisabled.current) return this
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val m = Fs.motion
    val sc by animateFloatAsState(if (pressed && m.press) pressScale else 1f, m.spring(0.6f, 900f), label = "press")
    val hap = Fs.haptics
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .combinedClickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = { if (haptic) hap.tick(); onClick?.invoke() },
            onLongClick = onLongClick?.let { l -> { hap.heavy(); l() } },
        )
}

// ───────────────────────── Tarjetas

/** Tarjeta base de la app. Su apariencia depende del ajuste 'Estilo de tarjetas'. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = Fs.shapes.card,
    color: Color = Fs.colors.surface,
    accent: Boolean = false,
    padding: PaddingValues = PaddingValues(Fs.ui.pad(16)),
    /** Va dentro de la tarjeta, detrás del contenido (por ejemplo, una animación de fondo). */
    behind: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Fs.colors
    val ui = Fs.ui
    val style = ui.cardStyle
    // El modificador del llamador va primero (afuera): así funcionan weight, tamaño, entrada animada, etc.
    val base = modifier.fillMaxWidth().tappable(onClick, onLongClick = onLongClick, pressScale = 0.985f)
    val decorated = if (accent) {
        val brush = if (ui.gradients) Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.10f), c.accent2)) else Brush.linearGradient(listOf(c.accent, c.accent))
        base
            .then(if (ui.glow > 0.3f) Modifier.shadow((14 * ui.glow).dp, shape, clip = false, ambientColor = c.accent, spotColor = c.accent) else Modifier)
            .clip(shape)
            .background(brush)
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.a(0.35f), Color.Transparent))), shape)
    } else when (style) {
        "flat" -> base.clip(shape).background(color)
        "outline" -> base.clip(shape).background(color).border(1.5.dp, c.outline, shape)
        "glass" -> base.clip(shape).background(color.a(0.62f)).border(
            BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.a(if (c.dark) 0.20f else 0.7f), Color.White.a(0.03f)))), shape,
        )
        else -> base
            .then(if (c.dark) Modifier else Modifier.shadow(6.dp, shape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)))
            .clip(shape)
            .background(color)
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.a(if (c.dark) 0.075f else 0.9f), Color.White.a(0f)))), shape)
    }
    CompositionLocalProvider(LocalContentColor provides (if (accent) c.onAccent else c.text)) {
        Column(decorated.then(behind).skinCardDecor(accent).padding(padding), content = content)
    }
}

// ───────────────────────── Íconos con contenedor, píldoras, insignias

@Composable
fun IconBadge(
    ic: Ic,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Fs.colors.accentText,
    bg: Color = Fs.colors.accentSoft,
    iconSize: Dp = size * 0.52f,
    onClick: (() -> Unit)? = null,
    container: String = Fs.ui.iconContainer,
) {
    val shape: Shape = Fs.shapes.iconShape(container)
    val mod = modifier.size(size).tappable(onClick)
    Box(
        if (container == "none") mod else mod.clip(shape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ic, size = if (container == "none") size * 0.8f else iconSize, tint = tint)
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: Ic? = null,
    height: Dp = 34.dp,
    soft: Boolean = false,
) {
    val c = Fs.colors
    val bg = when {
        selected -> c.accent
        soft -> c.accentSoft
        else -> c.surface2
    }
    val fg = when {
        selected -> c.onAccent
        soft -> c.accentText
        else -> c.textDim
    }
    Row(
        modifier.height(height).tappable(onClick).clip(Fs.shapes.pill).background(bg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, size = 16.dp, tint = fg)
        Txt(text, style = Fs.type.smallStrong, color = fg, maxLines = 1)
    }
}

/** Círculo con un número (como el '2' de "Hoy" en la referencia). */
@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier, size: Dp = 28.dp, selected: Boolean = false) {
    val c = Fs.colors
    Box(
        modifier.size(size).clip(Fs.shapes.round).background(if (selected) c.accent else c.accent.a(0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Txt(n.toString(), style = Fs.type.smallStrong, color = if (selected) c.onAccent else c.accentText, maxLines = 1)
    }
}

@Composable
fun HairLine(modifier: Modifier = Modifier, color: Color = Fs.colors.outline.a(0.6f)) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val sk = Fs.skin
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!sk.isDefault) Box(Modifier.padding(end = 9.dp).size(width = 4.dp, height = 18.dp).background(Fs.colors.accent))
        Txt(sk.word(text), Modifier.weight(1f), Fs.type.h2, maxLines = 1, upper = sk.caps)
        if (trailing != null) trailing()
        if (action != null) {
            Txt(action, Modifier.tappable(onAction).padding(start = 12.dp, top = 4.dp, bottom = 4.dp), Fs.type.smallStrong, Fs.colors.accentText)
        }
    }
}
