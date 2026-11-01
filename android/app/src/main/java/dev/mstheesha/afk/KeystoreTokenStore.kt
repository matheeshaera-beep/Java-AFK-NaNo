package dev.mstheesha.afk

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.mstheesha.afk.engine.StoredTokens
import dev.mstheesha.afk.engine.TokenStore
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tokens live in SharedPreferences encrypted with a non-exportable AES-GCM key
 * held by the Android Keystore. Nothing usable is written to disk in the clear,
 * and `android:allowBackup="false"` keeps the blob off cloud backups (the key
 * would not travel with it anyway).
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val filesDir = context.applicationContext.filesDir

    override fun load(): StoredTokens? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return migrateLegacy()
        val text = try {
            decrypt(blob)
        } catch (e: Exception) {
            // Key rotated/lost (restore to a new device): drop the unreadable blob.
            prefs.edit().remove(KEY_BLOB).apply()
            return migrateLegacy()
        }
        return runCatching { fromJson(JSONObject(text)) }.getOrNull()
    }

    override fun save(tokens: StoredTokens) {
        runCatching {
            prefs.edit().putString(KEY_BLOB, encrypt(toJson(tokens).toString())).apply()
        }
    }

    override fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
        deleteLegacy()
    }

    // ------------------------------------------------------------- legacy import

    /**
     * 2.4 kept credentials in `ms_token.json` and mineflayer's `minecraft-auth`
     * profile folder. Both are best-effort scanned once; a successful import is
     * re-encrypted and the plaintext files are deleted.
     */
    private fun migrateLegacy(): StoredTokens? {
        val candidates = buildList {
            add(File(filesDir, "ms_token.json"))
            val authDir = File(filesDir, "minecraft-auth")
            authDir.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".json")) add(it) }
        }
        for (file in candidates) {
            if (!file.isFile) continue
            val tokens = runCatching { parseLegacy(file.readText()) }.getOrNull() ?: continue
            if (tokens.refreshToken.isBlank() && tokens.accessToken.isBlank()) continue
            save(tokens)
            deleteLegacy()
            return tokens
        }
        return null
    }

    private fun parseLegacy(text: String): StoredTokens? {
        val root = JSONObject(text)
        fun pick(vararg names: String): String {
            for (name in names) {
                val value = root.optString(name)
                if (value.isNotBlank() && value != "null") return value
            }
            return ""
        }
        // mineflayer/msal nests the token payload one level down.
        val nested = root.optJSONObject("token") ?: root.optJSONObject("data")
        fun pickDeep(vararg names: String): String {
            val direct = pick(*names)
            if (direct.isNotBlank() || nested == null) return direct
            for (name in names) {
                val value = nested.optString(name)
                if (value.isNotBlank() && value != "null") return value
            }
            return ""
        }
        return StoredTokens(
            refreshToken = pickDeep("refresh_token", "refreshToken"),
            accessToken = pickDeep("access_token", "accessToken"),
            expiresAtMs = root.optLong("expires_at", root.optLong("expiresAt", 0L)),
            uuid = pickDeep("uuid", "profileId"),
            name = pickDeep("name", "username", "profileName"),
        )
    }

    private fun deleteLegacy() {
        runCatching {
            File(filesDir, "ms_token.json").delete()
            File(filesDir, "minecraft-auth").deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ crypto

    private fun toJson(tokens: StoredTokens) = JSONObject()
        .put("refreshToken", tokens.refreshToken)
        .put("accessToken", tokens.accessToken)
        .put("expiresAtMs", tokens.expiresAtMs)
        .put("uuid", tokens.uuid)
        .put("name", tokens.name)

    private fun fromJson(json: JSONObject) = StoredTokens(
        refreshToken = json.optString("refreshToken"),
        accessToken = json.optString("accessToken"),
        expiresAtMs = json.optLong("expiresAtMs", 0L),
        uuid = json.optString("uuid"),
        name = json.optString("name"),
    )

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val parts = blob.split(':')
        require(parts.size == 2) { "bad token blob" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "afk_secure"
        const val KEY_BLOB = "tokens"
        const val KEY_ALIAS = "afk.tokens.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
