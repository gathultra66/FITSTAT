package app.fitstat.ui.design

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.fitstat.ui.theme.mix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Mascotas de cabeza de las skins. Son diseños propios (no copian a ningún personaje):
 *  - CENTINELA (Carmesí): casco angular con un visor de barra roja, rejillas en las mejillas y cresta.
 *  - VIGÍA (Acero): casco táctico redondeado con visor inclinado, módulo de radio y retícula.
 * Usan la misma "pose" que FITBOT (estados de ánimo y reacciones al toque), pero cada una tiene sus
 * propios efectos: Centinela barre una línea de escaneo y se "glitchea" al tocarlo; Vigía lanza anillos
 * de radar y gira su retícula.
 */

private fun hp(vararg v: Float): Path = Path().apply {
    moveTo(v[0], v[1])
    var i = 2
    while (i < v.size) {
        lineTo(v[i], v[i + 1]); i += 2
    }
    close()
}

private fun DrawScope.rbox(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) {
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))
}

private val TWO_PI = 2f * PI.toFloat()

internal fun DrawScope.drawSkinHead(
    skin: String, p: Pose, accent: Color, accent2: Color, glowK: Float, bad: Color, dark: Boolean,
    breath: Float, fast: Float, loops: Boolean,
) {
    val sh = (1f + p.bounce / 70f).coerceIn(0.5f, 1.2f)
    drawOval(Color.Black.copy(alpha = if (dark) 0.5f else 0.22f), Offset(50f - 21f * sh, 93.6f), Size(42f * sh, 4.4f))
    // Giro tipo moneda: la cabeza se pone de canto y vuelve.
    val sx = if (p.spin == 0f) 1f else cos(Math.toRadians(p.spin.toDouble())).toFloat().let { if (abs(it) < 0.04f) 0.04f else it }
    val shake = if (p.strain > 0f) 0.9f * sin(p.react * 90f) * p.strain else 0f
    translate(shake, p.bounce) {
        scale(sx, p.squash, pivot = Offset(50f, 92f)) {
            translate(0f, p.lift) {
                rotate(p.tilt, Offset(50f, 62f)) {
                    if (skin == "carmesi") drawCentinela(p, accent, accent2, glowK, bad, breath, fast, loops)
                    else drawVigia(p, accent, accent2, glowK, bad, breath, fast, loops)
                }
            }
        }
    }
    drawPoseOverlays(p, accent)
}

// ───────────────────────── Centinela (Carmesí)

