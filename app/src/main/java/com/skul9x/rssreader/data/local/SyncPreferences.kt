package com.skul9x.rssreader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Extension property for DataStore
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

/**
 * DataStore-based preferences for sync metadata.
 * Stores last sync time and other sync-related settings.
 */
class SyncPreferences private constructor(private val context: Context) {

    private object PreferenceKeys {
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val LAST_DOWNLOAD_TIMESTAMP = longPreferencesKey("last_download_timestamp")
        val LAST_CLEANUP_DATE = longPreferencesKey("last_cleanup_date")
        val PENDING_SYNC_COUNT = intPreferencesKey("pending_sync_count")
        val DEVICE_TYPE = stringPreferencesKey("device_type")
    }

    /**
     * Get the last sync timestamp as Flow.
     */
    val lastSyncTime: Flow<Long> = context.syncDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferenceKeys.LAST_SYNC_TIME] ?: 0L
        }

    /**
     * Update the last sync timestamp.
     */
    suspend fun updateLastSyncTime(timestamp: Long) {
        context.syncDataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_SYNC_TIME] = timestamp
        }
    }

    /**
     * Get the last download timestamp as Flow.
     * Used for incremental sync.
     */
    val lastDownloadTimestamp: Flow<Long> = context.syncDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferenceKeys.LAST_DOWNLOAD_TIMESTAMP] ?: 0L
        }

    /**
     * Update the last download timestamp.
     */
    suspend fun updateLastDownloadTimestamp(timestamp: Long) {
        context.syncDataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_DOWNLOAD_TIMESTAMP] = timestamp
        }
    }

    /**
     * Get the last cleanup date as Flow.
     * Used for limiting cleanup frequency.
     */
    val lastCleanupDate: Flow<Long> = context.syncDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferenceKeys.LAST_CLEANUP_DATE] ?: 0L
        }

    /**
     * Update the last cleanup date.
     */
    suspend fun updateLastCleanupDate(timestamp: Long) {
        context.syncDataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_CLEANUP_DATE] = timestamp
        }
    }

    /**
     * Get the pending sync count as Flow.
     */
    val pendingSyncCount: Flow<Int> = context.syncDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferenceKeys.PENDING_SYNC_COUNT] ?: 0
        }

    /**
     * Update the pending sync count.
     */
    suspend fun updatePendingCount(count: Int) {
        context.syncDataStore.edit { preferences ->
            preferences[PreferenceKeys.PENDING_SYNC_COUNT] = count
        }
    }

    /**
     * Get stored device type (smartphone/androidbox).
     */
    val deviceType: Flow<String> = context.syncDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferenceKeys.DEVICE_TYPE] ?: "smartphone"
        }

    /**
     * Set device type.
     */
    suspend fun setDeviceType(type: String) {
        context.syncDataStore.edit { preferences ->
            preferences[PreferenceKeys.DEVICE_TYPE] = type
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncPreferences? = null

        fun getInstance(context: Context): SyncPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
