package app.fitstat.ui.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.fitstat.bot.ApiKeyStore
import app.fitstat.bot.BotController
import app.fitstat.bot.ChatStore
import app.fitstat.bot.ModelCatalog
import app.fitstat.bot.OpenRouter
import app.fitstat.bot.Recorder
import app.fitstat.conn.ConnStatus
import app.fitstat.conn.ConnectorHub
import app.fitstat.conn.Connectors
import app.fitstat.conn.RhythmEngine
import app.fitstat.conn.VisualizerTap
import app.fitstat.data.LayoutPage
import app.fitstat.data.Repo
import app.fitstat.data.awaitWallClock
import app.fitstat.sys.RestAlarm
import app.fitstat.sys.RestReturn
import app.fitstat.sys.WorkoutKeeper
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.Overlays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Estado de la interfaz que vive mientras vive la app: navegación, avisos, confeti y asistente. */
@Stable
class AppState(
    val context: Context,
    val repo: Repo,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    connectors: ConnectorHub? = null,
    rhythm: RhythmEngine? = null,
) {
    val overlays = Overlays()
    val nav = Nav(Route.tabOf(repo.prefs.str("home.start")))

    /** Edición de los widgets de la pantalla de inicio. */
    val home = PageEditor(repo, LayoutPage.HOME)

    /** Edición de la distribución de Ejercicios y de Rutinas (mismo comportamiento que el inicio). */
    val exercisesLayout = PageEditor(repo, LayoutPage.EXERCISES)
    val routinesLayout = PageEditor(repo, LayoutPage.ROUTINES)

    /** El editor de una pantalla editable. */
    fun editorOf(page: String): PageEditor = when (page) {
        LayoutPage.EXERCISES -> exercisesLayout
        LayoutPage.ROUTINES -> routinesLayout
        else -> home
    }

    /** Guarda lo que haya a medias en cualquiera de los editores de distribución. */
    fun finishLayoutEdits() {
        home.finish(); exercisesLayout.finish(); routinesLayout.finish()
    }

    /** Conectores (Namida, Health Connect…): permisos, música en el descanso y sincronización del sueño. */
    val connectors: ConnectorHub = connectors ?: ConnectorHub.create(context, repo, scope)

    /** El ritmo de la música para las animaciones (lee el audio del teléfono o, si no se puede, es aproximado). */
    val rhythm: RhythmEngine = rhythm ?: RhythmEngine(
        prefs = { repo.prefs },
        music = { this.connectors.music.state },
        ready = { this.connectors.musicReady() },
        tap = VisualizerTap(context),
    )

    // ───────── FITBOT
    val api = OpenRouter()
    val keys = ApiKeyStore(context)
    private val botDir = File(context.filesDir, "bot")
    val models = ModelCatalog(botDir, api)
    val chat = ChatStore(botDir, scope)
    val bot = BotController(AppBotHost(this), keys, models, chat, api, scope)
    val recorder = Recorder(chat.attachDir)

    init {
        bot.onReplyReady = { text ->
            overlays.toast("FITBOT: " + text.replace('\n', ' ').take(80), "Abrir", Ic.Bot) { nav.push(Route.Bot) }
        }
    }

    /** La pantalla de FITSTAT está a la vista (lo marca la actividad en onStart/onStop). */
    var visible by mutableStateOf(false)

    var confetti by mutableIntStateOf(0)
        private set

    /** Entrenamiento guiado en curso (null si no hay). */
    var workout by mutableStateOf<WorkoutSession?>(null)

    init {
        // La música (Namida, Spotify o YouTube Music) se pausa mientras dura el descanso entre series y vuelve sola al terminar.
        this.connectors.watchRest({ workout?.takeIf { !it.done }?.restEndsAt ?: 0L }, { repo.now() })
        // Una alarma del sistema despierta al teléfono cuando termina el descanso (con la música pausada y la pantalla apagada se duerme).
        RestAlarm.watch(context, scope, { workout?.takeIf { !it.done }?.restEndsAt ?: 0L })
        // El descanso termina a su hora aunque no estés viendo FITSTAT (antes lo vigilaba la pantalla, que con la app en
        // segundo plano o cerrada con «atrás» ya no estaba). Si FITSTAT no se ve, puede regresarte (ver finishRestIfDue).
        scope.launch {
            snapshotFlow { workout?.takeIf { !it.done }?.restEndsAt ?: 0L }.collectLatest { end ->
                if (end <= 0L) return@collectLatest
                RestReturn.clear(context)
                awaitWallClock(end, repo.now)
                finishRestIfDue(context)
            }
        }
        // Mientras entrenas, un servicio en primer plano mantiene a FITSTAT despierta (con el descanso en una notificación).
        WorkoutKeeper.watch(context, scope) {
            workout?.takeIf { !it.done }?.let { w ->
                WorkoutKeeper.Info(
                    name = w.routineId?.let { id -> repo.routine(id)?.name } ?: "Entrenamiento libre",
                    restEndsAt = w.restEndsAt,
                    restOver = w.restOverAt > 0L,
                    exercise = w.exercises.getOrNull(w.index)?.let { repo.exById[it]?.name },
                    sets = w.logIds.size,
                )
            }
        }
        // NAMSTAT se entera de cuándo entrenas (y puede poner tu lista de entreno).
        this.connectors.watchWorkout { workout?.takeIf { !it.done }?.let { w -> w.routineId?.let { id -> repo.routine(id)?.name } ?: "Entrenamiento libre" } }
    }

    fun celebrate() { confetti++ }

    /** Los registra la actividad: piden permisos del sistema y abren ajustes. */
    var notificationPermissionRequest: (() -> Unit)? = null
    fun ensureNotifications() { notificationPermissionRequest?.invoke() }

    /** Pide el permiso de audio (para que las animaciones sigan la música de verdad). Lo registra la actividad. */
    var audioPermissionRequest: (() -> Unit)? = null

    /** Aviso con "Deshacer" para el último cambio. */
    fun toastUndo(text: String) {
        overlays.toast(text, "Deshacer", app.fitstat.ui.design.Ic.Undo) { repo.undo() }
    }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState no disponible") }
val LocalRepo = staticCompositionLocalOf<Repo> { error("Repo no disponible") }
