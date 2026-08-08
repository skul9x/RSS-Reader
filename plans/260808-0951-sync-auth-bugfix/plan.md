# Plan: Fix Sync-Auth Bugs (BatchQueueManager Auth Guard + Singleton Re-init)
Created: 2026-08-08 09:51
Status: 🟡 In Progress

## Overview
Fix two confirmed bugs in the Firebase sync system:

1. **Bug 1 (Phase 02):** After sign-out → sign-in, `RssApplication._localSyncRepository` and `_batchQueueManager` remain null. New ViewModels/components get null references and silently skip sync.
2. **Bug 2 (Phase 01):** `BatchQueueManager.doFlush()` calls `FirestoreSyncRepository.uploadBatch()` without checking auth state. When user is not signed in, this throws `IllegalStateException("Not signed in")` and pollutes `firebase_logs` with error entries every 10 reads or every app-background event.

## Root Cause Analysis

### Bug 2 (simpler, fix first)
- `SyncCoordinator.performFullSync()` correctly checks `isUserSignedIn()` before doing work.
- `BatchQueueManager.doFlush()` bypasses this check and calls `uploadBatch()` directly.
- `uploadBatch()` → `getUserCollection()` → throws if `auth.currentUser == null`.

### Bug 1 (more complex)
- `RssApplication.initializeSyncComponents()` runs once in `onCreate()`.
- `onUserSignOut()` nullifies all singleton INSTANCE fields AND `_localSyncRepository` / `_batchQueueManager`.
- After sign-in, nothing re-creates these. `getLocalSyncRepository()` returns null permanently (within the same process).

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Auth Guard in BatchQueueManager & FirestoreSyncRepository | ⬜ Pending | 0% |
| 02 | Sync Re-initialization After Sign-In | ⬜ Pending | 0% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
