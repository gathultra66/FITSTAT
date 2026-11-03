package app.fitstat.bot

import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Connectors
import app.fitstat.conn.LinkResult
import app.fitstat.conn.NsLinkState
import app.fitstat.conn.NsState
import app.fitstat.conn.NsTrack
import app.fitstat.conn.NamstatClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val NS_ACTIONS = listOf(
    "state", "queue", "playlists", "stats", "search",
    "play", "pause", "toggle", "next", "previous", "seek", "seek_index", "volume", "shuffle", "repeat", "speed", "favorite", "sleep",
    "play_playlist", "play_favorites", "play_recent", "play_search", "play_album", "play_artist", "play_track", "enqueue",
)


private fun trackJson(t: NsTrack): JsonObject = buildJsonObject {
    put("key", t.key); put("title", t.title); put("artist", t.artist); put("album", t.album)
    if (t.year > 0) put("year", t.year)
    if (t.genre.isNotBlank()) put("genre", t.genre)
    put("duration_s", t.durationMs / 1000)
    put("favorite", t.fav); put("plays", t.plays)
}

private fun stateJson(b: JsonObjectBuilder, s: NsState) = with(b) {
    put("playing", s.playing); put("position_s", s.positionMs / 1000); put("duration_s", s.durationMs / 1000)
    put("shuffle", s.shuffle); put("repeat", s.repeat); put("volume_percent", (s.volume * 100).toInt()); put("speed", s.speed)
    put("queue_position", s.queueIndex + 1); put("queue_size", s.queueSize)
    if (s.source.isNotBlank()) put("playing_from", s.source)
    s.sleepMs?.let { put("sleep_timer_minutes_left", (it / 60000).toInt()) }
    if (s.sleepEnd) put("sleep_at_end_of_track", true)
    s.track?.let { put("track", trackJson(it)) }
    arr("up_next", s.next.map { trackJson(it) })
    put("library_ready", s.libraryReady); put("library_size", s.librarySize)
}

/**
 * La herramienta de FITBOT para NAMSTAT (separada para poder probarla): todas las acciones del Enlace T-A-T. Bloquea hasta ~4,5 s
 * esperando la respuesta de NAMSTAT (las herramientas de FITBOT no son suspendibles).
 */
