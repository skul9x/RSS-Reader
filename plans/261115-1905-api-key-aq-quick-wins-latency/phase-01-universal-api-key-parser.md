# Phase 01: Universal Gemini API Key Parser & Validator
Status: ✅ Completed
Dependencies: None

## Objective
Enable full support for both modern Google AI Studio authentication keys (starting with `AQ.`, such as `AQ.MockAb8RN6Kny...`) and legacy Google Cloud traffic keys (starting with `AIza`, exactly 39 characters). Centralize parsing and validation logic in `ApiKeyManager` as a single source of truth, allowing smart, fault-tolerant token extraction from arbitrary user text (multi-line, quoted, comma-separated, or environment variable exports) while updating the Settings dialogs.

## Scope
- Modify `ApiKeyManager.kt` to introduce `API_KEY_REGEX`, `isValidApiKey(key: String)`, `extractApiKeys(rawInput: String)`, and update `addApiKey()`.
- Update `ApiKeysSection.kt` to dynamically enable the confirmation button using `extractApiKeys()` and update user placeholder hints.
- Update `SettingsScreen.kt` to utilize `extractApiKeys()` during batch additions.
- Add exactly one comprehensive file-based test: `ApiKeyManagerValidationTest.kt`.

## Requirements

### Functional
1. **Support Modern `AQ.` Format:**
   - Recognize keys matching `AQ\.[A-Za-z0-9_-]{30,}` (including sample keys `AQ.MockAb8RN6KnyNTaXetekM8inIggtZ5lO_jyqfS46PyYq2o68Ako1Q` and `AQ.MockAb8RN6JJO6vkF0hUSsFv4LTsieKLEjxjRe1iKNixu_Ifb3U-wQ`).
2. **Preserve Legacy `AIza` Format:**
   - Match standard 39-character keys `AIza[A-Za-z0-9_-]{35}`.
3. **Smart Extraction:**
   - Extract valid keys from messy text:
     - Quotes: `"AQ.Ab8..."` or `'AIza...'`
     - Env variables: `GEMINI_API_KEY=AQ.Ab8...`
     - Delimiters: Newlines, spaces, commas, semicolons.
   - Automatically deduplicate extracted keys while preserving insertion order.
4. **Validation Integrity:**
   - Reject keys with invalid prefixes (e.g. `sk-`, `ghp_`, random strings).
   - Reject keys shorter than 30 characters.
5. **UI Interactivity:**
   - In `ApiKeysSection.kt`, the "Thêm" (Add) button must be enabled whenever `extractApiKeys(apiKeyText).isNotEmpty()`.
   - Update placeholder to display examples of both `AIza...` and `AQ.Ab8...`.
   - In `SettingsScreen.kt`, batch add extracted keys directly without double filtering.

### Non-Functional
- **Zero Third-party Dependencies:** Pure Kotlin standard library regex and collection operations.
- **Backward Compatibility:** All existing stored keys in `EncryptedSharedPreferences` remain 100% valid.
- **Safe Masking:** Existing `maskApiKey` (`take(8)...takeLast(4)`) remains safe and clean for ~55-character keys.

## How to Implement

### Step 1: Centralize Parsing & Validation in `ApiKeyManager.kt`
**File:** `app/src/main/java/com/skul9x/rssreader/data/local/ApiKeyManager.kt`

1. In the `companion object`, declare the universal regex pattern and static utility functions:
```kotlin
companion object {
    private const val PREFS_NAME = "api_keys_secure"
    private const val KEY_API_KEYS = "gemini_api_keys"

    // Universal regex matching legacy AIza (39 chars) and modern AQ. auth keys (30+ chars)
    private val API_KEY_REGEX = Regex("""\b(?:AIza[A-Za-z0-9_-]{35}|AQ\.[A-Za-z0-9_-]{30,})\b""")

    @Volatile
    private var instance: ApiKeyManager? = null

    fun getInstance(context: Context): ApiKeyManager {
        return instance ?: synchronized(this) {
            instance ?: ApiKeyManager(context.applicationContext).also { instance = it }
        }
    }

    /**
     * Validates whether an individual trimmed string is a valid Gemini API key.
     */
    fun isValidApiKey(apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        return API_KEY_REGEX.matches(trimmed)
    }

    /**
     * Extracts all valid Gemini API keys from an arbitrary input string.
     * Tolerates delimiters, quotes, assignment statements, and whitespace.
     */
    fun extractApiKeys(rawInput: String): List<String> {
        if (rawInput.isBlank()) return emptyList()
        return API_KEY_REGEX.findAll(rawInput)
            .map { it.value.trim() }
            .distinct()
            .toList()
    }
}
```

