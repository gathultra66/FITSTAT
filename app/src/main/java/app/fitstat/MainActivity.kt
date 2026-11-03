package app.fitstat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import app.fitstat.conn.ConnOpen
import app.fitstat.conn.HealthAccess
import app.fitstat.conn.Connectors
import app.fitstat.sys.Reminders
import app.fitstat.ui.app.FitstatRoot
import app.fitstat.ui.app.Route

class MainActivity : ComponentActivity() {
    private val app get() = application as FitstatApp

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) Thread { runCatching { Reminders.scheduleAll(applicationContext, app.repo) } }.start()
        }

    private val audioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            app.state.rhythm.refreshPermission()
        }

    private val healthPermission =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { _ ->
            app.state.connectors.refresh()
            app.state.connectors.autoSync(force = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = app.state
        state.notificationPermissionRequest = {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        state.audioPermissionRequest = {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                state.rhythm.refreshPermission()
            }
        }
        state.connectors.healthRequest = {
            try {
                healthPermission.launch(HealthAccess.permissions)
            } catch (_: Throwable) {
                state.connectors.platform.open(ConnOpen.HealthSettings)
            }
        }
        setContent { FitstatRoot(state, onExit = { finish() }) }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Los permisos de los conectores se dan en los ajustes del sistema: al volver se revisan de nuevo.
        app.state.connectors.refresh()
        app.state.connectors.autoSync()
        app.state.rhythm.refreshPermission()
    }

    /** Abre la pantalla pedida por un widget, una notificación o un acceso directo. */
    private fun handleIntent(i: Intent?) {
        // Health Connect pide una pantalla que explique qué datos lee la app y para qué.
        if (i?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" || i?.action == "android.intent.action.VIEW_PERMISSION_USAGE") {
            app.state.nav.push(Route.Connector(Connectors.HEALTH))
            return
        }
        val name = i?.getStringExtra(EXTRA_ROUTE) ?: return
        i.removeExtra(EXTRA_ROUTE)
        val route = Route.fromName(name) ?: return
        app.state.nav.push(route)
    }

    override fun onStop() {
        super.onStop()
        app.repo.flush()
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
