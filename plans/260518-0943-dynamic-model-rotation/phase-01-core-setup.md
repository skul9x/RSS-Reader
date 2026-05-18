# Phase 01: Core Setup & Self-Healing
Status: ✅ Completed
Dependencies: None

## 🎯 Mục tiêu
Xây dựng lớp `ModelManager.kt` đóng vai trò quản lý danh sách mô hình động thông qua bộ lưu trữ bền vững `SharedPreferences` và cài đặt cơ chế tự phục hồi (Self-Healing) khi danh sách bị rỗng, tuân thủ chặt chẽ Mục 5 của đặc tả `rotation.md`.

---

## 📋 Yêu cầu (Requirements)

### 1. Chức năng (Functional)
- [x] Định nghĩa data class `DynamicModel(val modelId: String, val displayName: String)` để biểu diễn mô hình động.
- [x] Cấu hình SharedPreferences bền vững có tên file `"dynamic_models"`.
- [x] Thiết lập danh sách mặc định (Hardcoded Fallback List) gồm 4 mô hình:
  1. `models/gemini-3.1-flash-lite` - "Gemini 3.1 Flash Lite"
  2. `models/gemini-2.5-flash-lite` - "Gemini 2.5 Flash Lite"
  3. `models/gemini-3-flash-preview` - "Gemini 3 Flash Preview"
  4. `models/gemini-2.5-flash` - "Gemini 2.5 Flash"
- [x] Cung cấp các hàm CRUD cơ bản:
  - `getModels(): List<DynamicModel>`: Lấy danh sách mô hình. Nếu trống/chưa khởi tạo -> tự nạp danh sách mặc định (**Self-Healing**).
  - `addModel(modelId: String, displayName: String): Boolean`: Thêm mô hình mới (Validate định dạng: bắt đầu bằng `models/`, độ dài > 10, không trùng lặp `modelId`).
  - `removeModel(modelId: String): Boolean`: Xóa mô hình theo ID. Nếu sau khi xóa danh sách trống -> kích hoạt **Self-Healing** nạp lại mặc định.
  - `updateModel(oldModelId: String, newModelId: String, newDisplayName: String): Boolean`: Cập nhật thông tin mô hình hiện tại.
  - `resetToDefaults()`: Ép khôi phục danh sách mặc định.

### 2. Phi chức năng (Non-Functional)
- [x] **Thread-Safety**: Sử dụng `Mutex` để bảo vệ các thao tác đọc ghi song song trên bộ nhớ tạm và SharedPreferences.
- [x] **Error-Tolerance**: Đọc ghi JSON an toàn thông qua `kotlinx.serialization` có try-catch tránh crash ứng dụng.

---

## 🛠️ Các bước thực hiện (Implementation Steps)

1. [x] **Tạo file data class**: Tạo `DynamicModel.kt` trong thư mục `data/network/gemini/`.
2. [x] **Xây dựng ModelManager**: Tạo `ModelManager.kt` trong thư mục `data/local/` (hoặc `data/network/gemini/` để gần gũi với logic Model).
3. [x] **Cập nhật AppPreferences**: Cập nhật phương thức lấy model đang được chọn để tương thích với kiểu String/DynamicModel mới (vì trước đây ta lưu enum `SummarizeModel`). Chúng ta sẽ chuyển đổi việc lưu trữ Selected Model từ Enum name sang Model ID (String) để hỗ trợ các model động người dùng tự thêm.
4. [x] **Viết Unit Test cho ModelManager**: Kiểm tra toàn bộ logic CRUD và Self-healing.

---

## 📂 Các file tạo mới hoặc chỉnh sửa
- **Tạo mới**: `app/src/main/java/com/skul9x/rssreader/data/network/gemini/DynamicModel.kt` - Biểu diễn model.
- **Tạo mới**: `app/src/main/java/com/skul9x/rssreader/data/local/ModelManager.kt` - Bộ quản lý mô hình động.
- **Chỉnh sửa**: `app/src/main/java/com/skul9x/rssreader/data/local/AppPreferences.kt` - Đổi từ lưu enum sang lưu String `modelId`.
- **Tạo mới**: `app/src/test/java/com/skul9x/rssreader/data/local/ModelManagerTest.kt` - File test chi tiết cho Phase 1.

---

## 🧪 Bài Test chi tiết (Test Cases)

Dưới đây là mã nguồn file kiểm thử tự động `ModelManagerTest.kt` cần được chạy và thông qua sau khi hoàn thành Phase 1:

```kotlin
package com.skul9x.rssreader.data.local

import android.content.Context
import android.content.SharedPreferences
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModelManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val prefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    
    private lateinit var modelManager: ModelManager

    @Before
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor
        every { prefsEditor.remove(any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        
        // Reset singleton
        val instanceField = ModelManager::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)
        
        modelManager = ModelManager.getInstance(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getModels should trigger self-healing and return defaults if storage is empty`() = runBlocking {
        // Given: preferences return null (no dynamic models saved)
        every { sharedPrefs.getString("saved_models", null) } returns null
        
        // When
        val models = modelManager.getModels()
        
        // Then: SharedPreferences should be saved with default list
        verify { prefsEditor.putString("saved_models", any()) }
        assertEquals(4, models.size)
        assertEquals("models/gemini-3.1-flash-lite", models[0].modelId)
    }

    @Test
    fun `addModel should validate modelId format and append new model`() = runBlocking {
        // Given
        every { sharedPrefs.getString("saved_models", null) } returns null
        
        // When: Add invalid format model
        val invalidAdded = modelManager.addModel("gemini-invalid", "Invalid Name")
        // When: Add valid model
        val validAdded = modelManager.addModel("models/gemini-2.5-pro", "Gemini 2.5 Pro")
        
        // Then
        assertFalse(invalidAdded)
        assertTrue(validAdded)
        
        val models = modelManager.getModels()
        assertEquals(5, models.size)
        assertEquals("models/gemini-2.5-pro", models.last().modelId)
    }

    @Test
    fun `removeModel should execute self-healing if empty after deletion`() = runBlocking {
        // Given: Storage contains only one custom model
        val singleModelJson = """[{"modelId":"models/custom-model","displayName":"Custom"}]"""
        every { sharedPrefs.getString("saved_models", null) } returns singleModelJson
        
        // When: delete that only model
        val deleted = modelManager.removeModel("models/custom-model")
        
        // Then: deleted is true, list size is reset to defaults (4)
        assertTrue(deleted)
        val models = modelManager.getModels()
        assertEquals(4, models.size)
        assertEquals("models/gemini-3.1-flash-lite", models[0].modelId)
    }
}
```

---
Next Phase: [Phase 02: Client Integration & Priority](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-02-client-integration.md)