private fun DrawScope.drawCentinela(p: Pose, accent: Color, accent2: Color, glowK: Float, bad: Color, breath: Float, fast: Float, loops: Boolean) {
    val edge = accent.copy(alpha = 0.38f + 0.27f * glowK)
    val eyeColor = if (p.eyes == Eyes.Cross) bad else accent
    val head = hp(30f, 13f, 70f, 13f, 83f, 27f, 83f, 57f, 72f, 77f, 59f, 87f, 41f, 87f, 28f, 77f, 17f, 57f, 17f, 27f)

    // cuello, cables y cuello de armadura
    drawPath(hp(35f, 86f, 65f, 86f, 72f, 96f, 28f, 96f), Brush.verticalGradient(listOf(Color(0xFF2A252C), Color(0xFF110E12)), 86f, 96f))
    drawLine(edge, Offset(35f, 86f), Offset(28f, 96f), 1f)
    drawLine(edge, Offset(65f, 86f), Offset(72f, 96f), 1f)
    for (x in listOf(44f, 50f, 56f)) drawLine(Color(0xFF0B090C), Offset(x, 86f), Offset(x, 95.5f), 2.2f)

    // módulos de los oídos con LED que parpadea
    for (side in listOf(-1f, 1f)) {
        val x = if (side < 0) 8f else 83f
        rbox(x, 35f, 9f, 18f, 2.2f, Color(0xFF2A252C))
        drawRoundRect(edge, Offset(x, 35f), Size(9f, 18f), CornerRadius(2.2f), style = Stroke(0.7f))
        val ph = if (side < 0) breath * 4f else breath * 4f + 0.5f
        val on = if (loops) (if (ph % 1f < 0.55f) 1f else 0.18f) else 1f
        drawCircle(accent.copy(alpha = 0.22f * on * (0.4f + glowK)), 3.6f, Offset(x + 4.5f, 44f))
        drawCircle(accent.copy(alpha = 0.25f + 0.75f * on), 1.7f, Offset(x + 4.5f, 44f))
    }

    // casco
    drawPath(head, Brush.verticalGradient(listOf(Color(0xFF3D383F), Color(0xFF1E1A20), Color(0xFF131014)), 12f, 88f))
    clipPath(head) {
        drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 17f, 45f), Offset(17f, 13f), Size(30f, 74f))
    }

    // cresta
    drawPath(hp(45.5f, 13f, 50f, 3.5f, 54.5f, 13f), Color(0xFF2E2930))
    val an = p.antenna
    drawCircle(accent.copy(alpha = 0.18f + 0.22f * an * glowK), 4.2f, Offset(50f, 6.5f))
    drawCircle(accent.mix(Color.White, 0.25f * an).copy(alpha = 0.5f + 0.5f * an), 1.5f, Offset(50f, 6.8f))

    // placa de la frente
    drawPath(hp(34f, 17f, 66f, 17f, 74f, 28f, 26f, 28f), Brush.verticalGradient(listOf(Color(0xFF4B454D), Color(0xFF2A252C)), 17f, 28f))
    drawLine(accent.copy(alpha = 0.5f), Offset(26f, 28f), Offset(74f, 28f), 0.9f)
    val chev = Path().apply { moveTo(44f, 21.5f); lineTo(50f, 25f); lineTo(56f, 21.5f) }
    drawPath(chev, accent.copy(alpha = 0.85f), style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Color(0xFF0B090C), 1.1f, Offset(31f, 22.5f)); drawCircle(Color(0xFF0B090C), 1.1f, Offset(69f, 22.5f))

    // visor
    val visor = hp(19f, 35f, 81f, 35f, 76f, 53f, 24f, 53f)
    drawPath(visor, Color(0xFF09070A))
    drawPath(visor, Color(0xFF3A343C), style = Stroke(1.2f, join = StrokeJoin.Miter))
    drawRect(accent.copy(alpha = 0.05f), Offset(22f, 36.5f), Size(56f, 15f))

    if (p.eyes == Eyes.Scan) crScan(p, eyeColor)
    else {
        crEyes(p, eyeColor, glowK, 0f, 1f)
        // al tocarlo, el visor se "glitchea": copias desplazadas y parpadeo
        if (p.react > 0f && sin(p.react * 47f) > 0f) {
            crEyes(p, accent2, glowK, 2.4f, 0.55f)
            crEyes(p, Color.White, glowK, -1.6f, 0.25f)
        }
    }

    // rejillas de las mejillas
    for (i in 0..2) {
        val y = 59f + i * 4.8f
        drawLine(Color(0xFF0B090C), Offset(24.5f, y), Offset(35.5f, y + 3f), 2f, StrokeCap.Round)
        drawLine(Color(0xFF0B090C), Offset(75.5f, y), Offset(64.5f, y + 3f), 2f, StrokeCap.Round)
        val a = if (loops) 0.3f + 0.7f * (0.5f + 0.5f * sin(TWO_PI * (fast * 2f - i * 0.18f))) else 0.7f
        drawCircle(accent.copy(alpha = a), 0.95f, Offset(36.5f, y + 3.2f))
        drawCircle(accent.copy(alpha = a), 0.95f, Offset(63.5f, y + 3.2f))
    }

    // boca: rejilla vertical que late al hablar
    rbox(39.5f, 69f, 21f, 12f, 2f, Color(0xFF0B090C))
    for (i in 0 until 5) {
        val bx = 42.2f + i * 3.9f
        val base = when (p.eyes) {
            Eyes.Sleepy -> 3f
            Eyes.Happy, Eyes.Heart -> 8f
            else -> 6f
        }
        val hh = if (p.talk > 0.02f) 2.5f + 8f * p.talk * (0.5f + 0.5f * sin(fast * TWO_PI * 3f + i * 1.3f)) else base
        drawRoundRect(eyeColor.copy(alpha = if (p.talk > 0.02f) 0.95f else 0.55f), Offset(bx - 1f, 75f - hh / 2f), Size(2f, hh), CornerRadius(1f))
    }

    // línea de escaneo que baja por el casco
    if (loops) {
        clipPath(head) {
            val y = 8f + 84f * breath
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.20f), Color.Transparent), y - 9f, y + 9f),
                Offset(0f, y - 9f), Size(100f, 18f),
            )
            drawLine(accent.copy(alpha = 0.28f), Offset(14f, y), Offset(86f, y), 0.7f)
        }
    }

    // borde de neón
    drawPath(head, edge, style = Stroke(1.1f, join = StrokeJoin.Miter))
}

