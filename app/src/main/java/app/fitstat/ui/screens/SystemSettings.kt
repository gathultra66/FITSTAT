package app.fitstat.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fitstat.data.Fmt
import app.fitstat.data.Opt
import app.fitstat.data.Reminder
import app.fitstat.data.Weekdays
import app.fitstat.sys.Notifier
import app.fitstat.sys.Reminders
import app.fitstat.sys.WidgetArt
import app.fitstat.sys.WidgetData
import app.fitstat.sys.WidgetKind
import app.fitstat.sys.WidgetStyle
import app.fitstat.sys.Widgets
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.design.BotMood
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.Card
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.FsTextField
import app.fitstat.ui.design.GroupCard
import app.fitstat.ui.design.HairLine
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.IconBadge
import app.fitstat.ui.design.Label
import app.fitstat.ui.design.Pill
import app.fitstat.ui.design.Toggle
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.WeekdayPicker
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a

/** Se ejecuta cada vez que la pantalla vuelve a primer plano (p. ej. tras cambiar un permiso). */
@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) block() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Widgets
// ═════════════════════════════════════════════════════════════════════════════

/** Ajustes › Widgets: vista previa real de cada widget y botón para ponerlo en la pantalla de inicio. */
@Composable
internal fun WidgetsSection() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val c = Fs.colors
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }
    val installed = remember(tick) { runCatching { Widgets.installedCount(ctx) }.getOrDefault(0) }
    val hasData = repo.hasUserData
    val settings = repo.data.settings
    val style = remember(settings, c.dark) { WidgetStyle.from(ctx, repo.prefs) }
    val data = remember(repo.data) { if (hasData) WidgetData.from(repo) else WidgetData.sample(repo.today()) }
    val density = LocalDensity.current.density

    Column(verticalArrangement = Arrangement.spacedBy(Fs.ui.pad(14))) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(Ic.Widgets, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Txt("Tu pantalla de inicio", style = Fs.type.h3, maxLines = 1)
                    Txt(
                        if (installed == 0) "Aún no tienes widgets puestos" else "Tienes ${Fmt.plural(installed, "widget puesto", "widgets puestos")}",
                        style = Fs.type.small, color = if (installed == 0) c.textDim else c.good, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Txt(
                "Usan tus colores, tu tipografía y tus datos, y se actualizan solos cuando entrenas." +
                    if (!hasData) " Mientras no tengas datos, las vistas previas son de ejemplo." else "",
                style = Fs.type.small, color = c.textDim,
            )
        }

        for ((i, kind) in WidgetKind.values().withIndex()) {
            val bmp = remember(kind, style, data, density) {
                val w = kind.defaultW.toFloat()
                val h = kind.defaultH.toFloat()
                runCatching { WidgetArt.render(ctx, kind, w, h, data, style, density).asImageBitmap() }.getOrNull()
            }
            Card(Modifier.animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(kind.title, style = Fs.type.h3, maxLines = 1)
                        Txt(kind.blurb, style = Fs.type.small, color = c.textDim, maxLines = 2)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp).clip(Fs.shapes.inner)
                        .background(Brush.verticalGradient(listOf(c.surface2, c.surface3.a(0.6f)))).padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(bmp, contentDescription = "Vista previa del widget ${kind.title}", modifier = Modifier.size(kind.defaultW.dp, kind.defaultH.dp))
                    } else {
                        Txt("No se pudo dibujar la vista previa", style = Fs.type.small, color = c.textFaint)
                    }
                }
                FsButton("Añadir a la pantalla de inicio", {
                    val ok = Widgets.requestPin(ctx, kind)
                    if (!ok) app.overlays.toast("Tu lanzador no lo permite desde aquí. Mantén pulsada la pantalla de inicio y busca «FITSTAT» en Widgets.", icon = Ic.Info)
                }, Modifier.fillMaxWidth(), icon = Ic.Plus, height = 48.dp, kind = BtnKind.Secondary)
            }
        }

        Txt(
            "¿No aparece el botón? Mantén pulsada una parte vacía de tu pantalla de inicio, entra a Widgets y busca «FITSTAT».",
            Modifier.fillMaxWidth().padding(horizontal = 8.dp), Fs.type.caption, c.textFaint, align = TextAlign.Center,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Recordatorios
// ═════════════════════════════════════════════════════════════════════════════

/** Ajustes › Recordatorios: lista de avisos, permisos y creación. */
@Composable
internal fun RemindersSection() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val c = Fs.colors
    val list = repo.reminders
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }
    val canPost = remember(tick) { Notifier.canPost(ctx) }
    val exact = remember(tick) { Reminders.canScheduleExact(ctx) }
    val anyOn = repo.prefs.bool("reminders.enabled") && list.any { it.enabled }

    fun openNotificationSettings() {
        runCatching {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Fs.ui.pad(14))) {
        AnimatedVisibility(
            anyOn && !canPost,
            enter = expandVertically(Fs.motion.tween(260)) + fadeIn(Fs.motion.tween(220)),
            exit = shrinkVertically(Fs.motion.tween(200)) + fadeOut(Fs.motion.tween(140)),
        ) {
            Card(color = c.warn.a(0.14f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Ic.BellOff, size = 42.dp, tint = c.warn, bg = c.warn.a(0.18f))
                    Column(Modifier.weight(1f)) {
                        Txt("Las notificaciones están apagadas", style = Fs.type.bodyStrong)
                        Txt("Sin ellas, los avisos no pueden sonar.", style = Fs.type.small, color = c.textDim)
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FsButton("Permitir", { app.ensureNotifications() }, Modifier.weight(1f), height = 44.dp, icon = Ic.Check)
                    FsButton("Ajustes", { openNotificationSettings() }, Modifier.weight(1f), kind = BtnKind.Secondary, height = 44.dp, icon = Ic.Cog)
                }
            }
        }
        AnimatedVisibility(
            anyOn && canPost && !exact,
            enter = expandVertically(Fs.motion.tween(260)) + fadeIn(Fs.motion.tween(220)),
            exit = shrinkVertically(Fs.motion.tween(200)) + fadeOut(Fs.motion.tween(140)),
        ) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Ic.Alarm, size = 42.dp)
                    Column(Modifier.weight(1f)) {
                        Txt("¿Quieres que lleguen a la hora exacta?", style = Fs.type.bodyStrong)
                        Txt("Android puede retrasarlos unos minutos. Permite «Alarmas y recordatorios» para esta app.", style = Fs.type.small, color = c.textDim)
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    FsButton("Abrir permiso", {
                        runCatching {
                            ctx.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }, Modifier.fillMaxWidth().padding(top = 12.dp), kind = BtnKind.Secondary, height = 44.dp, icon = Ic.External)
                }
            }
        }

        GroupCard(title = "Tus recordatorios") {
            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitBot(size = 84.dp, mood = BotMood.Sleepy)
                    Txt("Aún no tienes recordatorios", style = Fs.type.h3, align = TextAlign.Center)
                    Txt("Crea uno y te aviso a la hora que elijas para que no se te pase entrenar.", style = Fs.type.small, color = c.textDim, align = TextAlign.Center)
                }
            } else {
                Column(Modifier.animateContentSize()) {
                    list.forEachIndexed { i, r ->
                        if (i > 0) HairLine(Modifier.padding(horizontal = 16.dp))
                        ReminderRow(r)
                    }
                }
            }
        }

        FsButton("Nuevo recordatorio", { app.editReminder(null) }, Modifier.fillMaxWidth(), icon = Ic.Plus, height = 50.dp)
        FsButton("Probar cómo se ve el aviso", {
            val demo = Reminder("test", "Así se verá tu recordatorio", "", 18, 0, emptySet(), true, null)
            if (!Notifier.show(ctx, demo, repo)) {
                app.ensureNotifications()
                app.overlays.toast("Activa las notificaciones para ver el aviso.", icon = Ic.BellOff)
            }
        }, Modifier.fillMaxWidth(), kind = BtnKind.Secondary, icon = Ic.BellRing, height = 46.dp)
    }
}

