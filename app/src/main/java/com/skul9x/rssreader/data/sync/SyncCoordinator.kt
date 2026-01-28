package com.skul9x.rssreader.data.sync

import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import kotlinx.coroutines.delay

/**
 * Coordinator for synchronizing local and remote read status.
 * Handles:
 * 1. Uploading pending local changes
 * 2. Downloading remote changes
 * 3. Resolving conflicts
 * 4. Cleaning up old data
 */
class SyncCoordinator private constructor(
    private val localRepo: LocalSyncRepository,
    private val firestoreRepo: FirestoreSyncRepository,
    private val logRepo: com.skul9x.rssreader.data.repository.FirebaseLogRepository?
) {

    companion object {
        @Volatile
        private var INSTANCE: SyncCoordinator? = null

        fun getInstance(
            localRepo: LocalSyncRepository,
            firestoreRepo: FirestoreSyncRepository,
            logRepo: com.skul9x.rssreader.data.repository.FirebaseLogRepository? = null
        ): SyncCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncCoordinator(localRepo, firestoreRepo, logRepo).also { INSTANCE = it }
            }
        }
    }

    /**
     * Performs a full bi-directional sync.
     */
    /**
     * Performs a full bi-directional sync.
     * Uses incremental sync for downloads and smart cleanup.
     */
    suspend fun performFullSync() {
        logRepo?.logInfo("Sync started")
        // 1. Upload pending local items
        val pendingItems = localRepo.getPendingItems()
        if (pendingItems.isNotEmpty()) {
            firestoreRepo.uploadBatch(pendingItems)
            localRepo.markAsSynced(pendingItems.map { it.newsId })
        }

        // 2. Download remote items (Incremental Sync)
        val lastDownload = localRepo.getLastDownloadTimestamp()
        val (remoteItems, maxServerTimestamp) = firestoreRepo.downloadSince(lastDownload)

        // 3. Merge with conflict resolution
        mergeWithLocal(remoteItems)
        
        // 4. Update last download timestamp
        localRepo.updateLastDownloadTimestamp(maxServerTimestamp)

        // 5. Smart Cleanup (Only run once every 24 hours)
        maybePerformCleanup()
        
        // 6. Update last sync time (legacy field)
        localRepo.updateLastSyncTime()
        logRepo?.logInfo("Sync completed successfully")
    }
    
    /**
     * Performs cleanup only if 24 hours have passed since last cleanup.
     */
    private suspend fun maybePerformCleanup() {
        val lastCleanup = localRepo.getLastCleanupDate()
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        
        if (lastCleanup < oneDayAgo) {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            
            logRepo?.logInfo("Starting daily cleanup")
            // Clean local DB
            localRepo.cleanupOldItems()
            
            // Clean Firestore (expensive operation, do sparingly)
            firestoreRepo.deleteOldItems(thirtyDaysAgo)
            
            // Update last cleanup timestamp
            localRepo.updateLastCleanupDate(System.currentTimeMillis())
        }
    }

    /**
     * Performs sync with retry logic for resilience.
     */
    suspend fun performFullSyncWithRetry(maxRetries: Int = 3) {
        repeat(maxRetries) { attempt ->
            try {
                performFullSync()
                return // Success
            } catch (e: Exception) {
                logRepo?.logError("Sync attempt ${attempt + 1} failed", e)
                if (attempt == maxRetries - 1) throw e
                // Exponential backoff: 1s, 2s, 3s...
                delay(1000L * (attempt + 1))
            }
        }
    }

    /**
     * Merges remote items into local database with conflict resolution.
     * Strategy:
     * - New items: Insert as SYNCED
     * - Existing items:
     *   - Earliest readTimestamp wins (assume read earlier = accurate first read)
     *   - If timestamps equal, 'smartphone' device type wins (arbitrary tie-breaker)
     */
    private suspend fun mergeWithLocal(remoteItems: List<ReadNewsItem>) {
        val itemsToUpdate = mutableListOf<ReadNewsItem>()

        remoteItems.forEach { remote ->
            val local = localRepo.getById(remote.newsId)

            if (local == null) {
                // New item from remote → Insert
                itemsToUpdate.add(remote.copy(syncStatus = SyncStatus.SYNCED))
            } else {
                // Conflict resolution
                val shouldUpdate = when {
                    // Remote read LATTER than local (newer read is source of truth)
                    remote.readAt > local.readAt -> true
                    
                    // Same timestamp conflict
                    remote.readAt == local.readAt -> {
                        // Prioritize smartphone as source of truth if timestamps match
                        remote.deviceType == "smartphone" && local.deviceType != "smartphone"
                    }
                    
                    // Local is older (earlier) or equal/better, keep local
                    else -> false
                }

                if (shouldUpdate) {
                    itemsToUpdate.add(remote.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }

        if (itemsToUpdate.isNotEmpty()) {
            localRepo.insertFromRemote(itemsToUpdate)
        }
    }
}
