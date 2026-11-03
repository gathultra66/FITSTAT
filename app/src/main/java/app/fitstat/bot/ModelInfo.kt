package app.fitstat.bot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File

/** Lo que se sabe de un modelo de OpenRouter: qué acepta y cuánto cuesta. */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val contextLength: Int = 0,
    val inputs: Set<String> = setOf("text"),
    val outputs: Set<String> = setOf("text"),
    /** Dólares por millón de tokens. */
    val promptPerMillion: Double? = null,
    val completionPerMillion: Double? = null,
    val parameters: Set<String> = emptySet(),
    val created: Long = 0,
    /** false cuando el modelo no está en la lista descargada (p. ej. un ID escrito a mano). */
    val known: Boolean = true,
) {
    val images: Boolean get() = "image" in inputs
    val audio: Boolean get() = "audio" in inputs
    val files: Boolean get() = "file" in inputs

    /** Si no lo conocemos, se intenta con herramientas y se reintenta sin ellas si falla. */
    val tools: Boolean get() = !known || "tools" in parameters
    val reasoning: Boolean get() = "reasoning" in parameters || "include_reasoning" in parameters
    val free: Boolean get() = id.endsWith(":free") || (promptPerMillion == 0.0 && completionPerMillion == 0.0)

    /** "$0.10 / $0.40 por millón" o "Gratis". */
    val priceLabel: String
        get() = when {
            free -> "Gratis"
            promptPerMillion == null || completionPerMillion == null -> ""
            else -> "${money(promptPerMillion)} / ${money(completionPerMillion)}"
        }

    val contextLabel: String
        get() = when {
            contextLength >= 1_000_000 -> "%.1fM".format(java.util.Locale.US, contextLength / 1_000_000.0).replace(".0M", "M")
            contextLength >= 1000 -> "${contextLength / 1000}K"
            contextLength > 0 -> "$contextLength"
            else -> ""
        }

    companion object {
        fun unknown(id: String) = ModelInfo(id = id, name = id, known = false)

        private fun money(v: Double): String = when {
            v >= 10 -> "$" + "%.0f".format(java.util.Locale.US, v)
            v >= 1 -> "$" + "%.2f".format(java.util.Locale.US, v)
            v >= 0.1 -> "$" + "%.2f".format(java.util.Locale.US, v)
            else -> "$" + "%.3f".format(java.util.Locale.US, v)
        }

        /** Convierte la respuesta de GET /models. */
        fun parseList(text: String): List<ModelInfo> {
            val root = Json.parseToJsonElement(text).jsonObject
            val data = (root["data"] as? JsonArray) ?: return emptyList()
            return data.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val arch = o["architecture"] as? JsonObject
                val pricing = o["pricing"] as? JsonObject
                fun price(k: String): Double? = pricing?.str(k)?.toDoubleOrNull()?.let { it * 1_000_000.0 }
                ModelInfo(
                    id = id,
                    name = (o.str("name") ?: id).substringAfter(": ", o.str("name") ?: id),
                    description = o.str("description").orEmpty(),
                    contextLength = (o["context_length"] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() } ?: 0,
                    inputs = arch?.strList("input_modalities")?.toSet()?.ifEmpty { null } ?: setOf("text"),
                    outputs = arch?.strList("output_modalities")?.toSet()?.ifEmpty { null } ?: setOf("text"),
                    promptPerMillion = price("prompt"),
                    completionPerMillion = price("completion"),
                    parameters = o.strList("supported_parameters").toSet(),
                    created = (o["created"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }
        }

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.strList(k: String): List<String> =
            (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    }
}

/** Lista de modelos de OpenRouter, guardada en disco para no descargarla cada vez. */
class ModelCatalog(private val dir: File, private val api: OpenRouter) {
    var models: List<ModelInfo> by mutableStateOf(emptyList())
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var updatedAt: Long by mutableStateOf(0L)
        private set

    private val file = File(dir, "models.json")
    private var byId: Map<String, ModelInfo> = emptyMap()

    /** Carga la copia guardada (si la hay). Es rápido, pero se llama fuera del hilo principal. */
    suspend fun loadCached() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        try {
            val list = ModelInfo.parseList(file.readText())
            set(list, file.lastModified())
        } catch (_: Throwable) {
        }
    }

    /** Descarga la lista actual. Si falla, deja la copia guardada y guarda el motivo en [error]. */
    suspend fun refresh(apiKey: String?) {
        loading = true
        error = null
        try {
            val text = api.models(apiKey)
            val list = withContext(Dispatchers.IO) {
                val l = ModelInfo.parseList(text)
                if (l.isNotEmpty()) {
                    dir.mkdirs()
                    file.writeText(text)
                }
                l
            }
            if (list.isNotEmpty()) set(list, System.currentTimeMillis()) else error = "OpenRouter no devolvió modelos."
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.message ?: "No pude descargar la lista de modelos."
        } finally {
            loading = false
        }
    }

    private fun set(list: List<ModelInfo>, at: Long) {
        models = list
        byId = list.associateBy { it.id }
        updatedAt = at
    }

    fun info(id: String): ModelInfo = byId[id] ?: ModelInfo.unknown(id)

    val isStale: Boolean get() = models.isEmpty() || System.currentTimeMillis() - updatedAt > 12 * 3_600_000L
}
