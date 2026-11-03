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
    val state: AppState by lazy { AppState(this, repo, scope) }

    override fun onCreate() {
        super.onCreate()
        repo = Repo(filesDir, scope)
        Notifier.ensureChannel(this)
        SystemHooks.install(this, repo, scope)
        SystemHooks.bootstrap(this, repo, scope)
    }
}
