package app.fitstat.conn

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.fitstat.data.Repo
import app.fitstat.data.Sleep
import app.fitstat.data.SleepSession
import app.fitstat.data.awaitWallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Un conector: una app o servicio que se puede conectar a FITSTAT. */
class ConnectorDef(
    val id: String,
    val name: String,
    val tagline: String,
    val about: String,
    /** Nombre de un ícono (ver icOf). */
    val icon: String,
    val group: String,
) {
    /** Ajuste que lo enciende o apaga. */
    val enabledKey: String get() = "conn.$id.enabled"
}

/** Lista de conectores. Si agregas uno, FITBOT lo conoce solo (ver Prompt y las herramientas de conectores). */
object Connectors {
    const val NAMIDA = "namida"
    const val SPOTIFY = "spotify"
    const val YTMUSIC = "ytmusic"
    const val NAMSTAT = "namstat"
    const val HEALTH = "health"
    const val PHONE = "phone"

    val all: List<ConnectorDef> = listOf(
        ConnectorDef(
            NAMIDA, "Namida", "Música: se pausa en tus descansos y se controla desde el inicio",
            "Namida es un reproductor de música de código abierto. Conectado, FITSTAT pausa la música cuando empieza el descanso entre series, la reanuda subiendo el volumen poco a poco cuando termina, y te deja controlarla (play, pausa, siguiente, anterior) con un widget del inicio que muestra la carátula.",
            "Music", "Música",
        ),
        ConnectorDef(
            SPOTIFY, "Spotify", "Música: se pausa en tus descansos y se controla desde el inicio",
            "Conectado, FITSTAT pausa lo que suena en Spotify cuando empieza el descanso entre series, lo reanuda subiendo el volumen poco a poco cuando termina, y te deja controlarlo (play, pausa, siguiente, anterior) con el widget de música del inicio, que muestra la portada. Funciona con la app de Spotify de tu teléfono (no hace falta Premium para esto, pero Spotify decide qué deja saltar en cuentas gratuitas).",
            "Music", "Música",
        ),
        ConnectorDef(
            YTMUSIC, "YouTube Music", "Música: se pausa en tus descansos y se controla desde el inicio",
            "Conectado, FITSTAT pausa lo que suena en YouTube Music cuando empieza el descanso entre series, lo reanuda subiendo el volumen poco a poco cuando termina, y te deja controlarlo (play, pausa, siguiente, anterior) con el widget de música del inicio, que muestra la portada. Funciona con la app YouTube Music de tu teléfono.",
            "Music", "Música",
        ),
        ConnectorDef(
            NAMSTAT, "NAMSTAT", "Música del ecosistema T-A-T: control total, cola, listas y estadísticas",
            "NAMSTAT es el reproductor de música de código abierto hecho desde cero que comparte ecosistema con FITSTAT (T-A-T). Como las dos apps son tuyas, aquí no hay límites: además de ver y controlar la sesión (play, pausa, siguiente, anterior), FITSTAT habla directo con NAMSTAT por el Enlace T-A-T y ve la canción con su año, género y formato, la cola, tus listas, tus favoritas y tus estadísticas de escucha; elige volumen propio, velocidad, repetir y aleatorio; busca y reproduce canciones, álbumes, artistas o listas; marca favoritas; programa el temporizador de sueño; y reanuda tras el descanso con un fundido de volumen hecho por NAMSTAT mismo, sin tocar el volumen del teléfono. Si NAMSTAT está cerrado, FITSTAT lo abre con la orden. Cuando entrenas puede arrancar una lista tuya. Necesita NAMSTAT 1.2 o más nueva.",
            "Music", "Música",
        ),
        ConnectorDef(
            HEALTH, "Health Connect", "Sueño con fases, de tu reloj o pulsera",
            "Health Connect es el almacén de salud de Android. Si tu reloj o pulsera (Samsung, Fitbit, Garmin, Xiaomi, Pixel Watch…) guarda ahí tu sueño, FITSTAT lo lee y te muestra cada noche con sus fases: ligero, profundo y REM. Solo lee sueño; no escribe nada ni lo manda a ningún lado.",
            "HeartPulse", "Salud y sueño",
        ),
        ConnectorDef(
            PHONE, "Uso del teléfono", "Estima cuántas horas duermes, sin reloj",
            "Sin reloj ni pulsera no hay forma de saber en qué fase del sueño estás. Lo que sí se puede es estimar cuántas horas dormiste: FITSTAT busca cada madrugada el tramo más largo en que no tocaste el teléfono. Es solo una estimación de horas (sin fases) y se marca siempre como «estimado».",
            "Phone", "Salud y sueño",
        ),
    )

