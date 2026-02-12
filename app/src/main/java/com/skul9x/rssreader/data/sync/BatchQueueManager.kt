package com.skul9x.rssreader.data.sync

import android.util.Log
import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages batch queue for syncing read news status.
 * - Triggers sync when queue reaches 10 items.
 * - Thread-safe using Mutex and sequential list processing.
 * - Persists to local DB immediately for crash recovery.
 */
class BatchQueueManager private constructor(
    private val localRepo: LocalSyncRepository,
    private val firestoreRepo: FirestoreSyncRepository,
    private val syncCoordinator: SyncCoordinator,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val queue = mutableListOf<ReadNewsItem>()
    
    companion object {
        private const val TAG = "BatchQueueManager"
        private const val BATCH_THRESHOLD = 10
        private const val MAX_QUEUE_SIZE = 50 // Safety net to prevent memory issues
        
        @Volatile
        private var INSTANCE: BatchQueueManager? = null

        fun getInstance(
            localRepo: LocalSyncRepository,
            firestoreRepo: FirestoreSyncRepository,
            syncCoordinator: SyncCoordinator,
            scope: CoroutineScope
        ): BatchQueueManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BatchQueueManager(localRepo, firestoreRepo, syncCoordinator, scope).also { 
                    INSTANCE = it 
                }
            }
        }

        fun resetInstance() {
            INSTANCE = null
        }
    }

    /**
     * Adds a news item to the batch queue.
     * Persists to local DB immediately (crash recovery).
     * Triggers sync when batch threshold is reached.
     */
    suspend fun addToQueue(newsId: String) {
        // NOTE: Item is already marked as read in LocalSyncRepository before calling this
        
        // Get the item from local DB to add to in-memory queue
        val item = localRepo.getById(newsId) ?: return
        
        // Add to in-memory queue under lock and take snapshot if full (M3 fix)
        val itemsToFlush = mutex.withLock {
            queue.add(item)
            Log.d(TAG, "Added to queue: $newsId (size: ${queue.size})")
            
            if (queue.size >= BATCH_THRESHOLD || queue.size >= MAX_QUEUE_SIZE) {
                val snapshot = queue.toList()
                queue.clear()
                snapshot
            } else {
                emptyList()
            }
        }
        
        if (itemsToFlush.isNotEmpty()) {
            doFlush(itemsToFlush)
        }
    }

    /**
     * Flushes the queue and syncs only the queued items to Firestore.
     * Uses efficient batch upload instead of full sync.
     */
    private suspend fun doFlush(items: List<ReadNewsItem>) {
        if (items.isEmpty()) return
        
        Log.d(TAG, "Flushing ${items.size} items")
        
        try {
            // Upload only these specific items (not full sync)
            firestoreRepo.uploadBatch(items)
            localRepo.markAsSynced(items.map { it.newsId })
            Log.d(TAG, "Synced ${items.size} items successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Batch sync failed", e)
            // Items already in local DB with PENDING status
            // Will be retried by WorkManager
        }
    }

    private suspend fun flushQueue() {
        val items = mutex.withLock {
            if (queue.isEmpty()) return@withLock emptyList<ReadNewsItem>()
            val snapshot = queue.toList()
            queue.clear()
            snapshot
        }
        doFlush(items)
    }

    /**
     * Forces an immediate flush of the queue.
     * Called by timer/background triggers.
     */
    suspend fun forceFlush() {
        flushQueue()
    }

    /**
     * Recovers and syncs any pending items from local DB.
     * Call on app startup to handle crash recovery.
     */
    suspend fun recoverPendingItems() {
        val pendingCount = localRepo.getPendingCount()
        if (pendingCount > 0) {
            Log.d(TAG, "Recovering $pendingCount pending items")
            try {
                syncCoordinator.performFullSync()
                Log.d(TAG, "Recovery sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Recovery sync failed", e)
                // Will be retried by WorkManager
            }
        }
    }

    /**
     * Gets the current queue size (for debugging/UI).
     */
    suspend fun getQueueSize(): Int = mutex.withLock { queue.size }

    /**
     * Clears the in-memory queue.
     */
    suspend fun clearQueue() = mutex.withLock { queue.clear() }
}
