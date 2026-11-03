package app.fitstat.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.FsColors
import app.fitstat.ui.theme.a
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Efectos propios de las skins: fondo vivo, adornos de tarjetas, logo, transiciones entre pantallas,
 * pantalla de arranque al cambiar de skin y aviso de celebración. Todo respeta «Animaciones propias»
 * (apagadas / suaves / completas), el interruptor general de animaciones y la intensidad de efectos.
 */

private val TWO_PI = 2f * PI.toFloat()

// ───────────────────────── Reloj de la skin

/** Fase 0..1 de un ciclo de 8 segundos (siempre 0 si la skin no anima). */
val LocalSkinPhase = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Parpadeo de LED (cambia cada medio segundo): solo avisa a quien lo lee cuando cambia. */
val LocalSkinBlink = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }

@Composable
fun SkinHost(content: @Composable () -> Unit) {
    val s = Fs.skin
    val m = Fs.motion
    val phase: State<Float> = if (s.animates(m)) {
        rememberInfiniteTransition(label = "skin").animateFloat(
            0f, 1f, infiniteRepeatable(tween(m.ms(8000), easing = LinearEasing), RepeatMode.Restart), label = "skinPhase",
        )
    } else remember { mutableFloatStateOf(0f) }
    val blink = remember(phase) { derivedStateOf { (phase.value * 16f).toInt() % 2 == 0 } }
    CompositionLocalProvider(LocalSkinPhase provides phase, LocalSkinBlink provides blink, content = content)
}

// ───────────────────────── Fondo

/** Fondo de la skin (va dentro de [AppBackground]). Dos capas: una estática (guardada) y otra viva. */
@Composable
fun SkinBackdrop(modifier: Modifier = Modifier) {
    val s = Fs.skin
    val c = Fs.colors
    val m = Fs.motion
    val phase = LocalSkinPhase.current
    val lvl = s.fxLevel(m)
    val fx = s.fx.coerceIn(0f, 1f)
    val kind = s.bg
    if (kind == "plain") return
    val carmesi = s.isCarmesi
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { }.drawBehind {
                if (carmesi) carmesiStatic(c, kind, fx) else aceroStatic(c, kind, fx)
            },
        )
        if (lvl > 0f && kind == "full") {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    if (carmesi) carmesiLive(c, phase.value, lvl, fx) else aceroLive(c, phase.value, lvl, fx)
                },
            )
        }
    }
}

private fun DrawScope.carmesiStatic(c: FsColors, kind: String, fx: Float) {
    val w = size.width
    val h = size.height
    if (kind == "full") {
        drawRect(Brush.radialGradient(listOf(c.accent.a(0.22f * fx), Color.Transparent), Offset(w * 0.92f, -h * 0.02f), w * 1.15f))
        drawRect(Brush.radialGradient(listOf(c.accent2.a(0.10f * fx), Color.Transparent), Offset(-w * 0.1f, h * 0.62f), w * 0.95f))
    }
    // matriz de puntos LED que se apaga hacia abajo
    val step = 10.dp.toPx()
    val dot = 1.5.dp.toPx()
    val bands = 3
    for (b in 0 until bands) {
        val pts = ArrayList<Offset>()
        var y = step / 2f + b * (h / bands / step).toInt() * step
        val yEnd = if (b == bands - 1) h else step / 2f + (b + 1) * (h / bands / step).toInt() * step
        while (y < yEnd) {
            var x = step / 2f
            while (x < w) { pts.add(Offset(x, y)); x += step }
            y += step
        }
        val fade = 1f - b * 0.32f
        drawPoints(pts, PointMode.Points, c.text.a((0.04f + 0.06f * fx) * fade), dot, StrokeCap.Round)
    }
    if (kind == "full") {
        // líneas de barrido y viñeta
        val ls = 3.dp.toPx()
        var y = 0f
        while (y < h) { drawLine(Color.Black.a(0.16f), Offset(0f, y), Offset(w, y), 1f); y += ls }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.a(0.5f)), Offset(w / 2f, h / 2f), hypot(w, h) / 2f))
    }
}

private fun DrawScope.carmesiLive(c: FsColors, t: Float, lvl: Float, fx: Float) {
    val w = size.width
    val h = size.height
    val band = 160.dp.toPx()
    val y = (h + band) * t - band
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, c.accent.a((0.05f + 0.07f * fx) * lvl), Color.Transparent), y, y + band),
        Offset(0f, y), Size(w, band),
    )
    // LEDs sueltos que titilan
    val sz = 3.dp.toPx()
    for (i in 0 until 7) {
        val px = w * ((i * 0.37f + 0.11f) % 1f)
        val py = h * ((i * 0.53f + 0.07f) % 1f)
        val k = 0.5f + 0.5f * sin(TWO_PI * ((2 + i % 3) * t + i * 0.31f))
        val a = k * k * k
        drawRect(c.accent.a(0.6f * a * lvl), Offset(px, py), Size(sz, sz))
    }
}

