package app.fitstat.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.data.Changelog
import app.fitstat.data.Fmt
import app.fitstat.data.Opt
import app.fitstat.data.Pages
import app.fitstat.data.Prefs
import app.fitstat.data.SType
import app.fitstat.data.SettingSpec
import app.fitstat.data.Settings
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.BotMood
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.Card
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.FsSlider
import app.fitstat.ui.design.FsTextField
import app.fitstat.ui.design.GroupCard
import app.fitstat.ui.design.HairLine
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.IconBadge
import app.fitstat.ui.design.LedBanner
import app.fitstat.ui.design.RoundButton
import app.fitstat.ui.design.ScreenList
import app.fitstat.ui.design.Segmented
import app.fitstat.ui.design.Toggle
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.enter
import app.fitstat.ui.design.icOf
import app.fitstat.ui.design.tappable
import app.fitstat.conn.Connectors
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.parseHexColor

// ───────────────────────── Pantalla principal de Ajustes

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val prefs = repo.prefs
    ScreenList("Ajustes", subtitle = "Hazla tuya") {
        item("profile") { Box(Modifier.enter(0)) { ProfileCard() } }
        item("bot") { Box(Modifier.enter(1)) { BotShortcut() } }
        item("gs") {
            Box(Modifier.enter(2)) { PageGroup("Skin", listOf(Pages.SKIN)) }
        }
        item("g1") {
            Box(Modifier.enter(3)) { PageGroup("Personalización", listOf(Pages.THEME, Pages.SHAPES, Pages.TYPE, Pages.MOTION, Pages.ICON)) }
        }
        item("g2") {
            Box(Modifier.enter(4)) { PageGroup("Tu app", listOf(Pages.STATS, Pages.HOME, Pages.TRAIN, Pages.SLEEP)) }
        }
        item("g3") {
            Box(Modifier.enter(5)) { PageGroup("Extras", listOf(Pages.CONNECTORS, Pages.MUSICFX, Pages.WIDGETS, Pages.REMINDERS)) }
        }
        item("g4") {
            Box(Modifier.enter(6)) {
                GroupCard(title = "Tus datos") {
                    Column {
                        LinkRow(Ic.Database, "Datos", "Exportar, importar y borrar") { app.nav.push(Route.DataTools) }
                        HairLine(Modifier.padding(start = 72.dp))
                        LinkRow(Ic.ClipboardList, "Registro de cambios", "Versión ${Changelog.latest.version} · novedades") { app.nav.push(Route.Changelog) }
                        HairLine(Modifier.padding(start = 72.dp))
                        LinkRow(Ic.Info, "Acerca de FITSTAT", "Créditos y licencias") { app.nav.push(Route.About) }
                    }
                }
            }
        }
        item("reset") {
            Box(Modifier.enter(7)) {
                FsButton(
                    "Restablecer todos los ajustes",
                    {
                        app.overlays.confirm(
                            "¿Restablecer todo?",
                            "Vuelven los colores, formas, tipografía y demás ajustes a como venían. Tus rutinas, series y notas no se tocan.",
                            "Restablecer", danger = true,
                        ) {
                            repo.resetAllSettings()
                            app.toastUndo("Ajustes restablecidos")
                        }
                    },
                    Modifier.fillMaxWidth(), kind = BtnKind.Danger, icon = Ic.RotateCcw, height = 48.dp,
                )
            }
        }
        item("ver") {
            Txt("FITSTAT v${Changelog.latest.version}", Modifier.fillMaxWidth(), Fs.type.caption, c.textFaint, align = TextAlign.Center)
        }
    }
}

@Composable
private fun ProfileCard() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val name = repo.profile.name
    Card(accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(Fs.shapes.round).background(c.onAccent.a(0.14f)), contentAlignment = Alignment.Center) {
                FitBot(size = 56.dp)
            }
            Column(Modifier.weight(1f)) {
                Txt(if (name.isBlank()) "Atleta" else name, style = Fs.type.h1, color = c.onAccent, maxLines = 1)
                Txt("Meta: ${repo.weeklyGoal} días por semana", style = Fs.type.small, color = c.onAccentDim, maxLines = 1)
            }
            RoundButton(Ic.Edit, { app.overlays.sheet { close -> NameSheet(name) { repo.setProfileName(it); close() } } }, bg = c.onAccent.a(0.16f), tint = c.onAccent, border = false)
        }
        LedBanner("${Fs.skin.brand}  V${Changelog.latest.version}", Modifier.padding(top = 16.dp), lit = c.onAccent.a(0.85f))
    }
}