@Composable
private fun ReminderRow(r: Reminder) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val routine = r.routineId?.let { repo.routine(it) }
    val fade by animateFloatAsState(if (r.enabled) 1f else 0.5f, Fs.motion.tween(200), label = "remAlpha")
    Row(
        Modifier.fillMaxWidth().tappable({ app.editReminder(r) }, pressScale = 0.99f).padding(horizontal = Fs.ui.pad(16), vertical = Fs.ui.pad(14)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f).alpha(fade)) {
            Txt(Fmt.time(r.hour, r.minute), style = Fs.type.h1, maxLines = 1)
            Txt(r.title, style = Fs.type.bodyStrong, maxLines = 1)
            Txt(
                Weekdays.label(r.days) + if (routine != null) " · ${routine.name}" else "",
                style = Fs.type.small, color = c.textDim, maxLines = 1,
            )
        }
        Toggle(r.enabled, { on ->
            if (on) app.ensureNotifications()
            repo.saveReminder(r.copy(enabled = on))
        })
    }
}

private fun AppState.editReminder(existing: Reminder?) {
    overlays.sheet { close -> ReminderEditor(existing, close) }
}

@Composable
private fun ReminderEditor(existing: Reminder?, close: () -> Unit) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val def = remember { repo.prefs.str("reminders.time").split(":") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var hour by remember { mutableIntStateOf(existing?.hour ?: def.getOrNull(0)?.toIntOrNull() ?: 18) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: def.getOrNull(1)?.toIntOrNull() ?: 0) }
    var days by remember { mutableStateOf(existing?.days ?: setOf(1, 2, 3, 4, 5)) }
    var routineId by remember { mutableStateOf(existing?.routineId) }
    val routine = routineId?.let { repo.routine(it) }

    SheetBody(if (existing == null) "Nuevo recordatorio" else "Editar recordatorio") {
        FsTextField(title, { title = it.take(60) }, placeholder = "Título (p. ej. Hora de entrenar)", leading = Ic.Bell)
        FsTextField(body, { body = it.take(160) }, placeholder = "Mensaje (opcional)", leading = Ic.Message, singleLine = false, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Hora: ${Fmt.time(hour, minute)}", icon = Ic.Clock, onClick = {
                app.overlays.pickTime("Hora del recordatorio", hour, minute) { h, m -> hour = h; minute = m }
            })
            Pill(routine?.name ?: "Sin rutina", icon = Ic.Routines, onClick = {
                val opts = listOf(Opt("", "Sin rutina", "Un aviso general")) +
                    repo.routines.map { Opt(it.id, it.name, Fmt.plural(it.items.size, "ejercicio", "ejercicios")) }
                app.overlays.pick("Rutina del aviso", opts, routineId ?: "") { routineId = it.ifBlank { null } }
            })
        }
        Label("Días")
        WeekdayPicker(days, { d ->
            val nd = if (d in days) days - d else days + d
            if (nd.isNotEmpty()) days = nd
        }, weekStartsMonday = repo.mondayFirst)
        Txt(Weekdays.label(days), style = Fs.type.small, color = c.textDim)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing != null) {
                FsButton("Eliminar", {
                    close()
                    repo.deleteReminder(existing.id)
                    existing.routineId?.let { rid -> repo.updateRoutine(rid) { r -> if (r.reminderId == existing.id) r.copy(reminderId = null) else r } }
                    app.toastUndo("Recordatorio eliminado")
                }, kind = BtnKind.Danger, icon = Ic.Trash, height = 50.dp)
            }
            FsButton("Guardar", {
                app.ensureNotifications()
                val saved = repo.saveReminder(
                    Reminder(
                        id = existing?.id ?: "", title = title, body = body, hour = hour, minute = minute,
                        days = days, enabled = true, routineId = routineId,
                    ),
                )
                if (routineId != null) repo.updateRoutine(routineId!!) { it.copy(reminderId = saved.id) }
                val old = existing?.routineId
                if (old != null && old != routineId) repo.updateRoutine(old) { r -> if (r.reminderId == saved.id) r.copy(reminderId = null) else r }
                close()
            }, Modifier.weight(1f), icon = Ic.Check, height = 50.dp)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Ícono de la app
