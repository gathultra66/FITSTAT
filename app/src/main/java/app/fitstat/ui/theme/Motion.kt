package app.fitstat.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring as composeSpring
import androidx.compose.animation.core.tween as composeTween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Ajustes de animación: todo movimiento de la app pasa por aquí. */
@Immutable
class FsMotion(
    val enabled: Boolean,
    val speed: Float,
    val entrance: Boolean,
    val press: Boolean,
    val count: Boolean,
    val breathing: Boolean,
    val mascot: Boolean,
    val confetti: Boolean,
    val transition: String,
    /** Skin activa ("fitstat" = ninguna) y qué tanto anima (0 = nada, 0.5 = suave, 1 = completa). */
    val skin: String = "fitstat",
    val skinLevel: Float = 0f,
) {
    private val k: Float get() = if (speed <= 0f) 1f else speed

    /** Hay animaciones propias de la skin. */
    val skinFx: Boolean get() = enabled && skinLevel > 0f && skin != "fitstat"

    /** Curva principal: suave en la app clásica y mecánica (arranque brusco, frenado seco) en Acero. */
    val ease: Easing get() = if (skin == "acero") CubicBezierEasing(0.7f, 0f, 0.1f, 1f) else FastOutSlowInEasing

    fun ms(base: Int): Int = if (!enabled) 0 else (base / k).toInt().coerceAtLeast(1)

    fun <T> tween(base: Int, delay: Int = 0, easing: Easing? = null): FiniteAnimationSpec<T> =
        if (!enabled) snap() else composeTween(ms(base), (delay / k).toInt(), easing ?: ease)

    fun <T> spring(damping: Float = 0.78f, stiffness: Float = Spring.StiffnessMediumLow): FiniteAnimationSpec<T> =
        if (!enabled) snap() else composeSpring(damping, stiffness * k * k)

    /** Retardo escalonado para entradas en cascada. */
    fun stagger(index: Int, step: Int = 45, max: Int = 360): Int =
        if (!enabled || !entrance) 0 else (index * step).coerceAtMost(max)

    val loops: Boolean get() = enabled && breathing
    val mascotLoops: Boolean get() = enabled && mascot

    companion object {
        val Default = FsMotion(true, 1f, true, true, true, true, true, true, "slide")
    }
}

/** Respuesta háptica según el ajuste. 0 = apagada, 1 = suave, 2 = normal. */
class Haptics(private val level: Int, private val hf: HapticFeedback?) {
    fun tick() {
        if (level <= 0) return
        hf?.performHapticFeedback(if (level >= 2) HapticFeedbackType.ContextClick else HapticFeedbackType.SegmentTick)
    }

    fun confirm() {
        if (level <= 0) return
        hf?.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    fun heavy() {
        if (level <= 0) return
        hf?.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun toggle(on: Boolean) {
        if (level <= 0) return
        hf?.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    }

    companion object {
        val None = Haptics(0, null)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics.None }
