# Phase 04: Kiểm thử & Xoay màn hình
Status: ✅ Complete
Dependencies: Phase 01, 02, 03

## Objective
Kiểm tra toàn bộ tính năng continuous reading trên Landscape, bao gồm xoay màn hình và các edge cases.

## Test Scenarios

### 1. Kích hoạt Continuous Mode (Landscape)
- [x] Long-press nút "Đọc 5 tin" → Toast + haptic + icon đổi sang ∞
- [x] Banner "Đọc liên tục đang bật" xuất hiện
- [x] Progress bar di chuyển
- [x] Tin tức tự động được đọc lần lượt

### 2. Dừng Continuous Mode
- [x] Tap nút ∞ khi đang continuous → dừng
- [x] Banner biến mất
- [x] Icon quay lại PlaylistPlay
- [x] Màu nút quay lại primaryContainer

### 3. Xoay màn hình (State Persistence)
- [x] Bật continuous ở Landscape → xoay sang Portrait → vẫn hiện continuous state
- [x] Bật continuous ở Portrait → xoay sang Landscape → vẫn hiện continuous state
- [x] TTS không bị gián đoạn khi xoay (service chạy nền)

### 4. Edge Cases
- [x] Long-press khi không có tin → không crash
- [x] Long-press khi đang readAll → không kích hoạt continuous
- [x] Continuous + refresh → không conflict

## Verification Method
**Manual testing trên thiết bị/emulator:**
1. Build app: `./gradlew assembleDebug`
2. Cài lên thiết bị
3. Chuyển sang chế độ Landscape
4. Thực hiện từng test scenario ở trên

## Notes
- `NewsReaderService` chạy foreground → xoay màn hình không ảnh hưởng TTS
- `uiState` được collect từ service state → tự động sync khi xoay
- `collectAsState()` đã được dùng ở root composable → OK

---
