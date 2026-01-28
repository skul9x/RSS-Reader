package com.skul9x.rssreader.data.repository

import android.content.Context
import android.content.res.Configuration
import com.skul9x.rssreader.data.local.ReadNewsDao
import com.skul9x.rssreader.data.local.SyncPreferences
import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository for local sync operations.
 * Handles marking news as read and managing sync status.
 */
class LocalSyncRepository(
    private val dao: ReadNewsDao,
    private val prefs: SyncPreferences,
    private val context: Context
) {
    // BatchQueueManager is set after construction to avoid circular dependency
    private var batchQueueManager: com.skul9x.rssreader.data.sync.BatchQueueManager? = null
    
    fun setBatchQueueManager(manager: com.skul9x.rssreader.data.sync.BatchQueueManager) {
        this.batchQueueManager = manager
    }
    
    /**
     * Mark a news item as read with current device type.
     * Also adds to batch queue for Firebase sync if queue manager is available.
     */
    suspend fun markAsRead(newsId: String) {
        val deviceType = getDeviceType()
        val item = ReadNewsItem(
            newsId = newsId,
            readAt = System.currentTimeMillis(),
            deviceType = deviceType,
            syncStatus = SyncStatus.PENDING
        )
        dao.markAsRead(item)
        
        // Add to batch queue for Firebase sync (if available)
        batchQueueManager?.addToQueue(newsId)
    }

    /**
     * Check if a news item has been read.
     */
    suspend fun isNewsRead(newsId: String): Boolean {
        return dao.isRead(newsId)
    }

    /**
     * Get all items with PENDING sync status.
     */
    suspend fun getPendingItems(): List<ReadNewsItem> {
        return dao.getByStatus(SyncStatus.PENDING)
    }

    /**
     * Get pending items as observable Flow.
     */
    fun getPendingItemsFlow(): Flow<List<ReadNewsItem>> {
        return dao.getPendingSyncFlow()
    }

    /**
     * Get count of pending sync items.
     */
    suspend fun getPendingCount(): Int {
        return dao.getPendingCount()
    }

    /**
     * Mark items as synced after successful upload.
     */
    suspend fun markAsSynced(newsIds: List<String>) {
        if (newsIds.isNotEmpty()) {
            dao.updateSyncStatus(newsIds, SyncStatus.SYNCED)
        }
    }

    /**
     * Mark items as failed after sync failure.
     */
    suspend fun markAsFailed(newsIds: List<String>) {
        if (newsIds.isNotEmpty()) {
            dao.updateSyncStatus(newsIds, SyncStatus.FAILED)
        }
    }

    /**
     * Insert multiple items from remote sync.
     */
    suspend fun insertFromRemote(items: List<ReadNewsItem>) {
        // Mark as SYNCED since they came from remote
        val syncedItems = items.map { it.copy(syncStatus = SyncStatus.SYNCED) }
        dao.insertAll(syncedItems)
    }

    /**
     * Get a specific read item by ID.
     */
    suspend fun getById(newsId: String): ReadNewsItem? {
        return dao.getById(newsId)
    }

    /**
     * Cleanup items older than 30 days.
     */
    suspend fun cleanupOldItems() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.deleteOlderThan(thirtyDaysAgo)
    }

    /**
     * Update last sync time in preferences.
     */
    suspend fun updateLastSyncTime() {
        prefs.updateLastSyncTime(System.currentTimeMillis())
    }

    /**
     * Get last sync time.
     */
    suspend fun getLastSyncTime(): Long {
        return prefs.lastSyncTime.first()
    }

    /**
     * Get last download timestamp for incremental sync.
     */
    suspend fun getLastDownloadTimestamp(): Long {
        return prefs.lastDownloadTimestamp.first()
    }

    /**
     * Update last download timestamp.
     */
    suspend fun updateLastDownloadTimestamp(timestamp: Long) {
        prefs.updateLastDownloadTimestamp(timestamp)
    }

    /**
     * Get last cleanup date.
     */
    suspend fun getLastCleanupDate(): Long {
        return prefs.lastCleanupDate.first()
    }

    /**
     * Update last cleanup date.
     */
    suspend fun updateLastCleanupDate(timestamp: Long) {
        prefs.updateLastCleanupDate(timestamp)
    }

    /**
     * Get all read news IDs.
     */
    suspend fun getAllReadIds(): List<String> {
        return dao.getAllReadIds()
    }

    /**
     * Detect device type based on UI mode.
     * Returns "androidbox" for car/TV mode, "smartphone" otherwise.
     */
    private fun getDeviceType(): String {
        // Check for TV feature explicitly (more reliable for Android Boxes)
        if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) {
            return "androidbox"
        }

        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return when (uiMode) {
            Configuration.UI_MODE_TYPE_CAR -> "androidbox"
            Configuration.UI_MODE_TYPE_TELEVISION -> "androidbox"
            else -> "smartphone"
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LocalSyncRepository? = null

        fun getInstance(
            dao: ReadNewsDao,
            prefs: SyncPreferences,
            context: Context
        ): LocalSyncRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalSyncRepository(dao, prefs, context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
}
