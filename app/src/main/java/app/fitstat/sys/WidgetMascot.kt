package app.fitstat.sys

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * FITBOT dibujado con el Canvas de Android (para los widgets), en una cuadrícula de 100 x 100.
 * Es la misma figura de la mascota de la app, en una pose fija según el ánimo.
 */
internal object WidgetMascot {
    private fun p() = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun vGrad(y0: Float, y1: Float, vararg c: Int): Paint =
        p().apply { shader = LinearGradient(0f, y0, 0f, y1, c, null, Shader.TileMode.CLAMP) }

    private fun hGrad(x0: Float, x1: Float, vararg c: Int): Paint =
        p().apply { shader = LinearGradient(x0, 0f, x1, 0f, c, null, Shader.TileMode.CLAMP) }

    private fun solid(c: Int): Paint = p().apply { color = c }

    private fun stroke(c: Int, w: Float, round: Boolean = true): Paint = p().apply {
        color = c; style = Paint.Style.STROKE; strokeWidth = w
        if (round) { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    }

    private fun Canvas.rr(x: Float, y: Float, w: Float, h: Float, r: Float, paint: Paint) =
        drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint)

    private fun alpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((((c ushr 24) and 0xFF) * a.coerceIn(0f, 1f)).toInt() shl 24)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) * (1 - t) + (b shr s and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Dibuja a FITBOT dentro del cuadrado [left, top, left + size, top + size]. */
    fun draw(canvas: Canvas, left: Float, top: Float, size: Float, accent: Int, accent2: Int, feel: BotFeel, dark: Boolean) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size / 100f, size / 100f)

        val ink = 0xFF0A0A0F.toInt()
        val limb = vGrad(55f, 95f, 0xFF6A6A76.toInt(), 0xFF34343D.toInt())
        val hand = 0xFF9A9AA8.toInt()

        // Sombra en el suelo
        canvas.drawOval(RectF(33f, 94.8f, 67f, 99f), solid(if (dark) 0x80000000.toInt() else 0x38000000))

        // Piernas y pies
        canvas.drawLine(43.5f, 80f, 43.5f, 90f, limb.apply { style = Paint.Style.STROKE; strokeWidth = 5.4f; strokeCap = Paint.Cap.ROUND })
        canvas.drawLine(56.5f, 80f, 56.5f, 90f, limb)
        canvas.rr(38.5f, 89.5f, 10.5f, 5f, 2.5f, solid(0xFF8A8A98.toInt()))
        canvas.rr(51f, 89.5f, 10.5f, 5f, 2.5f, solid(0xFF8A8A98.toInt()))

        // Brazos (en reposo, o arriba si está feliz)
        val leftUp = feel == BotFeel.Love
        val rightUp = feel == BotFeel.Love || feel == BotFeel.Happy
        arm(canvas, 35.5f, 62.5f, -1f, if (leftUp) 150f else 7f, if (leftUp) 20f else 10f, hand)
        arm(canvas, 64.5f, 62.5f, 1f, if (rightUp) 150f else 7f, if (rightUp) 20f else 10f, hand)

        // Torso
        canvas.rr(35f, 58f, 30f, 25f, 9f, vGrad(56f, 82f, 0xFF55555F.toInt(), 0xFF2A2A31.toInt()))
        canvas.rr(35f, 58f, 30f, 12f, 9f, vGrad(58f, 68f, 0x29FFFFFF, 0x00FFFFFF))
        // Panel del pecho con latido
        canvas.rr(41.5f, 63f, 17f, 12f, 4f, solid(ink))
        val ecg = Path().apply {
            moveTo(43f, 69.5f); lineTo(47f, 69.5f); lineTo(48.6f, 65.8f); lineTo(51f, 73.2f); lineTo(52.6f, 69.5f); lineTo(57f, 69.5f)
        }
        canvas.drawPath(ecg, stroke(alpha(accent, 0.28f), 3.2f))
        canvas.drawPath(ecg, stroke(accent, 1.5f))
        // Cuello
        canvas.rr(45f, 53.5f, 10f, 6f, 2f, solid(0xFF17171D.toInt()))

        head(canvas, accent, accent2, feel)

        when (feel) {
            BotFeel.Sleepy -> zzz(canvas, accent)
            BotFeel.Love -> {
                spark(canvas, 80f, 18f, 7f, 0xFFFFFFFF.toInt())
                spark(canvas, 18f, 22f, 4f, accent)
            }
            BotFeel.Happy -> spark(canvas, 80f, 18f, 6f, accent)
            BotFeel.Calm -> {}
        }
        canvas.restore()
    }

    private fun arm(canvas: Canvas, sx: Float, sy: Float, side: Float, upper: Float, elbow: Float, handColor: Int) {
        val l1 = 10.5f
        val l2 = 9.5f
        val a1 = Math.toRadians(upper.toDouble())
        val ex = sx + side * l1 * Math.sin(a1).toFloat()
        val ey = sy + l1 * Math.cos(a1).toFloat()
        val a2 = Math.toRadians((upper + elbow).toDouble())
        val hx = ex + side * l2 * Math.sin(a2).toFloat()
        val hy = ey + l2 * Math.cos(a2).toFloat()
        val metal = vGrad(55f, 95f, 0xFF6A6A76.toInt(), 0xFF34343D.toInt()).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        metal.strokeWidth = 5.2f
        canvas.drawLine(sx, sy, ex, ey, metal)
        metal.strokeWidth = 4.6f
        canvas.drawLine(ex, ey, hx, hy, metal)
        canvas.drawCircle(ex, ey, 2.6f, solid(0xFF15151B.toInt()))
        canvas.drawCircle(hx, hy, 3.4f, solid(handColor))
    }

    private fun head(canvas: Canvas, accent: Int, accent2: Int, feel: BotFeel) {
        val eyeColor = accent
        // Antena
        canvas.drawLine(50f, 25f, 50f, 17.5f, stroke(0xFF5A5A66.toInt(), 1.8f))
        canvas.drawCircle(50f, 15.5f, 5.4f, solid(alpha(accent, 0.30f)))
        canvas.drawCircle(50f, 15.5f, 2.5f, solid(mix(accent, 0xFFFFFFFF.toInt(), 0.2f)))

        // Placas exteriores e interiores
        canvas.rr(3.5f, 12f, 13f, 48f, 5f, hGrad(3.5f, 16.5f, 0xFF8D8D9B.toInt(), 0xFF4B4B57.toInt(), 0xFF2B2B33.toInt()))
        canvas.rr(83.5f, 12f, 13f, 48f, 5f, hGrad(83.5f, 96.5f, 0xFF2B2B33.toInt(), 0xFF4B4B57.toInt(), 0xFF8D8D9B.toInt()))
        canvas.rr(17f, 19f, 11f, 34f, 4f, hGrad(17f, 28f, 0xFF7A7A88.toInt(), 0xFF45454F.toInt(), 0xFF2E2E36.toInt()))
        canvas.rr(72f, 19f, 11f, 34f, 4f, hGrad(72f, 83f, 0xFF2E2E36.toInt(), 0xFF45454F.toInt(), 0xFF7A7A88.toInt()))
        // Brillo superior de las placas
        for (x in listOf(3.5f, 83.5f)) canvas.rr(x, 12f, 13f, 14f, 5f, vGrad(12f, 26f, 0x38FFFFFF, 0x00FFFFFF))
        // Tornillos y aro de acento
        for (cx in listOf(10f, 90f)) {
            canvas.drawCircle(cx, 36f, 3.6f, solid(0xFF17171D.toInt()))
            canvas.drawCircle(cx, 36f, 3.6f, stroke(alpha(accent, 0.95f), 1.3f, false))
            canvas.drawCircle(cx, 36f, 1.0f, solid(alpha(accent, 0.9f)))
            canvas.drawCircle(cx, 20f, 1.1f, solid(0xFF17171D.toInt()))
            canvas.drawCircle(cx, 52f, 1.1f, solid(0xFF17171D.toInt()))
        }
        // Barra con el visor
        canvas.rr(27f, 24f, 46f, 24f, 8f, solid(0xFF1B1B21.toInt()))
        canvas.rr(29.5f, 26.5f, 41f, 19f, 6.5f, vGrad(26f, 46f, 0xFF0B0B10.toInt(), 0xFF14141B.toInt()))
        canvas.rr(29.5f, 26.5f, 41f, 9f, 6.5f, vGrad(26.5f, 36f, 0x12FFFFFF, 0x00FFFFFF))

        eyes(canvas, feel, eyeColor)
        mouth(canvas, feel, eyeColor)
    }

    private fun eyes(canvas: Canvas, feel: BotFeel, color: Int) {
        for (cx in listOf(39.5f, 60.5f)) {
            val cy = 34.5f
            val glow = alpha(color, 0.22f)
            when (feel) {
                BotFeel.Calm -> {
                    val w = 6.6f
                    val h = 9f
                    canvas.rr(cx - w / 2 - 1.5f, cy - h / 2 - 1.5f, w + 3f, h + 3f, (w + 3f) / 2.2f, solid(glow))
                    canvas.rr(cx - w / 2, cy - h / 2, w, h, w / 2.6f, solid(color))
                    canvas.rr(cx - w / 2 + 1f, cy - h / 2 + 1f, w * 0.32f, h * 0.28f, 1f, solid(0x8CFFFFFF.toInt()))
                }
                BotFeel.Happy -> {
                    val path = Path().apply { moveTo(cx - 3.6f, cy + 2.2f); quadTo(cx, cy - 4.2f, cx + 3.6f, cy + 2.2f) }
                    canvas.drawPath(path, stroke(alpha(color, 0.30f), 5f))
                    canvas.drawPath(path, stroke(color, 2.4f))
                }
                BotFeel.Sleepy -> {
                    canvas.drawLine(cx - 3.6f, cy + 1.5f, cx + 3.6f, cy + 1.5f, stroke(glow, 4.6f))
                    canvas.drawLine(cx - 3.4f, cy + 1.5f, cx + 3.4f, cy + 1.5f, stroke(color, 2.2f))
                }
                BotFeel.Love -> {
                    val path = Path().apply {
                        moveTo(cx, cy + 4f)
                        cubicTo(cx - 6f, cy - 0.5f, cx - 3.6f, cy - 5f, cx, cy - 1.6f)
                        cubicTo(cx + 3.6f, cy - 5f, cx + 6f, cy - 0.5f, cx, cy + 4f)
                        close()
                    }
                    canvas.drawPath(path, stroke(alpha(color, 0.35f), 3.5f))
                    canvas.drawPath(path, solid(color))
                }
            }
        }
    }

    private fun mouth(canvas: Canvas, feel: BotFeel, color: Int) {
        val my = 41.2f
        when (feel) {
            BotFeel.Happy, BotFeel.Love -> {
                val path = Path().apply { moveTo(46.5f, my - 0.8f); quadTo(50f, my + 2.6f, 53.5f, my - 0.8f) }
                canvas.drawPath(path, stroke(color, 1.5f))
            }
            BotFeel.Sleepy -> canvas.drawCircle(50f, my + 0.2f, 1.3f, solid(alpha(color, 0.8f)))
            BotFeel.Calm -> canvas.drawLine(47.5f, my, 52.5f, my, stroke(alpha(color, 0.55f), 1.4f))
        }
    }

    private fun zzz(canvas: Canvas, color: Int) {
        for (i in 0 until 3) {
            val x = 74f + i * 7f
            val y = 24f - i * 7f
            val sz = 2.2f + i * 1.3f
            val z = Path().apply { moveTo(x - sz, y - sz); lineTo(x + sz, y - sz); lineTo(x - sz, y + sz); lineTo(x + sz, y + sz) }
            canvas.drawPath(z, stroke(alpha(color, 0.5f + i * 0.2f), 1.5f))
        }
    }

    private fun spark(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path()
        for (i in 0 until 8) {
            val ang = Math.toRadians(-90.0 + i * 45.0)
            val rr = if (i % 2 == 0) r else r * 0.28f
            val x = cx + rr * Math.cos(ang).toFloat()
            val y = cy + rr * Math.sin(ang).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, solid(color))
    }

    /** Disco "zen" con relieve: luz arriba a la izquierda y sombra abajo a la derecha. */
    fun orb(ink: Ink, cx: Float, cy: Float, r: Float) {
        val st = ink.st
        val k = ink.k
        val c = ink.canvas
        // Brillo de fondo con el color de acento
        ink.radial(cx, cy, r * 1.55f, alpha(st.accent, if (st.filled) 0.10f else 0.30f), alpha(st.accent, 0f))
        // Disco
        val base = if (st.filled) alpha(0xFFFFFFFF.toInt(), 0.16f) else st.soft
        val disc = Paint(Paint.ANTI_ALIAS_FLAG)
        disc.shader = RadialGradient(
            (cx - r * 0.3f) * k, (cy - r * 0.35f) * k, r * 1.5f * k,
            intArrayOf(alpha(0xFFFFFFFF.toInt(), if (st.dark || st.filled) 0.16f else 0.7f), base, alpha(0xFF000000.toInt(), if (st.dark) 0.30f else 0.10f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(cx * k, cy * k, r * k, disc)
        ink.circleStroke(cx, cy, r - 0.5f, alpha(0xFFFFFFFF.toInt(), if (st.dark || st.filled) 0.14f else 0.8f), 1f)
    }
}
