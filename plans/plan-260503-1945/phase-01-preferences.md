# Phase 01: Data Layer & Preferences

## Mục tiêu
Định nghĩa Enum cho các model thực tế và thêm cơ chế lưu trữ lựa chọn vào `AppPreferences`.

## Công việc
- [x] Tạo `enum class SummarizeModel` trong `GeminiModels.kt` với các giá trị:
    - `GEMINI_2_5_FLASH_LITE("models/gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite")`
    - `GEMINI_3_FLASH_PREVIEW("models/gemini-3-flash-preview", "Gemini 3 Flash Preview")`
    - `GEMINI_3_1_FLASH_LITE_PREVIEW("models/gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite Preview")`
    - `GEMINI_2_5_FLASH("models/gemini-2.5-flash", "Gemini 2.5 Flash")`
- [x] Cập nhật `AppPreferences.kt`:
    - Thêm `KEY_SELECTED_SUMMARIZE_MODEL = "selected_summarize_model"`.
    - Thêm hàm `setSelectedSummarizeModel(model: SummarizeModel)`.
    - Thêm hàm `getSelectedSummarizeModel()` (Mặc định trả về `GEMINI_2_5_FLASH_LITE`).

## File liên quan
- `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiModels.kt`
- `app/src/main/java/com/skul9x/rssreader/data/local/AppPreferences.kt`

## Kiểm tra
- [x] Đảm bảo Enum khớp chính xác với string model ID trong `GeminiApiClient.kt`.