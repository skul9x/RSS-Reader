# Phase 01: Chuyển sang JSON Mode chuẩn
Status: ✅ Completed (2026-04-21)
Dependencies: Không

## Objective
Loại bỏ hoàn toàn việc dùng Regex để "mò" JSON từ response text. Thay vào đó, sử dụng tính năng `responseMimeType = "application/json"` có sẵn của Gemini REST API để đảm bảo response luôn là JSON hợp lệ 100%.

## Vấn đề hiện tại

### Code lỗi (GeminiApiClient.kt, dòng 286-293):
```kotlin
// Strip markdown code blocks if present (```json ... ```)
val cleaned = result.text
    .replace(Regex("```json\\s*"), "")
    .replace(Regex("```\\s*"), "")
    .trim()

// Find outermost JSON object using regex
val jsonMatch = Regex("\\{[\\s\\S]*\\}").find(cleaned)
val fullJson = jsonMatch?.value ?: "{}"
```

**Tại sao lỗi:**
- Gemini có thể trả về text giải thích + JSON → Regex lấy sai phần
- Nếu có nhiều `{}` lồng nhau hoặc JSON chứa `}` trong string value → Regex match sai
- Markdown code block (```` ```json ````) không phải lúc nào cũng xuất hiện

### Code lỗi (GeminiResponseHelper.kt, dòng 15-59):
```kotlin
fun buildRequestBody(...): String {
    val json = buildJsonObject {
        // ...
        putJsonObject("generationConfig") {
            put("temperature", temperature)
            put("maxOutputTokens", maxOutputTokens)
            // ❌ THIẾU: responseMimeType = "application/json"
        }
    }
}
```

## Implementation Steps

### 1. [MODIFY] GeminiResponseHelper.kt — Thêm JSON Mode parameter
- [ ] Thêm parameter `useJsonMode: Boolean = false` vào hàm `buildRequestBody()`
- [ ] Khi `useJsonMode = true`, thêm `put("responseMimeType", "application/json")` vào `generationConfig`
- [ ] **KHÔNG** thay đổi default behavior (các hàm khác như summarize, suggestClass vẫn hoạt động bình thường)

**Code mới:**
```kotlin
fun buildRequestBody(
    prompt: String, 
    model: String = "", 
    temperature: Double = 0.7, 
    maxOutputTokens: Int = 4096,
    useJsonMode: Boolean = false  // NEW
): String {
    val json = buildJsonObject {
        // ... contents giữ nguyên ...
        putJsonObject("generationConfig") {
            put("temperature", temperature)
            put("maxOutputTokens", maxOutputTokens)
            put("topP", 0.95)
            
            // NEW: JSON Mode - bắt buộc Gemini trả JSON chuẩn
            if (useJsonMode) {
                put("responseMimeType", "application/json")
            }
            
            // Thinking config giữ nguyên
            if (model.contains("gemini-3") || model.contains("latest")) {
                putJsonObject("thinkingConfig") {
                    put("thinkingLevel", "minimal")
                }
            }
        }
        // safetySettings giữ nguyên
    }
    return json.toString()
}
```

### 2. [MODIFY] GeminiApiClient.kt — Hàm `translateTitleBatch()` dùng JSON Mode
- [ ] Khi gọi `GeminiResponseHelper.buildRequestBody()` cho batch translation, truyền `useJsonMode = true`
- [ ] **Loại bỏ hoàn toàn** Regex parsing (`Regex("\\{[\\s\\S]*\\}")`), thay bằng `Json.parseToJsonElement()` trực tiếp
- [ ] Loại bỏ `replace(Regex("```json..."))` vì JSON Mode không bao giờ wrap trong markdown

**Code mới cho phần parsing (thay thế dòng 284-305):**
```kotlin
is ApiResult.Success -> {
    try {
        // JSON Mode đảm bảo response là JSON chuẩn, parse trực tiếp
        val parsed = Json.parseToJsonElement(result.text.trim()).jsonObject
        val resultMap = parsed.entries.associate { (k, v) ->
            k to v.jsonPrimitive.content
        }
        Log.d(TAG, "Batch translation success: ${resultMap.size} items")
        return@withContext resultMap
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse batch response: ${result.text.take(200)}", e)
        continue
    }
}
```

### 3. [MODIFY] GeminiApiClient.kt — Hàm `tryTranslate()` truyền `useJsonMode`
- [ ] Trong `translateTitleBatch()`, khi gọi `buildRequestBody()`, truyền `useJsonMode = true`
- [ ] Trong `translateToVietnamese()` (single), **KHÔNG** dùng `useJsonMode` (vì response là plain text, không phải JSON)

**Lưu ý quan trọng:** Chỉ batch translation cần JSON Mode. Single translation trả về text thuần túy.

## Files to Create/Modify
| File | Action | Chi tiết |
|------|--------|----------|
| `GeminiResponseHelper.kt` | MODIFY | Thêm param `useJsonMode`, thêm `responseMimeType` |
| `GeminiApiClient.kt` | MODIFY | Dùng `useJsonMode=true` cho batch, loại bỏ Regex parsing |

## Test Criteria
- [ ] Batch translation trả về JSON hợp lệ mà không cần Regex
- [ ] Single translation vẫn hoạt động bình thường (plain text)
- [ ] Summarize, suggestClass không bị ảnh hưởng (default `useJsonMode = false`)
- [ ] Build thành công, không có compilation error

## Rủi ro & Mitigation
| Rủi ro | Xác suất | Giải pháp |
|--------|----------|-----------|
| Model cũ không hỗ trợ `responseMimeType` | Thấp (code dùng v1beta) | Tất cả model trong MODELS đều hỗ trợ |
| JSON Mode thay đổi cách thinking hoạt động | Trung bình | Test kỹ với `gemini-flash-lite-latest` |

---
Next Phase: [phase-02-fail-fast.md](./phase-02-fail-fast.md)
