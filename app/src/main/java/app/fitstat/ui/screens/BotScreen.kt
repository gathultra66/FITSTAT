package app.fitstat.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitstat.bot.Attachment
import app.fitstat.bot.Attachments
import app.fitstat.bot.BotController
import app.fitstat.bot.BotPhase
import app.fitstat.bot.ChatMessage
import app.fitstat.bot.ModelInfo
import app.fitstat.bot.Recorder
import app.fitstat.data.Fmt
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.LocalApp
import app.fitstat.ui.app.LocalRepo
import app.fitstat.ui.app.Route
import app.fitstat.ui.design.BotMood
import app.fitstat.ui.design.BtnKind
import app.fitstat.ui.design.FitBot
import app.fitstat.ui.design.FsButton
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.IconBadge
import app.fitstat.ui.design.Icon
import app.fitstat.ui.design.Markdown
import app.fitstat.ui.design.LocalNavSpace
import app.fitstat.ui.design.MascotController
import app.fitstat.ui.design.enter
import app.fitstat.ui.design.RoundButton
import app.fitstat.ui.design.Txt
import app.fitstat.ui.design.rememberMascotController
import app.fitstat.ui.design.statusBarTop
import app.fitstat.ui.design.tappable
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.a
import app.fitstat.ui.theme.mix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_ATTACH = 4

private val suggestions = listOf(
    Ic.TrendUp to "¿Cómo voy esta semana?",
    Ic.Routines to "Arma una rutina de pierna de 45 minutos",
    Ic.Palette to "Cámbiale el color a la app a menta",
    Ic.Trophy to "¿Cuáles son mis mejores récords?",
    Ic.Bell to "Recuérdame entrenar a las 7:00 entre semana",
)

// ───────────────────────── Pantalla

