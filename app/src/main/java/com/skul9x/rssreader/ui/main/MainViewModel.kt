package com.skul9x.rssreader.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.model.NewsItem
import com.skul9x.rssreader.data.network.ArticleContentFetcher
import com.skul9x.rssreader.data.network.GeminiApiClient
import com.skul9x.rssreader.data.repository.RssRepository
import com.skul9x.rssreader.tts.TtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main ViewModel for the news display screen.
 * Handles news fetching, AI summarization, and TTS playback coordination.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val DEBOUNCE_MS = 500L  // Debounce for Next/Previous buttons
    }

    private val database = AppDatabase.getDatabase(application)
    private val repository = RssRepository(database.rssFeedDao(), database.cachedNewsDao(), database.readNewsDao())
    private val geminiClient = GeminiApiClient(application)
    private val articleFetcher = ArticleContentFetcher()
    val ttsManager = TtsManager(application)

    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _selectedNewsIndex = MutableStateFlow(-1)
    val selectedNewsIndex: StateFlow<Int> = _selectedNewsIndex.asStateFlow()

    init {
        ttsManager.initialize()
        initializeAndFetchNews()
        observeServiceState()
    }
    
    /**
     * Observe the NewsReaderService state to sync UI (blinking, index highlighting).
     */
    private fun observeServiceState() {
        viewModelScope.launch {
            com.skul9x.rssreader.service.NewsReaderService.serviceState.collect { serviceState ->
                _uiState.value = _uiState.value.copy(
                    isSummarizing = serviceState.isReading,
                    readingNewsIndex = serviceState.currentIndex,
                    currentSummary = serviceState.currentSummary ?: _uiState.value.currentSummary
                )
                // Also update selected index for visual consistency
                if (serviceState.currentIndex >= 0) {
                    _selectedNewsIndex.value = serviceState.currentIndex
                }
            }
        }
    }

    private fun initializeAndFetchNews() {
        viewModelScope.launch {
            // Seed default feeds if empty
            repository.seedDefaultFeedsIfEmpty()
            // Fetch news
            refreshNews()
        }
    }

    private var refreshJob: kotlinx.coroutines.Job? = null
    
    // Track indices of news items read in current session (for Next/Previous buttons)
    private val readIndicesInSession = mutableSetOf<Int>()

    /**
     * Refresh news - fetch random 5 items from enabled feeds.
     * @param force If true, bypass cache and fetch from network immediately.
     */
    fun refreshNews(force: Boolean = false) {
        // Cancel previous refresh job to prevent race condition on double-click
        refreshJob?.cancel()
        
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                // If force is true, use refreshAndGetRandomNews to bypass cache check
                val news = if (force) {
                    repository.refreshAndGetRandomNews(5)
                } else {
                    repository.fetchRandomNews(5)
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    newsItems = news,
                    error = if (news.isEmpty()) "Không tìm thấy tin tức. Vui lòng thêm nguồn RSS." else null
                )
                
                if (force) {
                    // Reset selection on force refresh
                    _selectedNewsIndex.value = -1
                    readIndicesInSession.clear()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Lỗi khi tải tin tức: ${e.message}"
                )
            }
        }
    }

    /**
     * Select a news item to summarize and read aloud.
     */
    fun selectNews(index: Int) {
        val news = _uiState.value.newsItems.getOrNull(index) ?: return
        _selectedNewsIndex.value = index
        
        // FIX: Stop TTS immediately for instant feedback
        // User knows their command was received right away
        ttsManager.stop()
        
        // Mark this news as read so it won't appear again today
        viewModelScope.launch {
            repository.markNewsAsRead(news.id)
        }
        
        summarizeAndSpeak(news)
    }

    // Debounce for Next/Previous buttons to prevent spam
    private var lastSelectTime = 0L

    /**
     * Select a random news item from the remaining items (excluding currently selected AND already read in session).
     * Used for Next/Previous media buttons on car steering wheel.
     * When all items are read, resets and starts fresh.
     * Includes debounce to prevent rapid successive calls.
     */
    fun selectRandomOtherNews() {
        // FIX: Debounce - ignore if pressed within 500ms
        val now = System.currentTimeMillis()
        if (now - lastSelectTime < DEBOUNCE_MS) {
            Log.d(TAG, "selectRandomOtherNews: Debounced (too fast)")
            return
        }
        lastSelectTime = now

        val newsItems = _uiState.value.newsItems
        if (newsItems.isEmpty()) return

        val currentIndex = _selectedNewsIndex.value
        
        // Exclude both current item and items already read in this session
        val availableIndices = newsItems.indices.filter { 
            it != currentIndex && it !in readIndicesInSession 
        }
        
        if (availableIndices.isEmpty()) {
            // All items read - reset and pick from all except current
            readIndicesInSession.clear()
            val freshIndices = newsItems.indices.filter { it != currentIndex }
            if (freshIndices.isEmpty()) return
            
            val randomIndex = freshIndices.random()
            readIndicesInSession.add(randomIndex)
            selectNews(randomIndex)
        } else {
            val randomIndex = availableIndices.random()
            readIndicesInSession.add(randomIndex)
            selectNews(randomIndex)
        }
    }

    private var summarizationJob: kotlinx.coroutines.Job? = null

    private fun summarizeAndSpeak(news: NewsItem) {
        // Cancel previous job if exists
        summarizationJob?.cancel()
        
        summarizationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSummarizing = true,
                currentSummary = null,
                error = null
            )

            // Step 1: Speak Title Immediately
            // We use a separate launch to allow summarization to start in parallel
            // but we want to await the speech to finish before potentially playing summary
            val titleSpeechJob = launch {
                ttsManager.speakAndWait(news.title)
            }

            try {
                // Step 2: Start Summarization in Background (Async)
                val summaryDeferred = async(Dispatchers.IO) {
                    var content = news.getContentForSummary()
                    
                    // If RSS content is short, fetch full article from URL
                    if (content.length < 500 && news.link.isNotBlank()) {
                        val fullContent = articleFetcher.fetchArticleContent(news.link)
                        if (fullContent != null && fullContent.length > content.length) {
                            content = fullContent
                        }
                    }
                    
                    // Refresh keys and summarize
                    geminiClient.refreshApiKeys()
                    geminiClient.summarizeForTts(content)
                }

                // Step 3: Wait for Title Speech to Finish
                titleSpeechJob.join()

                // Step 4: Pause for 1 second
                delay(1000)

                // Step 5: Get Summary Result (Wait if not ready yet)
                val result = summaryDeferred.await()

                when (result) {
                    is GeminiApiClient.SummarizeResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isSummarizing = false,
                            currentSummary = result.text,
                            currentModel = result.model
                        )
                        // Speak Summary and Wait
                        ttsManager.speakAndWait(result.text)
                        
                        // Reset selection state after reading finishes
                        _selectedNewsIndex.value = -1
                        _uiState.value = _uiState.value.copy(
                             readingNewsIndex = -1,
                             isSummarizing = false
                        )
                    }
                    is GeminiApiClient.SummarizeResult.AllQuotaExhausted -> {
                        _uiState.value = _uiState.value.copy(
                            isSummarizing = false,
                            error = "Đã hết toàn bộ quota API. Vui lòng thử lại sau."
                        )
                    }
                    is GeminiApiClient.SummarizeResult.NoApiKeys -> {
                        // Fallback: read short description if summarization failed
                        val fallbackText = news.description.take(200)
                        _uiState.value = _uiState.value.copy(
                            isSummarizing = false,
                            currentSummary = fallbackText,
                            error = "Vui lòng thêm API key Gemini trong Cài đặt."
                        )
                        ttsManager.speakSafely(fallbackText)
                    }
                    is GeminiApiClient.SummarizeResult.Error -> {
                        // Fallback: read short description
                        val fallbackText = news.description.take(200)
                        _uiState.value = _uiState.value.copy(
                            isSummarizing = false,
                            currentSummary = fallbackText,
                            error = null
                        )
                        ttsManager.speakSafely(fallbackText)
                    }
                }
            } catch (e: CancellationException) {
                // Job cancelled, do nothing
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSummarizing = false,
                    error = "Lỗi khi tóm tắt: ${e.message}"
                )
            }
        }
    }

    /**
     * Stop summarization (if running) and read the full article content immediately.
     */
    fun readFullArticle() {
        // PRIORITIZE: If "Read All" is active, use that index. Otherwise use selected index.
        var targetIndex = _selectedNewsIndex.value
        if (_uiState.value.readingNewsIndex != -1) {
            targetIndex = _uiState.value.readingNewsIndex
        }

        val news = _uiState.value.newsItems.getOrNull(targetIndex) ?: return

        // 1. Stop current TTS and any Read All jobs (Important!)
        stopSpeaking()

        // 2. Start new job to fetch and read full content
        summarizationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingFullContent = true,
                error = null
            )

            try {
                // Fetch content (logic similar to summarizeAndSpeak)
                var content = news.getContentForSummary()
                
                if (content.length < 500 && news.link.isNotBlank()) {
                    val fullContent = withContext(Dispatchers.IO) {
                        articleFetcher.fetchArticleContent(news.link)
                    }
                    if (fullContent != null && fullContent.length > content.length) {
                        content = fullContent
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoadingFullContent = false,
                    currentSummary = content,
                    isSummarizing = false
                )
                
                // Speak full content (safely waits for init)
                ttsManager.speakSafely(content)
                
            } catch (e: Exception) {
                 _uiState.value = _uiState.value.copy(
                    isLoadingFullContent = false,
                    error = "Lỗi khi tải nội dung: ${e.message}"
                )
            }
        }
    }

    /**
     * Stop TTS playback.\
     */
    /**
     * Stop TTS playback and any active summarization/reading jobs.
     * Also stops the NewsReaderService if running.
     */
    fun stopSpeaking() {
        readAllJob?.cancel()
        summarizationJob?.cancel() // FIX: Cancel single-item summarization job too
        _selectedNewsIndex.value = -1 // Reset selection on stop
        _uiState.value = _uiState.value.copy(
            readingNewsIndex = -1,
            isSummarizing = false
        )
        ttsManager.stop()
        
        // Stop the NewsReaderService if running
        val context = getApplication<Application>()
        val stopIntent = android.content.Intent(context, com.skul9x.rssreader.service.NewsReaderService::class.java).apply {
            action = com.skul9x.rssreader.service.NewsReaderService.ACTION_STOP
        }
        context.startService(stopIntent)
    }

    private var readAllJob: kotlinx.coroutines.Job? = null

    /**
     * Read summaries of all 5 news items sequentially with visual feedback.
     * Now uses NewsReaderService (Foreground Service) to keep running when screen is off.
     */
    fun readAllNewsSummaries() {
        val newsItems = _uiState.value.newsItems
        if (newsItems.isEmpty()) return

        // Cancel any running jobs
        summarizationJob?.cancel()
        readAllJob?.cancel()
        ttsManager.stop()

        // Start Foreground Service
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, com.skul9x.rssreader.service.NewsReaderService::class.java).apply {
            action = com.skul9x.rssreader.service.NewsReaderService.ACTION_START_READ_ALL
            putParcelableArrayListExtra(
                com.skul9x.rssreader.service.NewsReaderService.EXTRA_NEWS_ITEMS,
                ArrayList(newsItems.take(5))
            )
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        // Update UI state to show reading is in progress
        _uiState.value = _uiState.value.copy(
            isSummarizing = true,
            error = null
        )

        // Observe service state in a coroutine
        readAllJob = viewModelScope.launch {
            // Give service time to start
            delay(500)
            
            // For now, just track that we started the service
            // Future: bind to service and observe its state
            Log.d(TAG, "NewsReaderService started for Read All")
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
        ttsManager.clearError()
    }

    /**
     * Get API status for debugging.
     */
    fun getApiStatus(): String = geminiClient.getCurrentStatus()

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}

/**
 * UI State for the main screen.
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val isSummarizing: Boolean = false,
    val isLoadingFullContent: Boolean = false,
    val newsItems: List<NewsItem> = emptyList(),
    val currentSummary: String? = null,
    val currentModel: String? = null,
    val error: String? = null,
    val readingNewsIndex: Int = -1 // -1 means inactive. 0..4 means reading that specific item.
)
