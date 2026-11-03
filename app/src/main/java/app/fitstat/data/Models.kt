package app.fitstat.data

import kotlinx.serialization.Serializable

/** Músculos que maneja la app (coinciden con los de la base de ejercicios). */
@Serializable
enum class Muscle(val label: String) {
    NECK("Cuello"),
    TRAPS("Trapecio"),
    SHOULDERS("Hombros"),
    CHEST("Pecho"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    FOREARMS("Antebrazos"),
    ABS("Abdomen"),
    LATS("Dorsales"),
    MID_BACK("Espalda media"),
    LOWER_BACK("Lumbar"),
    GLUTES("Glúteos"),
    QUADS("Cuádriceps"),
    HAMSTRINGS("Isquiotibiales"),
    ADDUCTORS("Aductores"),
    ABDUCTORS("Abductores"),
    CALVES("Pantorrillas");

    /** Músculos visibles principalmente desde el frente. */
    val isFront: Boolean
        get() = this in setOf(CHEST, BICEPS, ABS, QUADS, ADDUCTORS, NECK)

    /** Músculos visibles principalmente por la espalda. */
    val isBack: Boolean
        get() = this in setOf(LATS, MID_BACK, LOWER_BACK, GLUTES, HAMSTRINGS, TRICEPS, ABDUCTORS)
}

data class Category(
    val id: String,
    val name: String,
    val tagline: String,
    val muscles: List<Muscle>,
    val back: Boolean = false,
)

/** Máximo 10 categorías, como se pidió. */
object Categories {
    val all: List<Category> = listOf(
        Category("pecho", "Pecho", "Press, aperturas y fondos", listOf(Muscle.CHEST)),
        Category("espalda", "Espalda", "Jalones, remos y peso muerto", listOf(Muscle.LATS, Muscle.MID_BACK, Muscle.LOWER_BACK), back = true),
        Category("hombro", "Hombro", "Press, elevaciones y trapecio", listOf(Muscle.SHOULDERS, Muscle.TRAPS)),
        Category("biceps", "Bíceps", "Curls y antebrazo", listOf(Muscle.BICEPS, Muscle.FOREARMS)),
        Category("triceps", "Tríceps", "Extensiones y fondos", listOf(Muscle.TRICEPS), back = true),
        Category("pierna", "Pierna", "Sentadillas, prensa y femorales", listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.ADDUCTORS)),
        Category("gluteo", "Glúteo", "Hip thrust, puentes y patadas", listOf(Muscle.GLUTES, Muscle.ABDUCTORS), back = true),
        Category("pantorrilla", "Pantorrilla", "Elevaciones de talones", listOf(Muscle.CALVES), back = true),
        Category("abdomen", "Abdomen", "Core, oblicuos y planchas", listOf(Muscle.ABS)),
        Category("cardio", "Cardio", "Caminadora, bici y saltos", listOf(Muscle.QUADS, Muscle.CALVES)),
    )

    fun byId(id: String): Category? = all.firstOrNull { it.id == id }
}

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val equipment: String = "",
    val level: String = "",
    val mechanic: String = "",
    val force: String = "",
    val primary: List<Muscle> = emptyList(),
    val secondary: List<Muscle> = emptyList(),
    val description: String = "",
    val aliases: List<String> = emptyList(),
    /** Cuadros de foto incluidos en la app (0, 1 o 2). */
    val images: Int = 0,
    val custom: Boolean = false,
    /** Nombre de archivo dentro de files/custom para ejercicios propios. */
    val customImage: String? = null,
)

@Serializable
data class ExerciseConfig(
    val exerciseId: String,
    val inSet: Boolean = true,
    val sets: Int = 3,
    val repsMin: Int = 10,
    /** 0 = repeticiones fijas (usa repsMin). */
    val repsMax: Int = 0,
    val restSec: Int = 90,
    /** 1 = lunes ... 7 = domingo. */
    val days: Set<Int> = emptySet(),
    val notes: String = "",
    val favorite: Boolean = false,
    val targetWeightKg: Double = 0.0,
) {
    val repsLabel: String get() = if (repsMax > repsMin) "$repsMin-$repsMax" else "$repsMin"
}