private fun DrawScope.aceroStatic(c: FsColors, kind: String, fx: Float) {
    val w = size.width
    val h = size.height
    if (kind == "full") {
        drawRect(Brush.radialGradient(listOf(c.accent.a(0.13f * fx), Color.Transparent), Offset(-w * 0.1f, 0f), w * 1.25f))
    }
    val minor = 24.dp.toPx()
    val minorA = 0.035f + 0.04f * fx
    val majorA = 0.07f + 0.09f * fx
    var i = 0
    var x = 0f
    while (x <= w) { drawLine(c.accent.a(if (i % 5 == 0) majorA else minorA), Offset(x, 0f), Offset(x, h), 1f); x += minor; i++ }
    i = 0
    var y = 0f
    while (y <= h) { drawLine(c.accent.a(if (i % 5 == 0) majorA else minorA), Offset(0f, y), Offset(w, y), 1f); y += minor; i++ }
    if (kind != "full") return
    // cruces en las intersecciones principales
    val arm = 3.dp.toPx()
    val ca = c.accent.a(0.20f + 0.20f * fx)
    var mx = 0f
    while (mx <= w) {
        var my = 0f
        while (my <= h) {
            drawLine(ca, Offset(mx - arm, my), Offset(mx + arm, my), 1f)
            drawLine(ca, Offset(mx, my - arm), Offset(mx, my + arm), 1f)
            my += minor * 5
        }
        mx += minor * 5
    }
    // regla en el borde derecho
    var ty = 0f
    var n = 0
    val tick = c.accent.a(0.18f + 0.14f * fx)
    while (ty < h) {
        val len = if (n % 5 == 0) 8.dp.toPx() else 4.dp.toPx()
        drawLine(tick, Offset(w - len, ty), Offset(w, ty), 1f)
        ty += 10.dp.toPx(); n++
    }
    // esquinas de mira
    val inset = 10.dp.toPx()
    val ln = 20.dp.toPx()
    val bc = c.accent.a(0.45f + 0.25f * fx)
    val sw = 1.5.dp.toPx()
    drawLine(bc, Offset(inset, inset), Offset(inset + ln, inset), sw); drawLine(bc, Offset(inset, inset), Offset(inset, inset + ln), sw)
    drawLine(bc, Offset(w - inset - ln, h - inset), Offset(w - inset, h - inset), sw); drawLine(bc, Offset(w - inset, h - inset - ln), Offset(w - inset, h - inset), sw)
}

private fun DrawScope.aceroLive(c: FsColors, t: Float, lvl: Float, fx: Float) {
    val w = size.width
    val h = size.height
    val y = h * t
    val trail = 70.dp.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, c.accent.a((0.04f + 0.06f * fx) * lvl)), y - trail, y),
        Offset(0f, (y - trail).coerceAtLeast(0f)), Size(w, trail.coerceAtMost(y)),
    )
    drawLine(c.accent.a((0.14f + 0.18f * fx) * lvl), Offset(0f, y), Offset(w, y), 1.dp.toPx())
    // marcador que parpadea junto a la regla
    val blinkOn = ((t * 16f).toInt() % 2) == 0
    if (blinkOn) drawRect(c.accent.a(0.55f * lvl), Offset(w - 20.dp.toPx(), 22.dp.toPx()), Size(6.dp.toPx(), 6.dp.toPx()))
}

// ───────────────────────── Adornos de tarjetas

/** Detalles de la skin sobre una tarjeta: tapa y LED (Carmesí) o esquinas de mira (Acero). */
@Composable
fun Modifier.skinCardDecor(accent: Boolean = false): Modifier {
    val s = Fs.skin
    if (s.isDefault || s.fx < 0.05f) return this
    val c = Fs.colors
    val blink = LocalSkinBlink.current
    val live = s.fxLevel(Fs.motion) > 0f
    val ink = if (accent) c.onAccent else c.accent
    val carmesi = s.isCarmesi
    return this.drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        if (carmesi) {
            drawRect(ink.a(0.9f), Offset(14.dp.toPx(), 0f), Size(26.dp.toPx(), 2.dp.toPx()))
            val sq = 3.dp.toPx()
            val gap = 5.dp.toPx()
            val x0 = w - 14.dp.toPx() - 3 * sq - 2 * (gap - sq)
            val on = !live || blink.value
            for (i in 0..2) {
                val a = if (i == 2) (if (on) 0.95f else 0.22f) else 0.35f
                drawRect(ink.a(a), Offset(x0 + i * gap, 9.dp.toPx()), Size(sq, sq))
            }
        } else {
            val ln = 8.dp.toPx()
            val sw = 1.3.dp.toPx()
            val col = ink.a(0.75f)
            val inset = 5.dp.toPx()
            drawLine(col, Offset(inset, inset), Offset(inset + ln, inset), sw); drawLine(col, Offset(inset, inset), Offset(inset, inset + ln), sw)
            drawLine(col, Offset(w - inset - ln, h - inset), Offset(w - inset, h - inset), sw); drawLine(col, Offset(w - inset, h - inset - ln), Offset(w - inset, h - inset), sw)
            drawRect(ink.a(0.9f), Offset(w * 0.5f - 12.dp.toPx(), 0f), Size(24.dp.toPx(), 2.dp.toPx()))
        }
    }
}