@Composable
private fun NameSheet(current: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    SheetBody("¿Cómo te llamas?", subtitle = "FITBOT y el inicio te saludarán por tu nombre") {
        FsTextField(text, { text = it.take(40) }, placeholder = "Tu nombre", leading = Ic.User, onDone = { onSave(text) })
        FsButton("Guardar", { onSave(text) }, Modifier.fillMaxWidth(), height = 50.dp)
    }
}

/** Arriba de los ajustes de sueño: atajo a la pantalla del sueño y a los conectores que lo alimentan. */
@Composable
private fun SleepLinkCard() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val nights = repo.sleep.size
    Card(onClick = { app.nav.push(Route.Sleep) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(Ic.Moon, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Txt("Ver mi sueño", style = Fs.type.h3, maxLines = 1)
                Txt(if (nights == 0) "Aún no hay noches: conecta un reloj o estima con el teléfono" else "$nights ${if (nights == 1) "noche guardada" else "noches guardadas"}", style = Fs.type.small, color = c.textDim, maxLines = 2)
            }
            Icon(Ic.ChevronRight, size = 20.dp, tint = c.textDim)
        }
    }
}

@Composable
private fun BotShortcut() {
    val app = LocalApp.current
    val c = Fs.colors
    val model = app.bot.model
    val hasKey = app.bot.hasKey
    Card(onClick = { app.nav.push(Route.SettingsPage(Pages.BOT)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).clip(Fs.shapes.round).background(c.accent.a(0.12f)), contentAlignment = Alignment.Center) {
                FitBot(size = 46.dp, animated = false, mood = if (hasKey) BotMood.Idle else BotMood.Sleepy)
            }
            Column(Modifier.weight(1f)) {
                Txt("${Fs.skin.word("FITBOT")} y API", style = Fs.type.h3, maxLines = 1)
                Txt(
                    if (hasKey) "Clave lista · ${model.name}" else "Falta tu clave de OpenRouter",
                    style = Fs.type.small, color = if (hasKey) c.textDim else c.warn, maxLines = 1,
                )
            }
            Icon(Ic.ChevronRight, size = 20.dp, tint = c.textDim)
        }
    }
}

@Composable
private fun PageGroup(title: String, ids: List<String>) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    GroupCard(title = title) {
        Column {
            ids.forEachIndexed { i, id ->
                val p = Settings.pages.firstOrNull { it.id == id } ?: return@forEachIndexed
                if (i > 0) HairLine(Modifier.padding(start = 72.dp))
                LinkRow(icOf(p.icon), p.title, p.subtitle, hint = pageHint(id, repo.prefs, repo.reminders.count { it.enabled })) { app.nav.push(Route.SettingsPage(id)) }
            }
        }
    }
}

@Composable
private fun LinkRow(ic: Ic, title: String, subtitle: String, hint: String = "", onClick: () -> Unit) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick, pressScale = 0.985f).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(ic, size = 42.dp)
        Column(Modifier.weight(1f)) {
            Txt(title, style = Fs.type.bodyStrong, maxLines = 1)
            Txt(subtitle, style = Fs.type.small, color = c.textDim, maxLines = 1)
        }
        if (hint.isNotEmpty()) Txt(hint, Modifier.padding(start = 4.dp), Fs.type.smallStrong, c.accentText, maxLines = 1)
        Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
    }
}

internal fun optionLabel(prefs: Prefs, key: String): String {
    val spec = Settings.byKey[key] ?: return ""
    val v = prefs.str(key)
    return (spec.type as? SType.Choice)?.options?.firstOrNull { it.value == v }?.label?.substringBefore(" (") ?: v
}

private fun pageHint(id: String, prefs: Prefs, activeReminders: Int): String = when (id) {
    Pages.THEME -> optionLabel(prefs, "theme.palette")
    Pages.SHAPES -> optionLabel(prefs, "shape.radius")
    Pages.TYPE -> optionLabel(prefs, "type.font")
    Pages.MOTION -> if (prefs.bool("motion.enabled")) "${prefs.int("motion.speed")}%" else "Apagadas"
    Pages.STATS -> optionLabel(prefs, "stats.chart")
    Pages.HOME -> ""
    Pages.TRAIN -> "${prefs.int("train.goal")} días"
    Pages.REMINDERS -> if (!prefs.bool("reminders.enabled")) "Apagados" else if (activeReminders > 0) "$activeReminders activos" else ""
    Pages.ICON -> optionLabel(prefs, "app.icon")
    Pages.SLEEP -> "${prefs.str("sleep.goal")} h"
    Pages.MUSICFX -> if (prefs.bool("fx.enabled")) "Activas" else "Apagadas"
    Pages.CONNECTORS -> Connectors.all.count { prefs.bool(it.enabledKey) }.let { if (it > 0) "$it activos" else "" }
    Pages.SKIN -> app.fitstat.data.Skins.byId(prefs.str("skin.id")).name
    else -> ""
}

