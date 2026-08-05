# Phase 03: HTML Analyzer Auto-Refresh on Selector Save
Status: ✅ Completed
Dependencies: None

## Objective
Automatically re-analyze article HTML and switch to the "Extracted" tab when a user saves a Custom CSS Selector in `HtmlAnalyzerScreen.kt`, avoiding confusion caused by stale RAM state.

## Requirements
### Functional
- When "Lưu Selector" button is clicked in the Custom Selector tab:
  1. Save selector to `CustomSelectorManager`.
  2. Clear current extraction states (`resultTitle`, `resultContent`).
  3. Automatically re-trigger `contentFetcher.fetchArticleWithTitle(urlInput)` asynchronously.
  4. Automatically set `selectedTab = 0` ("Extracted" tab) so the user immediately views newly extracted text.

### Non-Functional
- Handle network errors gracefully and maintain non-blocking UI during re-fetch.
- Debounce repeat clicks during analysis execution.

## Implementation Steps
1. Modify `HtmlAnalyzerScreen.kt` located at [HtmlAnalyzerScreen.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/main/java/com/skul9x/rssreader/ui/settings/HtmlAnalyzerScreen.kt).
2. Update the "Lưu Selector" `onClick` handler to trigger background extraction and switch `selectedTab` to 0.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/ui/settings/HtmlAnalyzerScreen.kt` - Implement auto re-analyze workflow upon selector save.

## Test Criteria & File-Based Test Plan
### Automated Unit & Integration Tests
Command: `./gradlew test`
- Run existing extraction and registry unit tests to ensure custom selector extraction remains functional.
