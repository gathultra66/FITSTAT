package app.fitstat.sys

import android.content.Context
import app.fitstat.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Une los datos de la app con el sistema: alarmas de recordatorios, widgets e ícono. */
object SystemHooks {
    private val jobs = ConcurrentHashMap<String, Job>()

    private fun debounce(scope: CoroutineScope, key: String, ms: Long, work: () -> Unit) {
        jobs.remove(key)?.cancel()
        jobs[key] = scope.launch {
            delay(ms)
            withContext(Dispatchers.Default) { runCatching(work) }
        }
    }

    fun install(ctx: Context, repo: Repo, scope: CoroutineScope) {
        val app = ctx.applicationContext
        repo.onRemindersChanged = { debounce(scope, "rem", 400) { Reminders.scheduleAll(app, repo) } }
        repo.onDataChanged = { debounce(scope, "wid", 1200) { Widgets.refreshAll(app) } }
        repo.onSettingChanged = { key, _ ->
            when {
                key.startsWith("reminders.") -> debounce(scope, "rem", 400) { Reminders.scheduleAll(app, repo) }
                else -> {
                    // El ícono sigue a la skin (si la sincronía está activa) o al color elegido.
                    if (key == "app.icon" || key == "skin.id" || key == "skin.icon_sync") AppIcon.apply(app, repo.prefs)
                    debounce(scope, "wid", 1200) { Widgets.refreshAll(app) }
                }
            }
        }
    }

    /** Al abrir la app: vuelve a poner alarmas (se pierden si se fuerza el cierre), ícono y widgets. */
    fun bootstrap(ctx: Context, repo: Repo, scope: CoroutineScope) {
        val app = ctx.applicationContext
        scope.launch(Dispatchers.Default) {
            runCatching {
                Notifier.ensureChannel(app)
                Reminders.scheduleAll(app, repo)
                AppIcon.apply(app, repo.prefs)
                Widgets.scheduleMidnight(app)
                Widgets.refreshAll(app)
            }
        }
    }
}
