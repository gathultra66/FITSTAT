# FITSTAT 1.9.2 — código fuente

App de fitness para Android (Kotlin + Jetpack Compose). Paquete `app.fitstat`, Android 8.0 o superior.

## Cómo compilar
- **Android Studio** (versión reciente, 2025.1 o más nueva): abre esta carpeta, espera la sincronización y ejecuta *Build › Build APK(s)*.
- **Terminal**: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (necesita JDK 17 o 21 y el SDK de Android 36; Android Studio crea `local.properties` solo).

## Firma — guarda bien estos archivos
- `fitstat.keystore` (alias `fitstat`; la contraseña se guarda aparte, en la variable de entorno `FITSTAT_KEY_PASSWORD`) es la llave con la que se firma el APK. Por seguridad, no está incluida en este repositorio.
- Para **actualizar** la app sin desinstalarla (y sin perder tus datos) hay que firmar siempre con esta misma llave.
  Si la pierdes, tendrás que desinstalar e instalar de nuevo.
- Si publicas el código en un sitio público, no incluyas la keystore ni su contraseña (el build la lee de `FITSTAT_KEY_PASSWORD`, no está escrita en el código).

## FITBOT
- En *Ajustes › FITBOT y API* pega tu clave de OpenRouter y elige el modelo (por defecto `xiaomi/mimo-v2.6-flash`).
- La clave se guarda cifrada en el teléfono y no se incluye al exportar tus datos.

## Fin del descanso (v1.9.2)
- Causa del error: la pantalla calculaba el descanso con el reloj de pared, pero el aviso de fin y la reanudación de la música esperaban con un `delay()`, que solo cuenta el tiempo con el teléfono despierto. Al pausar la música el teléfono se dormía con la pantalla apagada, el `delay` se congelaba y el descanso se quedaba en 0:00 (y la música sin volver) hasta que el teléfono despertaba y se cumplía el tiempo que faltaba.
- Arreglo: `data/WallClock.kt` (`awaitWallClock`) espera en tramos de 500 ms mirando el reloj de pared en cada uno; lo usan `WorkoutRestWatcher` y `ConnectorHub.watchRest`. `sys/RestAlarm.kt` programa una alarma del sistema (`AlarmManager`, como los recordatorios) para la hora de fin; `ReminderReceiver` la atiende con `AppState.finishRestIfDue` + `MusicRestCoordinator.onRestEnd`. Las dos rutas son idempotentes: da igual cuál llegue primero.
- Pruebas: `WallClockTest` (y las de `watchRest` en `ConnectorsTest`, que usan tiempo simulado y deben seguir igual). Límites: no se compiló ni se probó en un teléfono; la alarma exacta depende de que el sistema la permita (si no, se usa una aproximada que puede tardar), y la receptora no hace nada si el sistema ya cerró la app.

## Portadas de NAMSTAT (v1.9.1)
- NAMSTAT publica en su MediaSession solo `DISPLAY_ICON_URI` = el `content://media/…` del audio (sin bitmap), que FITSTAT no puede abrir (no tiene permiso sobre la música). `NamidaPlayer` ahora convierte ese enlace con `NamstatPackage.artUri` a `content://com.namstat.player.tat/art?u=…&size=640`, que NAMSTAT 1.2.1+ sirve desde `TatProvider.openFile` (solo apps permitidas, solo `content://media/`). Prueba: `NamstatTest.sharedAudioLinksBecomeArtRequests`. Límites: la parte del proveedor (NAMSTAT) no tiene prueba automática y no se vio en un teléfono; si el ajuste «carátula en la notificación» de NAMSTAT está apagado, NAMSTAT no comparte ningún enlace y no hay portada.