@Serializable
data class RoutineItem(
    val exerciseId: String,
    /** 0 = usar los valores del ejercicio. */
    val sets: Int = 0,
    val repsMin: Int = 0,
    val repsMax: Int = 0,
)

@Serializable
data class Routine(
    val id: String,
    val name: String,
    val icon: String = "dumbbell",
    val colorIdx: Int = 0,
    val items: List<RoutineItem> = emptyList(),
    val days: Set<Int> = emptySet(),
    val note: String = "",
    val reminderId: String? = null,
    val createdAt: Long = 0,
)

@Serializable
data class SetLog(
    val id: String,
    val exerciseId: String,
    val ts: Long,
    val weightKg: Double,
    val reps: Int,
    val routineId: String? = null,
    val note: String = "",
) {
    val volume: Double get() = weightKg * reps
}

@Serializable
data class Note(
    val id: String,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val colorIdx: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val exerciseId: String? = null,
)

@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val body: String = "",
    val hour: Int = 18,
    val minute: Int = 0,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val routineId: String? = null,
)

@Serializable
data class Profile(
    val name: String = "",
    val weeklyGoal: Int = 4,
    val onboarded: Boolean = false,
    val createdAt: Long = 0,
)

/** Un bloque de una pantalla editable (el inicio, Ejercicios o Rutinas): cuál es ([id], ver [HomeLayout]) y qué tamaño tiene ("s", "m" o "l"). */
@Serializable
data class HomeSlot(val id: String, val size: String = "m")

/** Un tramo de una noche de sueño: [t] es "awake", "light", "deep", "rem" o "sleep" (dormido sin más detalle). */
@Serializable
data class SleepStage(val t: String, val s: Long, val e: Long)

/**
 * Una noche (o siesta) de sueño. [source]: "health" (Health Connect, la registró un reloj o pulsera),
 * "phone" (estimada con el uso del teléfono) o "manual". Solo las de "health" traen [stages].
 */
@Serializable
data class SleepSession(
    val id: String,
    val start: Long,
    val end: Long,
    val source: String = "manual",
    val stages: List<SleepStage> = emptyList(),
    /** App que la registró (en Health Connect). */
    val origin: String = "",
    val note: String = "",
)

@Serializable
data class AppData(
    val schema: Int = 1,
    val profile: Profile = Profile(),
    val custom: List<Exercise> = emptyList(),
    val configs: Map<String, ExerciseConfig> = emptyMap(),
    val routines: List<Routine> = emptyList(),
    val logs: List<SetLog> = emptyList(),
    val notes: List<Note> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val unlocked: Map<String, Long> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
    val searchHistory: List<String> = emptyList(),
    /** Widgets del inicio, en orden. null = nunca se personalizó (se usa la disposición de siempre). */
    val homeLayout: List<HomeSlot>? = null,
    /** Bloques de las otras pantallas editables (clave: «exercises», «routines»). Sin la clave = nunca se personalizó. */
    val pageLayouts: Map<String, List<HomeSlot>> = emptyMap(),
    /** Sueño registrado (de Health Connect, estimado con el teléfono o a mano). */
    val sleep: List<SleepSession> = emptyList(),
)

object Weekdays {
    val short = listOf("L", "M", "X", "J", "V", "S", "D")
    val names = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val abbr3 = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    fun short(day: Int) = short[(day - 1).coerceIn(0, 6)]
    fun name(day: Int) = names[(day - 1).coerceIn(0, 6)]
    fun abbr(day: Int) = abbr3[(day - 1).coerceIn(0, 6)]
    fun label(days: Set<Int>): String = when {
        days.isEmpty() -> "Sin días"
        days.size == 7 -> "Todos los días"
        days == setOf(1, 2, 3, 4, 5) -> "Lun a Vie"
        days == setOf(6, 7) -> "Fin de semana"
        else -> days.sorted().joinToString(" · ") { abbr(it) }
    }
}

val Exercise.isCardio: Boolean get() = category == "cardio"
val Exercise.isBodyweight: Boolean get() = equipment.equals("Peso corporal", ignoreCase = true)

/** Texto de la unidad de "repeticiones" según el tipo de ejercicio. */
val Exercise.repsUnit: String get() = if (isCardio) "min" else "reps"
