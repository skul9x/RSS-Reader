# Phase 02: Repository Logic
Status: ✅ Complete
Dependencies: phase-01

## Objective
Áp dụng các DAO mới vào Repository và sửa lỗi Race Condition khi đánh dấu đã đọc.

## Requirements
### Functional
- [x] Sửa thuật toán `filterOutReadNews` để dùng query DB thay vì `getAllReadIds()`.
- [x] Sửa lỗi crash chặn list rỗng ở `getRandomNewsFromCache`.
- [x] Thêm cache RAM tạm thời (`memoryPendingReadIds`) để tránh Race Condition khi người dùng refresh nhanh.

## Implementation Steps
1. [x] Mở file `RssRepository.kt`
2. [x] Sửa hàm `filterOutReadNews` để truyền danh sách ID xuống DB kiểm tra.
3. [x] Ở hàm `getRandomNewsFromCache`, thêm logic `if (excludedInSession.isEmpty())` để gọi DAO an toàn.
4. [x] Khai báo `memoryPendingReadIds`. Thêm ID vào đây trong hàm `markNewsAsRead`. Kiểm tra ID này trong lúc filter tin tức.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/repository/RssRepository.kt`

---
Next Phase: phase-03-parser.md
