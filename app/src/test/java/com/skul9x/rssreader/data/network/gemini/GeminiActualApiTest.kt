package com.skul9x.rssreader.data.network.gemini

import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiActualApiTest {

    @Test
    fun testRealApiCallForSummarization() {
        val apiKey = "AIzaSyDVH10ImD1B1PlbcmfRfdxmKWDadlwDEUI" 
        val model = "models/gemini-flash-lite-latest"
        val url = "https://generativelanguage.googleapis.com/v1beta/$model:generateContent?key=$apiKey"

        // Simulated article content block
        val sampleArticle = """
            Một con hươu nhỏ khoảng 3-4 tuổi, đi lạc vào khu dân cư ở thành phố Vinh, Nghệ An. Do sợ hãi nên hươu đã húc một người bị thương nhẹ, sau đó bị người dân khống chế.
            Sự việc xảy ra sáng 21/4, tại một khu đô thị thuộc xã Tráng Liệt, huyện Bình Giang, tỉnh Hải Dương.
            Lực lượng kiểm lâm chuyên trách đã có mặt tại hiện trường để giải quyết, dự kiến sẽ đưa hươu về trung tâm cứu hộ tự nhiên.
            Cơ quan chức năng cảnh báo người dân không nên cố gắng tiếp cận hoặc đe dọa các động vật hoang dã.
        """.trimIndent()
        
        // 1. Build prompt exactly as the app does
        val prompt = GeminiPrompts.buildSummarizationPrompt(sampleArticle)
        
        // 2. Build JSON dynamically with thinkingConfig appropriately using our fix
        val requestBodyText = GeminiResponseHelper.buildRequestBody(prompt, model)
        println("Request Body generated:\n$requestBodyText\n")
        
        // 3. Make the API Call with OkHttp (acting exactly like GeminiApiClient)
        val client = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
            
        val request = Request.Builder()
            .url(url)
            .post(requestBodyText.toRequestBody("application/json".toMediaType()))
            .build()
            
        var responseBodyStr = ""
        client.newCall(request).execute().use { response ->
            responseBodyStr = response.body?.string() ?: ""
            println("HTTP Status Code: ${response.code}")
            // Debug the raw response structure
        }
        
        assertTrue("HTTP request failed", responseBodyStr.isNotEmpty())

        // 4. Extract and clean content exactly like our app
        val generatedText = GeminiResponseHelper.extractText(responseBodyStr)
        val cleanedText = GeminiResponseHelper.cleanForTts(generatedText)
        
        println("--- FINAL CLEAN TTS TEXT ---")
        println(cleanedText)
        println("----------------------------")
        
        // Ensure successful summarization content is returned
        assertTrue("Cleaned text should not be empty", cleanedText.isNotEmpty())
        assertTrue("Response is likely a crash or empty.", cleanedText.contains("1."))
    }
}
