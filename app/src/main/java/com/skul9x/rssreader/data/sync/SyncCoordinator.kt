package com.skul9x.rssreader.data.sync

import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import kotlinx.coroutines.delay

import androidx.annotation.VisibleForTesting

/**
 * Coordinator for synchronizing local and remote read status.
 * Handles:
 * 1. Uploading pending local changes
 * 2. Downloading remote changes
 * 3. Resolving conflicts
 * 4. Cleaning up old data
 */
class SyncCoordinator @VisibleForTesting internal constructor(
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
     * Uses incremental sync for downloads and smart cleanup.
     */
    suspend fun performFullSync() {
        if (!firestoreRepo.isUserSignedIn()) {
            logRepo?.logInfo("Sync skipped: User not signed in")
            return
        }
        
        logRepo?.logInfo("Sync started")
        // 1. Upload pending local items
        val pendingItems = localRepo.getPendingItems()
        if (pendingItems.isNotEmpty()) {
            firestoreRepo.uploadBatch(pendingItems)
            // Mark as SYNCED immediately after successful upload
            // to prevent duplicate uploads if later steps fail
            localRepo.markAsSynced(pendingItems.map { it.newsId })
        }

        // 2. Download remote items (Incremental Sync)
        val lastDownload = localRepo.getLastDownloadTimestamp()
        val (remoteItems, maxServerTimestamp) = firestoreRepo.downloadSince(lastDownload)

        // 3. Merge + update timestamp atomically (M2 fix)
        try {
            // 3. Merge with conflict resolution
            mergeWithLocal(remoteItems)

            // 4. Update last download timestamp
            localRepo.updateLastDownloadTimestamp(maxServerTimestamp)
        } catch (e: Exception) {
            logRepo?.logError("Merge failed, download timestamp NOT updated for safety", e)
            throw e
        }

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
            val retentionDays = com.skul9x.rssreader.utils.AppConfig.READ_HISTORY_RETENTION_DAYS.toLong()
            val cleanupThreshold = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000)
            
            logRepo?.logInfo("Starting daily cleanup")
            // Clean local DB
            localRepo.cleanupOldItems()
            
            // Clean Firestore (expensive operation, do sparingly)
            try {
                firestoreRepo.deleteOldItems(cleanupThreshold)
            } catch (e: Exception) {
                // Firestore cleanup failure should not block local cleanup success
                logRepo?.logError("Firestore cleanup failed (non-blocking)", e)
            }
            
            // Update last cleanup timestamp
            localRepo.updateLastCleanupDate(System.currentTimeMillis())
        }
    }

    /**
     * Performs sync with retry logic for resilience.
     */
    suspend fun performFullSyncWithRetry(maxRetries: Int = 1) {
        // NOTE: Internal retry removed to let WorkManager handle retries reliably.
        // maxRetries param is kept for compatibility but ignored.
        performFullSync()
    }

    /**
     * Merges remote items into local database with conflict resolution.
     * Strategy:
     * - New items: Insert as SYNCED
     * - Existing items:
     *   - Earliest readTimestamp wins (assume read earlier = accurate first read)
     *   - If timestamps equal, 'smartphone' device type wins (arbitrary tie-breaker)
     */
    @VisibleForTesting
    internal suspend fun mergeWithLocal(remoteItems: List<ReadNewsItem>) {
        if (remoteItems.isEmpty()) return

        // Batch fetch all corresponding local items to avoid N+1 queries
        val remoteIds = remoteItems.map { it.newsId }
        val localItems = localRepo.getByIds(remoteIds)
        val localItemsMap = localItems.associateBy { it.newsId }

        val itemsToUpdate = mutableListOf<ReadNewsItem>()

        remoteItems.forEach { remote ->
            val local = localItemsMap[remote.newsId]

            if (local == null) {
                // New item from remote -> Insert
                itemsToUpdate.add(remote.copy(syncStatus = SyncStatus.SYNCED))
            } else {
                // Skip items that are PENDING upload — local changes take priority
                if (local.syncStatus == SyncStatus.PENDING) return@forEach

                // Conflict resolution
                val shouldUpdate = when {
                    // Remote was read EARLIER than local -> earliest read is source of truth
                    remote.readAt < local.readAt -> true
                    
                    // Same timestamp conflict
                    remote.readAt == local.readAt -> {
                        // Prioritize smartphone as source of truth if timestamps match
                        remote.deviceType == "smartphone" && local.deviceType != "smartphone"
                    }
                    
                    // Local is earlier or equal, keep local
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
