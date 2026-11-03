package app.fitstat.bot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la clave de OpenRouter en un archivo privado de la app, cifrada con una llave del
 * Android Keystore cuando el teléfono lo permite. No va en las copias de seguridad ni en las
 * exportaciones, y FITBOT no tiene ninguna herramienta para leerla.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fitstat_private", Context.MODE_PRIVATE)

    var key: String by mutableStateOf(read())
        private set

    val hasKey: Boolean get() = key.isNotBlank()

    fun save(raw: String) {
        val k = raw.filterNot { it.isWhitespace() }
        key = k
        if (k.isEmpty()) prefs.edit().remove(ENTRY).apply() else prefs.edit().putString(ENTRY, encrypt(k)).apply()
    }

    fun clear() = save("")

    /** "sk-or-v1-ab…wxyz": sirve para mostrarla sin revelarla. */
    fun masked(): String {
        val k = key
        if (k.isEmpty()) return ""
        if (k.length <= 12) return "•".repeat(k.length)
        return k.take(8) + "…" + k.takeLast(4)
    }

    private fun read(): String {
        val v = prefs.getString(ENTRY, null) ?: return ""
        return decrypt(v)
    }

    private fun encrypt(plain: String): String {
        try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.ENCRYPT_MODE, secretKey())
            val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
            return "k1:" + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
        } catch (_: Throwable) {
            return "p1:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decrypt(stored: String): String = try {
        when {
            stored.startsWith("k1:") -> {
                val raw = Base64.decode(stored.removePrefix("k1:"), Base64.NO_WRAP)
                val c = Cipher.getInstance(TRANSFORM)
                c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw, 0, 12))
                String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
            }
            stored.startsWith("p1:") -> String(Base64.decode(stored.removePrefix("p1:"), Base64.NO_WRAP), Charsets.UTF_8)
            else -> ""
        }
    } catch (_: Throwable) {
        ""
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ENTRY = "openrouter_key"
        const val ALIAS = "fitstat_secret"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
