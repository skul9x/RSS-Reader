package com.skul9x.rssreader.data.network

import android.content.Context
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.NewsSummary
import com.skul9x.rssreader.utils.ActivityLogger
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
    
    /**
     * Translate text to Vietnamese using lightweight models.
     * 
     * Strategy: Try same model across ALL API keys before moving to heavier model.
     * This ensures we always use the lightest model available.
     * 
     * @param text The text to translate
     * @return Translated text in Vietnamese, or original text if translation fails
     */
    suspend fun translateToVietnamese(text: String): String {
        if (text.isBlank()) return text
        
        // Models for translation (ordered from lightest to heaviest)
        val translationModels = listOf(
            "models/gemma-3-1b-it",       // 1B params - lightest, fastest
            "models/gemma-3-4b-it",       // 4B params - balanced
            "models/gemini-2.0-flash-lite",
            "models/gemini-2.5-flash-lite"
        )
        
        return withContext(Dispatchers.IO) {
            val keys = apiKeys.toList()
            if (keys.isEmpty()) {
                Log.w(TAG, "No API keys for translation")
                return@withContext text
            }
            
            // Strategy: For each model, try ALL API keys before moving to next model
            for (model in translationModels) {
                for (keyIndex in keys.indices) {
                    val apiKey = keys[keyIndex]
                    
                    try {
                        Log.d(TAG, "Trying translation: $model with API key ${keyIndex + 1}/${keys.size}")
                        val result = tryTranslate(apiKey, model, text)
                        
                        when (result) {
                            is ApiResult.Success -> {
                                Log.d(TAG, "Translation successful: $model | API ${keyIndex + 1}")
                                return@withContext result.text
                            }
                            is ApiResult.QuotaExceeded -> {
                                Log.w(TAG, "Quota exceeded: $model | API ${keyIndex + 1}, trying next API key...")
                                continue // Try next API key for same model
                            }
                            else -> {
                                Log.w(TAG, "Translation failed: $model | API ${keyIndex + 1}, trying next...")
                                continue
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Translation exception: $model | API ${keyIndex + 1}", e)
                        continue
                    }
                }
                // All API keys exhausted for this model, try next model
                Log.w(TAG, "All API keys exhausted for $model, trying next model...")
            }
            
            // All models and API keys failed
            Log.w(TAG, "All translation attempts failed, returning original text")
            text
        }
    }

    /**
     * Translate a batch of titles to Vietnamese in one go.
     * 
     * @param titles Map of ID -> Original Title
     * @return Map of ID -> Translated Title
     */
    suspend fun translateTitleBatch(titles: Map<String, String>): Map<String, String> {
        if (titles.isEmpty()) return emptyMap()
        
        // Models for translation (use faster models for batch)
        val translationModels = listOf(
            "models/gemini-2.0-flash-lite", // Fast and cheap
            "models/gemini-2.5-flash-lite",
            "models/gemini-2.0-flash"
        )
        
        return withContext(Dispatchers.IO) {
            val keys = apiKeys.toList()
            if (keys.isEmpty()) {
                Log.w(TAG, "No API keys for translation")
                return@withContext emptyMap()
            }
            
            // Build the batch prompt
            val itemsJson = titles.entries.joinToString(",\n") { (id, title) ->
                "\"$id\": \"${title.replace("\"", "\\\"")}\""
            }
            val prompt = """
                Dịch các tiêu đề sau sang tiếng Việt. 
                Trả về kết quả dạng JSON object với format {"id": "tiêu đề đã dịch"}.
                Giữ nguyên ID. Chỉ dịch giá trị.
                
                Input:
                {
                $itemsJson
                }
            """.trimIndent()
            
            // Strategy: Try models
            for (model in translationModels) {
                for (keyIndex in keys.indices) {
                    val apiKey = keys[keyIndex]
                    try {
                        Log.d(TAG, "Trying batch translation: $model | API ${keyIndex + 1}")
                        val result = tryTranslate(apiKey, model, prompt)
                        
                        when (result) {
                            is ApiResult.Success -> {
                                val jsonStr = result.text.substringAfter("{").substringBeforeLast("}")
                                val fullJson = "{$jsonStr}"
                                try {
                                    val parsed = Json.parseToJsonElement(fullJson).jsonObject
                                    val resultMap = parsed.entries.associate { (k, v) ->
                                        k to v.jsonPrimitive.content
                                    }
                                    Log.d(TAG, "Batch translation success: ${resultMap.size} items")
                                    return@withContext resultMap
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse batch response", e)
                                    // Try next pattern if parsing fails? Or just continue to next model?
                                    continue
                                }
                            }
                            is ApiResult.QuotaExceeded -> continue
                            else -> continue
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            
            Log.w(TAG, "Batch translation failed on all attempts")
            emptyMap()
        }
    }
    
    /**
     * Try to translate text using Gemini API.
     */
    private suspend fun tryTranslate(apiKey: String, model: String, text: String): ApiResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            coroutineContext.ensureActive()
            
            ActivityLogger.log(
                eventType = "TRANSLATE_START",
                url = "",
                message = "Bắt đầu dịch tiêu đề",
                details = "Model: $model | Text: ${text.take(50)}...",
                isError = false
            )
            
            val prompt = buildTranslationPrompt(text)
            val requestBody = buildRequestBody(prompt)
            
            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            ActivityLogger.log(
                eventType = "TRANSLATE_HTTP",
                url = "",
                message = "Gửi request dịch",
                details = "URL: $BASE_URL/$model",
                isError = false
            )
            
            val httpStartTime = System.currentTimeMillis()
            
            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = client.newCall(request)
                
                continuation.invokeOnCancellation {
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
                                response.close()
                            }
                        } else {
                            response.close()
                        }
                    }
                })
            }
            
            val httpDuration = System.currentTimeMillis() - httpStartTime
            
            response.use { resp ->
                val responseBody = resp.body?.string() ?: ""
                
                when (resp.code) {
                    200 -> {
                        val rawText = extractText(responseBody)
                        val totalDuration = System.currentTimeMillis() - startTime
                        
                        if (rawText.isNotBlank()) {
                            // Clean up translation result
                            val cleanedText = rawText
                                .replace(Regex("^(Bản dịch:|Dịch:|Translation:)\\s*", RegexOption.IGNORE_CASE), "")
                                .trim()
                            
                            ActivityLogger.log(
                                eventType = "TRANSLATE_SUCCESS",
                                url = "",
                                message = "Dịch thành công (${totalDuration}ms)",
                                details = "HTTP: ${httpDuration}ms | Result: ${cleanedText.take(50)}...",
                                isError = false
                            )
                            
                            ApiResult.Success(cleanedText)
                        } else {
                            ActivityLogger.log(
                                eventType = "TRANSLATE_ERROR",
                                url = "",
                                message = "Response rỗng (${totalDuration}ms)",
                                details = "HTTP: ${httpDuration}ms",
                                isError = true
                            )
                            ApiResult.Error("Empty translation response")
                        }
                    }
                    429 -> {
                        ActivityLogger.log(
                            eventType = "TRANSLATE_ERROR",
                            url = "",
                            message = "Quota exceeded",
                            details = "Model: $model",
                            isError = true
                        )
                        ApiResult.QuotaExceeded
                    }
                    else -> {
                        ActivityLogger.log(
                            eventType = "TRANSLATE_ERROR",
                            url = "",
                            message = "HTTP ${resp.code}",
                            details = responseBody.take(200),
                            isError = true
                        )
                        ApiResult.Error("HTTP ${resp.code}")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            ActivityLogger.log(
                eventType = "TRANSLATE_ERROR",
                url = "",
                message = "Exception (${totalDuration}ms)",
                details = "${e.javaClass.simpleName}: ${e.message}",
                isError = true
            )
            ApiResult.Error(e.message ?: "Translation error")
        }
    }
    
    /**
     * Build translation prompt.
     */
    private fun buildTranslationPrompt(text: String): String {
        return """
Dịch đoạn text sau sang tiếng Việt. Chỉ trả về bản dịch, không giải thích hay thêm bất cứ điều gì khác.

Text: $text
""".trim()
    }

    /**
     * Summarize with retry using MODEL-FIRST rotation strategy.
     * 
     * Strategy: Try same model across ALL API keys before moving to next model.
     * Example: gemini-2.5-flash (Key1 → Key2 → Key3) → gemini-2.5-flash-lite (Key1 → Key2...)
     * 
     * This prioritizes using the best/fastest model available across all keys.
     */
    private suspend fun summarizeWithRetry(content: String): SummarizeResult {
        // Thread-safe read of current state
        val keys = stateMutex.withLock { apiKeys.toList() }
        
        // Check if any API keys are configured
        if (keys.isEmpty()) {
            return SummarizeResult.NoApiKeys
        }
        
        // Strategy: For each model, try ALL API keys before moving to next model
        for (modelIndex in MODELS.indices) {
            val model = MODELS[modelIndex]
            
            for (keyIndex in keys.indices) {
                val apiKey = keys[keyIndex]
                
                Log.d(TAG, "Trying Model: $model | API key ${keyIndex + 1}/${keys.size}")

                val result = tryGenerateContent(apiKey, model, content)

                when (result) {
                    is ApiResult.Success -> {
                        // Update shared state with successful indices
                        stateMutex.withLock {
                            currentApiKeyIndex = keyIndex
                            currentModelIndex = modelIndex
                        }
                        Log.d(TAG, "Summarize SUCCESS: $model | API key ${keyIndex + 1}")
                        return SummarizeResult.Success(result.text, model)
                    }
                    is ApiResult.QuotaExceeded -> {
                        Log.w(TAG, "Quota exceeded: $model | API key ${keyIndex + 1}, trying next key...")
                        continue // Try next API key for same model
                    }
                    is ApiResult.ModelNotFound -> {
                        Log.w(TAG, "Model not found: $model, skipping to next model...")
                        break // Skip to next model (all keys will have same result)
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "API error: ${result.message} | $model | API key ${keyIndex + 1}")
                        continue // Try next API key
                    }
                }
            }
            // All API keys exhausted for this model, try next model
            Log.w(TAG, "All API keys exhausted for $model, trying next model...")
        }
        
        // All models and API keys exhausted
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

    /**
     * Result class for CSS selector suggestion.
     */
    sealed class SuggestClassResult {
        data class Success(val selector: String, val model: String) : SuggestClassResult()
        object AllQuotaExhausted : SuggestClassResult()
        object NoApiKeys : SuggestClassResult()
        data class Error(val message: String) : SuggestClassResult()

        fun getSelectorOrNull(): String? {
            return when (this) {
                is Success -> if (selector != "NOT_FOUND") selector else null
                else -> null
            }
        }

        fun getErrorMessage(): String {
            return when (this) {
                is Success -> ""
                is AllQuotaExhausted -> "Hệ thống đang quá tải. Vui lòng thử lại sau."
                is NoApiKeys -> "Vui lòng thêm API key Gemini trong Cài đặt."
                is Error -> message
            }
        }
    }

    /**
     * Suggest the best CSS class/selector to extract article content from raw HTML.
     * Uses Gemini API with automatic model rotation on quota exceeded.
     *
     * @param rawHtml The raw HTML content to analyze
     * @return SuggestClassResult containing suggested selector or error
     */
    suspend fun suggestContentClass(rawHtml: String): SuggestClassResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "suggestContentClass called, HTML length: ${rawHtml.length}")
            suggestWithRetry(rawHtml)
        }
    }

    private suspend fun suggestWithRetry(rawHtml: String): SuggestClassResult {
        // Thread-safe read of current state
        val (keys, startApiKeyIndex, startModelIndex) = stateMutex.withLock {
            Triple(apiKeys.toList(), currentApiKeyIndex, currentModelIndex)
        }

        // Check if any API keys are configured
        if (keys.isEmpty()) {
            return SuggestClassResult.NoApiKeys
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

            Log.d(TAG, "SuggestClass: Trying API key ${localApiKeyIndex + 1}/${keys.size}, Model: $model")

            val result = trySuggestContent(apiKey, model, rawHtml)

            when (result) {
                is ApiResult.Success -> {
                    // Update shared state with successful indices
                    stateMutex.withLock {
                        currentApiKeyIndex = localApiKeyIndex
                        currentModelIndex = localModelIndex
                    }
                    return SuggestClassResult.Success(result.text.trim(), model)
                }
                is ApiResult.QuotaExceeded -> {
                    Log.w(TAG, "SuggestClass: Quota exceeded for $model with API key ${localApiKeyIndex + 1}")
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
                            return SuggestClassResult.AllQuotaExhausted
                        }
                    }
                }
                is ApiResult.ModelNotFound -> {
                    Log.w(TAG, "SuggestClass: Model not found: $model")
                    val rotated = rotateToNextModelLocal(localModelIndex)
                    if (rotated != null) {
                        localModelIndex = rotated
                    } else {
                        val rotatedKey = rotateToNextApiKeyLocal(localApiKeyIndex, keys.size)
                        if (rotatedKey != null) {
                            localApiKeyIndex = rotatedKey
                            localModelIndex = 0
                        } else {
                            return SuggestClassResult.AllQuotaExhausted
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "SuggestClass: API error: ${result.message}")
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
                            return SuggestClassResult.Error(result.message)
                        }
                    }
                }
            }

            // Prevent infinite loop - check if we've cycled through all combinations
        } while (localApiKeyIndex != startApiKeyIndex || localModelIndex != startModelIndex)

        return SuggestClassResult.AllQuotaExhausted
    }

    /**
     * Try to get content class suggestion from Gemini API.
     */
    private suspend fun trySuggestContent(apiKey: String, model: String, rawHtml: String): ApiResult {
        return try {
            coroutineContext.ensureActive()

            // Truncate HTML if too long to fit in context window
            val truncatedHtml = if (rawHtml.length > 50000) {
                rawHtml.take(50000) + "\n... [TRUNCATED]"
            } else {
                rawHtml
            }

            val prompt = buildSuggestClassPrompt(truncatedHtml)
            val requestBody = buildRequestBody(prompt)

            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = client.newCall(request)

                continuation.invokeOnCancellation {
                    Log.d(TAG, "SuggestClass: HTTP request cancelled for model: $model")
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
                                response.close()
                            }
                        } else {
                            response.close()
                        }
                    }
                })
            }

            response.use { resp ->
                val responseBody = resp.body?.string() ?: ""

                when (resp.code) {
                    200 -> {
                        val rawText = extractText(responseBody)
                        if (rawText.isNotBlank()) {
                            // Clean up the response - remove any extra whitespace or quotes
                            val cleanedSelector = rawText
                                .trim()
                                .removeSurrounding("\"")
                                .removeSurrounding("'")
                                .trim()
                            ApiResult.Success(cleanedSelector)
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
            Log.d(TAG, "SuggestClass: API call cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SuggestClass: Exception during API call", e)
            ApiResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Build prompt for suggesting content class.
     */
    private fun buildSuggestClassPrompt(rawHtml: String): String {
        return """
Bạn là chuyên gia phân tích HTML. Nhiệm vụ của bạn là phân tích HTML sau và tìm CSS class hoặc selector tốt nhất để lấy nội dung chính của bài viết (article content).

YÊU CẦU BẮT BUỘC:
1. Chỉ trả về ĐÚNG 1 CSS selector duy nhất (ví dụ: .article-content, #main-body, div.post-body, article.content).
2. TUYỆT ĐỐI KHÔNG trả về giải thích, chỉ trả về selector duy nhất.
3. Ưu tiên class chứa nội dung bài viết chính, bỏ qua sidebar, menu, header, footer, quảng cáo, comments.
4. Selector phải tồn tại trong HTML được cung cấp.
5. Nếu không tìm được class phù hợp, chỉ trả về: NOT_FOUND

HTML để phân tích:
$rawHtml
""".trim()
    }
}