private fun DrawScope.crScan(p: Pose, color: Color) {
    val bx = 29f + 42f * p.scan
    val back = if (p.scan > 0.5f) 1f else -1f
    for (i in 0..2) {
        val a = 1f - i * 0.32f
        val x = bx + back * i * 3.2f
        drawPath(hp(x - 2.6f, 48.5f, x - 0.6f, 39.5f, x + 3.4f, 39.5f, x + 1.4f, 48.5f), color.copy(alpha = 0.22f * a))
    }
    drawPath(hp(bx - 2.4f, 48f, bx - 0.4f, 40f, bx + 3.2f, 40f, bx + 1.2f, 48f), color)
    drawLine(Color.White.copy(alpha = 0.6f), Offset(bx + 0.4f, 41f), Offset(bx + 0.4f, 46.5f), 1.1f)
}

private fun DrawScope.crEyes(p: Pose, color: Color, glowK: Float, dx: Float, am: Float) {
    val ex = p.lookX * 2.6f + dx
    val ey = p.lookY * 1.4f
    val cy = 44f + ey
    val glow = 0.20f * (0.5f + glowK) * am
    fun eye(kind: Eyes, cx: Float, isLeft: Boolean) {
        val x = cx + ex
        when (kind) {
            Eyes.Normal, Eyes.Wide -> {
                val h = (if (kind == Eyes.Wide) 9.5f else 6f) * p.blink
                val w = 17f
                val path = hp(x - w / 2, cy + h / 2, x - w / 2 + 2.5f, cy - h / 2, x + w / 2 + 2.5f, cy - h / 2, x + w / 2, cy + h / 2)
                drawPath(path, color.copy(alpha = glow), style = Stroke(4.4f, join = StrokeJoin.Round))
                drawPath(path, color.copy(alpha = am))
                if (h > 3.5f) drawLine(Color.White.copy(alpha = 0.5f * am), Offset(x - w / 2 + 3.5f, cy - h / 2 + 1.1f), Offset(x + w / 2 - 2f, cy - h / 2 + 1.1f), 0.9f)
            }
            Eyes.Happy -> {
                val path = Path().apply { moveTo(x - 6.5f, cy + 3f); lineTo(x, cy - 3.2f); lineTo(x + 6.5f, cy + 3f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, color.copy(alpha = am), style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Eyes.Sleepy -> {
                drawLine(color.copy(alpha = glow), Offset(x - 7f, cy + 2.5f), Offset(x + 7f, cy + 2.5f), 4.6f, StrokeCap.Round)
                drawLine(color.copy(alpha = am), Offset(x - 6.8f, cy + 2.5f), Offset(x + 6.8f, cy + 2.5f), 2.1f, StrokeCap.Round)
            }
            Eyes.Squint -> {
                val s = if (isLeft) 1f else -1f
                val a = Offset(x - 7f * s, cy - 3.2f)
                val b = Offset(x + 7f * s, cy + 3.2f)
                drawLine(color.copy(alpha = glow * 1.4f), a, b, 6f, StrokeCap.Butt)
                drawLine(color.copy(alpha = am), a, b, 3.6f, StrokeCap.Butt)
            }
            Eyes.Cross -> {
                val path = Path().apply { moveTo(x - 3.4f, cy - 3.4f); lineTo(x + 3.4f, cy + 3.4f); moveTo(x + 3.4f, cy - 3.4f); lineTo(x - 3.4f, cy + 3.4f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5.2f, cap = StrokeCap.Round))
                drawPath(path, color.copy(alpha = am), style = Stroke(2.3f, cap = StrokeCap.Round))
            }
            Eyes.Heart -> {
                val path = Path().apply {
                    moveTo(x, cy + 4.6f)
                    cubicTo(x - 7f, cy - 0.5f, x - 4.2f, cy - 5.8f, x, cy - 2f)
                    cubicTo(x + 4.2f, cy - 5.8f, x + 7f, cy - 0.5f, x, cy + 4.6f)
                    close()
                }
                drawPath(path, color.copy(alpha = glow * 1.6f), style = Stroke(3.6f, join = StrokeJoin.Round))
                drawPath(path, color.copy(alpha = am))
            }
            Eyes.Scan -> {}
        }
    }
    eye(p.leftEyeOverride ?: p.eyes, 36.5f, true)
    eye(p.eyes, 63.5f, false)
}

// ───────────────────────── Vigía (Acero)

private fun DrawScope.drawVigia(p: Pose, accent: Color, accent2: Color, glowK: Float, bad: Color, breath: Float, fast: Float, loops: Boolean) {
    val rim = Color(0xFF5B6B7D)
    val edge = accent.copy(alpha = 0.34f + 0.22f * glowK)
    val eyeColor = if (p.eyes == Eyes.Cross) bad else accent

    // anillos de radar al tocarlo (detrás de la cabeza)
    if (p.react > 0f) {
        for (k in 0..1) {
            val r = p.react * 64f - k * 17f
            if (r > 1f) drawCircle(accent.copy(alpha = ((1f - p.react) * 0.5f).coerceIn(0f, 1f)), r, Offset(50f, 50f), style = Stroke(1.3f))
        }
    }

    val head = Path().apply {
        moveTo(20f, 50f)
        cubicTo(18f, 24f, 34f, 9f, 50f, 9f)
        cubicTo(66f, 9f, 82f, 24f, 80f, 50f)
        lineTo(80f, 64f); lineTo(70f, 79f); lineTo(60f, 87f); lineTo(40f, 87f); lineTo(30f, 79f); lineTo(20f, 64f)
        close()
    }

    // cuello de armadura con chevrones
    drawPath(hp(34f, 86f, 66f, 86f, 75f, 96f, 25f, 96f), Brush.verticalGradient(listOf(Color(0xFF222C37), Color(0xFF0E1319)), 86f, 96f))
    drawLine(edge, Offset(34f, 86f), Offset(25f, 96f), 1f)
    drawLine(edge, Offset(66f, 86f), Offset(75f, 96f), 1f)
    for (i in 0..1) {
        val c = Path().apply { moveTo(44.5f, 88.5f + i * 3f); lineTo(50f, 91.5f + i * 3f); lineTo(55.5f, 88.5f + i * 3f) }
        drawPath(c, accent.copy(alpha = 0.8f - i * 0.4f), style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // antena del módulo de radio (derecha) con luz que pulsa
    drawLine(Color(0xFF4A5868), Offset(84.5f, 41f), Offset(89f, 25f), 1.5f, StrokeCap.Round)
    val an = p.antenna
    drawCircle(accent.copy(alpha = 0.2f + 0.25f * an * glowK), 4.4f, Offset(89f, 24.5f))
    drawCircle(accent.mix(Color.White, 0.3f * an).copy(alpha = 0.5f + 0.5f * an), 1.9f, Offset(89f, 24.5f))
    // módulos de oído
    rbox(78.5f, 40f, 9.5f, 22f, 3f, Color(0xFF28333F))
    drawRoundRect(rim.copy(alpha = 0.7f), Offset(78.5f, 40f), Size(9.5f, 22f), CornerRadius(3f), style = Stroke(0.8f))
    val led = if (loops) (if ((breath * 3f) % 1f < 0.5f) 1f else 0.2f) else 1f
    drawCircle(accent.copy(alpha = 0.3f + 0.7f * led), 1.4f, Offset(83.2f, 46f))
    drawLine(Color(0xFF0B1016), Offset(80.5f, 51f), Offset(86f, 51f), 1f); drawLine(Color(0xFF0B1016), Offset(80.5f, 54f), Offset(86f, 54f), 1f)
    rbox(12f, 42f, 9f, 18f, 3f, Color(0xFF28333F))
    drawRoundRect(rim.copy(alpha = 0.7f), Offset(12f, 42f), Size(9f, 18f), CornerRadius(3f), style = Stroke(0.8f))

    // casco
    drawPath(head, Brush.verticalGradient(listOf(Color(0xFF3C4856), Color(0xFF1D2630), Color(0xFF10161D)), 9f, 88f))
    clipPath(head) {
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), 9f, 34f), Offset(10f, 9f), Size(80f, 26f))
        // costilla central
        drawPath(hp(46.5f, 9f, 53.5f, 9f, 55f, 30f, 45f, 30f), Color(0xFF2B3743))
        drawLine(accent.copy(alpha = 0.55f), Offset(50f, 10f), Offset(50f, 29f), 0.8f)
    }
    // chevrones de la frente
    for (i in 0..1) {
        val c = Path().apply { moveTo(43f, 24.5f - i * 4.5f); lineTo(50f, 19.5f - i * 4.5f); lineTo(57f, 24.5f - i * 4.5f) }
        // se dibujan a los lados de la costilla
        drawPath(c, accent.copy(alpha = 0.85f - i * 0.4f), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // banda de la frente
    drawPath(hp(21f, 31f, 79f, 31f, 79f, 35.5f, 21f, 35.5f), Color(0xFF0E141A))
    for (i in 0 until 9) drawLine(accent.copy(alpha = if (i % 3 == 0) 0.75f else 0.3f), Offset(26f + i * 6f, 32.2f), Offset(26f + i * 6f, 34.3f), 1f)

    // visor inclinado
    val visor = hp(22f, 37.5f, 78f, 37.5f, 74f, 57f, 26f, 57f)
    drawPath(visor, Brush.verticalGradient(listOf(Color(0xFF0A1118), Color(0xFF17232F)), 37f, 57f))
    drawPath(visor, rim, style = Stroke(1.2f, join = StrokeJoin.Miter))
    clipPath(visor) {
        drawPath(hp(30f, 37f, 44f, 37f, 36f, 58f, 22f, 58f), Color.White.copy(alpha = 0.06f))
        // retícula HUD
        val rot = if (loops) fast * 360f else 0f
        rotate(rot, Offset(50f, 47f)) {
            for (k in 0 until 4) {
                val a = k * 90f
                rotate(a, Offset(50f, 47f)) { drawLine(accent.copy(alpha = 0.32f), Offset(50f, 38.5f), Offset(50f, 41.5f), 0.9f) }
            }
        }
        drawCircle(accent.copy(alpha = 0.16f), 8.2f, Offset(50f, 47f), style = Stroke(0.8f))
        drawLine(accent.copy(alpha = 0.10f), Offset(24f, 47f), Offset(76f, 47f), 0.6f)
    }

    if (p.eyes == Eyes.Scan) vgScan(p, eyeColor)
    else vgEyes(p, eyeColor, glowK)

    // máscara de la mandíbula con rejilla
    val chin = hp(31f, 61f, 69f, 61f, 65f, 81f, 35f, 81f)
    drawPath(chin, Color(0xFF212B36))
    drawPath(chin, rim.copy(alpha = 0.6f), style = Stroke(0.8f, join = StrokeJoin.Miter))
    for (i in 0..2) {
        val y = 66.5f + i * 4.6f
        val inset = 3.2f + i * 1.6f
        val talk = if (p.talk > 0.02f) 0.35f + 0.65f * (0.5f + 0.5f * sin(fast * TWO_PI * 3f + i * 1.6f)) * p.talk else 0f
        val a = if (p.talk > 0.02f) 0.35f + 0.6f * talk else if (p.eyes == Eyes.Happy || p.eyes == Eyes.Heart) 0.6f else 0.32f
        drawLine(eyeColor.copy(alpha = a), Offset(31f + inset + 3f, y), Offset(69f - inset - 3f, y), 1.5f + 1.4f * talk, StrokeCap.Round)
    }

    // destello de línea que cruza el casco (sutil)
    if (loops) {
        clipPath(head) {
            val x = -10f + 120f * ((breath * 1.0f) % 1f)
            drawPath(hp(x, 9f, x + 6f, 9f, x - 6f, 88f, x - 12f, 88f), Color.White.copy(alpha = 0.05f))
        }
    }

    // borde
    drawPath(head, edge, style = Stroke(1.1f, join = StrokeJoin.Round))
}

private fun DrawScope.vgScan(p: Pose, color: Color) {
    val bx = 30f + 40f * p.scan
    val back = if (p.scan > 0.5f) 1f else -1f
    drawLine(color.copy(alpha = 0.3f), Offset(25f, 47f), Offset(75f, 47f), 0.8f)
    for (i in 0..2) {
        val a = 1f - i * 0.32f
        drawRect(color.copy(alpha = 0.22f * a), Offset(bx - 2.2f + back * i * 3f, 42.5f), Size(4.4f, 9f))
    }
    drawRect(color, Offset(bx - 2f, 43f), Size(4f, 8f))
    drawRect(Color.White.copy(alpha = 0.6f), Offset(bx - 0.8f, 44f), Size(1.4f, 3f))
}

private fun DrawScope.vgEyes(p: Pose, color: Color, glowK: Float) {
    val ex = p.lookX * 2.4f
    val ey = p.lookY * 1.3f
    val cy = 47.5f + ey
    val glow = 0.20f * (0.5f + glowK)
    fun cut(x0: Float, y0: Float, x1: Float, y1: Float, c: Float): Path =
        hp(x0 + c, y0, x1, y0, x1, y1 - c, x1 - c, y1, x0, y1, x0, y0 + c)
    fun eye(kind: Eyes, cx: Float, isLeft: Boolean) {
        val x = cx + ex
        when (kind) {
            Eyes.Normal, Eyes.Wide -> {
                val w = if (kind == Eyes.Wide) 10.5f else 9f
                val h = (if (kind == Eyes.Wide) 9.6f else 6.4f) * p.blink
                val path = cut(x - w / 2, cy - h / 2, x + w / 2, cy + h / 2, minOf(1.8f, h / 2.2f))
                drawPath(path, color.copy(alpha = glow), style = Stroke(4.2f, join = StrokeJoin.Round))
                drawPath(path, color)
                if (h > 3.5f) drawRect(Color.White.copy(alpha = 0.55f), Offset(x - w / 2 + 1.4f, cy - h / 2 + 1f), Size(w * 0.34f, 1.1f))
            }
            Eyes.Happy -> {
                val path = Path().apply { moveTo(x - 5f, cy + 2.6f); lineTo(x, cy - 2.8f); lineTo(x + 5f, cy + 2.6f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, color, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Eyes.Sleepy -> {
                drawLine(color.copy(alpha = glow), Offset(x - 5f, cy + 1.8f), Offset(x + 5f, cy + 1.8f), 4.4f, StrokeCap.Round)
                drawLine(color, Offset(x - 4.8f, cy + 1.8f), Offset(x + 4.8f, cy + 1.8f), 2f, StrokeCap.Round)
            }
            Eyes.Squint -> {
                val s = if (isLeft) 1f else -1f
                val path = Path().apply { moveTo(x - 4.6f * s, cy - 3.6f); lineTo(x + 3.6f * s, cy); lineTo(x - 4.6f * s, cy + 3.6f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, color, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Eyes.Cross -> {
                val path = Path().apply { moveTo(x - 3.2f, cy - 3.2f); lineTo(x + 3.2f, cy + 3.2f); moveTo(x + 3.2f, cy - 3.2f); lineTo(x - 3.2f, cy + 3.2f) }
                drawPath(path, color.copy(alpha = glow * 1.4f), style = Stroke(5f, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(2.2f, cap = StrokeCap.Round))
            }
            Eyes.Heart -> {
                val path = Path().apply {
                    moveTo(x, cy + 4.2f)
                    cubicTo(x - 6.2f, cy - 0.4f, x - 3.8f, cy - 5.2f, x, cy - 1.8f)
                    cubicTo(x + 3.8f, cy - 5.2f, x + 6.2f, cy - 0.4f, x, cy + 4.2f)
                    close()
                }
                drawPath(path, color.copy(alpha = glow * 1.6f), style = Stroke(3.4f, join = StrokeJoin.Round))
                drawPath(path, color)
            }
            Eyes.Scan -> {}
        }
    }
    eye(p.leftEyeOverride ?: p.eyes, 38f, true)
    eye(p.eyes, 62f, false)
}
