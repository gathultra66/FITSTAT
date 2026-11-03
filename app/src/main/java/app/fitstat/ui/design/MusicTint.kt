package app.fitstat.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.FsColors
import app.fitstat.ui.theme.LocalColors
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix

/**
 * Mientras suena la música, la app entera toma el color dominante de la portada: los acentos (botones, indicadores, meta…)
 * y un toque del fondo. Al pausar o terminar la canción vuelve despacio al color que elegiste. Lo pone la raíz de la app,
 * así que alcanza a todas las pantallas y hojas. Con el ajuste apagado, o sin música, no cambia nada.
 */
@Composable
fun MusicTintHost(content: @Composable () -> Unit) {
    val base = Fs.colors
    val prefs = Fs.prefs
    val app = LocalApp.current
    val hub = app.connectors
    val music = hub.music.state
    val on = prefs.bool("music.tint")
    val playingNow = on && music.connected && music.playing && hub.musicReady()
    // Entra en un momento y sale despacio (el color de la portada se queda un rato al pausar).
    val strength by animateFloatAsState(
        if (playingNow) prefs.int("music.tint_strength") / 100f else 0f,
        tween(if (playingNow) 900 else 3200, easing = FastOutSlowInEasing),
        label = "musicTintStrength",
    )
    val coverRaw = app.rhythm.coverColor
    val coverTarget = coverRaw?.let { coverTone(Color(it)) } ?: base.accent
    // Al cambiar de canción el color se desliza hacia el de la nueva portada.
    val cover by animateColorAsState(coverTarget, tween(1400), label = "musicTintCover")
    val colors = if (strength <= 0.002f) base else base.tinted(cover, strength)
    CompositionLocalProvider(LocalColors provides colors) { content() }
}

/**
 * El color de la portada, ajustado para que se lea como acento: si tiene algo de tono se le sube la viveza (un cromo
 * azulado debe verse azul, no gris) y ni casi negro ni casi blanco.
 */
internal fun coverTone(c: Color): Color {
    var out = vivid(c)
    // Se aclara (u oscurece) en pasos hasta caer dentro del rango legible, sin depender de cómo se mezcla.
    if (out.luminance() < 0.16f) {
        repeat(12) { if (out.luminance() < 0.16f) out = out.mix(Color.White, 0.12f) }
    } else if (out.luminance() > 0.80f) {
        repeat(12) { if (out.luminance() > 0.80f) out = out.mix(Color.Black, 0.12f) }
    }
    return out
}

/** Sube la saturación hasta un mínimo (0,45) si el color tiene tono; los grises puros se quedan grises. */
internal fun vivid(c: Color): Color {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    val d = mx - mn
    if (mx <= 0f || d / mx < 0.06f || d / mx >= 0.45f) return c
    val k = 0.45f * mx / d                       // factor para llevar la saturación a 0,45 manteniendo el brillo
    fun f(v: Float) = (mx + (v - mx) * k).coerceIn(0f, 1f)
    return Color(f(c.red), f(c.green), f(c.blue), c.alpha)
}

/** Los colores de la app con el acento de la portada en la proporción [s] (0 = los tuyos, 1 = los de la portada). */
internal fun FsColors.tinted(cover: Color, s: Float): FsColors {
    val k = s.coerceIn(0f, 1f)
    val coverOn = if (cover.luminance() > 0.34f) Color(0xFF14111F) else Color.White
    val coverText = if (dark) cover else cover.mix(Color.Black, 0.35f)
    val coverBg = if (dark) cover.mix(Color.Black, 0.55f) else cover.mix(Color.White, 0.80f)
    return FsColors(
        dark = dark,
        bg = bg.mix(coverBg, 0.40f * k),
        surface = surface.mix(coverBg, 0.24f * k),
        surface2 = surface2.mix(coverBg, 0.24f * k),
        surface3 = surface3.mix(coverBg, 0.24f * k),
        outline = outline.mix(cover, 0.22f * k),
        text = text,
        textDim = textDim,
        textFaint = textFaint,
        accent = accent.mix(cover, k),
        accent2 = accent2.mix(cover.mix(Color.White, 0.3f), k),
        onAccent = onAccent.mix(coverOn, k),
        accentText = accentText.mix(coverText, k),
        accentSoft = accentSoft.mix(cover.a(accentSoft.alpha), k),
        good = good,
        warn = warn,
        bad = bad,
        scrim = scrim,
    )
}