// ───────────────────────── Página de ajustes (se genera del esquema)

@Composable
fun SettingsPageScreen(page: String) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val info = Settings.pages.firstOrNull { it.id == page }
    val skinId = repo.prefs.str("skin.id")
    val skinned = !Fs.skin.isDefault
    val specs = Settings.specsOf(page).filter {
        !(page == Pages.BOT && it.key == "bot.model") && page != Pages.ICON && page != Pages.CONNECTORS &&
            // En Skins, el selector es la galería; el ícono sincronizado solo importa con una skin puesta.
            !(page == Pages.SKIN && (it.key == "skin.id" || (it.key == "skin.icon_sync" && skinId == "fitstat")))
    }
    val sections = specs.groupBy { it.section }
    ScreenList(info?.title ?: "Ajustes", subtitle = info?.subtitle, onBack = { app.nav.back() }) {
        var n = 0
        if (skinned && page in listOf(Pages.THEME, Pages.SHAPES, Pages.TYPE, Pages.MOTION)) item("skinnote") { SkinNote() }
        when (page) {
            Pages.SKIN -> item("skins") { Box(Modifier.enter(0)) { SkinsSection() } }
            Pages.THEME, Pages.SHAPES, Pages.TYPE -> item("preview") { Box(Modifier.enter(0)) { StylePreview() } }
            Pages.BOT -> item("bot") { Box(Modifier.enter(0)) { BotApiSection() } }
            Pages.HOME -> item("homeedit") { Box(Modifier.enter(0)) { HomeLayoutSection() } }
            Pages.WIDGETS -> item("widgets") { Box(Modifier.enter(0)) { WidgetsSection() } }
            Pages.ICON -> item("icon") { Box(Modifier.enter(0)) { AppIconSection() } }
            Pages.REMINDERS -> item("reminders") { Box(Modifier.enter(0)) { RemindersSection() } }
            Pages.CONNECTORS -> item("connectors") { ConnectorsSection() }
            Pages.MUSICFX -> item("musicfx") { Box(Modifier.enter(0)) { MusicFxSection() } }
            Pages.SLEEP -> item("sleeplink") { Box(Modifier.enter(0)) { SleepLinkCard() } }
        }
        n = 1
        for ((section, list) in sections) {
            val idx = n++
            item("sec:$section") { Box(Modifier.enter(idx)) { SettingsGroup(section, list) } }
        }
        if (specs.isNotEmpty()) {
            item("reset") {
                FsButton(
                    "Restablecer esta página",
                    {
                        repo.resetPage(page)
                        app.toastUndo("Página restablecida")
                    },
                    Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.RotateCcw, height = 46.dp,
                )
            }
        }
    }
}

@Composable
internal fun SettingsGroup(title: String, specs: List<SettingSpec>) {
    val prefs = LocalRepo.current.prefs
    val visible = specs.filter { s -> s.visibleIf?.let { (k, v) -> prefs.str(k) == v } ?: true }
    if (visible.isEmpty()) return
    GroupCard(title = title) {
        Column(Modifier.animateContentSize()) {
            visible.forEachIndexed { i, s ->
                if (i > 0) HairLine(Modifier.padding(horizontal = 16.dp))
                SettingRow(s)
            }
        }
    }
}

private val tileColumns = mapOf(
    "theme.mode" to 2, "theme.background" to 2, "shape.radius" to 2, "shape.card" to 2, "shape.button" to 3,
    "shape.density" to 3, "shape.thumbs" to 3, "shape.icon_style" to 3, "shape.icon_container" to 2,
    "type.digits" to 3, "home.nav" to 2,
)

private fun shortLabel(o: Opt) = o.label.substringBefore(" (")

