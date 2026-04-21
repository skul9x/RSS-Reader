package com.skul9x.rssreader.data.network

import android.content.Context
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.NewsSummaryDao
import com.skul9x.rssreader.utils.ActivityLogger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GeminiApiClientFailFastTest {
    private val context = mockk<Context>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>()
    private val database = mockk<AppDatabase>()
    private val newsSummaryDao = mockk<NewsSummaryDao>()
    private val okHttpClient = mockk<OkHttpClient>()
    private val mockCall = mockk<okhttp3.Call>()

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
        every { apiKeyManager.getApiKeys() } returns listOf("key1", "key2", "key3")

        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.newsSummaryDao() } returns newsSummaryDao
        coEvery { database.newsSummaryDao().getSummary(any()) } returns null

        mockkObject(NetworkClient)
        every { NetworkClient.okHttpClient } returns okHttpClient
        
        mockkObject(ActivityLogger)
        every { ActivityLogger.log(any(), any(), any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiStart(any(), any()) } just Runs
        every { ActivityLogger.logGeminiFallback(any(), any(), any(), any()) } just Runs
        
        // Mock client.newCall to return our mock call
        every { okHttpClient.newCall(any()) } returns mockCall

        apiClient = GeminiApiClient(context)
    }

    @Test
    fun `when network error occurs during translation, it stops retrying immediately`() = runBlocking {
        // Setup mock call to fail with IOException
        every { mockCall.enqueue(any()) } answers {
            val callback = it.invocation.args[0] as Callback
            callback.onFailure(mockCall, IOException("No internet connection"))
        }

        val text = "Hello"
        val result = apiClient.translateToVietnamese(text)

        // Verify it returns original text
        assertEquals(text, result)

        // Verify it ONLY called API for the first key
        // (Model: gemini-flash-lite-latest, 3 keys available)
        // If it didn't fail-fast, it would call newCall 3 times for that model
        verify(exactly = 1) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `when network error occurs during batch translation, it stops retrying immediately`() = runBlocking {
        // Setup mock call to fail with IOException
        every { mockCall.enqueue(any()) } answers {
            val callback = it.invocation.args[0] as Callback
            callback.onFailure(mockCall, IOException("No internet connection"))
        }

        val titles = mapOf("1" to "Title 1", "2" to "Title 2")
        val result = apiClient.translateTitleBatch(titles)

        // Verify it returns empty map
        assertEquals(0, result.size)

        // Verify it ONLY called API once
        verify(exactly = 1) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `when network error occurs during summarization, it stops retrying immediately`() = runBlocking {
        // Setup mock call to fail with IOException
        every { mockCall.enqueue(any()) } answers {
            val callback = it.invocation.args[0] as Callback
            callback.onFailure(mockCall, IOException("No internet connection"))
        }

        val content = "Some long article content"
        val url = "https://example.com"
        
        val result = apiClient.summarizeForTts(content, url)

        // Verify it returns error result
        assertEquals(true, result is com.skul9x.rssreader.data.network.gemini.SummarizeResult.Error)

        // Verify it ONLY called API once
        verify(exactly = 1) { okHttpClient.newCall(any()) }
    }
}
