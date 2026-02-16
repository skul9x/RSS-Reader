# Phase 01: Implementation
Status: ✅ Complete

## Objective
Thay thế logic WakeLock cứng nhắc (30p) bằng logic thông minh, tiết kiệm pin hơn.

## Requirements
### Functional
- [x] Giảm timeout `wakeLock.acquire()` xuống **10 phút**.
- [x] Tạo `Keep-Alive Coroutine`:
    - Chạy song song khi bắt đầu đọc.
    - Loop mỗi **5 phút**: Check `isReading`.
    - Nếu đang đọc: `wakeLock.acquire(10 mins)` (Gia hạn thêm).
    - Nếu đã dừng: Thoát loop.

### Non-Functional
- [x] Safety: Đảm bảo WakeLock luôn được release khi Service destroy hoặc Stop reading.
- [x] Log: Ghi log `DebugLogger` mỗi lần gia hạn để theo dõi.

## Implementation Steps
1.  [x] **Modify** `NewsReaderService.kt`:
    - Thêm `private var wakeLockJob: Job? = null`.
    - Viết hàm `startWakeLockMonitor()`: chứa logic loop gia hạn.
    - Sửa `startReadingSingle` & `startReadingAll`: Gọi `startWakeLockMonitor()` thay vì `wakeLock.acquire()`.
    - Sửa `stopReading`: Cancel `wakeLockJob` và Release lock.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/service/NewsReaderService.kt`

## Test Criteria
- [ ] **Unit Test (logic):** Khó test trực tiếp Service wake lock.
- [ ] **Manual Test:**
    - Cài đặt thời gian gia hạn xuống 10s (để test).
    - Bắt đầu đọc tin dài.
    - Quan sát Logcat: Thấy thông báo "Extending WakeLock" xuất hiện định kỳ.
    - Stop đọc -> Log báo "WakeLock Released".
