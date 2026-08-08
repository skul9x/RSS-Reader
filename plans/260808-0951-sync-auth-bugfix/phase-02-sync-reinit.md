# Phase 02: Sync Re-initialization After Sign-In
Status: ✅ Completed
Dependencies: Phase 01 (auth guard must be in place first)

## Objective
After a user signs out and signs back in within the same app process, re-create all sync singletons so that `RssApplication.getLocalSyncRepository()` and `getBatchQueueManager()` return valid instances instead of null. Also trigger an immediate sync to pull down the new user's read history.

## Root Cause
- `RssApplication.initializeSyncComponents()` runs only once in `onCreate()`.
- `onUserSignOut()` nullifies: `_localSyncRepository`, `_batchQueueManager`, and all static `INSTANCE` fields.
- After sign-in, nothing re-creates these. `getLocalSyncRepository()` returns null permanently.
- New ViewModels created after sign-out get null and fall through to the direct DAO fallback (no sync queue).

## Trace of the broken path after sign-out → sign-in

```
1. App start → onCreate() → initializeSyncComponents() → all singletons created ✅
2. Sign-out  → onUserSignOut() → _localSyncRepository = null, _batchQueueManager = null
3. Sign-in   → AuthManager.handleSignInResult() → only updates FirebaseAuth state
4. User reads news → MainViewModel (re-created) → getLocalSyncRepository() → NULL ❌
5. Falls to: readNewsDao.markAsRead(...) → Room only, no batch queue, no sync
```

## Requirements

### Functional
- [x] After successful sign-in, all sync singletons must be re-initialized
- [x] `RssApplication.getLocalSyncRepository()` must return non-null after sign-in
- [x] `RssApplication.getBatchQueueManager()` must return non-null after sign-in
- [x] An immediate sync must be triggered after sign-in to download the new user's read history
- [x] `AppLifecycleObserver` must reference the NEW `BatchQueueManager` instance (not the stale one)
- [x] Pending items from the previous (anonymous) session should be picked up by the new sync

### Non-Functional
- [x] Thread-safe: `onUserSignIn()` may be called from IO dispatcher
- [x] No duplicate initialization if called multiple times rapidly
- [x] No new dependencies added

## Implementation Steps

### Step 1: Create `RssApplication.onUserSignIn()` method

**File:** `app/src/main/java/com/skul9x/rssreader/RssApplication.kt`

Add a new companion method that re-runs the initialization logic:

```kotlin
companion object {
    // ... existing code ...

    /**
     * Re-initializes sync components after a user signs in.
     * Must be called after FirebaseAuth state changes to signed-in.
     * Thread-safe: synchronized on the companion object.
     */
    fun onUserSignIn(context: Context) {
        synchronized(this) {
            // Skip if already initialized (e.g., first launch, no sign-out happened)
            if (_localSyncRepository != null && _batchQueueManager != null) {
                return
            }

            val appContext = context.applicationContext
            val database = AppDatabase.getDatabase(appContext)
            val prefs = SyncPreferences.getInstance(appContext)

            // 1. Re-create LocalSyncRepository
            val localRepo = LocalSyncRepository.getInstance(
                database.readNewsDao(), prefs, appContext
            )
            _localSyncRepository = localRepo

            // 2. Re-create FirebaseLogRepository
            val firebaseLogRepo = com.skul9x.rssreader.data.repository.FirebaseLogRepository
                .getInstance(database.firebaseLogDao())
            _firebaseLogRepository = firebaseLogRepo

            // 3. Re-create FirestoreSyncRepository
            val firestoreRepo = FirestoreSyncRepository.getInstance(firebaseLogRepo)

            // 4. Re-create SyncCoordinator
            val syncCoordinator = SyncCoordinator.getInstance(
                localRepo, firestoreRepo, firebaseLogRepo
            )

            // 5. Re-create BatchQueueManager
            val batchQueueManager = BatchQueueManager.getInstance(
                localRepo, firestoreRepo, syncCoordinator, applicationScope
            )
            _batchQueueManager = batchQueueManager

            // 6. Wire BatchQueueManager back to LocalSyncRepository
            localRepo.setBatchQueueManager(batchQueueManager)

            // 7. Re-register lifecycle observer with new BatchQueueManager
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                AppLifecycleObserver(batchQueueManager, applicationScope)
            )
        }

        // 8. Schedule periodic sync (idempotent - KEEP policy)
        SyncScheduler(context.applicationContext).schedulePeriodicSync()

        // 9. Trigger immediate sync to download new user's read history
        SyncScheduler(context.applicationContext).triggerImmediateSync()
    }
}
```

