# FITSTAT — notas de trabajo (para retomar tras compactar contexto)

## Entorno / cómo compilar y probar
- Gradle: `export PATH=/opt/gradle/bin:$PATH; cd /home/claude/fitstat; gradle --offline <tarea> --console=plain`
  (quitar `--offline` solo si se agregan dependencias nuevas). Correr siempre en background (`run_in_background`) y leer el log; `sleep` está bloqueado.
- APK debug firmado: `gradle --offline :app:assembleDebug` → app/build/outputs/apk/debug/app-debug.apk. Release: `:app:assembleRelease`.
- Keystore persistente: /home/claude/fitstat/fitstat.keystore (alias fitstat). La contraseña va en la variable de entorno FITSTAT_KEY_PASSWORD. La keystore y la contraseña NO se suben al repositorio; incluirlas solo en el zip de código privado.
- Maven Central da 429 desde repo1.maven.org → ya está el espejo GCS en settings.gradle.kts.
- Pruebas visuales: Robolectric 4.16.1 modo NATIVE. Los jars de Android están en /home/claude/robolectric-deps (offline) — configurado en app/build.gradle.kts.
  - Helper `app/src/test/java/app/fitstat/Shots.kt`: `Shots.shot(rule, "nombre")` guarda PNG en /home/claude/fitstat/shots/nombre.png (usa PixelCopy directo; `captureToImage()` NO sirve en Robolectric).
  - Regla: `createAndroidComposeRule<ComponentActivity>()`, `@GraphicsMode(NATIVE)`, `@Config(sdk=[35], qualifiers="w411dp-h891dp-xhdpi")`.
  - Con `mainClock.autoAdvance=false` las animaciones infinitas corren; `Shots.settle` avanza el reloj de a 16 ms.
- Python: PIL, numpy, matplotlib disponibles. Scripts en /home/claude/fitstat/tools. Scratchpad: /tmp/claude-0/-home-claude/4136ee62-8443-5649-ac44-2988cbaa6a71/scratchpad
- Lucide (íconos, ISC): scratchpad/res/lucide/package/icon-nodes.json (1924 iconos). react-body-highlighter (MIT, polígonos del cuerpo anterior/posterior): scratchpad/res/npm/package/src/assets/index.ts (viewBox 0 0 100 200).
- Fotos de ejercicios: app/src/main/assets/ex/<id>_<0|1>.jpg (3:2, 640 px). Tratamiento duotono "neon-dark": sombras=ink #0C0B12, medios=acento*0.75, luces=#EDE8FF (LUT 3 tonos aplicada al decodificar, con caché).

## Decisiones de producto
- App en español (MX). Mascota FITBOT = robot con cabeza de mancuerna (placas a los lados, visor LED al centro).
- Modelo por defecto OpenRouter: `xiaomi/mimo-v2.6-flash`. API key en SharedPreferences privada (no se exporta, FITBOT no la ve).
- Máx. 10 categorías (Categories.all en data/Models.kt).
- Créditos: Lucide (ISC), free-exercise-db (Unlicense), react-body-highlighter (MIT), fuentes OFL de Google Fonts.

## Estado
- [x] Entorno + captura de pantallas con Robolectric
- [x] Catálogo 176 ejercicios + 352 fotos (data/CatalogData.kt generado por tools/build_catalog.py)
- [x] Sistema de diseño (íconos Lucide, LED, mascota, tema, componentes)
- [x] Datos / ajustes (esquema único en data/SettingsSchema.kt) / estadísticas
- [x] Pantallas: Inicio, Ejercicios, buscador predictivo, ficha, músculos, Rutinas, entrenamiento guiado, Progreso, Hitos, Notas
- [x] FITBOT: cliente OpenRouter (SSE + herramientas), 46 herramientas, chat, selector de modelo, adjuntos, audio, deshacer
- [x] Ajustes completos (personalización, FITBOT y API, widgets, recordatorios, ícono, datos, registro, acerca de)
- [x] Widgets (4, dibujados con Canvas), recordatorios (AlarmManager), 10 íconos alternativos (activity-alias), accesos directos
- [x] Pruebas: DataTest, BotTest (servidor falso), RemindersTest, y capturas (ScreensShotTest, SystemShotTest, WidgetPreviewTest, GalleryShotTest)
- [x] APK final firmado (v1.0, `gradle --offline :app:assembleRelease`) + zip de código entregados

