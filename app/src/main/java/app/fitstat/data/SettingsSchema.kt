package app.fitstat.data

/**
 * Esquema de ajustes de FITSTAT. Es la única fuente de verdad: de aquí salen
 *  - la pantalla de Ajustes (se genera sola),
 *  - las herramientas de FITBOT (leer / cambiar ajustes),
 *  - los valores por defecto.
 * Todos los valores se guardan como texto en AppData.settings.
 */
data class Opt(val value: String, val label: String, val hint: String = "")

sealed interface SType {
    data class Choice(val options: List<Opt>) : SType
    data object Toggle : SType
    data class Slider(val min: Float, val max: Float, val step: Float, val unit: String = "", val decimals: Int = 0) : SType
    data object ColorHex : SType
    data class Text(val hint: String = "", val long: Boolean = false) : SType
    data object Time : SType
}

data class SettingSpec(
    val key: String,
    val page: String,
    val section: String,
    val title: String,
    val desc: String,
    val type: SType,
    val default: String,
    /** Solo se muestra si otro ajuste tiene cierto valor (key=value). */
    val visibleIf: Pair<String, String>? = null,
)

data class SettingsPage(val id: String, val title: String, val subtitle: String, val icon: String)

object Pages {
    const val THEME = "tema"
    const val SHAPES = "formas"
    const val TYPE = "tipografia"
    const val MOTION = "animaciones"
    const val STATS = "estadisticas"
    const val HOME = "inicio"
    const val TRAIN = "entrenamiento"
    const val BOT = "fitbot"
    const val WIDGETS = "widgets"
    const val REMINDERS = "recordatorios"
    const val ICON = "icono"
    const val SKIN = "skin"
    const val CONNECTORS = "conectores"
    const val SLEEP = "sueno"
    const val MUSICFX = "musica_fx"
}

private fun opts(vararg p: Pair<String, String>) = SType.Choice(p.map { Opt(it.first, it.second) })
private fun slider(min: Float, max: Float, step: Float, unit: String = "", dec: Int = 0) = SType.Slider(min, max, step, unit, dec)

object Settings {
    val paletteIds = listOf("lavanda", "menta", "mandarina", "rosa", "azul", "lima", "fuego", "ambar", "turquesa", "mono", "sistema", "custom")
    val fontIds = listOf("manrope", "dm_sans", "outfit", "sora", "space_grotesk", "inter", "lexend", "jetbrains_mono", "system")

    /** Ajustes de las skins: los globales y un juego completo por cada skin especial (cada skin se personaliza aparte). */
    private fun skinSpecs(): List<SettingSpec> = buildList {
        add(
            SettingSpec("skin.id", Pages.SKIN, "Skin", "Skin activa", "Cambia el diseño completo de la app: colores, formas, tipografía, mascota, logo y animaciones.",
                SType.Choice(Skins.all.map { Opt(it.id, it.name, it.tagline) }), Skins.DEFAULT),
        )
        add(
            SettingSpec("skin.icon_sync", Pages.SKIN, "Skin", "Ícono de la app de la skin", "Con una skin activa, el ícono de la pantalla de inicio cambia al logo de la skin.", SType.Toggle, "true"),
        )
        for (s in Skins.special) {
            val on = "skin.id" to s.id
            val k = "skin.${s.id}."
            add(
                SettingSpec(k + "accent", Pages.SKIN, "Color", "Color de ${s.name}", "Color principal de la skin.",
                    SType.Choice(s.accents.map { Opt(it.id, it.label) } + Opt("custom", "Personalizado")), "base", visibleIf = on),
            )
            add(SettingSpec(k + "custom", Pages.SKIN, "Color", "Color personalizado", "Elige cualquier color con el selector.", SType.ColorHex,
                "#" + "%06X".format(s.accents.first().a1 and 0xFFFFFF), visibleIf = k + "accent" to "custom"))
            add(
                SettingSpec(k + "surface", Pages.SKIN, "Color", "Superficies", "Fondo profundo o negro puro (AMOLED).",
                    SType.Choice(listOf(Opt("deep", "Profundo"), Opt("black", "Negro puro"))), "deep", visibleIf = on),
            )
            add(
                SettingSpec(k + "cut", Pages.SKIN, "Formas y fondo", "Esquinas", "Redondeadas, cortadas en diagonal o rectas.",
                    SType.Choice(listOf(Opt("round", "Redondeadas"), Opt("cut", "Cortadas"), Opt("sharp", "Rectas"))), s.defCut, visibleIf = on),
            )
            add(
                SettingSpec(k + "bg", Pages.SKIN, "Formas y fondo", "Fondo", "Textura detrás de las pantallas.",
                    SType.Choice(listOf(Opt("full", "Completo"), Opt("grid", "Solo cuadrícula"), Opt("plain", "Liso"))), s.defBg, visibleIf = on),
            )
            add(
                SettingSpec(k + "fx", Pages.SKIN, "Formas y fondo", "Efectos de pantalla", "Líneas de escaneo, brillo y viñeta.",
                    SType.Slider(0f, 100f, 5f, "%"), s.defFx.toString(), visibleIf = on),
            )
            add(
                SettingSpec(k + "font", Pages.SKIN, "Tipografía", "Usar mi tipografía", "Apagado: la skin usa su propia letra. Encendido: la que elegiste en Tipografía.", SType.Toggle, "false", visibleIf = on),
            )
            add(
                SettingSpec(k + "led", Pages.SKIN, "Tipografía", "Números grandes", "Cómo se dibujan los números destacados.",
                    SType.Choice(listOf(Opt("led", "LED (matriz de puntos)"), Opt("mono", "Monoespaciados"), Opt("font", "Normales"))), s.defDigits, visibleIf = on),
            )
            add(SettingSpec(k + "caps", Pages.SKIN, "Tipografía", "Etiquetas en mayúsculas", "Rótulos pequeños y títulos de sección en mayúsculas.", SType.Toggle, "true", visibleIf = on))
            add(SettingSpec(k + "codes", Pages.SKIN, "Textos y marcas", "Códigos y marcas técnicas", "Líneas de estado, esquinas con marcas y códigos en las pantallas.", SType.Toggle, "true", visibleIf = on))
            add(SettingSpec(k + "words", Pages.SKIN, "Textos y marcas", "Textos temáticos", "Cambia nombres como «Rutinas» por los de la skin.", SType.Toggle, "true", visibleIf = on))
            add(
                SettingSpec(k + "anim", Pages.SKIN, "Animaciones", "Animaciones de la skin", "Transiciones, entradas, celebración y fondo propios de la skin.",
                    SType.Choice(listOf(Opt("off", "Apagadas"), Opt("soft", "Suaves"), Opt("full", "Completas"))), "full", visibleIf = on),
            )
            add(SettingSpec(k + "mascot", Pages.SKIN, "Animaciones", "Mascota de la skin", "Apagado: vuelve FITBOT clásico, con cabeza de mancuerna.", SType.Toggle, "true", visibleIf = on))
        }
    }

