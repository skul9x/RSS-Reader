# Phase 02: Refactor Sync Logic
Status: ✅ Complete
Dependencies: Phase 01

## Objective
Refactor `SyncCoordinator.mergeWithLocal` to use batch fetching instead of N+1 queries.

## Requirements
- [x] Verify `remoteItems` is not empty before querying.
- [x] Extract all `newsId` from `remoteItems`.
- [x] Fetch all matching local items using `localRepo.getByIds(ids)`.
- [x] Create a Map for O(1) lookup: `localItemsMap = localItems.associateBy { it.newsId }`.
- [x] Refactor the loop to use `localItemsMap[remote.newsId]`.

## Implementation Steps
1. [x] Edit `app/src/main/java/com/skul9x/rssreader/data/sync/SyncCoordinator.kt`
   - Locate `mergeWithLocal` function.
   - Replace `forEach` loop with batch loading pattern.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/sync/SyncCoordinator.kt`

---
Next Phase: [Verification](./phase-03-verification.md)
