package app.fitstat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitstat.data.Prefs

@Immutable
class FsShapes(val k: Float, buttonStyle: String, val kind: String = "round", val skinId: String = "fitstat") {
    val cardRadius: Dp = (28 * k).dp
    val innerRadius: Dp = (20 * k).dp
    val chipRadius: Dp = (14 * k).dp
    val smallRadius: Dp = (10 * k).dp
    private val cutKind = kind == "cut"
    private val skinned = skinId != "fitstat"

    private fun cutTL(dp: Float): Shape = CutCornerShape(topStart = dp.dp, topEnd = 0.dp, bottomEnd = dp.dp, bottomStart = 0.dp)

    val card: Shape = if (cutKind) cutTL(16f) else RoundedCornerShape(cardRadius)
    val inner: Shape = if (cutKind) cutTL(11f) else RoundedCornerShape(innerRadius)
    val chip: Shape = if (cutKind) cutTL(8f) else RoundedCornerShape(chipRadius)
    val small: Shape = if (cutKind) cutTL(5f) else RoundedCornerShape(smallRadius)
    val sheet: Shape = if (cutKind) CutCornerShape(topStart = 22.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
    else RoundedCornerShape(topStart = (30 * k).dp.coerceAtLeast(8.dp), topEnd = (30 * k).dp.coerceAtLeast(8.dp))
    val button: Shape = when {
        cutKind -> CutCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 0.dp)
        buttonStyle == "square" -> RoundedCornerShape((6 * k).dp)
        buttonStyle == "rounded" -> RoundedCornerShape((16 * k).dp)
        else -> CircleShape
    }

    /** Fichas y etiquetas (antes siempre píldora). */
    val pill: Shape = when {
        cutKind -> CutCornerShape(topStart = 9.dp, topEnd = 0.dp, bottomEnd = 9.dp, bottomStart = 0.dp)
        kind == "sharp" -> RoundedCornerShape(3.dp)
        else -> CircleShape
    }

    /** Forma de lo que antes era siempre un círculo: avatares, puntos, miniaturas, interruptores. */
    val round: Shape = when {
        cutKind -> CutCornerShape(percent = 30)
        kind == "sharp" -> RoundedCornerShape(percent = 12)
        else -> CircleShape
    }

    /** Aviso flotante y barra de navegación. */
    val toast: Shape = when {
        cutKind -> CutCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 0.dp)
        kind == "sharp" -> RoundedCornerShape(4.dp)
        skinned -> RoundedCornerShape(14.dp)
        else -> CircleShape
    }
    val navBar: Shape = when {
        cutKind -> CutCornerShape(topStart = 18.dp, topEnd = 0.dp, bottomEnd = 18.dp, bottomStart = 0.dp)
        kind == "sharp" -> RoundedCornerShape(4.dp)
        skinned -> RoundedCornerShape(22.dp)
        else -> CircleShape
    }

    /** Indicador de la pestaña activa; null = el dibujo clásico de píldora. */
    val navItem: Shape? = when {
        cutKind -> CutCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 0.dp)
        kind == "sharp" -> RoundedCornerShape(3.dp)
        skinned -> RoundedCornerShape(16.dp)
        else -> null
    }

    /** Contenedor de íconos según el ajuste. */
    fun iconShape(container: String): Shape = when {
        container == "cut" -> CutCornerShape(percent = 28)
        container == "sharp" -> RoundedCornerShape(percent = 10)
        container == "squircle" -> RoundedCornerShape(percent = 34)
        container == "rounded" -> RoundedCornerShape(percent = 24)
        else -> round
    }
}

@Immutable
class FsUi(
    val cardStyle: String,
    val density: Float,
    val thumbScale: Float,
    val photoMode: String,
    val background: String,
    val gradients: Boolean,
    val glow: Float,
    val iconStyle: String,
    val iconWeight: Float,
    val iconContainer: String,
    val units: String,
    val chartStyle: String,
    val records: Boolean,
    val grid: Boolean,
    val values: Boolean,
    val dockedNav: Boolean,
    val navLabels: String,
) {
    fun pad(v: Float): Dp = (v * density).dp
    fun pad(v: Int): Dp = (v * density).dp
}

