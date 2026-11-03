package app.fitstat.conn

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Respuesta del Enlace T-A-T. [ok] = NAMSTAT hizo lo pedido; si no, [error] es un código estable y [message] una frase
 * para el usuario. [json] trae el resto de los datos.
 */
class LinkResult(val ok: Boolean, val error: String = "", val message: String = "", val json: JsonObject = JsonObject(emptyMap())) {
    /** NAMSTAT está instalado pero cerrado (no hay motor que conteste). */
    val notRunning: Boolean get() = error == ERR_NOT_RUNNING

    /** NAMSTAT no tiene el enlace (versión anterior a la 1.2) o ni siquiera está instalado. */
    val noLink: Boolean get() = error == ERR_NO_LINK

    fun str(k: String): String = json[k]?.jsonPrimitive?.contentOrNull.orEmpty()

    companion object {
        const val ERR_NOT_RUNNING = "not_running"
        const val ERR_NO_LINK = "no_link"
        const val ERR_DISABLED = "disabled"

        fun fail(code: String, message: String) = LinkResult(false, code, message)
    }
}

/** El Enlace T-A-T visto desde FITSTAT: llamar a NAMSTAT y, si está cerrado, abrirlo con una orden. Interfaz para poder probar sin Android. */
interface NamstatLink {
    /** Llama a un método del protocolo (ver `lib/tat/tat_bridge.dart` en NAMSTAT). Nunca lanza. */
    suspend fun call(method: String, args: JsonObject = JsonObject(emptyMap())): LinkResult

    /** Abre NAMSTAT con una orden para que la haga al arrancar. [back] = que vuelva al fondo al terminar. false si no se pudo. */
    fun launch(method: String, args: JsonObject, back: Boolean): Boolean
}

/** Versión del protocolo que entiende FITSTAT. */
const val NAMSTAT_PROTO = 1

object NamstatPackage {
    const val NAME = "com.namstat.player"
    const val ACTIVITY = "com.namstat.player.MainActivity"
    const val AUTHORITY = "com.namstat.player.tat"

    /**
     * NAMSTAT comparte como «carátula» el enlace del archivo de audio (content://media/…), que FITSTAT no puede abrir (no tiene
     * permiso sobre tu música). Para verla, se la pide a NAMSTAT por su proveedor: `content://com.namstat.player.tat/art?u=…`
     * (NAMSTAT 1.2.1 o más nueva). Lo que no sea de MediaStore se deja igual.
     */
    fun artUri(shared: String, size: Int = 640): String =
        if (!shared.startsWith("content://media/")) shared
        else "content://$AUTHORITY/art?u=" + java.net.URLEncoder.encode(shared, "UTF-8") + "&size=$size"
}

/** El enlace real: el proveedor de contenido de NAMSTAT (`content://com.namstat.player.tat`). */
class AndroidNamstatLink(private val ctx: Context) : NamstatLink {
    private val uri: Uri = Uri.parse("content://${NamstatPackage.AUTHORITY}")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun call(method: String, args: JsonObject): LinkResult = withContext(Dispatchers.IO) {
        try {
            val extras = Bundle().apply { putString("json", args.toString()) }
            val res = ctx.contentResolver.call(uri, method, null, extras)
                ?: return@withContext LinkResult.fail(LinkResult.ERR_NO_LINK, "NAMSTAT no tiene el Enlace T-A-T: actualízalo a la versión 1.2 o más nueva.")
            parse(res.getString("json"))
        } catch (_: IllegalArgumentException) {
            LinkResult.fail(LinkResult.ERR_NO_LINK, "NAMSTAT no tiene el Enlace T-A-T: instala o actualiza NAMSTAT 1.2 o más nueva.")
        } catch (_: SecurityException) {
            LinkResult.fail("forbidden", "NAMSTAT no deja usar el enlace a esta app.")
        } catch (t: Throwable) {
            LinkResult.fail("failed", t.message ?: "No pude hablar con NAMSTAT.")
        }
    }

    override fun launch(method: String, args: JsonObject, back: Boolean): Boolean = try {
        val cmd = buildJsonObject { put("method", method); put("args", args) }
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).setClassName(NamstatPackage.NAME, NamstatPackage.ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("tat_cmd", cmd.toString())
                .putExtra("tat_back", back),
        )
        true
    } catch (_: Throwable) {
        false
    }

    private fun parse(raw: String?): LinkResult {
        if (raw.isNullOrBlank()) return LinkResult.fail("failed", "NAMSTAT no contestó.")
        return try {
            val o = json.parseToJsonElement(raw).jsonObject
            val ok = o["ok"]?.jsonPrimitive?.booleanOrNull == true
            LinkResult(ok, o["error"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["message"]?.jsonPrimitive?.contentOrNull.orEmpty(), o)
        } catch (_: Throwable) {
            LinkResult.fail("failed", "No entendí la respuesta de NAMSTAT.")
        }
    }
}

internal fun obj(vararg p: Pair<String, Any?>): JsonObject = buildJsonObject {
    for ((k, v) in p) when (v) {
        null -> {}
        is Boolean -> put(k, v)
        is Int -> put(k, v)
        is Long -> put(k, v)
        is Float -> put(k, v.toDouble())
        is Double -> put(k, v)
        is String -> put(k, v)
        else -> put(k, JsonPrimitive(v.toString()))
    }
}