## Conector de NAMSTAT (v1.9)
- Enlace T-A-T v1 (`conn/NamstatLink.kt`, `conn/Namstat.kt`): `ContentResolver.call(content://com.namstat.player.tat, método, null, Bundle{json})` → `Bundle{json}`. `NamstatLink` es la interfaz (real: `AndroidNamstatLink`; pruebas: `FakeNamstat`); `NamstatClient` traduce las respuestas (`NsState`, `NsTrack`, `NsQueue`, `NsPlaylists`, `NsStats`, `NsSearch`) y manda las órdenes. Si NAMSTAT está cerrado (`not_running`), las órdenes lo abren con `tat_cmd`/`tat_back` (NAMSTAT valida quién lo abrió).
- `ConnectorHub.namstat` (cliente), `watchWorkout` (avisa a NAMSTAT al empezar/terminar un entrenamiento y pone la «Lista para entrenar» si no suena nada), `MusicRestCoordinator(link=…)` (al reanudar el descanso pide `play_fade`; si falla cae a la subida de volumen del teléfono).
- Pantalla: `ui/screens/NamstatPanel.kt`. FITBOT: `bot/NamstatTool.kt` (herramienta `namstat`, bloquea hasta 4,5 s la hebra principal esperando a NAMSTAT).
- Ajustes: `conn.namstat.*` (enabled, pause_rest, resume_rest, fade, fade_start, workout_sync, workout_list, workout_shuffle).
- Pruebas: `NamstatTest` (con un NAMSTAT falso).
- Límites: no se probó entre dos apps reales en un teléfono; NAMSTAT identifica a FITSTAT solo por el nombre del paquete (no por firma): sirve contra apps ajenas, no contra una app que se haga pasar por FITSTAT. Las animaciones siguen leyendo el audio global del teléfono (`Visualizer(0)`); la sesión de audio que da NAMSTAT aún no se usa. El widget del inicio sigue siendo el de música común (portada y botones por la sesión de Android).

## Corrección del color de la portada (v1.8.4)
- `coverColorOf` (`ui/design/MusicFx.kt`) ya no promedia todos los píxeles (daba gris): reparte por tono (12 grupos), pesa los vivos, toma el tono dominante y promedia sus píxeles; sin tonos devuelve el promedio. `coverTone` (`MusicTint.kt`) además sube la saturación mínima a 0,45 si el color tiene tono (`vivid`). El tinte ahora también toca fondo (40 %), tarjetas (24 %) y bordes. Pruebas: `MusicFxUiTest` (cromo azulado → azul; gris puro → gris).
- Límites: no se vio en un teléfono físico; el matiz depende de la portada y se verificó solo con imágenes sintéticas. Una portada con varios colores parejos toma uno solo (el de mayor peso).

## Color de la portada y desvanecido (v1.8.3)
- Color de la portada (`ui/design/MusicTint.kt`): `MusicTintHost` envuelve la app (en `ui/app/Shell.kt`, dentro de `FitstatRoot`) y da `LocalColors` teñido. La fuerza (`strength`) sube a 1 con música reproduciéndose (900 ms) y baja a 0 al pausar o terminar (3,2 s, suave); el color de la portada (`app.rhythm.coverColor`) se desliza 1,4 s al cambiar de canción. `FsColors.tinted(cover, s)` mezcla los acentos (`accent`, `accent2`, `onAccent`, `accentText`, `accentSoft`) y da un 12 % de fondo; el resto de colores no cambia. `coverTone` aclara u oscurece el color en pasos hasta caer en un luminancia de 0,16–0,80 para que se lea como acento.
- Desvanecido (`conn/RhythmEngine.kt`, `conn/Rhythm.kt`): `fadeLevel` (0–1) sube en 0,3 s cuando suena música y, con `fx.fade_out` activo, baja linealmente en `fx.fade_secs` al pausar o terminar. Mientras tanto la energía se apaga con `RhythmFrame.faded` (constante de tiempo 0,8 s). `musicFx` y `coverShake` multiplican su intensidad por la curva suave (`RhythmEngine.smooth`) de `fadeLevel`. Con la opción apagada, `fadeLevel` va a 0 de inmediato y el movimiento se calma en unos 0,25 s.
- Ajustes › Animaciones de música: `fx.fade_out` (sí), `fx.fade_secs` (2,5 s, 0,5–6), `music.tint` (sí) y `music.tint_strength` (80 %, 20–100).
- Pruebas: `FadeTintTest` (10 pruebas: subida/bajada del desvanecido, duración, opción apagada, reanudar durante el desvanecido, curva, contraste del color y mezcla del tinte en 0, 50 y 100 %).
- Límites: no se probó en un teléfono físico. El tinte cambia toda la interfaz durante la transición (≈1 s): en teléfonos lentos puede notarse un tirón. Con Spotify y YouTube Music reales el comportamiento del desvanecido depende de cuándo esas apps reporten la pausa; solo se probó con dobles de prueba. Los colores de portadas casi negras o casi blancas se ajustan a propósito, así que no son exactamente los de la portada.

