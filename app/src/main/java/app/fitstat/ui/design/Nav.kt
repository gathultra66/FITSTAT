package app.fitstat.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.times
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import kotlin.math.floor
import kotlin.math.min

class NavItem(val id: String, val label: String, val icon: Ic)

/** Barra inferior: píldora flotante con un indicador que se desliza, o barra fija abajo. */
@Composable
fun BottomNav(
    items: List<NavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    docked: Boolean = Fs.ui.dockedNav,
    labels: String = Fs.ui.navLabels,
) {
    val c = Fs.colors
    val m = Fs.motion
    val n = items.size
    val selIdx = items.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val pos by animateFloatAsState(selIdx.toFloat(), m.spring(0.8f, 380f), label = "navPos")
    val rects = remember(n) { FloatArray(n * 2) }
    val barH = if (docked) 66.dp else 64.dp
    val inset = if (docked) 8.dp else 6.dp
    val sk = Fs.skin
    val itemShape = Fs.shapes.navItem
    val barShape = Fs.shapes.navBar

    val indicator = Modifier.drawBehind {
        if (n == 0) return@drawBehind
        val i0 = floor(pos).toInt().coerceIn(0, n - 1)
        val i1 = min(i0 + 1, n - 1)
        val f = (pos - i0).coerceIn(0f, 1f)
        val l = rects[i0 * 2] + (rects[i1 * 2] - rects[i0 * 2]) * f
        val w = rects[i0 * 2 + 1] + (rects[i1 * 2 + 1] - rects[i0 * 2 + 1]) * f
        if (w > 1f) {
            val ins = inset.toPx()
            val h = size.height - 2 * ins
            val corner = CornerRadius(if (docked) h * 0.36f else h / 2f)
            val tl = Offset(l + ins, ins)
            val sz = Size((w - 2 * ins).coerceAtLeast(1f), h)
            if (itemShape != null) {
                // Skins: el indicador toma la forma de la skin (esquinas cortadas, rectas…).
                val outline = itemShape.createOutline(sz, layoutDirection, this)
                translate(tl.x, tl.y) {
                    drawOutline(outline, Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.10f), c.accent2)))
                    drawOutline(outline, Color.White.a(0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            } else {
                drawRoundRect(Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.14f), c.accent2)), tl, sz, corner)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.a(0.30f), Color.Transparent), ins, ins + h * 0.6f), tl, sz, corner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
            }
        }
    }
    val rowShape: Modifier = if (docked) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth()
            .shadow(if (c.dark) 14.dp else 10.dp, barShape, clip = false, ambientColor = Color.Black.a(0.5f), spotColor = Color.Black.a(0.5f))
            .clip(barShape).background(if (c.dark) c.surface2.a(0.97f) else c.surface2)
            .border(1.dp, if (sk.isDefault) c.outline.a(0.8f) else c.accent.a(0.35f), barShape)
    }
    Box(modifier.fillMaxWidth().then(if (docked) Modifier.background(c.surface) else Modifier).windowInsetsPadding(WindowInsets.navigationBars)) {
        if (docked) Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline.a(0.8f)))
        Box {
            Row(
                rowShape.height(barH).then(indicator),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { i, it ->
                    val active = i == selIdx
                    val wgt by animateFloatAsState(if (labels == "active" && active) 2.1f else 1f, m.spring(0.85f, 420f), label = "navW$i")
                    val fg by animateColorAsState(if (active) c.onAccent else c.textDim, m.tween(180), label = "navFg$i")
                    Box(
                        Modifier.weight(wgt).fillMaxHeight()
                            .onGloballyPositioned { co ->
                                rects[i * 2] = co.positionInParent().x
                                rects[i * 2 + 1] = co.size.width.toFloat()
                            }
                            .tappable({ onSelect(it.id) }, pressScale = 0.93f, role = Role.Tab, haptic = !active),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (labels) {
                            "always" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(it.icon, size = 23.dp, tint = fg)
                                Txt(it.label, style = fitNavLabel(Fs.type.caption, it.label.length), color = fg, maxLines = 1, upper = sk.caps)
                            }
                            "active" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(it.icon, size = 23.dp, tint = fg)
                                AnimatedVisibility(
                                    active,
                                    enter = expandHorizontally(m.tween(260)) + fadeIn(m.tween(260, 80)),
                                    exit = shrinkHorizontally(m.tween(180)) + fadeOut(m.tween(120)),
                                ) {
                                    Txt(it.label, Modifier.padding(start = 7.dp), Fs.type.smallStrong, fg, maxLines = 1, softWrap = false, upper = sk.caps)
                                }
                            }
                            else -> Icon(it.icon, size = 24.dp, tint = fg)
                        }
                    }
                }
            }
        }
    }
}

/** Etiquetas largas (p. ej. «OPERACIONES» en mayúsculas espaciadas) se encogen un poco para caber en su casilla. */
private fun fitNavLabel(base: androidx.compose.ui.text.TextStyle, len: Int): androidx.compose.ui.text.TextStyle {
    if (len <= 9 || !base.fontSize.isSpecified) return base
    val k = (9f / len).coerceAtLeast(0.7f)
    return base.copy(
        fontSize = base.fontSize * k,
        letterSpacing = if (base.letterSpacing.isSpecified) base.letterSpacing * 0.5f else base.letterSpacing,
    )
}

/** Altura que ocupa la barra (para dejar espacio al final de las listas). */
fun navBarSpace(docked: Boolean): androidx.compose.ui.unit.Dp = if (docked) 66.dp else 64.dp + 10.dp + 8.dp
