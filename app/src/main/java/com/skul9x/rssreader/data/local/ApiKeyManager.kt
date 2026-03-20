package com.skul9x.rssreader.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manager for storing and retrieving Gemini API keys.
 * Uses EncryptedSharedPreferences for secure storage.
 */
class ApiKeyManager private constructor(context: Context) {

    companion object {
        // Changed name to ensure we start fresh with secure prefs
        private const val PREFS_NAME = "api_keys_secure"
        private const val KEY_API_KEYS = "gemini_api_keys"
        
        @Volatile
        private var instance: ApiKeyManager? = null
        
        fun getInstance(context: Context): ApiKeyManager {
            return instance ?: synchronized(this) {
                instance ?: ApiKeyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to clear data if encryption keys are corrupted (common on reinstall/restore)
            context.deleteSharedPreferences(PREFS_NAME)
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    /**
     * Get all stored API keys.
     */
    fun getApiKeys(): List<String> {
        val json = prefs.getString(KEY_API_KEYS, null) ?: return emptyList()
        return try {
            Json.decodeFromString<List<String>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add a new API key.
     * @return true if added successfully, false if key already exists or is invalid
     */
    fun addApiKey(apiKey: String): Boolean {
        val trimmedKey = apiKey.trim()
        
        // Validate key format (Gemini keys start with "AIza" and are ~39 chars)
        if (trimmedKey.length < 30 || !trimmedKey.startsWith("AIza")) {
            return false
        }
        
        val currentKeys = getApiKeys().toMutableList()
        
        // Check for duplicates
        if (currentKeys.contains(trimmedKey)) {
            return false
        }
        
        currentKeys.add(trimmedKey)
        saveKeys(currentKeys)
        return true
    }

    /**
     * Remove an API key by index.
     */
    fun removeApiKey(index: Int): Boolean {
        val currentKeys = getApiKeys().toMutableList()
        if (index < 0 || index >= currentKeys.size) {
            return false
        }
        currentKeys.removeAt(index)
        saveKeys(currentKeys)
        return true
    }

    /**
     * Remove an API key by value.
     */
    fun removeApiKey(apiKey: String): Boolean {
        val currentKeys = getApiKeys().toMutableList()
        val removed = currentKeys.remove(apiKey.trim())
        if (removed) {
            saveKeys(currentKeys)
        }
        return removed
    }

    /**
     * Check if there are any API keys stored.
     */
    fun hasApiKeys(): Boolean = getApiKeys().isNotEmpty()

    /**
     * Get the count of stored API keys.
     */
    fun getApiKeyCount(): Int = getApiKeys().size

    /**
     * Clear all API keys.
     */
    fun clearAllKeys() {
        prefs.edit().remove(KEY_API_KEYS).apply()
    }

    private fun saveKeys(keys: List<String>) {
        val json = Json.encodeToString(keys)
        prefs.edit().putString(KEY_API_KEYS, json).apply()
    }

    /**
     * Mask an API key for display (show first 8 and last 4 characters).
     */
    fun maskApiKey(apiKey: String): String {
        if (apiKey.length <= 12) return "****"
        return "${apiKey.take(8)}...${apiKey.takeLast(4)}"
    }
}