@Composable
fun BotScreen() {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val bot = app.bot
    val chat = app.chat
    val rec = app.recorder
    val c = Fs.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val controller = rememberMascotController()
    val density = LocalDensity.current
    val model = bot.model
    val openedAt = remember { repo.now() }
    val showReasoning = repo.prefs.bool("bot.show_reasoning")
    val attachDir = remember(chat) { chat.attachDir }

    var draft by rememberSaveable { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Attachment>() }
    var importing by remember { mutableIntStateOf(0) }

    // El catálogo de modelos sirve para saber qué admite el elegido (imágenes, audio, archivos…).
    LaunchedEffect(Unit) {
        if (app.models.models.isEmpty()) app.models.loadCached()
        if (app.models.isStale) app.models.refresh(app.keys.key.ifBlank { null })
    }

    // ── Ánimo de la mascota
    var userTyping by remember { mutableStateOf(false) }
    LaunchedEffect(draft) {
        if (draft.isEmpty()) {
            userTyping = false
        } else {
            userTyping = true
            delay(1500)
            userTyping = false
        }
    }
    var flash by remember { mutableStateOf<BotMood?>(null) }
    var seenTick by remember { mutableIntStateOf(bot.doneTick) }
    LaunchedEffect(bot.doneTick) {
        if (bot.doneTick != seenTick) {
            seenTick = bot.doneTick
            val last = chat.messages.lastOrNull()
            flash = when {
                last != null && !last.isUser && last.error != null -> BotMood.Error
                last?.status == "no_key" -> BotMood.Sleepy
                else -> BotMood.Happy
            }
            delay(2200)
            flash = null
        }
    }
    val flashed = flash
    val mood = when {
        bot.busy -> when (bot.phase) {
            BotPhase.Writing -> BotMood.Typing
            BotPhase.Confirm -> BotMood.Talking
            else -> BotMood.Thinking
        }
        rec.recording -> BotMood.Talking
        flashed != null -> flashed
        !bot.hasKey -> BotMood.Sleepy
        else -> BotMood.Idle
    }
    val status = when {
        bot.busy -> phaseLabel(bot.phase, model)
        rec.recording -> "Te escucho…"
        userTyping -> "Te leo…"
        !bot.hasKey -> "Dormido · falta tu clave"
        else -> "En línea"
    }

    // ── Grabación de voz
    fun finishRecording() {
        val id = repo.newId("a_")
        scope.launch {
            val a = rec.finish(id)
            if (a != null) attachments.add(a) else app.overlays.toast("No se grabó nada.", icon = Ic.MicOff)
        }
    }

    fun startRecording() {
        val ok = rec.start(scope) {
            app.overlays.toast("Llegaste al máximo de 2 minutos.", icon = Ic.Mic)
            finishRecording()
        }
        if (!ok) app.overlays.toast("No pude usar el micrófono.", icon = Ic.MicOff)
    }

    DisposableEffect(Unit) {
        onDispose { if (rec.recording) rec.cancel() }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else app.overlays.toast("Sin permiso de micrófono no puedo grabar.", icon = Ic.MicOff)
    }

    fun noSupport(flag: String, what: String) {
        app.overlays.toast("${model.name} no admite $what.", "Elegir modelo", Ic.Cpu) { app.openModelPicker(setOf(flag)) }
    }

    // ── Adjuntos
    fun importUri(uri: Uri?, kind: String) {
        if (uri == null) return
        if (attachments.size >= MAX_ATTACH) {
            app.overlays.toast("Máximo $MAX_ATTACH adjuntos por mensaje.", icon = Ic.Clip)
            return
        }
        val id = repo.newId("a_")
        importing++
        scope.launch {
            val r = withContext(Dispatchers.IO) { Attachments.import(ctx, attachDir, uri, kind, id) }
            importing--
            when (r) {
                is Attachments.Result.Ok -> attachments.add(r.attachment)
                is Attachments.Result.Fail -> app.overlays.toast(r.message, icon = Ic.Alert)
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { importUri(it, "image") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri(it, "file") }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri(it, "audio") }

    fun removeAttachment(a: Attachment) {
        attachments.remove(a)
        scope.launch(Dispatchers.IO) { runCatching { File(attachDir, a.path).delete() } }
    }

    fun openAttach() {
        app.overlays.sheet { close ->
            SheetBody("Adjuntar", subtitle = "Solo lo que ${model.name} puede entender") {
                AttachOption(
                    Ic.Image, "Imagen", enabled = model.images,
                    hint = if (model.images) "Una foto de tu rutina, de una máquina, de tu comida…" else "${model.name} no admite imágenes",
                    onClick = { close(); pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onChooseModel = { close(); app.openModelPicker(setOf("images")) },
                )
                AttachOption(
                    Ic.File, "Archivo", enabled = true,
                    hint = if (model.files) "PDF, texto, CSV o JSON" else "Texto, CSV o JSON (se lee como texto; para PDF necesitas otro modelo)",
                    onClick = {
                        close()
                        pickFile.launch(
                            if (model.files) arrayOf("application/pdf", "text/*", "application/json", "application/xml")
                            else arrayOf("text/*", "application/json", "application/xml"),
                        )
                    },
                    onChooseModel = null,
                )
                AttachOption(
                    Ic.Mic, "Audio", enabled = model.audio,
                    hint = if (model.audio) "Un archivo WAV o MP3 (o usa el micrófono del chat)" else "${model.name} no admite audio",
                    onClick = { close(); pickAudio.launch(arrayOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3")) },
                    onChooseModel = { close(); app.openModelPicker(setOf("audio")) },
                )
            }
        }
    }

    fun micTap() {
        if (!model.audio) {
            noSupport("audio", "audio")
            return
        }
        if (attachments.size >= MAX_ATTACH) {
            app.overlays.toast("Máximo $MAX_ATTACH adjuntos por mensaje.", icon = Ic.Clip)
            return
        }
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun send(text: String) {
        if (rec.recording) return
        val t = text.trim()
        if (t.isEmpty() && attachments.isEmpty()) return
        bot.send(t, attachments.toList())
        draft = ""
        attachments.clear()
    }

    fun openMenu() {
        app.overlays.sheet { close ->
            SheetBody("FITBOT") {
                MenuRow(Ic.Cpu, "Elegir modelo", model.name) { close(); app.openModelPicker() }
                MenuRow(Ic.Settings, "Ajustes de FITBOT", "Clave de OpenRouter, tono, razonamiento…") { close(); app.nav.push(Route.SettingsPage("fitbot")) }
                if (chat.messages.any { bot.canUndo(it) }) {
                    MenuRow(Ic.Undo, "Deshacer último cambio de FITBOT", "Vuelve a como estaba tu app antes") {
                        close()
                        val what = bot.undoLast()
                        app.overlays.toast(if (what != null) "Deshecho: $what" else "No hay cambios que deshacer.", icon = Ic.Undo)
                    }
                }
                if (chat.messages.isNotEmpty()) {
                    MenuRow(Ic.Trash, "Empezar un chat nuevo", "Borra esta conversación", danger = true) {
                        close()
                        app.overlays.confirm(
                            "¿Empezar de nuevo?",
                            "Se borra la conversación con FITBOT. Los cambios que hizo en tu app se quedan.",
                            "Borrar chat", danger = true,
                        ) { bot.clearChat() }
                    }
                }
            }
        }
    }

    // ── Desplazamiento automático
    val msgs = chat.messages
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    LaunchedEffect(msgs.size) {
        val last = msgs.lastOrNull()
        if (last != null && (last.isUser || atBottom)) listState.animateScrollToItem(0)
    }
    LaunchedEffect(bot.busy, bot.pending) {
        if (bot.busy || bot.pending != null) listState.animateScrollToItem(0)
    }

    val top = statusBarTop()
    var headerPx by remember { mutableIntStateOf(with(density) { (top + 72.dp).roundToPx() }) }
    val headerDp = with(density) { headerPx.toDp() }
    val navSpace = LocalNavSpace.current.let { if (it > 0.dp) it + 4.dp else 8.dp }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = msgs.toList().asReversed()
            if (list.isEmpty() && !bot.busy) {
                EmptyChat(
                    mood = mood, controller = controller, name = repo.profile.name, hasKey = bot.hasKey,
                    topPad = headerDp, onSuggestion = { send(it) },
                    onSetup = { app.nav.push(Route.SettingsPage("fitbot")) },
                )
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerDp + 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (bot.busy) {
                        item(key = "live") {
                            LiveRow(bot, mood, showReasoning, model, Modifier.popIn(true))
                        }
                    }
                    items(list, key = { it.id }) { msg ->
                        if (msg.isUser) {
                            Row(Modifier.fillMaxWidth().popIn(msg.ts > openedAt), horizontalArrangement = Arrangement.End) {
                                UserBubble(msg, attachDir)
                            }
                        } else {
                            AssistantMessage(
                                msg = msg, last = msg.id == msgs.lastOrNull()?.id && !bot.busy, showReasoning = showReasoning,
                                bot = bot, zone = repo.zone,
                                onSettings = { app.nav.push(Route.SettingsPage("fitbot")) },
                                onCopy = { copyText(ctx, msg.text); app.overlays.toast("Copiado", icon = Ic.Copy) },
                                onUndo = {
                                    val what = bot.undo(msg.id)
                                    app.overlays.toast(if (what != null) "Deshecho: $what" else "No pude deshacerlo.", icon = Ic.Undo)
                                },
                            )
                        }
                    }
                }
            }
            BotHeader(
                mood = mood, controller = controller, lookDown = userTyping, status = status,
                dotColor = when {
                    bot.busy -> c.accent
                    !bot.hasKey -> c.textFaint
                    else -> c.good
                },
                pulsing = bot.busy || rec.recording, model = model,
                onModel = { app.openModelPicker() }, onMenu = { openMenu() },
                modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height },
            )
            androidx.compose.animation.AnimatedVisibility(
                !atBottom && list.isNotEmpty(),
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp),
                enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
            ) {
                RoundButton(Ic.ArrowDown, { scope.launch { listState.animateScrollToItem(0) } }, size = 42.dp, bg = c.surface3)
            }
        }
        InputBar(
            draft = draft, onDraft = { draft = it },
            attachments = attachments, attachDir = attachDir, onRemove = { removeAttachment(it) }, importing = importing > 0,
            busy = bot.busy, model = model, recorder = rec,
            onAttach = { openAttach() }, onSend = { send(draft) }, onStop = { bot.stop() }, onMic = { micTap() },
            onCancelRec = { rec.cancel() }, onFinishRec = { finishRecording() },
            modifier = Modifier.padding(bottom = navSpace),
        )
    }
}

private fun phaseLabel(p: BotPhase, model: ModelInfo): String = when (p) {
    BotPhase.Connecting -> "Conectando…"
    BotPhase.Thinking -> if (model.reasoning) "Razonando…" else "Pensando…"
    BotPhase.Writing -> "Escribiendo…"
    BotPhase.Tool -> "Trabajando en tu app…"
    BotPhase.Confirm -> "Esperando tu permiso"
    BotPhase.Idle -> "En línea"
}

private fun copyText(ctx: Context, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("FITBOT", text))
    }
}

