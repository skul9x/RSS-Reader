# Phase 07: UI Integration & Conflict Resolution
Status: ✅ Complete  
Dependencies: Phase 06

## Objective
Integrate sync logic into news feed UI and verify conflict resolution.

## Requirements
### Functional
- [ ] Filter read news from feed
- [ ] Mark news as read on scroll/click
- [ ] Test conflict resolution with 2 devices

### Non-Functional
- [ ] UX: Filtering < 100ms
- [ ] Stability: No UI flicker during sync

## Implementation Steps
1. [ ] Modify `NewsRepository.kt` to filter read items
2. [ ] Update `MainViewModel.kt` - add `markAsRead()` function
3. [ ] Update `NewsScreen.kt` - mark on scroll using `DisposableEffect`
4. [ ] Add read count in Settings (optional)
5. [ ] Test conflict: Same timestamp → Smartphone wins
6. [ ] Test conflict: Different timestamp → Earlier wins

## Files to Create/Modify
- `NewsRepository.kt` - MODIFY
- `MainViewModel.kt` - MODIFY
- `NewsScreen.kt` - MODIFY

## Test Criteria
- [ ] Read on smartphone → Hidden on android box
- [ ] Same timestamp conflict → Smartphone deviceType wins
- [ ] Offline read → Syncs when online

---
Next Phase: [phase-08-testing.md](file:///C:/Users/Admin/Desktop/Test_code/RSS-Reader-main/plans/260120-2238-read-status-sync/phase-08-testing.md)
