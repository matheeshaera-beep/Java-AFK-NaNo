package dev.mstheesha.afk

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.mstheesha.afk.engine.StoredTokens
import dev.mstheesha.afk.engine.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AccountInfo(
    val key: String,
    val name: String,
    val uuid: String,
)

/**
 * Tokens live in SharedPreferences encrypted with a non-exportable AES-GCM key
 * held by the Android Keystore. Multiple Microsoft accounts are stored inside
 * one encrypted JSON map (`accounts_v2`), migrating single-account v1 blobs
 * automatically. Nothing usable is written to disk in the clear, and
 * `android:allowBackup="false"` keeps the blob off cloud backups.
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val filesDir = context.applicationContext.filesDir

    private val _accounts = MutableStateFlow<List<AccountInfo>>(emptyList())
    val accounts: StateFlow<List<AccountInfo>> = _accounts

    init {
        _accounts.value = listAccounts()
    }

    override fun load(): StoredTokens? = load(DEFAULT_KEY)

    override fun save(tokens: StoredTokens) = save(DEFAULT_KEY, tokens)

    override fun clear() = clear(DEFAULT_KEY)

    @Synchronized
    override fun load(accountKey: String): StoredTokens? {
        val key = normalizeKey(accountKey)
        return loadAllLocked()[key]
    }

    @Synchronized
    override fun save(accountKey: String, tokens: StoredTokens) {
        val key = normalizeKey(accountKey)
        val map = loadAllLocked().toMutableMap()
        map[key] = tokens
        saveAllLocked(map)
    }

    @Synchronized
    override fun clear(accountKey: String) {
        val key = normalizeKey(accountKey)
        val map = loadAllLocked().toMutableMap()
        map.remove(key)
        if (map.isEmpty()) {
            prefs.edit().remove(KEY_ACCOUNTS).remove(KEY_BLOB).apply()
            deleteLegacy()
            _accounts.value = emptyList()
        } else {
            saveAllLocked(map)
        }
    }

    @Synchronized
    fun listAccounts(): List<AccountInfo> = toAccountList(loadAllLocked())

    private fun normalizeKey(raw: String): String = raw.trim().ifBlank { DEFAULT_KEY }

    private fun loadAllLocked(): Map<String, StoredTokens> {
        val multiBlob = prefs.getString(KEY_ACCOUNTS, null)
        if (multiBlob != null) {
            val text = try {
                decrypt(multiBlob)
            } catch (e: Exception) {
                prefs.edit().remove(KEY_ACCOUNTS).apply()
                return emptyMap()
            }
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
            val out = LinkedHashMap<String, StoredTokens>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = root.optJSONObject(k) ?: continue
                out[k] = fromJson(obj)
            }
            return out
        }

        // One-time migration from v1 single-account blob or 2.4 plaintext files.
        val legacySingle = loadLegacySingleLocked() ?: return emptyMap()
        val migrated = linkedMapOf(DEFAULT_KEY to legacySingle)
        saveAllLocked(migrated)
        prefs.edit().remove(KEY_BLOB).apply()
        return migrated
    }

    private fun saveAllLocked(map: Map<String, StoredTokens>) {
        val root = JSONObject()
        for ((k, v) in map) {
            root.put(k, toJson(v))
        }
        runCatching {
            prefs.edit().putString(KEY_ACCOUNTS, encrypt(root.toString())).apply()
        }
        _accounts.value = toAccountList(map)
    }

    private fun toAccountList(map: Map<String, StoredTokens>): List<AccountInfo> =
        map.entries.map { (key, tok) ->
            AccountInfo(
                key = key,
                name = tok.name.ifBlank { if (key == DEFAULT_KEY) "Default account" else key },
                uuid = tok.uuid,
            )
        }

    private fun loadLegacySingleLocked(): StoredTokens? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return migrateLegacy()
        val text = try {
            decrypt(blob)
        } catch (e: Exception) {
            prefs.edit().remove(KEY_BLOB).apply()
            return migrateLegacy()
        }
        return runCatching { fromJson(JSONObject(text)) }.getOrNull()
    }

    // ------------------------------------------------------------- legacy import

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

    companion object {
        const val DEFAULT_KEY = "default"
        private const val PREFS = "afk_secure"
        private const val KEY_BLOB = "tokens"
        private const val KEY_ACCOUNTS = "accounts_v2"
        private const val KEY_ALIAS = "afk.tokens.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
