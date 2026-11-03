package app.fitstat.bot

import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorDef
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Connectors
import app.fitstat.conn.MusicState
import app.fitstat.conn.RhythmMode
import app.fitstat.data.Fmt
import app.fitstat.data.Pages
import app.fitstat.data.Settings
import app.fitstat.data.Sleep
import app.fitstat.data.SleepNight
import app.fitstat.data.SleepSrc
import app.fitstat.data.SleepStageType
import app.fitstat.ui.app.Route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val NO_HUB = "Los conectores no están disponibles desde aquí."

private fun ToolCtx.hub(): ConnectorHub? = host.connectors()

private fun statusCode(s: ConnStatus) = when (s) {
    ConnStatus.Off -> "off"
    ConnStatus.Missing -> "missing"
    ConnStatus.NeedsAccess -> "needs_permission"
    ConnStatus.Ready -> "ready"
}

private fun connectorJson(d: ConnectorDef, hub: ConnectorHub, c: ToolCtx): JsonObject = buildJsonObject {
    val st = hub.status(d.id)
    put("id", d.id)
    put("name", d.name)
    put("what_it_does", d.about)
    put("connected", hub.enabled(d.id))
    put("status", statusCode(st))
    put("status_label", st.label)
    hub.missing(d.id)?.let { put("missing", it) }
    arr("settings", Settings.specsOf(Pages.CONNECTORS).filter { it.key.startsWith("conn.${d.id}.") && it.key != d.enabledKey }.map { settingJson(it, c) })
    when (d.id) {
        Connectors.NAMIDA, Connectors.SPOTIFY, Connectors.YTMUSIC -> {
            val s = hub.music.state.let { if (it.source.ifEmpty { Connectors.NAMIDA } == d.id) it else MusicState.None }
            put("app_open", s.connected)
            if (s.hasTrack) {
                put("now_playing", s.title)
                put("artist", s.artist)
                put("playing", s.playing)
            }
        }
        Connectors.NAMSTAT -> {
            val s = hub.music.state.let { if (it.source == Connectors.NAMSTAT) it else MusicState.None }
            put("app_open", s.connected)
            put("link", hub.namstat.linkState.label)
            if (s.hasTrack) { put("now_playing", s.title); put("artist", s.artist); put("playing", s.playing) }
            put("note", "Con este conector usa la herramienta 'namstat' para cola, listas, estadísticas, búsqueda y control total.")
        }
        Connectors.HEALTH -> put("nights_stored", c.repo.sleep.count { it.source == SleepSrc.HEALTH })
        Connectors.PHONE -> put("nights_stored", c.repo.sleep.count { it.source == SleepSrc.PHONE })
    }
}

private fun nightJson(n: SleepNight, c: ToolCtx): JsonObject = buildJsonObject {
    val s = n.main
    val zone = c.repo.zone
    put("wake_date", n.date.toString())
    put("bedtime", Fmt.hourMinute(s.start, zone))
    put("wake_time", Fmt.hourMinute(s.end, zone))
    put("asleep", Sleep.fmt(Sleep.asleepMs(s)))
    put("asleep_minutes", Sleep.asleepMs(s) / 60_000L)
    put("source", s.source)
    put("estimated", s.source == SleepSrc.PHONE)
    if (Sleep.hasStages(s)) {
        val t = Sleep.stageTotals(s)
        fun mins(k: String) = (t[k] ?: 0L) / 60_000L
        put("deep_minutes", mins(SleepStageType.DEEP))
        put("rem_minutes", mins(SleepStageType.REM))
        put("light_minutes", mins(SleepStageType.LIGHT))
        put("awake_minutes", mins(SleepStageType.AWAKE))
    }
    if (n.extra.isNotEmpty()) strs("other_rests", n.extra.map { Sleep.fmt(Sleep.durationMs(it)) })
}

