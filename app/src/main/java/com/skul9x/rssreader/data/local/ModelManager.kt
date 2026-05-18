package com.skul9x.rssreader.data.local

import android.content.Context
import android.content.SharedPreferences
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ModelManager manages dynamically added Gemini models stored in SharedPreferences.
 * It implements self-healing behavior to fallback to defaults if the list becomes empty
 * or corrupted, and ensures thread safety using Coroutine Mutex.
 */
class ModelManager private constructor(context: Context) {
    private val sharedPrefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val mutex = Mutex()
    private var cachedModels: List<DynamicModel>? = null

    companion object {
        private const val PREFS_NAME = "dynamic_models"
        private const val KEY_SAVED_MODELS = "saved_models"

        val DEFAULT_MODELS = listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
            DynamicModel("models/gemini-3-flash-preview", "Gemini 3 Flash Preview"),
            DynamicModel("models/gemini-2.5-flash", "Gemini 2.5 Flash")
        )

        @Volatile
        private var instance: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return instance ?: synchronized(this) {
                instance ?: ModelManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Retrieves the current list of dynamic models.
     * Triggers self-healing if empty, missing, or corrupted.
     */
    suspend fun getModels(): List<DynamicModel> = mutex.withLock {
        cachedModels?.let { return it }

        val jsonStr = sharedPrefs.getString(KEY_SAVED_MODELS, null)
        if (jsonStr.isNullOrBlank()) {
            return performSelfHealing()
        }

        return try {
            val decoded = Json.decodeFromString<List<DynamicModel>>(jsonStr)
            if (decoded.isEmpty()) {
                performSelfHealing()
            } else {
                cachedModels = decoded
                decoded
            }
        } catch (e: Exception) {
            performSelfHealing()
        }
    }

    private fun performSelfHealing(): List<DynamicModel> {
        val defaults = DEFAULT_MODELS
        cachedModels = defaults
        saveToStorage(defaults)
        return defaults
    }

    private fun saveToStorage(models: List<DynamicModel>) {
        try {
            val jsonStr = Json.encodeToString(models)
            sharedPrefs.edit().putString(KEY_SAVED_MODELS, jsonStr).apply()
        } catch (e: Exception) {
            // Error-tolerance: avoid crashing the application
        }
    }

    /**
     * Adds a new dynamic model.
     * Validates that modelId starts with "models/", has length > 10, and is unique.
     * Returns true if successful, false otherwise.
     */
    suspend fun addModel(modelId: String, displayName: String): Boolean = mutex.withLock {
        if (!modelId.startsWith("models/") || modelId.length <= 10 || displayName.isBlank()) {
            return false
        }

        val currentModels = getModelsInternal()
        if (currentModels.any { it.modelId == modelId }) {
            return false
        }

        val updated = currentModels + DynamicModel(modelId, displayName)
        cachedModels = updated
        saveToStorage(updated)
        return true
    }

    /**
     * Removes a dynamic model by id.
     * Triggers self-healing if the list is empty after deletion.
     * Returns true if the model was found and deleted, false otherwise.
     */
    suspend fun removeModel(modelId: String): Boolean = mutex.withLock {
        val currentModels = getModelsInternal()
        if (currentModels.none { it.modelId == modelId }) {
            return false
        }

        val updated = currentModels.filter { it.modelId != modelId }
        if (updated.isEmpty()) {
            performSelfHealing()
        } else {
            cachedModels = updated
            saveToStorage(updated)
        }
        return true
    }

    /**
     * Updates an existing dynamic model.
     * Validates format and checks uniqueness of new modelId.
     * Returns true if successful, false otherwise.
     */
    suspend fun updateModel(oldModelId: String, newModelId: String, newDisplayName: String): Boolean = mutex.withLock {
        if (!newModelId.startsWith("models/") || newModelId.length <= 10 || newDisplayName.isBlank()) {
            return false
        }

        val currentModels = getModelsInternal()
        if (currentModels.none { it.modelId == oldModelId }) {
            return false
        }

        if (oldModelId != newModelId && currentModels.any { it.modelId == newModelId }) {
            return false
        }

        val updated = currentModels.map {
            if (it.modelId == oldModelId) {
                DynamicModel(newModelId, newDisplayName)
            } else {
                it
            }
        }
        cachedModels = updated
        saveToStorage(updated)
        return true
    }

    /**
     * Forces standard reset back to default models.
     */
    suspend fun resetToDefaults(): Unit = mutex.withLock {
        performSelfHealing()
    }

    private fun getModelsInternal(): List<DynamicModel> {
        val cached = cachedModels
        if (cached != null) return cached

        val jsonStr = sharedPrefs.getString(KEY_SAVED_MODELS, null)
        if (jsonStr.isNullOrBlank()) {
            val defaults = DEFAULT_MODELS
            cachedModels = defaults
            saveToStorage(defaults)
            return defaults
        }

        return try {
            val decoded = Json.decodeFromString<List<DynamicModel>>(jsonStr)
            if (decoded.isEmpty()) {
                val defaults = DEFAULT_MODELS
                cachedModels = defaults
                saveToStorage(defaults)
                defaults
            } else {
                cachedModels = decoded
                decoded
            }
        } catch (e: Exception) {
            val defaults = DEFAULT_MODELS
            cachedModels = defaults
            saveToStorage(defaults)
            defaults
        }
    }
}
