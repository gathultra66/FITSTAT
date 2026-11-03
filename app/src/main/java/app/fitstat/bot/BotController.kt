package app.fitstat.bot

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fitstat.data.Repo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Lo que la pantalla de FITBOT necesita: el chat guardado, la respuesta que se está escribiendo,
 * el estado (pensando, escribiendo, usando una herramienta…) y las acciones: enviar, detener,
 * regenerar y deshacer los cambios que hizo.
 */
@Stable
class BotController(
    private val host: BotHost,
    val keys: ApiKeyStore,
    val catalog: ModelCatalog,
    val store: ChatStore,
    private val api: OpenRouter,
    private val scope: CoroutineScope,
    private val toolContext: CoroutineContext = Dispatchers.Main.immediate,
) {
    /** Una acción que FITBOT quiere hacer y espera el permiso del usuario. */
    class Pending(val title: String, val destructive: Boolean, val answer: CompletableDeferred<Boolean>)

    val repo: Repo get() = host.repo

    var busy by mutableStateOf(false)
        private set
    var phase by mutableStateOf(BotPhase.Idle)
        private set
    var liveText by mutableStateOf("")
        private set
    var liveReasoning by mutableStateOf("")
        private set
    val liveActions = mutableStateListOf<ActionRecord>()
    var pending by mutableStateOf<Pending?>(null)
        private set

    /** Sube cada vez que FITBOT termina de responder (anima a la mascota). */
    var doneTick by mutableIntStateOf(0)
        private set

    /** La app lo usa para avisar cuando FITBOT responde y el usuario está en otra pantalla. */
    var onReplyReady: ((String) -> Unit)? = null

    private var job: Job? = null

    val model: ModelInfo get() = catalog.info(repo.prefs.str("bot.model"))
    val hasKey: Boolean get() = keys.hasKey

    // ───────────────────────── Enviar

    fun send(text: String, attachments: List<Attachment> = emptyList()) {
        val t = text.trim()
        if (busy || (t.isEmpty() && attachments.isEmpty())) return
        store.add(ChatMessage(id = repo.newId("m_"), role = "user", text = t, attachments = attachments, ts = repo.now()))
        start()
    }

    /** Vuelve a pedir la última respuesta. Si había hecho cambios, primero los deshace. */
    fun regenerate() {
        if (busy) return
        val last = store.messages.lastOrNull() ?: return
        if (last.isUser) {
            start()
            return
        }
        if (last.checkpoint != 0L && !last.undone && repo.hasCheckpoint(last.checkpoint)) repo.restoreTo(last.checkpoint)
        store.remove(last.id)
        if (store.messages.lastOrNull()?.isUser == true) start()
    }

    fun stop() {
        pending?.answer?.complete(false)
        job?.cancel()
    }

    fun answerPending(allow: Boolean) {
        pending?.answer?.complete(allow)
    }

    fun clearChat() {
        if (busy) stop()
        store.clear()
    }

    private fun start() {
        if (!keys.hasKey) {
            store.add(
                ChatMessage(
                    id = repo.newId("m_"), role = "assistant", ts = repo.now(), status = "no_key",
                    text = "Para poder pensar necesito tu clave de OpenRouter. Pégala en **Ajustes › FITBOT y API** (crear la cuenta es gratis) y vuelve aquí.",
                ),
            )
            doneTick++
            return
        }
        busy = true
        phase = BotPhase.Connecting
        liveText = ""
        liveReasoning = ""
        liveActions.clear()
        job = scope.launch { runTurn(keys.key) }
    }

    private suspend fun runTurn(key: String) {
        val model = model
        val id = repo.newId("m_")
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val sink = object : TurnSink {
            override fun onPhase(p: BotPhase) {
                phase = p
            }

            override fun onText(delta: String) {
                text.append(delta)
                liveText = text.toString()
            }

            override fun onReasoning(delta: String) {
                reasoning.append(delta)
                liveReasoning = reasoning.toString()
            }

            override fun onAction(a: ActionRecord) {
                liveActions.add(a)
            }

            override suspend fun confirm(title: String, destructive: Boolean): Boolean {
                val p = Pending(title, destructive, CompletableDeferred())
                pending = p
                try {
                    return p.answer.await()
                } finally {
                    pending = null
                }
            }
        }
        val outcome = try {
            BotAgent(host, api, toolContext).run(key, model, store.messages.toList(), store.attachDir, sink)
        } catch (e: Throwable) {
            TurnOutcome(text.toString(), reasoning.toString(), liveActions.toList(), 0L, 0, null, e.message ?: "Error inesperado.", e is kotlinx.coroutines.CancellationException)
        }
        withContext(NonCancellable) {
            val empty = outcome.text.isBlank() && outcome.actions.isEmpty() && outcome.error == null
            if (!(outcome.cancelled && empty)) {
                store.add(
                    ChatMessage(
                        id = id, role = "assistant", text = outcome.text, reasoning = outcome.reasoning, actions = outcome.actions,
                        ts = repo.now(), error = outcome.error, model = model.id, checkpoint = outcome.checkpoint,
                        tokens = outcome.tokens, cost = outcome.cost, status = if (outcome.cancelled) "stopped" else "",
                    ),
                )
            }
            liveText = ""
            liveReasoning = ""
            liveActions.clear()
            pending = null
            phase = BotPhase.Idle
            busy = false
            doneTick++
            if (outcome.error == null && outcome.text.isNotBlank() && !outcome.cancelled) {
                repo.unlock("bot1")
                if (host.currentScreen() != "bot") onReplyReady?.invoke(outcome.text)
            }
        }
    }

    // ───────────────────────── Deshacer

    /** Deshace los cambios de un mensaje de FITBOT (y de los posteriores). Devuelve qué se deshizo. */
    fun undo(messageId: String): String? {
        val msgs = store.messages
        val idx = msgs.indexOfFirst { it.id == messageId }
        if (idx < 0) return null
        val m = msgs[idx]
        if (m.checkpoint == 0L || m.undone || !repo.hasCheckpoint(m.checkpoint)) return null
        if (!repo.restoreTo(m.checkpoint)) return null
        for (i in idx until msgs.size) {
            val x = msgs[i]
            if (x.checkpoint != 0L && !x.undone) store.replace(x.id) { it.copy(undone = true) }
        }
        return m.actions.filter { it.write && it.ok && !it.cancelled }.joinToString("; ") { it.summary }.ifEmpty { "los cambios de FITBOT" }
    }

    fun undoLast(): String? {
        val m = store.messages.lastOrNull { it.checkpoint != 0L && !it.undone && repo.hasCheckpoint(it.checkpoint) } ?: return null
        return undo(m.id)
    }

    fun canUndo(m: ChatMessage): Boolean = repo.undoDepth >= 0 && m.checkpoint != 0L && !m.undone && repo.hasCheckpoint(m.checkpoint)
}
