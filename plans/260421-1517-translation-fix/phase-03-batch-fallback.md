# Phase 03: Batch-to-Single Fallback
Status: ⬜ Pending
Dependencies: Phase 01 (JSON Mode), Phase 02 (Fail-Fast)

## Objective
Khi dịch Batch thất bại (do Safety Filter chặn 1 tiêu đề "nhạy cảm" làm hỏng cả mẻ), hệ thống tự động "xé lẻ" để dịch từng tiêu đề riêng biệt. Đảm bảo 4 tiêu đề "sạch" vẫn được dịch dù 1 tiêu đề bị chặn.

## Vấn đề hiện tại

### Luồng hiện tại (GeminiApiClient.kt):
```
translateTitleBatch(5 titles) 
  → Gemini nhận 5 titles
  → 1 title chứa nội dung nhạy cảm  
  → Safety Filter chặn TOÀN BỘ response
  → Return emptyMap()
  → ❌ 4 title sạch cũng không được dịch
```

### Code hiện tại (MainViewModel.kt, dòng 192-230):
```kotlin
val translatedMap = geminiClient.translateTitleBatch(titlesMap)

if (translatedMap.isNotEmpty()) {
    // Update UI
} else {
    // ❌ Chỉ clear isTranslating, KHÔNG thử lại
}
```

**Hậu quả:** Người dùng thấy tiêu đề tiếng Anh mãi mãi, trong khi chỉ cần 1 tiêu đề "nhạy cảm" làm hỏng cả batch.

## Implementation Steps

### 1. [MODIFY] GeminiApiClient.kt — Thêm hàm `translateTitleBatchWithFallback()`
- [ ] Tạo hàm public mới bọc logic Batch + Single fallback
- [ ] Gọi `translateTitleBatch()` trước
- [ ] Nếu batch thành công nhưng thiếu 1 số tiêu đề → dịch lẻ phần thiếu  
- [ ] Nếu batch fail hoàn toàn (emptyMap) → dịch lẻ từng cái

**Code mới:**
```kotlin
/**
 * Translate titles with automatic batch-to-single fallback.
 * 
 * Strategy:
 * 1. Try batch translation first (1 API call for N titles)
 * 2. If batch returns partial results, translate missing titles individually
 * 3. If batch fails completely, fall back to translating each title individually
 *
 * This handles the case where Safety Filters block the entire batch
 * because of a single "sensitive" title.
 *
 * @param titles Map of ID -> Original Title
 * @return Map of ID -> Translated Title (best effort, may be partial)
 */
suspend fun translateTitleBatchWithFallback(titles: Map<String, String>): Map<String, String> {
    if (titles.isEmpty()) return emptyMap()
    
    // Step 1: Try batch first (most efficient)
    val batchResult = translateTitleBatch(titles)
    
    // Step 2: Check if all titles were translated
    val missingTitles = titles.filter { (id, _) -> id !in batchResult }
    
    if (missingTitles.isEmpty()) {
        // Perfect! All titles translated in one batch
        Log.d(TAG, "Batch translation complete: ${batchResult.size}/${titles.size}")
        return batchResult
    }
    
    if (batchResult.isNotEmpty()) {
        Log.d(TAG, "Batch partial: ${batchResult.size}/${titles.size}, falling back for ${missingTitles.size}")
    } else {
        Log.w(TAG, "Batch failed completely, falling back to single translation for ${titles.size} titles")
    }
    
    // Step 3: Translate missing titles individually
    val singleResults = mutableMapOf<String, String>()
    for ((id, title) in missingTitles) {
        try {
            val translated = translateToVietnamese(title)
            // Only add if actually translated (different from original)
            if (translated != title && translated.isNotBlank()) {
                singleResults[id] = translated
                Log.d(TAG, "Single fallback success for ID: $id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Single fallback failed for ID: $id", e)
            // Continue to next title - don't let one failure stop others
        }
    }
    
    // Step 4: Merge batch + single results
    val mergedResult = batchResult.toMutableMap()
    mergedResult.putAll(singleResults)
    
    Log.d(TAG, "Final translation result: ${mergedResult.size}/${titles.size} " +
          "(batch: ${batchResult.size}, single: ${singleResults.size})")
    
    return mergedResult
}
```

