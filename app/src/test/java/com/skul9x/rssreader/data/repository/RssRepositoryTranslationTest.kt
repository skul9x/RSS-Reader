package com.skul9x.rssreader.data.repository

import com.skul9x.rssreader.data.local.CachedNewsDao
import com.skul9x.rssreader.data.local.ReadNewsDao
import com.skul9x.rssreader.data.local.RssFeedDao
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests that RssRepository uses the batch method instead of per-item updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RssRepositoryTranslationTest {

    private lateinit var repository: RssRepository
    private lateinit var cachedNewsDao: CachedNewsDao
    private lateinit var rssFeedDao: RssFeedDao
    private lateinit var readNewsDao: ReadNewsDao
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        cachedNewsDao = mockk(relaxed = true)
        rssFeedDao = mockk(relaxed = true)
        readNewsDao = mockk(relaxed = true)
        repository = RssRepository(rssFeedDao, cachedNewsDao, readNewsDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateTranslatedTitles calls batch method not individual updates`() = runTest {
        val translations = mapOf("a" to "Title A", "b" to "Title B")

        repository.updateTranslatedTitles(translations)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should call batch method once
        coVerify(exactly = 1) { cachedNewsDao.updateTranslatedTitlesBatch(translations) }
        // Should NOT call individual method directly from repository
        coVerify(exactly = 0) { cachedNewsDao.updateTranslatedTitle(any(), any()) }
    }

    @Test
    fun `updateTranslatedTitles with empty map skips database call`() = runTest {
        repository.updateTranslatedTitles(emptyMap())
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { cachedNewsDao.updateTranslatedTitlesBatch(any()) }
    }
}
