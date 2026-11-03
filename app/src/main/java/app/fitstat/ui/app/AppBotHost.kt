package app.fitstat.ui.app

import app.fitstat.bot.BotHost
import app.fitstat.data.Repo

/** Conecta a FITBOT con la app: datos, pantallas y entrenamiento guiado. */
class AppBotHost(private val app: AppState) : BotHost {
    override val repo: Repo get() = app.repo
    override fun currentScreen(): String = app.nav.top.key
    override fun navigate(route: Route) = app.nav.push(route)
    override fun startWorkout(routineId: String?, exerciseIds: List<String>) = app.startWorkout(routineId, exerciseIds)
    override fun workoutActive(): Boolean = app.workoutActive
    override fun workoutStatus() = app.botWorkoutStatus()
    override fun finishWorkout(): String? = app.botFinishWorkout()
    override fun cancelWorkout(discardSets: Boolean): String? = app.botCancelWorkout(discardSets)
    override fun ensureNotifications() = app.ensureNotifications()
    override fun undoLastBotChange(): String? = app.bot.undoLast()
    override fun saveHomeEdit() { app.finishLayoutEdits() }
    override fun connectors() = app.connectors
    override fun rhythm() = app.rhythm
}