## Spotify y YouTube Music (v1.8.2)
- Nuevos conectores `spotify` (`com.spotify.music`) y `ytmusic` (`com.google.android.apps.youtube.music`), además de `namida`. Los tres usan la misma escucha de sesiones de reproducción (`conn/NamidaPlayer.kt`, que ahora atiende solo las apps activadas y, si suenan varias, la que está reproduciendo) y el mismo permiso de acceso a notificaciones. Lista de apps: `MusicApps` (mismo archivo); para sumar otra app de música basta una entrada ahí, otra en `Connectors.all` y sus ajustes `conn.<id>.*` (enabled, pause_rest, resume_rest, fade, fade_start).
- `MusicState.source` dice qué app suena; el descanso (`MusicRestCoordinator`) usa los ajustes de esa app. `ConnectorHub.musicStatus()/musicCurrent()/musicReady()` resumen las tres para el widget, las animaciones y FITBOT.
- Límites: no se probó con las apps reales ni en un teléfono físico (solo la lógica y las pantallas con dobles de prueba). Spotify y YouTube Music deben estar abiertas con algo cargado. Los anuncios de Spotify cuentan como música sonando. Esto no usa ninguna cuenta ni API de Spotify/Google: solo controla lo que ya suena en el teléfono, así que no puede buscar canciones ni poner listas.

## Tablet, horizontal y widgets (v1.8.1)
- La actividad ya no está fija en vertical (`AndroidManifest.xml`: sin `screenOrientation`, con `configChanges` y `resizeableActivity`). En `ui/app/Shell.kt` el contenido va en una columna centrada de `ContentMaxWidth` (760 dp, `ui/design/Surfaces.kt`) con márgenes laterales del sistema; hojas (680 dp) y avisos (560 dp) también se acotan. El fondo sigue a todo lo ancho.
- Widgets del escritorio (`sys/Widgets.kt`, `sys/WidgetArt.kt`): se dibujan al tamaño que informa el lanzador (`OPTION_APPWIDGET_SIZES` en Android 12+, con un `RemoteViews` por tamaño) y la imagen usa `fitCenter` (antes `fitXY`, que deformaba). En widgets grandes el diseño se amplía hasta 1.6× (`designZoom`).
- No se probó en una tablet física: solo con Robolectric (tablet horizontal 1280×800 dp, vertical 800×1280 y teléfono acostado) y con imágenes de los widgets en tamaños de tablet. No hay un diseño de dos paneles: es la misma interfaz de teléfono en columna.

## Animaciones con la música y distribución de Ejercicios/Rutinas (v1.8)
- **Animaciones** (`ui/design/MusicFx.kt`, `conn/Rhythm.kt`, `conn/RhythmEngine.kt`, `conn/AudioTap.kt`, pantalla `ui/screens/MusicFxScreen.kt`, ajustes `fx.*` en `data/SettingsSchema.kt`): detrás de la tarjeta de la meta semanal y del widget de música del inicio. 8 estilos (Barras, Ondas, Pulso, Partículas, Aurora, Osciloscopio, Radial y Mezcla), la portada vibra apenas al ritmo y todo se personaliza en *Ajustes › Animaciones de música* (con vista previa en vivo). Solo se animan mientras Namida suena (o en la vista previa). Arasaka y Militech usan las mismas por ahora: para darles las suyas, `drawMusicFx` recibe el estilo y basta con ramificar por skin.
- **Ritmo real o aproximado**: con el permiso de audio, `VisualizerTap` (`android.media.audiofx.Visualizer`, sesión 0) mide graves/medios/agudos del audio que sale del teléfono y `RhythmAnalyzer` detecta golpes; si no hay permiso, el sistema lo bloquea o no llega señal, `RhythmSim` da un ritmo a un tempo fijo (*Ajustes › Animaciones de música › Tempo*) y la pantalla lo dice («aproximado» y el motivo). Nunca se presenta como real lo que no lo es.
- **Distribución por pantalla**: *Inicio*, *Ejercicios* y *Rutinas* usan el mismo editor (`ui/app/PageEditor.kt`, `ui/screens/EditablePage.kt`): mantén pulsado un contenedor o cualquier parte de la pantalla. A Ejercicios y Rutinas se les pueden agregar los widgets del inicio. Se guarda en `AppData.pageLayouts` (`"exercises"`, `"routines"`; el inicio sigue en `homeLayout`). Las tarjetas de rutina se mueven y cambian de tamaño pero no se quitan; sus opciones están en el botón ⋮.
- FITBOT: `edit_home` y `get_home_layout` aceptan `page` (inicio, ejercicios, rutinas) y hay una herramienta nueva, `get_music_fx` (57 en total); las animaciones se cambian con `set_settings` (`fx.*`).

