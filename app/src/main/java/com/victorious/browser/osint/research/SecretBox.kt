package com.victorious.browser.osint.research

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * User-entered API keys (e.g. Have I Been Pwned), encrypted with an AES-256-GCM key
 * that lives in the Android Keystore and never leaves it. Ciphertext is kept in
 * app-private SharedPreferences. No API keys ship with the app.
 */
class SecretBox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("research_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
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

    fun put(name: String, value: String) {
        if (value.isBlank()) { prefs.edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val blob = c.iv + c.doFinal(value.trim().toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? = runCatching {
        val blob = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        String(c.doFinal(blob, 12, blob.size - 12))
    }.getOrNull()

    fun has(name: String): Boolean = prefs.contains(name)

    companion object {
        private const val ALIAS = "victorious_research_secrets"
        const val HIBP = "hibp_api_key"
    }
}
