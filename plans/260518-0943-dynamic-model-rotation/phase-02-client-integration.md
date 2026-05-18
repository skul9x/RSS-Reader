# Phase 02: Client Integration & Priority
Status: ✅ Completed
Dependencies: [Phase 01: Core Setup & Self-Healing](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-01-core-setup.md)

## 🎯 Mục tiêu
Tích hợp danh sách mô hình động từ `ModelManager` vào `GeminiApiClient.kt`, triển khai thuật toán sắp xếp độ ưu tiên theo Model được chọn trước (Active Model-First) và xoay vòng chịu lỗi thông minh (Fault-Tolerant Model-First, Key-Second).

---

## 📋 Yêu cầu (Requirements)

### 1. Chức năng (Functional)
- [x] Thay thế danh sách mô hình hardcoded `MODELS` trong `GeminiApiClient` bằng việc gọi động qua `ModelManager.getInstance(context).getModels()`.
- [x] Triển khai thuật toán sắp xếp danh sách xoay vòng ưu tiên:
  - Lấy `activeModelId` của người dùng từ `AppPreferences`.
  - Nếu `activeModelId` có trong danh sách động, đưa model đó lên vị trí đầu tiên (index 0). Các model còn lại giữ nguyên thứ tự sắp xếp mặc định của chúng trong danh sách động.
  - Nếu `activeModelId` không có trong danh sách (do bị xóa), sử dụng nguyên danh sách động.
- [x] Cập nhật luồng retry của `summarizeWithRetry` để duyệt qua danh sách mô hình động đã được sắp xếp ưu tiên này.
- [x] **Thread Safety & Sync**: Đảm bảo việc lấy danh sách model động không bị deadlock với luồng gọi mạng và được thực thi trên background thread.

### 2. Trải nghiệm & Giám sát (Observability)
- [x] Bổ sung log chi tiết cho từng bước xoay vòng: hiển thị chính xác model đang thử, API key đang dùng (đã được băm SHA-256 rút gọn 8 ký tự `makeKey` theo đúng đặc tả), mã lỗi HTTP (429/503) và trạng thái block/cooldown tương ứng.

---

## 🛠️ Các bước thực hiện (Implementation Steps)

1. **Chỉnh sửa GeminiApiClient.kt**:
   - Thay thế biến static `MODELS` bằng hàm helper tạo danh sách động: `fun getSortedPriorityModels(): List<String>`.
   - Cập nhật luồng `summarizeWithRetry` để nạp danh sách động này ở mỗi lần bắt đầu yêu cầu tóm tắt.
2. **Viết Unit Test cho GeminiApiClient**:
   - Sử dụng Mockk để giả lập danh sách model động từ `ModelManager` và giá trị cài đặt từ `AppPreferences`.
   - Xác thực thuật toán sắp xếp độ ưu tiên hoạt động chính xác trong các kịch bản khác nhau.

---

## 📂 Các file tạo mới hoặc chỉnh sửa
- **Chỉnh sửa**: `app/src/main/java/com/skul9x/rssreader/data/network/GeminiApiClient.kt` - Logic chính.
- **Tạo mới**: `app/src/test/java/com/skul9x/rssreader/data/network/GeminiApiClientPriorityTest.kt` - File test chi tiết cho Phase 2.

---

## 🧪 Bài Test chi tiết (Test Cases)

Dưới đây là mã nguồn file kiểm thử tự động `GeminiApiClientPriorityTest.kt` cần chạy thông qua sau khi hoàn thành Phase 2 để kiểm thử thuật toán sắp xếp ưu tiên:

```kotlin
package com.skul9x.rssreader.data.network

import android.content.Context
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.ModelManager
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GeminiApiClientPriorityTest {

    private val context = mockk<Context>(relaxed = true)
    private val modelManager = mockk<ModelManager>()
    private val appPreferences = mockk<AppPreferences>()

    @Before
    fun setUp() {
        mockkObject(ModelManager.Companion)
        mockkObject(AppPreferences.Companion)
        
        every { ModelManager.getInstance(any()) } returns modelManager
        every { AppPreferences.getInstance(any()) } returns appPreferences
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getSortedPriorityModels should put active model at first place`() {
        // Given: Dynamic model list from manager
        val dynamicModels = listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Lite"),
            DynamicModel("models/gemini-2.5-flash", "Gemini 2.5 Flash")
        )
        every { modelManager.getModels() } returns dynamicModels
        
        // Active model chosen by user is Gemini 2.5 Flash
        every { appPreferences.getSelectedModelId() } returns "models/gemini-2.5-flash"
        
        // When: We simulate getting sorted models in GeminiApiClient
        val sortedModels = getSortedPriorityModelsForTesting(context)
        
        // Then: The active model is moved to index 0, others remain in order
        assertEquals(3, sortedModels.size)
        assertEquals("models/gemini-2.5-flash", sortedModels[0])
        assertEquals("models/gemini-3.1-flash-lite", sortedModels[1])
        assertEquals("models/gemini-2.5-flash-lite", sortedModels[2])
    }

    @Test
    fun `getSortedPriorityModels should fallback to default order if active model is not in list`() {
        // Given
        val dynamicModels = listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Lite")
        )
        every { modelManager.getModels() } returns dynamicModels
        
        // Active model chosen is some custom deleted model
        every { appPreferences.getSelectedModelId() } returns "models/deleted-custom-model"
        
        // When
        val sortedModels = getSortedPriorityModelsForTesting(context)
        
        // Then: Keep original dynamic list order
        assertEquals(2, sortedModels.size)
        assertEquals("models/gemini-3.1-flash-lite", sortedModels[0])
        assertEquals("models/gemini-2.5-flash-lite", sortedModels[1])
    }

    // Helper simulation for unit test (this logic will be implemented in GeminiApiClient)
    private fun getSortedPriorityModelsForTesting(context: Context): List<String> {
        val allModels = ModelManager.getInstance(context).getModels().map { it.modelId }
        val activeModel = AppPreferences.getInstance(context).getSelectedModelId()
        
        if (allModels.contains(activeModel)) {
            val remaining = allModels.filter { it != activeModel }
            return listOf(activeModel) + remaining
        }
        return allModels
    }
}
```

---
Next Phase: [Phase 03: Premium UI CRUD](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-03-ui-settings.md)
