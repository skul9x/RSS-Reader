package com.skul9x.rssreader.ui.sharedlink

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skul9x.rssreader.data.model.NewsItem
import com.skul9x.rssreader.data.network.ArticleContentFetcher
import com.skul9x.rssreader.data.network.GeminiApiClient
import com.skul9x.rssreader.service.NewsReaderService
import com.skul9x.rssreader.utils.ActivityLogger
import com.skul9x.rssreader.utils.DebugLogger
import com.skul9x.rssreader.utils.VietnameseDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for handling shared links from other apps.
 * Uses ArticleContentFetcher (Jsoup) for content extraction and GeminiApiClient for summarization.
 */
class SharedLinkViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "SharedLinkViewModel"
    }
    
    private val articleFetcher = ArticleContentFetcher().apply {
        setCustomSelectorManager(com.skul9x.rssreader.data.local.CustomSelectorManager.getInstance(application))
    }
    private val geminiClient = GeminiApiClient(application)
    
    // Track current processing job to cancel on new URL (prevents race condition)
    private var processJob: Job? = null
    
    private val _uiState = MutableStateFlow(SharedLinkUiState())
    val uiState: StateFlow<SharedLinkUiState> = _uiState.asStateFlow()
    
    /**
     * Process a URL - fetch content and summarize.
     * @param url The URL to process
     */
    fun processUrl(url: String) {
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "URL không hợp lệ") }
            return
        }
        
        // Cancel any in-progress processing to prevent race condition
        processJob?.cancel()
        
        // Log link received
        ActivityLogger.logLinkReceived(url)
        DebugLogger.log(TAG, "Processing URL: $url")
        
        processJob = viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    url = url,
                    title = null, // Will be updated when we fetch the article
                    error = null,
                    summary = null
                ) 
            }
            
            try {
                // 1. Fetch content AND title using Jsoup (ArticleContentFetcher)
                ActivityLogger.logFetchStart(url)
                DebugLogger.log(TAG, "Fetching article content and title...")
                val articleData = articleFetcher.fetchArticleWithTitle(url)
                
                if (articleData != null && articleData.content.isNotBlank()) {
                    ActivityLogger.logFetchSuccess(url, articleData.content)
                    DebugLogger.log(TAG, "Content fetched: ${articleData.content.length} chars, Title: ${articleData.title}")
                    
                    val originalTitle = articleData.title
                    val needsTranslation = VietnameseDetector.needsTranslation(originalTitle)
                    
                    // Update UI with original content first
                    _uiState.update { 
                        it.copy(
                            originalContent = articleData.content,
                            title = originalTitle  // Show original title immediately
                        ) 
                    }
                    
                    // Run translation and summarization IN PARALLEL
                    coroutineScope {
                        // Start translation async (if needed)
                        val translationDeferred = if (needsTranslation) {
                            DebugLogger.log(TAG, "Title is not Vietnamese, translating...")
                            ActivityLogger.log(
                                eventType = "TITLE_TRANSLATE",
                                url = url,
                                message = "Dịch tiêu đề ngoại ngữ",
                                details = "Original: $originalTitle",
                                isError = false
                            )
                            async { geminiClient.translateToVietnamese(originalTitle) }
                        } else null
                        
                        // Start summarization async
                        ActivityLogger.logGeminiStart(url)
                        DebugLogger.log(TAG, "Summarizing with Gemini...")
                        val summaryDeferred = async { geminiClient.summarizeForTts(articleData.content, url) }
                        
                        // Wait for both results
                        val translatedTitle = translationDeferred?.await() ?: originalTitle
                        val result = summaryDeferred.await()
                        val summaryText = result.getTextOrFallback()
                        
                        // Log translation result
                        if (needsTranslation) {
                            DebugLogger.log(TAG, "Translated title: $translatedTitle")
                        }
                        
                        // Log Gemini result
                        when (result) {
                            is GeminiApiClient.SummarizeResult.Success -> {
                                ActivityLogger.logGeminiSuccess(url, summaryText, result.model)
                            }
                            is GeminiApiClient.SummarizeResult.Error -> {
                                ActivityLogger.logGeminiError(url, result.message)
                            }
                            else -> {
                                ActivityLogger.logGeminiError(url, "Không có API key hoặc hết quota")
                            }
                        }
                        
                        DebugLogger.log(TAG, "Summary received: ${summaryText.length} chars")
                        
                        // SINGLE atomic UI update to avoid race condition
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                title = translatedTitle,
                                summary = summaryText,
                                status = "Đã tóm tắt thành công"
                            )
                        }
                    }
                } else {
                    ActivityLogger.logFetchError(url, "Không thể trích xuất nội dung hoặc nội dung trống")
                    DebugLogger.log(TAG, "Failed to fetch content")
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = "Không thể trích xuất nội dung từ trang web này"
                        ) 
                    }
                }
            } catch (e: CancellationException) {
                // Re-throw CancellationException to properly cancel coroutine
                throw e
            } catch (e: Exception) {
                ActivityLogger.logFetchError(url, "Exception: ${e.message}")
                Log.e(TAG, "Error processing URL", e)
                DebugLogger.log(TAG, "Error: ${e.message}")
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = "Lỗi: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * Read the summary using TTS via NewsReaderService.
     */
    fun readSummary() {
        val state = _uiState.value
        val summary = state.summary ?: return
        val url = state.url
        val title = state.title ?: "Link được chia sẻ"
        
        DebugLogger.log(TAG, "Starting TTS for shared link")
        
        // Create a NewsItem to pass to the service
        val newsItem = NewsItem(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            description = summary,
            link = url,
            pubDate = "",
            sourceName = "Shared Link",
            content = state.originalContent ?: summary
        )
        
        // Start NewsReaderService
        val context = getApplication<Application>()
        val intent = Intent(context, NewsReaderService::class.java).apply {
            action = NewsReaderService.ACTION_READ_SINGLE
            putExtra(NewsReaderService.EXTRA_NEWS_ITEM, newsItem)
            putExtra(NewsReaderService.EXTRA_READ_FULL, false)
        }
        context.startService(intent)
    }
    
    /**
     * Read the full original content using TTS.
     */
    fun readFullContent() {
        val state = _uiState.value
        val content = state.originalContent ?: return
        val url = state.url
        val title = state.title ?: "Link được chia sẻ"
        
        DebugLogger.log(TAG, "Starting TTS for full content")
        
        val newsItem = NewsItem(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            description = content,
            link = url,
            pubDate = "",
            sourceName = "Shared Link",
            content = content
        )
        
        val context = getApplication<Application>()
        val intent = Intent(context, NewsReaderService::class.java).apply {
            action = NewsReaderService.ACTION_READ_SINGLE
            putExtra(NewsReaderService.EXTRA_NEWS_ITEM, newsItem)
            putExtra(NewsReaderService.EXTRA_READ_FULL, true)
        }
        context.startService(intent)
    }
    
    /**
     * Stop TTS playback.
     */
    fun stopReading() {
        val context = getApplication<Application>()
        val intent = Intent(context, NewsReaderService::class.java).apply {
            action = NewsReaderService.ACTION_STOP
        }
        context.startService(intent)
    }
    
    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/**
 * UI State for SharedLinkScreen.
 */
data class SharedLinkUiState(
    val url: String = "",
    val title: String? = null,
    val isLoading: Boolean = false,
    val status: String = "",
    val originalContent: String? = null,
    val summary: String? = null,
    val error: String? = null
)