/** Entrada suave (sube y aparece) para mensajes nuevos. */
@Composable
private fun Modifier.popIn(fresh: Boolean): Modifier {
    val m = Fs.motion
    val p = remember { Animatable(if (fresh && m.enabled && m.entrance) 0f else 1f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, m.spring(0.8f, 380f)) }
    val px = with(LocalDensity.current) { 22.dp.toPx() }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * px
        val s = 0.96f + 0.04f * v
        scaleX = s
        scaleY = s
    }
}

// ───────────────────────── Encabezado

@Composable
private fun BotHeader(
    mood: BotMood,
    controller: MascotController,
    lookDown: Boolean,
    status: String,
    dotColor: Color,
    pulsing: Boolean,
    model: ModelInfo,
    onModel: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fs.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.bg.a(0.98f), c.bg.a(0.93f), Color.Transparent)))
            .padding(top = statusBarTop() + 6.dp, start = 16.dp, end = 16.dp, bottom = 10.dp),
    ) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(56.dp).clip(shape).background(c.accent.a(0.12f)).border(1.dp, c.accent.a(0.3f), shape),
                contentAlignment = Alignment.Center,
            ) {
                FitBot(
                    size = 50.dp, mood = mood, controller = controller,
                    lookX = if (lookDown) -0.3f else 0f, lookY = if (lookDown) 0.9f else 0f,
                )
            }
            Column(Modifier.weight(1f)) {
                Txt("FITBOT", style = Fs.type.h1, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StatusDot(dotColor, pulsing)
                    Txt(status, style = Fs.type.small, color = c.textDim, maxLines = 1)
                }
            }
            // El selector de modelo ahora es un recuadro más, junto al de las opciones (el nombre del modelo sigue en ese menú).
            RoundButton(
                Ic.Cpu, onModel, Modifier.semantics { contentDescription = "Modelo: ${model.name}" }.testTag("bot_model"),
                tint = c.accentText,
            )
            RoundButton(Ic.MoreV, onMenu, Modifier.testTag("bot_menu"))
        }
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    val m = Fs.motion
    val tr = rememberInfiniteTransition(label = "dot")
    val k by if (pulsing && m.enabled) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "k") else remember { mutableStateOf(0f) }
    Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((7 + 5 * k).dp).graphicsLayer { alpha = 0.35f * (1f - k) }.clip(Fs.shapes.round).background(color))
        Box(Modifier.size(7.dp).clip(Fs.shapes.round).background(color))
    }
}

