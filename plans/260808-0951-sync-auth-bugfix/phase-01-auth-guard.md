# Phase 01: Auth Guard in BatchQueueManager & FirestoreSyncRepository
Status: ✅ Completed
Dependencies: None

## Objective
Prevent `BatchQueueManager.doFlush()` and `AppLifecycleObserver.onStop()` from attempting Firestore uploads when the user is not signed in. This eliminates unnecessary `IllegalStateException` throws and error log pollution in the `firebase_logs` table.

## Root Cause
- `SyncCoordinator.performFullSync()` correctly checks `firestoreRepo.isUserSignedIn()` before any work.
- `BatchQueueManager.doFlush()` calls `firestoreRepo.uploadBatch()` directly — no auth check.
- `FirestoreSyncRepository.getUserCollection()` throws `IllegalStateException("Not signed in")` when `auth.currentUser == null`.
- The exception is caught in `uploadBatch()` which logs an error to Room DB (`logRepo?.logError(...)`) and re-throws.
- `doFlush()` catches the re-thrown exception and logs to Logcat.
- **Result:** Every 10 reads + every app-background event generates an error log entry for unsigned-in users.

## Requirements

### Functional
- [x] `BatchQueueManager.doFlush()` must skip Firestore upload when user is not signed in
- [x] Items must remain PENDING in local DB (not lost) so they can sync later when user signs in
- [x] `doFlush()` must log an INFO-level message (not ERROR) when skipping due to no auth
- [x] No behavior change for signed-in users

### Non-Functional
- [x] No new dependencies added
- [x] Minimal code change surface area

## Implementation Steps

### Step 1: Add auth check in `BatchQueueManager.doFlush()`

**File:** `app/src/main/java/com/skul9x/rssreader/data/sync/BatchQueueManager.kt`

**Current code (lines 90-105):**
```kotlin
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
```

**Change to:**
```kotlin
private suspend fun doFlush(items: List<ReadNewsItem>) {
    if (items.isEmpty()) return
    
    // Guard: skip upload if user is not signed in
    if (!firestoreRepo.isUserSignedIn()) {
        Log.d(TAG, "Skipping flush: user not signed in (${items.size} items remain PENDING)")
        return
    }
    
    Log.d(TAG, "Flushing ${items.size} items")
    
    try {
        firestoreRepo.uploadBatch(items)
        localRepo.markAsSynced(items.map { it.newsId })
        Log.d(TAG, "Synced ${items.size} items successfully")
    } catch (e: Exception) {
        Log.e(TAG, "Batch sync failed", e)
    }
}
```

**Key design decision:** When `doFlush()` skips due to no auth, the items are NOT re-added to the in-memory queue (they were already cleared from the queue before `doFlush()` was called). However, they remain PENDING in the Room database. When the user eventually signs in and `SyncWorker` fires (or `recoverPendingItems()` runs), `SyncCoordinator.performFullSync()` will pick them up via `localRepo.getPendingItems()` and upload them. No data is lost.

### Step 2: Verify no other unguarded Firestore access paths exist

Review all call sites of `firestoreRepo.uploadBatch()`, `firestoreRepo.downloadSince()`, and `firestoreRepo.deleteOldItems()`:

| Call site | Auth guarded? | Action needed |
|-----------|--------------|---------------|
| `SyncCoordinator.performFullSync()` | ✅ Yes (line 48) | None |
| `BatchQueueManager.doFlush()` | ❌ No | **Fix in Step 1** |
| No other direct callers | — | — |

All three Firestore methods (`uploadBatch`, `downloadSince`, `deleteOldItems`) are only called from `SyncCoordinator` (guarded) and `BatchQueueManager.doFlush()` (fixing now). No other fixes needed.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/sync/BatchQueueManager.kt` — Add auth guard in `doFlush()`

## Test Criteria

### Test File
`app/src/test/java/com/skul9x/rssreader/data/sync/BatchQueueManagerAuthGuardTest.kt`

### Test Cases

```kotlin
package com.skul9x.rssreader.data.sync

