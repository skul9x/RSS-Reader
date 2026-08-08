package com.skul9x.rssreader.data.sync

import android.util.Log
import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchQueueManagerAuthGuardTest {

    private val localRepo = mockk<LocalSyncRepository>(relaxed = true)
    private val firestoreRepo = mockk<FirestoreSyncRepository>(relaxed = true)
    private val syncCoordinator = mockk<SyncCoordinator>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // Reset BatchQueueManager singleton for each test
        BatchQueueManager.resetInstance()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
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
