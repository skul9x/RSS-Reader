# Features Documentation

## 1. Cross-Device Sync (Read Status)

**Status:** ✅ Complete (8/8 Phases)
**Goal:** Synchronize read news items between smartphone and Android box devices.

### Architecture
- **Local:** Room Database (`read_news` table) + DataStore (`sync_preferences`)
- **Remote:** Firestore (`users/{userId}/readNews/{newsId}`)
- **Auth:** Firebase Auth (Google Sign-In)
- **Background:** WorkManager (`SyncWorker`) + LifecycleObserver (`AppLifecycleObserver`)

### Data Models
- **ReadNewsItem:**
  - `newsId` (String): Unique ID
  - `readAt` (Long): Timestamp
  - `deviceType` (String): "smartphone" or "androidbox"
  - `syncStatus` (Enum): PENDING, SYNCED, FAILED

### Sync Strategy
- **Trigger:**
  - Batch count (10 items) via `BatchQueueManager`
  - Timer (Periodic Work)
  - App Background (Lifecycle)
- **Conflict Resolution:** Earliest timestamp wins (logic in `SyncCoordinator`)

### Current Progress
- [x] Firebase Project Setup
- [x] Google Sign-In Implementation
- [x] Local Database Schema (with Sync fields)
- [x] Firestore Integration
- [x] Background Workers
- [x] UI Integration
- [x] Conflict Resolution

---

## 2. Text-to-Speech (TTS)
*To be documented...*

## 3. Article Content Extraction
*To be documented...*
## 4. Continuous Reading Mode
**Status:** ✅ Complete
**Goal:** Provide a hands-free, automated news consumption experience (e.g., while driving).

### Logic
- **Trigger:** Long-press (400ms) on "Đọc 5 tin" button.
- **Duration:** 30 minutes (auto-releasing WakeLock).
- **Cycle:**
  1. Read 5 items sequentially (Intro -> Summarize -> Speak).
  2. Mark each item as `read` in DB.
  3. Fetch 5 new random/fresh items via `refreshVozAndGetRandomNews`.
  4. Batch-translate titles via Gemini.
  5. Sync new list to UI.
  6. Repeat until timeout or manual stop.

### Visual Style
- **Icon:** `PlaylistPlay` (Standard) -> `AllInclusive` (Continuous).
- **Colors:** Primary/Cyan (Standard) -> Error/Amber (Continuous).
- **Border:** Amber/Orange pulsing gradient to highlight active reading item.
