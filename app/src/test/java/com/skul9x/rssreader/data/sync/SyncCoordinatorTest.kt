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
    fun `mergeWithLocal should correctly merge logic`() = runTest {
        // Arrange
        val coordinator = createSyncCoordinator()
        
        val remoteItem = ReadNewsItem("id1", 2000, "smartphone", SyncStatus.SYNCED)
        val localItem = ReadNewsItem("id1", 1000, "tablet", SyncStatus.SYNCED)
        
        // Setup mocks
        coEvery { localRepo.getByIds(listOf("id1")) } returns listOf(localItem)
        
        // Act
        coordinator.mergeWithLocal(listOf(remoteItem))
        
        // Assert: remote (2000) > local (1000) -> Should update
        coVerify { localRepo.insertFromRemote(match { 
            it.size == 1 && it[0].newsId == "id1" && it[0].readAt == 2000L 
        }) }
    }
}
