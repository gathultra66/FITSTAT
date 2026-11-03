package app.fitstat.ui.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.fitstat.bot.WorkoutStatus
import app.fitstat.data.Fmt
import app.fitstat.ui.design.Ic
import kotlinx.coroutines.delay

/**
 * Entrenamiento guiado en curso. Vive en [AppState] para que no se pierda al salir de la pantalla
 * (por ejemplo para ver un ejercicio) ni al girar el teléfono. Las series se guardan en el
 * registro en cuanto las completas; aquí solo se lleva la cuenta de lo que pasó en esta sesión.
 */
@Stable
class WorkoutSession(val routineId: String?, ids: List<String>, val startedAt: Long) {
    val exercises = mutableStateListOf<String>().also { it.addAll(ids) }
    var index by mutableIntStateOf(0)

    /** Series guardadas en esta sesión, en orden. */
    val logIds = mutableStateListOf<String>()

    /** Textos de los récords logrados ("Press de banca: 80 kg × 6"). */
    val records = mutableStateListOf<String>()

    /** Borradores del peso (en la unidad elegida) y las repeticiones de cada ejercicio. */
    val draftW = mutableStateMapOf<String, Double>()
    val draftR = mutableStateMapOf<String, Int>()

    /** Ejercicios que se saltaron. */
    val skipped = mutableStateListOf<String>()

    /** Ejercicios en los que se pidió hacer más series de las planeadas. */
    val more = mutableStateListOf<String>()

    /** Para abrir el selector de ejercicios solo una vez en un entrenamiento libre vacío. */
    var autoPicked = false

    var restEndsAt by mutableLongStateOf(0L)
    var restTotalMs by mutableLongStateOf(0L)

    /** Momento en que terminó el último descanso (0 = no hay aviso pendiente). */
    var restOverAt by mutableLongStateOf(0L)
    var finishedAt by mutableLongStateOf(0L)

    val done: Boolean get() = finishedAt > 0L
    val resting: Boolean get() = restEndsAt > 0L

    fun startRest(seconds: Int, now: Long) {
        restTotalMs = seconds.coerceAtLeast(5) * 1000L
        restEndsAt = now + restTotalMs
        restOverAt = 0L
    }

    fun addRest(deltaSec: Int, now: Long) {
        if (!resting) return
        val left = (restEndsAt - now + deltaSec * 1000L).coerceAtLeast(3000L)
        restEndsAt = now + left
        restTotalMs = maxOf(restTotalMs, left)
    }

    fun skipRest() {
        restEndsAt = 0L
        restOverAt = 0L
    }

    /** El descanso llegó a cero: deja un aviso en pantalla hasta que registres la siguiente serie. */
    fun finishRest(now: Long) {
        restEndsAt = 0L
        restOverAt = now
    }
}

private const val STALE_MS = 12L * 3600_000L

/** Crea un entrenamiento nuevo o continúa el que ya está en curso para esa rutina. */
fun AppState.ensureWorkout(routineId: String?, exercises: List<String>): WorkoutSession {
    val cur = workout
    if (cur != null && cur.routineId == routineId && !cur.done && repo.now() - cur.startedAt < STALE_MS) {
        if (routineId == null) for (id in exercises) if (id in repo.exById && id !in cur.exercises) cur.exercises.add(id)
        return cur
    }
    val ids = if (routineId != null) repo.routine(routineId)?.items?.map { it.exerciseId }.orEmpty() else exercises
    val s = WorkoutSession(routineId, ids.filter { repo.exById.containsKey(it) }.distinct(), repo.now())
    workout = s
    return s
}

/** Inicia (o continúa) un entrenamiento y abre su pantalla. */
fun AppState.startWorkout(routineId: String?, exercises: List<String> = emptyList()) {
    val cur = workout
    if (cur != null && !cur.done && cur.routineId != routineId && cur.logIds.isNotEmpty() && repo.now() - cur.startedAt < STALE_MS) {
        val name = cur.routineId?.let { repo.routine(it)?.name } ?: "entrenamiento libre"
        overlays.confirm(
            "Ya tienes un entrenamiento en curso",
            "Llevas ${cur.logIds.size} ${if (cur.logIds.size == 1) "serie" else "series"} en «$name». Si empiezas otro, ese se da por terminado y tus series se conservan.",
            "Empezar otro",
        ) {
            closeWorkout()
            ensureWorkout(routineId, exercises)
            nav.push(Route.Workout(routineId, exercises))
        }
        return
    }
    ensureWorkout(routineId, exercises)
    nav.push(Route.Workout(routineId, exercises))
}

/**
 * Cierra el entrenamiento: primero saca sus pantallas de la pila y luego olvida la sesión.
 * (Si se olvidara antes, la pantalla que todavía se ve durante la animación de salida volvería a crear otra sesión sola.)
 */
fun AppState.closeWorkout() {
    nav.removeWhere { it is Route.Workout }
    workout = null
}

/** Abre la pantalla del entrenamiento en curso (si ya está abierta no hace nada). */
fun AppState.openWorkout() {
    val s = workout ?: return
    if (nav.stack.none { it is Route.Workout }) nav.push(Route.Workout(s.routineId))
}