// ═════════════════════════════════════════════════════════════════════════════

private const val MARK_SCALE = 0.66f

private fun markPath(): Path {
    val s = MARK_SCALE
    fun X(x: Float) = 54f + (x - IconMarkData.CX) * s
    fun Y(y: Float) = 54f + (y - IconMarkData.CY) * s
    return Path().apply {
        fillType = PathFillType.EvenOdd
        for (r in IconMarkData.solids + IconMarkData.holeRects) {
            addRoundRect(RoundRect(X(r[0]), Y(r[1]), X(r[0]) + r[2] * s, Y(r[1]) + r[3] * s, CornerRadius(r[4] * s)))
        }
        for (r in IconMarkData.solidCircles + IconMarkData.holeCircles) {
            addOval(Rect(X(r[0]) - r[2] * s, Y(r[1]) - r[2] * s, X(r[0]) + r[2] * s, Y(r[1]) + r[2] * s))
        }
    }
}

private val markCache by lazy { markPath() }

/** Dibuja el ícono de la app (fondo degradado + cabeza de FITBOT) con una forma de máscara. */
@Composable
internal fun IconMark(p: IconPalette, size: Dp, modifier: Modifier = Modifier, corner: Float = 0.28f) {
    val shape = if (corner >= 0.5f) CircleShape else RoundedCornerShape(percent = (corner * 100).toInt())
    Canvas(modifier.size(size).clip(shape)) {
        val k = this.size.minDimension / 108f
        drawRect(
            Brush.linearGradient(listOf(Color(p.a1), Color(p.a2)), Offset(18f * k, 6f * k), Offset(92f * k, 106f * k)),
        )
        drawRect(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.24f), Color.Transparent), Offset(28f * k, 20f * k), 86f * k),
        )
        scale(k, k, pivot = Offset.Zero) {
            rotate(-14f, pivot = Offset(54f, 54f)) {
                drawPath(markCache, Color(p.ink))
            }
        }
    }
}

