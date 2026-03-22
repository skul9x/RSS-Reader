# Phase 04: ViewModel & UI Fixes
Status: ✅ Complete
Dependencies: phase-02

## Objective
Giải quyết bug nút Next kẹt vòng lặp và chế độ Đọc Tất Cả không lưu lịch sử.

## Requirements
### Functional
- [x] Lắng nghe `serviceState.currentIndex` trong chế độ Continuous và tự động gọi `markNewsAsRead`.
- [x] Logic vòng lặp Next: khi hết lệnh chưa đọc trên màn hình, ép tải loạt 5 tin mới thay vì loop lại.

## Implementation Steps
1. [x] Mở file `MainViewModel.kt`
2. [x] Khai báo biến `markedReadInService` theo dõi các ID bài báo đã tự động đọc trong phiên.
3. [x] Ở hàm `observeServiceState`, lấy tin đang đọc, và nếu ID chưa bị lưu thì đánh dấu gọi Repository `markNewsAsRead`.
4. [x] Ở `selectRandomOtherNews`, phần `availableIndices.isEmpty()`, gọi `refreshNews()` (nhớ test nếu cần `reset selection`).

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/ui/main/MainViewModel.kt`

---
Next Phase: Hoàn tất
