# Phase 03: Auto-Scroll LazyRow khi đọc
Status: ✅ Complete
Dependencies: Phase 01

## Objective
Đảm bảo khi `readingNewsIndex` thay đổi (tin tiếp theo được đọc), `LazyRow` trong Landscape tự động scroll đến item đang đọc.

## Phân tích hiện trạng
- `MainScreen.kt` line 251-258: **Đã có** `LaunchedEffect(readingIndex)` + `listState.animateScrollToItem(readingIndex)`
- Tuy nhiên cần kiểm tra lại khi continuous mode chạy và list thay đổi (refresh batch mới)

## Implementation Steps
1. [x] Verify `LaunchedEffect(readingIndex)` đã hoạt động đúng (đã có ở line 254-258)
2. [x] Kiểm tra khi `newsItems` thay đổi (batch mới) mà `readingIndex` reset về 0 → scroll về đầu

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/ui/main/MainScreen.kt` - Verify/fix auto-scroll logic

## Thay đổi cụ thể

**Kiểm tra code hiện tại (line 254-258):**
```kotlin
// Auto-scroll to reading item
LaunchedEffect(readingIndex) {
    if (readingIndex >= 0) {
        listState.animateScrollToItem(readingIndex)
    }
}
```

→ Code này **đã đủ** cho use case. Khi continuous mode đọc item 0→1→2→3→4, `readingIndex` sẽ thay đổi và LazyRow sẽ scroll theo.

**Có thể cần thêm:** Key theo `newsItems` để reset scroll khi batch mới được load:
```kotlin
LaunchedEffect(readingIndex, newsItems.size) {
    if (readingIndex >= 0 && readingIndex < newsItems.size) {
        listState.animateScrollToItem(readingIndex)
    }
}
```

## Test Criteria
- [x] Khi đọc liên tục, LazyRow tự scroll đến tin đang đọc
- [x] Khi batch mới load (sau 5 tin), scroll reset về tin đầu tiên
- [x] Không bị crash khi readingIndex > newsItems.size

---
Next Phase: [Phase 04 - Testing](./phase-04-testing.md)
