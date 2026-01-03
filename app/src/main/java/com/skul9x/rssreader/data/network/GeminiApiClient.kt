package com.skul9x.rssreader.data.network

import android.content.Context
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gemini API client with automatic failover between API keys and models.
 * Cycles through models when quota is exceeded, then switches to next API key.
 * API keys are loaded dynamically from ApiKeyManager (user-configurable in Settings).
 */
class GeminiApiClient(context: Context) {

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        // Models (cycled in order when quota exceeded)
        private val MODELS = listOf(
            "models/gemini-2.5-flash",
            "models/gemini-2.5-flash-lite",
            "models/gemini-2.0-flash",
            "models/gemini-2.0-flash-lite",
            "models/gemini-3-flash-preview"
        )
    }

    private val apiKeyManager = ApiKeyManager.getInstance(context)
    
    // API keys loaded dynamically from storage
    private var apiKeys: List<String> = apiKeyManager.getApiKeys()

    // Use shared OkHttpClient singleton
    private val client = NetworkClient.okHttpClient

    private var currentApiKeyIndex = 0
    private var currentModelIndex = 0

    /**
     * Refresh API keys from storage (call after user adds/removes keys).
     */
    fun refreshApiKeys() {
        apiKeys = apiKeyManager.getApiKeys()
        // Reset indices if current index is out of bounds
        if (currentApiKeyIndex >= apiKeys.size) {
            currentApiKeyIndex = 0
        }
    }

    /**
     * Check if any API keys are configured.
     */
    fun hasApiKeys(): Boolean = apiKeys.isNotEmpty()

    /**
     * Reset to first API key and first model.
     */
    fun reset() {
        currentApiKeyIndex = 0
        currentModelIndex = 0
    }

    /**
     * Summarize news content for Vietnamese TTS.
     * Automatically handles quota errors with model/API key rotation.
     *
     * @param content The news content to summarize
     * @return Summarized text in Vietnamese, or fallback message on complete failure
     */
    suspend fun summarizeForTts(content: String): SummarizeResult {
        return withContext(Dispatchers.IO) {
            summarizeWithRetry(content)
        }
    }

    private fun summarizeWithRetry(content: String): SummarizeResult {
        // Check if any API keys are configured
        if (apiKeys.isEmpty()) {
            return SummarizeResult.NoApiKeys
        }
        
        val startApiKeyIndex = currentApiKeyIndex
        val startModelIndex = currentModelIndex

        do {
            val apiKey = apiKeys[currentApiKeyIndex]
            val model = MODELS[currentModelIndex]

            Log.d(TAG, "Trying API key ${currentApiKeyIndex + 1}/${apiKeys.size}, Model: $model")

            val result = tryGenerateContent(apiKey, model, content)

            when (result) {
                is ApiResult.Success -> {
                    return SummarizeResult.Success(result.text, model)
                }
                is ApiResult.QuotaExceeded -> {
                    Log.w(TAG, "Quota exceeded for $model with API key ${currentApiKeyIndex + 1}")
                    if (!rotateToNextModel()) {
                        // All models exhausted for current API key, try next API key
                        if (!rotateToNextApiKey()) {
                            // All API keys exhausted
                            return SummarizeResult.AllQuotaExhausted
                        }
                    }
                }
                is ApiResult.ModelNotFound -> {
                    Log.w(TAG, "Model not found: $model")
                    if (!rotateToNextModel()) {
                        if (!rotateToNextApiKey()) {
                            return SummarizeResult.AllQuotaExhausted
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "API error: ${result.message}")
                    // Try next model on generic errors
                    if (!rotateToNextModel()) {
                        if (!rotateToNextApiKey()) {
                            return SummarizeResult.Error(result.message)
                        }
                    }
                }
            }

            // Prevent infinite loop - check if we've cycled through all combinations
        } while (currentApiKeyIndex != startApiKeyIndex || currentModelIndex != startModelIndex)

        return SummarizeResult.AllQuotaExhausted
    }

    private fun tryGenerateContent(apiKey: String, model: String, content: String): ApiResult {
        try {
            val prompt = buildSummarizationPrompt(content)
            val requestBody = buildRequestBody(prompt)
            
            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            return when (response.code) {
                200 -> {
                    val rawText = extractText(responseBody)
                    if (rawText.isNotBlank()) {
                        val cleanedText = cleanForTts(rawText)
                        ApiResult.Success(cleanedText)
                    } else {
                        ApiResult.Error("Empty response from API")
                    }
                }
                429 -> ApiResult.QuotaExceeded
                404 -> {
                    if (responseBody.contains("not found", ignoreCase = true)) {
                        ApiResult.ModelNotFound
                    } else {
                        ApiResult.Error("Not found: $responseBody")
                    }
                }
                else -> ApiResult.Error("HTTP ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API call", e)
            return ApiResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun buildSummarizationPrompt(content: String): String {
        return """
Bạn là trợ lý AI chuyên tóm tắt tin tức cho người lái xe. Nhiệm vụ của bạn là tóm tắt nội dung sau thành các ý chính quan trọng nhất, ngắn gọn, súc tích.

YÊU CẦU BẮT BUỘC:
1. Chỉ trả về nội dung tóm tắt dưới dạng danh sách đánh số (1. 2. 3...).
2. TUYỆT ĐỐI KHÔNG có bất kỳ câu dẫn dắt, chào hỏi, rào đón hay kết thúc nào (Ví dụ: KHÔNG viết "Dưới đây là tóm tắt...", "Thưa giám đốc...", "Chào bạn...", "Tuyệt vời...").
3. Vào thẳng nội dung chính ngay lập tức.
4. Ngôn ngữ tự nhiên, dễ nghe khi đọc bằng giọng nói.

Nội dung cần tóm tắt:
$content
""".trim()
    }

    /**
     * Clean AI response for TTS: remove markdown formatting but keep numbering.
     * Uses simple replacements to avoid regex issues.
     */
    fun cleanForTts(text: String): String {
        Log.d(TAG, "cleanForTts input length: ${text.length}")
        Log.d(TAG, "cleanForTts input: $text")
        
        var result = text
        
        // Remove bold markers: **text** -> text
        while (result.contains("**")) {
            val start = result.indexOf("**")
            val end = result.indexOf("**", start + 2)
            if (end > start) {
                val content = result.substring(start + 2, end)
                result = result.substring(0, start) + content + result.substring(end + 2)
            } else {
                // Remove orphan **
                result = result.replaceFirst("**", "")
            }
        }
        
        // Remove heading markers at start of lines: # ## ### etc
        result = result.lines().joinToString("\n") { line ->
            line.trimStart().let { trimmed ->
                if (trimmed.startsWith("#")) {
                    trimmed.dropWhile { it == '#' }.trimStart()
                } else {
                    line
                }
            }
        }
        
        // Remove inline code backticks: `code` -> code
        while (result.contains("`")) {
            val start = result.indexOf("`")
            val end = result.indexOf("`", start + 1)
            if (end > start) {
                val content = result.substring(start + 1, end)
                result = result.substring(0, start) + content + result.substring(end + 1)
            } else {
                result = result.replaceFirst("`", "")
            }
        }
        
        // Remove blockquote markers: > at start of line
        result = result.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith(">")) {
                line.trimStart().drop(1).trimStart()
            } else {
                line
            }
        }
        
        // Remove bullet points: - or * at start of line (but keep numbered lists)
        result = result.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            if ((trimmed.startsWith("- ") || trimmed.startsWith("* ")) && 
                !trimmed.first().isDigit()) {
                trimmed.drop(2)
            } else {
                line
            }
        }
        
        // Clean up multiple blank lines
        while (result.contains("\n\n\n")) {
            result = result.replace("\n\n\n", "\n\n")
        }
        
        // Clean up multiple spaces
        while (result.contains("  ")) {
            result = result.replace("  ", " ")
        }
        
        result = result.trim()
        
        Log.d(TAG, "cleanForTts output length: ${result.length}")
        Log.d(TAG, "cleanForTts output: $result")
        
        return result
    }

    private fun buildRequestBody(prompt: String): String {
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
                put("temperature", 0.7)
                put("maxOutputTokens", 4096)  // Increased to allow for thinking tokens
                put("topP", 0.95)
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

    private fun extractText(responseBody: String): String {
        return try {
            val json = Json.parseToJsonElement(responseBody).jsonObject
            val candidates = json["candidates"]?.jsonArray ?: return ""
            if (candidates.isEmpty()) return ""
            
            val firstCandidate = candidates[0].jsonObject
            val content = firstCandidate["content"]?.jsonObject ?: return ""
            val parts = content["parts"]?.jsonArray ?: return ""
            if (parts.isEmpty()) return ""
            
            val textElement = parts[0].jsonObject["text"] ?: return ""
            textElement.jsonPrimitive.content.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from response", e)
            ""
        }
    }

    /**
     * Rotate to the next model in the list.
     * @return true if rotation successful, false if all models exhausted
     */
    private fun rotateToNextModel(): Boolean {
        val nextIndex = currentModelIndex + 1
        return if (nextIndex < MODELS.size) {
            currentModelIndex = nextIndex
            true
        } else {
            false
        }
    }

    /**
     * Rotate to the next API key and reset model index.
     * @return true if rotation successful, false if all API keys exhausted
     */
    private fun rotateToNextApiKey(): Boolean {
        val nextIndex = currentApiKeyIndex + 1
        return if (nextIndex < apiKeys.size) {
            currentApiKeyIndex = nextIndex
            currentModelIndex = 0 // Reset to first model
            true
        } else {
            false
        }
    }

    /**
     * Get current status for debugging/UI display.
     */
    fun getCurrentStatus(): String {
        if (apiKeys.isEmpty()) {
            return "Chưa có API key"
        }
        return "API ${currentApiKeyIndex + 1}/${apiKeys.size}, Model: ${MODELS[currentModelIndex]}"
    }

    sealed class ApiResult {
        data class Success(val text: String) : ApiResult()
        object QuotaExceeded : ApiResult()
        object ModelNotFound : ApiResult()
        data class Error(val message: String) : ApiResult()
    }

    sealed class SummarizeResult {
        data class Success(val text: String, val model: String) : SummarizeResult()
        object AllQuotaExhausted : SummarizeResult()
        object NoApiKeys : SummarizeResult()
        data class Error(val message: String) : SummarizeResult()

        fun getTextOrFallback(): String {
            return when (this) {
                is Success -> text
                is AllQuotaExhausted -> "Xin lỗi, hệ thống đang quá tải. Vui lòng thử lại sau."
                is NoApiKeys -> "Vui lòng thêm API key Gemini trong Cài đặt."
                is Error -> "Không thể tóm tắt tin tức. Vui lòng thử lại sau."
            }
        }
    }
}
