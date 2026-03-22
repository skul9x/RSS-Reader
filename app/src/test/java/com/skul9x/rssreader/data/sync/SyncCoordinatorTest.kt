package com.skul9x.rssreader.data.sync

import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {

    private val localRepo = mockk<LocalSyncRepository>(relaxed = true)
    private val firestoreRepo = mockk<FirestoreSyncRepository>(relaxed = true)
    
    // Direct instantiation since constructor is now internal
    private fun createSyncCoordinator(): SyncCoordinator {
        return SyncCoordinator(localRepo, firestoreRepo, null)
    }

    @Test
    fun `mergeWithLocal should use getByIds (batch) instead of getById (loop)`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        
        val remoteItems = listOf(
            ReadNewsItem("id1", 1000, "dev1", SyncStatus.SYNCED),
            ReadNewsItem("id2", 2000, "dev1", SyncStatus.SYNCED),
            ReadNewsItem("id3", 3000, "dev1", SyncStatus.SYNCED)
        )
        
        // Mock getByIds to return empty list (simulating no local items)
        coEvery { localRepo.getByIds(any()) } returns emptyList()

        // Act - Call internal method directly
        coordinator.mergeWithLocal(remoteItems)

        // Assert
        // Verify getByIds is called ONCE with correct IDs
        coVerify(exactly = 1) { localRepo.getByIds(match { it.size == 3 && it.containsAll(listOf("id1", "id2", "id3")) }) }
        
        // Verify getById is NEVER called (proof of N+1 fix)
        coVerify(exactly = 0) { localRepo.getById(any()) }
    }
    
    @Test
    fun `mergeWithLocal should correctly merge logic - earliest wins`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        
        // Remote (1000) is EARLIER than local (2000) -> Should update to 1000
        val remoteItem = ReadNewsItem("id1", 1000, "smartphone", SyncStatus.SYNCED)
        val localItem = ReadNewsItem("id1", 2000, "tablet", SyncStatus.SYNCED)
        
        coEvery { localRepo.getByIds(listOf("id1")) } returns listOf(localItem)
        
        // Act
        coordinator.mergeWithLocal(listOf(remoteItem))
        
        // Assert
        coVerify { localRepo.insertFromRemote(match { 
            it.size == 1 && it[0].newsId == "id1" && it[0].readAt == 1000L 
        }) }
    }

    @Test
    fun `mergeWithLocal should keep local if local is earlier`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        
        // Local (1000) is EARLIER than remote (2000) -> Should NOT update
        val remoteItem = ReadNewsItem("id1", 2000, "smartphone", SyncStatus.SYNCED)
        val localItem = ReadNewsItem("id1", 1000, "tablet", SyncStatus.SYNCED)
        
        coEvery { localRepo.getByIds(listOf("id1")) } returns listOf(localItem)
        
        // Act
        coordinator.mergeWithLocal(listOf(remoteItem))
        
        // Assert
        coVerify(exactly = 0) { localRepo.insertFromRemote(any()) }
    }

    @Test
    fun `mergeWithLocal should prefer smartphone on same timestamp tie-breaker`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        
        // Same timestamp (1000), Remote is smartphone, Local is tablet -> Should update
        val remoteItem = ReadNewsItem("id1", 1000, "smartphone", SyncStatus.SYNCED)
        val localItem = ReadNewsItem("id1", 1000, "tablet", SyncStatus.SYNCED)
        
        coEvery { localRepo.getByIds(listOf("id1")) } returns listOf(localItem)
        
        // Act
        coordinator.mergeWithLocal(listOf(remoteItem))
        
        // Assert
        coVerify(exactly = 1) { localRepo.insertFromRemote(any()) }
    }

    @Test
    fun `mergeWithLocal should insert new items from remote`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        
        val remoteItem = ReadNewsItem("id-new", 1000, "smartphone", SyncStatus.SYNCED)
        
        // Mock local DB as empty for this ID
        coEvery { localRepo.getByIds(listOf("id-new")) } returns emptyList()
        
        // Act
        coordinator.mergeWithLocal(listOf(remoteItem))
        
        // Assert
        coVerify { localRepo.insertFromRemote(match { 
            it.size == 1 && it[0].newsId == "id-new" 
        }) }
    }

    @Test
    fun `performFullSync should mark as synced right after upload`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        val pending = listOf(ReadNewsItem("p1", 100, "dev", SyncStatus.PENDING))
        coEvery { localRepo.getPendingItems() } returns pending
        coEvery { firestoreRepo.isUserSignedIn() } returns true
        coEvery { firestoreRepo.downloadSince(any()) } returns Pair(emptyList(), 200L)

        // Act
        coordinator.performFullSync()

        // Assert
        coVerifyOrder {
            firestoreRepo.uploadBatch(pending)
            localRepo.markAsSynced(listOf("p1"))
            firestoreRepo.downloadSince(any())
        }
    }

    @Test
    fun `performFullSync should not update download timestamp if merge fails`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        coEvery { firestoreRepo.isUserSignedIn() } returns true
        coEvery { localRepo.getPendingItems() } returns emptyList()
        coEvery { firestoreRepo.downloadSince(any()) } returns Pair(listOf(mockk()), 500L)
        
        // Simulating merge failure
        coEvery { localRepo.getByIds(any()) } throws RuntimeException("Merge failed")

        // Act & Assert
        try {
            coordinator.performFullSync()
        } catch (e: Exception) {
            // Expected
        }

        // Verify timestamp was NOT updated
        coVerify(exactly = 0) { localRepo.updateLastDownloadTimestamp(500L) }
    }
}
