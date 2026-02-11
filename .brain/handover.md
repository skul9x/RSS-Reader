━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 HANDOVER DOCUMENT - 2026-02-11
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📍 Đang làm: Firebase Cloud-Device Sync
🔢 Đến bước: Hoàn thành fix 4 lỗi Medium (M1, M2, M3, M4)

✅ ĐÃ XONG:
   - Fix **Conflict Resolution**: Đã chuyển sang "Earliest Wins" ✓
   - Fix **Firestore Batch Limit**: Đã thêm chunking 400 items ✓
   - Fix **M1 (Ordering)**: `markAsSynced` chạy ngay sau upload ✓
   - Fix **M2 (Atomic)**: Merge + Timestamp update trong try-catch ✓
   - Fix **M3 (Race)**: BatchQueueManager drain inside mutex ✓
   - Fix **M4 (Guard)**: DAO warnings + documentation ✓
   - Unit Tests: `SyncCoordinatorTest.kt` pass 7/7 cases ✓

⏳ CÒN LẠI (Low Priority):
   - **L1**: `SyncScheduler.triggerImmediateSync` cần dùng unique work name.
   - **L2**: Lưu trạng thái `BatchQueueManager` vào DB bền vững hơn (handle process bị kill).
   - **L3**: `downloadSince` dùng `>=` thay vì `>` để tránh lệch boundary 1ms.
   - **Cleanup**: Dọn các warnings deprecated trong `GeminiApiClient`.

🔧 QUYẾT ĐỊNH QUAN TRỌNG:
   - Ưu tiên ghi trạng thái "Đã Sync" ngay sau khi upload thành công để tránh lãng phí bandwidth nếu bước sau fail.
   - Không update `lastDownloadTimestamp` nếu quá trình merge cục bộ bị lỗi (để lần sau sync lại từ mốc đó).
   - Dùng snapshot queue bên trong Mutex để đảm bảo tính nguyên tử (Atomicity) cho batch processing.

⚠️ LƯU Ý CHO SESSION SAU:
   - Các file sync hiện tại đã ổn định về logic nghiệp vụ chính.
   - Nếu muốn tối ưu thêm, tập trung vào `SyncScheduler.kt` (L1) và boundary query trong `FirestoreSyncRepository.kt` (L3).

📁 FILES QUAN TRỌNG:
   - .brain/brain.json (KI & Patterns)
   - .brain/session.json (Progress)
   - CHANGELOG.md (History)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 Đã lưu! Để tiếp tục: Gõ /recap
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
