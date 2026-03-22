# Phase 02Status: ✅ Complete
Dependencies: phase-01-core-fetch.md

## Objective
Khắc phục nguy cơ tràn bộ nhớ RAM (OOM Memory Leak) khi nhân bản cây DOM Document, thêm khối `return` bị thiếu của Custom Selector, và sắp xếp lại luồng dự phòng (fallback).

## Requirements
### Functional
- [x] Xoá hoàn toàn phương thức `doc.clone()` trong tất cả logic parse.
- [x] Bổ sung khối `return` vào nhánh `Custom Selector` chạy thành công.
- [x] Điều chỉnh lại thứ tự dự phòng: test `Regex` trước, chốt cuối bằng `Jsoup` trong Generic.

### Non-Functional
- [x] Giảm đáng kể lượng RAM tiêu thụ khi parse nội dung, chặn hiện tượng crash máy yếu.
- [x] Hỗ trợ logic User overrides (Custom Selector) đáng tin cậy.

## Implementation Steps
1. [x] Mở `ContentExtractorRegistry.kt`: Thêm câu lệnh `return cleanedResult` vào trong block `try-catch` của phần `customSelector`.
2. [x] Trong `ContentExtractorRegistry.kt`: Đưa đoạn logic test `genericExtractor.extractByRegex(html)` lên trước `genericExtractor.extractByJsoup(doc, url)`.
3. [x] Quét toàn bộ project tìm hàm `doc.clone()` và thay thế bằng việc query cục bộ, lấy đoạn thẻ `Element` rồi dùng `HtmlCleaner` xử lý string html nội vi rác thay vì loại bỏ các node trên bản DOM copy.

## Files Modified
- `app/src/main/java/com/skul9x/rssreader/data/network/ContentExtractorRegistry.kt` - [Sửa luồng fallback và vá điểm mù `return`]
- `extractors/*.kt` (12 files) - [Dọn dẹp `doc.clone()` nguyên nhân gây tốn RAM]

## Test Results
- [x] Gradle Build SUCCESSFUL (`assembleDebug`).
- [x] Verified build fails when calling non-existent `cleanHtml` (fixed by swapping to `clean`).

---
Next Phase: phase-03-extractor-enhance.md
