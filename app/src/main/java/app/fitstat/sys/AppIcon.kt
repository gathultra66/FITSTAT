package app.fitstat.sys

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Color del ícono de la app. Cada color es un "alias" de la actividad principal en el manifiesto;
 * solo uno está activo a la vez y el lanzador muestra el ícono de ese.
 */
object AppIcon {
    /** Colores que elige el usuario en Ajustes › Ícono de la app. */
    val ids = listOf("lavanda", "menta", "mandarina", "rosa", "azul", "lima", "fuego", "ambar", "turquesa", "mono")
    const val DEFAULT = "lavanda"

    /** Íconos propios de las skins: se activan solos cuando la skin está puesta (si «Cambiar también el ícono» está activo). */
    val skinIds = listOf("carmesi", "acero")
    private val all = ids + skinIds

    /** Qué ícono toca según los ajustes: el de la skin activa (con la sincronía) o el color elegido. */
    fun wanted(prefs: app.fitstat.data.Prefs): String {
        val skin = prefs.str("skin.id")
        return if (skin in skinIds && prefs.bool("skin.icon_sync")) skin else prefs.str("app.icon")
    }

    fun apply(ctx: Context, prefs: app.fitstat.data.Prefs) = apply(ctx, wanted(prefs))

    private fun component(ctx: Context, id: String) =
        ComponentName(ctx.packageName, "app.fitstat.Icon" + id.replaceFirstChar { it.uppercase() })

    private fun enabled(pm: PackageManager, cn: ComponentName, manifestDefault: Boolean): Boolean =
        when (pm.getComponentEnabledSetting(cn)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> manifestDefault
        }

    /** Activa el ícono [id] y desactiva los demás. Si ya estaba así, no hace nada. */
    fun apply(ctx: Context, id: String) {
        val target = if (id in all) id else DEFAULT
        val pm = ctx.packageManager
        try {
            // Primero se activa el nuevo para que nunca falte un ícono de lanzador.
            val cn = component(ctx, target)
            if (!enabled(pm, cn, target == DEFAULT)) {
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
            for (other in all) {
                if (other == target) continue
                val oc = component(ctx, other)
                if (enabled(pm, oc, other == DEFAULT)) {
                    pm.setComponentEnabledSetting(oc, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
        } catch (_: Throwable) {
            // En vistas previas o pruebas el alias puede no existir.
        }
    }
}
