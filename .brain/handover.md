━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 HANDOVER DOCUMENT - 2026-02-11
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📍 Đang làm: Firebase Cloud-Device Sync
🔢 Đến bước: Hoàn thành fix 4 lỗi quan trọng (Bug #1, #2, #3, #4)

✅ ĐÃ XONG:
   - Fix **Conflict Resolution**: Đã chuyển sang "Earliest Wins" ✓
   - Fix **Firestore Batch Limit**: Đã thêm chunking 400 items ✓
   - Fix **Silent Failures**: Repository đã rethrow exception để WorkManager retry ✓
   - Fix **Auth Guard**: Thêm check login trước khi sync ✓
   - Unit Tests: `SyncCoordinatorTest.kt` đã update và pass 100% ✓

⏳ CÒN LẠI:
   - Bug #5: Tối ưu `BatchQueueManager` concurrency (tránh lệch queueSize).
   - Bug #6: One-time sync trong `SyncScheduler` chưa tự động chaining.
   - Bug #7: Download query dùng `>=` gây tải lại data cũ (tối ưu bandwidth).

🔧 QUYẾT ĐỊNH QUAN TRỌNG:
   - Giữ nguyên semantics "đọc bài đầu tiên" (Earliest Wins).
   - Chunk size cố định là 400 để đảm bảo an toàn cho Firestore (limit 500).

⚠️ LƯU Ý CHO SESSION SAU:
   - File `SyncCoordinator.kt` và `FirestoreSyncRepository.kt` là trọng tâm.
   - Các tests nằm trong `com.skul9x.rssreader.data.sync.SyncCoordinatorTest`.

📁 FILES QUAN TRỌNG:
   - .brain/brain.json (KI & Patterns)
   - .brain/session.json (Progress)
   - CHANGELOG.md (History)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 Đã lưu! Để tiếp tục: Gõ /recap
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
