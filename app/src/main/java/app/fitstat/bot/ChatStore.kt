package app.fitstat.bot

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Archivo que el usuario adjuntó a un mensaje (guardado dentro de la app). */
@Serializable
data class Attachment(
    val id: String,
    /** "image", "file" o "audio". */
    val kind: String,
    val name: String,
    val mime: String,
    /** Ruta del archivo dentro de files/chat. */
    val path: String,
    val size: Long = 0,
    /** Duración en segundos (solo audio). */
    val seconds: Int = 0,
)

/** Algo que FITBOT hizo con una herramienta, para mostrarlo como "chip" bajo su respuesta. */
@Serializable
data class ActionRecord(
    val tool: String,
    val summary: String,
    val ok: Boolean = true,
    /** true si cambió datos de la app. */
    val write: Boolean = false,
    val cancelled: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: String,
    /** "user" o "assistant". */
    val role: String,
    val text: String = "",
    val reasoning: String = "",
    val attachments: List<Attachment> = emptyList(),
    val actions: List<ActionRecord> = emptyList(),
    val ts: Long = 0,
    val error: String? = null,
    val model: String? = null,
    /** Punto de restauración del turno (0 = no hubo cambios que deshacer). */
    val checkpoint: Long = 0,
    val undone: Boolean = false,
    val tokens: Int = 0,
    val cost: Double? = null,
    /** "" normal · "stopped" el usuario detuvo la respuesta · "no_key" falta la clave de OpenRouter. */
    val status: String = "",
) {
    val isUser: Boolean get() = role == "user"
}

/** Guarda el chat en disco (chat.json) y las adjuntos en la carpeta chat/. */
class ChatStore(private val dir: File, private val scope: CoroutineScope) {
    val messages = mutableStateListOf<ChatMessage>()

    private val file = File(dir, "chat.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)
    val attachDir: File get() = File(dir, "chat").apply { mkdirs() }

    init {
        try {
            if (file.exists()) messages.addAll(json.decodeFromString(ListSerializer(ChatMessage.serializer()), file.readText()))
        } catch (_: Throwable) {
        }
        scope.launch(Dispatchers.IO) {
            for (u in saveSignal) {
                delay(250)
                writeNow()
            }
        }
    }

    fun add(m: ChatMessage) {
        messages.add(m)
        save()
    }

    fun replace(id: String, f: (ChatMessage) -> ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) {
            messages[i] = f(messages[i])
            save()
        }
    }

    fun remove(id: String) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) {
            val m = messages.removeAt(i)
            deleteFilesOf(m)
            save()
        }
    }

    /** Quita todos los mensajes desde el que tiene [id] (incluido) hacia el final. */
    fun removeFrom(id: String) {
        val i = messages.indexOfFirst { it.id == id }
        if (i < 0) return
        while (messages.size > i) deleteFilesOf(messages.removeAt(messages.size - 1))
        save()
    }

    fun clear() {
        messages.forEach { deleteFilesOf(it) }
        messages.clear()
        save()
    }

    private fun deleteFilesOf(m: ChatMessage) {
        for (a in m.attachments) runCatching { File(attachDir, a.path).delete() }
    }

    fun save() {
        saveSignal.trySend(Unit)
    }

    @Synchronized
    fun writeNow() {
        try {
            dir.mkdirs()
            val tmp = File(dir, "chat.json.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(ChatMessage.serializer()), messages.toList()))
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        } catch (_: Throwable) {
        }
    }
}
