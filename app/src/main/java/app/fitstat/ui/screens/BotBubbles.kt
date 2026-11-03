package app.fitstat.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.bot.ActionRecord
import app.fitstat.bot.Attachment
import app.fitstat.bot.BotController
import app.fitstat.bot.ChatMessage
import app.fitstat.data.Fmt
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.BotMood
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.MascotController
import app.fitstat.ui.design.Markdown
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

// ───────────────────────── Avatar y burbujas

/** El ícono de FITBOT que acompaña cada respuesta. */
@Composable
internal fun BotAvatar(size: Dp = 34.dp, mood: BotMood = BotMood.Idle, animated: Boolean = false, controller: MascotController? = null) {
    val c = Fs.colors
    Box(
        Modifier.size(size + 6.dp).clip(Fs.shapes.round).background(c.accent.a(0.12f)).border(1.dp, c.accent.a(0.28f), Fs.shapes.round),
        contentAlignment = Alignment.Center,
    ) {
        FitBot(size = size + 2.dp, mood = mood, animated = animated, controller = controller)
    }
}

@Composable
internal fun AssistantBubble(modifier: Modifier = Modifier, error: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = Fs.colors
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
    Column(
        modifier.clip(shape)
            .background(if (error) c.bad.a(0.10f) else c.surface)
            .border(1.dp, if (error) c.bad.a(0.45f) else c.outline.a(0.5f), shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun UserBubble(m: ChatMessage, attachDir: File, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
    val brush = if (Fs.ui.gradients) Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.1f), c.accent2)) else Brush.linearGradient(listOf(c.accent, c.accent))
    Column(
        modifier.widthIn(max = 310.dp).clip(shape).background(brush).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (m.attachments.isNotEmpty()) AttachmentViews(m.attachments, attachDir, onAccent = true)
        if (m.text.isNotBlank()) Txt(m.text, style = Fs.type.body, color = c.onAccent)
    }
}

// ───────────────────────── Adjuntos

@Composable
internal fun AttachmentViews(list: List<Attachment>, dir: File, onAccent: Boolean = false, onRemove: ((Attachment) -> Unit)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (a in list) {
            if (a.kind == "image") ImageThumb(a, dir, onRemove) else FileChip(a, onAccent, onRemove)
        }
    }
}

@Composable
private fun ImageThumb(a: Attachment, dir: File, onRemove: ((Attachment) -> Unit)?) {
    val c = Fs.colors
    val bmp: ImageBitmap? by produceState<ImageBitmap?>(null, a.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(dir, a.path)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / s > 520) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(width = 150.dp, height = 110.dp).clip(RoundedCornerShape(16.dp)).background(c.surface3)) {
        bmp?.let { Image(it, a.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        if (onRemove != null) RemoveDot(Modifier.align(Alignment.TopEnd).padding(5.dp)) { onRemove(a) }
    }
}

@Composable
private fun FileChip(a: Attachment, onAccent: Boolean, onRemove: ((Attachment) -> Unit)?) {
    val c = Fs.colors
    val bg = if (onAccent) Color.White.a(0.2f) else c.surface3
    val fg = if (onAccent) c.onAccent else c.text
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(start = 10.dp, end = if (onRemove != null) 4.dp else 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (a.kind == "audio") Ic.Mic else Ic.File, size = 18.dp, tint = fg)
        Txt(
            if (a.kind == "audio") (if (a.seconds > 0) "Audio · ${Fmt.duration(a.seconds)}" else a.name) else a.name,
            Modifier.widthIn(max = 170.dp), Fs.type.smallStrong, fg, maxLines = 1,
        )
        if (onRemove != null) RemoveDot(Modifier) { onRemove(a) }
    }
}

@Composable
private fun RemoveDot(modifier: Modifier, onClick: () -> Unit) {
    val c = Fs.colors
    Box(modifier.size(24.dp).tappable(onClick).clip(Fs.shapes.round).background(c.bg.a(0.78f)), contentAlignment = Alignment.Center) {
        Icon(Ic.Close, size = 14.dp, tint = c.text, style = "line", weight = 2.6f)
    }
}

// ───────────────────────── Indicador de "escribiendo"

@Composable
internal fun TypingDots(color: Color = Fs.colors.textDim) {
    val m = Fs.motion
    val tr = rememberInfiniteTransition(label = "dots")
    val t by if (m.enabled) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "t") else remember { mutableStateOf(0.5f) }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(18.dp)) {
        repeat(3) { i ->
            val p = (t + i * 0.16f) % 1f
            val bump = sin(p * PI.toFloat()).let { it * it }
            Box(
                Modifier.size(8.dp).graphicsLayer {
                    val s = 0.65f + 0.45f * bump
                    scaleX = s; scaleY = s; alpha = 0.4f + 0.6f * bump; translationY = -3.dp.toPx() * bump
                }.clip(Fs.shapes.round).background(color),
            )
        }
    }
}

