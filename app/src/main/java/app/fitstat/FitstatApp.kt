package app.fitstat

import android.app.Application
import app.fitstat.data.Repo
import app.fitstat.sys.Notifier
import app.fitstat.sys.SystemHooks
import app.fitstat.ui.app.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FitstatApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repo: Repo
        private set
    private val stateLazy = lazy { AppState(this, repo, scope) }
    val state: AppState get() = stateLazy.value

    /** El estado de la app si ya existe: un receptor no debe crearlo entero solo para mirar si hay algo que hacer. */
    val stateOrNull: AppState? get() = if (stateLazy.isInitialized()) stateLazy.value else null

    override fun onCreate() {
        super.onCreate()
        repo = Repo(filesDir, scope)
        Notifier.ensureChannel(this)
        SystemHooks.install(this, repo, scope)
        SystemHooks.bootstrap(this, repo, scope)
    }
}
