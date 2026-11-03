package app.fitstat.data

/** Construye un ejercicio del catálogo a partir de los datos generados. */
internal fun cat(
    id: String,
    name: String,
    category: String,
    equipment: String,
    level: String,
    mechanic: String,
    force: String,
    primary: String,
    secondary: String,
    images: Int,
    aliases: String,
    description: String,
): Exercise = Exercise(
    id = id,
    name = name,
    category = category,
    equipment = equipment,
    level = level,
    mechanic = mechanic,
    force = force,
    primary = parseMuscles(primary),
    secondary = parseMuscles(secondary),
    description = description,
    aliases = aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    images = images,
)

private fun parseMuscles(s: String): List<Muscle> =
    s.split(",").mapNotNull { n -> Muscle.entries.firstOrNull { it.name == n.trim() } }

object Catalog {
    val all: List<Exercise> = CATALOG_DATA
    val byId: Map<String, Exercise> = all.associateBy { it.id }
    fun inCategory(id: String): List<Exercise> = all.filter { it.category == id }
}