import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchQueueManagerAuthGuardTest {

    private val localRepo = mockk<LocalSyncRepository>(relaxed = true)
    private val firestoreRepo = mockk<FirestoreSyncRepository>(relaxed = true)
    private val syncCoordinator = mockk<SyncCoordinator>(relaxed = true)

    // Use reflection or make doFlush testable to test the private method,
    // OR test through public API (addToQueue triggering flush at threshold).

    @Before
    fun setup() {
        // Reset BatchQueueManager singleton for each test
        BatchQueueManager.resetInstance()
    }

    /**
     * Test 1: When user is NOT signed in, doFlush should NOT call uploadBatch.
     * Trigger: Add items to reach BATCH_THRESHOLD (10) → auto-flush.
     */
    @Test
    fun `flush should skip uploadBatch when user is not signed in`() = runTest {
        // Arrange
        every { firestoreRepo.isUserSignedIn() } returns false

        val bqm = BatchQueueManager.getInstance(
            localRepo, firestoreRepo, syncCoordinator,
            this  // CoroutineScope from runTest
        )

        // Create fake items that localRepo.getById returns
        repeat(10) { i ->
            val item = ReadNewsItem("id-$i", 1000L + i, "smartphone", SyncStatus.PENDING)
            coEvery { localRepo.getById("id-$i") } returns item
        }

        // Act: Add 10 items to trigger auto-flush
        repeat(10) { i ->
            bqm.addToQueue("id-$i")
        }

        // Assert: uploadBatch should NEVER be called
        coVerify(exactly = 0) { firestoreRepo.uploadBatch(any()) }

        // Assert: markAsSynced should NEVER be called (items remain PENDING)
        coVerify(exactly = 0) { localRepo.markAsSynced(any()) }
    }

    /**
     * Test 2: When user IS signed in, doFlush should call uploadBatch normally.
     */
    @Test
    fun `flush should call uploadBatch when user is signed in`() = runTest {
        // Arrange
        every { firestoreRepo.isUserSignedIn() } returns true

        val bqm = BatchQueueManager.getInstance(
            localRepo, firestoreRepo, syncCoordinator,
            this
        )

        repeat(10) { i ->
            val item = ReadNewsItem("id-$i", 1000L + i, "smartphone", SyncStatus.PENDING)
            coEvery { localRepo.getById("id-$i") } returns item
        }

        // Act
        repeat(10) { i ->
            bqm.addToQueue("id-$i")
        }

        // Assert: uploadBatch SHOULD be called with 10 items
        coVerify(exactly = 1) { firestoreRepo.uploadBatch(match { it.size == 10 }) }

        // Assert: markAsSynced SHOULD be called
        coVerify(exactly = 1) { localRepo.markAsSynced(any()) }
    }

    /**
     * Test 3: forceFlush (called by AppLifecycleObserver.onStop) should also
     * respect auth guard and not call uploadBatch when not signed in.
     */
    @Test
    fun `forceFlush should skip upload when user is not signed in`() = runTest {
        // Arrange
        every { firestoreRepo.isUserSignedIn() } returns false

        val bqm = BatchQueueManager.getInstance(
            localRepo, firestoreRepo, syncCoordinator,
            this
        )

        // Add a few items (below threshold, so no auto-flush)
        repeat(3) { i ->
            val item = ReadNewsItem("id-$i", 1000L + i, "smartphone", SyncStatus.PENDING)
            coEvery { localRepo.getById("id-$i") } returns item
            bqm.addToQueue("id-$i")
        }

        // Act: Force flush (simulates app going to background)
        bqm.forceFlush()

        // Assert: uploadBatch should NOT be called
        coVerify(exactly = 0) { firestoreRepo.uploadBatch(any()) }
    }
}
```

### How to run tests
```bash
./gradlew test --tests "com.skul9x.rssreader.data.sync.BatchQueueManagerAuthGuardTest"
```

### Expected results
- All 3 tests pass
- No `IllegalStateException` thrown during test execution
- Unsigned-in users: items stay PENDING in Room, no error logs generated

## Notes
- The in-memory queue items ARE cleared before `doFlush()` is called (see `addToQueue()` lines 68-79). When `doFlush()` skips, those items are "lost" from the in-memory queue but NOT from the Room database (they were persisted with PENDING status by `markAsRead()` before `addToQueue()` was called). `SyncWorker` will pick them up on the next periodic run after the user signs in.
- This is acceptable because the alternative (re-adding to queue) would create an infinite retry loop while the user remains unsigned.

---
Next Phase: [phase-02-sync-reinit.md](./phase-02-sync-reinit.md)
