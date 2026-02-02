# Phase 05: Batch Queue Manager
Status: ✅ Complete  
Dependencies: Phase 04

## Objective
Implement in-memory batch queue that triggers sync when reaching 10 items, with thread-safe counter and flush mechanism.

## Requirements
### Functional
- [ ] Maintain in-memory queue for pending read items
- [ ] Trigger sync when queue reaches 10 items
- [ ] Flush queue to database and Firestore on sync
- [ ] Thread-safe operations (multiple threads may mark items as read)
- [ ] Persist queue on app crash (via database)

### Non-Functional
- [ ] Performance: Queue operations should be O(1)
- [ ] Thread-safety: Use synchronized or concurrent collections
- [ ] Memory: Queue should not exceed 50 items (auto-flush)

## Implementation Steps

### 1. Create `BatchQueueManager.kt`
```kotlin
class BatchQueueManager(
    private val localRepo: LocalSyncRepository,
    private val syncCoordinator: SyncCoordinator,
    private val scope: CoroutineScope
) {
    private val queue = ConcurrentLinkedQueue<ReadNewsEntity>()
    private val queueSize = AtomicInteger(0)
    private val batchThreshold = 10
    
    suspend fun addToQueue(newsId: String, deviceType: String)
    private suspend fun flushQueue()
    suspend fun forceFlush() // For timer/background triggers
}
```

### 2. Implement `addToQueue()`
```kotlin
suspend fun addToQueue(newsId: String, deviceType: String) {
    val item = ReadNewsEntity(
        newsItemId = newsId,
        readTimestamp = System.currentTimeMillis(),
        deviceType = deviceType,
        syncStatus = SyncStatus.PENDING
    )
    
    // Add to in-memory queue
    queue.add(item)
    val currentSize = queueSize.incrementAndGet()
    
    // Persist to local DB immediately (for crash recovery)
    localRepo.markAsRead(newsId, deviceType)
    
    // Check if batch threshold reached
    if (currentSize >= batchThreshold) {
        flushQueue()
    }
}
```

### 3. Implement `flushQueue()`
```kotlin
private suspend fun flushQueue() = withContext(Dispatchers.IO) {
    val items = mutableListOf<ReadNewsEntity>()
    
    // Drain queue
    while (queue.isNotEmpty()) {
        queue.poll()?.let { items.add(it) }
    }
    queueSize.set(0)
    
    if (items.isEmpty()) return@withContext
    
    try {
        // Sync to Firestore
        syncCoordinator.performFullSyncWithRetry()
        
        Log.d("BatchQueue", "Synced ${items.size} items")
    } catch (e: Exception) {
        Log.e("BatchQueue", "Sync failed", e)
        // Items already in local DB with PENDING status
        // Will be retried by WorkManager
    }
}
```

### 4. Implement `forceFlush()`
```kotlin
suspend fun forceFlush() {
    if (queueSize.get() > 0) {
        flushQueue()
    }
}
```

### 5. Handle Crash Recovery
```kotlin
// Call on app startup (in MainActivity or Application class)
suspend fun recoverPendingItems() {
    val pending = localRepo.getPendingItems()
    if (pending.isNotEmpty()) {
        try {
            syncCoordinator.performFullSyncWithRetry()
        } catch (e: Exception) {
            Log.e("BatchQueue", "Recovery sync failed", e)
            // Will be retried by WorkManager
        }
    }
}
```

### 6. Thread-Safety Test
```kotlin
// Unit test
@Test
fun testConcurrentAdds() = runBlocking {
    val manager = BatchQueueManager(...)
    
    // Simulate 100 concurrent reads
    val jobs = (1..100).map { index ->
        launch {
            manager.addToQueue("news_$index", "smartphone")
        }
    }
    jobs.joinAll()
    
    // Verify all items synced
    verify(syncCoordinator, times(10)).performFullSyncWithRetry() // 100/10 = 10 batches
}
```

### 7. Integrate with MainViewModel
```kotlin
// In MainViewModel or NewsRepository
suspend fun markNewsAsRead(newsId: String) {
    val deviceType = getDeviceType(context)
    batchQueueManager.addToQueue(newsId, deviceType)
}
```

## Files to Create/Modify
- `app/src/main/java/[package]/data/sync/BatchQueueManager.kt` - NEW
- `app/src/main/java/[package]/ui/MainViewModel.kt` - MODIFY (integrate queue)
- `app/src/main/java/[package]/NewsReaderApplication.kt` - MODIFY (add crash recovery)

## Test Criteria
- [ ] Add 9 items → No sync triggered
- [ ] Add 10th item → Sync triggered automatically
- [ ] Add 100 items concurrently → All items synced, no duplicates
- [ ] Crash after adding 5 items → On restart, 5 items synced
- [ ] Force flush with 3 items → All 3 synced immediately

## Notes
- **Why ConcurrentLinkedQueue?**
  - Thread-safe without explicit synchronization
  - Non-blocking O(1) operations
  - Better performance than synchronized ArrayList

- **Why persist immediately to local DB?**
  - Crash recovery: Queue is in-memory only
  - If app crashes before sync, items won't be lost
  - Local DB acts as persistent queue

- **Memory Safety:**
  ```kotlin
  // Auto-flush if queue exceeds 50 items (safety net)
  if (currentSize >= 50) {
      scope.launch { flushQueue() }
  }
  ```

---
Next Phase: [phase-06-timer-background.md](file:///C:/Users/Admin/Desktop/Test_code/RSS-Reader-main/plans/260120-2238-read-status-sync/phase-06-timer-background.md)