internal fun namstatAction(hub: ConnectorHub, a: JsonObject): ToolOutcome {
    val st = hub.status(Connectors.NAMSTAT)
    if (st == ConnStatus.Off) return fail("El conector NAMSTAT no está conectado. Usa connect_connector con 'namstat'.")
    if (st == ConnStatus.Missing) return fail(hub.missing(Connectors.NAMSTAT) ?: "NAMSTAT no está instalada.")
    // NAMSTAT por el enlace solo necesita estar instalada; el acceso a notificaciones es para la portada y la sesión.
    val action = a.s("action")?.lowercase() ?: return fail("Falta 'action'. Opciones: ${NS_ACTIONS.joinToString()}.")
    if (action !in NS_ACTIONS) return fail("Acción no válida: «$action». Opciones: ${NS_ACTIONS.joinToString()}.")
    val ns = hub.namstat
    val q = a.s("query")
    val shuffle = a.b("shuffle")
    val out: ToolOutcome? = runBlocking {
        withTimeoutOrNull(4500L) {
            when (action) {
                "state" -> {
                    val s = ns.refresh() ?: return@withTimeoutOrNull linkFail(ns)
                    ok("Revisó NAMSTAT") { stateJson(this, s) }
                }
                "queue" -> {
                    val r = ns.queue(a.i("from") ?: 0, (a.i("limit") ?: 20).coerceIn(1, 100)) ?: return@withTimeoutOrNull linkFail(ns)
                    ok("Revisó la cola de NAMSTAT") { put("current_position", r.index + 1); put("size", r.size); put("from", r.from); arr("items", r.items.map { trackJson(it) }) }
                }
                "playlists" -> {
                    val r = ns.playlists() ?: return@withTimeoutOrNull linkFail(ns)
                    ok("Revisó las listas de NAMSTAT") {
                        put("favorites", r.favorites)
                        arr("playlists", r.items.map { p -> buildJsonObject { put("id", p.id); put("name", p.name); put("tracks", p.count); put("minutes", p.durationMs / 60000) } })
                    }
                }
                "stats" -> {
                    val r = ns.stats() ?: return@withTimeoutOrNull linkFail(ns)
                    ok("Revisó las estadísticas de NAMSTAT") {
                        put("listened_today_minutes", r.todayMs / 60000); put("listened_this_week_hours", r.weekHours); put("streak_days", r.streak)
                        put("tracks", r.tracks); put("albums", r.albums); put("artists", r.artists); put("favorites", r.favorites); put("playlists", r.playlists)
                        arr("most_played", r.top.map { trackJson(it) })
                    }
                }
                "search" -> {
                    val text = q ?: return@withTimeoutOrNull fail("Falta 'query'.")
                    val r = ns.search(text, (a.i("limit") ?: 10).coerceIn(1, 30)) ?: return@withTimeoutOrNull linkFail(ns)
                    ok("Buscó «$text» en NAMSTAT (${r.total})") { put("total", r.total); arr("results", r.items.map { trackJson(it) }) }
                }
                "play" -> done(ns.play(), "NAMSTAT: play")
                "pause" -> done(ns.pause(), "NAMSTAT: pausa")
                "toggle" -> done(ns.toggle(), "NAMSTAT: play/pausa")
                "next" -> done(ns.next(), "NAMSTAT: siguiente")
                "previous" -> done(ns.previous(), "NAMSTAT: anterior")
                "seek" -> { val sec = a.d("seconds") ?: return@withTimeoutOrNull fail("Falta 'seconds'."); done(ns.seek((sec * 1000).toLong().coerceAtLeast(0)), "NAMSTAT: ir a ${sec.toInt()} s") }
                "seek_index" -> { val n = a.i("index") ?: return@withTimeoutOrNull fail("Falta 'index' (posición en la cola, desde 1)."); done(ns.seekIndex((n - 1).coerceAtLeast(0)), "NAMSTAT: saltar a la canción $n") }
                "volume" -> { val v = a.d("value") ?: return@withTimeoutOrNull fail("Falta 'value' (0 a 100)."); done(ns.volume((v / 100.0).coerceIn(0.0, 1.0)), "Volumen de NAMSTAT al ${v.toInt()}%") }
                "shuffle" -> done(ns.shuffle(a.b("on")), "NAMSTAT: aleatorio")
                "repeat" -> done(ns.repeat(a.s("mode")), "NAMSTAT: repetir")
                "speed" -> { val v = a.d("value") ?: return@withTimeoutOrNull fail("Falta 'value' (0.5 a 2)."); done(ns.speed(v), "NAMSTAT: velocidad $v×") }
                "favorite" -> done(ns.favorite(a.b("on"), a.s("key")), "NAMSTAT: favorita")
                "sleep" -> when {
                    a.b("cancel") == true -> done(ns.sleepCancel(), "NAMSTAT: temporizador de sueño cancelado")
                    a.b("at_end") == true -> done(ns.sleepAtEnd(), "NAMSTAT: se pausará al terminar la canción")
                    else -> { val m = a.i("minutes") ?: return@withTimeoutOrNull fail("Falta 'minutes', 'at_end' o 'cancel'."); done(ns.sleepIn(m), "NAMSTAT: se pausará en $m min") }
                }
                "play_playlist" -> { val n = q ?: return@withTimeoutOrNull fail("Falta 'query' con el nombre de la lista."); done(ns.playPlaylist(name = n, shuffle = shuffle), "NAMSTAT: lista «$n»") }
                "play_favorites" -> done(ns.playFavorites(shuffle), "NAMSTAT: tus favoritas")
                "play_recent" -> done(ns.playRecent(shuffle), "NAMSTAT: lo reciente")
                "play_search" -> { val n = q ?: return@withTimeoutOrNull fail("Falta 'query'."); done(ns.playSearch(n, shuffle), "NAMSTAT: «$n»") }
                "play_album" -> { val n = q ?: return@withTimeoutOrNull fail("Falta 'query' con el álbum."); done(ns.playAlbum(n, shuffle), "NAMSTAT: álbum «$n»") }
                "play_artist" -> { val n = q ?: return@withTimeoutOrNull fail("Falta 'query' con el artista."); done(ns.playArtist(n, shuffle), "NAMSTAT: artista «$n»") }
                "play_track" -> { val k = a.s("key") ?: return@withTimeoutOrNull fail("Falta 'key' (sale de search)."); done(ns.playTrack(k), "NAMSTAT: canción") }
                "enqueue" -> {
                    if (a.s("key") == null && q == null) return@withTimeoutOrNull fail("Falta 'key' o 'query'.")
                    done(ns.enqueue(a.s("key"), q, a.b("next") ?: false), "NAMSTAT: agregada a la cola")
                }
                else -> fail("Acción no válida.")
            }
        }
    }
    return out ?: fail("NAMSTAT no contestó a tiempo. Puede que la orden sí se haya hecho: revisa con action=state.")
}

