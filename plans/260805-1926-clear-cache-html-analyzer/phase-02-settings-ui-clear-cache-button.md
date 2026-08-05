# Phase 02: Settings UI Clear Cache Button & Dialog
Status: ✅ Completed
Dependencies: Phase 01

## Objective
Add a user interface element in `SettingsScreen.kt` for "Clear Cache" with a confirmation AlertDialog to prevent accidental data loss.

## Requirements
### Functional
- Add a new `SettingsItem` entry in `SettingsScreen.kt` titled "Xóa bộ nhớ tạm (Cache)".
- Show a confirmation `AlertDialog` when clicked with "Hủy" (Cancel) and "Xóa" (Delete) actions.
- Execute `viewModel.clearCache()` upon confirmation and show a Toast notification `"Đã xóa bộ nhớ tạm thành công!"`.

### Non-Functional
- Match Material Design 3 styling consistent with other Settings items.
- Ensure state persistence for dialog visibility during recomposition.

## Implementation Steps
1. Modify `SettingsScreen.kt` located at [SettingsScreen.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/main/java/com/skul9x/rssreader/ui/settings/SettingsScreen.kt).
2. Declare state `var showClearCacheDialog by remember { mutableStateOf(false) }`.
3. Add `SettingsItem` for Clear Cache under data management section.
4. Render `AlertDialog` when `showClearCacheDialog` is true.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/ui/settings/SettingsScreen.kt` - Add Clear Cache item and AlertDialog.

## Test Criteria & File-Based Test Plan
### Automated & Build Verification
Command: `./gradlew test`
- Ensure code compiles cleanly and existing tests pass.

---
Next Phase: [Phase 03: HTML Analyzer Auto-Refresh on Selector Save](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260805-1926-clear-cache-html-analyzer/phase-03-html-analyzer-auto-refresh.md)
