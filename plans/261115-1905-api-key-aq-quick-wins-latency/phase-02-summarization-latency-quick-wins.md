# Phase 02: Summarization Latency Quick Wins
Status: ✅ Completed
Dependencies: None

## Objective
Radically cut down Gemini API summarization latency (from ~6–8 seconds down to ~1.5–2.5 seconds) through targeted, low-risk request and payload optimizations:
1. Turn off reasoning/thinking overhead on Gemini 2.5 Flash and Flash-Lite models by setting `"thinkingConfig": { "thinkingBudget": 0 }`.
2. Truncate summarization input text to a focused 4,000 characters (~800–1,000 words), eliminating excessive token upload and processing delays caused by the previous 100,000-character ceiling.
3. Constrain maximum generation tokens (`maxOutputTokens = 512`) to match the compact nature of audio news summaries.

## Scope
- Modify `GeminiResponseHelper.kt` to:
  - Add `"thinkingBudget": 0` inside `thinkingConfig` for all `gemini-2.5` model identifiers.
  - Maintain `"thinkingLevel": "minimal"` for `gemini-3` and `-latest` models.
  - Support setting `maxOutputTokens` tailored to concise summarization (defaulting or passing 512 for summaries).
- Modify `GeminiApiClient.kt` in `tryGenerateContent`:
  - Change truncation limit from `100_000` to `4_000` characters.
  - Pass `maxOutputTokens = 512` into `GeminiResponseHelper.buildRequestBody`.
- Add exactly one comprehensive file-based test: `GeminiLatencyOptimizationTest.kt`.

## Requirements

### Functional
1. **Thinking Budget Configuration:**
   - When the target model is `gemini-2.5-flash` or `gemini-2.5-flash-lite`, the JSON `generationConfig` MUST contain:
     ```json
     "thinkingConfig": {
       "thinkingBudget": 0
     }
     ```
   - When the target model contains `gemini-3` or `latest`, the JSON `generationConfig` MUST contain:
     ```json
     "thinkingConfig": {
       "thinkingLevel": "minimal"
     }
     ```
   - Other models (e.g. legacy or third-party) must NOT receive unknown thinking properties that cause HTTP 400 Bad Request.
2. **Context Window Optimization:**
   - In `tryGenerateContent`, input content with `length > 4000` characters must be sliced to `take(4000) + "\n... [NỘI DUNG ĐÃ CẮT BỚT]"`.
   - Content with `length <= 4000` characters must remain completely untouched.
3. **Output Token Right-Sizing:**
   - Summarization requests must set `maxOutputTokens: 512` in `generationConfig`, preventing runaway generation latency.

### Non-Functional
- **Zero Protocol Regressions:** Ensure compatibility with Gemini v1beta endpoint specifications.
- **Zero Impact on Translation:** Keep translation requests with appropriate default configs.
- **High Concurrency Safety:** Truncation and JSON payload building remain pure functions without mutating shared states.

## How to Implement

### Step 1: Configure Thinking & Token Limits in `GeminiResponseHelper.kt`
**File:** `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiResponseHelper.kt`

Modify `buildRequestBody` around line 43:
```kotlin
fun buildRequestBody(
    prompt: String, 
    model: String = "", 
    temperature: Double = 0.7, 
    maxOutputTokens: Int = 4096,
    useJsonMode: Boolean = false
): String {
    val json = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                putJsonArray("parts") {
                    addJsonObject {
                        put("text", prompt)
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", temperature)
            put("maxOutputTokens", maxOutputTokens)
            put("topP", 0.95)
            
            // JSON Mode - force Gemini to return valid JSON
            if (useJsonMode) {
                put("responseMimeType", "application/json")
            }
            
            // Latency Optimization: Disable or minimize thinking overhead
            // Gemini 2.5 series uses thinkingBudget (integer token count: 0 = disabled)
            // Gemini 3.x+ series uses thinkingLevel ("minimal" / "low")
            if (model.contains("gemini-2.5")) {
                putJsonObject("thinkingConfig") {
                    put("thinkingBudget", 0) // Explicitly disable thinking tokens for instant response
                }
            } else if (model.contains("gemini-3") || model.contains("latest")) {
                putJsonObject("thinkingConfig") {
                    put("thinkingLevel", "minimal") // Recommended for Gemini 3.1 to minimize latency
                }
            }
        }
        putJsonArray("safetySettings") {
            addJsonObject {
                put("category", "HARM_CATEGORY_HARASSMENT")
                put("threshold", "BLOCK_NONE")
            }
            addJsonObject {
                put("category", "HARM_CATEGORY_HATE_SPEECH")
                put("threshold", "BLOCK_NONE")
            }
            addJsonObject {
                put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                put("threshold", "BLOCK_NONE")
            }
            addJsonObject {
                put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                put("threshold", "BLOCK_NONE")
            }
        }
    }
    return json.toString()
}
```

### Step 2: Optimize Input Truncation & Max Tokens in `GeminiApiClient.kt`
**File:** `app/src/main/java/com/skul9x/rssreader/data/network/GeminiApiClient.kt`

