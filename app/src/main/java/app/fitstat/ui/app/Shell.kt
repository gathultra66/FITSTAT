package app.fitstat.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fitstat.data.AppEvent
import app.fitstat.data.Fmt
import app.fitstat.ui.design.AppBackground
import app.fitstat.ui.design.SkinBootScreen
import app.fitstat.ui.design.SkinCelebrate
import app.fitstat.ui.design.SkinHost
import app.fitstat.ui.design.SkinTransitionFx
import app.fitstat.ui.design.BottomNav
import app.fitstat.ui.design.Confetti
import app.fitstat.ui.design.Ic
import app.fitstat.ui.design.LocalNavSpace
import app.fitstat.ui.design.LocalOverlays
import app.fitstat.ui.design.LocalRhythm
import app.fitstat.ui.design.RhythmDriver
import app.fitstat.ui.design.LocalScreenKey
import app.fitstat.ui.design.NavItem
import app.fitstat.ui.design.ContentMaxWidth
import app.fitstat.ui.design.MusicTintHost
import app.fitstat.ui.design.OverlayHost
import app.fitstat.ui.design.sideInsets
import app.fitstat.ui.design.navBarSpace
import app.fitstat.ui.screens.*
import app.fitstat.ui.theme.Fs
import app.fitstat.ui.theme.FitstatTheme

private val navItems = listOf(
    NavItem("home", "Inicio", Ic.Home),
    NavItem("exercises", "Ejercicios", Ic.Dumbbell),
    NavItem("routines", "Rutinas", Ic.Routines),
    NavItem("bot", "FITBOT", Ic.Bot),
    NavItem("settings", "Ajustes", Ic.Settings),
)

@Composable
fun FitstatRoot(app: AppState, onExit: () -> Unit = {}) {
    val repo = app.repo
    FitstatTheme(repo.prefs) {
        CompositionLocalProvider(LocalApp provides app, LocalRepo provides repo, LocalOverlays provides app.overlays, LocalRhythm provides app.rhythm) {
            RhythmDriver(app.rhythm)
            MusicTintHost { SkinHost { RootContent(app, onExit) } }
        }
    }
}