2. Refactor `addApiKey(apiKey: String)`:
```kotlin
fun addApiKey(apiKey: String): Boolean {
    val trimmedKey = apiKey.trim()

    // Validate using the unified validator
    if (!isValidApiKey(trimmedKey)) {
        return false
    }

    val currentKeys = getApiKeys().toMutableList()

    // Check for duplicates
    if (currentKeys.contains(trimmedKey)) {
        return false
    }

    currentKeys.add(trimmedKey)
    saveKeys(currentKeys)
    return true
}
```

### Step 2: Update Dialog Button & Hints in `ApiKeysSection.kt`
**File:** `app/src/main/java/com/skul9x/rssreader/ui/settings/ApiKeysSection.kt`

1. Update the placeholder at line ~223:
```kotlin
placeholder = { Text("Dán danh sách key vào đây...\nAIza...\nAQ.Ab8...") },
```

2. Replace the hardcoded `apiKeyText.contains("AIza")` check at line ~254:
```kotlin
confirmButton = {
    val hasValidKeys = remember(apiKeyText) {
        ApiKeyManager.extractApiKeys(apiKeyText).isNotEmpty()
    }
    Button(
        onClick = { onAddKey(apiKeyText) },
        enabled = hasValidKeys,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text("Thêm")
    }
},
```

### Step 3: Streamline Key Processing in `SettingsScreen.kt`
**File:** `app/src/main/java/com/skul9x/rssreader/ui/settings/SettingsScreen.kt`

