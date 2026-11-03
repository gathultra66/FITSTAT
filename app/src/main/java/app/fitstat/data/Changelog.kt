package app.fitstat.data

/** Registro de versiones de la app (se muestra en Ajustes › Registro de cambios). */
class ChangelogEntry(val version: String, val date: String, val title: String, val sections: List<Pair<String, List<String>>>)

object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "1.9.1",
            date = "31 oct 2026",
            title = "Las portadas de NAMSTAT",
            sections = listOf(
                "Corrección" to listOf(
                    "Corregido: las portadas de NAMSTAT no aparecían (en el widget de música ni en la pantalla del conector). NAMSTAT le comparte a Android el enlace del archivo de audio como carátula, y FITSTAT no puede abrir tu música. Ahora FITSTAT se la pide a NAMSTAT por el Enlace T-A-T, que saca la imagen (la del sistema o la incrustada en el archivo).",
                    "Necesita NAMSTAT 1.2.1 o más nueva. Con una versión anterior de NAMSTAT se sigue viendo el ícono de música.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.9",
            date = "30 oct 2026",
            title = "Conector de NAMSTAT: el ecosistema T-A-T",
            sections = listOf(
                "NAMSTAT (conector nuevo)" to listOf(
                    "NAMSTAT es el reproductor de música de código abierto hecho desde cero que comparte ecosistema con FITSTAT (T-A-T). Como las dos apps son tuyas, este conector no tiene los límites de los demás: FITSTAT habla directo con NAMSTAT por el Enlace T-A-T, un canal local entre las dos apps (sin internet).",
                    "Ves la canción con su año, género y formato, la barra de progreso (y puedes saltar a cualquier parte), la cola completa, tus listas, tus favoritas y tus estadísticas de escucha (hoy, semana, racha y lo más escuchado).",
                    "Controlas play, pausa, siguiente y anterior, el volumen propio de NAMSTAT, la velocidad, el aleatorio, repetir, marcar favorita y el temporizador de sueño (15, 30, 60 min o al terminar la canción).",
                    "Buscas en tu biblioteca y pones una canción, la agregas como siguiente, o reproduces tus favoritas, lo reciente, una lista, un álbum o un artista. Si NAMSTAT está cerrado, lo abre un momento con la orden y regresa a FITSTAT.",
                    "Necesita NAMSTAT 1.2 o más nueva (con el enlace encendido en NAMSTAT › Ajustes › Aplicación) y, como con las otras apps, el Acceso a notificaciones para la portada.",
                ),
                "Descansos y entrenamiento" to listOf(
                    "Al volver del descanso, NAMSTAT sube su propio volumen poco a poco (el fundido lo hace NAMSTAT): el volumen del teléfono ya no se toca. Si el enlace no contesta, se usa la subida de volumen del teléfono como con las demás apps.",
                    "NAMSTAT se entera de cuándo empiezas y terminas de entrenar (y el nombre de la rutina). Se apaga con «Avisar de mis entrenamientos».",
                    "Nuevo: «Lista para entrenar». Escribe el nombre de una lista de NAMSTAT y suena sola al empezar un entrenamiento, si no hay música sonando (con opción de aleatorio).",
                ),
                "FITBOT" to listOf(
                    "FITBOT tiene una herramienta nueva, «namstat», con todo lo anterior: «pon mi lista Gym en aleatorio», «súbele a 70», «qué es lo más que he escuchado esta semana», «apágala en 30 minutos», «busca algo de Soda Stereo y ponlo».",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.8.4",
            date = "29 oct 2026",
            title = "El color de la portada ahora sí se nota",
            sections = listOf(
                "Corrección" to listOf(
                    "Corregido: en 1.8.3 el color de la portada salía casi siempre gris, porque se promediaban todos los colores de la imagen. Ahora se toma el tono que más predomina y más vivo, así que una portada cromada con tonos azules da azul, una roja da rojo, etc.",
                    "El tinte ahora se ve en toda la app: además de los acentos (botones, indicadores), el fondo, las tarjetas y los bordes toman un matiz del color de la portada. Las portadas realmente grises (blanco y negro) se quedan grises.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.8.3",
            date = "29 oct 2026",
            title = "Color de la portada y desvanecido",
            sections = listOf(
                "Color de la portada" to listOf(
                    "Mientras suena música, la app se tiñe con el color dominante de la portada: los acentos (botones, barras, resaltados) y un toque de fondo. Al pausar o terminar la canción vuelve, poco a poco, al color que elegiste.",
                    "Si la portada es muy oscura o muy clara, el color se ajusta para que se lea como acento. El texto sigue legible.",
                    "Se activa o desactiva en Ajustes › Animaciones de música › Color de la portada, y la intensidad se ajusta de 20 % a 100 %.",
                ),
                "Desvanecer al pausar o terminar" to listOf(
                    "Las animaciones que se mueven con el ritmo ya no se cortan de golpe: al pausar o terminar la canción se desvanecen con una caída suave y natural.",
                    "Puedes elegir cuánto dura el desvanecido (de 0,5 a 6 segundos) o desactivarlo para que se detengan al instante. Si vuelves a dar play durante el desvanecido, regresan de inmediato.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.8.2",
            date = "29 oct 2026",
            title = "Conectores de Spotify y YouTube Music",
            sections = listOf(
                "Conectores" to listOf(
                    "Dos conectores nuevos en Ajustes › Conectores: Spotify y YouTube Music, junto a Namida. Cada uno se conecta y desconecta por separado.",
                    "Funcionan igual que el de Namida: al empezar el descanso entre series, la música se pausa sola y, al terminar, vuelve con el volumen bajo y sube poco a poco. Cada app tiene sus propios ajustes (pausar, reanudar, segundos de subida y volumen inicial). Solo reanuda lo que FITSTAT pausó.",
                    "Necesitan la app instalada y abierta (puede estar en segundo plano) y el acceso a notificaciones de Android, que se da una sola vez para las tres apps.",
                ),
                "Widget de música y animaciones" to listOf(
                    "El widget de música del inicio muestra la portada y controla la app que esté sonando (Namida, Spotify o YouTube Music). Si suenan varias, atiende la que está reproduciendo.",
                    "Las animaciones con la música también funcionan con Spotify y YouTube Music.",
                    "FITBOT: get_music y music_control atienden las tres apps, y puede conectar o desconectar cada una.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.8.1",
            date = "29 oct 2026",
            title = "Tablet y modo horizontal",
            sections = listOf(
                "Tablet y horizontal" to listOf(
                    "La app ya gira: funciona en horizontal y en tablet (antes estaba fija en vertical). En pantallas anchas el contenido es una columna centrada con el fondo a todo lo ancho; los avisos y las hojas inferiores también se acotan en vez de estirarse.",
                    "Respeta los márgenes laterales del sistema (barra de navegación o recorte de pantalla) al acostar el teléfono, y la app se adapta al modo de ventanas del sistema.",
                ),
                "Widgets del escritorio" to listOf(
                    "Corregido: en tablet los widgets se veían deformados (el dibujo se estiraba al tamaño del widget). Ahora se dibujan al tamaño exacto que informa el lanzador, en Android 12 o superior uno por cada orientación, y nunca se estiran.",
                    "En widgets grandes el diseño se amplía en vez de quedar chico y con huecos, y la tarjeta «Hoy» muestra la fila de la semana desde una altura menor.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.8",
            date = "29 oct 2026",
            title = "Animaciones con la música y más control de la distribución",
            sections = listOf(
                "Animaciones con la música" to listOf(
                    "Detrás de la meta semanal y del widget de música ahora hay animaciones que laten con lo que suena en Namida. Solo se mueven mientras suena música; en pausa no gastan batería ni se ven.",
                    "8 estilos: Barras, Ondas, Pulso, Partículas, Aurora, Osciloscopio, Radial y Mezcla (cambia de uno a otro cada pocos segundos). Cada una se elige por separado para la meta y para el widget de música.",
                    "La portada de la canción vibra apenas, al ritmo (se apaga o se ajusta su fuerza).",
                    "Se personalizan en Ajustes › Animaciones de música: intensidad, sensibilidad, velocidad, color (automático, acento, contraste, de la portada o arcoíris), calidad (menos cuadros por segundo para ahorrar batería), duración de la Mezcla y más. Arriba hay una vista previa en vivo para probar cada estilo.",
                    "Cómo sigue el ritmo: si le das el permiso de audio y Android lo permite, mide el volumen de los graves, medios y agudos del sonido del teléfono (no graba ni guarda nada). Si no, usa un ritmo aproximado a un tempo fijo que tú eliges, que se ve bien pero NO sigue la canción exacta. La pantalla siempre dice cuál de los dos está usando.",
                    "ARASAKA y MILITECH usan estas mismas animaciones por ahora.",
                    "FITBOT puede ver y cambiar todo esto (get_music_fx y set_settings).",
                ),
                "Distribución de Ejercicios y Rutinas" to listOf(
                    "Ejercicios y Rutinas se editan igual que el inicio: mantén pulsado un contenedor (o cualquier parte de la pantalla) y se activa el editor. Mueve, achica, agranda, quita y agrega bloques; Listo guarda todo con un solo Deshacer.",
                    "A ambas pantallas puedes agregarles los widgets del inicio (racha, progreso, meta semanal, música…) además de sus propios bloques.",
                    "En Rutinas, las tarjetas de rutina se pueden mover y cambiar de tamaño pero no quitar (quitarlas sería borrar la rutina). Las opciones de cada rutina pasaron al botón ⋮ de la tarjeta, porque el toque largo ahora abre el editor.",
                    "Ajustes › Inicio y navegación: Editar ejercicios, Editar rutinas y sus botones de restablecer. FITBOT: edit_home y get_home_layout aceptan la pantalla (inicio, ejercicios o rutinas).",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.7.1",
            date = "29 oct 2026",
            title = "Barra de navegación sin tapar",
            sections = listOf(
                "Skins" to listOf(
                    "Corregido: el efecto de transición de las skins (persianas en MILITECH, línea de escaneo en ARASAKA) ya no pasa por encima de la barra de navegación de abajo; ahora solo cruza el contenido.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.7",
            date = "29 oct 2026",
            title = "Sueño, música y conectores",
            sections = listOf(
                "Sueño" to listOf(
                    "Pantalla nueva de Sueño (también como widget del inicio): cada noche con su hora de dormir y despertar, el tiempo dormido frente a tu meta, qué tan regular eres y la tendencia de 7, 14 o 30 días.",
                    "Con un reloj o pulsera que guarde tu sueño en Health Connect ves las fases: ligero, profundo, REM y los ratos despierto. Es la única forma de tenerlas: un teléfono solo no puede saber en qué fase estás, así que FITSTAT no las inventa.",
                    "Sin reloj, el conector «Uso del teléfono» estima cuántas horas dormiste (el tramo más largo de la noche sin tocar el teléfono). Es solo una estimación, siempre se marca como «estimado» y no trae fases. Android guarda más o menos 7 días de ese historial.",
                    "También puedes anotar una noche a mano: lo que escribes manda sobre lo estimado. Tu meta se cambia en Ajustes › Sueño.",
                    "FITBOT: pregúntale cómo has dormido, pídele que actualice tus datos o dile «anota que anoche dormí de 11 a 7».",
                ),
                "Conectores" to listOf(
                    "Apartado nuevo en Ajustes › Conectores con las apps que se conectan a FITSTAT: Namida, Health Connect y Uso del teléfono. Cada uno muestra su estado y te lleva directo al permiso que falta.",
                    "FITBOT los maneja desde el chat: conectarlos o desconectarlos, cambiar sus opciones, ver qué suena y controlar la música.",
                ),
                "Namida" to listOf(
                    "Al empezar el descanso entre series, la música se pausa sola. Cuando termina, vuelve a sonar con el volumen bajo y sube poco a poco hasta el de antes. Solo reanuda lo que FITSTAT pausó: si tú la pausaste, no la toca.",
                    "Widget de música para el inicio (se agrega desde «Agregar»): carátula, título, anterior, play o pausa y siguiente.",
                    "Pausar y reanudar se apagan por separado en Ajustes › Conectores › Namida, donde también eliges cuántos segundos tarda en subir el volumen y desde dónde empieza.",
                    "Necesita el acceso a notificaciones de Android para ver y controlar lo que suena, y Namida abierta en segundo plano.",
                ),
                "Ejercicios" to listOf(
                    "Ahora hay 423 ejercicios con fotos (antes 176): más de 240 variaciones nuevas de press, remos, dominadas, curls, tríceps, hombro, sentadillas, peso muerto, abdomen y cardio, con agarres, ángulos, poleas, máquinas, bandas y una sola mano.",
                ),
                "FITBOT" to listOf(
                    "Animaciones más fluidas: los cambios de ánimo se mezclan en vez de saltar, parpadea con un ritmo menos mecánico, respira y mira alrededor con más suavidad, y al hablar la boca ya no da un tirón.",
                ),
                "Inicio" to listOf(
                    "Ahora hay 16 widgets: se suman Sueño (viene puesto) y Música (se agrega a mano).",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.6",
            date = "29 oct 2026",
            title = "Inicio a tu medida",
            sections = listOf(
                "Inicio personalizable" to listOf(
                    "Mantén pulsado cualquier widget del inicio (Entrenar, FITBOT, Progreso, Hitos, Notas, Racha, Volumen semanal…) para entrar al modo de edición.",
                    "Muévelos arrastrándolos: desde el asa de arriba a la derecha, o manteniendo pulsado el widget. La lista se desplaza sola cuando llegas al borde.",
                    "Cambia su tamaño con los botones de abajo: pequeño (tres por fila), mediano (dos por fila) o grande (fila completa). Cada widget se adapta al tamaño que le des.",
                    "Quita los que no uses con la ✕ y vuelve a agregarlos con «Agregar»; hay 14 widgets, incluidos Meta semanal, Hoy, Mapa muscular y Calendario de actividad.",
                    "«Listo» guarda todo de una vez y un solo «Deshacer» revierte la sesión de edición completa. «Restablecer» vuelve a la disposición de siempre.",
                    "También está en Ajustes › Inicio y navegación. Las tarjetas que tenías apagadas en «Tarjetas visibles» siguen ocultas: ahora las agregas o quitas desde el propio inicio.",
                ),
                "FITBOT" to listOf(
                    "El selector de modelo salió de debajo del título: ahora es un recuadro junto al de las opciones (⋮) y el chat tiene más espacio. El nombre del modelo sigue visible en el menú.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.5.3",
            date = "29 oct 2026",
            title = "Logos Arasaka y Militech",
            sections = listOf(
                "Skins" to listOf(
                    "Las skins ahora se llaman solo ARASAKA y MILITECH, sin Carmesi ni Acero.",
                    "Logos nuevos inspirados en las corporaciones: emblema circular rojo con palabra ARASAKA y cuadro amarillo con M y palabra MILITECH.",
                    "El icono de la app cambia con la skin: muestra el logo de la corporacion activa.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.5.1",
            date = "29 oct 2026",
            title = "Skins Arasaka y Militech",
            sections = listOf(
                "Skins" to listOf(
                    "La skin roja ahora se llama Carmesi - Arasaka y muestra ARASAKA en logo, LED e inicio.",
                    "La skin azul ahora se llama Acero - Militech y muestra MILITECH en logo, LED e inicio.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.5",
            date = "29 oct 2026",
            title = "Skins: Carmesí y Acero",
            sections = listOf(
                "Skins" to listOf(
                    "Nueva sección «Skin» en Ajustes, arriba de Personalización: elige el diseño completo de la app con una vista previa de cada skin.",
                    "Carmesí: tarjetas oscuras, matriz de puntos LED, acento rojo, líneas de escaneo y textos de «misión».",
                    "Acero: terminal industrial con esquinas cortadas, cuadrícula táctica, códigos técnicos y acento azul plateado.",
                    "Cada skin trae su propio logo, que también pasa a ser el ícono de la app en tu pantalla de inicio (se puede apagar).",
                    "Cada skin trae su mascota de cabeza en lugar de FITBOT: Centinela (Carmesí) y Vigía (Acero), con los mismos estados de ánimo y reacciones al tocarla.",
                ),
                "Personalización de skins" to listOf(
                    "Cada skin se personaliza por separado: color (cinco acentos o cualquier color), superficie, esquinas, fondo, intensidad de efectos, tipografía, números, mayúsculas, códigos técnicos, textos temáticos, mascota y animaciones.",
                    "Con una skin puesta, Tema, Formas y Tipografía avisan que la skin los controla; con la skin FITSTAT todo sigue como antes.",
                ),
                "Animaciones propias" to listOf(
                    "Cada skin trae fondo vivo, adornos animados en las tarjetas, entrada de elementos, transición entre pantallas, confeti y aviso de celebración propios; puedes dejarlas apagadas, suaves o completas.",
                    "Al cambiar de skin aparece una pantalla de arranque con su logo.",
                    "La mascota de cada skin tiene efectos propios: Centinela barre una línea de escaneo y se «glitchea» al tocarlo; Vigía lanza anillos de radar y gira su retícula.",
                ),
                "FITBOT" to listOf(
                    "Puede cambiar de skin y ajustar sus opciones por ti con las herramientas de ajustes de siempre.",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.1",
            date = "29 oct 2026",
            title = "Salir del entrenamiento sin trabas",
            sections = listOf(
                "Entrenamiento" to listOf(
                    "Nuevo botón ✕ en la pantalla de entrenamiento y en la barra «Entrenamiento en curso»: Finalizar, Cancelar o Seguir entrenando.",
                    "Cancelar descarta la sesión y borra solo las series que registraste en ella; después puedes deshacerlo.",
                    "Si sales sin haber registrado ninguna serie, te pregunta si quieres cancelar o seguir en segundo plano.",
                    "Corregido: al cancelar o terminar, el entrenamiento ya no reaparece solo en la barra de «en curso».",
                ),
                "FITBOT" to listOf(
                    "Ahora puede ver tu entrenamiento en curso, finalizarlo (conserva tus series) o cancelarlo (borra las series de esa sesión).",
                ),
            ),
        ),
        ChangelogEntry(
            version = "1.0",
            date = "28 oct 2026",
            title = "Primera versión",
            sections = listOf(
                "Entrenamiento" to listOf(
                    "176 ejercicios con fotos, descripción y músculos trabajados, repartidos en 10 categorías.",
                    "Buscador con lupa y sistema predictivo: sugiere ejercicios mientras escribes, completa el texto y perdona errores de dedo.",
                    "Si no encuentras un ejercicio, crea tu propia plantilla con foto, categoría y músculos.",
                    "Tu set habitual: agrega o quita ejercicios con un toque.",
                    "Varias rutinas con los ejercicios que quieras, días de la semana y entrenamiento guiado con descansos.",
                    "Ficha por ejercicio: foto grande animada, estadísticas completas, anotaciones, series, repeticiones y días.",
                    "Al tocar la foto ves el cuerpo humano con los músculos trabajados resaltados.",
                ),
                "Estadísticas y logros" to listOf(
                    "Registro de series con peso y repeticiones, 1RM estimado, volumen y récords personales.",
                    "Gráficas en 5 estilos: barras, línea, área, puntos LED y anillos.",
                    "Meta semanal, rachas, calendario de actividad y mapa muscular de la semana.",
                    "41 hitos por desbloquear, con celebración al lograrlos.",
                    "Anotaciones del gym con notas fijadas y color.",
                ),
                "FITBOT" to listOf(
                    "Asistente con IA (OpenRouter): escribe tu clave y elige el modelo que quieras.",
                    "Mascota animada: reacciona al escribir, al pensar y cuando la tocas.",
                    "Control total de la app: consulta tus estadísticas, crea rutinas, registra series, cambia ajustes y más, con botón de Deshacer.",
                    "Adjunta imágenes, archivos y audio cuando el modelo elegido lo soporte.",
                ),
                "Personalización" to listOf(
                    "12 paletas, modo claro, oscuro y AMOLED, tinte de superficies, fondos y brillo.",
                    "Formas: redondeo, estilo de tarjetas y botones, densidad, estilo y grosor de íconos.",
                    "8 tipografías, tamaño y grosor del texto, números estilo LED.",
                    "Velocidad y estilo de animaciones, vibración y confeti.",
                    "Pantalla de inicio configurable, unidades kg/lb, fórmulas de 1RM y más de 70 ajustes.",
                    "Cambia el color del ícono de la app.",
                ),
                "Extras" to listOf(
                    "4 widgets para la pantalla de inicio.",
                    "Recordatorios de entrenamiento.",
                    "Exporta e importa tus datos (JSON y CSV).",
                ),
            ),
        ),
    )

    val latest: ChangelogEntry get() = entries.first()
}
