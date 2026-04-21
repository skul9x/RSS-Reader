# Phase 02: Fail-Fast Network Errors
Status: ✅ Completed
Dependencies: Không (độc lập với Phase 01)

## Objective
Nhận diện lỗi mạng (`IOException`) để dừng vòng lặp Retry ngay lập tức, thay vì thử lần lượt tất cả API Key khi bản chất lỗi không liên quan đến API Key.

## Vấn đề hiện tại

### Code lỗi (GeminiApiClient.kt — hàm `translateToVietnamese`, dòng 222-225):
```kotlin
} catch (e: Exception) {
    Log.e(TAG, "Translation exception: $model | API ${keyIndex + 1}", e)
    continue  // ❌ Mù quáng thử API Key tiếp theo
}
```

### Code lỗi (GeminiApiClient.kt — hàm `translateTitleBatch`, dòng 327-329):
```kotlin
} catch (e: Exception) {
    continue  // ❌ Còn không có log
}
```

### Code lỗi (GeminiApiClient.kt — hàm `tryTranslate`, dòng 469-479):
```kotlin
} catch (e: Exception) {
    // ...
    ApiResult.Error(e.message ?: "Translation error")  
    // ❌ Không phân biệt IOException vs Exception khác
}
```

**Hậu quả thực tế:**
- Nếu có 10 API Key + mất WiFi → gọi lỗi 10 lần liên tiếp
- Mỗi lần timeout mặc định ~30s → tổng 300s (5 phút) treo app vô ích
- Tốn pin, CPU, làm nóng thiết bị

## Implementation Steps

### 1. [MODIFY] GeminiModels.kt — Thêm `NetworkError` vào ApiResult
- [x] Thêm `object NetworkError : ApiResult()` vào sealed class `ApiResult`

**Code mới:**
```kotlin
sealed class ApiResult {
    data class Success(val text: String) : ApiResult()
    object QuotaExceeded : ApiResult()
    object ServerBusy : ApiResult()
    object ModelNotFound : ApiResult()
    object NetworkError : ApiResult()  // NEW: Lỗi mạng, dừng retry ngay
    data class Error(val message: String) : ApiResult()
}
```

### 2. [MODIFY] GeminiApiClient.kt — Hàm `tryTranslate()` phân biệt IOException
- [x] Trong khối `catch`, kiểm tra nếu exception là `IOException` → trả về `ApiResult.NetworkError`
- [x] Các exception khác vẫn trả về `ApiResult.Error` như cũ

**Code mới (thay thế dòng 467-479):**
```kotlin
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: java.io.IOException) {
    // NEW: Network error - don't retry with other keys
    val totalDuration = System.currentTimeMillis() - startTime
    ActivityLogger.log(
        eventType = "TRANSLATE_ERROR",
        url = "",
        message = "Network error (${totalDuration}ms)",
        details = "${e.javaClass.simpleName}: ${e.message}",
        isError = true
    )
    ApiResult.NetworkError
} catch (e: Exception) {
    val totalDuration = System.currentTimeMillis() - startTime
    ActivityLogger.log(
        eventType = "TRANSLATE_ERROR",
        url = "",
        message = "Exception (${totalDuration}ms)",
        details = "${e.javaClass.simpleName}: ${e.message}",
        isError = true
    )
    ApiResult.Error(e.message ?: "Translation error")
}
```

### 3. [MODIFY] GeminiApiClient.kt — Hàm `translateToVietnamese()` xử lý NetworkError
- [x] Trong `when (result)`, thêm case `ApiResult.NetworkError` → **break toàn bộ vòng lặp** (cả model lẫn API key)
- [x] Log rõ ràng: "Mất mạng, dừng retry"

**Code mới (thêm vào block `when` ở dòng 191-221):**
```kotlin
is ApiResult.NetworkError -> {
    Log.w(TAG, "Network error detected, stopping all retries")
    ActivityLogger.log(
        eventType = "TRANSLATE_ABORT",
        url = "",
        message = "Dừng dịch - Lỗi mạng",
        details = "Model: $model | API ${keyIndex + 1}",
        isError = true
    )
    return@withContext text  // Trả về text gốc ngay lập tức
}
```

### 4. [MODIFY] GeminiApiClient.kt — Hàm `translateTitleBatch()` xử lý NetworkError
- [x] Tương tự, thêm case `ApiResult.NetworkError` → return `emptyMap()` ngay lập tức
- [x] Thêm log vào khối `catch` hiện tại (đang trống)

**Code mới (thêm vào block `when` ở dòng 283-326):**
```kotlin
is ApiResult.NetworkError -> {
    Log.w(TAG, "Network error, aborting batch translation")
    return@withContext emptyMap()
}
```

**Và sửa khối catch trống (dòng 327-329):**
```kotlin
} catch (e: java.io.IOException) {
    Log.e(TAG, "Batch translation network error, aborting", e)
    return@withContext emptyMap()
} catch (e: Exception) {
    Log.e(TAG, "Batch translation exception: $model | API ${keyIndex + 1}", e)
    continue
}
```

### 5. [BONUS] Áp dụng tương tự cho `tryGenerateContent()` (summarize)
- [x] Hàm `tryGenerateContent()` (dòng 697-700) cũng có cùng vấn đề
- [x] Thêm `catch (e: IOException)` trả về `ApiResult.NetworkError`
- [x] Hàm `summarizeWithRetry()` xử lý `ApiResult.NetworkError` → return luôn

## Files to Create/Modify
| File | Action | Chi tiết |
|------|--------|----------|
| `GeminiModels.kt` | MODIFY | Thêm `NetworkError` vào `ApiResult` sealed class |
| `GeminiApiClient.kt` | MODIFY | Phân biệt IOException, thêm early return cho NetworkError |

## Test Criteria
- [x] Khi mất mạng: app chỉ thử 1 lần rồi dừng (không quét hết API keys)
- [x] Khi API key hết quota (429): vẫn thử key tiếp theo bình thường
- [x] Khi server bận (503): vẫn thử key tiếp theo bình thường
- [x] Log hiển thị rõ ràng "Network error, stopping all retries"
- [x] Build thành công

## Rủi ro & Mitigation
| Rủi ro | Xác suất | Giải pháp |
|--------|----------|-----------|
| Lỗi timeout (SocketTimeoutException) cũng là IOException | Cao | SocketTimeoutException kế thừa IOException → tự động bắt đúng |
| Mạng flaky (lúc có lúc không) | Trung bình | Chấp nhận: 1 lần thử là đủ. Lần refresh tiếp sẽ thử lại |

---
Previous Phase: [phase-01-json-mode.md](./phase-01-json-mode.md)
Next Phase: [phase-03-batch-fallback.md](./phase-03-batch-fallback.md)