Replace lines ~330-340 in `AddApiKeyDialog`:
```kotlin
onAddKey = { input ->
    val keys = ApiKeyManager.extractApiKeys(input)
    var addedCount = 0

    keys.forEach { key ->
        if (apiKeyManager.addApiKey(key)) {
            addedCount++
        }
    }

    if (addedCount > 0) {
        refreshKeys()
        Toast.makeText(context, "Đã thêm $addedCount API key", Toast.LENGTH_SHORT).show()
        showAddKeyDialog = false
    } else {
        Toast.makeText(context, "Không tìm thấy API key hợp lệ hoặc key đã tồn tại", Toast.LENGTH_SHORT).show()
    }
}
```

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/local/ApiKeyManager.kt` (Modify)
- `app/src/main/java/com/skul9x/rssreader/ui/settings/ApiKeysSection.kt` (Modify)
- `app/src/main/java/com/skul9x/rssreader/ui/settings/SettingsScreen.kt` (Modify)
- `app/src/test/java/com/skul9x/rssreader/data/local/ApiKeyManagerValidationTest.kt` (Create)

## Dependencies
- None. Uses standard Kotlin regex and collection APIs.

## Constraints
- Do not break backward compatibility with stored `AIza` keys.
- Do not alter the encryption schema in `EncryptedSharedPreferences`.
- Pure unit test must not require an Android instrumentation device / emulator.

## Acceptance Criteria
- [x] `ApiKeyManager.isValidApiKey()` returns `true` for standard 39-character `AIza` keys.
- [x] `ApiKeyManager.isValidApiKey()` returns `true` for valid `AQ.` formatted sample keys.
- [x] `ApiKeyManager.isValidApiKey()` returns `false` for invalid keys (`short`, `AIzaShort`, `sk-proj-xxx`, random text).
- [x] `ApiKeyManager.extractApiKeys()` successfully extracts both `AIza` and `AQ.` keys from multi-line strings, strings with quotes, and `.env` format.
- [x] Duplicate keys in the input string are deduplicated.
- [x] In `ApiKeysSection`, pasting an `AQ.` key immediately enables the "Thêm" button.

---

## Comprehensive File-Based Test

**File:** `app/src/test/java/com/skul9x/rssreader/data/local/ApiKeyManagerValidationTest.kt`

```kotlin
package com.skul9x.rssreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyManagerValidationTest {

    private val validLegacyKey1 = "AIzaSyD-1234567890abcdefghijklmnopqrst" // 39 chars
    private val validLegacyKey2 = "AIzaSyA_9876543210zyxwvutsrqponmlkjihg" // 39 chars

    private val validModernKey1 = "AQ.MockAb8RN6KnyNTaXetekM8inIggtZ5lO_jyqfS46PyYq2o68Ako1Q" // Mock sample 1 (59 chars)
    private val validModernKey2 = "AQ.MockAb8RN6JJO6vkF0hUSsFv4LTsieKLEjxjRe1iKNixu_Ifb3U-wQ" // Mock sample 2 (59 chars)

    @Test
    fun `isValidApiKey should return true for valid legacy AIza keys`() {
        assertTrue(ApiKeyManager.isValidApiKey(validLegacyKey1))
        assertTrue(ApiKeyManager.isValidApiKey(validLegacyKey2))
        assertTrue(ApiKeyManager.isValidApiKey("  $validLegacyKey1  ")) // Whitespace tolerance
    }

    @Test
    fun `isValidApiKey should return true for valid modern AQ keys`() {
        assertTrue(ApiKeyManager.isValidApiKey(validModernKey1))
        assertTrue(ApiKeyManager.isValidApiKey(validModernKey2))
        assertTrue(ApiKeyManager.isValidApiKey("  $validModernKey1  ")) // Whitespace tolerance
    }

    @Test
    fun `isValidApiKey should return false for invalid keys`() {
        // Too short
        assertFalse(ApiKeyManager.isValidApiKey("AIzaShort"))
        assertFalse(ApiKeyManager.isValidApiKey("AQ.Short"))

        // Wrong prefix
        assertFalse(ApiKeyManager.isValidApiKey("sk-proj-1234567890123456789012345678901234567890"))
        assertFalse(ApiKeyManager.isValidApiKey("ghp_1234567890123456789012345678901234567890"))

        // Illegal characters
        assertFalse(ApiKeyManager.isValidApiKey("AIzaSyD-1234567890abcdefghijklm!@#$"))
        assertFalse(ApiKeyManager.isValidApiKey("AQ.MockAb8RN6KnyNTaXetekM8inIggtZ5lO_jyqfS46PyYq2o68Ako1Q!@#"))

        // Blank or empty
        assertFalse(ApiKeyManager.isValidApiKey(""))
        assertFalse(ApiKeyManager.isValidApiKey("   "))
    }

    @Test
    fun `extractApiKeys should extract clean keys from messy input`() {
        val rawInput = """
            Here are my configured keys:
            API_KEY_1="$validModernKey1"
            GEMINI_API_KEY = '$validModernKey2';
            legacy_key=$validLegacyKey1,
            some notes...
            $validLegacyKey2
            invalid_key=AIzaTooShort
            another_invalid=AQ.TooShort
        """.trimIndent()

        val extracted = ApiKeyManager.extractApiKeys(rawInput)

        assertEquals(4, extracted.size)
        assertEquals(validModernKey1, extracted[0])
        assertEquals(validModernKey2, extracted[1])
        assertEquals(validLegacyKey1, extracted[2])
        assertEquals(validLegacyKey2, extracted[3])
    }

    @Test
    fun `extractApiKeys should deduplicate repeated keys while preserving order`() {
        val rawInput = """
            $validModernKey1
            $validLegacyKey1
            $validModernKey1
            $validLegacyKey2
            $validLegacyKey1
        """.trimIndent()

        val extracted = ApiKeyManager.extractApiKeys(rawInput)

        assertEquals(3, extracted.size)
        assertEquals(validModernKey1, extracted[0])
        assertEquals(validLegacyKey1, extracted[1])
        assertEquals(validLegacyKey2, extracted[2])
    }

    @Test
    fun `extractApiKeys should return empty list for blank or invalid input`() {
        assertTrue(ApiKeyManager.extractApiKeys("").isEmpty())
        assertTrue(ApiKeyManager.extractApiKeys("   \n\t  ").isEmpty())
        assertTrue(ApiKeyManager.extractApiKeys("random text with no keys").isEmpty())
    }
}
```

