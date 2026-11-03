package app.fitstat

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.Repo
import app.fitstat.sys.WidgetArt
import app.fitstat.sys.WidgetData
import app.fitstat.sys.WidgetKind
import app.fitstat.sys.WidgetStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/** Dibuja los 4 widgets con distintos estilos y tamaños, y guarda PNG para revisarlos. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi")
class WidgetPreviewTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 10, 28).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()
    private val out = File(Shots.dir, "widgets").apply { mkdirs() }

    private fun repo(vararg settings: Pair<String, String>, demo: Boolean = true): Repo {
        val dir = Files.createTempDirectory("fitstat-widgets").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        if (demo) {
            repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
            repo.setProfileName("Chris")
        }
        for ((k, v) in settings) repo.setSetting(k, v)
        return repo
    }

    private fun save(bmp: Bitmap, name: String) {
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val variants: List<Pair<String, Array<Pair<String, String>>>> = listOf(
        "dark" to arrayOf("theme.mode" to "dark"),
        "light" to arrayOf("theme.mode" to "light"),
        "accent" to arrayOf("theme.mode" to "dark", "widgets.theme" to "accent"),
        "mint_sharp" to arrayOf("theme.mode" to "dark", "theme.palette" to "menta", "shape.radius" to "sharp", "type.font" to "space_grotesk"),
        "glass" to arrayOf("theme.mode" to "amoled", "widgets.opacity" to "55", "widgets.led" to "false", "theme.palette" to "mandarina"),
    )

    @Test
    fun rendersEveryWidgetInEveryStyle() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val density = 3f
        for ((name, settings) in variants) {
            val r = repo(*settings)
            val data = WidgetData.from(r)
            val style = WidgetStyle.from(ctx, r.prefs)
            for (kind in WidgetKind.values()) {
                val sizes = listOf(kind.defaultW.toFloat() to kind.defaultH.toFloat()) +
                    if (kind == WidgetKind.Bot) listOf(70f to 70f, 180f to 180f)
                    else listOf(110f to 110f, 360f to 110f, 250f to 250f, 110f to 55f, 170f to 110f, 110f to 250f, 250f to 60f)
                for ((w, h) in sizes) {
                    val bmp = WidgetArt.render(ctx, kind, w, h, data, style, density)
                    assertTrue(bmp.width > 0 && bmp.height > 0)
                    save(bmp, "${name}_${kind.id}_${w.toInt()}x${h.toInt()}")
                }
            }
        }
    }

    @Test
    fun rendersEmptyStates() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val r = repo("theme.mode" to "dark", demo = false)
        val data = WidgetData.from(r)
        val style = WidgetStyle.from(ctx, r.prefs)
        for (kind in WidgetKind.values()) {
            save(WidgetArt.render(ctx, kind, kind.defaultW.toFloat(), kind.defaultH.toFloat(), data, style, 3f), "empty_${kind.id}")
        }
    }

    /** Las vistas previas que se ven al elegir el widget en el lanzador. */
    @Test
    fun rendersLauncherPreviews() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val r = repo("theme.mode" to "dark")
        val data = WidgetData.from(r)
        val style = WidgetStyle.from(ctx, r.prefs)
        val previews = File(out, "previews").apply { mkdirs() }
        for (kind in WidgetKind.values()) {
            val bmp = WidgetArt.render(ctx, kind, kind.defaultW.toFloat(), kind.defaultH.toFloat(), data, style, 2.5f)
            FileOutputStream(File(previews, "widget_preview_${kind.name.lowercase()}.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** v1.8.1: en tablets las celdas son grandes; el diseño se amplía en vez de quedar chico y con huecos, y nunca se deforma. */
    @Test
    fun rendersTabletSizesWithoutStretching() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val r = repo("theme.mode" to "dark")
        val style = WidgetStyle.from(ctx, r.prefs)
        val rest = WidgetData.from(r)
        val sizes = listOf(372f to 186f, 372f to 300f, 560f to 260f, 186f to 186f, 300f to 140f)
        for (kind in WidgetKind.values()) {
            for ((w, h) in sizes) {
                val z = WidgetArt.designZoom(kind, w, h)
                assertTrue("zoom en rango ($z)", z in 1f..1.6f)
                val bmp = WidgetArt.render(ctx, kind, w, h, rest, style, 2.5f)
                // la imagen guarda la misma proporción que el widget: no se estira al mostrarla
                assertTrue(Math.abs(bmp.width.toFloat() / bmp.height - w / h) < 0.02f)
                save(bmp, "tablet_${kind.id}_${w.toInt()}x${h.toInt()}")
            }
        }
        // el tamaño de diseño no cambia
        for (kind in WidgetKind.values()) assertTrue(WidgetArt.designZoom(kind, kind.defaultW.toFloat(), kind.defaultH.toFloat()) == 1f)
    }

    /** El lanzador de Android 12+ informa cada tamaño posible: se usan esos, no el mínimo/máximo. */
    @Test
    fun usesTheSizesTheLauncherReports() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        val id = org.robolectric.Shadows.shadowOf(mgr).createWidget(app.fitstat.sys.WidgetToday::class.java, app.fitstat.R.layout.widget_image)
        val b = android.os.Bundle().apply {
            putParcelableArrayList(
                android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(android.util.SizeF(372f, 190f), android.util.SizeF(560f, 150f)),
            )
        }
        mgr.updateAppWidgetOptions(id, b)
        val sizes = app.fitstat.sys.Widgets.sizesDp(ctx, WidgetKind.Today, id)
        assertTrue(sizes.toString(), sizes == listOf(372f to 190f, 560f to 150f))
        // sin datos del lanzador se usa el tamaño de diseño
        assertTrue(app.fitstat.sys.Widgets.sizesDp(ctx, WidgetKind.Today, org.robolectric.Shadows.shadowOf(mgr).createWidget(app.fitstat.sys.WidgetToday::class.java, app.fitstat.R.layout.widget_image)).first() == (250f to 110f))
    }
}
