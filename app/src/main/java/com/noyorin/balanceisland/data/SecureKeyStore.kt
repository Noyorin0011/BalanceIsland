package com.noyorin.balanceisland.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores API keys encrypted with a non-exportable key in Android Keystore. */
class SecureKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun credentials(): List<ApiCredential> {
        migrateLegacyCredentials()
        val index = prefs.getString(KEY_CREDENTIAL_INDEX, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(index)
            (0 until array.length()).mapNotNull { position ->
                val item = array.getJSONObject(position)
                val id = item.getString("id")
                val apiKey = get(keyName(id))
                if (apiKey.isBlank()) return@mapNotNull null
                ApiCredential(
                    id = id,
                    provider = Provider.valueOf(item.getString("provider")),
                    label = item.optString("label").trim().take(MAX_LABEL_LENGTH),
                    apiKey = apiKey,
                    keySuffix = item.optString("keySuffix").ifBlank { suffixOf(apiKey) }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun summaries(): List<CredentialSummary> = credentials().map {
        CredentialSummary(it.id, it.provider, it.label, it.keySuffix)
    }

    fun addCredential(provider: Provider, label: String, apiKey: String): ApiCredential {
        val cleanedKey = apiKey.trim()
        require(cleanedKey.isNotBlank()) { "API Key 不能为空" }
        val current = credentials().toMutableList()
        val existingIndex = current.indexOfFirst {
            it.provider == provider && it.apiKey == cleanedKey
        }
        val credential = if (existingIndex >= 0) {
            current[existingIndex].copy(label = label.trim().take(MAX_LABEL_LENGTH))
        } else {
            ApiCredential(
                id = UUID.randomUUID().toString().replace("-", ""),
                provider = provider,
                label = label.trim().take(MAX_LABEL_LENGTH),
                apiKey = cleanedKey,
                keySuffix = suffixOf(cleanedKey)
            )
        }
        if (existingIndex >= 0) current[existingIndex] = credential else current.add(credential)
        put(keyName(credential.id), credential.apiKey)
        saveIndex(current)
        return credential
    }

    fun removeCredential(id: String) {
        val remaining = credentials().filterNot { it.id == id }
        prefs.edit().remove(keyName(id)).apply()
        saveIndex(remaining)
    }

    private fun migrateLegacyCredentials() {
        if (prefs.contains(KEY_CREDENTIAL_INDEX)) return
        val migrated = buildList {
            get(KEY_DEEPSEEK).takeIf { it.isNotBlank() }?.let { apiKey ->
                add(ApiCredential(LEGACY_DEEPSEEK_ID, Provider.DEEPSEEK, "", apiKey, suffixOf(apiKey)))
            }
            get(KEY_OPENAI_ADMIN).takeIf { it.isNotBlank() }?.let { apiKey ->
                add(ApiCredential(LEGACY_OPENAI_ID, Provider.OPENAI, "", apiKey, suffixOf(apiKey)))
            }
        }
        migrated.forEach { put(keyName(it.id), it.apiKey) }
        saveIndex(migrated)
    }

    private fun saveIndex(credentials: List<ApiCredential>) {
        val array = JSONArray()
        credentials.forEach { credential ->
            array.put(
                JSONObject()
                    .put("id", credential.id)
                    .put("provider", credential.provider.name)
                    .put("label", credential.label)
                    .put("keySuffix", credential.keySuffix)
            )
        }
        prefs.edit().putString(KEY_CREDENTIAL_INDEX, array.toString()).apply()
    }

    private fun suffixOf(apiKey: String): String = apiKey.takeLast(KEY_SUFFIX_LENGTH)
    private fun keyName(id: String) = "credential_key_$id"

    private fun put(name: String, plaintext: String) {
        if (plaintext.isBlank()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plaintext.trim().toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    private fun get(name: String): String {
        val encoded = prefs.getString(name, null) ?: return ""
        return runCatching {
            val packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
            val ivLength = packed.int
            require(ivLength in 12..32)
            val iv = ByteArray(ivLength).also(packed::get)
            val encrypted = ByteArray(packed.remaining()).also(packed::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, iv)
            )
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()
    }

    companion object {
        private const val PREFS_NAME = "secure_credentials"
        private const val KEY_ALIAS = "balance_island_aes_key_v1"
        private const val KEY_DEEPSEEK = "deepseek_key"
        private const val KEY_OPENAI_ADMIN = "openai_admin_key"
        private const val KEY_CREDENTIAL_INDEX = "credential_index_v2"
        private const val LEGACY_DEEPSEEK_ID = "legacy_deepseek"
        private const val LEGACY_OPENAI_ID = "legacy_openai"
        private const val KEY_SUFFIX_LENGTH = 4
        private const val MAX_LABEL_LENGTH = 12
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
