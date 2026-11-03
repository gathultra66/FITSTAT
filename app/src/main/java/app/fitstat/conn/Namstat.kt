package app.fitstat.conn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Lo que NAMSTAT le cuenta a FITSTAT por el Enlace T-A-T (ecosistema T-A-T). Ver `lib/tat/tat_bridge.dart` en NAMSTAT.

private fun JsonObject.s(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.l(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: 0L
private fun JsonObject.i(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.d(k: String, def: Double = 0.0) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull ?: def
private fun JsonObject.b(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false
private fun JsonObject.arr(k: String): List<JsonObject> = ((this[k] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }

/** Una canción de la biblioteca de NAMSTAT. [key] la identifica para los comandos. */
class NsTrack(
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int = 0,
    val genre: String = "",
    val format: String = "",
    val durationMs: Long = 0L,
    val fav: Boolean = false,
    val plays: Int = 0,
) {
    val label: String get() = if (artist.isBlank()) title else "$title · $artist"

    companion object {
        fun parse(o: JsonObject) = NsTrack(o.s("key"), o.s("title"), o.s("artist"), o.s("album"), o.i("year"), o.s("genre"), o.s("format"), o.l("duration_ms"), o.b("fav"), o.i("plays"))
    }
}

/** El estado completo del reproductor de NAMSTAT (más que lo que da la sesión de Android). */
class NsState(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Double = 1.0,
    val shuffle: Boolean = false,
    /** off, all u one. */
    val repeat: String = "off",
    /** 0 a 1, el volumen propio de NAMSTAT (no el del teléfono). */
    val volume: Double = 1.0,
    /** Milisegundos que faltan para que el temporizador de sueño pause la música (null = no hay). */
    val sleepMs: Long? = null,
    /** El temporizador de sueño pausará al terminar la canción. */
    val sleepEnd: Boolean = false,
    /** De dónde viene la cola («FAVORITAS», «ÁLBUM · …»). */
    val source: String = "",
    /** Sesión de audio de Android de NAMSTAT. */
    val session: Int = 0,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val track: NsTrack? = null,
    val next: List<NsTrack> = emptyList(),
    val libraryReady: Boolean = false,
    val librarySize: Int = 0,
) {
    companion object {
        fun parse(o: JsonObject) = NsState(
            playing = o.b("playing"), buffering = o.b("buffering"),
            positionMs = o.l("position_ms"), durationMs = o.l("duration_ms"),
            speed = o.d("speed", 1.0), shuffle = o.b("shuffle"), repeat = o.s("repeat").ifEmpty { "off" },
            volume = o.d("volume", 1.0),
            sleepMs = (o["sleep_ms"] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull,
            sleepEnd = o.b("sleep_end"), source = o.s("source"), session = o.i("session"),
            queueIndex = (o["queue_index"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: -1,
            queueSize = o.i("queue_size"),
            track = (o["track"] as? JsonObject)?.let { NsTrack.parse(it) },
            next = o.arr("next").map { NsTrack.parse(it) },
            libraryReady = o.b("library_ready"), librarySize = o.i("library_size"),
        )
    }
}

class NsPlaylist(val id: String, val name: String, val count: Int, val durationMs: Long)

class NsPlaylists(val items: List<NsPlaylist>, val favorites: Int)

class NsStats(
    val todayMs: Long, val weekMs: List<Long>, val weekHours: Double, val streak: Int,
    val tracks: Int, val albums: Int, val artists: Int, val favorites: Int, val playlists: Int, val top: List<NsTrack>,
) {
    companion object {
        fun parse(o: JsonObject) = NsStats(
            o.l("today_ms"),
            ((o["week_ms"] as? JsonArray) ?: JsonArray(emptyList())).map { (it as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: 0L },
            o.d("week_hours"), o.i("streak"), o.i("tracks"), o.i("albums"), o.i("artists"), o.i("favorites"), o.i("playlists"),
            o.arr("top").map { NsTrack.parse(it) },
        )
    }
}

class NsQueue(val index: Int, val size: Int, val from: Int, val items: List<NsTrack>)

class NsSearch(val total: Int, val items: List<NsTrack>)

/** Cómo va el enlace con NAMSTAT, para mostrarlo y decidir qué hacer. */
enum class NsLinkState(val label: String) {
    Unknown("Sin comprobar"),
    Linked("Enlazado"),
    Closed("NAMSTAT está cerrado"),
    NoLink("NAMSTAT no tiene el enlace"),
    Disabled("Enlace apagado en NAMSTAT"),
    OldProto("Versiones incompatibles"),
    Error("No contesta"),
}

/**
 * El conector completo con NAMSTAT. Además de ver y controlar la sesión de reproducción (como con Namida), habla con
 * NAMSTAT directamente: estado exacto, cola, listas, estadísticas, búsqueda, volumen propio, velocidad, repetir, aleatorio,
 * favoritas, temporizador de sueño y reproducir listas, álbumes, artistas o canciones. Si NAMSTAT está cerrado, las órdenes
 * lo abren y se las dan al arrancar.
 */
class NamstatClient(private val link: NamstatLink, private val scope: CoroutineScope, private val now: () -> Long = System::currentTimeMillis) {
    var state: NsState? by mutableStateOf(null)
        private set
    var linkState: NsLinkState by mutableStateOf(NsLinkState.Unknown)
        private set
    var message: String by mutableStateOf("")
        private set
    var version: String by mutableStateOf("")
        private set
    var capabilities: List<String> by mutableStateOf(emptyList())
        private set
    var stateAt: Long = 0L
        private set

    private var pollJob: Job? = null

    fun has(cap: String) = cap in capabilities

    /** Saluda a NAMSTAT: ¿está el enlace, se entienden los protocolos, está encendido? */
    suspend fun hello(): NsLinkState {
        val r = link.call("hello")
        message = r.message
        linkState = when {
            r.notRunning -> NsLinkState.Closed
            r.noLink -> NsLinkState.NoLink
            !r.ok -> NsLinkState.Error
            (r.json["proto"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull.let { it == null || it > NAMSTAT_PROTO } -> NsLinkState.OldProto
            !r.json.b("enabled") -> NsLinkState.Disabled
            else -> NsLinkState.Linked
        }
        if (r.ok) {
            version = r.str("version")
            capabilities = ((r.json["capabilities"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        }
        if (linkState != NsLinkState.Linked) state = null
        return linkState
    }

    /** Pide el estado. Devuelve null (y deja el motivo en [linkState]) si no se pudo. */
    suspend fun refresh(): NsState? {
        val r = link.call("state")
        if (r.ok) {
            val s = NsState.parse(r.json)
            state = s
            stateAt = now()
            linkState = NsLinkState.Linked
            message = ""
            return s
        }
        applyFailure(r)
        return null
    }

    private fun applyFailure(r: LinkResult) {
        message = r.message
        linkState = when {
            r.notRunning -> NsLinkState.Closed
            r.noLink -> NsLinkState.NoLink
            r.error == LinkResult.ERR_DISABLED -> NsLinkState.Disabled
            else -> NsLinkState.Error
        }
        if (linkState != NsLinkState.Linked) state = null
    }

    /** Posición estimada ahora, avanzando desde la última consulta si está sonando. */
    fun positionNow(): Long {
        val s = state ?: return 0L
        if (!s.playing) return s.positionMs
        val p = s.positionMs + ((now() - stateAt) * s.speed).toLong()
        return if (s.durationMs > 0) p.coerceAtMost(s.durationMs) else p
    }

    /** Consulta el estado cada [everyMs] mientras se llame desde una pantalla visible. Llamar otra vez no duplica. */
    fun startPolling(everyMs: Long = 1500L) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            hello()
            while (true) {
                refresh()
                // Si está cerrado no tiene sentido insistir cada segundo y medio.
                delay(if (linkState == NsLinkState.Linked) everyMs else everyMs * 4)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // ───────── lecturas

    suspend fun queue(from: Int = 0, limit: Int = 30): NsQueue? {
        val r = link.call("queue", obj("from" to from, "limit" to limit))
        if (!r.ok) { applyFailure(r); return null }
        return NsQueue(r.json.i("index"), r.json.i("size"), r.json.i("from"), r.json.arr("items").map { NsTrack.parse(it) })
    }

    suspend fun playlists(): NsPlaylists? {
        val r = link.call("playlists")
        if (!r.ok) { applyFailure(r); return null }
        return NsPlaylists(r.json.arr("items").map { NsPlaylist(it.s("id"), it.s("name"), it.i("count"), it.l("duration_ms")) }, r.json.i("favorites"))
    }

    suspend fun stats(): NsStats? {
        val r = link.call("stats")
        if (!r.ok) { applyFailure(r); return null }
        return NsStats.parse(r.json)
    }

    suspend fun search(q: String, limit: Int = 10): NsSearch? {
        val r = link.call("search", obj("q" to q, "limit" to limit))
        if (!r.ok) { applyFailure(r); return null }
        return NsSearch(r.json.i("total"), r.json.arr("items").map { NsTrack.parse(it) })
    }

    // ───────── comandos

    /**
     * Manda una orden. Si NAMSTAT está cerrado, lo abre con la orden para que la haga al arrancar (y [backAfter] lo manda al
     * fondo para que no te saque de FITSTAT). Con [openIfClosed] en false solo informa.
     */
    suspend fun command(method: String, args: JsonObject = JsonObject(emptyMap()), openIfClosed: Boolean = true, backAfter: Boolean = true): LinkResult {
        val r = link.call(method, args)
        if (r.ok) {
            // La orden cambió algo: se refresca el estado sin esperar al siguiente ciclo.
            if (method !in QUIET) scope.launch { delay(350); refresh() }
            return r
        }
        if (r.notRunning && openIfClosed) {
            return if (link.launch(method, args, backAfter)) LinkResult(true, message = "Abrí NAMSTAT para hacerlo.", json = obj("opened" to true))
            else LinkResult.fail("launch_failed", "No pude abrir NAMSTAT.")
        }
        applyFailure(r)
        return r
    }

    suspend fun play() = command("play")
    suspend fun pause() = command("pause")
    suspend fun toggle() = command("toggle")
    suspend fun next() = command("next")
    suspend fun previous() = command("previous")
    suspend fun seek(ms: Long) = command("seek", obj("ms" to ms))
    suspend fun seekIndex(index: Int) = command("seek_index", obj("index" to index))
    suspend fun volume(v: Double) = command("volume", obj("value" to v.coerceIn(0.0, 1.0)), openIfClosed = false)
    suspend fun shuffle(on: Boolean? = null) = command("shuffle", obj("on" to on))
    suspend fun repeat(mode: String? = null) = command("repeat", obj("mode" to mode))
    suspend fun speed(v: Double) = command("speed", obj("value" to v.coerceIn(0.25, 3.0)))
    suspend fun favorite(on: Boolean? = null, key: String? = null) = command("favorite", obj("on" to on, "key" to key))
    suspend fun sleepIn(minutes: Int) = command("sleep", obj("minutes" to minutes))
    suspend fun sleepAtEnd() = command("sleep", obj("end" to true))
    suspend fun sleepCancel() = command("sleep", obj("cancel" to true))
    suspend fun playFade(fromPercent: Int, secs: Int) = command("play_fade", obj("from" to fromPercent, "secs" to secs))
    suspend fun playPlaylist(name: String? = null, id: String? = null, shuffle: Boolean? = null) = command("play_playlist", obj("name" to name, "id" to id, "shuffle" to shuffle))
    suspend fun playFavorites(shuffle: Boolean? = null) = command("play_favorites", obj("shuffle" to shuffle))
    suspend fun playRecent(shuffle: Boolean? = null) = command("play_recent", obj("shuffle" to shuffle))
    suspend fun playSearch(q: String, shuffle: Boolean? = null) = command("play_search", obj("q" to q, "shuffle" to shuffle))
    suspend fun playAlbum(name: String, shuffle: Boolean? = null) = command("play_album", obj("name" to name, "shuffle" to shuffle))
    suspend fun playArtist(name: String, shuffle: Boolean? = null) = command("play_artist", obj("name" to name, "shuffle" to shuffle))
    suspend fun playTrack(key: String) = command("play_track", obj("key" to key))
    suspend fun enqueue(key: String? = null, q: String? = null, next: Boolean = false) = command("enqueue", obj("key" to key, "q" to q, "next" to next))

    /** Avisa a NAMSTAT que empezó o terminó un entrenamiento (solo informativo). Nunca lo abre. */
    suspend fun workout(on: Boolean, name: String = "") = command("workout", obj("on" to on, "name" to name), openIfClosed = false)

    companion object {
        /** Órdenes tras las que no hace falta refrescar enseguida. */
        private val QUIET = setOf("workout", "volume")
    }
}

/** Para pruebas y para cuando no hay enlace: nunca contesta. */
object NoNamstatLink : NamstatLink {
    override suspend fun call(method: String, args: JsonObject) = LinkResult.fail(LinkResult.ERR_NO_LINK, "NAMSTAT no está disponible.")
    override fun launch(method: String, args: JsonObject, back: Boolean) = false
}