@Composable
private fun RootContent(app: AppState, onExit: () -> Unit) {
    val repo = app.repo
    val nav = app.nav
    val m = Fs.motion
    val ui = Fs.ui
    val holder = rememberSaveableStateHolder()
    val ime = WindowInsets.isImeVisible
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val showBar = nav.stack.size == 1
    val baseSpace = when {
        ime -> 0.dp
        showBar -> navBarSpace(ui.dockedNav) + (if (ui.dockedNav) 0.dp else navInset)
        else -> navInset
    }
    val onboarded = repo.profile.onboarded || repo.hasUserData
    // Barra "Entrenamiento en curso" sobre el menú inferior
    val miniBar = app.workoutActive && showBar && !ime && onboarded
    val space = baseSpace + (if (miniBar) 66.dp else 0.dp)

    // Eventos de la app: récords e hitos
    LaunchedEffect(Unit) {
        repo.events.collect { ev ->
            when (ev) {
                is AppEvent.Record -> {
                    app.celebrate()
                    app.overlays.toast("¡Récord! ${ev.exerciseName}: ${Fmt.weight(ev.weightKg, repo.units)} × ${ev.reps}", icon = Ic.Trophy)
                }
                is AppEvent.Milestone -> {
                    app.celebrate()
                    app.overlays.toast("Hito desbloqueado: ${ev.def.title}", "Ver", Ic.Medal) { nav.push(Route.Milestones) }
                }
                is AppEvent.Message -> app.overlays.toast(ev.text)
            }
        }
    }

    BackHandler(enabled = app.overlays.hasOverlay || nav.canBack) {
        if (!app.overlays.closeTop()) {
            if (!nav.back()) onExit()
        }
    }

    val transition = m.transition
    val enabled = m.enabled
    val dur = m.ms(360)

    // Al cambiar de skin en vivo se muestra una pantalla de arranque breve.
    val skin = Fs.skin
    var lastSkin by remember { mutableStateOf(skin.id) }
    var boot by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(skin.id) {
        if (lastSkin != skin.id) {
            lastSkin = skin.id
            if (m.enabled) {
                boot = skin.id
                delay(m.ms(1450).toLong())
                boot = null
            }
        }
    }
    val items = navItems.map { NavItem(it.id, skin.word(it.label), it.icon) }

    Box(Modifier.fillMaxSize()) {
        AppBackground {
          // En pantallas anchas (tablet, o el teléfono acostado) el contenido es una columna centrada: el fondo sigue a todo lo ancho.
          Box(Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = ContentMaxWidth).fillMaxWidth().windowInsetsPadding(sideInsets())) {
            if (!onboarded) {
                OnboardingScreen()
            } else {
                CompositionLocalProvider(LocalNavSpace provides space) {
                    AnimatedContent(
                        targetState = nav.top,
                        modifier = Modifier.fillMaxSize().imePadding(),
                        transitionSpec = {
                            val dir = nav.direction
                            val spec: ContentTransform = when {
                                !enabled || transition == "none" -> EnterTransition.None togetherWith ExitTransition.None
                                dir == 0 -> (fadeIn(m.tween(dur, dur / 4)) + scaleIn(m.tween(dur), initialScale = 0.97f)) togetherWith fadeOut(m.tween(dur / 2))
                                // Skins: la pantalla nueva aparece tras el efecto que cruza la pantalla (ver SkinTransitionFx).
                                transition == "scan" -> fadeIn(m.tween(dur / 2, dur / 5)) togetherWith fadeOut(m.tween(dur / 5))
                                transition == "shutter" -> (fadeIn(m.tween(dur / 4, dur / 4)) + scaleIn(m.tween(dur / 2), initialScale = 0.985f)) togetherWith fadeOut(m.tween(dur / 4))
                                transition == "fade" -> fadeIn(m.tween(dur)) togetherWith fadeOut(m.tween(dur))
                                transition == "scale" -> (fadeIn(m.tween(dur)) + scaleIn(m.tween(dur), initialScale = if (dir > 0) 0.9f else 1.08f)) togetherWith
                                    (fadeOut(m.tween(dur)) + scaleOut(m.tween(dur), targetScale = if (dir > 0) 1.06f else 0.9f))
                                dir > 0 -> (slideInHorizontally(m.tween(dur, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(m.tween(dur))) togetherWith
                                    (slideOutHorizontally(m.tween(dur, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(m.tween(dur)))
                                else -> (slideInHorizontally(m.tween(dur, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(m.tween(dur))) togetherWith
                                    (slideOutHorizontally(m.tween(dur, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(m.tween(dur)))
                            }
                            spec.apply { targetContentZIndex = if (dir >= 0) 1f else -1f }
                        },
                        contentKey = { it.key },
                        label = "screens",
                    ) { route ->
                        holder.SaveableStateProvider(route.key) {
                            CompositionLocalProvider(LocalScreenKey provides route.key) { ScreenFor(route) }
                        }
                    }
                }
                // El efecto cruza el contenido pero va por debajo de la barra de navegación (antes la tapaba).
                SkinTransitionFx(nav.top.key)
                AnimatedVisibility(
                    miniBar,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = baseSpace + 6.dp),
                    enter = slideInVertically(m.spring(0.85f, 500f)) { it } + fadeIn(m.tween(220)),
                    exit = slideOutVertically(m.tween(200)) { it } + fadeOut(m.tween(160)),
                ) {
                    WorkoutMiniBar(app)
                }
                AnimatedVisibility(
                    showBar && !ime && onboarded,
                    Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(m.spring(0.85f, 500f)) { it } + fadeIn(m.tween(200)),
                    exit = slideOutVertically(m.tween(220)) { it } + fadeOut(m.tween(160)),
                ) {
                    BottomNav(items, selected = nav.tab.key, onSelect = { nav.switchTab(Route.tabOf(it)) })
                }
            }
          }
        }
        WorkoutRestWatcher(app)
        OverlayHost(app.overlays)
        Confetti(app.confetti)
        SkinCelebrate(app.confetti)
        AnimatedVisibility(
            boot != null, Modifier.fillMaxSize(),
            enter = fadeIn(m.tween(160)), exit = fadeOut(m.tween(380)),
        ) { SkinBootScreen(boot ?: skin.id) }
    }
}

@Composable
private fun ScreenFor(route: Route) {
    when (route) {
        Route.Home -> HomeScreen()
        Route.Exercises -> ExercisesScreen()
        Route.Routines -> RoutinesScreen()
        Route.Bot -> BotScreen()
        Route.Settings -> SettingsScreen()
        is Route.Category -> CategoryScreen(route.id)
        is Route.Exercise -> ExerciseScreen(route.id)
        is Route.Muscles -> MusclesScreen(route.id)
        is Route.Search -> SearchScreen(route.q)
        Route.MySet -> MySetScreen()
        is Route.RoutineDetail -> RoutineScreen(route.id)
        is Route.Workout -> WorkoutScreen(route)
        Route.Progress -> ProgressScreen()
        Route.Milestones -> MilestonesScreen()
        Route.Notes -> NotesScreen()
        is Route.SettingsPage -> SettingsPageScreen(route.page)
        Route.Changelog -> ChangelogScreen()
        Route.About -> AboutScreen()
        Route.DataTools -> DataScreen()
        is Route.Connector -> ConnectorScreen(route.id)
        Route.Sleep -> SleepScreen()
    }
}
