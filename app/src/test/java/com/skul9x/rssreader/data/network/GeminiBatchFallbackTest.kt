package com.skul9x.rssreader.data.network

import android.content.Context
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.NewsSummaryDao
import com.skul9x.rssreader.utils.ActivityLogger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GeminiBatchFallbackTest {
    private val context = mockk<Context>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>()
    private val database = mockk<AppDatabase>()
    private val newsSummaryDao = mockk<NewsSummaryDao>()

    private lateinit var apiClient: GeminiApiClient

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(ApiKeyManager)
        every { ApiKeyManager.getInstance(any()) } returns apiKeyManager
        every { apiKeyManager.getApiKeys() } returns listOf("key1")

        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.newsSummaryDao() } returns newsSummaryDao
        
        mockkObject(ActivityLogger)
        every { ActivityLogger.log(any(), any(), any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiFallback(any(), any(), any(), any()) } just Runs

        apiClient = spyk(GeminiApiClient(context))
    }

    @Test
    fun `batch success - returns all results and no single fallback`() = runBlocking {
        val titles = mapOf("1" to "Title 1", "2" to "Title 2")
        val batchResult = mapOf("1" to "Tiêu đề 1", "2" to "Tiêu đề 2")

        coEvery { apiClient.translateTitleBatch(any()) } returns batchResult
        coEvery { apiClient.translateToVietnamese(any()) } returns "Should not be called"

        val result = apiClient.translateTitleBatchWithFallback(titles)

        assertEquals(2, result.size)
        assertEquals("Tiêu đề 1", result["1"])
        assertEquals("Tiêu đề 2", result["2"])
        
        coVerify(exactly = 1) { apiClient.translateTitleBatch(titles) }
        coVerify(exactly = 0) { apiClient.translateToVietnamese(any()) }
    }

    @Test
    fun `batch partial success - falls back for missing items`() = runBlocking {
        val titles = mapOf("1" to "Title 1", "2" to "Title 2")
        val batchResult = mapOf("1" to "Tiêu đề 1") // Only 1 translated

        coEvery { apiClient.translateTitleBatch(any()) } returns batchResult
        coEvery { apiClient.translateToVietnamese("Title 2") } returns "Tiêu đề 2 fallback"

        val result = apiClient.translateTitleBatchWithFallback(titles)

        assertEquals(2, result.size)
        assertEquals("Tiêu đề 1", result["1"])
        assertEquals("Tiêu đề 2 fallback", result["2"])
        
        coVerify(exactly = 1) { apiClient.translateTitleBatch(titles) }
        coVerify(exactly = 1) { apiClient.translateToVietnamese("Title 2") }
    }

    @Test
    fun `batch complete failure - falls back for all items`() = runBlocking {
        val titles = mapOf("1" to "Title 1", "2" to "Title 2")
        
        coEvery { apiClient.translateTitleBatch(any()) } returns emptyMap()
        coEvery { apiClient.translateToVietnamese("Title 1") } returns "Tiêu đề 1 fallback"
        coEvery { apiClient.translateToVietnamese("Title 2") } returns "Tiêu đề 2 fallback"

        val result = apiClient.translateTitleBatchWithFallback(titles)

        assertEquals(2, result.size)
        assertEquals("Tiêu đề 1 fallback", result["1"])
        assertEquals("Tiêu đề 2 fallback", result["2"])
        
        coVerify(exactly = 1) { apiClient.translateTitleBatch(titles) }
        coVerify(exactly = 2) { apiClient.translateToVietnamese(any()) }
    }

    @Test
    fun `chunking - splits large batch into chunks of 15`() = runBlocking {
        // Create 20 titles
        val titles = (1..20).associate { it.toString() to "Title $it" }
        
        coEvery { apiClient.translateTitleBatch(any()) } returns emptyMap()
        coEvery { apiClient.translateToVietnamese(any()) } returns "Translated"

        apiClient.translateTitleBatchWithFallback(titles)

        // Verify it called batch twice (15 items then 5 items)
        coVerify(exactly = 2) { apiClient.translateTitleBatch(any()) }
        
        // Check specifically that it splits correctly
        coVerify { apiClient.translateTitleBatch(match { it.size == 15 }) }
        coVerify { apiClient.translateTitleBatch(match { it.size == 5 }) }
    }
}