### 2. [MODIFY] MainViewModel.kt — Sử dụng hàm mới
- [ ] Thay đổi dòng 195: `translateTitleBatch` → `translateTitleBatchWithFallback`
- [ ] Đây là thay đổi duy nhất ở ViewModel — toàn bộ logic fallback nằm trong GeminiApiClient

**Code thay đổi (dòng 195):**
```diff
- val translatedMap = geminiClient.translateTitleBatch(titlesMap)
+ val translatedMap = geminiClient.translateTitleBatchWithFallback(titlesMap)
```

### 3. [MODIFY] GeminiApiClient.kt — Thêm chunking cho batch lớn
- [ ] Nếu batch > 15 tiêu đề, chia thành các chunk nhỏ (mỗi chunk 10-15 tiêu đề)
- [ ] Điều này tránh prompt quá dài khiến AI "mất trí nhớ" hoặc vượt token limit

**Code mới (thêm vào `translateTitleBatchWithFallback`):**
```kotlin
// Chunk large batches to prevent prompt overflow
val CHUNK_SIZE = 15

if (titles.size > CHUNK_SIZE) {
    Log.d(TAG, "Large batch (${titles.size}), splitting into chunks of $CHUNK_SIZE")
    val allResults = mutableMapOf<String, String>()
    
    titles.entries.chunked(CHUNK_SIZE).forEachIndexed { index, chunk ->
        val chunkMap = chunk.associate { it.key to it.value }
        Log.d(TAG, "Processing chunk ${index + 1}/${(titles.size + CHUNK_SIZE - 1) / CHUNK_SIZE}")
        
        val chunkResult = translateTitleBatch(chunkMap)
        allResults.putAll(chunkResult)
    }
    
    // Fall back for any missing after all chunks
    val stillMissing = titles.filter { (id, _) -> id !in allResults }
    // ... (same single fallback logic as above)
    
    return allResults
}
```

## Tổng quan luồng mới (Flowchart)

```
translateTitleBatchWithFallback(N titles)
│
├─ N > 15? → Chia thành chunks 15 titles
│              ├─ Chunk 1: translateTitleBatch(15)
│              ├─ Chunk 2: translateTitleBatch(15)
│              └─ ...
│
├─ N ≤ 15 → translateTitleBatch(N)
│
├─ Kết quả batch?
│   ├─ Full (N/N) → ✅ Return ngay
│   ├─ Partial (M/N, M > 0) → Dịch lẻ (N-M) cái còn thiếu
│   └─ Failed (0/N) → Dịch lẻ từng cái
│
└─ Merge kết quả → Return
```

## Files to Create/Modify
| File | Action | Chi tiết |
|------|--------|----------|
| `GeminiApiClient.kt` | MODIFY | Thêm `translateTitleBatchWithFallback()`, chunking logic |
| `MainViewModel.kt` | MODIFY | Đổi 1 dòng: gọi hàm mới thay hàm cũ |

## Test Criteria
- [ ] Batch 5 tiêu đề → AI dịch đủ 5 → Return ngay (không fallback)
- [ ] Batch 5 tiêu đề → AI chặn 1 → Dịch lẻ 4 cái còn lại thành công
- [ ] Batch 5 tiêu đề → AI fail hết → Dịch lẻ 5 cái, ít nhất 4 cái thành công
- [ ] Batch 20 tiêu đề → Tự chia 2 chunk (15 + 5)
- [ ] Mất mạng giữa chừng → Fail-fast (Phase 02) dừng, không dịch lẻ
- [ ] Build thành công

## Rủi ro & Mitigation
| Rủi ro | Xác suất | Giải pháp |
|--------|----------|-----------|
| Dịch lẻ tốn nhiều API quota hơn | Cao | Chỉ dịch lẻ khi batch fail — là fallback, không phải default |
| Dịch lẻ chậm hơn batch | Cao | Chấp nhận: chậm mà có kết quả > nhanh mà trắng tay |
| 1 tiêu đề nhạy cảm cũng fail khi dịch lẻ | Trung bình | OK — chỉ 1 cái fail thay vì 5 cái. Đây là kết quả mong muốn |

---
Previous Phase: [phase-02-fail-fast.md](./phase-02-fail-fast.md)
