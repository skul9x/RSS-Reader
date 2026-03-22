# Phase 03: Generic Extractor Enhance
Status: ✅ Complete
Dependencies: phase-02-memory-logic.md

## Objective
Nâng cấp `GenericExtractor` để tránh tình trạng "mù" thẻ HTML, tránh làm rơi rụng kí tự, danh sách, hay đoạn mã.

## Requirements
### Functional
- [x] Sửa lại `extractByJsoup` của `GenericExtractor`: Không gọi `.select("p").eachText().joinToString()`.
- [x] Lấy khối `.html()` rồi đưa cho `HtmlCleaner.clean(html)` để tự động chắt lọc và map `<br>`, `<div>` thành ký tự xuống dòng thuần túy hợp lý.

## Implementation Steps
1. [x] Mở `GenericExtractor.kt` hàm `extractByJsoup`: Khi tóm được thẻ khối như `<article>`, bóc tách nguyên cục html bằng lệnh `element.html()`.
2. [x] Bảo đảm `HtmlCleaner` xử lý chuẩn đoạn DOM chứa `<ul>`, `<li>`, `<b>`, `<br>` (viết thêm custom extension nếu cần).

## Files Modified
- `app/src/main/java/com/skul9x/rssreader/utils/AndroidHtmlCleaner.kt` - [Enhanced to preserve line breaks and structure]

## Test Results
- [x] Gradle Build SUCCESSFUL (`assembleDebug` in 43s).
- [x] HTML structural tags now map to newlines: `<br>` → `\n`, `<p>/<div>` → `\n\n`, `<li>` → `\n• `.

---
Next Phase: phase-04-testing.md