    /** Estilos de animación de la música (los comparten todas las skins). */
    val FX_STYLES = SType.Choice(
        listOf(
            Opt("bars", "Barras", "Ecualizador que sube desde abajo"),
            Opt("waves", "Ondas", "Olas que crecen con el volumen"),
            Opt("pulse", "Pulso", "Anillos que salen con cada golpe"),
            Opt("particles", "Partículas", "Puntos que suben y saltan con el ritmo"),
            Opt("aurora", "Aurora", "Luces suaves que se mueven"),
            Opt("scope", "Osciloscopio", "Una línea de señal"),
            Opt("radial", "Radial", "Espectro en círculo"),
            Opt("mix", "Mezcla", "Va cambiando de estilo solo"),
        ),
    )

    val all: List<SettingSpec> = listOf(
        // ───────────── Tema y color
        SettingSpec("theme.mode", Pages.THEME, "Modo", "Modo de color", "Oscuro, claro, negro puro (AMOLED) o el del sistema.",
            opts("dark" to "Oscuro", "light" to "Claro", "amoled" to "Negro AMOLED", "system" to "Sistema"), "dark"),
        SettingSpec("theme.palette", Pages.THEME, "Color", "Paleta de color", "Color principal de toda la app.",
            opts(
                "lavanda" to "Lavanda", "menta" to "Menta", "mandarina" to "Mandarina", "rosa" to "Rosa neón", "azul" to "Azul eléctrico",
                "lima" to "Lima", "fuego" to "Fuego", "ambar" to "Ámbar", "turquesa" to "Turquesa", "mono" to "Monocromo",
                "sistema" to "Sistema (Android 12+)", "custom" to "Personalizado",
            ), "lavanda"),
        SettingSpec("theme.custom", Pages.THEME, "Color", "Color personalizado", "Elige cualquier color con el selector.", SType.ColorHex, "#9B8CF5",
            visibleIf = "theme.palette" to "custom"),
        SettingSpec("theme.tint", Pages.THEME, "Superficies", "Tinte en las superficies", "Mezcla un poco del color principal en fondos y tarjetas.",
            opts("none" to "Neutro", "soft" to "Suave", "strong" to "Marcado"), "soft"),
        SettingSpec("theme.background", Pages.THEME, "Superficies", "Fondo de la app", "Textura detrás de las pantallas.",
            opts("plain" to "Liso", "gradient" to "Degradado", "dots" to "Puntos LED", "grid" to "Cuadrícula"), "gradient"),
        SettingSpec("theme.gradients", Pages.THEME, "Superficies", "Degradados en tarjetas de color", "Tarjetas con degradado en lugar de color plano.", SType.Toggle, "true"),
        SettingSpec("theme.glow", Pages.THEME, "Superficies", "Intensidad del brillo", "Resplandor de botones, órbitas y datos destacados.", slider(0f, 100f, 5f, "%"), "60"),
        SettingSpec("theme.contrast", Pages.THEME, "Superficies", "Contraste", "Texto y bordes más marcados para leer mejor.",
            opts("normal" to "Normal", "high" to "Alto"), "normal"),
        SettingSpec("theme.photos", Pages.THEME, "Fotos de ejercicios", "Tratamiento de las fotos", "Cómo se ven las fotos de ejemplo de cada ejercicio.",
            opts("duotone" to "Duotono (color de la paleta)", "mono" to "Blanco y negro", "color" to "Color original"), "duotone"),

        // ───────────── Formas e íconos
        SettingSpec("shape.radius", Pages.SHAPES, "Formas", "Redondeo", "Qué tan redondas son las tarjetas y los botones.",
            opts("sharp" to "Cuadrado", "soft" to "Suave", "round" to "Redondo", "extra" to "Extra redondo"), "round"),
        SettingSpec("shape.card", Pages.SHAPES, "Formas", "Estilo de tarjetas", "Plano, con relieve, con borde o cristal.",
            opts("raised" to "Relieve suave", "flat" to "Plano", "outline" to "Con borde", "glass" to "Cristal"), "raised"),
        SettingSpec("shape.button", Pages.SHAPES, "Formas", "Estilo de botones", "Forma de los botones principales.",
            opts("pill" to "Píldora", "rounded" to "Redondeado", "square" to "Cuadrado"), "pill"),
        SettingSpec("shape.density", Pages.SHAPES, "Formas", "Densidad", "Espaciado entre elementos.",
            opts("compact" to "Compacta", "normal" to "Normal", "roomy" to "Amplia"), "normal"),
        SettingSpec("shape.thumbs", Pages.SHAPES, "Formas", "Tamaño de las miniaturas", "Fotos de ejercicios en las listas.",
            opts("small" to "Pequeña", "normal" to "Normal", "large" to "Grande"), "normal"),
        SettingSpec("shape.icon_style", Pages.SHAPES, "Íconos", "Estilo de íconos", "Línea fina, duotono o trazo grueso.",
            opts("line" to "Línea", "duo" to "Duotono", "bold" to "Grueso"), "duo"),
        SettingSpec("shape.icon_weight", Pages.SHAPES, "Íconos", "Grosor de línea", "Grosor del trazo de los íconos.", slider(1.25f, 3f, 0.25f, "", 2), "2"),
        SettingSpec("shape.icon_container", Pages.SHAPES, "Íconos", "Forma del contenedor de íconos", "Fondo detrás de los íconos importantes.",
            opts("circle" to "Círculo", "squircle" to "Squircle", "rounded" to "Cuadrado redondeado", "none" to "Sin contenedor"), "circle"),

        // ───────────── Tipografía
        SettingSpec("type.font", Pages.TYPE, "Fuente", "Tipografía", "Fuente de toda la aplicación.",
            opts(
                "manrope" to "Manrope", "dm_sans" to "DM Sans", "outfit" to "Outfit", "sora" to "Sora", "space_grotesk" to "Space Grotesk",
                "inter" to "Inter", "lexend" to "Lexend", "jetbrains_mono" to "JetBrains Mono", "system" to "Del sistema",
            ), "manrope"),
        SettingSpec("type.scale", Pages.TYPE, "Fuente", "Tamaño del texto", "Escala todo el texto de la app (además del tamaño del sistema).", slider(80f, 140f, 5f, "%"), "100"),
        SettingSpec("type.weight", Pages.TYPE, "Fuente", "Grosor del texto", "Qué tan marcado se ve el texto.",
            opts("light" to "Ligero", "normal" to "Normal", "strong" to "Fuerte"), "normal"),
        SettingSpec("type.tracking", Pages.TYPE, "Fuente", "Espaciado entre letras", "En centésimas de em.", slider(-2f, 8f, 1f, "", 0), "0"),
        SettingSpec("type.digits", Pages.TYPE, "Números", "Números grandes", "Cómo se dibujan los números destacados.",
            opts("font" to "Normales", "led" to "LED (matriz de puntos)", "mono" to "Monoespaciados"), "font"),
        SettingSpec("type.caps", Pages.TYPE, "Números", "Etiquetas en mayúsculas", "Pone en mayúsculas los títulos pequeños.", SType.Toggle, "false"),

        // ───────────── Animaciones
        SettingSpec("motion.enabled", Pages.MOTION, "General", "Animaciones", "Apágalas para reducir el movimiento al mínimo.", SType.Toggle, "true"),
        SettingSpec("motion.speed", Pages.MOTION, "General", "Velocidad", "Qué tan rápidas son las animaciones.", slider(50f, 200f, 10f, "%"), "100",
            visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.transition", Pages.MOTION, "General", "Transición entre pantallas", "Cómo cambia una pantalla por otra.",
            opts("slide" to "Deslizar", "fade" to "Fundido", "scale" to "Zoom", "none" to "Ninguna"), "slide", visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.entrance", Pages.MOTION, "Detalles", "Entrada escalonada", "Las tarjetas aparecen una tras otra.", SType.Toggle, "true", visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.press", Pages.MOTION, "Detalles", "Rebote al tocar", "Los botones se hunden un poco al presionarlos.", SType.Toggle, "true", visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.count", Pages.MOTION, "Detalles", "Números que cuentan", "Los números suben animados hasta su valor.", SType.Toggle, "true", visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.breathing", Pages.MOTION, "Detalles", "Brillo que respira", "Resplandor suave y continuo en lo importante.", SType.Toggle, "true", visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.mascot", Pages.MOTION, "Detalles", "FITBOT animado", "La mascota parpadea, respira y reacciona.", SType.Toggle, "true", visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.confetti", Pages.MOTION, "Detalles", "Confeti en los récords", "Celebración cuando superas un récord.", SType.Toggle, "true", visibleIf = "motion.enabled" to "true"),
        SettingSpec("motion.haptics", Pages.MOTION, "Vibración", "Respuesta háptica", "Vibración corta al tocar y al completar series.",
            opts("off" to "Apagada", "soft" to "Suave", "normal" to "Normal"), "soft"),

        // ───────────── Estadísticas
        SettingSpec("stats.chart", Pages.STATS, "Gráficas", "Forma de mostrar las gráficas", "Barras, línea, área, puntos LED o anillos.",
            opts("bars" to "Barras", "line" to "Línea", "area" to "Área", "dots" to "Puntos LED", "ring" to "Anillos"), "bars"),
        SettingSpec("stats.metric", Pages.STATS, "Gráficas", "Dato principal", "Qué mide la gráfica de cada ejercicio.",
            opts("volume" to "Volumen (kg × reps)", "weight" to "Peso máximo", "e1rm" to "1RM estimado", "reps" to "Repeticiones"), "volume"),
        SettingSpec("stats.range", Pages.STATS, "Gráficas", "Rango por defecto", "Periodo que se muestra al abrir una gráfica.",
            opts("4w" to "4 semanas", "8w" to "8 semanas", "12w" to "12 semanas", "6m" to "6 meses", "all" to "Todo"), "8w"),
        SettingSpec("stats.grid", Pages.STATS, "Gráficas", "Líneas de guía", "Cuadrícula tenue detrás de las gráficas.", SType.Toggle, "true"),
        SettingSpec("stats.values", Pages.STATS, "Gráficas", "Valores sobre las barras", "Muestra el número encima de cada barra.", SType.Toggle, "true"),
        SettingSpec("stats.records", Pages.STATS, "Gráficas", "Resaltar récords", "Marca con una estrella los récords personales.", SType.Toggle, "true"),
        SettingSpec("stats.units", Pages.STATS, "Unidades", "Unidad de peso", "Kilogramos o libras.", opts("kg" to "Kilogramos (kg)", "lb" to "Libras (lb)"), "kg"),
        SettingSpec("stats.step", Pages.STATS, "Unidades", "Incremento de peso", "Cuánto sube o baja el peso al tocar + y −.",
            opts("0.5" to "0.5", "1" to "1", "2" to "2", "2.5" to "2.5", "5" to "5"), "2.5"),
        SettingSpec("stats.formula", Pages.STATS, "Unidades", "Fórmula de 1RM", "Cómo se estima tu repetición máxima.",
            opts("epley" to "Epley", "brzycki" to "Brzycki"), "epley"),

        // ───────────── Inicio y navegación
        SettingSpec("home.start", Pages.HOME, "Navegación", "Pestaña inicial", "Pantalla que se abre primero.",
            opts("home" to "Inicio", "exercises" to "Ejercicios", "routines" to "Rutinas", "bot" to "FITBOT"), "home"),
        SettingSpec("home.nav", Pages.HOME, "Navegación", "Barra de navegación", "Flotante sobre el contenido o fija abajo.",
            opts("floating" to "Flotante", "docked" to "Fija"), "floating"),
        SettingSpec("home.labels", Pages.HOME, "Navegación", "Etiquetas de la barra", "Texto bajo los íconos de la barra.",
            opts("active" to "Solo la activa", "always" to "Siempre", "never" to "Nunca"), "active"),
        SettingSpec("home.greeting", Pages.HOME, "Inicio", "Saludo", "Muestra tu nombre arriba en Inicio.", SType.Toggle, "true"),
        SettingSpec("home.week_start", Pages.HOME, "Inicio", "La semana empieza en", "Afecta al calendario y a la meta semanal.",
            opts("mon" to "Lunes", "sun" to "Domingo"), "mon"),

        // ───────────── Entrenamiento
        SettingSpec("train.goal", Pages.TRAIN, "Metas", "Meta semanal", "Entrenamientos por semana.", slider(1f, 7f, 1f, " días"), "4"),
        SettingSpec("train.sets", Pages.TRAIN, "Valores por defecto", "Series por defecto", "Para ejercicios nuevos.", slider(1f, 10f, 1f), "3"),
        SettingSpec("train.reps", Pages.TRAIN, "Valores por defecto", "Repeticiones por defecto", "Para ejercicios nuevos.", slider(1f, 30f, 1f), "10"),
        SettingSpec("train.rest", Pages.TRAIN, "Valores por defecto", "Descanso por defecto", "Segundos entre series.", slider(15f, 300f, 15f, " s"), "90"),
        SettingSpec("train.autorest", Pages.TRAIN, "Sesión de entrenamiento", "Iniciar descanso solo", "Al completar una serie arranca el temporizador.", SType.Toggle, "true"),
        SettingSpec("train.rest_alert", Pages.TRAIN, "Sesión de entrenamiento", "Aviso al terminar el descanso", "Cómo te avisa el temporizador.",
            opts("off" to "Ninguno", "vibrate" to "Vibración", "sound" to "Sonido", "both" to "Vibración y sonido"), "vibrate"),
        SettingSpec("train.keep_awake", Pages.TRAIN, "Sesión de entrenamiento", "Mantener pantalla encendida", "Durante el entrenamiento guiado.", SType.Toggle, "true"),
        SettingSpec("train.show_prev", Pages.TRAIN, "Sesión de entrenamiento", "Mostrar la serie anterior", "Te recuerda el peso y las reps de la última vez.", SType.Toggle, "true"),

        // ───────────── FITBOT
        SettingSpec("bot.model", Pages.BOT, "Modelo", "Modelo de OpenRouter", "Identificador del modelo que usa FITBOT.", SType.Text("proveedor/modelo"), "xiaomi/mimo-v2.6-flash"),
        SettingSpec("bot.effort", Pages.BOT, "Modelo", "Esfuerzo de razonamiento", "Solo se usa si el modelo soporta razonamiento.",
            opts("auto" to "Automático", "off" to "Sin razonar", "low" to "Bajo", "medium" to "Medio", "high" to "Alto"), "auto"),
        SettingSpec("bot.temperature", Pages.BOT, "Modelo", "Creatividad", "0 = más preciso, 1.5 = más creativo.", slider(0f, 1.5f, 0.1f, "", 1), "0.7"),
        SettingSpec("bot.stream", Pages.BOT, "Modelo", "Respuesta en vivo", "Muestra el texto mientras se escribe.", SType.Toggle, "true"),
        SettingSpec("bot.history", Pages.BOT, "Conversación", "Mensajes que recuerda", "Cuántos mensajes anteriores se envían al modelo.", slider(4f, 40f, 2f), "20"),
        SettingSpec("bot.context", Pages.BOT, "Conversación", "Enviar resumen de mis datos", "Así FITBOT conoce tus rutinas y estadísticas sin que se lo pidas.", SType.Toggle, "true"),
        SettingSpec("bot.tone", Pages.BOT, "Personalidad", "Tono", "Cómo te habla FITBOT.",
            opts("friendly" to "Amigable", "coach" to "Entrenador exigente", "brief" to "Breve y directo", "serious" to "Serio"), "friendly"),
        SettingSpec("bot.length", Pages.BOT, "Personalidad", "Longitud de respuestas", "Qué tan largas son las respuestas.",
            opts("short" to "Cortas", "normal" to "Normales", "long" to "Detalladas"), "normal"),
        SettingSpec("bot.show_reasoning", Pages.BOT, "Personalidad", "Mostrar el razonamiento", "Si el modelo razona, muestra su proceso.", SType.Toggle, "true"),
        SettingSpec("bot.confirm", Pages.BOT, "Control de la app", "Cambios de FITBOT", "Qué hace antes de modificar tus datos.",
            opts("auto" to "Aplicar y avisarme (con Deshacer)", "destructive" to "Preguntar solo al borrar", "always" to "Preguntar siempre"), "auto"),

        // ───────────── Widgets
        SettingSpec("widgets.theme", Pages.WIDGETS, "Apariencia", "Estilo de los widgets", "Colores de los widgets en la pantalla de inicio.",
            opts("app" to "Igual que la app", "dark" to "Siempre oscuro", "light" to "Siempre claro", "accent" to "Color de la paleta"), "app"),
        SettingSpec("widgets.opacity", Pages.WIDGETS, "Apariencia", "Transparencia del fondo", "Más bajo = se ve más tu fondo de pantalla.", slider(40f, 100f, 5f, "%"), "100"),
        SettingSpec("widgets.led", Pages.WIDGETS, "Apariencia", "Números de puntos LED", "Los números grandes y los títulos de los widgets se dibujan como un letrero LED.", SType.Toggle, "true"),

        // ───────────── Recordatorios
        SettingSpec("reminders.enabled", Pages.REMINDERS, "General", "Recordatorios", "Interruptor general de todos los avisos.", SType.Toggle, "true"),
        SettingSpec("reminders.time", Pages.REMINDERS, "General", "Hora por defecto", "Para recordatorios nuevos.", SType.Time, "18:00"),
        SettingSpec("reminders.skip_done", Pages.REMINDERS, "General", "No avisar si ya entrenaste", "Si hoy ya registraste series, el aviso se salta.", SType.Toggle, "true"),
        SettingSpec("reminders.snooze", Pages.REMINDERS, "General", "Tiempo de «Posponer»", "Cuánto tarda en volver a sonar.", slider(5f, 120f, 5f, " min"), "30"),

        // ───────────── Ícono de la app
        SettingSpec("app.icon", Pages.ICON, "Ícono", "Color del ícono", "Cambia el ícono de la app en tu pantalla de inicio.",
            opts("lavanda" to "Lavanda", "menta" to "Menta", "mandarina" to "Mandarina", "rosa" to "Rosa neón", "azul" to "Azul eléctrico",
                "lima" to "Lima", "fuego" to "Fuego", "ambar" to "Ámbar", "turquesa" to "Turquesa", "mono" to "Monocromo"), "lavanda"),

        // ───────────── Sueño
        SettingSpec("sleep.goal", Pages.SLEEP, "Meta", "Meta de sueño", "Horas que quieres dormir por noche.", slider(5f, 10f, 0.5f, " h", 1), "8"),
        SettingSpec("sleep.auto_sync", Pages.SLEEP, "Datos", "Actualizar al abrir la app", "Trae las noches nuevas de los conectores de sueño que tengas activados.", SType.Toggle, "true"),
        SettingSpec("sleep.phone_gap", Pages.SLEEP, "Estimación con el teléfono", "Mínimo sin tocar el teléfono", "Para estimar una noche, el teléfono tiene que estar sin usarse al menos este tiempo seguido.", slider(2f, 6f, 0.5f, " h", 1), "3"),

        // ───────────── Conectores
        SettingSpec("conn.namida.enabled", Pages.CONNECTORS, "Namida", "Conectar Namida", "Deja que FITSTAT vea y controle lo que suena en Namida.", SType.Toggle, "false"),
        SettingSpec("conn.namida.pause_rest", Pages.CONNECTORS, "Namida", "Pausar la música en el descanso", "Al empezar el temporizador de descanso de una rutina, Namida se pausa.", SType.Toggle, "true", visibleIf = "conn.namida.enabled" to "true"),
        SettingSpec("conn.namida.resume_rest", Pages.CONNECTORS, "Namida", "Reanudar al terminar el descanso", "Cuando se acaba el descanso, la música vuelve a sonar sola.", SType.Toggle, "true", visibleIf = "conn.namida.enabled" to "true"),
        SettingSpec("conn.namida.fade", Pages.CONNECTORS, "Namida", "Subida gradual del volumen", "Segundos que tarda en volver al volumen de antes (0 = de golpe).", slider(0f, 20f, 1f, " s"), "6", visibleIf = "conn.namida.resume_rest" to "true"),
        SettingSpec("conn.namida.fade_start", Pages.CONNECTORS, "Namida", "Volumen con el que arranca", "Qué parte del volumen de antes tiene al reanudar, antes de subir.", slider(0f, 50f, 5f, "%"), "10", visibleIf = "conn.namida.resume_rest" to "true"),
        SettingSpec("conn.spotify.enabled", Pages.CONNECTORS, "Spotify", "Conectar Spotify", "Deja que FITSTAT vea y controle lo que suena en Spotify.", SType.Toggle, "false"),
        SettingSpec("conn.spotify.pause_rest", Pages.CONNECTORS, "Spotify", "Pausar la música en el descanso", "Al empezar el temporizador de descanso de una rutina, Spotify se pausa.", SType.Toggle, "true", visibleIf = "conn.spotify.enabled" to "true"),
        SettingSpec("conn.spotify.resume_rest", Pages.CONNECTORS, "Spotify", "Reanudar al terminar el descanso", "Cuando se acaba el descanso, la música vuelve a sonar sola.", SType.Toggle, "true", visibleIf = "conn.spotify.enabled" to "true"),
        SettingSpec("conn.spotify.fade", Pages.CONNECTORS, "Spotify", "Subida gradual del volumen", "Segundos que tarda en volver al volumen de antes (0 = de golpe).", slider(0f, 20f, 1f, " s"), "6", visibleIf = "conn.spotify.resume_rest" to "true"),
        SettingSpec("conn.spotify.fade_start", Pages.CONNECTORS, "Spotify", "Volumen con el que arranca", "Qué parte del volumen de antes tiene al reanudar, antes de subir.", slider(0f, 50f, 5f, "%"), "10", visibleIf = "conn.spotify.resume_rest" to "true"),
        SettingSpec("conn.ytmusic.enabled", Pages.CONNECTORS, "YouTube Music", "Conectar YouTube Music", "Deja que FITSTAT vea y controle lo que suena en YouTube Music.", SType.Toggle, "false"),
        SettingSpec("conn.ytmusic.pause_rest", Pages.CONNECTORS, "YouTube Music", "Pausar la música en el descanso", "Al empezar el temporizador de descanso de una rutina, YouTube Music se pausa.", SType.Toggle, "true", visibleIf = "conn.ytmusic.enabled" to "true"),
        SettingSpec("conn.ytmusic.resume_rest", Pages.CONNECTORS, "YouTube Music", "Reanudar al terminar el descanso", "Cuando se acaba el descanso, la música vuelve a sonar sola.", SType.Toggle, "true", visibleIf = "conn.ytmusic.enabled" to "true"),
        SettingSpec("conn.ytmusic.fade", Pages.CONNECTORS, "YouTube Music", "Subida gradual del volumen", "Segundos que tarda en volver al volumen de antes (0 = de golpe).", slider(0f, 20f, 1f, " s"), "6", visibleIf = "conn.ytmusic.resume_rest" to "true"),
        SettingSpec("conn.ytmusic.fade_start", Pages.CONNECTORS, "YouTube Music", "Volumen con el que arranca", "Qué parte del volumen de antes tiene al reanudar, antes de subir.", slider(0f, 50f, 5f, "%"), "10", visibleIf = "conn.ytmusic.resume_rest" to "true"),
        SettingSpec("conn.namstat.enabled", Pages.CONNECTORS, "NAMSTAT", "Conectar NAMSTAT", "Deja que FITSTAT vea y controle NAMSTAT, el reproductor del ecosistema T-A-T, con el Enlace T-A-T (cola, listas, estadísticas, búsqueda y más).", SType.Toggle, "false"),
        SettingSpec("conn.namstat.pause_rest", Pages.CONNECTORS, "NAMSTAT", "Pausar la música en el descanso", "Al empezar el temporizador de descanso de una rutina, NAMSTAT se pausa.", SType.Toggle, "true", visibleIf = "conn.namstat.enabled" to "true"),
        SettingSpec("conn.namstat.resume_rest", Pages.CONNECTORS, "NAMSTAT", "Reanudar al terminar el descanso", "Cuando se acaba el descanso, la música vuelve a sonar sola.", SType.Toggle, "true", visibleIf = "conn.namstat.enabled" to "true"),
        SettingSpec("conn.namstat.fade", Pages.CONNECTORS, "NAMSTAT", "Subida gradual del volumen", "Segundos que tarda en volver al volumen de antes (0 = de golpe). NAMSTAT hace el fundido con su propio volumen, sin tocar el del teléfono.", slider(0f, 30f, 1f, " s"), "6", visibleIf = "conn.namstat.resume_rest" to "true"),
        SettingSpec("conn.namstat.fade_start", Pages.CONNECTORS, "NAMSTAT", "Volumen con el que arranca", "Qué parte del volumen tiene al reanudar, antes de subir.", slider(0f, 50f, 5f, "%"), "10", visibleIf = "conn.namstat.resume_rest" to "true"),
        SettingSpec("conn.namstat.workout_sync", Pages.CONNECTORS, "NAMSTAT", "Avisar de mis entrenamientos", "NAMSTAT se entera de cuándo empiezas y terminas de entrenar (y el nombre), y lo muestra en sus ajustes. No abre NAMSTAT.", SType.Toggle, "true", visibleIf = "conn.namstat.enabled" to "true"),
        SettingSpec("conn.namstat.workout_list", Pages.CONNECTORS, "NAMSTAT", "Lista para entrenar", "Nombre de una lista de NAMSTAT que suena sola al empezar a entrenar, si no hay música sonando (déjalo vacío para no poner nada). Si NAMSTAT está cerrado, lo abre un momento y vuelve a FITSTAT.", SType.Text("Nombre de la lista"), "", visibleIf = "conn.namstat.enabled" to "true"),
        SettingSpec("conn.namstat.workout_shuffle", Pages.CONNECTORS, "NAMSTAT", "Lista de entreno en aleatorio", "Mezcla la lista de entreno al ponerla.", SType.Toggle, "true", visibleIf = "conn.namstat.enabled" to "true"),
        SettingSpec("conn.health.enabled", Pages.CONNECTORS, "Health Connect", "Conectar Health Connect", "Trae tu sueño (con fases) de lo que registra tu reloj o pulsera.", SType.Toggle, "false"),
        SettingSpec("conn.phone.enabled", Pages.CONNECTORS, "Uso del teléfono", "Estimar el sueño con el teléfono", "Calcula cuántas horas dormiste según cuánto tiempo seguido no tocaste el teléfono de noche.", SType.Toggle, "false"),

        // ───────────── Animaciones con la música
        SettingSpec("fx.enabled", Pages.MUSICFX, "General", "Animaciones con la música", "Fondos que laten al ritmo de lo que suena (Namida, Spotify o YouTube Music), detrás de la meta semanal y del widget de música.", SType.Toggle, "true"),
        SettingSpec("fx.source", Pages.MUSICFX, "General", "Fuente del ritmo", "Automática: lee el audio del teléfono si diste el permiso y el sistema lo deja; si no, usa un ritmo aproximado. Aproximada: siempre un ritmo a un tempo fijo (no sigue la canción).",
            opts("auto" to "Automática", "approx" to "Aproximada"), "auto", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.bpm", Pages.MUSICFX, "General", "Tempo del ritmo aproximado", "Pulsos por minuto del ritmo aproximado. Elige uno parecido al de tu música.", slider(60f, 180f, 2f, " bpm"), "110", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.idle", Pages.MUSICFX, "General", "Fondo suave con la música en pausa", "Con la música en pausa, el fondo sigue con un movimiento tranquilo en vez de apagarse.", SType.Toggle, "false", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.goal.on", Pages.MUSICFX, "Meta semanal", "En la meta semanal", "Anima el fondo de la tarjeta de la meta semanal del inicio.", SType.Toggle, "true", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.goal.style", Pages.MUSICFX, "Meta semanal", "Estilo en la meta semanal", "La animación de fondo de esa tarjeta.", FX_STYLES, "waves", visibleIf = "fx.goal.on" to "true"),
        SettingSpec("fx.music.on", Pages.MUSICFX, "Widget de música", "En el widget de música", "Anima el fondo del widget de música.", SType.Toggle, "true", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.music.style", Pages.MUSICFX, "Widget de música", "Estilo en el widget de música", "La animación de fondo de ese widget.", FX_STYLES, "bars", visibleIf = "fx.music.on" to "true"),
        SettingSpec("fx.fade_out", Pages.MUSICFX, "Al pausar o terminar", "Desvanecer al pausar o terminar", "Las animaciones se apagan poco a poco cuando la música se pausa o se acaba, en vez de cortarse de golpe.", SType.Toggle, "true", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.fade_secs", Pages.MUSICFX, "Al pausar o terminar", "Duración del desvanecido", "Cuántos segundos tardan en apagarse.", slider(0.5f, 6f, 0.5f, " s", 1), "2.5", visibleIf = "fx.fade_out" to "true"),
        SettingSpec("music.tint", Pages.MUSICFX, "Color de la portada", "Tintar la app con la portada", "Mientras suena la música, la app toma el color dominante de la portada. Al pausar o terminar, vuelve despacio a tu color.", SType.Toggle, "true"),
        SettingSpec("music.tint_strength", Pages.MUSICFX, "Color de la portada", "Fuerza del color", "Qué tanto cambia la app hacia el color de la portada.", slider(20f, 100f, 5f, "%"), "80", visibleIf = "music.tint" to "true"),
        SettingSpec("fx.cover", Pages.MUSICFX, "Portada", "Vibración de la portada", "La portada de la canción vibra muy sutilmente al ritmo.", SType.Toggle, "true", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.cover_amount", Pages.MUSICFX, "Portada", "Fuerza de la vibración", "Qué tanto se mueve la portada (siempre sutil).", slider(10f, 100f, 5f, "%"), "40", visibleIf = "fx.cover" to "true"),
        SettingSpec("fx.intensity", Pages.MUSICFX, "Apariencia", "Intensidad", "Qué tan visible es la animación. Más baja se lee mejor el texto de encima.", slider(10f, 100f, 5f, "%"), "60", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.sens", Pages.MUSICFX, "Apariencia", "Sensibilidad al ritmo", "Qué tanto reacciona a los golpes y al volumen de la música.", slider(10f, 100f, 5f, "%"), "60", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.speed", Pages.MUSICFX, "Apariencia", "Velocidad del movimiento", "Qué tan rápido se mueven las ondas, partículas y luces (no cambia el tempo).", slider(25f, 200f, 5f, "%"), "100", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.color", Pages.MUSICFX, "Apariencia", "Color",
            "Auto: el que mejor se ve en cada tarjeta. Portada: toma el color de la carátula de la canción. Arcoíris: cambia de color con el tiempo.",
            opts("auto" to "Auto", "accent" to "Acento", "contrast" to "Contraste", "cover" to "Portada", "rainbow" to "Arcoíris"), "auto", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.quality", Pages.MUSICFX, "Apariencia", "Detalle y fluidez", "Ahorro usa menos batería; Alta dibuja más elementos y a más cuadros por segundo.",
            opts("low" to "Ahorro", "normal" to "Normal", "high" to "Alta"), "normal", visibleIf = "fx.enabled" to "true"),
        SettingSpec("fx.mix_secs", Pages.MUSICFX, "Apariencia", "Cambio en «Mezcla»", "Cada cuántos segundos cambia de animación el estilo «Mezcla».", slider(8f, 60f, 2f, " s"), "20", visibleIf = "fx.enabled" to "true"),
    ) + skinSpecs()

    val byKey: Map<String, SettingSpec> = all.associateBy { it.key }

    val pages: List<SettingsPage> = listOf(
        SettingsPage(Pages.SKIN, "Skins", "Cambia el diseño completo de la app", "Layers"),
        SettingsPage(Pages.THEME, "Tema y color", "Modo, paleta, fondo y brillo", "Palette"),
        SettingsPage(Pages.SHAPES, "Formas e íconos", "Redondeo, tarjetas e íconos", "Hexagon"),
        SettingsPage(Pages.TYPE, "Tipografía", "Fuente, tamaño y números", "Type"),
        SettingsPage(Pages.MOTION, "Animaciones", "Velocidad y vibración", "Zap"),
        SettingsPage(Pages.STATS, "Estadísticas", "Gráficas, unidades y fórmulas", "Stats"),
        SettingsPage(Pages.HOME, "Inicio y navegación", "Widgets del inicio y barra inferior", "Dashboard"),
        SettingsPage(Pages.TRAIN, "Entrenamiento", "Metas, descansos y sesión", "Dumbbell"),
        SettingsPage(Pages.BOT, "FITBOT y API", "Clave de OpenRouter, modelo y personalidad", "Bot"),
        SettingsPage(Pages.SLEEP, "Sueño", "Meta y estimación", "Moon"),
        SettingsPage(Pages.CONNECTORS, "Conectores", "Música (Namida, Spotify, YouTube Music), Health Connect y más", "Plug"),
        SettingsPage(Pages.MUSICFX, "Animaciones de música", "Fondos que laten con el ritmo", "Music"),
        SettingsPage(Pages.WIDGETS, "Widgets", "Widgets para la pantalla de inicio", "Widgets"),
        SettingsPage(Pages.REMINDERS, "Recordatorios", "Avisos de entrenamiento", "Bell"),
        SettingsPage(Pages.ICON, "Ícono de la app", "Elige el color del ícono", "Sparkles"),
    )

    fun specsOf(page: String) = all.filter { it.page == page }

    fun default(key: String): String = byKey[key]?.default ?: ""

    /** Valida y normaliza un valor para un ajuste. Devuelve null si no es válido. */
    fun normalize(spec: SettingSpec, raw: String): String? {
        val v = raw.trim()
        return when (val t = spec.type) {
            is SType.Choice -> t.options.firstOrNull { it.value.equals(v, true) || it.label.equals(v, true) }?.value
            SType.Toggle -> when (v.lowercase()) {
                "true", "si", "sí", "on", "1", "yes", "activado", "encendido" -> "true"
                "false", "no", "off", "0", "desactivado", "apagado" -> "false"
                else -> null
            }
            is SType.Slider -> v.replace(',', '.').toFloatOrNull()?.let { x ->
                val c = x.coerceIn(t.min, t.max)
                val snapped = Math.round((c - t.min) / t.step) * t.step + t.min
                if (t.decimals <= 0) snapped.toInt().toString()
                else java.math.BigDecimal("%.${t.decimals}f".format(java.util.Locale.US, snapped)).stripTrailingZeros().toPlainString()
            }
            SType.ColorHex -> Regex("^#?[0-9a-fA-F]{6}$").matchEntire(v)?.let { "#" + v.removePrefix("#").uppercase() }
            is SType.Text -> v.take(200)
            SType.Time -> Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(v)?.let {
                "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2])
            }
        }
    }

    fun optionsText(spec: SettingSpec): String = when (val t = spec.type) {
        is SType.Choice -> t.options.joinToString(", ") { it.value }
        SType.Toggle -> "true, false"
        is SType.Slider -> "${t.min} a ${t.max}${t.unit}"
        SType.ColorHex -> "#RRGGBB"
        is SType.Text -> "texto"
        SType.Time -> "HH:MM"
    }
}

/** Lectura tipada de ajustes con valores por defecto. */
class Prefs(private val map: Map<String, String>) {
    fun str(key: String): String = map[key] ?: Settings.default(key)
    fun bool(key: String): Boolean = str(key) == "true"
    fun float(key: String): Float = str(key).replace(',', '.').toFloatOrNull() ?: Settings.default(key).toFloatOrNull() ?: 0f
    fun int(key: String): Int = float(key).toInt()
    fun isCustom(key: String): Boolean = map.containsKey(key) && map[key] != Settings.default(key)
    val raw: Map<String, String> get() = map
}