// ───────────────────────── Razonamiento

@Composable
internal fun ReasoningBlock(text: String, live: Boolean) {
    val c = Fs.colors
    var open by remember(live) { mutableStateOf(live) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.accent.a(0.08f)).border(1.dp, c.accent.a(0.2f), shape)
            .tappable({ open = !open }, pressScale = 0.99f).animateContentSize().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.Brain, size = 18.dp, tint = c.accentText)
            Txt(if (live) "Razonando…" else "Razonamiento", Modifier.weight(1f), Fs.type.smallStrong, c.accentText, maxLines = 1)
            if (live) TypingDots(c.accentText)
            Icon(if (open) Ic.ChevronUp else Ic.ChevronDown, size = 16.dp, tint = c.accentText)
        }
        if (open) Txt(text.trim().let { if (live) it.takeLast(480) else it.take(6000) }, style = Fs.type.small, color = c.textDim)
    }
}

// ───────────────────────── Acciones de FITBOT

@Composable
internal fun ActionChips(actions: List<ActionRecord>) {
    if (actions.isEmpty()) return
    val shown = actions.filter { it.write || it.tool == "notice" || it.cancelled || !it.ok }
    val reads = actions.filter { !it.write && it.tool != "notice" && it.ok }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shown.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in shown) ActionChip(a)
            }
        }
        if (reads.isNotEmpty()) ReadsChip(reads)
    }
}

@Composable
private fun ActionChip(a: ActionRecord) {
    val c = Fs.colors
    val m = Fs.motion
    val (tint, ic) = when {
        a.cancelled -> c.warn to Ic.Close
        !a.ok -> c.bad to Ic.Alert
        a.tool == "notice" -> c.warn to Ic.Info
        else -> c.good to Ic.Check
    }
    val p = remember { Animatable(if (m.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (m.enabled) p.animateTo(1f, m.spring(0.65f, 520f)) }
    Row(
        Modifier.graphicsLayer { scaleX = 0.7f + 0.3f * p.value; scaleY = scaleX; alpha = p.value.coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(14.dp)).background(tint.a(0.13f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(ic, size = 16.dp, tint = tint, style = "line", weight = 2.6f)
        Txt(a.summary, Modifier.widthIn(max = 280.dp), Fs.type.smallStrong, c.text, maxLines = 3)
    }
}

@Composable
private fun ReadsChip(reads: List<ActionRecord>) {
    val c = Fs.colors
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface3.a(0.6f)).tappable({ open = !open }, pressScale = 0.99f).animateContentSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Ic.Eye, size = 16.dp, tint = c.textDim)
            Txt(if (reads.size == 1) reads[0].summary else "Consultó ${reads.size} cosas en tu app", style = Fs.type.smallStrong, color = c.textDim, maxLines = 1)
            if (reads.size > 1) Icon(if (open) Ic.ChevronUp else Ic.ChevronDown, size = 14.dp, tint = c.textDim)
        }
        if (open && reads.size > 1) for (r in reads) Txt("• " + r.summary, Modifier.padding(start = 23.dp), Fs.type.small, c.textDim)
    }
}

// ───────────────────────── Permiso para una acción

@Composable
internal fun ConfirmCard(p: BotController.Pending, onAnswer: (Boolean) -> Unit) {
    val c = Fs.colors
    val tint = if (p.destructive) c.bad else c.accent
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(tint.a(0.10f)).border(1.5.dp, tint.a(0.5f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (p.destructive) Ic.Alert else Ic.Shield, size = 20.dp, tint = if (p.destructive) c.bad else c.accentText)
            Txt("FITBOT pide tu permiso", style = Fs.type.smallStrong, color = if (p.destructive) c.bad else c.accentText)
        }
        Txt(p.title, style = Fs.type.bodyStrong)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FsButton("No", { onAnswer(false) }, Modifier.weight(1f), kind = BtnKind.Secondary, height = 44.dp)
            FsButton("Permitir", { onAnswer(true) }, Modifier.weight(1f), kind = if (p.destructive) BtnKind.Danger else BtnKind.Primary, height = 44.dp)
        }
    }
}

internal fun costLabel(m: ChatMessage): String? {
    if (m.tokens <= 0 && m.cost == null) return null
    val parts = ArrayList<String>()
    if (m.tokens > 0) parts += if (m.tokens >= 1000) "%.1fk tokens".format(java.util.Locale.US, m.tokens / 1000.0) else "${m.tokens} tokens"
    m.cost?.let { if (it > 0.0) parts += if (it < 0.01) "$%.4f".format(java.util.Locale.US, it) else "$%.2f".format(java.util.Locale.US, it) }
    return parts.joinToString(" · ").ifEmpty { null }
}
