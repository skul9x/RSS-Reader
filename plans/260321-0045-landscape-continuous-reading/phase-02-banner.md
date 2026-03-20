# Phase 02: ContinuousReadingBanner cho Landscape
Status: ✅ Complete
Dependencies: Phase 01

## Objective
Hiển thị banner thông báo trạng thái "Đang đọc liên tục" ở cột phải (content area) của giao diện Landscape, giúp user biết rõ app đang ở chế độ continuous.

## Phân tích hiện trạng
- Landscape layout chia 2 phần: News list (LazyRow phía trên) + BottomControls (phía dưới)
- Hiện tại không có visual feedback nào cho continuous mode ngoài blinking animation trên nút

## Implementation Steps
1. [x] Tạo Composable `ContinuousReadingBanner` (tái sử dụng từ plan-landscape.txt)
2. [x] Chèn Banner phía trên NewsList khi `isContinuousMode == true`
3. [x] Thêm animation `AnimatedVisibility` cho banner (fadeIn/fadeOut)

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/ui/main/MainScreen.kt` - Thêm ContinuousReadingBanner

## Thay đổi cụ thể

### 1. Thêm Composable `ContinuousReadingBanner` (file mới hoặc cùng file)
```kotlin
@Composable
private fun ContinuousReadingBanner(readingProgress: Float) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = "Continuous Mode",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đọc liên tục đang bật",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { readingProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
            )
        }
    }
}
```

### 2. Chèn vào MainScreen layout (trước NewsList, line ~126)
```kotlin
// Continuous reading banner
AnimatedVisibility(
    visible = uiState.isContinuousMode,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically()
) {
    ContinuousReadingBanner(readingProgress = uiState.readingProgress)
}
```

## Test Criteria
- [x] Khi continuous mode bật → banner hiển thị với animation
- [x] Progress bar cập nhật theo `readingProgress`
- [x] Khi stop → banner biến mất với animation
- [x] Banner không chiếm quá nhiều không gian

---
Next Phase: [Phase 03 - Auto-Scroll](./phase-03-autoscroll.md)