### Límites que conviene saber (v1.8)
- **No se probó en un teléfono físico.** El análisis del ritmo, el dibujo de los 8 estilos, el editor y los gestos se probaron con Robolectric y señales sintéticas; el rendimiento y el consumo de batería reales no se midieron (hay un ajuste de calidad para bajarlos).
- Leer el audio de Namida **no está garantizado**: Android decide, y Namida no expone su sesión de audio, así que se usa la sesión global (0). En algunos teléfonos o versiones llega señal y en otros no (o llega solo con el volumen alto). Cuando no llega, las animaciones usan el ritmo aproximado, que late a un tempo fijo y **no sigue la canción**.
- Las animaciones necesitan que Namida esté conectada (Ajustes › Conectores). Con la música en pausa no se ven (salvo la opción «Fondo suave con la música en pausa»).
- Ya no existe el menú por toque largo de las rutinas: el toque largo abre el editor, y el menú está en el ⋮.
- El toque largo «en la pantalla» solo funciona sobre el fondo de la lista: sobre un botón o una tarjeta con su propio toque largo (por ejemplo arrastrar en modo edición) hace lo suyo.

## Sueño, conectores y Namida (v1.7)
- **Conectores** (*Ajustes › Conectores*): lista de apps y servicios que se conectan a FITSTAT, cada uno con su estado (sin conectar, falta un permiso, conectado) y el botón que abre el permiso que falta. Hoy: **Namida**, **Health Connect** y **Uso del teléfono**. Para agregar otro: una entrada en `Connectors.all` (`conn/Connectors.kt`) y sus ajustes `conn.<id>.*`; FITBOT lo conoce solo porque el prompt y `list_connectors` leen esa lista.
- **Namida** (`conn/NamidaPlayer.kt`, `conn/Music.kt`): lee y controla la sesión de reproducción de Android (MediaSession) de `com.msob7y.namida`; para verla Android exige el *Acceso a notificaciones* (`conn/FitstatListener.kt`, FITSTAT no lee ni guarda notificaciones). Al empezar el descanso entre series la música se pausa; al terminar vuelve con el volumen bajo y sube poco a poco hasta el de antes (`MusicRestCoordinator`). Solo reanuda lo que FITSTAT pausó. Pausar, reanudar, los segundos de subida y el volumen inicial se cambian en *Conectores › Namida*. Widget **Música** para el inicio (carátula, anterior, play/pausa, siguiente): se agrega desde «Agregar» (no viene puesto).
- **Sueño** (*Sueño*, widget del inicio, y FITBOT con `get_sleep`, `log_sleep`, `delete_sleep`, `sync_sleep`): noches con hora de dormir y despertar, tiempo dormido contra la meta (*Ajustes › Sueño*), regularidad y tendencia de 7, 14 o 30 noches. Tres fuentes, de mayor a menor prioridad: lo que registras a mano, **Health Connect** (reloj o pulsera; es **lo único que trae fases** ligero/profundo/REM) y una **estimación con el uso del teléfono** (el tramo más largo de la noche sin tocarlo; solo horas, siempre marcada «estimado»). Un teléfono solo no puede saber en qué fase del sueño estás y FITSTAT no lo inventa.
- Ejercicios: 423 (antes 176), con más de 240 variaciones nuevas. Fuente: free-exercise-db (dominio público) vía `tools/build_catalog.py` y `tools/catalog_src.txt`.
- FITBOT: animaciones más fluidas (mezcla de estados de ánimo, parpadeo no mecánico, bucles sin tirones) en `ui/design/Mascot.kt`.
- Inicio: ahora 16 widgets (se suman Sueño y Música).

