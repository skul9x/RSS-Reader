package com.skul9x.rssreader.data.repository

import com.skul9x.rssreader.data.local.CachedNewsDao
import com.skul9x.rssreader.data.local.ReadNewsDao
import com.skul9x.rssreader.data.local.RssFeedDao
import com.skul9x.rssreader.data.model.CachedNewsItem
import com.skul9x.rssreader.data.model.RssFeed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RssRepositoryTest {

    private lateinit var rssFeedDao: RssFeedDao
    private lateinit var cachedNewsDao: CachedNewsDao
    private lateinit var readNewsDao: ReadNewsDao
    private lateinit var repository: RssRepository

    @Before
    fun setup() {
        rssFeedDao = mockk(relaxed = true)
        cachedNewsDao = mockk(relaxed = true)
        readNewsDao = mockk(relaxed = true)
        repository = RssRepository(rssFeedDao, cachedNewsDao, readNewsDao)
    }

    @Test
    fun `getRandomNewsFromCache uses application level shuffling`() = runTest {
        // Arrange
        val feed = RssFeed(id = 1, name = "Test Feed", url = "http://test.com")
        coEvery { rssFeedDao.getEnabledFeedsList() } returns listOf(feed)
        
        // Mock ID return
        val mockIds = listOf("1", "2", "3", "4", "5")
        coEvery { cachedNewsDao.getUnreadNewsIdsFromFeeds(any(), any()) } returns mockIds

        // Mock Item return (simulation of returning items for shuffled IDs)
        val mockItems = mockIds.map { 
            CachedNewsItem(
                id = it, 
                title = "Title $it", 
                description = "Desc",
                content = "Content",
                link = "http://$it",
                pubDate = "Now",
                imageUrl = null,
                feedId = 1, 
                feedName = "Test Feed",
                titleHash = it
            ) 
        }
        
        coEvery { cachedNewsDao.getNewsByIds(any()) } answers {
            val requestedIds = firstArg<List<String>>()
            mockItems.filter { it.id in requestedIds }
        }

        // Act
        val result = repository.getRandomNewsFromCache(count = 3)

        // Assert
        // 1. Verify getUnreadNewsIdsFromFeeds is called (Step 1 of opt)
        coVerify { cachedNewsDao.getUnreadNewsIdsFromFeeds(any(), any()) }
        
        // 2. Verify getNewsByIds is called (Step 3 of opt)
        coVerify { cachedNewsDao.getNewsByIds(any()) }

        // 3. Verify result size
        assertTrue(result.size <= 3)
    }
}