// ───────────────────────── Logo

/** Dibuja el logo de una skin (los mismos trazos del ícono del lanzador) en [size]. */
@Composable
fun SkinMark(skin: String, modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = Fs.colors.accent) {
    val prims = remember(skin) {
        SkinMarkData.marks[skin].orEmpty().map { it to PathParser().parsePathString(it.path).toPath() }
    }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 108f
        scale(k, k, Offset.Zero) {
            for ((pr, path) in prims) {
                val col = if (pr.role == 0) color else color.copy(alpha = color.alpha * 0.5f)
                if (pr.stroke == 0f) {
                    path.fillType = if (pr.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                    drawPath(path, col)
                } else {
                    val cap = when (pr.cap) { 1 -> StrokeCap.Round; 2 -> StrokeCap.Square; else -> StrokeCap.Butt }
                    val join = when (pr.join) { 1 -> StrokeJoin.Round; 2 -> StrokeJoin.Bevel; else -> StrokeJoin.Miter }
                    drawPath(path, col, style = Stroke(pr.stroke, miter = 4f, cap = cap, join = join))
                }
            }
        }
    }
}

/** El ícono de la skin como ficha cuadrada (fondo oscuro con brillo + logo). */
@Composable
fun SkinLogo(skin: String, modifier: Modifier = Modifier, size: Dp = 56.dp, color: Color? = null) {
    val carmesi = skin == "carmesi"
    val base = if (carmesi) Color(0xFF0B090C) else Color(0xFF0B0B06)
    val glow = if (carmesi) Color(0xFF4A0B1B) else Color(0xFF4A3D05)
    val tint = color ?: if (carmesi) Color(0xFFE30613) else Color(0xFFFCEE0A)
    Box(
        modifier.size(size).clip(RoundedCornerShape(percent = 24)).drawBehind {
            val cx = if (carmesi) 0.5f else 0.2f
            val cy = if (carmesi) 0.89f else 0.13f
            drawRect(base)
            drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(this.size.width * cx, this.size.height * cy), this.size.width * 0.78f))
        },
        contentAlignment = Alignment.Center,
    ) {
        SkinMark(skin, size = size, color = tint)
    }
}

// ───────────────────────── Transición entre pantallas

/** Efecto que cruza la pantalla cuando cambia [trigger]: línea de escaneo (Carmesí) o persianas (Acero). */
@Composable
fun SkinTransitionFx(trigger: Any, modifier: Modifier = Modifier) {
    val s = Fs.skin
    val m = Fs.motion
    val c = Fs.colors
    val lvl = s.fxLevel(m)
    val p = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    val dur = m.ms(if (s.isAcero) 480 else 560)
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        if (lvl > 0f) {
            p.snapTo(0f)
            p.animateTo(1f, tween(dur, easing = LinearEasing))
        }
    }
    if (lvl <= 0f || !s.id.let { it == "carmesi" || it == "acero" }) return
    val carmesi = s.isCarmesi
    Canvas(modifier.fillMaxSize()) {
        val t = p.value
        if (t >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        if (carmesi) {
            val y = h * t
            // lo que aún no se ha escaneado queda tapado
            drawRect(c.bg.a(0.92f * lvl), Offset(0f, y), Size(w, h - y))
            val trail = 110.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.accent.a(0.30f * lvl)), y - trail, y),
                Offset(0f, (y - trail).coerceAtLeast(0f)), Size(w, trail.coerceAtMost(y)),
            )
            drawRect(c.accent.a(0.95f * lvl), Offset(0f, y - 1.dp.toPx()), Size(w, 2.dp.toPx()))
            // ruido digital pegado a la línea
            val sq = 4.dp.toPx()
            for (i in 0 until 22) {
                val x = w * ((i * 0.6180339f) % 1f)
                val dy = ((i * 7) % 5 - 2) * sq
                val a = 0.25f + 0.5f * (((i * 13) % 7) / 7f)
                drawRect(c.accent.a(a * lvl * (1f - t)), Offset(x, y + dy), Size(sq * (1 + i % 3), sq * 0.6f))
            }
        } else {
            val n = 7
            val cell = h / n
            for (i in 0 until n) {
                val tt = (t - i * 0.035f).coerceIn(0f, 1f)
                val cover = if (tt < 0.38f) tt / 0.38f else 1f - (tt - 0.38f) / 0.62f
                val k = cover.coerceIn(0f, 1f)
                val bh = cell * k
                if (bh <= 0.5f) continue
                val top = i * cell
                drawRect(c.bg.a(0.97f * lvl.coerceAtLeast(0.6f)), Offset(0f, top), Size(w, bh))
                drawRect(c.accent.a(0.7f * lvl), Offset(0f, top + bh - 1.dp.toPx()), Size(w, 1.dp.toPx()))
            }
        }
    }
}

