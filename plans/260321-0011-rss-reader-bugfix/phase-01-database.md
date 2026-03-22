# Phase 01: Database Optimization
Status: ✅ Complete

## Objective
Sửa lỗi Crash SQLite với list rỗng và vấn đề tải toàn bộ DB lên RAM để filter (N+1 memory).

## Requirements
### Functional
- [x] Thêm SQL Query kiểm tra ID đã đọc dưới DB thay vì load lên RAM.
- [x] Xử lý logic lọc ID trong ngày `todayStart` (nếu cần).
- [x] Thêm SQL Query thay thế cú pháp `NOT IN ()` thành query không chứa biến `excludedIds`.

## Implementation Steps
1. [x] Mở file `ReadNewsDao.kt`
2. [x] Thêm query `getAlreadyReadIds(newsIds: List<String>)`.
3. [x] Mở file `CachedNewsDao.kt`
4. [x] Viết query `getUnreadNewsIdsFromFeedsNoExclude` KHÔNG có tham số `excludedIds`.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/local/ReadNewsDao.kt`
- `app/src/main/java/com/skul9x/rssreader/data/local/CachedNewsDao.kt`

---
Next Phase: phase-02-repository.md
