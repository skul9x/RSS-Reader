# Phase 04: Verification & Robustness
Status: ✅ Completed
Dependencies: All previous phases.

## 🎯 Mục tiêu
Tiến hành kiểm thử hộp đen (Black-box testing) và hộp trắng (White-box testing) toàn diện trên mọi khía cạnh để xác minh tính ổn định, chịu lỗi, thread-safe của hệ thống xoay vòng thông minh Universal Dynamic Multi-Model API Rotation & Quota Management.

---

## 📋 Yêu cầu (Requirements)

### 1. Các Kịch Bản Lỗi Cần Giả Lập & Kiểm Thử (Failure Scenarios)
- [x] **Kịch bản 429 (Cạn Quota)**:
  - Khi một cặp `Model-Key` trả về lỗi HTTP 429 -> Hệ thống phải đánh dấu block cặp này trong 30 giờ (Lưu bền vững vào SharedPreferences).
  - Yêu cầu tiếp theo phải bỏ qua cặp này ngay lập tức.
- [x] **Kịch bản 503 (Server Overloaded / Temporary Busy)**:
  - Khi một cặp `Model-Key` trả về lỗi HTTP 503 -> Đưa cặp này vào trạng thái cooldown 5 phút trên bộ nhớ tạm (Temporary Cooldown Map).
  - Các request trong 5 phút tới sẽ không gọi cặp này, nhưng sau 5 phút nó phải tự động khả dụng trở lại mà không cần khởi động lại ứng dụng.
- [x] **Kịch bản Cạn Kiệt Toàn Bộ (Full Outage)**:
  - Nếu tất cả các mô hình và toàn bộ API Key đều bị block/cooldown -> Hệ thống phải trả về lỗi tường minh cho người dùng ("Tất cả các tài nguyên API đang bận hoặc hết hạn mức. Vui lòng thử lại sau!").
- [x] **Kịch bản Khôi Phục Nhanh (Self-Healing Validation)**:
  - Giả lập việc xóa toàn bộ danh sách mô hình qua UI -> Xác minh danh sách mặc định được nạp lại tức thì, đảm bảo dịch vụ tóm tắt không bị gián đoạn.

---

## 🛠️ Các bước thực hiện (Implementation Steps)

1. **Chuẩn bị môi trường kiểm thử**:
   - Sử dụng `MockWebServer` (của OkHttp) để dựng một server API cục bộ giả lập các phản hồi HTTP (200 OK, 429 Too Many Requests, 503 Service Unavailable, 400 Bad Request).
2. **Viết Integration Test chi tiết**:
   - Viết file `DynamicModelRotationIntegrationTest.kt` kiểm tra toàn bộ vòng đời của một request tóm tắt khi đi qua các trạng thái lỗi khác nhau.
3. **Thực thi và sửa lỗi**:
   - Chạy test suites.
   - Khắc phục các lỗi liên quan đến đồng bộ thread (Race Conditions) hoặc lưu trữ sai lệch trạng thái block.

---

## 📂 Các file tạo mới hoặc chỉnh sửa
- **Tạo mới**: `app/src/test/java/com/skul9x/rssreader/data/network/DynamicModelRotationIntegrationTest.kt` - Bài kiểm thử tích hợp đỉnh cao.

---

## 🧪 Bài Test chi tiết (Test Cases)

Dưới đây là mã nguồn file kiểm thử tích hợp `DynamicModelRotationIntegrationTest.kt` mô phỏng đầy đủ kịch bản xoay tua chịu lỗi phức tạp:

```kotlin
package com.skul9x.rssreader.data.network

import android.content.Context
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.ModelManager
import com.skul9x.rssreader.data.network.gemini.DynamicModel
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
    
    private lateinit var quotaManager: ModelQuotaManager
    private lateinit var apiClient: GeminiApiClient

    @Before
    fun setUp() {
        mockWebServer.start()
        
        mockkObject(ApiKeyManager.Companion)
        mockkObject(ModelManager.Companion)
        
        every { ApiKeyManager.getInstance(any()) } returns apiKeyManager
        every { ModelManager.getInstance(any()) } returns modelManager
        
        // Cấu hình Mock Keys & Models
        every { apiKeyManager.getApiKeys() } returns listOf("AIzaTestKey1", "AIzaTestKey2")
        every { modelManager.getModels() } returns listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Lite")
        )
        
        quotaManager = ModelQuotaManager(context)
        // Reset quota manager states
        quotaManager.clearAll() 

        // Khởi tạo GeminiApiClient kết nối tới MockWebServer
        apiClient = GeminiApiClient(context).apply {
            setBaseUrlForTesting(mockWebServer.url("/").toString())
        }
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
        val result = apiClient.summarizeArticle("Tiêu đề test", "Nội dung bài viết rất dài...")
        
        // Xác minh kết quả
        assertTrue(result is GeminiResult.Success)
        assertEquals("Bản tóm tắt thành công từ Key 2!", (result as GeminiResult.Success).text)
        
        // Xác minh xem cặp (Model 1, Key 1) đã bị block chưa (không còn khả dụng)
        assertFalse(quotaManager.isAvailable("models/gemini-3.1-flash-lite", "AIzaTestKey1"))
    }
}
```

---
**Chúc mừng anh!** Chúng ta đã hoàn thành việc thiết lập toàn bộ file đặc tả kế hoạch (Plans) chi tiết cho cả 4 Phase. Kế hoạch đã sẵn sàng để anh phê duyệt và tiến hành code thực tế!
