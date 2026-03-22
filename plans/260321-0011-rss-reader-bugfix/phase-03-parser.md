# Phase 03: URL Normalization
Status: ✅ Complete
Dependencies: none

## Objective
Loại bỏ rác tracking parameter (`?utm_source`) khỏi URL bài viết để ngăn trùng lặp ID.

## Requirements
### Functional
- [x] Tạo hàm mở rộng (Extension function) chuẩn hóa URL.
- [x] Áp dụng hàm chuần hóa này trước khi khởi tạo ID cho `NewsItem`.

## Implementation Steps
1. [x] Mở file chứa logic Parse RSS (`RssParser.kt` hoặc thư mục Utils thích hợp).
2. [x] Thêm hàm `String.normalizeNewsId()` chặn các param `?utm_`, `&utm_`, `?fbclid`, v.v.
3. [x] Gán chuỗi đã chuẩn hóa làm `id` thay vì raw data từ network.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/RssParser.kt`

---
Next Phase: phase-04-viewmodel.md