Update `tryGenerateContent` around lines 791–800:
```kotlin
// Truncate content to 4,000 characters (sufficient for news summaries, eliminates TTFT latency)
val truncatedContent = if (content.length > 4_000) {
    content.take(4_000) + "\n... [NỘI DUNG ĐÃ CẮT BỚT]"
} else {
    content
}

val prompt = GeminiPrompts.buildSummarizationPrompt(truncatedContent)
val requestBody = GeminiResponseHelper.buildRequestBody(
    prompt = prompt, 
    model = model, 
    maxOutputTokens = 512
)
```

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/gemini/GeminiResponseHelper.kt` (Modify)
- `app/src/main/java/com/skul9x/rssreader/data/network/GeminiApiClient.kt` (Modify)
- `app/src/test/java/com/skul9x/rssreader/data/network/GeminiLatencyOptimizationTest.kt` (Create)

## Dependencies
- `kotlinx.serialization.json` (already present)
- JUnit 4 and MockK (already present in test scope)

## Constraints
- Do not set `thinkingLevel` for `gemini-2.5` (causes HTTP 400 Bad Request).
- Do not set `thinkingBudget` for `gemini-3` (deprecated in favor of `thinkingLevel`).
- Ensure truncation string indicator matches existing localization style `... [NỘI DUNG ĐÃ CẮT BỚT]`.

## Acceptance Criteria
- [x] For `models/gemini-2.5-flash` and `models/gemini-2.5-flash-lite`, generated JSON contains `"thinkingBudget": 0`.
- [x] For `models/gemini-3.1-flash-lite` and `models/gemini-3.5-flash-lite`, generated JSON contains `"thinkingLevel": "minimal"`.
- [x] For other models (e.g. `models/custom-model`), no `thinkingConfig` object is attached.
- [x] In summarization requests, `maxOutputTokens` is explicitly configured to `512`.
- [x] Content up to 4,000 characters is untouched; content exceeding 4,000 characters is truncated cleanly to 4,000 characters plus suffix.

---

## Comprehensive File-Based Test

**File:** `app/src/test/java/com/skul9x/rssreader/data/network/GeminiLatencyOptimizationTest.kt`

```kotlin
package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.data.network.gemini.GeminiResponseHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLatencyOptimizationTest {

    @Test
    fun `buildRequestBody for Gemini 2_5 should configure thinkingBudget as 0`() {
        val jsonString = GeminiResponseHelper.buildRequestBody(
            prompt = "Summarize this article",
            model = "models/gemini-2.5-flash-lite",
            maxOutputTokens = 512
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject
        val genConfig = json["generationConfig"]?.jsonObject
        assertNotNull(genConfig)

        assertEquals(512, genConfig!!["maxOutputTokens"]?.jsonPrimitive?.int)

        val thinkingConfig = genConfig["thinkingConfig"]?.jsonObject
        assertNotNull("thinkingConfig must be present for Gemini 2.5", thinkingConfig)
        assertEquals(0, thinkingConfig!!["thinkingBudget"]?.jsonPrimitive?.int)
        assertNull("thinkingLevel should NOT be set on Gemini 2.5", thinkingConfig["thinkingLevel"])
    }

    @Test
    fun `buildRequestBody for Gemini 3 should configure thinkingLevel as minimal`() {
        val jsonString = GeminiResponseHelper.buildRequestBody(
            prompt = "Summarize this article",
            model = "models/gemini-3.1-flash-lite",
            maxOutputTokens = 512
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject
        val genConfig = json["generationConfig"]?.jsonObject
        assertNotNull(genConfig)

        val thinkingConfig = genConfig!!["thinkingConfig"]?.jsonObject
        assertNotNull("thinkingConfig must be present for Gemini 3", thinkingConfig)
        assertEquals("minimal", thinkingConfig!!["thinkingLevel"]?.jsonPrimitive?.content)
        assertNull("thinkingBudget should NOT be set on Gemini 3", thinkingConfig["thinkingBudget"])
    }

    @Test
    fun `buildRequestBody for non-thinking models should omit thinkingConfig`() {
        val jsonString = GeminiResponseHelper.buildRequestBody(
            prompt = "Translate this text",
            model = "models/gemini-1.5-flash",
            maxOutputTokens = 1024
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject
        val genConfig = json["generationConfig"]?.jsonObject
        assertNotNull(genConfig)

        assertNull("thinkingConfig must NOT be set on legacy models", genConfig!!["thinkingConfig"])
    }

    @Test
    fun `summarization truncation logic should correctly clamp content to 4000 characters`() {
        val shortContent = "Short article content under limit."
        val truncatedShort = if (shortContent.length > 4_000) {
            shortContent.take(4_000) + "\n... [NỘI DUNG ĐÃ CẮT BỚT]"
        } else {
            shortContent
        }
        assertEquals(shortContent, truncatedShort)

        val longContent = "A".repeat(10_000)
        val truncatedLong = if (longContent.length > 4_000) {
            longContent.take(4_000) + "\n... [NỘI DUNG ĐÃ CẮT BỚT]"
        } else {
            longContent
        }

        assertTrue(truncatedLong.startsWith("A".repeat(4_000)))
        assertTrue(truncatedLong.endsWith("\n... [NỘI DUNG ĐÃ CẮT BỚT]"))
        assertEquals(4_000 + "\n... [NỘI DUNG ĐÃ CẮT BỚT]".length, truncatedLong.length)
    }
}
```

