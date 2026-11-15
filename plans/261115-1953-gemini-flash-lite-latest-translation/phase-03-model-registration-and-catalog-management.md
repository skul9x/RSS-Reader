# Phase 03: Model Registration & Catalog Management
Status: ✅ Completed
Dependencies: Phase 01, Phase 02

## Objective
Register `models/gemini-flash-lite-latest` in the app's dynamic model catalog (`ModelManager.kt`, `GeminiModels.kt`), ensuring it passes model ID validation, is available in model lists, and is recognized across preferences and settings screens.

## Scope
- Register `GEMINI_FLASH_LITE_LATEST` in `GeminiModels.kt` with model ID `"models/gemini-flash-lite-latest"` and display name `"Gemini Flash Lite (Latest)"`.
- Add `models/gemini-flash-lite-latest` to the default model list in `ModelManager.kt`.
- Update `AppPreferences.kt` migration / fallback mapping if applicable.
- Ensure model validation rules (`modelId.startsWith("models/")`, `length > 10`) recognize `models/gemini-flash-lite-latest`.
- Verify UI components in `SummarizeModelSection.kt` display and handle the latest model correctly without UI regression.

## Requirements
### Functional
- `GeminiModels.kt` enum must include:
  ```kotlin
  GEMINI_FLASH_LITE_LATEST("models/gemini-flash-lite-latest", "Gemini Flash Lite (Latest)")
  ```
- `ModelManager.DEFAULT_MODELS` must include:
  ```kotlin
  DynamicModel("models/gemini-flash-lite-latest", "Gemini Flash Lite (Latest)")
  ```
- `ModelManager.validateModelId` and `addModel` must accept `"models/gemini-flash-lite-latest"`.
- `AppPreferences.getSelectedModelId()` and migration logic must recognize `"models/gemini-flash-lite-latest"`.

### Non-Functional
- **Consistency:** Model IDs must consistently start with `"models/"`.
- **Backward Compatibility:** Existing saved preferences in SharedPreferences must not be cleared or corrupted.

## Implementation Steps
1. In `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiModels.kt`:
   - Add `GEMINI_FLASH_LITE_LATEST("models/gemini-flash-lite-latest", "Gemini Flash Lite (Latest)")` as the first entry in enum or top recommended list.
2. In `app/src/main/java/com/skul9x/rssreader/data/local/ModelManager.kt`:
   - Add `DynamicModel("models/gemini-flash-lite-latest", "Gemini Flash Lite (Latest)")` into `DEFAULT_MODELS`.
3. In `app/src/main/java/com/skul9x/rssreader/data/local/AppPreferences.kt`:
   - Update string migration map in `getSelectedModelId()` to handle `GEMINI_FLASH_LITE_LATEST` if previously saved as enum name.
4. Review `app/src/main/java/com/skul9x/rssreader/ui/settings/SummarizeModelSection.kt` to ensure seamless display in dropdown/selection dialogs.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiModels.kt` - Add enum entry for `gemini-flash-lite-latest`.
- `app/src/main/java/com/skul9x/rssreader/data/local/ModelManager.kt` - Add dynamic model entry to default list.
- `app/src/main/java/com/skul9x/rssreader/data/local/AppPreferences.kt` - Support preference parsing/migration for the new model.

## Dependencies
- Phase 01 and Phase 02.

## Constraints
- Do not remove existing default models (`gemini-3.5-flash-lite`, `gemini-3.1-flash-lite`, `gemini-2.5-flash-lite`, `gemini-3.6-flash`, `gemini-2.5-flash`) to preserve existing user selections.

## Acceptance Criteria
- [x] `GeminiModels.fromModelId("models/gemini-flash-lite-latest")` resolves to `GEMINI_FLASH_LITE_LATEST`.
- [x] `ModelManager.getModels()` includes `"models/gemini-flash-lite-latest"` in its initial default set.
- [x] Model validation passes with no errors for `"models/gemini-flash-lite-latest"`.
- [x] Settings screen renders the model name `"Gemini Flash Lite (Latest)"` cleanly.

## Comprehensive File-Based Test
- **Test File:** `app/src/test/java/com/skul9x/rssreader/data/local/GeminiFlashLiteModelRegistrationTest.kt`
- **Description:** Verifies that `models/gemini-flash-lite-latest` is properly registered in `GeminiModels`, `ModelManager`, and `AppPreferences`, passing all model ID validation checks.
- **Key Test Cases:**
  1. `testGeminiModels_containsFlashLiteLatest`: Checks that `GeminiModels.fromModelId` and lookup maps properly resolve `"models/gemini-flash-lite-latest"`.
  2. `testModelManager_defaultModelsIncludeFlashLiteLatest`: Validates that `ModelManager.DEFAULT_MODELS` contains the new model as an active selectable option.
  3. `testModelValidation_acceptsFlashLiteLatest`: Verifies that `ModelManager.isValidModelId("models/gemini-flash-lite-latest")` returns `true`.

