package com.skul9x.rssreader.data.network.gemini

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResponseHelperTest {

    @Test
    fun testBuildRequestBody_ThinkingConfig_Gemini3() {
        val prompt = "Translate this"
        
        // Model gemini-3.1-flash-lite-preview should contain thinkingConfig
        val model31 = "models/gemini-3.1-flash-lite-preview"
        val requestBody31 = GeminiResponseHelper.buildRequestBody(prompt, model31)
        assertTrue("Expected thinkingConfig for $model31 but not found. Request: $requestBody31", 
            requestBody31.contains("\"thinkingConfig\""))

        // Model gemini-3-flash-preview should contain thinkingConfig
        val model30 = "models/gemini-3-flash-preview"
        val requestBody30 = GeminiResponseHelper.buildRequestBody(prompt, model30)
        assertTrue("Expected thinkingConfig for $model30 but not found. Request: $requestBody30", 
            requestBody30.contains("\"thinkingConfig\""))
            
        // Model gemini-flash-lite-latest should contain thinkingConfig
        val modelLatest = "models/gemini-flash-lite-latest"
        val requestBodyLatest = GeminiResponseHelper.buildRequestBody(prompt, modelLatest)
        assertTrue("Expected thinkingConfig for $modelLatest but not found. Request: $requestBodyLatest", 
            requestBodyLatest.contains("\"thinkingConfig\""))
    }

    @Test
    fun testBuildRequestBody_NoThinkingConfig_Gemini25() {
        val prompt = "Translate this"
        
        // Model gemini-2.5-flash-lite should NOT contain thinkingConfig
        val model25Lite = "models/gemini-2.5-flash-lite"
        val requestBody25Lite = GeminiResponseHelper.buildRequestBody(prompt, model25Lite)
        assertFalse("Did NOT expect thinkingConfig for $model25Lite. Request: $requestBody25Lite", 
            requestBody25Lite.contains("\"thinkingConfig\""))

        // Model gemini-2.5-flash should NOT contain thinkingConfig
        val model25 = "models/gemini-2.5-flash"
        val requestBody25 = GeminiResponseHelper.buildRequestBody(prompt, model25)
        assertFalse("Did NOT expect thinkingConfig for $model25. Request: $requestBody25", 
            requestBody25.contains("\"thinkingConfig\""))
            
        // Empty model string should NOT contain thinkingConfig
        val modelEmpty = ""
        val requestBodyEmpty = GeminiResponseHelper.buildRequestBody(prompt, modelEmpty)
        assertFalse("Did NOT expect thinkingConfig for empty model. Request: $requestBodyEmpty", 
            requestBodyEmpty.contains("\"thinkingConfig\""))
    }
    @Test
    fun testBuildRequestBody_JsonMode() {
        val prompt = "Translate this"
        val model = "models/gemini-2.5-flash"
        
        // When useJsonMode is true, should contain responseMimeType
        val requestBodyJson = GeminiResponseHelper.buildRequestBody(prompt, model, useJsonMode = true)
        assertTrue("Expected responseMimeType for JSON mode but not found. Request: $requestBodyJson", 
            requestBodyJson.contains("\"responseMimeType\":\"application/json\""))

        // When useJsonMode is false (default), should NOT contain responseMimeType
        val requestBodyDefault = GeminiResponseHelper.buildRequestBody(prompt, model)
        assertFalse("Did NOT expect responseMimeType for default mode. Request: $requestBodyDefault", 
            requestBodyDefault.contains("\"responseMimeType\""))
    }
}