### Límites que conviene saber
- Las fases del sueño solo existen si tu reloj o pulsera las guarda en Health Connect.
- La estimación con el teléfono es de horas, no una medición: usar el teléfono a medianoche la acorta y dejarlo sin tocar estando despierto la alarga. Android guarda más o menos 7 días de ese historial, así que conviene abrir FITSTAT seguido.
- Namida debe estar abierta (puede estar en segundo plano) y con algo cargado; FITSTAT no la arranca. En Android 13 o más nuevo, si la app se instaló fuera de Play Store, hay que permitir «ajustes restringidos» antes de dar el acceso a notificaciones (la pantalla del conector lo explica).
- Si Android cierra FITSTAT mientras la música está pausada por un descanso, no hay quien la reanude: se reanuda a mano en Namida. Si se cierra a media subida de volumen, al abrir la app el volumen vuelve al de antes.
- Nada de esto se probó en un teléfono físico ni con Namida de verdad: se probó la lógica y las pantallas con dobles de prueba y Robolectric.

## Inicio personalizable y selector de modelo (v1.6)
- En *Inicio*, **mantén pulsado cualquier widget** para editar: arrastra (desde el asa de arriba a la derecha o manteniendo pulsado) para moverlos, usa los botones de abajo para achicar o agrandar (pequeño = 3 por fila, mediano = 2, grande = fila completa), ✕ para quitar y **Agregar** para volver a ponerlos. **Listo** guarda todo con un solo *Deshacer*. También está en *Ajustes › Inicio y navegación*.
- 16 widgets desde la 1.7 (`data/HomeLayout.kt`; en la 1.6 eran 14): meta semanal, hoy, entrenar, FITBOT, progreso, hitos, notas, racha, volumen, récords, próximo hito, mapa muscular, calendario de actividad, notas fijadas, sueño y música (este último no viene puesto). La disposición se guarda en `AppData.homeLayout` (null = nunca personalizada). Quien actualiza desde la 1.5 conserva apagadas las tarjetas que tenía ocultas en «Tarjetas visibles» (esos ajustes ya no existen).
- FITBOT puede verlo y cambiarlo con `get_home_layout` y `edit_home`.
- Chat de FITBOT: el selector de modelo pasó a un recuadro junto al de opciones (⋮); el nombre del modelo sigue en ese menú.
- Archivos: `data/HomeLayout.kt` (lógica), `ui/app/PageEditor.kt` (edición, antes HomeEditor.kt), `ui/screens/HomeScreen.kt`, `HomeWidgets.kt` (los widgets en sus tres tamaños) y `HomeEdit.kt` (cuadrícula, arrastre y controles).

## Skins (v1.5)
- *Ajustes › Skins* (arriba de *Tema y color*) cambia el diseño completo: colores, formas, tipografía, fondo, mascota, logo, ícono del lanzador y animaciones.
- Skins: **FITSTAT** (clásica), **Carmesí** (oscura, puntos LED, rojo) y **Acero** (terminal industrial, esquinas cortadas, azul plateado). Son diseños originales, con logo y mascota propios (Centinela y Vigía).
- Cada skin se personaliza aparte (`skin.<id>.*`: color, superficies, esquinas, fondo, efectos, tipografía, números LED, mayúsculas, códigos, textos temáticos, animaciones, mascota). FITBOT puede cambiarlas con `set_settings`.
- El ícono de la app cambia con la skin (se apaga con «Ícono de la app de la skin»).
- Archivos: `data/Skins.kt` (datos), `ui/theme/Skin.kt` (estilo, colores, diccionario de textos), `ui/design/Skin*.kt` (fondo, marcas, transiciones, mascotas), `ui/screens/SkinsScreen.kt`, `tools/skin_marks.py` (logos, vectores).

## Estructura
- `app/src/main/java/app/fitstat/data` — modelos, `Repo` (fuente única de datos), esquema de ajustes, catálogo de 423 ejercicios, estadísticas, hitos, registro de cambios.
- `.../bot` — cliente de OpenRouter, bucle de herramientas de FITBOT (57 herramientas), chat.
- `.../sys` — recordatorios, widgets (se dibujan con Canvas), íconos alternativos.
- `.../ui` — sistema de diseño (íconos, LED, mascota, tema) y pantallas.
- `tools/` — scripts que generan el catálogo, los íconos y los recursos de Android.
- `NOTES.md` — notas técnicas y comandos de prueba (más de 270 pruebas, buena parte con Robolectric).

## Créditos
Lucide (ISC), free-exercise-db (Unlicense), react-body-highlighter (MIT) y fuentes de Google Fonts (OFL, incluida Chakra Petch para la skin Acero).
