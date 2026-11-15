package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.data.network.gemini.GeminiResponseHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTranslationPayloadTest {

    @Test
    fun testSingleTranslationPayload_structureAndTokens() {
        val systemInstruction = "Translate the following English title into natural, concise Vietnamese."
        val userPrompt = "Breaking: Tech conference announces new AI innovations"
        val model = "models/gemini-flash-lite-latest"

        val jsonString = GeminiResponseHelper.buildTranslationRequestBody(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            model = model,
            maxOutputTokens = 128,
            useJsonMode = false
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject

        // 1. Validates presence of top-level system_instruction
        val systemInstructionObj = json["system_instruction"]?.jsonObject
        assertNotNull("system_instruction must be present at top level", systemInstructionObj)
        val sysParts = systemInstructionObj!!["parts"]?.jsonArray
        assertNotNull("system_instruction must have parts array", sysParts)
        assertEquals(1, sysParts!!.size)
        assertEquals(systemInstruction, sysParts[0].jsonObject["text"]?.jsonPrimitive?.content)

        // 2. Validates user prompt in contents with role/parts
        val contents = json["contents"]?.jsonArray
        assertNotNull("contents array must be present", contents)
        assertEquals(1, contents!!.size)
        val contentObj = contents[0].jsonObject
        assertEquals("user", contentObj["role"]?.jsonPrimitive?.content)
        val userParts = contentObj["parts"]?.jsonArray
        assertNotNull("content parts array must be present", userParts)
        assertEquals(1, userParts!!.size)
        assertEquals(userPrompt, userParts[0].jsonObject["text"]?.jsonPrimitive?.content)

        // 3. Validates generationConfig: temperature == 0.1, maxOutputTokens == 128, topP == 0.95
        val genConfig = json["generationConfig"]?.jsonObject
        assertNotNull("generationConfig must be present", genConfig)
        assertEquals(0.1, genConfig!!["temperature"]?.jsonPrimitive?.double ?: 0.0, 0.001)
        assertEquals(128, genConfig["maxOutputTokens"]?.jsonPrimitive?.int)
        assertEquals(0.95, genConfig["topP"]?.jsonPrimitive?.double ?: 0.0, 0.001)
        assertNull("responseMimeType must be omitted when useJsonMode is false", genConfig["responseMimeType"])

        // 4. Validates thinkingLevel == 'minimal' for models/gemini-flash-lite-latest
        val thinkingConfig = genConfig["thinkingConfig"]?.jsonObject
        assertNotNull("thinkingConfig must be present for gemini-flash-lite-latest", thinkingConfig)
        assertEquals("minimal", thinkingConfig!!["thinkingLevel"]?.jsonPrimitive?.content)
        assertNull("thinkingBudget should NOT be set on latest models", thinkingConfig["thinkingBudget"])

        // 5. Validates safetySettings presence
        val safetySettings = json["safetySettings"]?.jsonArray
        assertNotNull("safetySettings array must be present", safetySettings)
        assertTrue(safetySettings!!.isNotEmpty())
    }

    @Test
    fun testBatchTranslationPayload_jsonMode() {
        val systemInstruction = "Translate the given JSON map of English titles to Vietnamese in JSON mode."
        val userPrompt = """{"id1": "Title One", "id2": "Title Two"}"""
        val model = "models/gemini-flash-lite-latest"

        val jsonString = GeminiResponseHelper.buildTranslationRequestBody(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            model = model,
            maxOutputTokens = 384,
            useJsonMode = true
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject

        // 1. Validates top-level system_instruction
        val systemInstructionObj = json["system_instruction"]?.jsonObject
        assertNotNull("system_instruction must be present at top level", systemInstructionObj)
        val sysParts = systemInstructionObj!!["parts"]?.jsonArray
        assertEquals(systemInstruction, sysParts!![0].jsonObject["text"]?.jsonPrimitive?.content)

        // 2. Validates responseMimeType == 'application/json', maxOutputTokens == 384, and temperature == 0.1
        val genConfig = json["generationConfig"]?.jsonObject
        assertNotNull("generationConfig must be present", genConfig)
        assertEquals("application/json", genConfig!!["responseMimeType"]?.jsonPrimitive?.content)
        assertEquals(384, genConfig["maxOutputTokens"]?.jsonPrimitive?.int)
        assertEquals(0.1, genConfig["temperature"]?.jsonPrimitive?.double ?: 0.0, 0.001)

        // 3. Validates thinkingLevel == 'minimal'
        val thinkingConfig = genConfig["thinkingConfig"]?.jsonObject
        assertNotNull(thinkingConfig)
        assertEquals("minimal", thinkingConfig!!["thinkingLevel"]?.jsonPrimitive?.content)
    }

    @Test
    fun testFallbackModelThinkingBudget() {
        val systemInstruction = "Translate to Vietnamese"
        val userPrompt = "Hello world"
        val model = "models/gemini-2.5-flash-lite"

        val jsonString = GeminiResponseHelper.buildTranslationRequestBody(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            model = model,
            maxOutputTokens = 128,
            useJsonMode = false
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject
        val genConfig = json["generationConfig"]?.jsonObject
        assertNotNull(genConfig)

        // Validates that passing models/gemini-2.5-flash-lite configures thinkingBudget == 0
        val thinkingConfig = genConfig!!["thinkingConfig"]?.jsonObject
        assertNotNull("thinkingConfig must be present for Gemini 2.5", thinkingConfig)
        assertEquals(0, thinkingConfig!!["thinkingBudget"]?.jsonPrimitive?.int)
        assertNull("thinkingLevel should NOT be set on Gemini 2.5", thinkingConfig["thinkingLevel"])
    }

    @Test
    fun testBlankSystemInstructionOmitted() {
        val userPrompt = "Translate this without system prompt"
        val jsonString = GeminiResponseHelper.buildTranslationRequestBody(
            systemInstruction = "",
            userPrompt = userPrompt,
            model = "models/gemini-flash-lite-latest"
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject
        assertNull("system_instruction should not be included when blank", json["system_instruction"])
        assertNotNull("contents must be present", json["contents"])
    }
}