val LocalColors = staticCompositionLocalOf<FsColors> { error("FitstatTheme no está presente") }
val LocalShapes = staticCompositionLocalOf<FsShapes> { error("FitstatTheme no está presente") }
val LocalType = staticCompositionLocalOf<FsType> { error("FitstatTheme no está presente") }
val LocalMotion = staticCompositionLocalOf { FsMotion.Default }
val LocalUi = staticCompositionLocalOf<FsUi> { error("FitstatTheme no está presente") }
val LocalPrefs = staticCompositionLocalOf { Prefs(emptyMap()) }
val LocalContentColor = compositionLocalOf { Color.Unspecified }

object Fs {
    val colors: FsColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val shapes: FsShapes @Composable @ReadOnlyComposable get() = LocalShapes.current
    val type: FsType @Composable @ReadOnlyComposable get() = LocalType.current
    val motion: FsMotion @Composable @ReadOnlyComposable get() = LocalMotion.current
    val ui: FsUi @Composable @ReadOnlyComposable get() = LocalUi.current
    val prefs: Prefs @Composable @ReadOnlyComposable get() = LocalPrefs.current
    val content: Color @Composable @ReadOnlyComposable get() = LocalContentColor.current.takeIf { it != Color.Unspecified } ?: LocalColors.current.text
    val haptics: Haptics @Composable @ReadOnlyComposable get() = LocalHaptics.current
    val skin: SkinStyle @Composable @ReadOnlyComposable get() = LocalSkin.current
}

internal fun systemAccent(ctx: android.content.Context): Color? {
    if (Build.VERSION.SDK_INT < 31) return null
    return try {
        Color(ctx.getColor(android.R.color.system_accent1_300) or (0xFF shl 24))
    } catch (_: Throwable) {
        null
    }
}

fun resolvePalette(prefs: Prefs, ctx: android.content.Context): AccentPalette {
    val id = prefs.str("theme.palette")
    return when (id) {
        "custom" -> Palettes.fromColor(parseHexColor(prefs.str("theme.custom")) ?: Palettes.all[0].accent, "custom", "Personalizado")
        "sistema" -> systemAccent(ctx)?.let { Palettes.fromColor(it, "sistema", "Sistema") } ?: Palettes.all[0]
        else -> Palettes.byId(id) ?: Palettes.all[0]
    }
}