    fun def(id: String): ConnectorDef? = all.firstOrNull { it.id == id }

    /** Encuentra un conector por su id o por su nombre (sin importar mayúsculas ni acentos). */
    fun find(ref: String): ConnectorDef? {
        val n = app.fitstat.data.Search.norm(ref)
        if (n.isEmpty()) return null
        return all.firstOrNull { it.id == n || app.fitstat.data.Search.norm(it.name) == n }
            ?: all.firstOrNull { app.fitstat.data.Search.norm(it.name).contains(n) && n.length >= 3 }
            ?: if (n.contains("salud") || n.contains("health") || n.contains("reloj")) def(HEALTH)
            else if (n.contains("telefono") || n.contains("uso")) def(PHONE)
            else if (n.contains("youtube") || n.contains("yt")) def(YTMUSIC)
            else if (n.contains("nam") && n.contains("stat")) def(NAMSTAT)
            else if (n.contains("musica") || n.contains("music")) def(NAMIDA) else null
    }
}

enum class ConnStatus(val label: String) {
    Off("Sin conectar"),
    Missing("No disponible"),
    NeedsAccess("Falta un permiso"),
    Ready("Conectado"),
}

enum class HealthAvail { Unavailable, NeedsInstall, Available }

enum class ConnOpen { NotificationAccess, UsageAccess, AppSettings, HealthSettings, HealthInstall, Namida, Spotify, YtMusic, Namstat }

/** Qué abrir para lanzar la app de música de un conector. */
fun musicOpen(id: String): ConnOpen = when (id) {
    Connectors.SPOTIFY -> ConnOpen.Spotify
    Connectors.YTMUSIC -> ConnOpen.YtMusic
    Connectors.NAMSTAT -> ConnOpen.Namstat
    else -> ConnOpen.Namida
}

/** Lo que los conectores necesitan del teléfono. Es una interfaz para poder probar sin Android de verdad. */
interface ConnPlatform {
    fun installed(pkg: String): Boolean
    fun notificationAccess(): Boolean
    fun usageSupported(): Boolean
    fun usageAccess(): Boolean
    fun healthAvail(): HealthAvail
    suspend fun healthGranted(): Boolean
    suspend fun readHealthSleep(fromMs: Long, toMs: Long): List<SleepSession>
    suspend fun screenEvents(fromMs: Long, toMs: Long): List<app.fitstat.data.ScreenEvent>
    fun open(what: ConnOpen): Boolean
}

/** Lo que se sabe de los permisos y apps, para que la pantalla se redibuje cuando cambia. */
class ConnSnapshot(
    val namidaInstalled: Boolean = false,
    val notificationAccess: Boolean = false,
    val usageSupported: Boolean = true,
    val usageAccess: Boolean = false,
    val health: HealthAvail = HealthAvail.Unavailable,
    val healthGranted: Boolean = false,
    val spotifyInstalled: Boolean = false,
    val ytmusicInstalled: Boolean = false,
    val namstatInstalled: Boolean = false,
) {
    /** ¿Está instalada la app de música de este conector? */
    fun musicInstalled(id: String): Boolean = when (id) {
        Connectors.SPOTIFY -> spotifyInstalled
        Connectors.YTMUSIC -> ytmusicInstalled
        Connectors.NAMSTAT -> namstatInstalled
        else -> namidaInstalled
    }
}

class SleepSyncResult(val ok: Boolean, val newNights: Int, val message: String)

/**
 * Todo lo de los conectores en un solo lugar: estado, permisos, conectar y desconectar, la música durante el descanso y
 * la sincronización del sueño. La pantalla de Conectores, el inicio y FITBOT usan esto.
 */