private fun done(r: LinkResult, summary: String): ToolOutcome =
    if (r.ok) ok(if (r.json["opened"] != null) "$summary (abrí NAMSTAT)" else summary) {
        if (r.json["opened"] != null) put("note", "NAMSTAT estaba cerrado: lo abrí con la orden; tarda unos segundos en hacerla.")
        for (k in listOf("name", "count", "added", "fav", "shuffle", "repeat", "speed", "volume")) r.json[k]?.let { put(k, it) }
    } else fail(r.message.ifEmpty { "NAMSTAT no pudo hacerlo (${r.error})." })

private fun linkFail(ns: NamstatClient): ToolOutcome = fail(
    when (ns.linkState) {
        NsLinkState.Closed -> "NAMSTAT está cerrado: el usuario debe abrirlo (o usa una orden de reproducir, que lo abre)."
        NsLinkState.NoLink -> "Esta versión de NAMSTAT no tiene el Enlace T-A-T: hay que actualizar NAMSTAT a la 1.2 o más nueva."
        NsLinkState.Disabled -> "El enlace está apagado en NAMSTAT (Ajustes › Aplicación)."
        else -> ns.message.ifEmpty { "NAMSTAT no contesta." }
    },
)

internal val namstatTools: List<BotTool> = listOf(
    tool(
        "namstat",
        "Control TOTAL de NAMSTAT (el reproductor del ecosistema T-A-T) por el Enlace T-A-T; requiere el conector 'namstat' conectado y NAMSTAT 1.2+. " +
            "Lectura: state (canción con año/género, posición, volumen, aleatorio, repetir, temporizador y lo que sigue), queue, playlists, stats (escucha de hoy/semana, racha, lo más escuchado), search (query). " +
            "Control: play, pause, toggle, next, previous, seek (seconds), seek_index (index desde 1), volume (value 0-100, volumen de NAMSTAT), shuffle (on), repeat (mode off|all|one), speed (value), favorite (on, key opcional), sleep (minutes | at_end | cancel). " +
            "Poner música: play_playlist (query = nombre), play_favorites, play_recent, play_search (query), play_album (query), play_artist (query), play_track (key de search), enqueue (key o query, next). " +
            "Si NAMSTAT está cerrado, las órdenes de reproducir lo abren y se hacen al arrancar. Para pausar/reanudar la música de otra app usa music_control.",
        S.obj(
            listOf(
                "action" to S.str("Qué hacer.", NS_ACTIONS),
                "query" to S.str("Texto a buscar, o nombre de la lista, álbum o artista."),
                "key" to S.str("Clave de una canción (la dan search, queue y state)."),
                "value" to S.num("Número: volumen (0-100) o velocidad (0.5-2)."),
                "seconds" to S.num("Segundo al que saltar (seek)."),
                "index" to S.int("Posición en la cola (desde 1) para seek_index."),
                "minutes" to S.int("Minutos del temporizador de sueño."),
                "on" to S.bool("Para shuffle y favorite: true/false (omitido = alterna)."),
                "shuffle" to S.bool("Al poner música: reproducir en aleatorio."),
                "next" to S.bool("En enqueue: ponerla justo después de la actual."),
                "mode" to S.str("Para repeat.", listOf("off", "all", "one")),
                "at_end" to S.bool("En sleep: pausar al terminar la canción."),
                "cancel" to S.bool("En sleep: cancelar el temporizador."),
                "from" to S.int("Desde qué posición (queue)."),
                "limit" to S.int("Cuántos resultados (queue/search)."),
            ),
            listOf("action"),
        ),
    ) { a, c ->
        val hub = c.host.connectors() ?: return@tool fail("Los conectores no están disponibles desde aquí.")
        namstatAction(hub, a)
    },
)
