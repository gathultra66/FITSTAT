package app.fitstat.conn

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.fitstat.data.ScreenEvent
import app.fitstat.data.Sleep
import app.fitstat.data.SleepSession
import app.fitstat.data.SleepSrc
import app.fitstat.data.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/** Health Connect: el permiso que pide FITSTAT (solo leer sueño). */
object HealthAccess {
    val readSleep: String get() = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val permissions: Set<String> get() = setOf(readSleep)
    const val PROVIDER = "com.google.android.apps.healthdata"
}

/** La implementación real de [ConnPlatform]: lo que de verdad pasa en el teléfono. */
class AndroidConnPlatform(private val ctx: Context) : ConnPlatform {
    override fun installed(pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) ctx.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Throwable) {
        false
    }

    override fun notificationAccess(): Boolean = runCatching { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }.getOrDefault(false)

    override fun usageSupported(): Boolean = Build.VERSION.SDK_INT >= 28

    override fun usageAccess(): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        false
    }

    override fun healthAvail(): HealthAvail = try {
        when (HealthConnectClient.getSdkStatus(ctx)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvail.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvail.NeedsInstall
            else -> HealthAvail.Unavailable
        }
    } catch (_: Throwable) {
        HealthAvail.Unavailable
    }

    override suspend fun healthGranted(): Boolean =
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(HealthAccess.permissions)

    override suspend fun readHealthSleep(fromMs: Long, toMs: Long): List<SleepSession> {
        val client = HealthConnectClient.getOrCreate(ctx)
        val out = ArrayList<SleepSession>()
        var token: String? = null
        do {
            val res = client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs)),
                    pageSize = 200,
                    pageToken = token,
                ),
            )
            for (r in res.records) {
                val start = r.startTime.toEpochMilli()
                out += SleepSession(
                    id = "health_$start",
                    start = start,
                    end = r.endTime.toEpochMilli(),
                    source = SleepSrc.HEALTH,
                    stages = r.stages.map { SleepStage(Sleep.stageFromHealth(it.stage), it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) },
                    origin = r.metadata.dataOrigin.packageName,
                    note = r.title.orEmpty(),
                )
            }
            token = res.pageToken
        } while (token != null)
        return out
    }

    override suspend fun screenEvents(fromMs: Long, toMs: Long): List<ScreenEvent> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 28) return@withContext emptyList()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@withContext emptyList()
        val ev = usm.queryEvents(fromMs, toMs)
        val out = ArrayList<ScreenEvent>()
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> out += ScreenEvent(e.timeStamp, true)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> out += ScreenEvent(e.timeStamp, false)
            }
        }
        out
    }

    private fun start(i: Intent?): Boolean {
        if (i == null) return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun open(what: ConnOpen): Boolean = when (what) {
        ConnOpen.NotificationAccess -> {
            val detail = if (Build.VERSION.SDK_INT >= 30) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, FitstatListener::class.java).flattenToString())
            } else null
            (detail != null && start(detail)) || start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        ConnOpen.UsageAccess -> start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:${ctx.packageName}"))) ||
            start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        ConnOpen.AppSettings -> start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${ctx.packageName}")))
        ConnOpen.HealthSettings -> start(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")) || start(Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"))
        ConnOpen.HealthInstall -> start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthAccess.PROVIDER}&url=healthconnect%3A%2F%2Fonboarding")))
        ConnOpen.Namida, ConnOpen.Spotify, ConnOpen.YtMusic, ConnOpen.Namstat -> {
            val pm = ctx.packageManager
            val id = when (what) { ConnOpen.Spotify -> Connectors.SPOTIFY; ConnOpen.YtMusic -> Connectors.YTMUSIC; ConnOpen.Namstat -> Connectors.NAMSTAT; else -> Connectors.NAMIDA }
            start(MusicApps.byId(id)?.packages?.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) })
        }
    }
}
