package app.fitstat.bot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.EOFException
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

/** Error devuelto por OpenRouter (o de red). [code] 0 = sin conexión. */
class OpenRouterException(val code: Int, message: String, val raw: String = "") : Exception(message) {
    /** El mensaje de OpenRouter sugiere que el modelo no admite herramientas. */
    val mentionsTools: Boolean
        get() = Regex("tool|function", RegexOption.IGNORE_CASE).containsMatchIn(raw.ifEmpty { message.orEmpty() })
    val mentionsReasoning: Boolean
        get() = Regex("reasoning|thinking", RegexOption.IGNORE_CASE).containsMatchIn(raw.ifEmpty { message.orEmpty() })
}

data class ToolCall(val id: String, val name: String, val arguments: String)

data class Usage(val promptTokens: Int = 0, val completionTokens: Int = 0, val cost: Double? = null) {
    val total: Int get() = promptTokens + completionTokens
}

/** Resultado completo de una llamada al modelo. */
class Completion(
    val text: String,
    val reasoning: String,
    val reasoningDetails: JsonArray?,
    val toolCalls: List<ToolCall>,
    val finishReason: String?,
    val usage: Usage?,
)

/** Recibe el avance de la respuesta mientras llega. */
interface StreamListener {
    fun onText(delta: String) {}
    fun onReasoning(delta: String) {}
    fun onToolStarted(name: String) {}
}

/**
 * Cliente mínimo de OpenRouter (API compatible con OpenAI), con HttpURLConnection para no depender
 * de librerías externas. Soporta respuesta en vivo (SSE), herramientas y razonamiento.
 */
