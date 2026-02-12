━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 HANDOVER DOCUMENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📍 Đang làm: Firebase Sync Subsystem Fixes
🔢 Đến bước: Hoàn thành Audit & Fix Bugs (High/Medium)

✅ ĐÃ XONG:
   - [BUG 3] Fixed Polling Loop (High)
   - [BUG 4] Unique Work for Immediate Sync (High)
   - [BUG 8] Sign-out State Cleanup (High)
   - [BUG 2] Batch DB Writes (Medium)
   - [BUG 5] applicationScope for Background Flush (Medium)
   - [BUG 6] Timestamp Precision (>= 1ms buffer) (Medium)
   - [BUG 10] Recovery Logic Simplification (Medium)
   - All fixes verified with `SyncCoordinatorTest`.

⏳ CÒN LẠI:
   - [BUG 1] Remove redundant PENDING check in SQL (Low)
   - [UI] Thêm Sync Indicator (xoay xoay) khi app đang đẩy data (Feature)

🔧 QUYẾT ĐỊNH QUAN TRỌNG:
   - Ưu tiên "Earliest Wins" cho Read Status (Conflict Resolution).
   - Chunking Firestore upload/delete ở mức 400 items.
   - Hashing API keys (SHA-256) trước khi lưu.

⚠️ LƯU Ý CHO SESSION SAU:
   - Mọi logic đồng bộ giờ đây tập trung tại `SyncCoordinator`.
   - `SyncWorker` chỉ gọi `performFullSync()` (WorkManager lo retry).
   - Đăng xuất sẽ reset toàn bộ Singletons via `RssApplication.onUserSignOut`.

📁 FILES QUAN TRỌNG:
   - `SyncCoordinator.kt`: Trái tim của hệ thống sync.
   - `ReadNewsDao.kt`: Chứa Batch Upsert logic.
   - `.brain/session.json`: Progress hiện tại.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 Đã lưu! Để tiếp tục: Gõ /recap
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
