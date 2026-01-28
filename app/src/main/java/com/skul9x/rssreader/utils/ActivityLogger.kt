package com.skul9x.rssreader.utils

import android.content.Context
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.model.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Singleton utility for logging activity events to the database.
 * Tracks shared link processing flow for debugging purposes.
 */
object ActivityLogger {
    
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private const val MAX_CONTENT_LENGTH = 50
    
    fun initialize(context: Context) {
        database = AppDatabase.getDatabase(context.applicationContext)
        // Clear all logs on app start
        clearAllLogs()
    }
    
    /**
     * Clear all activity logs (called on app start).
     */
    fun clearAllLogs() {
        scope.launch {
            try {
                database?.activityLogDao()?.deleteAll()
            } catch (e: Exception) {
                android.util.Log.e("ActivityLogger", "Failed to clear logs: ${e.message}")
            }
        }
    }
    
    // Event Types
    const val EVENT_LINK_RECEIVED = "LINK_RECEIVED"
    const val EVENT_FETCH_START = "FETCH_START"
    const val EVENT_FETCH_SUCCESS = "FETCH_SUCCESS"
    const val EVENT_FETCH_ERROR = "FETCH_ERROR"
    const val EVENT_GEMINI_START = "GEMINI_START"
    const val EVENT_GEMINI_SUCCESS = "GEMINI_SUCCESS"
    const val EVENT_GEMINI_ERROR = "GEMINI_ERROR"
    const val EVENT_HTTP_REQUEST = "HTTP_REQUEST"
    const val EVENT_HTTP_SUCCESS = "HTTP_SUCCESS"
    const val EVENT_HTTP_ERROR = "HTTP_ERROR"
    const val EVENT_JSOUP_PARSE = "JSOUP_PARSE"
    
    /**
     * Log when a shared link is received.
     */
    fun logLinkReceived(url: String) {
        logEvent(
            eventType = EVENT_LINK_RECEIVED,
            url = url,
            message = "Link được chia sẻ",
            details = null,
            isError = false
        )
    }
    
    /**
     * Log when content fetching starts.
     */
    fun logFetchStart(url: String) {
        logEvent(
            eventType = EVENT_FETCH_START,
            url = url,
            message = "Bắt đầu tải nội dung",
            details = null,
            isError = false
        )
    }
    
    /**
     * Log successful content extraction.
     * @param contentPreview First 50 characters of extracted content
     */
    fun logFetchSuccess(url: String, contentPreview: String) {
        val truncated = truncateContent(contentPreview)
        logEvent(
            eventType = EVENT_FETCH_SUCCESS,
            url = url,
            message = "Trích xuất nội dung thành công",
            details = "Nội dung: $truncated",
            isError = false
        )
    }
    
    /**
     * Log content extraction error.
     */
    fun logFetchError(url: String, error: String) {
        logEvent(
            eventType = EVENT_FETCH_ERROR,
            url = url,
            message = "Lỗi trích xuất nội dung",
            details = error,
            isError = true
        )
    }
    
    /**
     * Log when Gemini API call starts.
     */
    fun logGeminiStart(url: String, model: String = "") {
        logEvent(
            eventType = EVENT_GEMINI_START,
            url = url,
            message = "Bắt đầu tóm tắt với Gemini",
            details = if (model.isNotEmpty()) "Model: $model" else null,
            isError = false
        )
    }
    
    /**
     * Log successful Gemini summarization.
     * @param summaryPreview First 50 characters of summary
     */
    fun logGeminiSuccess(url: String, summaryPreview: String, model: String = "") {
        val truncated = truncateContent(summaryPreview)
        logEvent(
            eventType = EVENT_GEMINI_SUCCESS,
            url = url,
            message = "Tóm tắt thành công",
            details = "Tóm tắt: $truncated${if (model.isNotEmpty()) " | Model: $model" else ""}",
            isError = false
        )
    }
    
    /**
     * Log Gemini API error.
     */
    fun logGeminiError(url: String, error: String) {
        logEvent(
            eventType = EVENT_GEMINI_ERROR,
            url = url,
            message = "Lỗi Gemini API",
            details = error,
            isError = true
        )
    }
    
    /**
     * Log HTTP request start.
     */
    fun logHttpRequest(url: String) {
        logEvent(
            eventType = EVENT_HTTP_REQUEST,
            url = url,
            message = "Gửi HTTP request",
            details = null,
            isError = false
        )
    }
    
    /**
     * Log HTTP request success.
     */
    fun logHttpSuccess(url: String, statusCode: Int) {
        logEvent(
            eventType = EVENT_HTTP_SUCCESS,
            url = url,
            message = "HTTP request thành công",
            details = "Status code: $statusCode",
            isError = false
        )
    }
    
    /**
     * Log HTTP request error.
     */
    fun logHttpError(url: String, error: String) {
        logEvent(
            eventType = EVENT_HTTP_ERROR,
            url = url,
            message = "HTTP request thất bại",
            details = error,
            isError = true
        )
    }
    
    /**
     * Log Jsoup parsing.
     */
    fun logJsoupParse(url: String, success: Boolean, details: String? = null) {
        logEvent(
            eventType = EVENT_JSOUP_PARSE,
            url = url,
            message = if (success) "Jsoup parse thành công" else "Jsoup parse thất bại",
            details = details,
            isError = !success
        )
    }
    
    /**
     * Generic log method for custom events.
     */
    fun log(
        eventType: String,
        url: String,
        message: String,
        details: String? = null,
        isError: Boolean = false
    ) {
        logEvent(
            eventType = eventType,
            url = url,
            message = message,
            details = details,
            isError = isError
        )
    }
    
    /**
     * Truncate content to MAX_CONTENT_LENGTH characters.
     */
    private fun truncateContent(content: String): String {
        return if (content.length > MAX_CONTENT_LENGTH) {
            content.take(MAX_CONTENT_LENGTH) + "..."
        } else {
            content
        }
    }
    
    /**
     * Internal method to log an event to the database.
     */
    private fun logEvent(
        eventType: String,
        url: String,
        message: String,
        details: String?,
        isError: Boolean
    ) {
        scope.launch {
            try {
                database?.activityLogDao()?.insert(
                    ActivityLog(
                        eventType = eventType,
                        url = url,
                        message = message,
                        details = details,
                        isError = isError
                    )
                )
            } catch (e: Exception) {
                // Silently fail to avoid disrupting the main flow
                android.util.Log.e("ActivityLogger", "Failed to log event: ${e.message}")
            }
        }
    }
}