// ───────────────────────── Mensajes

@Composable
private fun AssistantMessage(
    msg: ChatMessage,
    last: Boolean,
    showReasoning: Boolean,
    bot: BotController,
    zone: java.time.ZoneId,
    onSettings: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit,
) {
    val c = Fs.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BotAvatar(mood = if (msg.error != null) BotMood.Error else BotMood.Idle, animated = true)
        Column(Modifier.weight(1f, fill = false).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showReasoning && msg.reasoning.isNotBlank()) ReasoningBlock(msg.reasoning, live = false)
            if (msg.text.isNotBlank() || msg.actions.isNotEmpty() || msg.error != null) {
                AssistantBubble(error = msg.error != null && msg.text.isBlank()) {
                    if (msg.text.isNotBlank()) Markdown(msg.text)
                    if (msg.error != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Ic.Alert, Modifier.padding(top = 2.dp), size = 18.dp, tint = c.bad)
                            Txt(msg.error, style = Fs.type.small, color = c.text)
                        }
                        if (last) FsButton("Reintentar", { bot.regenerate() }, kind = BtnKind.Secondary, icon = Ic.Refresh, height = 42.dp)
                    }
                    ActionChips(msg.actions)
                    if (msg.status == "no_key") FsButton("Abrir ajustes de FITBOT", onSettings, kind = BtnKind.Secondary, icon = Ic.Key, height = 42.dp)
                }
            }
            MessageFooter(msg, last, bot, zone, onCopy, onUndo)
        }
    }
}

