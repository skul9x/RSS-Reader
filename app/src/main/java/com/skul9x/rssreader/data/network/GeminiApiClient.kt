package com.skul9x.rssreader.data.network

import android.content.Context
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.NewsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

/**
 * Gemini API client with automatic failover between API keys and models.
 * Cycles through models when quota is exceeded, then switches to next API key.
 * API keys are loaded dynamically from ApiKeyManager (user-configurable in Settings).
 * 
 * Thread-safe implementation using Mutex for coroutine synchronization.
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
    private val newsSummaryDao = AppDatabase.getDatabase(context).newsSummaryDao()
    
    // Mutex for thread-safe access to mutable state
    private val stateMutex = Mutex()
    
    // API keys loaded dynamically from storage (protected by stateMutex)
    @Volatile
    private var apiKeys: List<String> = apiKeyManager.getApiKeys().toList() // Defensive copy

    // Use shared OkHttpClient singleton
    private val client = NetworkClient.okHttpClient

    // State variables protected by stateMutex
    @Volatile
    private var currentApiKeyIndex = 0
    @Volatile
    private var currentModelIndex = 0

    /**
     * Refresh API keys from storage (call after user adds/removes keys).
     * Thread-safe.
     */
    suspend fun refreshApiKeys() {
        stateMutex.withLock {
            apiKeys = apiKeyManager.getApiKeys().toList() // Defensive copy
            // Reset indices if current index is out of bounds
            if (apiKeys.isEmpty() || currentApiKeyIndex >= apiKeys.size) {
                currentApiKeyIndex = 0
            }
        }
    }
    
    /**
     * Non-suspend version for backward compatibility.
     * Uses synchronized for thread-safety.
     */
    @Synchronized
    fun refreshApiKeysSync() {
        apiKeys = apiKeyManager.getApiKeys().toList() // Defensive copy
        // Reset indices if current index is out of bounds
        if (apiKeys.isEmpty() || currentApiKeyIndex >= apiKeys.size) {
            currentApiKeyIndex = 0
        }
    }

    /**
     * Check if any API keys are configured.
     * Thread-safe due to @Volatile.
     */
    fun hasApiKeys(): Boolean = apiKeys.isNotEmpty()

    /**
     * Reset to first API key and first model.
     * Thread-safe.
     */
    suspend fun reset() {
        stateMutex.withLock {
            currentApiKeyIndex = 0
            currentModelIndex = 0
        }
    }
    
    /**
     * Non-suspend version for backward compatibility.
     */
    @Synchronized
    fun resetSync() {
        currentApiKeyIndex = 0
        currentModelIndex = 0
    }

    /**
     * Cleanup old cache entries.
     * @param retentionPeriodMs Data older than this will be deleted.
     */
    suspend fun cleanupCache(retentionPeriodMs: Long) {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - retentionPeriodMs
            newsSummaryDao.deleteOldSummaries(cutoffTime)
            Log.d(TAG, "Cleaned up cache older than $retentionPeriodMs ms")
        }
    }

    /**
     * Summarize news content for Vietnamese TTS.
     * Automatically handles quota errors with model/API key rotation.
     * Checks local cache first to save quota.
     *
     * @param content The news content to summarize
     * @param url The URL of the news item (used as cache key)
     * @return Summarized text in Vietnamese, or fallback message on complete failure
     */
    suspend fun summarizeForTts(content: String, url: String): SummarizeResult {
        return withContext(Dispatchers.IO) {
            // 1. Check Cache
            val cachedSummary = newsSummaryDao.getSummary(url)
            if (cachedSummary != null) {
                Log.d(TAG, "Cache HIT for $url")
                return@withContext SummarizeResult.Success(cachedSummary.summary, "CACHE")
            }

            Log.d(TAG, "Cache MISS for $url - Calling API")
            
            // 2. Call API
            val result = summarizeWithRetry(content)
            
            // 3. Save to Cache if successful
            if (result is SummarizeResult.Success) {
                newsSummaryDao.insertSummary(NewsSummary(url, result.text))
            }
            
            result
        }
    }

    private suspend fun summarizeWithRetry(content: String): SummarizeResult {
        // Thread-safe read of current state
        val (keys, startApiKeyIndex, startModelIndex) = stateMutex.withLock {
            Triple(apiKeys.toList(), currentApiKeyIndex, currentModelIndex)
        }
        
        // Check if any API keys are configured
        if (keys.isEmpty()) {
            return SummarizeResult.NoApiKeys
        }
        
        var localApiKeyIndex = startApiKeyIndex
        var localModelIndex = startModelIndex

        do {
            // Bounds check before accessing
            if (localApiKeyIndex >= keys.size) {
                localApiKeyIndex = 0
            }
            if (localModelIndex >= MODELS.size) {
                localModelIndex = 0
            }
            
            val apiKey = keys[localApiKeyIndex]
            val model = MODELS[localModelIndex]

            Log.d(TAG, "Trying API key ${localApiKeyIndex + 1}/${keys.size}, Model: $model")

            val result = tryGenerateContent(apiKey, model, content)

            when (result) {
                is ApiResult.Success -> {
                    // Update shared state with successful indices
                    stateMutex.withLock {
                        currentApiKeyIndex = localApiKeyIndex
                        currentModelIndex = localModelIndex
                    }
                    return SummarizeResult.Success(result.text, model)
                }
                is ApiResult.QuotaExceeded -> {
                    Log.w(TAG, "Quota exceeded for $model with API key ${localApiKeyIndex + 1}")
                    val rotated = rotateToNextModelLocal(localModelIndex)
                    if (rotated != null) {
                        localModelIndex = rotated
                    } else {
                        // All models exhausted for current API key, try next API key
                        val rotatedKey = rotateToNextApiKeyLocal(localApiKeyIndex, keys.size)
                        if (rotatedKey != null) {
                            localApiKeyIndex = rotatedKey
                            localModelIndex = 0 // Reset model index
                        } else {
                            // All API keys exhausted
                            return SummarizeResult.AllQuotaExhausted
                        }
                    }
                }
                is ApiResult.ModelNotFound -> {
                    Log.w(TAG, "Model not found: $model")
                    val rotated = rotateToNextModelLocal(localModelIndex)
                    if (rotated != null) {
                        localModelIndex = rotated
                    } else {
                        val rotatedKey = rotateToNextApiKeyLocal(localApiKeyIndex, keys.size)
                        if (rotatedKey != null) {
                            localApiKeyIndex = rotatedKey
                            localModelIndex = 0
                        } else {
                            return SummarizeResult.AllQuotaExhausted
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "API error: ${result.message}")
                    // Try next model on generic errors
                    val rotated = rotateToNextModelLocal(localModelIndex)
                    if (rotated != null) {
                        localModelIndex = rotated
                    } else {
                        val rotatedKey = rotateToNextApiKeyLocal(localApiKeyIndex, keys.size)
                        if (rotatedKey != null) {
                            localApiKeyIndex = rotatedKey
                            localModelIndex = 0
                        } else {
                            return SummarizeResult.Error(result.message)
                        }
                    }
                }
            }

            // Prevent infinite loop - check if we've cycled through all combinations
        } while (localApiKeyIndex != startApiKeyIndex || localModelIndex != startModelIndex)

        return SummarizeResult.AllQuotaExhausted
    }
    
    /**
     * Local rotation helper - returns new index or null if exhausted.
     */
    private fun rotateToNextModelLocal(currentIndex: Int): Int? {
        val nextIndex = currentIndex + 1
        return if (nextIndex < MODELS.size) nextIndex else null
    }
    
    /**
     * Local rotation helper - returns new index or null if exhausted.
     */
    private fun rotateToNextApiKeyLocal(currentIndex: Int, keysSize: Int): Int? {
        val nextIndex = currentIndex + 1
        return if (nextIndex < keysSize) nextIndex else null
    }

    /**
     * Try to generate content with proper cancellation support.
     * Uses suspendCancellableCoroutine to cancel HTTP request when coroutine is cancelled.
     */
    private suspend fun tryGenerateContent(apiKey: String, model: String, content: String): ApiResult {
        return try {
            // Check if already cancelled before making request
            coroutineContext.ensureActive()
            
            val prompt = buildSummarizationPrompt(content)
            val requestBody = buildRequestBody(prompt)
            
            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            // Use suspendCancellableCoroutine for proper cancellation
            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = client.newCall(request)
                
                // Cancel HTTP request when coroutine is cancelled
                continuation.invokeOnCancellation {
                    Log.d(TAG, "HTTP request cancelled for model: $model")
                    call.cancel()
                }
                
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(e)
                        }
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isCompleted) {
                            continuation.resume(response) { cause ->
                                // If continuation is cancelled while we have a response, close it
                                response.close()
                            }
                        } else {
                            // Response arrived but continuation already completed, close it
                            response.close()
                        }
                    }
                })
            }
            
            // Use response.use{} to ensure proper resource cleanup
            response.use { resp ->
                val responseBody = resp.body?.string() ?: ""
                
                when (resp.code) {
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
                    else -> ApiResult.Error("HTTP ${resp.code}: $responseBody")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "API call cancelled")
            throw e  // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API call", e)
            ApiResult.Error(e.message ?: "Unknown error")
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
     * Uses regex for robust pattern matching with safeguards against infinite loops.
     */
    fun cleanForTts(text: String): String {
        Log.d(TAG, "cleanForTts input length: ${text.length}")
        Log.d(TAG, "cleanForTts input: $text")
        
        var result = text
        
        // Remove bold markers: **text** -> text (using regex for safety)
        result = result.replace(Regex("""\*\*([^*]*)\*\*""")) { it.groupValues[1] }
        // Remove any remaining orphan ** markers
        result = result.replace("**", "")
        
        // Remove italic markers: *text* -> text (but not ** which is already handled)
        result = result.replace(Regex("""\*([^*]+)\*""")) { it.groupValues[1] }
        // Remove any remaining orphan * markers at word boundaries
        result = result.replace(Regex("""(?<!\*)\*(?!\*)"""), "")
        
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
        
        // Remove inline code backticks: `code` -> code (using regex)
        result = result.replace(Regex("""`([^`]*)`""")) { it.groupValues[1] }
        // Remove any remaining orphan backticks
        result = result.replace("`", "")
        
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
                trimmed.firstOrNull()?.isDigit() != true) {
                trimmed.drop(2)
            } else {
                line
            }
        }
        
        // Clean up multiple blank lines (using regex for efficiency)
        result = result.replace(Regex("""\n{3,}"""), "\n\n")
        
        // Clean up multiple spaces (using regex for efficiency)
        result = result.replace(Regex(""" {2,}"""), " ")
        
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
     * Get current status for debugging/UI display.
     * Thread-safe.
     */
    fun getCurrentStatus(): String {
        // Read volatile variables
        val keys = apiKeys
        val keyIndex = currentApiKeyIndex
        val modelIndex = currentModelIndex
        
        if (keys.isEmpty()) {
            return "Chưa có API key"
        }
        
        // Safe bounds check
        val safeKeyIndex = if (keyIndex < keys.size) keyIndex else 0
        val safeModelIndex = if (modelIndex < MODELS.size) modelIndex else 0
        
        return "API ${safeKeyIndex + 1}/${keys.size}, Model: ${MODELS[safeModelIndex]}"
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
