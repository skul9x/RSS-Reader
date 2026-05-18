package com.skul9x.rssreader.data.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.ModelManager
import com.skul9x.rssreader.data.local.NewsSummaryDao
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import com.skul9x.rssreader.data.network.gemini.SummarizeResult
import com.skul9x.rssreader.utils.ActivityLogger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DynamicModelRotationIntegrationTest {

    private val mockWebServer = MockWebServer()
    private val context = mockk<Context>(relaxed = true)
    
    private val apiKeyManager = mockk<ApiKeyManager>()
    private val modelManager = mockk<ModelManager>()
    private val database = mockk<AppDatabase>()
    private val newsSummaryDao = mockk<NewsSummaryDao>()
    private val appPreferences = mockk<AppPreferences>()

    private val sharedPrefsMap = mutableMapOf<String, String?>()
    private val mockPrefs = mockk<SharedPreferences>()
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    private lateinit var quotaManager: ModelQuotaManager
    private lateinit var apiClient: GeminiApiClient
    
    private var simulatedTime = 1000000000000L // arbitrary fixed time

    @Before
    fun setUp() {
        mockWebServer.start()

        // Mock Static Logs
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // We use quotaManager.timeProvider to simulate time changes without unsafe static JVM System mocking

        // Mock Singleton Companion Objects
        mockkObject(ApiKeyManager.Companion)
        mockkObject(ModelManager.Companion)
        mockkObject(AppDatabase.Companion)
        mockkObject(AppPreferences.Companion)
        mockkObject(ActivityLogger)

        // Setup Singleton Returns
        every { ApiKeyManager.getInstance(any()) } returns apiKeyManager
        every { ModelManager.getInstance(any()) } returns modelManager
        every { AppDatabase.getDatabase(any()) } returns database
        every { AppPreferences.getInstance(any()) } returns appPreferences

        // Mock Dao & Cache (Always MISS so we call API)
        every { database.newsSummaryDao() } returns newsSummaryDao
        coEvery { newsSummaryDao.getSummary(any()) } returns null
        coEvery { newsSummaryDao.insertSummary(any()) } just Runs

        // Mock Preferences
        every { appPreferences.getSelectedModelId() } returns "models/gemini-3.1-flash-lite"

        // Mock ActivityLogger
        every { ActivityLogger.log(any(), any(), any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiStart(any(), any()) } just Runs
        every { ActivityLogger.logGeminiSuccess(any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiFallback(any(), any(), any(), any()) } just Runs
        every { ActivityLogger.logGeminiError(any(), any(), any()) } just Runs

        // Setup dynamic keys and models
        every { apiKeyManager.getApiKeys() } returns listOf("AIzaTestKey1", "AIzaTestKey2")
        coEvery { modelManager.getModels() } returns listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Lite")
        )

        // Setup Shared Storage Mocking for ModelQuotaManager
        sharedPrefsMap.clear()
        every { mockPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            sharedPrefsMap[key] ?: default
        }
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            sharedPrefsMap[key] = value
            mockEditor
        }
        every { mockEditor.clear() } answers {
            sharedPrefsMap.clear()
            mockEditor
        }
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs

        // Initialize Quota Manager & Client
        quotaManager = ModelQuotaManager.getInstance(context)
        quotaManager.timeProvider = { simulatedTime }
        runBlocking { quotaManager.clearAll() }

        // Setup real HTTP client connected to MockWebServer
        val realClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
        
        mockkObject(NetworkClient)
        every { NetworkClient.okHttpClient } returns realClient

        apiClient = GeminiApiClient(context).apply {
            setBaseUrlForTesting(mockWebServer.url("/").toString())
        }
        
        // Refresh client keys
        runBlocking { apiClient.refreshApiKeys() }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkAll()
    }

    @Test
    fun `summarize should rotate to second key when first key returns 429`() = runBlocking {
        // Cặp 1: Model 1 + Key 1 -> Trả về 429
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Quota Exceeded"))
        // Cặp 2: Model 1 + Key 2 -> Trả về 200 OK thành công!
        val mockSuccessJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Bản tóm tắt thành công từ Key 2!"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockSuccessJson))

        // Thực hiện tóm tắt
        val result = apiClient.summarizeForTts("Nội dung bài viết rất dài...", "https://example.com/test")
        
        // Xác minh kết quả
        assertTrue(result is SummarizeResult.Success)
        assertEquals("Bản tóm tắt thành công từ Key 2!", (result as SummarizeResult.Success).text)
        
        // Xác minh xem cặp (Model 1, Key 1) đã bị block chưa (không còn khả dụng)
        assertFalse(quotaManager.isAvailable("models/gemini-3.1-flash-lite", "AIzaTestKey1"))

        // Giả lập tua thời gian qua 30 giờ + 1 giây (30h = 30 * 60 * 60 * 1000 = 108,000,000 ms)
        simulatedTime += 30 * 60 * 60 * 1000 + 1000
        
        // Cặp (Model 1, Key 1) phải tự động khả dụng trở lại sau 30 giờ
        assertTrue(quotaManager.isAvailable("models/gemini-3.1-flash-lite", "AIzaTestKey1"))
    }

    @Test
    fun `summarize should rotate to next key when first key returns 503 and cooldown is applied`() = runBlocking {
        // Cặp 1: Model 1 + Key 1 -> Trả về 503 Server Busy
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        // Cặp 2: Model 1 + Key 2 -> Trả về 200 OK thành công!
        val mockSuccessJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Bản tóm tắt thành công từ Key 2 khi 503 xảy ra!"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockSuccessJson))

        // Thực hiện tóm tắt
        val result = apiClient.summarizeForTts("Nội dung bài viết...", "https://example.com/test503")
        
        // Xác minh kết quả thành công từ Key 2
        assertTrue(result is SummarizeResult.Success)
        assertEquals("Bản tóm tắt thành công từ Key 2 khi 503 xảy ra!", (result as SummarizeResult.Success).text)
        
        // Xác minh xem cặp (Model 1, Key 1) đã rơi vào trạng thái cooldown (không khả dụng)
        assertFalse(quotaManager.isAvailable("models/gemini-3.1-flash-lite", "AIzaTestKey1"))

        // Giả lập tua thời gian qua 5 phút + 1 giây (5m = 5 * 60 * 1000 = 300,000 ms)
        simulatedTime += 5 * 60 * 1000 + 1000
        
        // Sau 5 phút, cặp này phải tự động khả dụng trở lại
        assertTrue(quotaManager.isAvailable("models/gemini-3.1-flash-lite", "AIzaTestKey1"))
    }

    @Test
    fun `summarize should return AllQuotaExhausted when all models and keys fail`() = runBlocking {
        // Có 2 Models và 2 Keys -> Tổng cộng 4 cặp combinations.
        // Giả lập cả 4 lần gọi đều bị 429 hoặc 503
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Quota Exceeded"))
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Quota Exceeded"))
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Busy"))
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Busy"))

        val result = apiClient.summarizeForTts("Nội dung...", "https://example.com/outage")

        // Xác minh trả về lỗi cạn kiệt toàn bộ
        assertTrue(result is SummarizeResult.AllQuotaExhausted)
        assertEquals("Xin lỗi, hệ thống đang quá tải. Vui lòng thử lại sau.", result.getTextOrFallback())
    }

    @Test
    fun `model manager self-healing should trigger default models restoration when all models are deleted`() = runBlocking {
        // Chúng ta tạo mock SharedPreferences thực tế cho ModelManager
        val modelPrefsMap = mutableMapOf<String, String?>()
        val mockModelPrefs = mockk<SharedPreferences>()
        val mockModelEditor = mockk<SharedPreferences.Editor>(relaxed = true)

        every { mockModelPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            modelPrefsMap[key] ?: default
        }
        every { mockModelPrefs.edit() } returns mockModelEditor
        every { mockModelEditor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            modelPrefsMap[key] = value
            mockModelEditor
        }
        every { mockModelEditor.clear() } answers {
            modelPrefsMap.clear()
            mockModelEditor
        }

        val testContext = mockk<Context>(relaxed = true)
        every { testContext.applicationContext } returns testContext
        every { testContext.getSharedPreferences("dynamic_models", Context.MODE_PRIVATE) } returns mockModelPrefs

        // Tạo instance ModelManager thực tế
        mockkObject(ModelManager.Companion)
        val realModelManager = ModelManager.getInstance(testContext)

        // Reset về default trước
        realModelManager.resetToDefaults()
        
        val initialModels = realModelManager.getModels()
        assertTrue(initialModels.isNotEmpty())
        assertEquals(ModelManager.DEFAULT_MODELS.size, initialModels.size)

        // Giả lập xóa từng model cho đến hết
        for (model in initialModels) {
            realModelManager.removeModel(model.modelId)
        }

        // Sau khi xóa toàn bộ, self-healing phải tự động kích hoạt và khôi phục lại danh sách mặc định
        val healedModels = realModelManager.getModels()
        assertTrue("Self-healing should restore default models", healedModels.isNotEmpty())
        assertEquals(ModelManager.DEFAULT_MODELS.size, healedModels.size)
        assertEquals(ModelManager.DEFAULT_MODELS.first().modelId, healedModels.first().modelId)
    }
}
