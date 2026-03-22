# Phase 02: Logic & UI Connection
Status: ⬜ Pending
Dependencies: Phase 01

## Objective
Wire up the UI "Refresh" button to the "Flash Refresh" logic (Voz Priority) and ensure `force = true` is passed correctly.

## Requirements
### Functional
- [ ] `MainScreen.kt`: Call `viewModel.refreshNews(force = true)` on Refresh button click.
- [ ] `MainScreenPortrait.kt`: Call `viewModel.refreshNews(force = true)` on Refresh button click.
- [ ] `MainViewModel.kt`: Verify `refreshNews` logic properly routes `force=true` to `refreshVozAndGetRandomNews`.

### Non-Functional
- [ ] UX: Ensure Refresh still shows loading indicator.

## Implementation Steps
1. [ ] Edit `MainScreen.kt`: Update `onRefresh` callback.
2. [ ] Edit `MainScreenPortrait.kt`: Update `onRefresh` callback.
3. [ ] Verify `MainViewModel.kt` logic (already analyzed, likely good, but double check).

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/ui/main/MainScreen.kt`
- `app/src/main/java/com/skul9x/rssreader/ui/main/MainScreenPortrait.kt`

## Test Criteria
- [ ] Click Refresh -> Logcat shows "Flash Refresh" or "Fetching Voz".
- [ ] UI shows new items mixed with cache.

---
Next Phase: [Phase 03](phase-03-verification.md)
