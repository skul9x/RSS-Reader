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
