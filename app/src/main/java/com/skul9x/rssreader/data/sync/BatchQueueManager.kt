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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages batch queue for syncing read news status.
 * - Triggers sync when queue reaches 10 items.
 * - Thread-safe using ConcurrentLinkedQueue.
 * - Persists to local DB immediately for crash recovery.
 */
class BatchQueueManager private constructor(
    private val localRepo: LocalSyncRepository,
    private val firestoreRepo: FirestoreSyncRepository,
    private val syncCoordinator: SyncCoordinator,
    private val scope: CoroutineScope
) {
    private val queue = ConcurrentLinkedQueue<ReadNewsItem>()
    private val queueSize = AtomicInteger(0)
    
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
        
        // Add to in-memory queue
        queue.add(item)
        val currentSize = queueSize.incrementAndGet()
        
        Log.d(TAG, "Added to queue: $newsId (size: $currentSize)")
        
        // Check if batch threshold reached
        if (currentSize >= BATCH_THRESHOLD) {
            flushQueue()
        }
        
        // Safety net: Force flush if queue exceeds max size
        if (currentSize >= MAX_QUEUE_SIZE) {
            Log.w(TAG, "Queue exceeded max size, forcing flush")
            scope.launch { flushQueue() }
        }
    }

    /**
     * Flushes the queue and syncs only the queued items to Firestore.
     * Uses efficient batch upload instead of full sync.
     */
    private suspend fun flushQueue() = withContext(Dispatchers.IO) {
        val items = mutableListOf<ReadNewsItem>()
        
        // Drain queue atomically
        var item = queue.poll()
        while (item != null) {
            items.add(item)
            queueSize.decrementAndGet() // Atomic decrement for thread safety
            item = queue.poll()
        }
        
        if (items.isEmpty()) return@withContext
        
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

    /**
     * Forces an immediate flush of the queue.
     * Called by timer/background triggers.
     */
    suspend fun forceFlush() {
        if (queueSize.get() > 0) {
            Log.d(TAG, "Force flushing ${queueSize.get()} items")
            flushQueue()
        }
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
                syncCoordinator.performFullSyncWithRetry()
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
    fun getQueueSize(): Int = queueSize.get()
}