**Design decisions:**
- Uses `synchronized(this)` to prevent race conditions if called from multiple coroutines.
- Early return if singletons are already alive (covers the normal first-launch case).
- Re-registers `AppLifecycleObserver`. The old observer still references the dead `BatchQueueManager` but its `forceFlush()` on the empty queue is a no-op. The new observer correctly references the new `BatchQueueManager`.

### Step 2: Call `onUserSignIn()` from `AuthManager.handleSignInResult()`

**File:** `app/src/main/java/com/skul9x/rssreader/auth/AuthManager.kt`

**Current code (lines 51-81):**
```kotlin
suspend fun handleSignInResult(data: Intent?): Result<FirebaseUser> {
    return try {
        // ... existing sign-in logic ...
        val user = authResult.user

        if (user != null) {
            Log.d(TAG, "Sign-in successful: ${user.email}")
            Result.success(user)
        } else {
            Result.failure(Exception("Đăng nhập thất bại"))
        }
    } catch (e: ...) { ... }
}
```

**Change the success branch to:**
```kotlin
if (user != null) {
    Log.d(TAG, "Sign-in successful: ${user.email}")

    // Re-initialize sync components for the new user session
    com.skul9x.rssreader.RssApplication.onUserSignIn(context)

    Result.success(user)
}
```

### Step 3: Handle stale AppLifecycleObserver references

**Problem:** After sign-out, the OLD `AppLifecycleObserver` is still registered with `ProcessLifecycleOwner`. When `onStop()` fires, it calls `forceFlush()` on the old `BatchQueueManager` (whose queue was cleared and is disconnected).

**Solution:** This is a benign issue — the old observer's `forceFlush()` on an empty dead queue is a no-op. The new observer (added in Step 1) correctly flushes the new queue. Over time, the old observer accumulates but has zero cost since it does nothing meaningful.

**However**, to be clean, we should remove the old observer during sign-out:

**File:** `app/src/main/java/com/skul9x/rssreader/RssApplication.kt`

Add a field to track the current observer:

```kotlin
companion object {
    // ... existing fields ...
    private var _lifecycleObserver: AppLifecycleObserver? = null
}
```

In `initializeSyncComponents()` and `onUserSignIn()`, store the observer:
```kotlin
val observer = AppLifecycleObserver(batchQueueManager, applicationScope)
_lifecycleObserver?.let {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
}
ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
_lifecycleObserver = observer
```

In `onUserSignOut()`, remove the observer:
```kotlin
_lifecycleObserver?.let {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
}
_lifecycleObserver = null
```

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/com/skul9x/rssreader/RssApplication.kt` | MODIFY | Add `onUserSignIn()`, add `_lifecycleObserver` tracking, update `initializeSyncComponents()` and `onUserSignOut()` |
| `app/src/main/java/com/skul9x/rssreader/auth/AuthManager.kt` | MODIFY | Call `onUserSignIn()` after successful Firebase sign-in |

## Test Criteria

### Test File
`app/src/test/java/com/skul9x/rssreader/auth/SyncReinitAfterSignInTest.kt`

### Test Cases

```kotlin
package com.skul9x.rssreader.auth

