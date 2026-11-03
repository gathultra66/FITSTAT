package app.fitstat.conn

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Paquetes de Namida (la versión normal y la de depuración). */
object Namida {
    const val PACKAGE = "com.msob7y.namida"
    const val DEBUG_PACKAGE = "com.msob7y.namida.debug"
    val packages = listOf(PACKAGE, DEBUG_PACKAGE)
}

/** Una app de música que se conecta a FITSTAT por su sesión de reproducción (cada una es un conector con ese [id]). */
class MusicApp(val id: String, val name: String, val packages: List<String>)

/** Paquete de NAMSTAT (el reproductor del ecosistema T-A-T). */
object Namstat {
    val packages = listOf(NamstatPackage.NAME)
}

object MusicApps {
    val all: List<MusicApp> = listOf(
        MusicApp(Connectors.NAMIDA, "Namida", Namida.packages),
        MusicApp(Connectors.SPOTIFY, "Spotify", listOf("com.spotify.music")),
        MusicApp(Connectors.YTMUSIC, "YouTube Music", listOf("com.google.android.apps.youtube.music")),
        MusicApp(Connectors.NAMSTAT, "NAMSTAT", Namstat.packages),
    )
    val ids: List<String> get() = all.map { it.id }
    fun byId(id: String): MusicApp? = all.firstOrNull { it.id == id }
    fun byPackage(pkg: String): MusicApp? = all.firstOrNull { pkg in it.packages }
    fun isMusic(id: String) = byId(id) != null
}

/**
 * Habla con las apps de música a través de su sesión de reproducción de Android (MediaSession): ve qué suena y la carátula,
 * y le manda play, pausa, siguiente y anterior. Solo mira las apps cuyo conector está activado ([allowed]) y, si suenan
 * varias, atiende la que está sonando. Necesita el «Acceso a notificaciones» de FITSTAT (ver [FitstatListener]).
 * No arranca ninguna app: si no está abierta, no hay sesión y no se puede controlar.
 */
class NamidaPlayer(
    private val ctx: Context,
    private val scope: CoroutineScope,
    /** Ids de los conectores de música activados. */
    private val allowed: () -> Set<String> = { MusicApps.ids.toSet() },
) : MusicPlayer {
    override var state: MusicState by mutableStateOf(MusicState.None)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val msm: MediaSessionManager? = runCatching { ctx.getSystemService(MediaSessionManager::class.java) }.getOrNull()
    private val listener = ComponentName(ctx, FitstatListener::class.java)
    private var controller: MediaController? = null
    private var started = false
    private var artJobKey = ""

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(st: PlaybackState?) = publish()
        override fun onMetadataChanged(md: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            state = MusicState.None
        }
    }

    /** Empieza a escuchar las sesiones de reproducción. Si falta el acceso a notificaciones, no hace nada. */
    fun start() {
        val m = msm ?: return
        stop()
        try {
            m.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
            started = true
            pick(m.getActiveSessions(listener))
        } catch (_: SecurityException) {
            started = false
            state = MusicState.None
        } catch (_: Throwable) {
            started = false
            state = MusicState.None
        }
    }

    fun stop() {
        if (started) runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        started = false
        runCatching { controller?.unregisterCallback(callback) }
        controller = null
        state = MusicState.None
    }

    private fun pick(list: List<MediaController>) {
        val ok = allowed()
        val mine = list.filter { c -> MusicApps.byPackage(c.packageName)?.id in ok }
        val next = mine.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: mine.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            runCatching { controller?.unregisterCallback(callback) }
            controller = next
            runCatching { next?.registerCallback(callback, main) }
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            state = MusicState.None
            return
        }
        val md = c.metadata
        val pb = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val uri = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            .firstNotNullOfOrNull { k -> md?.getString(k)?.takeIf { it.isNotBlank() } }
        val bmp = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            .firstNotNullOfOrNull { k -> runCatching { md?.getBitmap(k) }.getOrNull() }
        val source = MusicApps.byPackage(c.packageName)?.id.orEmpty()
        val key = "$title|$artist|$uri"
        val keep = state.takeIf { it.artKey == key }?.art
        val playing = pb?.state == PlaybackState.STATE_PLAYING || pb?.state == PlaybackState.STATE_BUFFERING
        state = MusicState(
            connected = true, playing = playing, title = title.orEmpty(), artist = artist.orEmpty(), album = album,
            art = bmp ?: keep, artKey = key, durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L, source = source,
        )
        if (bmp == null && keep == null && uri != null && artJobKey != key) {
            artJobKey = key
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { loadArt(if (source == Connectors.NAMSTAT) NamstatPackage.artUri(uri) else uri) }
                // Solo se aplica si sigue siendo la misma canción.
                if (loaded != null && state.artKey == key) state = MusicState(state.connected, state.playing, state.title, state.artist, state.album, loaded, key, state.durationMs, state.source)
            }
        }
    }

    /** Lee la carátula que la app comparte por un enlace (content://…) y la reduce para no gastar memoria. */
    private fun loadArt(uri: String): Bitmap? = try {
        val u = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) sample *= 2
        ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } catch (_: Throwable) {
        null
    }

    override fun play() { runCatching { controller?.transportControls?.play() } }
    override fun pause() { runCatching { controller?.transportControls?.pause() } }
    override fun next() { runCatching { controller?.transportControls?.skipToNext() } }
    override fun previous() { runCatching { controller?.transportControls?.skipToPrevious() } }
}

/** El volumen de la música (STREAM_MUSIC) del teléfono. */
class AndroidVolume(ctx: Context) : VolumeControl {
    private val am: AudioManager? = runCatching { ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()
    override val max: Int get() = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
    override var level: Int
        get() = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        set(v) {
            runCatching { am?.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, max), 0) }
        }
}

/** Guarda en disco el volumen al que se estaba subiendo. */
class PrefsFadeStore(ctx: Context) : FadeStore {
    private val sp = ctx.getSharedPreferences("fitstat_music", Context.MODE_PRIVATE)
    override var pendingTarget: Int
        get() = sp.getInt("fade_target", 0)
        set(v) { sp.edit().putInt("fade_target", v).apply() }
}
