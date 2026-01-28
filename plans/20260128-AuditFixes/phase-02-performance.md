# Phase 02: SQL Optimization
Status: ✅ Complete

## Objective
Tăng tốc độ query lấy tin ngẫu nhiên. Thay thế `ORDER BY RANDOM()` (siêu chậm trên bảng lớn) bằng thuật toán ứng dụng (Application-level shuffling).

## Requirements
### Functional
- [x] Loại bỏ hoàn toàn `ORDER BY RANDOM()` trong `CachedNewsDao` (Đã deprecate và thay thế logic gọi).
- [x] Implement thuật toán:
    1. Lấy danh sách ID thỏa mãn điều kiện (feedId, excludedIds...).
    2. Shuffle list ID này trên RAM (Kotlin).
    3. Lấy N items từ DB dựa trên list ID đã shuffle.

## Implementation Steps
1.  [x] **Modify** `CachedNewsDao.kt`:
    - Thêm `getUnreadNewsIdsFromFeeds(...)`: Chỉ lấy cột `id`.
    - Thêm `getNewsByIds(ids: List<String>)`: Lấy full tin theo list ID.
    - Deprecate các hàm `getRandomNews` cũ.
2.  [x] **Modify** `RssRepository.kt`:
    - Viết lại hàm `getRandomNewsFromCache` để phối hợp 2 method trên.
    - Logic: `ids = dao.getUnreadNewsIds...` -> `shuffledIds = ids.shuffled().take(n)` -> `items = dao.getNewsByIds(shuffledIds)`.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/local/CachedNewsDao.kt`
- `app/src/main/java/com/skul9x/rssreader/data/repository/RssRepository.kt`

## Test Criteria
- [x] Review code: Logic shuffling đã được chuyển lên Kotlin layer.
- [ ] Manual Test:
    - Spam nút Refresh liên tục.
    - Quan sát Logs: Thời gian query giảm từ >500ms (dự đoán) xuống <50ms.
    - Đảm bảo tin vẫn hiện ngẫu nhiên, không bị trùng lặp.
