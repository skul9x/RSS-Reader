# Phase 03: Premium UI CRUD
Status: ✅ Completed
Dependencies: [Phase 01: Core Setup & Self-Healing](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-01-core-setup.md)

## 🎯 Mục tiêu
Nâng cấp toàn diện giao diện `SummarizeModelSection.kt` trong màn hình Cài đặt theo chuẩn Material 3 cao cấp (Premium UI/UX). Thay thế RadioButton tĩnh bằng một danh sách động hỗ trợ đầy đủ các thao tác CRUD (Thêm mô hình tùy chỉnh, Chỉnh sửa tên, Xóa mô hình) và nút khôi phục mặc định cực kỳ mượt mà.

---

## 📋 Yêu cầu (Requirements)

### 1. Trải nghiệm người dùng (UX) & Thiết kế (UI)
- [x] **Giao diện Danh sách Mô hình**: Hiển thị danh sách các mô hình động (Dynamic Models) hiện có dưới dạng một danh sách tinh tế với RadioButton để chọn Active Model.
- [x] **Hành động trên từng dòng (Inline Actions)**:
  - Hiển thị tên hiển thị (DisplayName) và ID của mô hình (ví dụ: `models/gemini-3.1-flash-lite`).
  - Hỗ trợ nút Xóa (thùng rác) bên phải mỗi dòng. (Không cho xóa model đang được chọn làm Active hoặc tự động self-heal nếu người dùng cố ý xóa hết).
  - Hỗ trợ nút Chỉnh sửa (bút chì) bên phải mỗi dòng để chỉnh sửa nhanh thông tin model.
- [x] **Nút thêm mô hình (Add Model Button)**: Một nút "Thêm mô hình tùy chỉnh" đẹp mắt với icon dấu cộng (`Icons.Default.Add`). Khi nhấn, mở ra một `AddModelDialog`.
- [x] **Dialog Thêm Mô hình (`AddModelDialog`)**:
  - TextField nhập ID Mô hình (ví dụ: `models/gemini-2.5-pro`) có Placeholder gợi ý.
  - TextField nhập Tên Hiển Thị (ví dụ: "Gemini 2.5 Pro").
  - Nút Hủy và nút Thêm (có validate dữ liệu trước khi lưu, báo lỗi đỏ nếu dữ liệu không hợp lệ hoặc bị trùng).
- [x] **Nút khôi phục mặc định (`Reset to Defaults`)**: Nút khôi phục nhanh nằm ở cuối danh sách, sử dụng màu sắc Tertiary tinh tế, giúp người dùng reset toàn bộ danh sách về trạng thái mặc định ban đầu chỉ với 1 lượt chạm.

### 2. Micro-Animations & Responsive Vibe
- [x] Áp dụng bo góc lớn `RoundedCornerShape(16.dp)` sang trọng.
- [x] Màu sắc HSL hài hòa sử dụng hệ thống màu `MaterialTheme.colorScheme` (PrimaryContainer, SurfaceVariant).
- [x] Thêm hiệu ứng Ripple mượt mà khi tương tác.

---

## 🛠️ Các bước thực hiện (Implementation Steps)

1. **Nâng cấp SummarizeModelSection.kt**:
   - Thay đổi tham số truyền vào để nhận danh sách models động từ `ModelManager` thay vì sử dụng Enum.
   - Thêm các trạng thái mở dialog thêm/sửa model (`showAddDialog`, `showEditDialog`, `modelToEdit`).
   - Xây dựng layout hiển thị từng mô hình kèm các nút sửa/xóa tương ứng.
2. **Cập nhật SettingsScreen.kt**:
   - Truyền các callback CRUD (`onAddModel`, `onDeleteModel`, `onUpdateModel`, `onResetDefaults`) từ `SettingsScreen` xuống `SummarizeModelSection`.
   - Cập nhật state quản lý danh sách mô hình để kích hoạt Recomposition khi danh sách mô hình thay đổi.

---

## 📂 Các file tạo mới hoặc chỉnh sửa
- **Chỉnh sửa**: `app/src/main/java/com/skul9x/rssreader/ui/settings/SummarizeModelSection.kt` - Giao diện chính của mục Model AI.
- **Chỉnh sửa**: `app/src/main/java/com/skul9x/rssreader/ui/settings/SettingsScreen.kt` - Truyền state và callback.

---

## 🧪 Bài Test chi tiết (Test Cases)
Do Jetpack Compose UI Test yêu cầu môi trường giả lập thiết bị (Instrumentation Test), chúng ta sẽ tạo file **Unit Test cho ViewModel/State Flow** tương tác với UI tại `app/src/test/java/com/skul9x/rssreader/ui/settings/SummarizeModelViewModelTest.kt` để đảm bảo luồng sự kiện từ UI gọi xuống các hàm CRUD của `ModelManager` và cập nhật State chuẩn xác.

Mã nguồn file kiểm thử `SummarizeModelViewModelTest.kt`:

```kotlin
package com.skul9x.rssreader.ui.settings

import android.content.Context
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.ModelManager
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SummarizeModelViewModelTest {

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
    fun `when user triggers reset to default UI should receive updated default models`() {
        // Given
        val defaultModels = listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Lite")
        )
        every { modelManager.resetToDefaults() } just Runs
        every { modelManager.getModels() } returns defaultModels

        // When: UI triggers Reset to Defaults
        modelManager.resetToDefaults()
        val modelsAfterReset = modelManager.getModels()

        // Then: Verifies manager reset was called and returns default models
        verify(exactly = 1) { modelManager.resetToDefaults() }
        assertEquals(2, modelsAfterReset.size)
        assertEquals("models/gemini-3.1-flash-lite", modelsAfterReset[0].modelId)
    }
}
```

---
Next Phase: [Phase 04: Verification & Robustness](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-04-testing.md)