@Composable
fun FitstatTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    val skinSig = prefs.raw.filterKeys { it.startsWith("skin.") }.toString()
    val skin = remember(skinSig) { SkinStyle.from(prefs) }
    val skinned = !skin.isDefault
    val mode = prefs.str("theme.mode")
    val dark = if (skinned) true else when (mode) {
        "light" -> false
        "system" -> sysDark
        else -> true
    }
    val amoled = mode == "amoled" || (mode == "system" && sysDark && false)
    val paletteKey = prefs.str("theme.palette") + prefs.str("theme.custom")
    val palette = remember(paletteKey) { resolvePalette(prefs, ctx) }
    val tintName = prefs.str("theme.tint")
    val high = prefs.str("theme.contrast") == "high"
    val colors = remember(dark, amoled, palette, tintName, high, skin) {
        if (skinned) skinColors(skin)
        else buildColors(dark, amoled, palette, when (tintName) { "none" -> 0f; "strong" -> 0.22f; else -> 0.10f }, high)
    }

    val radius = prefs.str("shape.radius")
    val buttonStyle = prefs.str("shape.button")
    val shapes = remember(radius, buttonStyle, skin) {
        if (!skinned) FsShapes(when (radius) { "sharp" -> 0.3f; "soft" -> 0.7f; "extra" -> 1.3f; else -> 1f }, buttonStyle)
        else when (skin.cut) {
            "cut" -> FsShapes(1f, "rounded", "cut", skin.id)
            "sharp" -> FsShapes(0.14f, "square", "sharp", skin.id)
            else -> FsShapes(0.55f, "rounded", "round", skin.id)
        }
    }

    val font = if (skinned && !skin.ownFont) skin.info.defFont else prefs.str("type.font")
    val scale = prefs.float("type.scale") / 100f
    val weight = prefs.str("type.weight")
    val tracking = prefs.float("type.tracking")
    val caps = if (skinned) skin.caps else prefs.bool("type.caps")
    val digits = if (skinned) skin.digits else prefs.str("type.digits")
    val type = remember(font, scale, weight, tracking, caps, digits, skinned) {
        FsType(
            Fonts.family(font), scale, when (weight) { "light" -> -100; "strong" -> 100; else -> 0 },
            tracking / 100f, caps, digits, labelMono = skinned,
        )
    }

    val enabled = prefs.bool("motion.enabled")
    val skinFx = skinned && skin.anim > 0f && enabled
    val motion = FsMotion(
        enabled = enabled,
        speed = (prefs.float("motion.speed") / 100f).coerceIn(0.4f, 3f),
        entrance = prefs.bool("motion.entrance"),
        press = prefs.bool("motion.press"),
        count = prefs.bool("motion.count"),
        breathing = prefs.bool("motion.breathing"),
        mascot = prefs.bool("motion.mascot"),
        confetti = prefs.bool("motion.confetti"),
        transition = if (skinFx) (if (skin.isCarmesi) "scan" else "shutter") else prefs.str("motion.transition").let { if (it == "scan" || it == "shutter") "slide" else it },
        skin = if (skinned) skin.id else "fitstat",
        skinLevel = if (skinFx) skin.anim else 0f,
    )

    val ui = FsUi(
        cardStyle = if (skinned) (if (skin.isAcero) "outline" else "raised") else prefs.str("shape.card"),
        density = if (skin.isAcero) 0.96f else when (prefs.str("shape.density")) { "compact" -> 0.86f; "roomy" -> 1.14f; else -> 1f },
        thumbScale = when (prefs.str("shape.thumbs")) { "small" -> 0.85f; "large" -> 1.2f; else -> 1f },
        photoMode = prefs.str("theme.photos"),
        background = if (skinned) "skin" else prefs.str("theme.background"),
        gradients = if (skinned) skin.isCarmesi else prefs.bool("theme.gradients"),
        glow = prefs.float("theme.glow") / 100f,
        iconStyle = if (skinned) "line" else prefs.str("shape.icon_style"),
        iconWeight = if (skinned) (if (skin.isAcero) 2.25f else 1.75f) else prefs.float("shape.icon_weight"),
        iconContainer = if (skinned) (if (skin.cut == "cut") "cut" else if (skin.cut == "sharp") "sharp" else "rounded") else prefs.str("shape.icon_container"),
        units = prefs.str("stats.units"),
        chartStyle = prefs.str("stats.chart"),
        records = prefs.bool("stats.records"),
        grid = prefs.bool("stats.grid"),
        values = prefs.bool("stats.values"),
        dockedNav = if (skinned) skin.isAcero else prefs.str("home.nav") == "docked",
        navLabels = if (skin.isAcero) "always" else prefs.str("home.labels"),
    )

    val hf = LocalHapticFeedback.current
    val haptics = remember(prefs.str("motion.haptics"), hf) {
        Haptics(when (prefs.str("motion.haptics")) { "soft" -> 1; "normal" -> 2; else -> 0 }, hf)
    }

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalShapes provides shapes,
        LocalType provides type,
        LocalMotion provides motion,
        LocalUi provides ui,
        LocalPrefs provides prefs,
        LocalHaptics provides haptics,
        LocalSkin provides skin,
        LocalContentColor provides colors.text,
        content = content,
    )
}
