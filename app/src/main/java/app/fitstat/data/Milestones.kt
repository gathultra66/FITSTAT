package app.fitstat.data

/** Un hito (logro) que se desbloquea al llegar a cierta meta. [icon] es el nombre de un `Ic`. */
class MilestoneDef(
    val id: String,
    val group: String,
    val title: String,
    val desc: String,
    val icon: String,
    val target: Double,
    val unit: String,
    val value: (Ctx) -> Double,
)

class Ctx(val data: AppData, val agg: Stats.Aggregates)

class MilestoneState(val def: MilestoneDef, val value: Double, val unlockedAt: Long?) {
    val done: Boolean get() = unlockedAt != null
    val progress: Float get() = if (def.target <= 0.0) 1f else (value / def.target).toFloat().coerceIn(0f, 1f)
}

object Milestones {
    val groups = listOf("Constancia", "Racha", "Series", "Volumen", "Fuerza", "Variedad", "Organización", "FITBOT")

    private fun m(
        id: String, group: String, title: String, desc: String, icon: String, target: Number, unit: String,
        value: (Ctx) -> Double,
    ) = MilestoneDef(id, group, title, desc, icon, target.toDouble(), unit, value)

    val all: List<MilestoneDef> = listOf(
        m("w1", "Constancia", "Primer entrenamiento", "Registra tu primer día de gym.", "Flag", 1, "días") { it.agg.workoutDays.toDouble() },
        m("w5", "Constancia", "Tomando ritmo", "5 días de entrenamiento registrados.", "Activity", 5, "días") { it.agg.workoutDays.toDouble() },
        m("w10", "Constancia", "Hábito en marcha", "10 días entrenando.", "CalendarCheck", 10, "días") { it.agg.workoutDays.toDouble() },
        m("w25", "Constancia", "Constante", "25 días entrenando.", "Medal", 25, "días") { it.agg.workoutDays.toDouble() },
        m("w50", "Constancia", "Medio centenar", "50 días entrenando.", "Award", 50, "días") { it.agg.workoutDays.toDouble() },
        m("w100", "Constancia", "Centurión", "100 días entrenando.", "Crown", 100, "días") { it.agg.workoutDays.toDouble() },
        m("w200", "Constancia", "Leyenda del gym", "200 días entrenando.", "Gem", 200, "días") { it.agg.workoutDays.toDouble() },

        m("st2", "Racha", "Dos semanas seguidas", "Cumple tu meta semanal 2 semanas seguidas.", "Flame", 2, "semanas") { it.agg.bestStreak.toDouble() },
        m("st4", "Racha", "Un mes sin fallar", "4 semanas cumpliendo tu meta.", "Flame", 4, "semanas") { it.agg.bestStreak.toDouble() },
        m("st8", "Racha", "Dos meses de hierro", "8 semanas cumpliendo tu meta.", "Flame", 8, "semanas") { it.agg.bestStreak.toDouble() },
        m("st12", "Racha", "Trimestre imparable", "12 semanas cumpliendo tu meta.", "Flame", 12, "semanas") { it.agg.bestStreak.toDouble() },
        m("st26", "Racha", "Medio año", "26 semanas cumpliendo tu meta.", "Flame", 26, "semanas") { it.agg.bestStreak.toDouble() },

        m("s10", "Series", "Calentando", "10 series registradas.", "Zap", 10, "series") { it.agg.totalSets.toDouble() },
        m("s100", "Series", "Cien series", "100 series registradas.", "Zap", 100, "series") { it.agg.totalSets.toDouble() },
        m("s500", "Series", "Quinientas series", "500 series registradas.", "Bolt", 500, "series") { it.agg.totalSets.toDouble() },
        m("s1000", "Series", "Mil series", "1,000 series registradas.", "Bolt", 1000, "series") { it.agg.totalSets.toDouble() },
        m("s5000", "Series", "Máquina", "5,000 series registradas.", "Atom", 5000, "series") { it.agg.totalSets.toDouble() },

        m("v1k", "Volumen", "Primera tonelada", "Mueve 1,000 kg en total.", "Weight", 1000, "kg") { it.agg.totalVolume },
        m("v10k", "Volumen", "Camión cargado", "Mueve 10,000 kg en total.", "Weight", 10000, "kg") { it.agg.totalVolume },
        m("v50k", "Volumen", "Grúa", "Mueve 50,000 kg en total.", "Weight", 50000, "kg") { it.agg.totalVolume },
        m("v100k", "Volumen", "Hércules", "Mueve 100,000 kg en total.", "Weight", 100000, "kg") { it.agg.totalVolume },
        m("v500k", "Volumen", "Titán", "Mueve 500,000 kg en total.", "Mountain", 500000, "kg") { it.agg.totalVolume },
        m("v1m", "Volumen", "Un millón", "Mueve 1,000,000 kg en total.", "Gem", 1000000, "kg") { it.agg.totalVolume },

        m("pr1", "Fuerza", "Primer récord", "Supera tu mejor marca en un ejercicio.", "Trophy", 1, "récords") { it.agg.prCount.toDouble() },
        m("pr10", "Fuerza", "Rompe marcas", "10 récords personales.", "Trophy", 10, "récords") { it.agg.prCount.toDouble() },
        m("pr25", "Fuerza", "Coleccionista de récords", "25 récords personales.", "Trophy", 25, "récords") { it.agg.prCount.toDouble() },
        m("pr50", "Fuerza", "Imparable", "50 récords personales.", "Crown", 50, "récords") { it.agg.prCount.toDouble() },
        m("kg60", "Fuerza", "Club de los 60", "Levanta 60 kg o más en una serie.", "Dumbbell", 60, "kg") { it.agg.maxWeight },
        m("kg100", "Fuerza", "Club de los 100", "Levanta 100 kg o más en una serie.", "Dumbbell", 100, "kg") { it.agg.maxWeight },
        m("kg140", "Fuerza", "Club de los 140", "Levanta 140 kg o más en una serie.", "Dumbbell", 140, "kg") { it.agg.maxWeight },

        m("ex5", "Variedad", "Probando cosas", "Registra 5 ejercicios distintos.", "Swap", 5, "ejercicios") { it.agg.distinctExercises.toDouble() },
        m("ex15", "Variedad", "Repertorio", "Registra 15 ejercicios distintos.", "Layers", 15, "ejercicios") { it.agg.distinctExercises.toDouble() },
        m("ex30", "Variedad", "Enciclopedia", "Registra 30 ejercicios distintos.", "Book", 30, "ejercicios") { it.agg.distinctExercises.toDouble() },
        m("cat10", "Variedad", "Cuerpo completo", "Entrena las 10 categorías de ejercicio.", "Body", 10, "categorías") { it.agg.categoriesTrained.toDouble() },

        m("rt1", "Organización", "Plan en marcha", "Crea tu primera rutina.", "Routines", 1, "rutinas") { it.data.routines.size.toDouble() },
        m("rt3", "Organización", "Planificador", "Ten 3 rutinas distintas.", "ClipboardList", 3, "rutinas") { it.data.routines.size.toDouble() },
        m("set10", "Organización", "Mi set", "Agrega 10 ejercicios a tu set habitual.", "ListPlus", 10, "ejercicios") { it.data.configs.values.count { c -> c.inSet }.toDouble() },
        m("nt1", "Organización", "Anotador", "Escribe tu primera anotación.", "NotebookPen", 1, "notas") { it.data.notes.size.toDouble() },
        m("nt5", "Organización", "Cuaderno lleno", "Escribe 5 anotaciones.", "Note", 5, "notas") { it.data.notes.size.toDouble() },
        m("rem1", "Organización", "Con aviso", "Crea tu primer recordatorio.", "BellRing", 1, "avisos") { it.data.reminders.size.toDouble() },

        m("bot1", "FITBOT", "Hola, FITBOT", "Chatea por primera vez con tu asistente.", "Bot", 1, "chats") { if (it.data.unlocked.containsKey("bot1")) 1.0 else 0.0 },
    )

    val byId: Map<String, MilestoneDef> = all.associateBy { it.id }

    fun evaluate(data: AppData, agg: Stats.Aggregates): List<MilestoneState> {
        val ctx = Ctx(data, agg)
        return all.map { d ->
            val v = d.value(ctx)
            MilestoneState(d, v, data.unlocked[d.id])
        }
    }

    /** Hitos que cumplen su meta pero aún no están marcados como desbloqueados. */
    fun newlyDone(data: AppData, agg: Stats.Aggregates): List<MilestoneDef> {
        val ctx = Ctx(data, agg)
        return all.filter { d -> !data.unlocked.containsKey(d.id) && d.value(ctx) >= d.target }
    }

    /** El hito pendiente más cercano a lograrse. */
    fun next(states: List<MilestoneState>): MilestoneState? =
        states.filter { !it.done && it.value > 0.0 }.maxByOrNull { it.progress }
            ?: states.firstOrNull { !it.done }
}
