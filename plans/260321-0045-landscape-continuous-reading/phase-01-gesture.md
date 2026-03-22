# Phase 01: Long-Press Gesture cho nút "Đọc 5 tin" (Landscape)
Status: ✅ Complete
Dependencies: Không

## Objective
Thêm long-press gesture vào nút "Đọc 5 tin" trong `BottomControls` ở layout Landscape, giống cách đã làm ở Portrait.

## Phân tích hiện trạng

### Portrait (`MainScreenPortrait.kt`, line 157-202):
- Dùng `Box` + `pointerInput` + `detectTapGestures`
- `onTap`: nếu đang đọc → stop, ngược lại → `readAllNewsSummaries()`
- `onLongPress`: haptic feedback + `startContinuousReading()` + Toast
- Đổi icon: `AllInclusive` khi continuous, `PlaylistPlay` khi bình thường
- Đổi background color: `error` khi continuous mode

### Landscape (`MainScreen.kt`, line 418-438):
- Dùng `Button` với `onClick` đơn giản
- Chỉ toggle giữa readAll/stop
- **KHÔNG** có long-press, **KHÔNG** hỗ trợ continuous mode

## Implementation Steps
1. [x] Thay thế `Button` composable bằng `Box` + `pointerInput` + `detectTapGestures` (giống Portrait)
2. [x] Thêm `onLongPress` handler gọi `viewModel.startContinuousReading()`
3. [x] Thêm haptic feedback + Toast khi kích hoạt
4. [x] Đổi icon và background color theo `isContinuousMode` state
5. [x] Truyền thêm callbacks: `onContinuousLongClick`, `onStopClick` vào `BottomControls`

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/ui/main/MainScreen.kt` - Thêm long-press vào BottomControls

## Thay đổi cụ thể

### 1. Cập nhật tham số `BottomControls` (line 352)
Thêm 2 params mới:
```kotlin
isContinuousMode: Boolean,
onContinuousLongClick: () -> Unit,
```

### 2. Thay thế nút "Đọc 5 tin" (line 418-438)
**Từ:**
```kotlin
Button(
    onClick = { if (isReadingAll) onStop() else onReadAll5() },
    ...
)
```

**Thành (copy pattern từ Portrait):**
```kotlin
Box(
    modifier = Modifier
        .size(56.dp)
        .alpha(if (isButtonEnabled) alpha else 0.4f)
        .background(
            color = when {
                isContinuousMode -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                isReadingAll -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            shape = RoundedCornerShape(16.dp)
        )
        .pointerInput(isReadingAll, isContinuousMode, isButtonEnabled) {
            detectTapGestures(
                onTap = {
                    if (!isButtonEnabled) return@detectTapGestures
                    if (isReadingAll || isContinuousMode) onStop()
                    else onReadAll5()
                },
                onLongPress = {
                    if (!isButtonEnabled) return@detectTapGestures
                    if (!isReadingAll && !isContinuousMode) {
                        // haptic + toast
                        onContinuousLongClick()
                    }
                }
            )
        },
    contentAlignment = Alignment.Center
) {
    Icon(
        imageVector = if (isContinuousMode) Icons.Default.AllInclusive else Icons.Default.PlaylistPlay,
        ...
    )
}
```

### 3. Cập nhật caller ở `MainScreen` (line 169-193)
Truyền thêm params:
```kotlin
isContinuousMode = uiState.isContinuousMode,
onContinuousLongClick = {
    viewModel.startContinuousReading()
},
```

## Test Criteria
- [x] Nhấn giữ nút "Đọc 5 tin" ở Landscape → bật continuous mode
- [x] Nút đổi icon sang ∞ (AllInclusive)
- [x] Nút đổi màu sang đỏ
- [x] Tap khi đang continuous → dừng
- [x] Haptic feedback + Toast hiển thị

---
Next Phase: [Phase 02 - Continuous Banner](./phase-02-banner.md)
