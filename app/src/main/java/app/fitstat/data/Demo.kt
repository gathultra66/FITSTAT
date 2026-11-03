package app.fitstat.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/** Datos de ejemplo para conocer la app (y para las pruebas de pantalla). Es una historia de 10 semanas. */
object Demo {
    private class Plan(val id: String, val sets: Int, val repsLo: Int, val repsHi: Int, val kg: Double, val rest: Int = 90)

    private val push = listOf(
        Plan("press_de_banca_con_barra", 4, 6, 10, 70.0, 120),
        Plan("press_inclinado_con_mancuernas", 3, 8, 12, 24.0),
        Plan("press_militar_sentado", 3, 8, 10, 40.0),
        Plan("elevaciones_laterales", 3, 12, 15, 9.0, 60),
        Plan("extension_de_triceps_con_cuerda", 3, 10, 14, 27.5, 60),
        Plan("fondos_en_paralelas_triceps", 3, 8, 12, 10.0),
    )
    private val pull = listOf(
        Plan("dominadas", 4, 5, 9, 0.0, 120),
        Plan("remo_con_barra", 4, 8, 10, 60.0, 105),
        Plan("jalon_al_pecho_agarre_ancho", 3, 10, 12, 55.0),
        Plan("curl_con_barra_z", 3, 8, 12, 30.0, 60),
        Plan("curl_martillo", 3, 10, 12, 14.0, 60),
        Plan("face_pull", 3, 12, 15, 22.5, 60),
    )
    private val legs = listOf(
        Plan("sentadilla_con_barra", 4, 5, 8, 90.0, 150),
        Plan("prensa_de_pierna", 3, 10, 12, 160.0, 120),
        Plan("peso_muerto_rumano", 3, 8, 10, 80.0, 120),
        Plan("curl_femoral_tumbado", 3, 10, 12, 40.0, 75),
        Plan("hip_thrust_con_barra", 3, 8, 12, 100.0, 105),
        Plan("elevacion_de_talones_de_pie", 4, 12, 15, 80.0, 60),
    )

    private fun snap(v: Double, step: Double = 2.5): Double = Math.round(v / step) * step

    fun build(base: AppData, now: Long, zone: ZoneId, formula: String = "epley"): AppData {
        val rnd = Random(2610)
        val today: LocalDate = Fmt.date(now, zone)
        val routines = listOf(
            Routine("demo_r1", "Empuje", "Dumbbell", 0, push.map { RoutineItem(it.id) }, setOf(1, 4), "Pecho, hombro y tríceps.", null, now - 70L * 86_400_000),
            Routine("demo_r2", "Tirón", "Bolt", 1, pull.map { RoutineItem(it.id) }, setOf(2, 5), "Espalda y bíceps.", null, now - 69L * 86_400_000),
            Routine("demo_r3", "Pierna", "Footprints", 2, legs.map { RoutineItem(it.id) }, setOf(3, 6), "Cuádriceps, femorales, glúteo y pantorrilla.", null, now - 68L * 86_400_000),
        )
        val groups = listOf(push to setOf(1, 4), pull to setOf(2, 5), legs to setOf(3, 6))
        val configs = HashMap<String, ExerciseConfig>()
        for ((plans, days) in groups) for (p in plans) {
            configs[p.id] = ExerciseConfig(p.id, true, p.sets, p.repsLo, p.repsHi, p.rest, days)
        }
        configs["press_de_banca_con_barra"] = configs["press_de_banca_con_barra"]!!.copy(
            notes = "Escápulas juntas, pies firmes. Baja controlado 2 s y sube explosivo.", favorite = true, targetWeightKg = 80.0,
        )
        configs["sentadilla_con_barra"] = configs["sentadilla_con_barra"]!!.copy(notes = "Rodillas abiertas, pecho arriba. Profundidad hasta paralelo.", favorite = true)
        configs["dominadas"] = configs["dominadas"]!!.copy(notes = "Sin balanceo. Meta: 10 repeticiones limpias.")

        val logs = ArrayList<SetLog>()
        var n = 0
        val firstDay = today.minusDays(70)
        var d = firstDay
        while (!d.isAfter(today)) {
            val dow = d.dayOfWeek.value
            val grp = groups.firstOrNull { dow in it.second }
            val weekIdx = java.time.temporal.ChronoUnit.DAYS.between(firstDay, d) / 7.0
            val skip = rnd.nextInt(100) < 24 || (d == today && rnd.nextInt(100) < 50)
            if (grp != null && !skip && !(d == today)) {
                var t = d.atTime(18, 5 + rnd.nextInt(30)).atZone(zone).toInstant().toEpochMilli()
                val rid = routines.first { dow in it.days }.id
                for (p in grp.first) {
                    val prog = 1.0 + 0.011 * weekIdx + (rnd.nextDouble() - 0.5) * 0.02
                    val w = if (p.kg == 0.0) 0.0 else snap(p.kg * prog, if (p.kg < 20) 1.0 else 2.5)
                    for (s in 0 until p.sets) {
                        val reps = (p.repsHi - s - (if (rnd.nextInt(4) == 0) 1 else 0)).coerceIn(p.repsLo - 1, p.repsHi)
                        logs.add(SetLog("demo_l${n++}", p.id, t, w, reps.coerceAtLeast(1), rid))
                        t += (p.rest + 40 + rnd.nextInt(30)) * 1000L
                    }
                    t += 120_000L
                }
            }
            d = d.plusDays(1)
        }

        val notes = listOf(
            Note("demo_n1", "Meta del mes", "Subir 5 kg en press de banca y llegar a 10 dominadas limpias.", true, 0, now - 20L * 86_400_000, now - 20L * 86_400_000),
            Note("demo_n2", "Prensa de pierna", "Asiento en nivel 5, pies al centro de la plataforma.", false, 2, now - 14L * 86_400_000, now - 14L * 86_400_000, "prensa_de_pierna"),
            Note("demo_n3", "Calentamiento de hombro", "Banda elástica: 2×15 rotaciones externas antes de cualquier press.", true, 4, now - 9L * 86_400_000, now - 9L * 86_400_000),
        )
        val reminders = listOf(
            Reminder("demo_a1", "Hora de entrenar", "Hoy toca rutina. ¡Vamos!", 18, 30, setOf(1, 2, 3, 4, 5, 6), true, null),
        )

        val data = base.copy(
            profile = base.profile.copy(onboarded = true),
            custom = emptyList(),
            configs = configs,
            routines = routines,
            logs = logs,
            notes = notes,
            reminders = reminders,
            unlocked = emptyMap(),
        )
        val ex = (Catalog.all).associateBy { it.id }
        val mondayFirst = (data.settings["home.week_start"] ?: "mon") == "mon"
        val goal = data.settings["train.goal"]?.toIntOrNull() ?: 4
        val agg = Stats.aggregates(data, ex, goal, mondayFirst, today, formula, zone)
        val fresh = Milestones.newlyDone(data, agg)
        val unlocked = fresh.associate { it.id to (now - (rnd.nextInt(60) + 1) * 86_400_000L) }
        return data.copy(unlocked = unlocked)
    }
}