@Composable
private fun MessageFooter(msg: ChatMessage, last: Boolean, bot: BotController, zone: java.time.ZoneId, onCopy: () -> Unit, onUndo: () -> Unit) {
    val c = Fs.colors
    val meta = buildList {
        if (msg.ts > 0) add(Fmt.hourMinute(msg.ts, zone))
        costLabel(msg)?.let { add(it) }
        if (msg.status == "stopped") add("Detenido")
    }.joinToString(" · ")
    val canUndo = bot.canUndo(msg)
    Row(Modifier.padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (msg.text.isNotBlank()) FooterIcon(Ic.Copy, onCopy)
        if (last && msg.status != "no_key" && msg.error == null) FooterIcon(Ic.Refresh) { bot.regenerate() }
        if (canUndo) {
            Row(
                Modifier.padding(start = 4.dp).tappable(onUndo, pressScale = 0.95f).clip(Fs.shapes.round).background(c.accent.a(0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Ic.Undo, size = 14.dp, tint = c.accentText)
                Txt("Deshacer cambios", style = Fs.type.caption, color = c.accentText, maxLines = 1)
            }
        } else if (msg.undone) {
            Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Ic.Undo, size = 13.dp, tint = c.textFaint)
                Txt("Cambios deshechos", style = Fs.type.caption, color = c.textFaint, maxLines = 1)
            }
        }
        if (meta.isNotEmpty()) Txt(meta, Modifier.padding(start = 8.dp), Fs.type.caption, c.textFaint, maxLines = 1)
    }
}

@Composable
private fun FooterIcon(ic: Ic, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).tappable(onClick, pressScale = 0.88f).clip(Fs.shapes.round), contentAlignment = Alignment.Center) {
        Icon(ic, size = 16.dp, tint = Fs.colors.textFaint)
    }
}

@Composable
private fun LiveRow(bot: BotController, mood: BotMood, showReasoning: Boolean, model: ModelInfo, modifier: Modifier) {
    val c = Fs.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BotAvatar(mood = mood, animated = true)
        Column(Modifier.weight(1f, fill = false).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showReasoning && bot.liveReasoning.isNotBlank()) ReasoningBlock(bot.liveReasoning, live = bot.liveText.isBlank())
            AssistantBubble {
                if (bot.liveText.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TypingDots()
                        Txt(phaseLabel(bot.phase, model), style = Fs.type.small, color = c.textDim, maxLines = 1)
                    }
                } else {
                    Markdown(bot.liveText)
                }
                ActionChips(bot.liveActions.toList())
            }
            val p = bot.pending
            if (p != null) ConfirmCard(p) { bot.answerPending(it) }
        }
    }
}

// ───────────────────────── Chat vacío

