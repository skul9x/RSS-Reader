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

