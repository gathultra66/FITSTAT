package app.fitstat

import android.app.Application
import android.content.ComponentName
import android.app.AlarmManager
import android.os.VibrationAttributes
import android.os.Looper
import android.os.PowerManager
import app.fitstat.sys.RestWake
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowPowerManager
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitstat.data.Demo
import app.fitstat.data.Repo
import app.fitstat.sys.RestReturn
import app.fitstat.sys.WorkoutKeeper
import app.fitstat.sys.WorkoutService
import app.fitstat.ui.app.AppState
import app.fitstat.ui.app.closeWorkout
import app.fitstat.ui.app.ensureWorkout
import app.fitstat.ui.app.finishRestIfDue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.9.6: «Volver a FITSTAT al terminar el descanso». Si el descanso llega a cero con FITSTAT fuera de la vista, la app
 * se abre sola en el entrenamiento (con una vibración suave en lugar del aviso normal). Y mientras hay un entrenamiento,
 * un servicio en primer plano mantiene la app despierta.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RestReturnTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.of(2026, 11, 24).atTime(18, 40).atZone(zone).toInstant().toEpochMilli()
    private val ctx: Application get() = ApplicationProvider.getApplicationContext()

    private val pm get() = shadowOf(ctx.getSystemService(PowerManager::class.java))
    private val am get() = ctx.getSystemService(AlarmManager::class.java)
    private val vibrator get() = shadowOf(RestReturn.vibrator(ctx))

    @Before fun clean() {
        pm.setIsInteractive(true)
        ShadowPowerManager.clearWakeLocks()
        RestWake.lock = null
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        WorkoutKeeper.running = false
        WorkoutKeeper.foreground = false
        ShadowSettings.setCanDrawOverlays(true)
        val sa = shadowOf(ctx)
        while (sa.nextStartedActivity != null) Unit
        while (sa.nextStartedService != null) Unit
        while (sa.nextStoppedService != null) Unit
    }

    private fun makeApp(vararg settings: Pair<String, String>): AppState {
        val dir = Files.createTempDirectory("fitstat-return").toFile()
        val repo = Repo(dir, CoroutineScope(SupervisorJob() + Dispatchers.Default), { now }) { zone }
        repo.replaceAll(Demo.build(repo.data, now, zone, repo.formula))
        for ((k, v) in settings) repo.setSetting(k, v)
        return AppState(ctx, repo)
    }

    private fun idle() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Un entrenamiento con un descanso que ya llegó a cero. */
    private fun restOver(app: AppState) = app.ensureWorkout("demo_r1", emptyList()).also { it.startRest(30, now - 60_000L) }

    @Test fun awayFromTheAppItComesBackToTheWorkout() {
        val app = makeApp()
        app.visible = false
        val s = restOver(app)
        assertTrue(app.finishRestIfDue(ctx))
        assertFalse("el descanso terminó", s.resting)
        val started = shadowOf(ctx).nextStartedActivity
        assertNotNull("FITSTAT se abrió sola", started)
        assertEquals(ComponentName(ctx, MainActivity::class.java), started.component)
        assertTrue("directo al entrenamiento", started.getBooleanExtra(RestReturn.EXTRA_WORKOUT, false))
        assertFalse("solo la primera vez", app.finishRestIfDue(ctx))
        assertNull(shadowOf(ctx).nextStartedActivity)
    }

    @Test fun insideTheAppNothingIsOpened() {
        val app = makeApp()
        app.visible = true
        restOver(app)
        assertTrue(app.finishRestIfDue(ctx))
        assertNull("ya estás en FITSTAT", shadowOf(ctx).nextStartedActivity)
    }

    @Test fun theSettingTurnsItOff() {
        val app = makeApp("train.rest_return" to "false")
        app.visible = false
        restOver(app)
        assertTrue(app.finishRestIfDue(ctx))
        assertNull(shadowOf(ctx).nextStartedActivity)
    }

    @Test fun theRestEndsOnItsOwnEvenWithoutTheScreen() {
        // Antes lo vigilaba la pantalla; ahora el estado de la app, así que funciona aunque la pantalla no exista.
        val app = makeApp()
        app.visible = false
        val s = app.ensureWorkout("demo_r1", emptyList())
        s.startRest(30, now - 29_000L)   // le falta 1 s
        idle()
        assertTrue(s.resting)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        idle()
        assertFalse("terminó a su hora", s.resting)
        assertTrue(s.restOverAt > 0L)
        assertNotNull("y te regresó a FITSTAT", shadowOf(ctx).nextStartedActivity)
    }

    @Test fun aServiceKeepsTheAppAwakeWhileYouTrain() {
        val app = makeApp()
        idle()
        assertNull("sin entrenamiento no hay servicio", shadowOf(ctx).nextStartedService)
        app.ensureWorkout("demo_r1", emptyList())
        idle()
        val started = shadowOf(ctx).nextStartedService
        assertNotNull(started)
        assertEquals(ComponentName(ctx, WorkoutService::class.java), started.component)
        assertEquals("la notificación dice qué rutina es", app.repo.routine("demo_r1")?.name, WorkoutKeeper.current?.name)
        WorkoutKeeper.foreground = true   // (el servicio ya se puso en primer plano)
        app.closeWorkout()
        idle()
        assertNotNull("al terminar se detiene", shadowOf(ctx).nextStoppedService)
        assertNull(WorkoutKeeper.current)
    }

    @Test fun aWorkoutCancelledBeforeTheServiceStartsLetsItStopItself() {
        val app = makeApp()
        app.ensureWorkout("demo_r1", emptyList())
        idle()
        assertNotNull(shadowOf(ctx).nextStartedService)
        app.closeWorkout()   // el servicio aún no llega a primer plano
        idle()
        assertNull("no se detiene desde fuera (Android cerraría la app)", shadowOf(ctx).nextStoppedService)
        // al arrancar, el servicio ve que ya no hay entrenamiento, pasa a primer plano y se detiene solo
        val svc = Robolectric.buildService(WorkoutService::class.java).create().startCommand(0, 1).get()
        assertTrue(shadowOf(svc).isStoppedBySelf)
    }

    @Test fun theIntentGoesStraightToTheWorkoutInTheSameTask() {
        // (La actividad completa no se arma aquí: la interfaz tiene animaciones sin fin y Robolectric nunca queda en reposo.)
        val i = RestReturn.openIntent(ctx)
        assertEquals(ComponentName(ctx, MainActivity::class.java), i.component)
        assertTrue(i.getBooleanExtra(RestReturn.EXTRA_WORKOUT, false))
        assertTrue("se puede abrir desde fuera de la app", i.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue("no apila otra copia de FITSTAT", i.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun askingForThePermissionOnlyHappensWhenItIsMissing() {
        ShadowSettings.setCanDrawOverlays(true)
        assertFalse(RestReturn.shouldAsk(ctx, enabled = true))
        ShadowSettings.setCanDrawOverlays(false)
        assertTrue(RestReturn.shouldAsk(ctx, enabled = true))
        assertFalse("apagado no se pregunta", RestReturn.shouldAsk(ctx, enabled = false))
        RestReturn.markAsked(ctx)
        assertFalse("solo una vez", RestReturn.shouldAsk(ctx, enabled = true))
    }

    // ───────── v1.9.7: con la pantalla apagada

    private val strongPattern = longArrayOf(0, 160, 100, 160, 100, 320)

    @Test fun withTheScreenOffItVibratesLikeTheNormalAlert() {
        val app = makeApp()
        app.visible = false
        pm.setIsInteractive(false)
        restOver(app)
        assertTrue(app.finishRestIfDue(ctx))
        assertTrue("vibración normal del aviso, no la suave", strongPattern.contentEquals(vibrator.pattern))
        assertEquals("con atributos de alarma (Android no ignora la vibración en segundo plano)",
            VibrationAttributes.USAGE_ALARM, (vibrator.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
        assertNotNull("y FITSTAT igual queda abierta para cuando desbloquees", shadowOf(ctx).nextStartedActivity)
    }

    @Test fun inAnotherAppWithTheScreenOnTheVibrationIsSoft() {
        val app = makeApp()
        app.visible = false
        pm.setIsInteractive(true)
        restOver(app)
        assertTrue(app.finishRestIfDue(ctx))
        assertFalse("la suave, no la del aviso", strongPattern.contentEquals(vibrator.pattern))
    }

    @Test fun theNormalAlertCanStillBeTurnedOff() {
        val app = makeApp("train.rest_alert" to "off")
        app.visible = false
        pm.setIsInteractive(false)
        restOver(app)
        assertTrue(app.finishRestIfDue(ctx))
        assertFalse(strongPattern.contentEquals(vibrator.pattern))
    }

    @Test fun thePhoneStaysAwakeDuringTheRest() {
        val app = makeApp()
        idle()
        val s = app.ensureWorkout("demo_r1", emptyList())
        s.startRest(90, now)
        idle()
        val wl = ShadowPowerManager.getLatestWakeLock()
        assertNotNull("se pidió mantener despierto el procesador", wl)
        assertTrue(wl.isHeld)
        assertEquals(RestWake.TAG, shadowOf(wl).tag)
    }

    @Test fun withTheScreenOffTheAlarmIsAnAlarmClock() {
        val app = makeApp()
        app.screenOn = false
        idle()
        val s = app.ensureWorkout("demo_r1", emptyList())
        s.startRest(90, now)
        idle()
        val info = am.nextAlarmClock
        assertNotNull("alarma de despertador: Android no la retrasa", info)
        assertEquals(now + 90_000L, info!!.triggerTime)
        // al encender la pantalla vuelve a ser una alarma exacta normal (sin ícono de alarma mientras usas el teléfono)
        app.screenOn = true
        idle()
        assertNull(am.nextAlarmClock)
        // y al terminar el descanso se quita
        s.skipRest()
        idle()
        assertNull(am.nextAlarmClock)
    }

    @Test fun withTheScreenOnTheAlarmIsNotAnAlarmClock() {
        val app = makeApp()
        app.screenOn = true
        idle()
        app.ensureWorkout("demo_r1", emptyList()).startRest(90, now)
        idle()
        assertNull(am.nextAlarmClock)
    }

    @Test fun batteryOptimizationIsAskedOnlyWhenItMatters() {
        pm.setIgnoringBatteryOptimizations(ctx.packageName, true)
        assertTrue(RestReturn.batteryFree(ctx))
        assertFalse(RestReturn.shouldAskBattery(ctx))
        pm.setIgnoringBatteryOptimizations(ctx.packageName, false)
        assertFalse(RestReturn.batteryFree(ctx))
        assertTrue(RestReturn.shouldAskBattery(ctx))
        RestReturn.markBatteryAsked(ctx)
        assertFalse("solo una vez", RestReturn.shouldAskBattery(ctx))
    }
}
