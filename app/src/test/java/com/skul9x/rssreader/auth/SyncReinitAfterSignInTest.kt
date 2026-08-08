package com.skul9x.rssreader.auth

import android.util.Log
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
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // Reset all singletons to simulate fresh state
        SyncCoordinator.resetInstance()
        FirestoreSyncRepository.resetInstance()
        BatchQueueManager.resetInstance()
        LocalSyncRepository.resetInstance()
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
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