import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import com.skul9x.rssreader.data.sync.BatchQueueManager
import com.skul9x.rssreader.data.sync.SyncCoordinator
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests that verify sync singletons are properly re-initialized
 * after a sign-out → sign-in cycle.
 *
 * These tests operate at the singleton level since RssApplication.onUserSignIn()
 * requires Android Context. We test the underlying singleton behavior directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncReinitAfterSignInTest {

    @Before
    fun setup() {
        // Reset all singletons to simulate fresh state
        SyncCoordinator.resetInstance()
        FirestoreSyncRepository.resetInstance()
        BatchQueueManager.resetInstance()
        LocalSyncRepository.resetInstance()
    }

    @After
    fun teardown() {
        SyncCoordinator.resetInstance()
        FirestoreSyncRepository.resetInstance()
        BatchQueueManager.resetInstance()
        LocalSyncRepository.resetInstance()
    }

    /**
     * Test 1: After resetInstance() (simulating sign-out), getInstance() creates
     * a NEW instance (simulating sign-in re-initialization).
     *
     * This verifies the singleton pattern correctly supports destroy→recreate.
     */
    @Test
    fun `LocalSyncRepository getInstance returns new instance after resetInstance`() {
        val dao = mockk<com.skul9x.rssreader.data.local.ReadNewsDao>(relaxed = true)
        val prefs = mockk<com.skul9x.rssreader.data.local.SyncPreferences>(relaxed = true)
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.applicationContext } returns context

        // Create first instance
        val instance1 = LocalSyncRepository.getInstance(dao, prefs, context)
        assertNotNull("First instance should not be null", instance1)

        // Simulate sign-out
        LocalSyncRepository.resetInstance()

        // Create second instance (simulating sign-in re-init)
        val instance2 = LocalSyncRepository.getInstance(dao, prefs, context)
        assertNotNull("Second instance should not be null", instance2)

        // They should be DIFFERENT objects
        assertNotSame("After reset, getInstance should return a NEW instance", instance1, instance2)
    }

    /**
     * Test 2: After resetInstance(), BatchQueueManager.getInstance() creates
     * a new functional instance that can accept queue items.
     */
    @Test
    fun `BatchQueueManager getInstance returns functional instance after resetInstance`() = runTest {
        val localRepo = mockk<LocalSyncRepository>(relaxed = true)
        val firestoreRepo = mockk<FirestoreSyncRepository>(relaxed = true)
        val syncCoordinator = mockk<SyncCoordinator>(relaxed = true)

        // Create and verify first instance
        val bqm1 = BatchQueueManager.getInstance(localRepo, firestoreRepo, syncCoordinator, this)
        assertNotNull(bqm1)

        // Simulate sign-out
        bqm1.clearQueue()
        BatchQueueManager.resetInstance()

        // Re-create (simulating sign-in)
        val bqm2 = BatchQueueManager.getInstance(localRepo, firestoreRepo, syncCoordinator, this)
        assertNotNull(bqm2)
        assertNotSame(bqm1, bqm2)

        // Verify new instance is functional — can accept items
        val item = ReadNewsItem("test-id", 1000L, "smartphone", SyncStatus.PENDING)
        coEvery { localRepo.getById("test-id") } returns item
        every { firestoreRepo.isUserSignedIn() } returns false  // Auth guard from Phase 01

        bqm2.addToQueue("test-id")
        assertEquals("New BatchQueueManager should have 1 item in queue", 1, bqm2.getQueueSize())
    }

    /**
     * Test 3: Full sign-out → sign-in cycle. Verify that after re-initialization,
     * the new BatchQueueManager can flush items to Firestore when user is signed in.
     */
    @Test
    fun `full sign-out sign-in cycle - new BatchQueueManager can sync after re-init`() = runTest {
        val localRepo = mockk<LocalSyncRepository>(relaxed = true)
        val firestoreRepo = mockk<FirestoreSyncRepository>(relaxed = true)
        val syncCoordinator = mockk<SyncCoordinator>(relaxed = true)

        // === Phase: First session ===
        val bqm1 = BatchQueueManager.getInstance(localRepo, firestoreRepo, syncCoordinator, this)

        // === Phase: Sign-out ===
        bqm1.clearQueue()
        BatchQueueManager.resetInstance()
        SyncCoordinator.resetInstance()
        FirestoreSyncRepository.resetInstance()

        // === Phase: Sign-in (re-init) ===
        val newFirestoreRepo = mockk<FirestoreSyncRepository>(relaxed = true)
        every { newFirestoreRepo.isUserSignedIn() } returns true

        val newSyncCoordinator = SyncCoordinator(localRepo, newFirestoreRepo, null)
        val bqm2 = BatchQueueManager.getInstance(
            localRepo, newFirestoreRepo, newSyncCoordinator, this
        )

        // Add 10 items to trigger auto-flush
        repeat(10) { i ->
            val item = ReadNewsItem("new-id-$i", 2000L + i, "smartphone", SyncStatus.PENDING)
            coEvery { localRepo.getById("new-id-$i") } returns item
            bqm2.addToQueue("new-id-$i")
        }

        // Assert: uploadBatch should be called on the NEW FirestoreSyncRepository
        coVerify(exactly = 1) { newFirestoreRepo.uploadBatch(match { it.size == 10 }) }

        // Assert: The OLD firestoreRepo should NOT have been called
        coVerify(exactly = 0) { firestoreRepo.uploadBatch(any()) }
    }

    /**
     * Test 4: Verify SyncCoordinator re-creates properly and can perform full sync
     * after reset.
     */
    @Test
    fun `SyncCoordinator performs full sync after re-initialization`() = runTest {
        val localRepo = mockk<LocalSyncRepository>(relaxed = true)
        val firestoreRepo = mockk<FirestoreSyncRepository>(relaxed = true)

        // Simulate sign-out
        SyncCoordinator.resetInstance()

        // Re-create after sign-in
        every { firestoreRepo.isUserSignedIn() } returns true
        coEvery { localRepo.getPendingItems() } returns emptyList()
        coEvery { firestoreRepo.downloadSince(any()) } returns Pair(emptyList(), 100L)
        coEvery { localRepo.getLastDownloadTimestamp() } returns 0L
        coEvery { localRepo.getLastCleanupDate() } returns 0L

        val coordinator = SyncCoordinator.getInstance(localRepo, firestoreRepo, null)

        // Should not throw — coordinator is fully functional
        coordinator.performFullSync()

        // Verify it actually ran (download was called)
        coVerify { firestoreRepo.downloadSince(0L) }
    }
}
```

### How to run tests
```bash
./gradlew test --tests "com.skul9x.rssreader.auth.SyncReinitAfterSignInTest"
```

### Expected results
- All 4 tests pass
- Singletons are properly destroyed and recreated
- New BatchQueueManager can queue and flush items after re-initialization
- SyncCoordinator can perform full sync after re-initialization

## Notes
- `ProcessLifecycleOwner.get().lifecycle` is an Android API requiring the main thread. In `onUserSignIn()`, the lifecycle observer registration must happen on the main thread. Since `handleSignInResult()` is called from `viewModelScope.launch {}` (which uses `Dispatchers.Main`), this is safe. However, if `onUserSignIn()` is ever called from `Dispatchers.IO`, the observer registration must be wrapped in `withContext(Dispatchers.Main)`.
- The `AppLifecycleObserver` leak (old observers stacking up) is addressed by tracking and removing the previous observer before adding a new one.
- `schedulePeriodicSync()` uses `ExistingPeriodicWorkPolicy.KEEP`, so calling it again after sign-in is idempotent — it won't create duplicate periodic work.

---
Previous Phase: [phase-01-auth-guard.md](./phase-01-auth-guard.md)