## Mapa del código (app/src/main/java/app/fitstat)
- data/: modelos, Repo (única fuente de verdad, deshacer), SettingsSchema, Catalog*, Stats, Milestones, Changelog, Demo
- bot/: OpenRouter (HttpURLConnection), BotAgent (bucle de herramientas), ReadTools/WriteTools/ToolKit, Prompt, BotController, ChatStore, ModelInfo, Recorder, ApiKeyStore
- sys/: Reminders, Notifier, Receivers, AppIcon, Widgets* (WidgetArt dibuja con Canvas; WidgetInk = pincel), SystemHooks
- ui/design: componentes, íconos, LED, mascota; ui/theme; ui/app (Routes, AppState); ui/screens
- tools/gen_android_res.py regenera íconos, accesos directos, XML de widgets, strings y AndroidManifest.xml (NO editar esos archivos a mano)

## Pruebas (solo lo afectado; la suite completa — 70 pruebas — tarda ~1–2 min con Gradle ya caliente)
- `gradle --offline :app:testDebugUnitTest --tests 'app.fitstat.BotTest' --tests 'app.fitstat.RemindersTest' --tests 'app.fitstat.DataTest'` (rápidas, JVM puro)
- Capturas: `--tests 'app.fitstat.SystemShotTest'` (FITBOT + Ajustes), `WidgetPreviewTest` (shots/widgets/*), `ScreensShotTest` (resto de pantallas)
- Vistas previas de widgets del selector: salen en shots/widgets/previews/ y se copian a res/drawable-nodpi/widget_preview_{today,bot,week,records}.png

## Notas de la entrega v1.0
- Release: `gradle --offline :app:assembleRelease` → app/build/outputs/apk/release/app-release.apk (firmado con fitstat.keystore, esquema v2, minSdk 26, targetSdk 35).
- lint está desactivado para release (`lint { checkReleaseBuilds = false }`): lint-gradle no se puede bajar sin conexión.
- Verificación del APK: `apksigner verify --print-certs`, `aapt2 dump badging`, `aapt2 dump xmltree --file AndroidManifest.xml`, `zipalign -c -P 16 -v 4`.
- La app NO se probó en un teléfono físico: solo Robolectric (modo NATIVE) + capturas. Lo que depende del sistema (alarmas exactas, widgets en el lanzador, cambio de ícono, permisos) hay que confirmarlo en un dispositivo real.

## v1.1 (29 oct 2026)
- Bug raíz: al cancelar/terminar un entrenamiento, `WorkoutScreen` (aún visible en su animación de salida) volvía a crear la sesión con `ensureWorkout`. Ahora `closeWorkout()` saca primero las pantallas de la pila (`Nav.removeWhere`) y la pantalla solo crea sesión si su ruta sigue en la pila.
- Salidas: ✕ en el encabezado y en la barra «en curso» (hoja Finalizar/Cancelar/Seguir), aviso al salir sin series, `requestFinishWorkout` / `requestCancelWorkout` en WorkoutScreen.kt.
- FITBOT: `get_workout_status`, `finish_workout`, `cancel_workout(keep_sets)`; `BotHost.workoutStatus/finishWorkout/cancelWorkout`.
- Pruebas nuevas: WorkoutFlowTest (10) y 4 en BotTest.

## v1.5 (29 oct 2026) — skins
- Skins `carmesi` y `acero` (originales; logos y mascotas propios) + `fitstat` clásica. Datos en data/Skins.kt; ajustes `skin.id`, `skin.icon_sync` y 13 ajustes por skin (`skin.<id>.accent|custom|surface|cut|bg|fx|font|led|caps|codes|words|anim|mascot`) generados en `Settings.skinSpecs()`.
- `SkinStyle.from(prefs)` (ui/theme/Skin.kt) resuelve la skin; `Fs.skin` la expone. Las skins son siempre oscuras. `FitstatTheme` se puede anidar (la galería de skins dibuja cada ficha con su tema).
- Formas: `FsShapes(k, buttonStyle, kind, skinId)` (`card/inner/chip/small/sheet/button/pill/round/toast/navBar/navItem`). No usar `CircleShape` en pantallas: `Fs.shapes.round/pill`.
- Mascota: `ui/design/Mascot.kt` despacha a `drawSkinHead` (SkinHeads.kt: Centinela y Vigía) cuando `Fs.skin.head != null`.
- Animaciones propias: SkinFx.kt (SkinHost, SkinBackdrop, skinCardDecor, SkinMark/SkinLogo, SkinTransitionFx, SkinBootScreen, SkinCelebrate); transiciones `scan`/`shutter` en FsMotion.
- Ícono del lanzador: `activity-alias` `.IconCarmesi`/`.IconAcero` (tools/gen_android_res.py + tools/skin_marks.py); `AppIcon.wanted(prefs)` decide cuál. No editar a mano los XML generados ni SkinMarkData.kt/IconMarkData.kt.
- Tipografía Chakra Petch solo la usa la skin Acero (no está en `type.font`, para no romper `changelogClaimsMatchReality`).
- Pruebas nuevas: SkinTest (lógica/datos/manifiesto) y SkinShotTest (recorrido con capturas en shots/skin_*). Prompt de FITBOT incluye «Skin activa».
- No probado en teléfono físico: cambio real del ícono del lanzador y rendimiento de las animaciones en equipos modestos.

## v1.6 (29 oct 2026) — inicio personalizable + selector de modelo
- **Entorno de esta sesión**: JDK 21, Gradle 8.14.3 en /opt/gradle, SDK en /home/claude/android-sdk (`local.properties` con `sdk.dir=...`; no se incluye en el zip). Las dependencias de pruebas NO estaban en caché → correr gradle **sin** `--offline` (el espejo GCS de settings.gradle.kts funciona). Robolectric necesita `android-all-instrumented-15-robolectric-13954326-i7.jar` en /home/claude/robolectric-deps (bajado de maven-central.storage-download.googleapis.com; build.gradle.kts ya lo usa en modo offline).
- Inicio: `data/HomeLayout.kt` (catálogo de 14 widgets, tamaños S/M/L = 2/3/6 de 6 columnas, `pack`, `resize`, `move`, `legacyDefault`), `AppData.homeLayout` (null = nunca personalizado), `Repo.homeSlots/setHomeLayout`, `HomeEditor` (copia de trabajo, un solo snapshot de deshacer al pulsar «Listo»; también se guarda al salir de la pantalla o con atrás).
- UI: `HomeFlow` es un `Layout` plano con hijos con clave (cada widget conserva su estado al reordenar); las filas con varios widgets igualan su alto con `minIntrinsicHeight` (si algún widget no admite intrinsics, `runCatching` cae a medir cada uno). Los widgets M/S no usan `BoxWithConstraints` (LedBanner y Heatmap solo van en L, siempre solos en su fila).
- Gestos: toque largo para entrar a editar se vigila en la pasada Initial (`longPressToEdit`) y se «come» el resto del gesto para que no abra la pantalla del widget al soltar. En edición: `LocalTapsDisabled` (en `tappable`) + capa transparente que se queda los toques; arrastre desde el asa (inmediato) o con toque largo (`homeReorder`). El intercambio se bloquea con el último widget tocado hasta que el dedo salga de su recuadro (evita rebotes). Auto-desplazamiento cerca de los bordes.
- Se eliminaron los 8 ajustes `home.show_*` (la migración los lee de `Prefs.raw` si el usuario nunca personalizó). Quedan `home.start|nav|labels|greeting|week_start`.
- Chat de FITBOT: `BotHeader` ya no tiene `ModelChip` (queda en Ajustes › FITBOT); `RoundButton(Ic.Cpu)` junto al ⋮ (testTags `bot_model`, `bot_menu`).
- FITBOT: `get_home_layout`, `edit_home` (reset/remove/add/resize/move); `BotHost.saveHomeEdit()` guarda lo que el usuario llevaba editando antes de aplicar el cambio.
- Pruebas nuevas: HomeLayoutTest (20: lógica, editor, migración, herramientas de FITBOT) y HomeEditTest (16, Robolectric: toque largo, quitar, achicar/agrandar, arrastre, agregar, vacío, atrás, ajustes, encabezado del chat; capturas shots/v16_*). Se corrigieron SkinTest y BotTest (esperaban nombres de skin anteriores a 1.5.1).
- No probado en teléfono físico: el gesto con dedo real (toque largo + arrastre + auto-desplazamiento) solo se probó con eventos simulados de Robolectric.

## v1.7 (29 oct 2026) — sueño, conectores, Namida, +ejercicios, animaciones de FITBOT
- **Conectores**: `conn/Connectors.kt` (`Connectors.all`, `ConnectorHub`: estado/permisos, `watchRest`, `syncSleep`, `autoSync`), `conn/Music.kt` (`MusicState`, `MusicPlayer`, `VolumeControl`, `FadeStore`, `MusicRestCoordinator`), `conn/NamidaPlayer.kt` (MediaSessionManager + `FitstatListener` como NotificationListenerService; paquetes `com.msob7y.namida` y `.debug`), `conn/AndroidPlatform.kt` (permisos, Health Connect 1.1.0, UsageStatsManager, volumen). Todo detrás de interfaces (`ConnPlatform`, `MusicPlayer`, `VolumeControl`) para probar sin Android: dobles en `src/test/.../ConnFakes.kt`.
- Música en el descanso: `AppState` llama `connectors.watchRest { workout.restEndsAt }` (snapshotFlow + collectLatest). Solo reanuda lo que FITSTAT pausó; si el usuario toca play/volumen se respeta; `FadeStore` (SharedPreferences) deja el volumen bien si el proceso muere a media subida. **Limitación conocida**: si el proceso muere durante el descanso, la música queda pausada.
- **Sueño**: `data/Sleep.kt` (lógica pura: `nights`, `dedupe` por solape >50 % con prioridad MANUAL 4 > HEALTH 3 > PHONE 1, `cluster` ≤60 min misma fuente — el rato levantado cuenta como despierto aunque no haya fases —, `summary`, `estimateFromScreen`), `AppData.sleep`, `Repo.addSleep/deleteSleep/mergeSleep` (mergeSleep no deja punto de deshacer). La estimación del teléfono une apagados con encendidos de ≤10 min, toma el tramo más largo por madrugada (≥ `sleep.phone_gap` h, punto medio entre 21:00 y 11:00, máx. 14 h; id `phone_<fecha>`). **Fases solo desde Health Connect**; nunca se inventan.
- Ajustes nuevos: páginas `conectores` y `sueno` (`conn.namida.enabled|pause_rest|resume_rest|fade|fade_start`, `conn.health.enabled`, `conn.phone.enabled`, `sleep.goal|auto_sync|phone_gap`). Rutas `Route.Sleep` y `Route.Connector(id)`; FITBOT las abre con `sueno`, `conectores`, `conector:<id>`.
- Inicio: 16 widgets. `optIn` en `HomeWidgetDef` = no entra en `default()`/`legacyDefault` pero sí en `missing()` (así «Música» se ofrece en «Agregar» pero no aparece sola).
- FITBOT: herramientas `list_connectors`, `connect_connector`, `get_music`, `music_control`, `get_sleep`, `log_sleep`, `delete_sleep`, `sync_sleep` (`bot/ConnectorTools.kt`); `BotHost.connectors()`. El prompt debe seguir <14 000 caracteres (BotTest lo comprueba).
- Catálogo: 423 ejercicios (free-exercise-db, dominio público) — `tools/build_catalog.py tools/exercises.json` con `catalog_src.txt` (líneas `id_dataset|Nombre|categoria|equipo|alias1,alias2|Descripción`; imágenes con ImageMagick a 560 px, calidad 64). El APK creció ~8 MB por las fotos nuevas. `DataTest` exige `Catalog.all.size >= 176` porque el changelog de la 1.0 dice 176.
- Mascota (`ui/design/Mascot.kt`): estados de ánimo mezclados con `Animatable` (440 ms), dos parpadeos con periodos inconmensurables, bucles de ciclo entero para que no den tirón, fantasma de los adornos al cambiar de estado.
- Archivos generados: AndroidManifest/atajos/XML de widgets/strings los genera `tools/gen_android_res.py` (el manifiesto ya declara `FitstatListener`, permisos de Health Connect y `PACKAGE_USAGE_STATS`); `IconData.kt` lo genera `tools/build_icons.py` (Music y Watch se agregaron a mano).
- Pruebas nuevas: `SleepTest` (30), `ConnectorsTest` (48: coordinador con tiempo simulado, estado/permisos, sincronización, herramientas de FITBOT, prompt, changelog) y `ConnectorsUiTest` (14, Robolectric; capturas `shots/v17_*`). Suite completa: 214 pruebas, ~3–4 min. Se ajustó `HomeEditTest.addSheetListsOnlyMissingWidgets` (con 16 widgets el botón «Agregar» queda detrás de la barra inferior: se baja la lista antes de tocar).
- No probado en teléfono físico ni con Namida real: MediaSession/NotificationListener, Health Connect real, UsageStats y el volumen de verdad. Esas partes están detrás de interfaces y se probaron con dobles; lo demás (pantallas, widgets, FITBOT) sí se vio en Robolectric.

## v1.7.1 (29 oct 2026)
- `SkinTransitionFx` (persianas MILITECH / escaneo ARASAKA) se dibuja ahora entre el contenido y la barra de navegación en `ui/app/Shell.kt` (antes iba encima de todo y tapaba la barra). Pruebas: `ConnectorsUiTest.*TransitionDoesNotCoverTheNavigationBar` (captura a media transición y comprueba que la barra se ve).
- Versión 1.7.1 (código 9). Los logos de las skins no se cambiaron (ver mensaje de la entrega).

## v1.8 (29 oct 2026) — animaciones con la música + distribución de Ejercicios/Rutinas
- Versión 1.8 (código 10). `Changelog` "1.8".
- Distribución: `HomeWidgetDef.page` (null = widget del inicio, usable en cualquier pantalla), `LayoutPage`, `HomeLayout.default/effective/sanitize/add/missing(page)`, bloques propios (`ex_search`, `ex_set`, `cat:<id>`, `ex_create`, `rt_free`, `rt:<id>` dinámicos, no removibles). `AppData.pageLayouts` (Map<String, List<HomeSlot>>). `Repo.pageSlots/defaultSlots/pageCustomized/setPageLayout`. `PageEditor` (antes HomeEditor; `typealias HomeEditor`). `EditablePage` es la pantalla genérica; `longPressToEdit` va en la LazyColumn (pase Initial) con `enabled = !editing`. Pruebas: `PageLayoutTest` (lógica, repo, editor, FITBOT) y `PageEditUiTest` (gestos, quitar/agrandar/agregar, arrastrar, rutinas, ajustes).
- Animaciones: `RhythmFrame` (nivel, graves, medios, agudos, golpe, 32 barras, 4 pulsos), `RhythmAnalyzer` (AGC + detección de golpes de bombo con calentamiento), `RhythmSim` (tempo fijo determinista), `RhythmEngine` (real vs aproximado con motivo `RhythmWhy`, sin señal 2.5 s → aproximado, reintento a los 8 s, calidad 20/40/todos los cuadros), `AudioTap`/`VisualizerTap`, `MusicFx.kt` (8 estilos, `Modifier.musicFx(target)`, `coverShake()`, `RhythmDriver`). `AppState(rhythm=…)` se puede inyectar en pruebas. Pruebas: `RhythmTest` (FFT, analizador, simulación, motor con `FakeTap`) y `MusicFxUiTest` (los 8 estilos dibujados fuera de pantalla, hoja de estilos `shots/v18_fx_styles.png`, inicio con música, ajustes).
- Trampas de pruebas: la vista previa de ajustes anima sin parar, así que ahí no se puede usar `performScrollTo` (se cuelga esperando reposo): usar `performSemanticsAction(OnClick)` o `Shots.settle`. `Dispatchers.Main.immediate` + `hub.refresh()` antes de `setContent` para que la música cuente como sonando.
- Sin probar en un teléfono físico: lectura real del audio (Visualizer sesión 0), gestos del toque largo en pantalla, consumo de batería.
- Comando de la suite completa: `./gradlew :app:testDebugUnitTest --continue -q` (~5 min).

## v1.8.1 (29 oct 2026) — tablet / horizontal / widgets
- Versión 1.8.1 (código 11). Pruebas: `TabletUiTest` (capturas `shots/v181_*`), `WidgetPreviewTest.rendersTabletSizesWithoutStretching` y `usesTheSizesTheLauncherReports`. Para crear un widget en Robolectric: `shadowOf(AppWidgetManager).createWidget(...)`.

## v1.8.2 (29 oct 2026) — Spotify y YouTube Music
- Versión 1.8.2 (código 12). `MusicApps`, `ConnectorHub.musicStatus/musicCurrent/musicReady/musicName`, `MusicState.source`, `ConnOpen.Spotify/YtMusic` + `musicOpen(id)`. Pruebas: `MusicAppsTest`; `FakePlayer(source=…)`.
- No se probó con las apps reales (MediaSession de Spotify/YT Music) en un teléfono físico.

## v1.8.3 (29 oct 2026) — color de la portada + desvanecido al pausar
- Versión 1.8.3 (código 13). `MusicTintHost` (`ui/design/MusicTint.kt`) en `FitstatRoot`; `coverTone` (aclara/oscurece en pasos hasta luminancia 0,16–0,80) y `FsColors.tinted`. `RhythmEngine.fadeLevel` + `RhythmFrame.faded(dt, now, tau)`; `coverShake` y `musicFx` usan `RhythmEngine.smooth(fadeLevel)`. Ajustes nuevos: `fx.fade_out`, `fx.fade_secs`, `music.tint`, `music.tint_strength`.
- Pruebas: `FadeTintTest` (10). Al escribirlas: `Color.mix` interpola en espacio sRGB, así que el contraste se verifica con el mismo `luminance()` de la prueba y no con una fracción fija; la muestra "normal" se cambió a `#E02060` (`#B02040` tiene luminancia 0,106 y sí cae en el rango oscuro). Con la opción apagada, `fadeLevel` va a 0 al instante, pero la energía visual decae con τ=0,25 s, así que la prueba espera 1,5 s.
- Pendiente de verificar en teléfono: el tinte recompone toda la app durante la transición (≈1 s); medir si hay tirones en gama baja.

## v1.8.4 (29 oct 2026) — corrección del color de la portada
- Versión 1.8.4 (código 14). Causa: `coverColorOf` promediaba todos los píxeles → gris en portadas cromadas; el tinte de fondo (12 %) apenas se notaba y hasta oscurecía. Ahora tono dominante por histograma de matiz + `vivid` + fondo/tarjetas/bordes teñidos.
