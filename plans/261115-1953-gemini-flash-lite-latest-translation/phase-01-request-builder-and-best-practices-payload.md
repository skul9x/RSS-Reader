# Phase 01: Request Builder & Best Practices Payload
Status: ✅ Completed
Dependencies: None

## Objective
Enhance `GeminiResponseHelper` to generate Google AI compliant translation request payloads featuring dedicated top-level `system_instruction`, low deterministic temperature (`0.1`), strictly capped `maxOutputTokens`, and zero/minimal thinking overhead for the `gemini-flash-lite-latest` model family.

## Scope
- Modify `GeminiResponseHelper.buildRequestBody` or add a dedicated `buildTranslationRequestBody` function to format `system_instruction` at the top level of the JSON payload.
- Configure `temperature = 0.1` and `topP = 0.95` for precise, deterministic translations.
- Apply `maxOutputTokens = 128` for single title translation and `384` for batch title translation (15 items).
- Ensure `thinkingConfig` automatically applies `thinkingLevel = "minimal"` when encountering `latest` or `gemini-3.x` models, and `thinkingBudget = 0` for `gemini-2.5`.
- Maintain full backward compatibility for summarization and HTML class suggestion requests.

## Requirements
### Functional
- `buildTranslationRequestBody` must output valid JSON parsable by `kotlinx.serialization.json.Json`.
- The JSON output must contain a top-level `system_instruction` object:
  ```json
  "system_instruction": {
    "parts": [{ "text": "..." }]
  }
  ```
- The user content must be inside `contents` with role/parts structure.
- When `useJsonMode = true`, `generationConfig.responseMimeType` must be set to `"application/json"`.

### Non-Functional
- **Payload size:** Eliminate prompt bloating by keeping system instructions concise and tokens strictly capped.
- **Latency:** Low token limit (`128` - `384`) minimizes Time To First Token (TTFT) and total generation time on mobile networks.
- **Thread Safety:** Stateless helper object executing pure functions.

## Implementation Steps
1. In `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiResponseHelper.kt`:
   - Add function `buildTranslationRequestBody(systemInstruction: String, userPrompt: String, model: String, maxOutputTokens: Int, useJsonMode: Boolean)` (or update `buildRequestBody` with optional `systemInstruction`).
   - Implement `system_instruction` JSON structure matching Google AI v1beta specifications:
     ```kotlin
     if (!systemInstruction.isNullOrBlank()) {
         putJsonObject("system_instruction") {
             putJsonArray("parts") {
                 addJsonObject { put("text", systemInstruction) }
             }
         }
     }
     ```
   - Ensure temperature defaults to `0.1` for translation.
   - Wire `thinkingConfig` logic: if `model.contains("gemini-2.5")` use `thinkingBudget = 0`, else if `model.contains("latest") || model.contains("gemini-3")` use `thinkingLevel = "minimal"`.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiResponseHelper.kt` - Modify to support `system_instruction`, translation parameters, and thinking configs.

## Dependencies
- `kotlinx.serialization.json.*` (existing in project).

## Constraints
- Must not break existing callers of `buildRequestBody` used in news summarization or HTML extraction.
- Must not require external libraries outside current project classpath.

## Acceptance Criteria
- [x] `buildTranslationRequestBody` includes `system_instruction` at top level when provided.
- [x] `temperature` in `generationConfig` is set to `0.1`.
- [x] For model `models/gemini-flash-lite-latest`, `thinkingLevel` is set to `"minimal"` under `thinkingConfig`.
- [x] When `useJsonMode = true`, `responseMimeType` is `"application/json"`.
- [x] When `useJsonMode = false`, `responseMimeType` is omitted.
- [x] Existing summarization calls to `buildRequestBody` continue to function identically with existing behavior.

## Comprehensive File-Based Test
- **Test File:** `app/src/test/java/com/skul9x/rssreader/data/network/GeminiTranslationPayloadTest.kt`
- **Description:** Verifies that `buildTranslationRequestBody` generates exact JSON structure according to Google AI specs for single translation and batch translation under `models/gemini-flash-lite-latest`.
- **Key Test Cases:**
  1. `testSingleTranslationPayload_structureAndTokens`: Validates presence of top-level `system_instruction`, user prompt in `contents`, `temperature == 0.1`, `maxOutputTokens == 128`, and `thinkingLevel == "minimal"`.
  2. `testBatchTranslationPayload_jsonMode`: Validates `responseMimeType == "application/json"`, `maxOutputTokens == 384`, and `useJsonMode == true`.
  3. `testFallbackModelThinkingBudget`: Validates that passing `models/gemini-2.5-flash-lite` configures `thinkingBudget == 0`.