@Composable
internal fun SettingRow(spec: SettingSpec) {
    val repo = LocalRepo.current
    val app = LocalApp.current
    val c = Fs.colors
    val prefs = repo.prefs
    val value = prefs.str(spec.key)
    val custom = prefs.isCustom(spec.key)
    val pad = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)

    @Composable
    fun Header(trailing: @Composable () -> Unit = {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Txt(spec.title, style = Fs.type.bodyStrong)
                Txt(spec.desc, style = Fs.type.small, color = c.textDim)
            }
            trailing()
            if (custom) {
                Box(Modifier.size(32.dp).tappable({ repo.resetSetting(spec.key) }, pressScale = 0.88f).clip(Fs.shapes.round), contentAlignment = Alignment.Center) {
                    Icon(Ic.RotateCcw, size = 15.dp, tint = c.textFaint)
                }
            }
        }
    }

    when (val t = spec.type) {
        SType.Toggle -> Row(
            Modifier.fillMaxWidth().tappable({ repo.setSetting(spec.key, (!prefs.bool(spec.key)).toString()) }, pressScale = 0.99f, haptic = false)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Txt(spec.title, style = Fs.type.bodyStrong)
                Txt(spec.desc, style = Fs.type.small, color = c.textDim)
            }
            Toggle(prefs.bool(spec.key), { repo.setSetting(spec.key, it.toString()) })
        }

        is SType.Choice -> {
            val opts = t.options
            val set: (String) -> Unit = { repo.setSetting(spec.key, it) }
            when {
                spec.key == "theme.palette" -> Column(pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header()
                    PaletteGrid(value, prefs.str("theme.custom"), set)
                }
                spec.key == "type.font" -> Column(pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header()
                    FontList(opts, value, set)
                }
                spec.key == "stats.chart" -> Column(pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header()
                    ChartTiles(opts, value, set)
                }
                spec.key in tileColumns -> Column(pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header()
                    StyleTiles(spec, opts, value, tileColumns.getValue(spec.key), set)
                }
                opts.size in 2..6 && opts.maxOf { shortLabel(it).length } <= (315f / opts.size / 7.6f).toInt() - 1 -> Column(pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header()
                    Segmented(opts.map { shortLabel(it) }, opts.indexOfFirst { it.value == value }.coerceAtLeast(0), { set(opts[it].value) })
                }
                else -> Row(
                    Modifier.fillMaxWidth().tappable(
                        { app.overlays.pick(spec.title, opts, value, spec.desc) { set(it) } }, pressScale = 0.99f,
                    ).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) { Header() }
                    Txt(opts.firstOrNull { it.value == value }?.let { shortLabel(it) } ?: value, Modifier.padding(start = 4.dp), Fs.type.smallStrong, c.accentText, maxLines = 1)
                    Icon(Ic.ChevronDown, size = 16.dp, tint = c.textFaint)
                }
            }
        }

        is SType.Slider -> {
            var drag by remember(spec.key, value) { mutableStateOf<Float?>(null) }
            val current = drag ?: (value.replace(',', '.').toFloatOrNull() ?: t.min)
            Column(pad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Header {
                    Txt(Fmt.num(current.toDouble(), t.decimals) + t.unit, style = Fs.type.bodyStrong, color = c.accentText, maxLines = 1)
                }
                FsSlider(
                    current, { drag = it }, t.min, t.max, Modifier.padding(top = 4.dp), step = t.step,
                    onFinished = {
                        drag?.let { v -> repo.setSetting(spec.key, v.toString()) }
                        drag = null
                    },
                )
            }
        }

        SType.ColorHex -> Row(
            Modifier.fillMaxWidth().tappable(
                { app.overlays.pickColor(spec.title, parseHexColor(value) ?: c.accent) { repo.setSetting(spec.key, it) } }, pressScale = 0.99f,
            ).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) { Header() }
            Box(Modifier.size(34.dp).clip(Fs.shapes.round).background(parseHexColor(value) ?: c.accent).border(2.dp, c.outline, Fs.shapes.round))
        }

        SType.Time -> Row(
            Modifier.fillMaxWidth().tappable(
                {
                    val hh = value.substringBefore(':').toIntOrNull() ?: 18
                    val mm = value.substringAfter(':', "0").toIntOrNull() ?: 0
                    app.overlays.pickTime(spec.title, hh, mm) { h, m -> repo.setSetting(spec.key, Fmt.time(h, m)) }
                }, pressScale = 0.99f,
            ).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) { Header() }
            Box(Modifier.clip(Fs.shapes.pill).background(c.accentSoft).padding(horizontal = 14.dp, vertical = 7.dp)) {
                Txt(value, style = Fs.type.bodyStrong, color = c.accentText, maxLines = 1)
            }
        }

        is SType.Text -> {
            var text by remember(spec.key, value) { mutableStateOf(value) }
            Column(pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header()
                FsTextField(text, { text = it }, placeholder = t.hint, onDone = { repo.setSetting(spec.key, text) })
            }
        }
    }
}
