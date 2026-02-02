# Phase 02: Verification
Status: ✅ Complete

## Objective
Đảm bảo cơ chế WakeLock mới hoạt động đúng.

## Verification Checklist
### Manual Testing
1.  **Test 1: Normal Reading (Short)**
    - Chọn 1 tin ngắn (< 1 phút).
    - Đọc xong -> Check Log -> Thấy "WakeLock released".
    - **Pass:** App không giữ lock sau khi đọc xong.

2.  **Test 2: Long Reading (Renew Logic)**
    - Tạm thời sửa code: Set `WAKELOCK_TIMEOUT = 30s`, `RENEW_INTERVAL = 15s`.
    - Đọc 1 tin dài (> 1 phút).
    - Quan sát Logcat:
        - T=0s: "Acquired WakeLock (30s)"
        - T=15s: "Extending WakeLock (30s)"
        - T=30s: "Extending WakeLock (30s)"
    - **Pass:** Lock được gia hạn tự động.

3.  **Test 3: Interrupt/Stop**
    - Đang đọc -> Bấm Stop / Pause.
    - Check Log -> Thấy "WakeLock released" ngay lập tức.
    - Quan sát Job Monitor (Android Studio): `wakeLockJob` phải bị cancelled.

## Files to Modify
- Không sửa code phase này.

## Conclusion
Đã hoàn thành Verification guide. Hệ thống sẵn sàng cho test thực tế.