/** Series de esta sesión que siguen en el registro. */
fun AppState.sessionSets(s: WorkoutSession): Int {
    val ids = s.logIds.toSet()
    return repo.logs.count { it.id in ids }
}

/** Marca el entrenamiento como terminado y muestra el resumen. Las series ya estaban guardadas. */
fun AppState.completeWorkout(s: WorkoutSession) {
    s.skipRest()
    s.finishedAt = repo.now()
    celebrate()
}

/**
 * Cancela el entrenamiento en curso. Con [deleteSets] también borra las series que se registraron en esta sesión
 * (se puede deshacer). Devuelve cuántas series se borraron, o -1 si no había entrenamiento.
 */
fun AppState.cancelWorkout(deleteSets: Boolean): Int {
    val s = workout ?: return -1
    val del = if (deleteSets) {
        val ids = s.logIds.toSet()
        repo.logs.filter { it.id in ids }.map { it.id }.toSet()
    } else emptySet()
    closeWorkout()
    if (del.isNotEmpty()) {
        repo.update("Entrenamiento cancelado") { d -> d.copy(logs = d.logs.filterNot { it.id in del }) }
        toastUndo("Entrenamiento cancelado · ${Fmt.plural(del.size, "serie borrada", "series borradas")}")
    } else {
        overlays.toast("Entrenamiento cancelado")
    }
    return del.size
}

/** Estado del entrenamiento en curso para FITBOT. */
fun AppState.botWorkoutStatus(): WorkoutStatus? {
    val s = workout ?: return null
    return WorkoutStatus(
        routine = s.routineId?.let { repo.routine(it)?.name },
        minutes = ((repo.now() - s.startedAt) / 60_000L).toInt().coerceAtLeast(0),
        sets = sessionSets(s),
        exercises = s.exercises.mapNotNull { repo.exById[it]?.name },
        skipped = s.skipped.mapNotNull { repo.exById[it]?.name },
        current = s.exercises.getOrNull(s.index)?.let { repo.exById[it]?.name },
        resting = s.resting,
        finished = s.done,
    )
}

/** FITBOT finaliza el entrenamiento: conserva las series. Devuelve lo que pasó, o null si no había entrenamiento. */
fun AppState.botFinishWorkout(): String? {
    val s = workout ?: return null
    if (s.done) {
        closeWorkout()
        return "El entrenamiento ya estaba finalizado; cerré el resumen (las series se conservan)."
    }
    val n = sessionSets(s)
    if (n == 0) {
        cancelWorkout(false)
        return "No había series registradas, así que cerré el entrenamiento vacío."
    }
    val minutes = ((repo.now() - s.startedAt) / 60_000L).toInt().coerceAtLeast(0)
    completeWorkout(s)
    overlays.toast("Entrenamiento finalizado", "Ver resumen", Ic.Check) { openWorkout() }
    return "Finalizó el entrenamiento: ${Fmt.plural(n, "serie", "series")} en $minutes min. Las series quedaron guardadas."
}

/** FITBOT cancela el entrenamiento. Devuelve lo que pasó, o null si no había entrenamiento. */
fun AppState.botCancelWorkout(deleteSets: Boolean): String? {
    val s = workout ?: return null
    if (s.done) {
        closeWorkout()
        return "El entrenamiento ya estaba finalizado, no se podía cancelar; cerré el resumen y las series se conservan."
    }
    val n = cancelWorkout(deleteSets)
    return if (n > 0) "Canceló el entrenamiento y borró ${Fmt.plural(n, "serie", "series")} de esta sesión."
    else "Canceló el entrenamiento" + if (!deleteSets && sessionSets(s) > 0) " (las series registradas se conservaron)." else " (no había series registradas)."
}

/** Hay un entrenamiento sin terminar. */
val AppState.workoutActive: Boolean get() = workout?.let { !it.done } == true

// ───────────────────────── Aviso de fin de descanso

/**
 * Vigila el descanso del entrenamiento en curso aunque cambies de pantalla: cuando llega a cero
 * avisa con vibración y/o sonido según el ajuste "Aviso al terminar el descanso".
 */
@Composable
fun WorkoutRestWatcher(app: AppState) {
    val s = app.workout
    val end = s?.restEndsAt ?: 0L
    val ctx = LocalContext.current
    LaunchedEffect(s, end) {
        if (s == null || end <= 0L) return@LaunchedEffect
        val wait = end - app.repo.now()
        if (wait > 0L) delay(wait)
        if (s.restEndsAt == end) {
            s.finishRest(app.repo.now())
            restAlert(ctx, app.repo.prefs.str("train.rest_alert"))
        }
    }
}

fun restAlert(ctx: Context, mode: String) {
    if (mode == "vibrate" || mode == "both") vibratePattern(ctx)
    if (mode == "sound" || mode == "both") beep()
}

private fun vibratePattern(ctx: Context) {
    try {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 160, 100, 160, 100, 320), -1))
    } catch (_: Throwable) {
    }
}

private fun beep() {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tg.release() } }, 900)
    } catch (_: Throwable) {
    }
}