// ───────────────────────── Arranque al cambiar de skin

private val bootLinesCarmesi = listOf("INICIANDO CARMESÍ", "CARGANDO MÓDULOS DE APTITUD", "SISTEMA EN LÍNEA")
private val bootLinesAcero = listOf("ACERO // TERMINAL", "CALIBRANDO SENSORES", "ACCESO AUTORIZADO")
private val bootLinesDefault = listOf("FITSTAT", "RESTAURANDO DISEÑO", "LISTO")

/** Pantalla breve que cubre el cambio de skin: logo, barra de carga y mensajes de estado. */
@Composable
fun SkinBootScreen(skin: String, modifier: Modifier = Modifier) {
    val c = Fs.colors
    val m = Fs.motion
    val p = remember(skin) { Animatable(0f) }
    LaunchedEffect(skin) { p.animateTo(1f, tween(m.ms(1250), easing = LinearEasing)) }
    val lines = when (skin) { "carmesi" -> bootLinesCarmesi; "acero" -> bootLinesAcero; else -> bootLinesDefault }
    val idx = ((p.value * lines.size).toInt()).coerceIn(0, lines.size - 1)
    val brand = app.fitstat.data.Skins.byId(skin).brand
    Box(
        modifier.fillMaxSize().background(c.bg).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val pulse = 1f + 0.04f * sin(TWO_PI * p.value * 3f)
            Box(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }) {
                if (skin == "carmesi" || skin == "acero") SkinLogo(skin, size = 104.dp, color = c.accent)
                else FitBot(size = 104.dp, mood = BotMood.Happy)
            }
            Txt(brand, style = Fs.type.title, color = c.text, maxLines = 1)
            // barra de carga por bloques
            val blocks = 14
            val filled = (p.value * blocks).toInt()
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until blocks) {
                    Box(Modifier.size(width = 12.dp, height = 8.dp).background(if (i < filled) c.accent else c.surface3))
                }
            }
            Txt(lines[idx], style = Fs.type.caption, color = c.accentText, maxLines = 1)
        }
    }
}

// ───────────────────────── Aviso de celebración

/** Banda grande que cruza la pantalla en récords, hitos y al terminar un entrenamiento. */
@Composable
fun SkinCelebrate(trigger: Int, modifier: Modifier = Modifier) {
    val s = Fs.skin
    val m = Fs.motion
    val c = Fs.colors
    val lvl = s.fxLevel(m)
    val p = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger <= 0 || lvl <= 0f || s.isDefault) return@LaunchedEffect
        shown = trigger
        p.snapTo(0f)
        p.animateTo(1f, tween(m.ms(2100), easing = LinearEasing))
        shown = 0
    }
    if (shown == 0 || lvl <= 0f) return
    val text = if (s.isCarmesi) "OBJETIVO ALCANZADO" else "OPERACIÓN EXITOSA"
    val t = p.value
    val appear = (t / 0.18f).coerceIn(0f, 1f)
    val vanish = ((1f - t) / 0.16f).coerceIn(0f, 1f)
    val vis = minOf(appear, vanish)
    val chars = (text.length * (t / 0.32f).coerceIn(0f, 1f)).toInt()
    val jitter = if (s.isCarmesi && t < 0.3f) ((((t * 90f).toInt()) % 3) - 1) * 3f else 0f
    Box(modifier.fillMaxSize().padding(top = 150.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.fillMaxWidth().height(64.dp)
                .graphicsLayer { scaleY = if (s.isAcero) vis else 1f; alpha = if (s.isAcero) 1f else vis }
                .background(c.bg.a(0.88f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.accent).align(Alignment.TopCenter))
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.accent).align(Alignment.BottomCenter))
            Txt(
                text.take(chars) + if (chars < text.length) "_" else "",
                Modifier.graphicsLayer { translationX = jitter }.width(320.dp),
                Fs.type.h1, c.accentText, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
