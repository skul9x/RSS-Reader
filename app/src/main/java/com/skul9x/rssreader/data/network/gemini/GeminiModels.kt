package com.skul9x.rssreader.data.network.gemini

/**
 * Result class for generic API calls.
 */
sealed class ApiResult {
    data class Success(val text: String) : ApiResult()
    object QuotaExceeded : ApiResult()
    object ServerBusy : ApiResult()
    object ModelNotFound : ApiResult()
    object NetworkError : ApiResult()
    data class Error(val message: String) : ApiResult()
}

/**
 * Result class for Summarization.
 */
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
