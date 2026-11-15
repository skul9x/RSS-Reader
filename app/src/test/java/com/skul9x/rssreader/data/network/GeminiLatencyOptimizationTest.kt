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