internal val connectorTools: List<BotTool> = listOf(
    tool(
        "get_music_fx",
        "Estado de las animaciones con la música (Ajustes › Animaciones de música): un fondo que late al ritmo detrás de la meta semanal y del widget de música, y la portada que vibra sutilmente. " +
            "Dice si están activas, el estilo de cada tarjeta, de dónde sale el ritmo (audio real del teléfono o aproximado a un tempo fijo, y por qué) y si falta el permiso de audio, que solo da el usuario. " +
            "Estilos: " + Settings.FX_STYLES.options.joinToString(", ") { "${it.value} (${it.label})" } + ". Se cambian con set_settings (claves 'fx.*', page 'musica_fx').",
    ) { _, c ->
        val p = c.repo.prefs
        val eng = c.host.rhythm()
        ok("Revisó las animaciones de la música") {
            put("enabled", p.bool("fx.enabled"))
            put("goal_card", p.bool("fx.goal.on"))
            put("goal_style", p.str("fx.goal.style"))
            put("music_widget", p.bool("fx.music.on"))
            put("music_style", p.str("fx.music.style"))
            put("cover_vibration", p.bool("fx.cover"))
            if (eng != null) {
                put("rhythm_source", when (eng.mode) { RhythmMode.Real -> "real"; RhythmMode.Approx -> "approximate"; RhythmMode.Off -> "idle (no music playing)" })
                put("source_setting", p.str("fx.source"))
                put("audio_permission_granted", eng.permission)
                if (eng.why.label.isNotBlank()) put("why_approximate", eng.why.label)
            }
            put("note", "Real = mide el audio del teléfono (necesita el permiso y que Android lo entregue). Aproximado = ritmo a tempo fijo (fx.bpm) que no sigue la canción exacta. Si el usuario quiere ritmo real, dile que dé el permiso en Ajustes › Animaciones de música.")
            arr("settings", Settings.specsOf(Pages.MUSICFX).map { settingJson(it, c) })
        }
    },

    tool(
        "list_connectors",
        "Lista los conectores de FITSTAT (Ajustes › Conectores): apps y servicios que se conectan a la app. Para cada uno: si está conectado, su estado (ready, needs_permission, missing u off), qué falta y sus opciones. " +
            "Conectores: " + Connectors.all.joinToString("; ") { "${it.id} = ${it.name} (${it.tagline})" } + ". Sus opciones se cambian con set_settings (claves 'conn.<id>.*').",
    ) { _, c ->
        val hub = c.hub() ?: return@tool fail(NO_HUB)
        hub.refresh()
        ok("Revisó los conectores") { arr("connectors", Connectors.all.map { connectorJson(it, hub, c) }) }
    },

    tool(
        "connect_connector",
        "Conecta o desconecta un conector. Al conectar, si Android necesita un permiso (acceso a notificaciones para Namida, permiso de Health Connect, acceso al uso de apps para el teléfono) " +
            "se abre la pantalla del conector para que el USUARIO lo dé él mismo: tú no puedes darlo. Después revisa el estado con list_connectors.",
        S.obj(
            listOf(
                "connector" to S.str("Id o nombre: " + Connectors.all.joinToString(", ") { it.id } + "."),
                "connect" to S.bool("true = conectar (por defecto), false = desconectar."),
            ),
            listOf("connector"),
        ),
        write = true,
        plan = { a, _ -> (if (a.b("connect") == false) "Desconectar " else "Conectar ") + (Connectors.find(a.s("connector").orEmpty())?.name ?: a.s("connector").orEmpty()) },
    ) { a, c ->
        val hub = c.hub() ?: return@tool fail(NO_HUB)
        val d = Connectors.find(a.s("connector").orEmpty()) ?: return@tool fail("No existe ese conector. Opciones: ${Connectors.all.joinToString { it.id + " (" + it.name + ")" }}.")
        if (a.b("connect") == false) {
            hub.disconnect(d.id)
            return@tool ok("Desconectó ${d.name}") { put("connector", d.id); put("status", statusCode(hub.status(d.id))) }
        }
        val st = hub.enable(d.id)
        if (st == ConnStatus.NeedsAccess || st == ConnStatus.Missing) c.host.navigate(Route.Connector(d.id))
        val msg = when (st) {
            ConnStatus.Ready -> "Conectó ${d.name}"
            ConnStatus.NeedsAccess -> "Activó ${d.name}; falta que el usuario dé un permiso"
            ConnStatus.Missing -> "Activó ${d.name}, pero no está disponible"
            ConnStatus.Off -> "No se pudo conectar ${d.name}"
        }
        ok(msg) {
            put("connector", d.id)
            put("status", statusCode(st))
            hub.missing(d.id)?.let { put("missing", it); put("next_step", "Abrí la pantalla de ${d.name}: dile al usuario que toque «Dar permiso» y luego regrese a FITSTAT.") }
        }
    },

    tool(
        "get_music",
        "Qué está sonando ahora en la app de música conectada (Namida, Spotify, YouTube Music o NAMSTAT): canción, artista y si está en pausa, y si el conector de música está listo.",
    ) { _, c ->
        val hub = c.hub() ?: return@tool fail(NO_HUB)
        val cur = hub.musicCurrent()
        val st = hub.musicStatus()
        val s = hub.music.state
        ok("Revisó la música") {
            put("connector_status", statusCode(st))
            put("app", hub.musicName())
            put("ready_apps", JsonArray(hub.musicReadyIds().map { JsonPrimitive(it) }))
            hub.missing(cur)?.let { put("missing", it) }
            put("app_open", s.connected)
            if (s.hasTrack) {
                put("title", s.title)
                put("artist", s.artist)
                put("album", s.album)
                put("playing", s.playing)
            }
        }
    },

    tool(
        "music_control",
        "Controla la música de la app conectada (Namida, Spotify, YouTube Music o NAMSTAT): play, pause, toggle (alterna), next (siguiente) o previous (anterior). Atiende la app que esté sonando. Solo funciona si hay un conector de música listo y esa app está abierta (FITSTAT no puede arrancarla).",
        S.obj(listOf("action" to S.str("Acción.", listOf("play", "pause", "toggle", "next", "previous"))), listOf("action")),
    ) { a, c ->
        val hub = c.hub() ?: return@tool fail(NO_HUB)
        val cur = hub.musicCurrent()
        val nm = hub.musicName()
        if (!hub.musicReady()) return@tool fail(hub.missing(cur) ?: "No hay ninguna app de música conectada (Namida, Spotify, YouTube Music o NAMSTAT). Usa connect_connector.")
        val m = hub.music
        if (!m.state.connected) return@tool fail("$nm no está abierta o no hay nada cargado: el usuario tiene que abrirla y darle play una vez.")
        val act = a.s("action")?.lowercase()
        when (act) {
            "play" -> m.play()
            "pause" -> m.pause()
            "toggle" -> if (m.state.playing) m.pause() else m.play()
            "next", "siguiente" -> m.next()
            "previous", "anterior" -> m.previous()
            else -> return@tool fail("Acción no válida: usa play, pause, toggle, next o previous.")
        }
        ok("$nm: $act") { put("was_playing", m.state.playing); put("track", m.state.title) }
    },

    tool(
        "get_sleep",
        "Cómo ha dormido el usuario: las últimas noches (hora de dormir y despertar, tiempo dormido, fase si hay) y el resumen (promedio, regularidad, meta). IMPORTANTE: las fases (ligero, profundo, REM) solo existen si un reloj o pulsera las registró (Health Connect); " +
            "lo estimado con el teléfono son solo horas y se llama «estimado»; nunca afirmes que detectas fases sin reloj. Meta de sueño: ajuste sleep.goal.",
        S.obj(listOf("days" to S.int("Cuántas noches revisar (1-60, por defecto 7)."))),
    ) { a, c ->
        val r = c.repo
        val days = (a.i("days") ?: 7).coerceIn(1, 60)
        val nights = r.sleepNights().take(days)
        val sm = Sleep.summary(nights, r.sleepGoal, r.zone)
        val hub = c.hub()
        ok(if (nights.isEmpty()) "No hay noches registradas" else "Revisó el sueño (${nights.size} noches)") {
            put("goal_hours", r.sleepGoal)
            arr("nights", nights.map { nightJson(it, c) })
            if (nights.isNotEmpty()) {
                put("average_asleep", Sleep.fmt(sm.avgAsleepMs))
                sm.avgBedMin?.let { put("average_bedtime", Sleep.clock(it)) }
                sm.avgWakeMin?.let { put("average_wake_time", Sleep.clock(it)) }
                put("nights_meeting_goal", sm.metGoal)
                sm.regularity?.let { put("regularity", it) }
                put("nights_with_stages", sm.withStages)
            }
            if (hub != null) {
                put("health_connect", statusCode(hub.status(Connectors.HEALTH)))
                put("phone_estimate", statusCode(hub.status(Connectors.PHONE)))
            }
            if (nights.isEmpty()) put("how_to_start", "Conectar Health Connect (si tiene reloj o pulsera), activar el conector «Uso del teléfono» para estimar horas, o registrar una noche con log_sleep.")
        }
    },

    tool(
        "log_sleep",
        "Registra una noche de sueño a mano (lo que registra el usuario manda sobre lo estimado). Indica a qué hora se durmió, a qué hora despertó y el día en que despertó.",
        S.obj(
            listOf(
                "bedtime" to S.str("Hora de dormir, p. ej. '23:30' o '11:30 pm'."),
                "wake_time" to S.str("Hora de despertar, p. ej. '07:00'."),
                "wake_day" to S.str("Día en que despertó: hoy (por defecto), ayer, anteayer o AAAA-MM-DD."),
                "note" to S.str("Nota opcional."),
            ),
            listOf("bedtime", "wake_time"),
        ),
        write = true,
        plan = { a, _ -> "Registrar sueño: ${a.s("bedtime")} → ${a.s("wake_time")} (${a.s("wake_day") ?: "hoy"})" },
    ) { a, c ->
        val r = c.repo
        val bed = a.s("bedtime")?.let { parseTime(it) } ?: return@tool fail("Hora de dormir no válida (usa por ejemplo 23:30).")
        val wake = a.s("wake_time")?.let { parseTime(it) } ?: return@tool fail("Hora de despertar no válida (usa por ejemplo 07:00).")
        val day = a.s("wake_day")?.let { parseDate(it, r.today()) ?: return@tool fail("Día no válido: usa hoy, ayer, anteayer o AAAA-MM-DD.") } ?: r.today()
        val (start, end) = Sleep.span(day, bed, wake, r.zone)
        r.addSleep(start, end, a.s("note").orEmpty(), label = null).fold(
            onSuccess = { s -> ok("Registró ${Sleep.fmt(Sleep.durationMs(s))} de sueño") { put("id", s.id); put("wake_date", day.toString()); put("asleep", Sleep.fmt(Sleep.durationMs(s))) } },
            onFailure = { fail(it.message ?: "No se pudo registrar la noche.") },
        )
    },

    tool(
        "delete_sleep",
        "Borra una noche registrada a mano (por la fecha en que despertó). Las noches que vienen del reloj o del teléfono no se pueden borrar porque volverían al actualizar; para corregirlas, registra la noche a mano con log_sleep.",
        S.obj(listOf("wake_day" to S.str("Día en que despertó: hoy, ayer, anteayer o AAAA-MM-DD.")), listOf("wake_day")),
        write = true,
        destructive = { true },
        plan = { a, _ -> "Borrar la noche registrada a mano del ${a.s("wake_day")}" },
    ) { a, c ->
        val r = c.repo
        val day = a.s("wake_day")?.let { parseDate(it, r.today()) } ?: return@tool fail("Día no válido: usa hoy, ayer, anteayer o AAAA-MM-DD.")
        val mine = r.sleep.filter { it.source == SleepSrc.MANUAL && java.time.Instant.ofEpochMilli(it.end).atZone(r.zone).toLocalDate() == day }
        if (mine.isEmpty()) return@tool fail("No hay ninguna noche registrada a mano que termine el $day (las del reloj o del teléfono no se pueden borrar).")
        mine.forEach { r.deleteSleep(it.id, label = null) }
        ok("Borró ${mine.size} ${if (mine.size == 1) "noche" else "noches"}") { put("wake_date", day.toString()) }
    },

    tool(
        "sync_sleep",
        "Actualiza el sueño desde los conectores que estén listos (Health Connect y la estimación del teléfono). Tarda unos segundos: después usa get_sleep.",
    ) { _, c ->
        val hub = c.hub() ?: return@tool fail(NO_HUB)
        val sources = hub.sleepSources()
        if (sources.isEmpty()) return@tool fail("No hay ningún conector de sueño listo. Conecta Health Connect (reloj o pulsera) o «Uso del teléfono» con connect_connector.")
        hub.autoSync(force = true)
        ok("Empezó a actualizar el sueño") { strs("sources", sources); put("note", "Las noches nuevas aparecen en unos segundos; revisa con get_sleep.") }
    },
)
