package com.skul9x.rssreader.data.network

import android.content.Context
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.NewsSummaryDao
import com.skul9x.rssreader.data.network.gemini.GeminiPrompts
import com.skul9x.rssreader.utils.ActivityLogger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GeminiFlashLiteTranslationTest {

    private val context = mockk<Context>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>()
    private val database = mockk<AppDatabase>()
    private val newsSummaryDao = mockk<NewsSummaryDao>()
    private val okHttpClient = mockk<OkHttpClient>()
    private val quotaManager = mockk<ModelQuotaManager>(relaxed = true)

    private lateinit var apiClient: GeminiApiClient

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(ApiKeyManager)
        every { ApiKeyManager.getInstance(any()) } returns apiKeyManager
        every { apiKeyManager.getApiKeys() } returns listOf("test_api_key_1")

        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.newsSummaryDao() } returns newsSummaryDao
        coEvery { database.newsSummaryDao().getSummary(any()) } returns null

        mockkObject(ModelQuotaManager)
        every { ModelQuotaManager.getInstance(any()) } returns quotaManager
        coEvery { quotaManager.isAvailable(any(), any()) } returns true
        coEvery { quotaManager.markExhausted(any(), any()) } just Runs
        coEvery { quotaManager.markCooldown(any(), any()) } just Runs

        mockkObject(NetworkClient)
        every { NetworkClient.okHttpClient } returns okHttpClient

        mockkObject(ActivityLogger)
        every { ActivityLogger.log(any(), any(), any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiStart(any(), any()) } just Runs
        every { ActivityLogger.logGeminiSuccess(any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiFallback(any(), any(), any(), any()) } just Runs

        apiClient = GeminiApiClient(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun readRequestBody(request: Request): JsonObject {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        val jsonString = buffer.readUtf8()
        return Json.parseToJsonElement(jsonString).jsonObject
    }

    @Test
    fun testSingleTranslation_usesFlashLiteLatestAndSystemInstruction() = runBlocking {
        val capturedRequests = mutableListOf<Request>()
        val mockCall = mockk<okhttp3.Call>()

        every { okHttpClient.newCall(capture(capturedRequests)) } returns mockCall
        every { mockCall.cancel() } just Runs

        val responseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Tin nóng: Hội nghị công nghệ công bố các đột phá AI mới"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<Callback>()
            val request = capturedRequests.last()
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseJson.toResponseBody("application/json".toMediaType()))
                .build()
            callback.onResponse(mockCall, response)
        }

        val originalTitle = "Breaking: Tech conference announces new AI innovations"
        val translated = apiClient.translateToVietnamese(originalTitle)

        assertEquals("Tin nóng: Hội nghị công nghệ công bố các đột phá AI mới", translated)
        assertEquals(1, capturedRequests.size)

        val request = capturedRequests[0]
        val url = request.url.toString()
        assertTrue(
            "Request URL must target models/gemini-flash-lite-latest:generateContent, got $url",
            url.contains("models/gemini-flash-lite-latest:generateContent")
        )
        assertTrue(
            "Request URL must contain test API key, got $url",
            url.contains("key=test_api_key_1")
        )

        val body = readRequestBody(request)

        // 1. Verify system instruction at top level
        val sysInstructionObj = body["system_instruction"]?.jsonObject
        assertNotNull("system_instruction must be present at top level", sysInstructionObj)
        val sysParts = sysInstructionObj!!["parts"]?.jsonArray
        assertNotNull("system_instruction must contain parts", sysParts)
        assertEquals(1, sysParts!!.size)
        assertEquals(
            GeminiPrompts.TRANSLATION_SYSTEM_INSTRUCTION,
            sysParts[0].jsonObject["text"]?.jsonPrimitive?.content
        )

        // 2. Verify clean user prompt
        val contents = body["contents"]?.jsonArray
        assertNotNull("contents array must be present", contents)
        val userParts = contents!![0].jsonObject["parts"]?.jsonArray
        assertEquals("Text: $originalTitle", userParts!![0].jsonObject["text"]?.jsonPrimitive?.content)

        // 3. Verify generationConfig: maxOutputTokens == 128, temperature == 0.1, thinkingLevel == minimal
        val genConfig = body["generationConfig"]?.jsonObject
        assertNotNull("generationConfig must be present", genConfig)
        assertEquals(128, genConfig!!["maxOutputTokens"]?.jsonPrimitive?.int)
        assertEquals(0.1, genConfig["temperature"]?.jsonPrimitive?.double ?: 0.0, 0.001)
        assertEquals("minimal", genConfig["thinkingConfig"]?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)
    }

    @Test
    fun testBatchTranslation_usesFlashLiteLatestWithJsonMode() = runBlocking {
        val capturedRequests = mutableListOf<Request>()
        val mockCall = mockk<okhttp3.Call>()

        every { okHttpClient.newCall(capture(capturedRequests)) } returns mockCall
        every { mockCall.cancel() } just Runs

        val titles = (1..15).associate { "id_$it" to "Original Title $it" }

        val responseMapJson = buildJsonObject {
            titles.forEach { (id, _) ->
                put(id, "Tiêu đề đã dịch $id")
            }
        }.toString()

        val responseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": ${Json.encodeToString(responseMapJson)}
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<Callback>()
            val request = capturedRequests.last()
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseJson.toResponseBody("application/json".toMediaType()))
                .build()
            callback.onResponse(mockCall, response)
        }

        val result = apiClient.translateTitleBatch(titles)

        assertEquals(15, result.size)
        assertEquals("Tiêu đề đã dịch id_1", result["id_1"])
        assertEquals("Tiêu đề đã dịch id_15", result["id_15"])

        assertEquals(1, capturedRequests.size)
        val request = capturedRequests[0]
        val url = request.url.toString()
        assertTrue(
            "Batch translation URL must target models/gemini-flash-lite-latest, got $url",
            url.contains("models/gemini-flash-lite-latest:generateContent")
        )

        val body = readRequestBody(request)

        // Verify system instruction
        val sysInstructionObj = body["system_instruction"]?.jsonObject
        assertNotNull("system_instruction must be present in batch request", sysInstructionObj)
        assertEquals(
            GeminiPrompts.BATCH_TRANSLATION_SYSTEM_INSTRUCTION,
            sysInstructionObj!!["parts"]?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
        )

        // Verify JSON mode and maxOutputTokens == 384
        val genConfig = body["generationConfig"]?.jsonObject
        assertNotNull("generationConfig must be present", genConfig)
        assertEquals("application/json", genConfig!!["responseMimeType"]?.jsonPrimitive?.content)
        assertEquals(384, genConfig["maxOutputTokens"]?.jsonPrimitive?.int)
        assertEquals("minimal", genConfig["thinkingConfig"]?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)

        // Verify user prompt contains all 15 titles in JSON mapping
        val userPrompt = body["contents"]?.jsonArray?.get(0)?.jsonObject
            ?.get("parts")?.jsonArray?.get(0)?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
        assertNotNull(userPrompt)
        val userJson = Json.parseToJsonElement(userPrompt!!).jsonObject
        assertEquals(15, userJson.size)
        assertEquals("Original Title 1", userJson["id_1"]?.jsonPrimitive?.content)
    }

    @Test
    fun testTranslationFallbackOnQuota429() = runBlocking {
        val capturedRequests = mutableListOf<Request>()
        val mockCall = mockk<okhttp3.Call>()

        every { okHttpClient.newCall(capture(capturedRequests)) } returns mockCall
        every { mockCall.cancel() } just Runs

        val successResponseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Bản dịch từ model dự phòng"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<Callback>()
            val request = capturedRequests.last()
            val url = request.url.toString()

            if (url.contains("models/gemini-flash-lite-latest")) {
                // Return 429 Quota Exceeded for primary model
                val response = Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("{\"error\": {\"code\": 429, \"message\": \"Quota exceeded\"}}".toResponseBody("application/json".toMediaType()))
                    .build()
                callback.onResponse(mockCall, response)
            } else if (url.contains("models/gemini-3.5-flash-lite")) {
                // Return 200 OK for next fallback model
                val response = Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(successResponseJson.toResponseBody("application/json".toMediaType()))
                    .build()
                callback.onResponse(mockCall, response)
            } else {
                val response = Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Unexpected model")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
                callback.onResponse(mockCall, response)
            }
        }

        val result = apiClient.translateToVietnamese("Test headline")

        assertEquals("Bản dịch từ model dự phòng", result)
        assertEquals(2, capturedRequests.size)

        // First attempt must be gemini-flash-lite-latest
        assertTrue(
            "First request must be models/gemini-flash-lite-latest",
            capturedRequests[0].url.toString().contains("models/gemini-flash-lite-latest")
        )
        // Second attempt must fall back to gemini-3.5-flash-lite
        assertTrue(
            "Second request must fall back to models/gemini-3.5-flash-lite",
            capturedRequests[1].url.toString().contains("models/gemini-3.5-flash-lite")
        )

        // Verify quotaManager marked exhausted for gemini-flash-lite-latest
        coVerify(atLeast = 1) {
            quotaManager.markExhausted("models/gemini-flash-lite-latest", "test_api_key_1")
        }
    }

    @Test
    fun testBatchTranslationFallbackOnQuota429() = runBlocking {
        val capturedRequests = mutableListOf<Request>()
        val mockCall = mockk<okhttp3.Call>()

        every { okHttpClient.newCall(capture(capturedRequests)) } returns mockCall
        every { mockCall.cancel() } just Runs

        val successBatchResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"id_1\": \"Tiêu đề 1 dự phòng\"}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<Callback>()
            val request = capturedRequests.last()
            val url = request.url.toString()

            if (url.contains("models/gemini-flash-lite-latest")) {
                val response = Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
                callback.onResponse(mockCall, response)
            } else if (url.contains("models/gemini-3.5-flash-lite")) {
                val response = Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(successBatchResponse.toResponseBody("application/json".toMediaType()))
                    .build()
                callback.onResponse(mockCall, response)
            }
        }

        val result = apiClient.translateTitleBatch(mapOf("id_1" to "Title 1"))

        assertEquals(1, result.size)
        assertEquals("Tiêu đề 1 dự phòng", result["id_1"])
        assertEquals(2, capturedRequests.size)
        assertTrue(capturedRequests[0].url.toString().contains("models/gemini-flash-lite-latest"))
        assertTrue(capturedRequests[1].url.toString().contains("models/gemini-3.5-flash-lite"))

        coVerify(atLeast = 1) {
            quotaManager.markExhausted("models/gemini-flash-lite-latest", "test_api_key_1")
        }
    }

    @Test
    fun testSingleTranslation_networkErrorAbortsCleanly() = runBlocking {
        val mockCall = mockk<okhttp3.Call>()
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.cancel() } just Runs
        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<Callback>()
            callback.onFailure(mockCall, IOException("Connection reset"))
        }

        val originalText = "Original Untranslated Headline"
        val result = apiClient.translateToVietnamese(originalText)

        // Safety guarantee: aborts cleanly and returns original text
        assertEquals(originalText, result)
    }
}
