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
import com.skul9x.rssreader.utils.VietnameseDetector
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
    private val localSyncRepo = com.skul9x.rssreader.RssApplication.getLocalSyncRepository()
    private val repository = RssRepository(
        database.rssFeedDao(), 
        database.cachedNewsDao(), 
        database.readNewsDao(),
        localSyncRepository = localSyncRepo
    )
    private val geminiClient = GeminiApiClient(application)
    private val articleFetcher = ArticleContentFetcher().apply {
        setCustomSelectorManager(com.skul9x.rssreader.data.local.CustomSelectorManager.getInstance(application))
    }
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
        observeResumeState()
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
                    isReadAllMode = serviceState.isReadAllMode,
                    currentSummary = serviceState.currentSummary ?: _uiState.value.currentSummary,
                    // FIX: Ensure history is marked even if reading finishes naturally (not just on Stop)
                    hasReadHistory = (serviceState.currentSummary != null) || _uiState.value.hasReadHistory,
                    readingProgress = serviceState.readingProgress // NEW
                )
                // Also update selected index for visual consistency
                if (serviceState.currentIndex >= 0) {
                    _selectedNewsIndex.value = serviceState.currentIndex
                }
            }
        }
    }
    
    /**
     * Observe the NewsReaderService resume state.
     */
    private fun observeResumeState() {
        viewModelScope.launch {
            com.skul9x.rssreader.service.NewsReaderService.resumeState.collect { resumeState ->
                _uiState.value = _uiState.value.copy(
                    hasResumableContent = resumeState.isResumable,
                    resumableNewsTitle = resumeState.newsTitle,
                    resumableNewsIndex = resumeState.newsIndex  // FIX: Use index for reliable matching
                )
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
        
        // FIX #5: DO NOT clear resume state immediately
        // Only clear AFTER new news loads successfully
        // This way, if load fails, user can still resume old content
        
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                // If force is true, use refreshAndGetRandomNews to bypass cache check
                val news = if (force) {
                    // Flash Refresh: Only fetch Voz, mix with existing cache
                    repository.refreshVozAndGetRandomNews(5)
                } else {
                    repository.fetchRandomNews(5)
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    newsItems = news,
                    error = if (news.isEmpty()) "Không tìm thấy tin tức. Vui lòng thêm nguồn RSS." else null
                )
                
                // FIX #5: Clear resume state ONLY after successful load with new items
                // Old resume state is now invalid (different news list)
                if (news.isNotEmpty()) {
                    com.skul9x.rssreader.service.NewsReaderService.clearResumeState()
                }
                
                if (force) {
                    // Reset selection on force refresh
                    _selectedNewsIndex.value = -1
                    readIndicesInSession.clear()
                    // Reset read history when force refreshing
                    _uiState.value = _uiState.value.copy(hasReadHistory = false)
                }

                // Check for items needing translation
                val itemsToTranslate = news.filter {
                    it.translatedTitle == null && !VietnameseDetector.isVietnamese(it.title)
                }

                if (itemsToTranslate.isNotEmpty()) {
                    // 1. Set isTranslating = true for these items
                    val translatingIds = itemsToTranslate.map { it.id }.toSet()
                    _uiState.value = _uiState.value.copy(
                        newsItems = _uiState.value.newsItems.map {
                            if (it.id in translatingIds) it.copy(isTranslating = true) else it
                        }
                    )

                    // 2. Call batch translation (async, allowing UI to update first)
                    viewModelScope.launch {
                        try {
                            val titlesMap = itemsToTranslate.associate { it.id to it.title }
                            val translatedMap = geminiClient.translateTitleBatch(titlesMap)

                            // 3. Update UI with results
                            if (translatedMap.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(
                                    newsItems = _uiState.value.newsItems.map { item ->
                                        val newTitle = translatedMap[item.id]
                                        if (newTitle != null) {
                                            item.copy(translatedTitle = newTitle, isTranslating = false)
                                        } else if (item.id in translatingIds) {
                                            item.copy(isTranslating = false)
                                        } else {
                                            item
                                        }
                                    }
                                )
                                // 4. Save to DB
                                repository.updateTranslatedTitles(translatedMap)
                            } else {
                                // All failed, clear isTranslating
                                _uiState.value = _uiState.value.copy(
                                    newsItems = _uiState.value.newsItems.map {
                                        if (it.id in translatingIds) it.copy(isTranslating = false) else it
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Translation error", e)
                            // Clear isTranslating on error
                             _uiState.value = _uiState.value.copy(
                                newsItems = _uiState.value.newsItems.map {
                                    if (it.id in translatingIds) it.copy(isTranslating = false) else it
                                }
                            )
                        }
                    }
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
        // Cancel local jobs, but DON'T send ACTION_STOP to service
        // The new service start will override the previous one
        readAllJob?.cancel()
        summarizationJob?.cancel()
        ttsManager.stop()
        
        val currentIndex = _selectedNewsIndex.value
        val allNewsItems = _uiState.value.newsItems
        
        // Start Foreground Service for Single Item with full list for Next/Prev navigation
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, com.skul9x.rssreader.service.NewsReaderService::class.java).apply {
            action = com.skul9x.rssreader.service.NewsReaderService.ACTION_READ_SINGLE
            putParcelableArrayListExtra(
                com.skul9x.rssreader.service.NewsReaderService.EXTRA_NEWS_ITEMS,
                ArrayList(allNewsItems)
            )
            putExtra(com.skul9x.rssreader.service.NewsReaderService.EXTRA_NEWS_INDEX, currentIndex)
            putExtra(com.skul9x.rssreader.service.NewsReaderService.EXTRA_READ_FULL, false)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        // Update UI state
        _uiState.value = _uiState.value.copy(
            isSummarizing = true,
            error = null
        )
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

        val allNewsItems = _uiState.value.newsItems
        if (targetIndex !in allNewsItems.indices) return

        // Cancel local jobs, but DON'T send ACTION_STOP to service
        readAllJob?.cancel()
        summarizationJob?.cancel()
        ttsManager.stop()

        // Start Foreground Service for Full Content with full list for Next/Prev navigation
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, com.skul9x.rssreader.service.NewsReaderService::class.java).apply {
            action = com.skul9x.rssreader.service.NewsReaderService.ACTION_READ_SINGLE
            putParcelableArrayListExtra(
                com.skul9x.rssreader.service.NewsReaderService.EXTRA_NEWS_ITEMS,
                ArrayList(allNewsItems)
            )
            putExtra(com.skul9x.rssreader.service.NewsReaderService.EXTRA_NEWS_INDEX, targetIndex)
            putExtra(com.skul9x.rssreader.service.NewsReaderService.EXTRA_READ_FULL, true)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        _uiState.value = _uiState.value.copy(
            isLoadingFullContent = true,
            error = null
        )
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
            isSummarizing = false,
            hasReadHistory = true  // FIX: Mark that user has read something (for Replay button)
        )
        ttsManager.stop()
        
        // Stop the NewsReaderService if running
        val context = getApplication<Application>()
        val stopIntent = android.content.Intent(context, com.skul9x.rssreader.service.NewsReaderService::class.java).apply {
            action = com.skul9x.rssreader.service.NewsReaderService.ACTION_STOP
        }
        context.startService(stopIntent)
    }
    
    /**
     * Resume interrupted playback from saved state.
     */
    fun resumeReading() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, com.skul9x.rssreader.service.NewsReaderService::class.java).apply {
            action = com.skul9x.rssreader.service.NewsReaderService.ACTION_RESUME
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
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
    val readingNewsIndex: Int = -1, // -1 means inactive. 0..4 means reading that specific item.
    val isReadAllMode: Boolean = false, // True when using "Read All 5" mode
    val hasReadHistory: Boolean = false,  // True after any reading completes/stops (for Replay button)
    val readingProgress: Float = 0f,  // NEW
    val hasResumableContent: Boolean = false,  // NEW: True when there's interrupted content to resume
    val resumableNewsTitle: String? = null,    // NEW: Title of resumable news for button display
    val resumableNewsIndex: Int = -1           // FIX: Index of resumable news for reliable matching
)