@Composable
private fun EmptyChat(
    mood: BotMood,
    controller: MascotController,
    name: String,
    hasKey: Boolean,
    topPad: androidx.compose.ui.unit.Dp,
    onSuggestion: (String) -> Unit,
    onSetup: () -> Unit,
) {
    val c = Fs.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topPad, start = 20.dp, end = 20.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FitBot(size = 132.dp, mood = mood, controller = controller)
        Txt(
            if (name.isNotBlank()) "Hola, ${name.trim().substringBefore(' ')}" else "Hola, soy FITBOT",
            style = Fs.type.h1, align = TextAlign.Center,
        )
        Txt(
            "Conozco tus rutinas, tus récords y tus ajustes, y puedo cambiarlos por ti. Pídeme lo que quieras:",
            style = Fs.type.small, color = c.textDim, align = TextAlign.Center,
        )
        if (!hasKey) {
            Row(
                Modifier.padding(top = 6.dp).fillMaxWidth().clip(Fs.shapes.inner).background(c.warn.a(0.12f)).border(1.dp, c.warn.a(0.4f), Fs.shapes.inner)
                    .tappable(onSetup, pressScale = 0.98f).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Ic.Key, size = 22.dp, tint = c.warn)
                Column(Modifier.weight(1f)) {
                    Txt("Falta tu clave de OpenRouter", style = Fs.type.bodyStrong, maxLines = 1)
                    Txt("Pégala en Ajustes › FITBOT y API para despertarme.", style = Fs.type.small, color = c.textDim)
                }
                Icon(Ic.ChevronRight, size = 18.dp, tint = c.textDim)
            }
        }
        Column(Modifier.padding(top = 8.dp).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEachIndexed { i, (ic, text) ->
                Row(
                    Modifier.fillMaxWidth().enter(i + 2).tappable({ onSuggestion(text) }, pressScale = 0.98f).clip(Fs.shapes.inner)
                        .background(c.surface).border(1.dp, c.outline.a(0.6f), Fs.shapes.inner).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(ic, size = 34.dp)
                    Txt(text, Modifier.weight(1f), Fs.type.body, c.text, maxLines = 2)
                    Icon(Ic.ArrowUpRight, size = 16.dp, tint = c.textFaint)
                }
            }
        }
    }
}

// ───────────────────────── Hojas

@Composable
private fun AttachOption(ic: Ic, title: String, hint: String, enabled: Boolean, onClick: () -> Unit, onChooseModel: (() -> Unit)?) {
    val c = Fs.colors
    Row(
        Modifier.fillMaxWidth().tappable(if (enabled) onClick else onChooseModel, pressScale = 0.985f).clip(Fs.shapes.inner)
            .background(c.surface2).border(1.dp, c.outline.a(0.6f), Fs.shapes.inner).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(ic, size = 42.dp, tint = if (enabled) c.accentText else c.textFaint, bg = if (enabled) c.accentSoft else c.surface3)
        Column(Modifier.weight(1f)) {
            Txt(title, style = Fs.type.bodyStrong, color = if (enabled) c.text else c.textDim, maxLines = 1)
            Txt(hint, style = Fs.type.small, color = c.textDim)
        }
        if (!enabled && onChooseModel != null) {
            Txt("Elegir modelo", style = Fs.type.smallStrong, color = c.accentText, maxLines = 1)
        } else if (enabled) {
            Icon(Ic.ChevronRight, size = 18.dp, tint = c.textFaint)
        }
    }
}

@Composable
private fun MenuRow(ic: Ic, title: String, hint: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Fs.colors
    val tint = if (danger) c.bad else c.accentText
    Row(
        Modifier.fillMaxWidth().tappable(onClick, pressScale = 0.985f).clip(Fs.shapes.inner)
            .background(c.surface2).border(1.dp, c.outline.a(0.6f), Fs.shapes.inner).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(ic, size = 40.dp, tint = tint, bg = tint.a(0.14f))
        Column(Modifier.weight(1f)) {
            Txt(title, style = Fs.type.bodyStrong, color = if (danger) c.bad else c.text, maxLines = 1)
            if (hint.isNotEmpty()) Txt(hint, style = Fs.type.small, color = c.textDim, maxLines = 1)
        }
    }
}

// ───────────────────────── Barra de entrada

