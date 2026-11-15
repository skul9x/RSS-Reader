# Phase 02: Client Engine & Fallback Pipeline
Status: ✅ Completed
Dependencies: Phase 01

## Objective
Update `GeminiApiClient` translation methods (`translateToVietnamese`, `translateTitleBatch`, and helper `tryTranslate`) to use `models/gemini-flash-lite-latest` as the primary translation model with a prioritized fallback chain, leveraging the updated translation request builder with system instructions and bounded token limits.

## Scope
- Update `translationModels` list in `translateToVietnamese` and `translateTitleBatch` to place `"models/gemini-flash-lite-latest"` as the primary model.
- Add resilient fallback models: `"models/gemini-3.5-flash-lite"`, `"models/gemini-3.1-flash-lite"`, `"models/gemini-2.5-flash-lite"`.
- Separate translation system instructions from user content in `GeminiPrompts.kt` and `GeminiApiClient.kt`.
- Update `tryTranslate` to pass `systemInstruction`, custom `maxOutputTokens`, and delegate to `GeminiResponseHelper.buildTranslationRequestBody`.
- Preserve existing failover logic: try active model across all valid API keys first, skip blocked keys (429/503), then fallback to next model in chain.

## Requirements
### Functional
- `translateToVietnamese(text: String)`:
  - System instruction: `"Bạn là chuyên gia dịch thuật tin tức sang tiếng Việt. Dịch chính xác, tự nhiên, ngắn gọn. TUYỆT ĐỐI không thêm giải thích hay câu chào."`
  - User prompt: `"Text: <title>"`
  - `maxOutputTokens = 128`
- `translateTitleBatch(titles: Map<String, String>)`:
  - System instruction: `"Bạn là chuyên gia dịch thuật. Dịch các giá trị tiêu đề trong JSON object sau sang tiếng Việt. Giữ nguyên toàn bộ ID làm keys. Chỉ dịch phần text của values. Trả về đúng định dạng JSON object."`
  - User prompt: JSON string of `titles` mapping
  - `maxOutputTokens = 384`
  - `useJsonMode = true`
- If all API keys fail with 429 for `models/gemini-flash-lite-latest`, execution immediately proceeds to `models/gemini-3.5-flash-lite`, and so on.

### Non-Functional
- **Resilience:** If network drops or rate limit hits, do not crash; fallback seamlessly or return original title as safety guarantee.
- **Observability:** `ActivityLogger` must record `models/gemini-flash-lite-latest` in `TRANSLATE_START`, `TRANSLATE_HTTP`, and fallback events.

## Implementation Steps
1. In `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiPrompts.kt`:
   - Add constants or helper functions for translation system instructions:
     - `TRANSLATION_SYSTEM_INSTRUCTION`
     - `BATCH_TRANSLATION_SYSTEM_INSTRUCTION`
   - Refactor `buildTranslationPrompt` to provide clean user text input without prompt bloat.
2. In `app/src/main/java/com/skul9x/rssreader/data/network/GeminiApiClient.kt`:
   - Update `translationModels` list in both `translateToVietnamese` and `translateTitleBatch`:
     ```kotlin
     val translationModels = listOf(
         "models/gemini-flash-lite-latest",
         "models/gemini-3.5-flash-lite",
         "models/gemini-3.1-flash-lite",
         "models/gemini-2.5-flash-lite"
     )
     ```
   - Update `tryTranslate`:
     ```kotlin
     private suspend fun tryTranslate(
         apiKey: String,
         model: String,
         prompt: String,
         systemInstruction: String = "",
         maxOutputTokens: Int = 128,
         useJsonMode: Boolean = false
     ): ApiResult
     ```
   - Wire `tryTranslate` to call `GeminiResponseHelper.buildTranslationRequestBody`.
   - Update `translateTitleBatch` to request `maxOutputTokens = 384`.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiPrompts.kt` - Add dedicated translation system instructions and user prompt formatters.
- `app/src/main/java/com/skul9x/rssreader/data/network/GeminiApiClient.kt` - Update translation model priority list, pass system instructions and token limits.

## Dependencies
- Phase 01 changes in `GeminiResponseHelper.kt`.
- Existing `ModelQuotaManager.kt` for 429/503 cooldown handling.

## Constraints
- Single title translation must continue to return plain text string (cleaned of prefixes like "Bản dịch:").
- Batch title translation must continue to return `Map<String, String>` mapping original ID to translated title.
- Existing `translateTitleBatchWithFallback` chunking logic (15 titles/chunk) remains intact.

## Acceptance Criteria
- [x] `translationModels` starts with `"models/gemini-flash-lite-latest"`.
- [x] Fallback models include `"models/gemini-3.5-flash-lite"`, `"models/gemini-3.1-flash-lite"`, and `"models/gemini-2.5-flash-lite"`.
- [x] System instructions are separated from the text payload in both single and batch translation requests.
- [x] Quota exhaustion (429) triggers model fallback across the defined list.
- [x] Network errors abort cleanly without infinite retry loops.

## Comprehensive File-Based Test
- **Test File:** `app/src/test/java/com/skul9x/rssreader/data/network/GeminiFlashLiteTranslationTest.kt`
- **Description:** Verifies end-to-end translation dispatch using `models/gemini-flash-lite-latest`, proper system instruction delivery via mock OkHttpClient, and fallback behavior when `gemini-flash-lite-latest` encounters 429 quota exhaustion.
- **Key Test Cases:**
  1. `testSingleTranslation_usesFlashLiteLatestAndSystemInstruction`: Confirms HTTP request URL targets `models/gemini-flash-lite-latest:generateContent` with system instruction present in JSON body.
  2. `testBatchTranslation_usesFlashLiteLatestWithJsonMode`: Confirms batch translation sends 15 items to `models/gemini-flash-lite-latest` and parses returned JSON map.
  3. `testTranslationFallbackOnQuota429`: Mocks 429 for `models/gemini-flash-lite-latest` and verifies automatic failover to the next available model in `translationModels`.