@Stable
class ConnectorHub(
    val repo: Repo,
    private val scope: CoroutineScope,
    val platform: ConnPlatform,
    val music: MusicPlayer,
    volume: VolumeControl,
    fadeStore: FadeStore = FadeStore.Memory(),
    /** Prende (o reinicia, si cambió qué apps están activas) o apaga la escucha de la música (la implementación real arranca [NamidaPlayer]). */
    private val musicSwitch: (Boolean) -> Unit = {},
    /** El Enlace T-A-T con NAMSTAT (el conector de música «sin límites»). */
    val link: NamstatLink = NoNamstatLink,
) {
    var snapshot: ConnSnapshot by mutableStateOf(ConnSnapshot())
        private set

    /** Pide el permiso de Health Connect (lo conecta la actividad principal). */
    var healthRequest: (() -> Unit)? = null

    val rest = MusicRestCoordinator(music, volume, { repo.prefs }, scope, fadeStore, link.takeIf { it !== NoNamstatLink })

    /** Todo lo que NAMSTAT deja ver y hacer (estado, cola, listas, estadísticas, búsqueda y comandos). */
    val namstat = NamstatClient(link, scope) { repo.now() }

    var syncing: Boolean by mutableStateOf(false)
        private set
    var lastSync: Long by mutableStateOf(0L)
        private set
    var lastSyncMessage: String by mutableStateOf("")
        private set

    private var watching = false

    init {
        rest.recover()
        refresh()
        // La escucha va encendida solo si algún conector de música está activado y Android dio el acceso;
        // si cambia cuáles están activos, se reinicia para atender solo esas apps.
        scope.launch {
            snapshotFlow { MusicApps.ids.filter { repo.prefs.bool("conn.$it.enabled") }.toSet() to snapshot.notificationAccess }.distinctUntilChanged().collect { (ids, access) ->
                val on = ids.isNotEmpty() && access
                musicSwitch(on)
                if (!on) rest.reset()
            }
        }
    }

    fun enabled(id: String): Boolean = repo.prefs.bool("conn.$id.enabled")

    fun status(id: String): ConnStatus {
        if (!enabled(id)) return ConnStatus.Off
        val s = snapshot
        return when (id) {
            Connectors.NAMIDA, Connectors.SPOTIFY, Connectors.YTMUSIC, Connectors.NAMSTAT -> when {
                !s.musicInstalled(id) -> ConnStatus.Missing
                !s.notificationAccess -> ConnStatus.NeedsAccess
                else -> ConnStatus.Ready
            }
            Connectors.HEALTH -> when {
                s.health != HealthAvail.Available -> ConnStatus.Missing
                !s.healthGranted -> ConnStatus.NeedsAccess
                else -> ConnStatus.Ready
            }
            Connectors.PHONE -> when {
                !s.usageSupported -> ConnStatus.Missing
                !s.usageAccess -> ConnStatus.NeedsAccess
                else -> ConnStatus.Ready
            }
            else -> ConnStatus.Off
        }
    }

    /** Qué falta para que funcione, en una frase (null si no falta nada o no está conectado). */
    fun missing(id: String): String? {
        val s = snapshot
        return when (status(id)) {
            ConnStatus.Missing -> when (id) {
                Connectors.NAMIDA, Connectors.SPOTIFY, Connectors.YTMUSIC, Connectors.NAMSTAT -> "No encuentro ${Connectors.def(id)?.name} instalada en este teléfono."
                Connectors.HEALTH -> if (s.health == HealthAvail.NeedsInstall) "Health Connect necesita instalarse o actualizarse." else "Este teléfono no tiene Health Connect."
                else -> "Esta función necesita Android 9 o más nuevo."
            }
            ConnStatus.NeedsAccess -> when (id) {
                Connectors.NAMIDA, Connectors.SPOTIFY, Connectors.YTMUSIC, Connectors.NAMSTAT -> "Falta darle a FITSTAT el «Acceso a notificaciones» para ver y controlar lo que suena en ${Connectors.def(id)?.name}."
                Connectors.HEALTH -> "Falta darle permiso a FITSTAT para leer tu sueño en Health Connect."
                else -> "Falta darle a FITSTAT el «Acceso al uso de apps» para saber cuándo usas el teléfono."
            }
            else -> null
        }
    }

    private fun readBase(): ConnSnapshot {
        val p = platform
        return ConnSnapshot(
            namidaInstalled = Namida.packages.any { p.installed(it) },
            spotifyInstalled = MusicApps.byId(Connectors.SPOTIFY)!!.packages.any { p.installed(it) },
            ytmusicInstalled = MusicApps.byId(Connectors.YTMUSIC)!!.packages.any { p.installed(it) },
            namstatInstalled = MusicApps.byId(Connectors.NAMSTAT)!!.packages.any { p.installed(it) },
            notificationAccess = p.notificationAccess(),
            usageSupported = p.usageSupported(),
            usageAccess = p.usageAccess(),
            health = p.healthAvail(),
            healthGranted = snapshot.healthGranted,
        )
    }

    private fun ConnSnapshot.granted(g: Boolean) = ConnSnapshot(namidaInstalled, notificationAccess, usageSupported, usageAccess, health, g, spotifyInstalled, ytmusicInstalled, namstatInstalled)

    /** Vuelve a revisar permisos y apps (al volver de los ajustes del sistema, por ejemplo). */
    fun refresh() {
        val base = readBase()
        snapshot = base
        if (base.health == HealthAvail.Available) {
            scope.launch {
                val g = runCatching { platform.healthGranted() }.getOrDefault(false)
                if (g != snapshot.healthGranted) snapshot = snapshot.granted(g)
            }
        }
    }

    /** Igual que [refresh], pero espera a saber si Health Connect ya dio el permiso (para sincronizar justo después de darlo). */
    suspend fun refreshAwait() {
        val base = readBase()
        snapshot = base
        if (base.health == HealthAvail.Available) {
            val g = runCatching { platform.healthGranted() }.getOrDefault(false)
            snapshot = snapshot.granted(g)
        }
    }

    /** Solo lo activa (sin abrir ninguna pantalla del sistema). */
    fun enable(id: String): ConnStatus {
        repo.setSetting("conn.$id.enabled", "true")
        refresh()
        return status(id)
    }

    /** Conecta: lo activa y, si falta algún permiso, abre la pantalla del sistema donde se da. */
    fun connect(id: String): ConnStatus {
        enable(id)
        requestAccess(id)
        return status(id)
    }

    fun disconnect(id: String) {
        repo.setSetting("conn.$id.enabled", "false")
        if (MusicApps.isMusic(id)) rest.reset()
        if (id == Connectors.NAMSTAT) namstat.stopPolling()
    }

    /** Abre donde se da el permiso que falta. Devuelve false si no hay nada que pedir. */
    fun requestAccess(id: String): Boolean = when (status(id)) {
        ConnStatus.NeedsAccess -> when (id) {
            Connectors.NAMIDA, Connectors.SPOTIFY, Connectors.YTMUSIC, Connectors.NAMSTAT -> platform.open(ConnOpen.NotificationAccess)
            Connectors.PHONE -> platform.open(ConnOpen.UsageAccess)
            Connectors.HEALTH -> { healthRequest?.invoke() ?: platform.open(ConnOpen.HealthSettings); true }
            else -> false
        }
        ConnStatus.Missing -> if (id == Connectors.HEALTH && snapshot.health == HealthAvail.NeedsInstall) platform.open(ConnOpen.HealthInstall) else false
        else -> false
    }

    // ───────────────────────── Música (Namida, Spotify, YouTube Music)

    /** Conectores de música que están listos (conectados y con permiso). */
    fun musicReadyIds(): List<String> = MusicApps.ids.filter { status(it) == ConnStatus.Ready }

    /** Hay alguna app de música lista. */
    fun musicReady(): Boolean = musicReadyIds().isNotEmpty()

    /** Estado global de la música para el widget: lista si alguna lo está; si no, lo más cercano a estarlo. */
    fun musicStatus(): ConnStatus {
        val all = MusicApps.ids.map { status(it) }
        return when {
            ConnStatus.Ready in all -> ConnStatus.Ready
            ConnStatus.NeedsAccess in all -> ConnStatus.NeedsAccess
            ConnStatus.Missing in all -> ConnStatus.Missing
            else -> ConnStatus.Off
        }
    }

    /** El conector de música «actual»: el de la app que suena, o el único listo, o el primero activado. */
    fun musicCurrent(): String {
        val s = music.state
        if (s.connected && s.source.isNotEmpty() && status(s.source) == ConnStatus.Ready) return s.source
        return musicReadyIds().firstOrNull() ?: MusicApps.ids.firstOrNull { enabled(it) } ?: Connectors.NAMIDA
    }

    /** Nombre de la app de música actual. */
    fun musicName(): String = Connectors.def(musicCurrent())?.name ?: "Música"

    // ───────────────────────── Música durante el descanso

    /**
     * Vigila el descanso del entrenamiento: [restEndsAt] da el momento en que termina (0 = no hay descanso).
     * Al empezar pausa la música y, al terminar (o al saltarlo o cancelar el entrenamiento), la reanuda.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watchRest(restEndsAt: () -> Long, now: () -> Long) {
        if (watching) return
        watching = true
        scope.launch {
            snapshotFlow(restEndsAt).collectLatest { end ->
                if (end > 0L) {
                    rest.onRestStart()
                    awaitWallClock(end, now)
                    rest.onRestEnd()
                } else {
                    rest.onRestEnd()
                }
            }
        }
    }

    // ───────────────────────── NAMSTAT durante el entrenamiento

    private var watchingWorkout = false

    /**
     * Vigila el entrenamiento: [session] da su nombre mientras hay uno en curso (null = no hay). Con NAMSTAT conectado le avisa
     * de cuándo empieza y termina, y si hay una «Lista para entrenar» la pone al empezar (si no estaba sonando música).
     */
    fun watchWorkout(session: () -> String?) {
        if (watchingWorkout) return
        watchingWorkout = true
        scope.launch {
            var before: String? = null
            snapshotFlow(session).distinctUntilChanged().collect { name ->
                val started = name != null && before == null
                val ended = name == null && before != null
                val endedName = before
                before = name
                if (!enabled(Connectors.NAMSTAT) || status(Connectors.NAMSTAT) != ConnStatus.Ready) return@collect
                val p = repo.prefs
                if (started && p.bool("conn.namstat.workout_sync")) runCatching { namstat.workout(true, name.orEmpty()) }
                if (ended && p.bool("conn.namstat.workout_sync")) runCatching { namstat.workout(false, endedName.orEmpty()) }
                if (started) startWorkoutList()
            }
        }
    }

    /** Pone la «Lista para entrenar» de NAMSTAT si hay una configurada y no suena nada. Devuelve true si la mandó. */
    suspend fun startWorkoutList(): Boolean {
        val p = repo.prefs
        val list = p.str("conn.namstat.workout_list").trim()
        if (list.isEmpty()) return false
        if (music.state.connected && music.state.playing) return false
        val r = runCatching { namstat.playPlaylist(name = list, shuffle = p.bool("conn.namstat.workout_shuffle")) }.getOrNull()
        return r?.ok == true
    }

    // ───────────────────────── Sueño

    /** Trae las noches nuevas si el ajuste lo permite y hace rato que no se actualiza (o si [force]). */
    fun autoSync(force: Boolean = false) {
        if (syncing) return
        if (!force && (!repo.prefs.bool("sleep.auto_sync") || repo.now() - lastSync < 10 * 60_000L)) return
        scope.launch { syncSleep() }
    }

    /** Conectores de sueño listos para traer datos. */
    fun sleepSources(): List<String> = listOf(Connectors.HEALTH, Connectors.PHONE).filter { status(it) == ConnStatus.Ready }

    /** Trae las noches nuevas de Health Connect y del teléfono (las que estén conectadas). */
    suspend fun syncSleep(): SleepSyncResult {
        if (syncing) return SleepSyncResult(false, 0, "Ya estoy actualizando.")
        syncing = true
        try {
            refreshAwait()
            val sources = sleepSources()
            if (sources.isEmpty()) {
                val msg = "No hay ningún conector de sueño listo. Conecta Health Connect (si tienes reloj) o «Uso del teléfono» en Ajustes › Conectores."
                lastSyncMessage = msg
                return SleepSyncResult(false, 0, msg)
            }
            val now = repo.now()
            var added = 0
            val notes = ArrayList<String>()
            for (id in sources) {
                try {
                    when (id) {
                        Connectors.HEALTH -> {
                            val found = platform.readHealthSleep(now - 30L * 24 * 3600_000L, now)
                            val n = repo.mergeSleep(found)
                            added += n
                            notes += if (found.isEmpty()) "Health Connect: no hay noches registradas en los últimos 30 días." else "Health Connect: ${found.size} noches leídas."
                        }
                        Connectors.PHONE -> {
                            val events = platform.screenEvents(now - 8L * 24 * 3600_000L, now)
                            val gap = (repo.prefs.float("sleep.phone_gap") * 3600_000f).toLong()
                            val est = Sleep.estimateFromScreen(events, repo.zone, gap)
                            val n = repo.mergeSleep(est)
                            added += n
                            notes += if (est.isEmpty()) "Teléfono: no encontré un tramo largo sin usarlo en los últimos días." else "Teléfono: ${est.size} noches estimadas."
                        }
                    }
                } catch (e: Throwable) {
                    notes += "${Connectors.def(id)?.name ?: id}: no pude leer (${e.message ?: "error"})."
                }
            }
            lastSync = now
            val msg = notes.joinToString(" ")
            lastSyncMessage = msg
            return SleepSyncResult(true, added, msg)
        } finally {
            syncing = false
        }
    }

    companion object {
        /** El conjunto real: Namida, Spotify y YouTube Music por MediaSession, volumen del teléfono, Health Connect y estadísticas de uso. */
        fun create(ctx: Context, repo: Repo, scope: CoroutineScope): ConnectorHub {
            val app = ctx.applicationContext
            val namida = NamidaPlayer(app, scope, allowed = { MusicApps.ids.filter { repo.prefs.bool("conn.$it.enabled") }.toSet() })
            return ConnectorHub(
                repo, scope, AndroidConnPlatform(app), namida, AndroidVolume(app), PrefsFadeStore(app),
                musicSwitch = { on -> if (on) namida.start() else namida.stop() },
                link = AndroidNamstatLink(app),
            )
        }
    }
}
