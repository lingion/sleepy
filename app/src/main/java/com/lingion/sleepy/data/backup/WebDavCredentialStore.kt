package com.lingion.sleepy.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedWebDavSettings(
    val config: WebDavConfig,
    val password: String,
    val hasStoredPassword: Boolean,
    val passwordNeedsReentry: Boolean = false,
)

/** Stores the endpoint privately and encrypts the password with an Android Keystore AES-GCM key. */
object WebDavCredentialStore {
    private const val PREFERENCES = "sleepy_webdav"
    private const val KEY_ALIAS = "sleepy.webdav.password.v1"
    private const val SERVER_URL = "server_url"
    private const val DIRECTORY = "directory"
    private const val USERNAME = "username"
    private const val PASSWORD_CIPHER_TEXT = "password_cipher_text"
    private const val PASSWORD_IV = "password_iv"

    fun load(context: Context): SavedWebDavSettings? {
        val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(SERVER_URL, null)?.takeIf(String::isNotBlank) ?: return null
        val cipherText = prefs.getString(PASSWORD_CIPHER_TEXT, null)
        val iv = prefs.getString(PASSWORD_IV, null)
        var passwordNeedsReentry = false
        val password = if (cipherText == null || iv == null) {
            ""
        } else {
            try {
                decrypt(cipherText, iv)
            } catch (_: Exception) {
                prefs.edit().remove(PASSWORD_CIPHER_TEXT).remove(PASSWORD_IV).apply()
                passwordNeedsReentry = true
                ""
            }
        }
        return SavedWebDavSettings(
            config = WebDavConfig(
                serverUrl = serverUrl,
                directory = prefs.getString(DIRECTORY, "Sleepy") ?: "Sleepy",
                username = prefs.getString(USERNAME, "").orEmpty(),
            ),
            password = password,
            hasStoredPassword = cipherText != null && !passwordNeedsReentry,
            passwordNeedsReentry = passwordNeedsReentry,
        )
    }

    /** A null password keeps the existing secret; an empty password removes it. */
    fun save(context: Context, config: WebDavConfig, password: String?): SavedWebDavSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(SERVER_URL, config.serverUrl.trim())
            .putString(DIRECTORY, config.directory.trim())
            .putString(USERNAME, config.username.trim())
        if (password != null) {
            if (password.isEmpty()) {
                editor.remove(PASSWORD_CIPHER_TEXT).remove(PASSWORD_IV)
            } else {
                val (cipherText, iv) = encrypt(password)
                editor.putString(PASSWORD_CIPHER_TEXT, cipherText).putString(PASSWORD_IV, iv)
            }
        }
        check(editor.commit()) { "Could not save WebDAV settings" }
        return checkNotNull(load(context))
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameters = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey() ?: throw IllegalStateException("Keystore key is missing"), parameters)
        return cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey = getExistingKey() ?: run {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    }
}
