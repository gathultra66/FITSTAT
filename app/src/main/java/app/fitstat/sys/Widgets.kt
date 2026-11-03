package app.fitstat.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import app.fitstat.FitstatApp
import app.fitstat.R
import app.fitstat.data.Repo
import java.time.LocalDate
import java.time.ZoneId

/** Widgets de la pantalla de inicio: se dibujan como imagen con el estilo de la app. */
object Widgets {
    const val ACTION_REFRESH = "app.fitstat.action.REFRESH_WIDGETS"

    private fun providerClass(kind: WidgetKind): Class<*> = when (kind) {
        WidgetKind.Today -> WidgetToday::class.java
        WidgetKind.Bot -> WidgetBot::class.java
        WidgetKind.Week -> WidgetWeek::class.java
        WidgetKind.Records -> WidgetRecords::class.java
    }

    fun ids(ctx: Context, kind: WidgetKind): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, providerClass(kind)))

    /** Cuántos widgets de FITSTAT hay puestos en la pantalla de inicio. */
    fun installedCount(ctx: Context): Int = WidgetKind.values().sumOf { ids(ctx, it).size }

    /** Tamaño actual del widget [id] en dp (según la orientación). */
    fun sizeDp(ctx: Context, kind: WidgetKind, id: Int): Pair<Float, Float> = sizesDp(ctx, kind, id).first()

    /**
     * Los tamaños en dp que puede tener el widget [id]. En Android 12+ el lanzador informa cada tamaño posible (vertical y horizontal,
     * y en tablets el real de la cuadrícula); se dibuja uno por cada tamaño para no estirar nada. Antes: el mínimo/máximo clásico.
     */
    fun sizesDp(ctx: Context, kind: WidgetKind, id: Int): List<Pair<Float, Float>> {
        val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
        val listed = if (Build.VERSION.SDK_INT >= 31) {
            o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java).orEmpty()
                .filter { it.width > 0f && it.height > 0f }.map { it.width to it.height }.distinct().take(4)
        } else emptyList()
        if (listed.isNotEmpty()) return listed
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val w = if (land) maxW else minW
        val h = if (land) minH else maxH
        return listOf((if (w > 0) w.toFloat() else kind.defaultW.toFloat()) to (if (h > 0) h.toFloat() else kind.defaultH.toFloat()))
    }

    /** Pantalla que se abre al tocar el widget. */
    fun route(kind: WidgetKind, data: WidgetData): String = when (kind) {
        WidgetKind.Today -> data.planRoutineId?.let { "rutina:$it" } ?: "inicio"
        WidgetKind.Bot -> "fitbot"
        WidgetKind.Week, WidgetKind.Records -> "progreso"
    }

    fun update(
        ctx: Context,
        kind: WidgetKind,
        ids: IntArray,
        repo: Repo,
        data: WidgetData = WidgetData.from(repo),
        style: WidgetStyle = WidgetStyle.from(ctx, repo.prefs),
    ) {
        if (ids.isEmpty()) return
        val mgr = AppWidgetManager.getInstance(ctx)
        val density = ctx.resources.displayMetrics.density
        val click = Notifier.openPending(ctx, route(kind, data), 4000 + kind.ordinal)
        for (id in ids) {
            try {
                fun view(w: Float, h: Float): RemoteViews {
                    val bmp = WidgetArt.render(ctx, kind, w, h, data, style, density)
                    return RemoteViews(ctx.packageName, R.layout.widget_image).also { rv ->
                        rv.setImageViewBitmap(R.id.widget_img, bmp)
                        rv.setContentDescription(R.id.widget_img, data.describe(kind))
                        rv.setOnClickPendingIntent(R.id.widget_root, click)
                    }
                }
                val sizes = sizesDp(ctx, kind, id)
                val rv = if (Build.VERSION.SDK_INT >= 31 && sizes.size > 1) {
                    RemoteViews(sizes.associate { (w, h) -> android.util.SizeF(w, h) to view(w, h) })
                } else {
                    val (w, h) = sizes.first()
                    view(w, h)
                }
                mgr.updateAppWidget(id, rv)
            } catch (_: Throwable) {
                // Un widget que falla no debe tumbar a los demás.
            }
        }
    }

    /** Vuelve a dibujar todos los widgets puestos (si hay alguno). */
    fun refreshAll(ctx: Context) {
        val app = ctx.applicationContext as? FitstatApp ?: return
        val repo = app.repo
        val todo = WidgetKind.values().map { it to ids(ctx, it) }.filter { it.second.isNotEmpty() }
        if (todo.isEmpty()) return
        val data = WidgetData.from(repo)
        val style = WidgetStyle.from(ctx, repo.prefs)
        for ((kind, ids) in todo) update(ctx, kind, ids, repo, data, style)
    }

    /** Pide al lanzador que ofrezca poner el widget (Android 8+). Devuelve false si no se puede. */
    fun requestPin(ctx: Context, kind: WidgetKind): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return try {
            mgr.requestPinAppWidget(ComponentName(ctx, providerClass(kind)), null, null)
        } catch (_: Throwable) {
            false
        }
    }

    /** Programa un refresco poco después de medianoche para que "Hoy" cambie de día. */
    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val at = LocalDate.now(zone).plusDays(1).atTime(0, 1).atZone(zone).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            ctx, 7001, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        } catch (_: Throwable) {
        }
    }
}

abstract class FitstatWidget(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? FitstatApp ?: return
        Widgets.update(context, kind, appWidgetIds, app.repo)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val app = context.applicationContext as? FitstatApp ?: return
        Widgets.update(context, kind, intArrayOf(appWidgetId), app.repo)
    }

    override fun onEnabled(context: Context) {
        Widgets.scheduleMidnight(context)
    }
}

class WidgetToday : FitstatWidget(WidgetKind.Today)
class WidgetBot : FitstatWidget(WidgetKind.Bot)
class WidgetWeek : FitstatWidget(WidgetKind.Week)
class WidgetRecords : FitstatWidget(WidgetKind.Records)
