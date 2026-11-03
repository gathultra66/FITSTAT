package app.fitstat.bot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.Base64
import kotlin.coroutines.CoroutineContext

/** En qué está FITBOT en este momento (para animar a la mascota y mostrar el estado). */
enum class BotPhase { Idle, Connecting, Thinking, Writing, Tool, Confirm }

/** Quien escucha el avance de un turno (la interfaz). */
interface TurnSink {
    fun onPhase(p: BotPhase) {}
    fun onText(delta: String) {}
    fun onReasoning(delta: String) {}
    fun onAction(a: ActionRecord) {}

    /** Pide permiso al usuario antes de una acción. Devuelve true si la permite. */
    suspend fun confirm(title: String, destructive: Boolean): Boolean = true
}

class TurnOutcome(
    val text: String,
    val reasoning: String,
    val actions: List<ActionRecord>,
    val checkpoint: Long,
    val tokens: Int,
    val cost: Double?,
    /** Mensaje de error para el usuario (null si todo salió bien). */
    val error: String?,
    val cancelled: Boolean,
)

/**
 * Un turno de conversación de FITBOT: manda el chat a OpenRouter, ejecuta las herramientas que pida el
 * modelo (leer y cambiar la app) y repite hasta tener la respuesta final.
 */
class BotAgent(
    private val host: BotHost,
    private val api: OpenRouter,
    private val toolContext: CoroutineContext = Dispatchers.Main.immediate,
    private val tools: List<BotTool> = allTools,
) {
    private val byName = tools.associateBy { it.name }
    private val lenient = Json { isLenient = true; ignoreUnknownKeys = true }

    suspend fun run(apiKey: String, model: ModelInfo, history: List<ChatMessage>, attachDir: File, sink: TurnSink): TurnOutcome {
        val repo = host.repo
        val prefs = repo.prefs
        val ctx = ToolCtx(host) { repo.checkpoint("Cambios de FITBOT") }
        val confirmMode = prefs.str("bot.confirm")
        val stream = prefs.bool("bot.stream")
        var useTools = model.tools
        var useReasoning = model.reasoning

        val messages = ArrayList<JsonObject>()
        messages += msg("system", Prompt.system(host, model))
        messages += convertHistory(history, model, attachDir, prefs.int("bot.history"))

        val actions = ArrayList<ActionRecord>()
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var tokens = 0
        var cost: Double? = null
        var error: String? = null
        var cancelled = false
        var noticed = false

        try {
            for (step in 0 until MAX_STEPS) {
                val lastStep = step == MAX_STEPS - 1
                var stepStarted = false
                var delivered = false
                val listener = object : StreamListener {
                    override fun onText(delta: String) {
                        delivered = true
                        if (!stepStarted) {
                            stepStarted = true
                            if (text.isNotEmpty() && !text.endsWith("\n\n")) {
                                text.append("\n\n")
                                sink.onText("\n\n")
                            }
                        }
                        sink.onPhase(BotPhase.Writing)
                        text.append(delta)
                        sink.onText(delta)
                    }

                    override fun onReasoning(delta: String) {
                        delivered = true
                        sink.onPhase(BotPhase.Thinking)
                        reasoning.append(delta)
                        sink.onReasoning(delta)
                    }

                    override fun onToolStarted(name: String) {
                        delivered = true
                        sink.onPhase(BotPhase.Tool)
                    }
                }

                sink.onPhase(BotPhase.Connecting)
                var attempt = 0
                var result: Completion? = null
                while (result == null) {
                    delivered = false
                    val request = body(model, messages, withTools = useTools && !lastStep, withReasoning = useReasoning)
                    try {
                        result = api.chat(apiKey, request, stream, listener)
                    } catch (e: OpenRouterException) {
                        val badRequest = e.code in 400..422
                        when {
                            badRequest && e.mentionsTools && useTools && !delivered -> {
                                useTools = false
                                if (!noticed) {
                                    noticed = true
                                    val n = ActionRecord("notice", "Este modelo no admite herramientas: solo puedo conversar.", ok = false)
                                    actions += n
                                    sink.onAction(n)
                                }
                            }
                            badRequest && e.mentionsReasoning && useReasoning && !delivered -> useReasoning = false
                            (e.code == 429 || e.code >= 500 || e.code == 0) && attempt < 2 && !delivered -> {
                                attempt++
                                delay(1200L * attempt)
                            }
                            else -> throw e
                        }
                    }
                }
                val completion: Completion = result

                completion.usage?.let { u ->
                    tokens += u.total
                    if (u.cost != null) cost = (cost ?: 0.0) + u.cost
                }
                if (completion.toolCalls.isEmpty()) break
                if (lastStep) break

                messages += assistantToolCalls(completion)
                for (call in completion.toolCalls) {
                    sink.onPhase(BotPhase.Tool)
                    val result = execute(call, ctx, confirmMode, sink, actions)
                    messages += buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", call.id)
                        put("name", call.name)
                        put("content", result)
                    }
                }
            }
        } catch (e: CancellationException) {
            cancelled = true
        } catch (e: OpenRouterException) {
            error = e.message ?: "Error de OpenRouter."
        } catch (e: Throwable) {
            error = "Algo salió mal: ${e.message ?: e.javaClass.simpleName}"
        }

        var finalText = text.toString().trim()
        if (finalText.isEmpty() && error == null && !cancelled) {
            val done = actions.filter { it.ok && !it.cancelled && it.tool != "notice" }
            if (done.isNotEmpty()) finalText = "Listo:\n" + done.takeLast(8).joinToString("\n") { "• ${it.summary}" }
            else error = "El modelo no devolvió ninguna respuesta. Inténtalo de nuevo o elige otro modelo."
        }

        var checkpoint = ctx.checkpoint
        if (checkpoint != 0L && repo.dropCheckpointIfUnchanged(checkpoint)) checkpoint = 0L
        return TurnOutcome(finalText, reasoning.toString(), actions, checkpoint, tokens, cost, error, cancelled)
    }

    // ───────────────────────── Herramientas

    private suspend fun execute(call: ToolCall, ctx: ToolCtx, mode: String, sink: TurnSink, actions: MutableList<ActionRecord>): String {
        val name = call.name.trim().substringAfterLast('.')
        val tool = byName[name]
        if (tool == null) {
            val rec = ActionRecord(name, "Herramienta desconocida: $name", ok = false)
            actions += rec
            sink.onAction(rec)
            return fail("La herramienta '$name' no existe. Herramientas disponibles: ${tools.joinToString { it.name }}.").json.toString()
        }
        val args = parseArgs(call.arguments)
        if (args == null) {
            val rec = ActionRecord(tool.name, "Argumentos no válidos en ${tool.name}", ok = false, write = tool.write)
            actions += rec
            sink.onAction(rec)
            return fail("Los argumentos no son un JSON válido. Vuelve a llamar la herramienta con un objeto JSON.").json.toString()
        }
        val destructive = tool.destructive(args)
        val needsConfirm = tool.write && (tool.alwaysConfirm || mode == "always" || (mode == "destructive" && destructive))
        if (needsConfirm) {
            sink.onPhase(BotPhase.Confirm)
            val title = withContext(toolContext) { tool.plan(args, ctx) }.ifBlank { tool.name }
            val allowed = sink.confirm(title, destructive)
            if (!allowed) {
                val rec = ActionRecord(tool.name, "Cancelado: $title", ok = false, write = true, cancelled = true)
                actions += rec
                sink.onAction(rec)
                return fail("El usuario canceló esta acción. No la repitas; pregunta qué prefiere.").json.toString()
            }
            sink.onPhase(BotPhase.Tool)
        }
        val outcome: ToolOutcome = withContext(toolContext) {
            try {
                if (tool.write) {
                    ctx.beforeWrite()
                    host.repo.withoutUndo { tool.run(args, ctx) }
                } else {
                    tool.run(args, ctx)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail("Error al ejecutar ${tool.name}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        val rec = ActionRecord(tool.name, outcome.summary, ok = outcome.ok, write = tool.write)
        actions += rec
        sink.onAction(rec)
        val s = outcome.json.toString()
        return if (s.length <= MAX_RESULT) s else s.take(MAX_RESULT) + "…[recortado: pide menos datos o filtra]"
    }

    // ───────────────────────── Mensajes

    private fun body(model: ModelInfo, messages: List<JsonObject>, withTools: Boolean, withReasoning: Boolean): JsonObject {
        val prefs = host.repo.prefs
        return buildJsonObject {
            put("model", model.id)
            put("messages", JsonArray(messages))
            put("temperature", prefs.float("bot.temperature").toDouble())
            putJsonObject("usage") { put("include", true) }
            if (withTools) {
                put("tools", buildJsonArray { tools.forEach { add(it.spec()) } })
                put("tool_choice", "auto")
            }
            if (withReasoning) {
                val effort = prefs.str("bot.effort")
                val hide = !prefs.bool("bot.show_reasoning")
                val e = when (effort) {
                    "off" -> "none"
                    "low", "medium", "high" -> effort
                    else -> null
                }
                if (e != null || hide) putJsonObject("reasoning") {
                    if (e != null) put("effort", e)
                    if (hide) put("exclude", true)
                }
            }
        }
    }

    private fun msg(role: String, content: String): JsonObject = buildJsonObject { put("role", role); put("content", content) }

    private fun assistantToolCalls(c: Completion): JsonObject = buildJsonObject {
        put("role", "assistant")
        put("content", c.text)
        put(
            "tool_calls",
            buildJsonArray {
                for (t in c.toolCalls) add(
                    buildJsonObject {
                        put("id", t.id)
                        put("type", "function")
                        putJsonObject("function") { put("name", t.name); put("arguments", t.arguments) }
                    },
                )
            },
        )
        c.reasoningDetails?.let { put("reasoning_details", it) }
    }

    private fun convertHistory(history: List<ChatMessage>, model: ModelInfo, dir: File, limit: Int): List<JsonObject> {
        val usable = history.filter { it.isUser || it.text.isNotBlank() }.takeLast(limit.coerceAtLeast(2)).dropWhile { !it.isUser }
        val lastUser = usable.lastOrNull { it.isUser }?.id
        return usable.map { m -> if (m.isUser) userMessage(m, model, dir, withMedia = m.id == lastUser) else assistantMessage(m) }
    }

    private fun assistantMessage(m: ChatMessage): JsonObject {
        val done = m.actions.filter { it.write && it.ok && !it.cancelled }
        val extra = when {
            m.undone && done.isNotEmpty() -> "\n\n[El usuario deshizo estos cambios: ${done.joinToString("; ") { it.summary }}]"
            done.isNotEmpty() -> "\n\n[Acciones que realicé: ${done.joinToString("; ") { it.summary }}]"
            else -> ""
        }
        return msg("assistant", m.text + extra)
    }

    private fun userMessage(m: ChatMessage, model: ModelInfo, dir: File, withMedia: Boolean): JsonObject {
        if (m.attachments.isEmpty()) return msg("user", m.text)
        val parts = ArrayList<JsonObject>()
        val inline = StringBuilder()
        for (a in m.attachments) {
            val f = File(dir, a.path)
            val ext = a.name.substringAfterLast('.', "").lowercase()
            when {
                !withMedia -> inline.append("\n[Adjunto anterior: ${a.name}]")
                !f.exists() -> inline.append("\n[Adjunto no disponible: ${a.name}]")
                a.kind == "image" && model.images -> parts += buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", dataUri(a.mime.ifBlank { "image/jpeg" }, f)) }
                }
                a.kind == "audio" && model.audio -> parts += buildJsonObject {
                    put("type", "input_audio")
                    putJsonObject("input_audio") {
                        put("data", Base64.getEncoder().encodeToString(f.readBytes()))
                        put("format", if (ext == "mp3" || a.mime.contains("mpeg")) "mp3" else if (ext.isNotEmpty() && ext != "wav") ext else "wav")
                    }
                }
                a.kind == "file" && isTextual(a.mime, ext) -> {
                    val body = runCatching { f.readText().take(MAX_TEXT_FILE) }.getOrDefault("")
                    inline.append("\n\nContenido del archivo «${a.name}»:\n```\n$body\n```")
                }
                a.kind == "file" && model.files -> parts += buildJsonObject {
                    put("type", "file")
                    putJsonObject("file") {
                        put("filename", a.name)
                        put("file_data", dataUri(a.mime.ifBlank { "application/pdf" }, f))
                    }
                }
                else -> inline.append("\n[No puedo leer «${a.name}» con este modelo]")
            }
        }
        val text = (m.text + inline).trim().ifEmpty { "(adjunto)" }
        if (parts.isEmpty()) return msg("user", text)
        return buildJsonObject {
            put("role", "user")
            put("content", buildJsonArray { add(OpenRouter.textPart(text)); parts.forEach { add(it) } })
        }
    }

    private fun dataUri(mime: String, f: File): String = "data:$mime;base64," + Base64.getEncoder().encodeToString(f.readBytes())

    private fun isTextual(mime: String, ext: String): Boolean =
        mime.startsWith("text/") || mime == "application/json" || ext in setOf("txt", "md", "csv", "tsv", "json", "log", "xml", "yml", "yaml")

    // ───────────────────────── Argumentos tolerantes

    internal fun parseArgs(raw: String): JsonObject? {
        var t = raw.trim()
        if (t.isEmpty() || t == "null") return JsonObject(emptyMap())
        if (t.startsWith("```")) t = t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            fromElement(lenient.parseToJsonElement(t))
        } catch (_: Throwable) {
            // Algunos modelos mandan dos objetos pegados ({...}{...}): se usa el primero.
            firstObject(t)?.let { runCatching { fromElement(lenient.parseToJsonElement(it)) }.getOrNull() }
        }
    }

    private fun fromElement(el: JsonElement): JsonObject? = when (el) {
        is JsonObject -> el
        is JsonArray -> el.firstOrNull() as? JsonObject
        is JsonNull -> JsonObject(emptyMap())
        // Algunos modelos mandan el objeto como texto ("{\"a\":1}"). Solo se desenvuelve si de verdad parece JSON,
        // para no entrar en un ciclo con textos sueltos.
        is JsonPrimitive -> if (el.isString && el.content.trimStart().let { it.startsWith("{") || it.startsWith("[") }) {
            runCatching { fromElement(lenient.parseToJsonElement(el.content)) }.getOrNull()
        } else null
    }

    private fun firstObject(t: String): String? {
        val start = t.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until t.length) {
            val ch = t[i]
            if (inStr) {
                if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false
            } else when (ch) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return t.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_STEPS = 8
        const val MAX_RESULT = 7000
        const val MAX_TEXT_FILE = 60_000
    }
}
