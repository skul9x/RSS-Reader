package com.skul9x.rssreader.data.network

import android.content.Context
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.NewsSummaryDao
import com.skul9x.rssreader.data.local.ModelManager
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import com.skul9x.rssreader.data.network.gemini.SummarizeModel
import com.skul9x.rssreader.utils.ActivityLogger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GeminiApiClientPriorityTest {
    private val context = mockk<Context>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>()
    private val appPreferences = mockk<AppPreferences>()
    private val modelManager = mockk<ModelManager>()
    private val database = mockk<AppDatabase>()
    private val newsSummaryDao = mockk<NewsSummaryDao>()
    private val okHttpClient = mockk<OkHttpClient>()
    private val mockCall = mockk<okhttp3.Call>()

    private lateinit var apiClient: GeminiApiClient

    @Before
    fun setup() {
        every { context.applicationContext } returns context
        
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(ApiKeyManager)
        every { ApiKeyManager.getInstance(any()) } returns apiKeyManager
        every { apiKeyManager.getApiKeys() } returns listOf("key1")

        mockkObject(ModelManager)
        every { ModelManager.getInstance(any()) } returns modelManager
        coEvery { modelManager.getModels() } returns listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
            DynamicModel("models/gemini-3-flash-preview", "Gemini 3 Flash Preview"),
            DynamicModel("models/gemini-2.5-flash", "Gemini 2.5 Flash")
        )

        mockkObject(AppPreferences)
        every { AppPreferences.getInstance(any()) } returns appPreferences
        // Default: User selects Gemini 3 Flash Preview
        every { appPreferences.getSelectedSummarizeModel() } returns SummarizeModel.GEMINI_3_FLASH_PREVIEW
        every { appPreferences.getSelectedModelId() } returns "models/gemini-3-flash-preview"

        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.newsSummaryDao() } returns newsSummaryDao
        coEvery { database.newsSummaryDao().getSummary(any()) } returns null
        coEvery { database.newsSummaryDao().insertSummary(any()) } just Runs

        mockkObject(NetworkClient)
        every { NetworkClient.okHttpClient } returns okHttpClient
        
        mockkObject(ActivityLogger)
        every { ActivityLogger.log(any(), any(), any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiStart(any(), any()) } just Runs
        every { ActivityLogger.logGeminiSuccess(any(), any(), any()) } just Runs
        
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.cancel() } just Runs
        
        apiClient = GeminiApiClient(context)
    }

    @Test
    fun `summarizeWithRetry should prioritize the user selected model`() = runBlocking {
        val capturedModels = mutableListOf<String>()
        val requestSlot = slot<Request>()
        every { okHttpClient.newCall(capture(requestSlot)) } returns mockCall
        
        // Mock successful response
        every { mockCall.enqueue(any()) } answers {
            val request = requestSlot.captured
            val url = request.url.toString()
            
            // Extract model from URL: .../v1beta/models/xxx:generateContent?key=...
            val modelName = url.substringAfter("v1beta/").substringBefore(":generateContent")
            capturedModels.add(modelName)
            
            val callback = it.invocation.args[0] as Callback
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(
                    "application/json".toMediaType(),
                    "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Summary content\"}]}}]}"
                ))
                .build()
            callback.onResponse(mockCall, response)
        }

        apiClient.summarizeForTts("content", "url")

        // Verify that the first model called is Gemini 3 Flash Preview (the user selection)
        assertEquals("models/gemini-3-flash-preview", capturedModels.first())
    }

    @Test
    fun `getSortedPriorityModels should put active model at first place`() = runBlocking {
        every { appPreferences.getSelectedModelId() } returns "models/gemini-2.5-flash"
        
        val sortedModels = apiClient.getSortedPriorityModels()
        
        assertEquals("models/gemini-2.5-flash", sortedModels.first())
        assertEquals(4, sortedModels.size)
        assertEquals(
            listOf(
                "models/gemini-2.5-flash",
                "models/gemini-3.1-flash-lite",
                "models/gemini-2.5-flash-lite",
                "models/gemini-3-flash-preview"
            ),
            sortedModels
        )
    }

    @Test
    fun `getSortedPriorityModels should fallback to default order if active model is not in list`() = runBlocking {
        every { appPreferences.getSelectedModelId() } returns "models/non-existent-model"
        
        val sortedModels = apiClient.getSortedPriorityModels()
        
        assertEquals(
            listOf(
                "models/gemini-3.1-flash-lite",
                "models/gemini-2.5-flash-lite",
                "models/gemini-3-flash-preview",
                "models/gemini-2.5-flash"
            ),
            sortedModels
        )
    }
}
