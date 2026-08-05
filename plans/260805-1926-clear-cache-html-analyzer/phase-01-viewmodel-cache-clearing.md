# Phase 01: ViewModel Cache Clearing Logic
Status: ✅ Completed
Dependencies: None

## Objective
Expose a `clearCache()` function in `MainViewModel` that delegates to `RssRepository.clearCache()` to purge cached RSS news items from the local SQLite database (`cached_news` table).

## Requirements
### Functional
- Add `clearCache()` method to `MainViewModel`.
- Ensure execution occurs asynchronously within `viewModelScope` on IO/background context.
- Provide a unit test confirming `rssRepository.clearCache()` is executed when `clearCache()` is invoked on `MainViewModel`.

### Non-Functional
- Prevent UI thread blocking during database deletion.
- Maintain error safety and proper coroutine scope cancellation handling.

## Implementation Steps
1. Open `MainViewModel.kt` located at [MainViewModel.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/main/java/com/skul9x/rssreader/ui/main/MainViewModel.kt).
2. Add function `fun clearCache()`:
   ```kotlin
   fun clearCache() {
       viewModelScope.launch {
           rssRepository.clearCache()
       }
   }
   ```
3. Create or update unit test file `MainViewModelTest.kt` to verify `clearCache()` invocation.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/ui/main/MainViewModel.kt` - Add `clearCache()` method.
- `app/src/test/java/com/skul9x/rssreader/ui/main/MainViewModelTest.kt` - Add unit test for cache clearing.

## Test Criteria & File-Based Test Plan
### Automated Unit Test
File: `app/src/test/java/com/skul9x/rssreader/ui/main/MainViewModelTest.kt`
- Test case: `clearCache_callsRepositoryClearCache()`
- Description: Verifies that calling `viewModel.clearCache()` invokes `rssRepository.clearCache()` using MockK/coroutines test framework.

---
Next Phase: [Phase 02: Settings UI Clear Cache Button & Dialog](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260805-1926-clear-cache-html-analyzer/phase-02-settings-ui-clear-cache-button.md)