class OpenRouter(private val baseUrl: String = "https://openrouter.ai/api/v1") {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun models(apiKey: String?): String = withContext(Dispatchers.IO) {
        val conn = open("$baseUrl/models", apiKey, post = false)
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw errorFrom(code, text)
            text
        } catch (e: IOException) {
            throw networkError(e)
        } finally {
            conn.disconnect()
        }
    }

    /** Comprueba la clave (OpenRouter responde 401 si no es válida). Devuelve un resumen. */
    suspend fun checkKey(apiKey: String): String = withContext(Dispatchers.IO) {
        val conn = open("$baseUrl/key", apiKey, post = false)
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw errorFrom(code, text)
            val d = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonObject
            val label = (d?.get("label") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val limit = (d?.get("limit_remaining") as? JsonPrimitive)?.doubleOrNull
            buildString {
                append("Clave válida")
                if (label.isNotBlank()) append(" · ").append(label)
                if (limit != null) append(" · restante $").append("%.2f".format(java.util.Locale.US, limit))
            }
        } catch (e: IOException) {
            throw networkError(e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Pide una respuesta al modelo. Con [stream] el texto llega por [listener] mientras se escribe;
     * en cualquier caso devuelve la respuesta completa.
     */
    suspend fun chat(apiKey: String, body: JsonObject, stream: Boolean, listener: StreamListener? = null): Completion =
        withContext(Dispatchers.IO) {
            val payload = JsonObject(body + ("stream" to JsonPrimitive(stream))).toString()
            val conn = open("$baseUrl/chat/completions", apiKey, post = true)
            // Si se cancela la corrutina, se corta la conexión para no quedarse esperando en una lectura bloqueada.
            val finished = AtomicBoolean(false)
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    if (!finished.get()) runCatching { conn.disconnect() }
                }
            }
            try {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    throw errorFrom(code, err)
                }
                if (stream) readStream(conn, listener) else readWhole(conn, listener)
            } catch (e: IOException) {
                ensureActive()
                throw networkError(e)
            } finally {
                finished.set(true)
                watcher.cancel()
                conn.disconnect()
            }
        }

    // ───────────────────────── Lectura

    private fun readWhole(conn: HttpURLConnection, listener: StreamListener?): Completion {
        val text = conn.inputStream.bufferedReader().readText()
        val root = json.parseToJsonElement(text) as? JsonObject ?: throw OpenRouterException(502, "Respuesta no válida de OpenRouter.", text)
        (root["error"] as? JsonObject)?.let { throw errorFromObject(it, text) }
        val choice = ((root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?: throw OpenRouterException(502, "OpenRouter no devolvió ninguna respuesta.", text)
        (choice["error"] as? JsonObject)?.let { throw errorFromObject(it, text) }
        val msg = choice["message"] as? JsonObject ?: JsonObject(emptyMap())
        val acc = Accumulator()
        acc.applyMessage(msg, listener)
        acc.finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
        acc.usage = parseUsage(root["usage"])
        return acc.build()
    }

    private suspend fun readStream(conn: HttpURLConnection, listener: StreamListener?): Completion {
        val acc = Accumulator()
        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        val ctx = currentCoroutineContext()
        reader.use { r ->
            while (true) {
                ctx.ensureActive()
                val line = r.readLine() ?: break
                if (line.isEmpty() || line.startsWith(":")) continue // comentarios de mantenimiento ("OPENROUTER PROCESSING")
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val obj = try {
                    json.parseToJsonElement(data) as? JsonObject
                } catch (_: Throwable) {
                    null
                } ?: continue
                (obj["error"] as? JsonObject)?.let { throw errorFromObject(it, data) }
                val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                if (choice != null) {
                    (choice["error"] as? JsonObject)?.let { throw errorFromObject(it, data) }
                    (choice["delta"] as? JsonObject)?.let { acc.applyDelta(it, listener) }
                    (choice["message"] as? JsonObject)?.let { acc.applyMessage(it, listener) }
                    (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull?.let { acc.finish = it }
                }
                parseUsage(obj["usage"])?.let { acc.usage = it }
            }
        }
        return acc.build()
    }

    private fun parseUsage(el: JsonElement?): Usage? {
        val o = el as? JsonObject ?: return null
        return Usage(
            promptTokens = (o["prompt_tokens"] as? JsonPrimitive)?.intOrNull ?: 0,
            completionTokens = (o["completion_tokens"] as? JsonPrimitive)?.intOrNull ?: 0,
            cost = (o["cost"] as? JsonPrimitive)?.doubleOrNull,
        )
    }

    /** Junta los pedazos que van llegando (texto, razonamiento y llamadas a herramientas). */
    private class Accumulator {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val details = ArrayList<JsonObject>()
        val calls = sortedMapOf<Int, CallBuf>()
        var finish: String? = null
        var usage: Usage? = null

        class CallBuf { var id = ""; var name = StringBuilder(); var args = StringBuilder() }

        fun applyDelta(d: JsonObject, l: StreamListener?) {
            (d["content"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { text.append(it); l?.onText(it) }
            val r = (d["reasoning"] as? JsonPrimitive)?.contentOrNull ?: (d["reasoning_content"] as? JsonPrimitive)?.contentOrNull
            if (!r.isNullOrEmpty()) { reasoning.append(r); l?.onReasoning(r) }
            (d["reasoning_details"] as? JsonArray)?.forEach { mergeDetail(it as? JsonObject ?: return@forEach) }
            (d["tool_calls"] as? JsonArray)?.forEach { tcEl ->
                val tc = tcEl as? JsonObject ?: return@forEach
                val idx = (tc["index"] as? JsonPrimitive)?.intOrNull ?: calls.size
                val buf = calls.getOrPut(idx) { CallBuf() }
                (tc["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { buf.id = it }
                val fn = tc["function"] as? JsonObject
                val before = buf.name.length
                (fn?.get("name") as? JsonPrimitive)?.contentOrNull?.let { buf.name.append(it) }
                (fn?.get("arguments") as? JsonPrimitive)?.contentOrNull?.let { buf.args.append(it) }
                if (before == 0 && buf.name.isNotEmpty()) l?.onToolStarted(buf.name.toString())
            }
        }

        fun applyMessage(m: JsonObject, l: StreamListener?) {
            val c = m["content"]
            val s = when (c) {
                is JsonPrimitive -> c.contentOrNull
                is JsonArray -> c.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }.joinToString("")
                else -> null
            }
            if (!s.isNullOrEmpty() && text.isEmpty()) { text.append(s); l?.onText(s) }
            val r = (m["reasoning"] as? JsonPrimitive)?.contentOrNull
            if (!r.isNullOrEmpty() && reasoning.isEmpty()) { reasoning.append(r); l?.onReasoning(r) }
            if (details.isEmpty()) (m["reasoning_details"] as? JsonArray)?.forEach { mergeDetail(it as? JsonObject ?: return@forEach) }
            (m["tool_calls"] as? JsonArray)?.forEachIndexed { i, tcEl ->
                val tc = tcEl as? JsonObject ?: return@forEachIndexed
                if (calls.containsKey(i)) return@forEachIndexed
                val buf = CallBuf()
                buf.id = (tc["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val fn = tc["function"] as? JsonObject
                buf.name.append((fn?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty())
                val a = fn?.get("arguments")
                buf.args.append(if (a is JsonPrimitive) a.contentOrNull.orEmpty() else a?.toString().orEmpty())
                calls[i] = buf
                l?.onToolStarted(buf.name.toString())
            }
        }

        /** Los detalles de razonamiento llegan en trozos con el mismo índice: se concatena el texto. */
        private fun mergeDetail(item: JsonObject) {
            val idx = (item["index"] as? JsonPrimitive)?.intOrNull
            val type = (item["type"] as? JsonPrimitive)?.contentOrNull
            if (idx != null) {
                val at = details.indexOfFirst { (it["index"] as? JsonPrimitive)?.intOrNull == idx && (it["type"] as? JsonPrimitive)?.contentOrNull == type }
                if (at >= 0) {
                    val old = details[at]
                    val merged = LinkedHashMap<String, JsonElement>(old)
                    for ((k, v) in item) {
                        val prev = old[k]
                        if ((k == "text" || k == "summary" || k == "data") && prev is JsonPrimitive && v is JsonPrimitive && prev.isString && v.isString) {
                            merged[k] = JsonPrimitive(prev.content + v.content)
                        } else if (v !is JsonNull) merged[k] = v
                    }
                    details[at] = JsonObject(merged)
                    return
                }
            }
            details.add(item)
        }

        fun build() = Completion(
            text = text.toString(),
            reasoning = reasoning.toString(),
            reasoningDetails = if (details.isEmpty()) null else JsonArray(details),
            toolCalls = calls.values.filter { it.name.isNotEmpty() }.mapIndexed { i, b ->
                ToolCall(b.id.ifEmpty { "call_$i" }, b.name.toString(), b.args.toString().ifBlank { "{}" })
            },
            finishReason = finish,
            usage = usage,
        )
    }

    // ───────────────────────── Conexión y errores

    private fun open(url: String, apiKey: String?, post: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 180_000
        c.requestMethod = if (post) "POST" else "GET"
        c.setRequestProperty("Accept", if (post) "text/event-stream, application/json" else "application/json")
        c.setRequestProperty("X-Title", "FITSTAT")
        c.setRequestProperty("HTTP-Referer", "https://fitstat.app")
        if (!apiKey.isNullOrBlank()) c.setRequestProperty("Authorization", "Bearer $apiKey")
        if (post) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return c
    }

    private fun networkError(e: IOException): OpenRouterException = when (e) {
        is UnknownHostException -> OpenRouterException(0, "Sin conexión a internet.")
        is SocketTimeoutException -> OpenRouterException(0, "OpenRouter tardó demasiado en responder.")
        is ConnectException, is NoRouteToHostException -> OpenRouterException(0, "No pude conectar con OpenRouter. Revisa tu conexión a internet.")
        is SSLException -> OpenRouterException(0, "No pude abrir una conexión segura con OpenRouter.")
        is SocketException, is EOFException -> OpenRouterException(0, "Se cortó la conexión con OpenRouter. Inténtalo de nuevo.")
        else -> OpenRouterException(0, "Error de red: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun errorFrom(code: Int, text: String): OpenRouterException {
        val o = try {
            (json.parseToJsonElement(text) as? JsonObject)?.get("error") as? JsonObject
        } catch (_: Throwable) {
            null
        }
        if (o != null) return errorFromObject(o, text, code)
        val msg = when (code) {
            401 -> "La clave de OpenRouter no es válida."
            402 -> "No hay créditos suficientes en OpenRouter."
            403 -> "OpenRouter rechazó la petición."
            404 -> "El modelo no está disponible."
            429 -> "Demasiadas peticiones. Espera un momento."
            else -> "OpenRouter respondió con error $code."
        }
        return OpenRouterException(code, msg, text)
    }

    private fun errorFromObject(o: JsonObject, raw: String, httpCode: Int? = null): OpenRouterException {
        val code = (o["code"] as? JsonPrimitive)?.intOrNull ?: httpCode ?: 500
        var msg = (o["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val meta = (o["metadata"] as? JsonObject)?.get("raw")
        if (meta is JsonPrimitive && meta.contentOrNull?.isNotBlank() == true && msg.length < 60) msg += " (" + meta.content.take(200) + ")"
        if (msg.isBlank()) msg = "Error $code de OpenRouter."
        val friendly = when (code) {
            401 -> "La clave de OpenRouter no es válida. Revísala en Ajustes › FITBOT."
            402 -> "No hay créditos suficientes en OpenRouter. Elige un modelo gratis o agrega saldo."
            429 -> "Demasiadas peticiones a este modelo. Espera un momento o prueba otro."
            else -> msg
        }
        return OpenRouterException(code, friendly, raw.take(2000))
    }

    companion object {
        /** Ayudante para armar mensajes y cuerpos sin repetir código. */
        fun textPart(t: String): JsonObject = buildJsonObject { put("type", "text"); put("text", t) }
        fun parts(vararg p: JsonObject): JsonArray = buildJsonArray { p.forEach { add(it) } }
    }
}