/** Ajustes › Ícono de la app: elige el color del ícono del lanzador. */
@Composable
internal fun AppIconSection() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val c = Fs.colors
    val current = repo.prefs.str("app.icon")
    val pal = IconMarkData.palettes.firstOrNull { it.id == current } ?: IconMarkData.palettes.first()

    Column(verticalArrangement = Arrangement.spacedBy(Fs.ui.pad(14))) {
        Card {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    IconMark(pal, 52.dp, corner = 0.5f)
                    IconMark(pal, 104.dp, corner = 0.28f)
                    IconMark(pal, 52.dp, corner = 0.16f)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(pal.label, style = Fs.type.h2, maxLines = 1)
                    Txt("Así se ve en círculo, cuadrado suave y cuadrado", style = Fs.type.caption, color = c.textFaint, maxLines = 1)
                }
            }
        }
        Card {
            Txt("Elige un color", style = Fs.type.h3)
            Spacer(Modifier.height(12.dp))
            val rows = IconMarkData.palettes.chunked(5)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (row in rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (p in row) IconChoice(p, p.id == current) {
                            repo.setSetting("app.icon", p.id)
                            app.overlays.toast("Ícono «${p.label}». Tu pantalla de inicio puede tardar unos segundos en mostrarlo.", icon = Ic.Check)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Txt(
                "Al cambiarlo, algunos teléfonos cierran la app un instante. Si el ícono no cambia, espera unos segundos o reinicia el lanzador.",
                style = Fs.type.caption, color = c.textFaint,
            )
        }
    }
}

@Composable
private fun IconChoice(p: IconPalette, selected: Boolean, onClick: () -> Unit) {
    val c = Fs.colors
    val ring by animateFloatAsState(if (selected) 1f else 0f, Fs.motion.spring(0.7f, 600f), label = "iconRing")
    Column(Modifier.width(54.dp).tappable(onClick, pressScale = 0.92f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(percent = 30)).border(2.dp, c.accent.a(ring), RoundedCornerShape(percent = 30)))
            IconMark(p, 44.dp, corner = 0.28f)
            if (selected) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(c.bg).padding(2.dp).clip(CircleShape).background(c.accent).align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.Check, size = 11.dp, tint = c.onAccent, weight = 3f) }
            }
        }
        // El nombre puede ser más ancho que el cuadro del ícono (Mandarina, Monocromo): se deja salir hacia los lados.
        Box(Modifier.width(54.dp).wrapContentWidth(unbounded = true)) {
            Txt(p.label.substringBefore(' '), style = Fs.type.caption, color = if (selected) c.text else c.textDim, maxLines = 1, align = TextAlign.Center)
        }
    }
}