@Composable
private fun InputBar(
    draft: String,
    onDraft: (String) -> Unit,
    attachments: List<Attachment>,
    attachDir: File,
    onRemove: (Attachment) -> Unit,
    importing: Boolean,
    busy: Boolean,
    model: ModelInfo,
    recorder: Recorder,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onCancelRec: () -> Unit,
    onFinishRec: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fs.colors
    val m = Fs.motion
    val recording = recorder.recording
    val canSend = draft.isNotBlank() || attachments.isNotEmpty()
    val pill = RoundedCornerShape(28.dp)
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(attachments.isNotEmpty() || importing, enter = fadeIn(m.tween(180)) + scaleIn(m.tween(180), initialScale = 0.96f), exit = fadeOut(m.tween(120))) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (attachments.isNotEmpty()) AttachmentViews(attachments, attachDir, onRemove = onRemove)
                if (importing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypingDots()
                        Txt("Preparando adjunto…", style = Fs.type.small, color = c.textDim)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(pill).background(c.surface2).border(1.dp, c.outline.a(0.8f), pill).padding(4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Crossfade(recording, Modifier.weight(1f), animationSpec = m.tween(160), label = "inputMode") { rec ->
                if (rec) {
                    RecordingStrip(recorder, onCancelRec)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.size(48.dp).tappable(onAttach, pressScale = 0.9f).clip(Fs.shapes.round), contentAlignment = Alignment.Center) {
                            Icon(Ic.Plus, size = 24.dp, tint = c.textDim)
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraft,
                            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                            textStyle = Fs.type.body.copy(color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (draft.isEmpty()) Txt(if (busy) "FITBOT está respondiendo…" else "Escríbele a FITBOT…", style = Fs.type.body, color = c.textFaint, maxLines = 1)
                                    inner()
                                }
                            },
                        )
                    }
                }
            }
            val mode = when {
                recording -> "done"
                busy -> "stop"
                canSend -> "send"
                else -> "mic"
            }
            AnimatedContent(
                mode, Modifier.size(48.dp),
                transitionSpec = { (scaleIn(m.tween(200), initialScale = 0.6f) + fadeIn(m.tween(160))) togetherWith (scaleOut(m.tween(140), targetScale = 0.6f) + fadeOut(m.tween(120))) },
                contentAlignment = Alignment.Center, label = "actionBtn",
            ) { md ->
                when (md) {
                    "send" -> ActionCircle(Ic.Send, onSend, accent = true)
                    "done" -> ActionCircle(Ic.Check, onFinishRec, accent = true)
                    "stop" -> ActionCircle(Ic.Stop, onStop, accent = false, tint = c.bad)
                    else -> ActionCircle(Ic.Mic, onMic, accent = false, dim = !model.audio)
                }
            }
        }
    }
}

@Composable
private fun ActionCircle(ic: Ic, onClick: () -> Unit, accent: Boolean, tint: Color = Fs.colors.textDim, dim: Boolean = false) {
    val c = Fs.colors
    val brush = if (accent) Brush.verticalGradient(listOf(c.accent.mix(Color.White, 0.12f), c.accent2)) else SolidColor(c.surface3)
    Box(
        Modifier.size(48.dp).tappable(onClick, pressScale = 0.9f).clip(Fs.shapes.round).background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ic, size = 22.dp, tint = if (accent) c.onAccent else if (dim) c.textFaint else tint)
    }
}

@Composable
private fun RecordingStrip(recorder: Recorder, onCancel: () -> Unit) {
    val c = Fs.colors
    val m = Fs.motion
    val tr = rememberInfiniteTransition(label = "rec")
    val k by if (m.enabled) tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "k") else remember { mutableStateOf(1f) }
    Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(48.dp).tappable(onCancel, pressScale = 0.9f).clip(Fs.shapes.round), contentAlignment = Alignment.Center) {
            Icon(Ic.Trash, size = 22.dp, tint = c.bad)
        }
        Box(Modifier.size(10.dp).graphicsLayer { alpha = k }.clip(Fs.shapes.round).background(c.bad))
        Txt(Fmt.duration(recorder.seconds), style = Fs.type.bodyStrong, color = c.text, maxLines = 1)
        Waveform(recorder.levels, Modifier.weight(1f).height(32.dp), c.accent)
    }
}

@Composable
private fun Waveform(levels: List<Float>, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val barW = 3.dp.toPx()
        val gap = 3.dp.toPx()
        val n = (size.width / (barW + gap)).toInt().coerceAtLeast(1)
        val shown = levels.takeLast(n)
        val startX = size.width - shown.size * (barW + gap)
        val minH = 4.dp.toPx()
        shown.forEachIndexed { i, l ->
            val h = (minH + l.coerceIn(0f, 1f) * (size.height - minH)).coerceAtMost(size.height)
            drawRoundRect(color, Offset(startX + i * (barW + gap), (size.height - h) / 2f), Size(barW, h), CornerRadius(barW / 2f))
        }
    }
}
