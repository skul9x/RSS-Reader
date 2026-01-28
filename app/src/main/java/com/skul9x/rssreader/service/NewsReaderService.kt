package com.skul9x.rssreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.skul9x.rssreader.MainActivity
import com.skul9x.rssreader.R
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.model.NewsItem
import com.skul9x.rssreader.data.network.ArticleContentFetcher
import com.skul9x.rssreader.data.network.GeminiApiClient
import com.skul9x.rssreader.tts.TtsManager
import com.skul9x.rssreader.utils.DebugLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.skul9x.rssreader.data.model.ReadNewsItem

/**
 * Foreground Service for reading news aloud.
 * Keeps running even when screen is off.
 */
class NewsReaderService : Service() {

    companion object {
        private const val TAG = "NewsReaderService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "news_reader_channel"
        private const val SESSION_TAG = "NewsReaderSession"
        private const val SKIP_DEBOUNCE_MS = 500L  // Debounce for Next/Previous to prevent race condition

        // Intent actions
        const val ACTION_START_READ_ALL = "com.skul9x.rssreader.START_READ_ALL"
        const val ACTION_READ_SINGLE = "com.skul9x.rssreader.READ_SINGLE"
        const val ACTION_STOP = "com.skul9x.rssreader.STOP"
        const val ACTION_NEXT = "com.skul9x.rssreader.NEXT"
        const val ACTION_PREVIOUS = "com.skul9x.rssreader.PREVIOUS"
        const val ACTION_PLAY = "com.skul9x.rssreader.PLAY"
        const val ACTION_RESUME = "com.skul9x.rssreader.RESUME"
        
        const val EXTRA_NEWS_ITEMS = "news_items"
        const val EXTRA_NEWS_ITEM = "news_item"
        const val EXTRA_NEWS_INDEX = "news_index"
        const val EXTRA_READ_FULL = "read_full"
        
        // STATIC StateFlow for ViewModel to observe (global access)
        private val _serviceState = MutableStateFlow(NewsReaderState())
        val serviceState: StateFlow<NewsReaderState> = _serviceState.asStateFlow()
        
        // Reset state when Service is not running
        fun resetState() {
            _serviceState.value = NewsReaderState()
        }
        
        // Resume state for interrupted playback (session-scoped)
        private val _resumeState = MutableStateFlow(ResumeState())
        val resumeState: StateFlow<ResumeState> = _resumeState.asStateFlow()
        
        /**
         * Clear resume state. Call when:
         * - User selects new news
         * - User refreshes news list
         * - Playback completes normally
         */
        fun clearResumeState() {
            _resumeState.value = ResumeState()
        }
    }

    // Service scope - survives configuration changes
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var readAllJob: Job? = null

    // Components
    private lateinit var ttsManager: TtsManager
    private lateinit var geminiClient: GeminiApiClient
    private lateinit var articleFetcher: ArticleContentFetcher
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockJob: Job? = null // Job to monitor and extend WakeLock

    // FIX: @Volatile for thread-safe visibility across coroutines
    @Volatile private var newsItems: List<NewsItem> = emptyList()
    @Volatile private var currentNewsIndex: Int = 0
    @Volatile private var isReadAllMode: Boolean = false
    @Volatile private var readFullContent: Boolean = false
    
    // FIX: Thread-safety for skip operations using Mutex (non-blocking)
    private val skipMutex = Mutex()  // Non-blocking mutex for coroutine-safe access
    private var lastSkipTime: Long = 0L  // Debounce tracking
    // NOTE: Removed isSkipping flag - it was getting stuck at true and blocking all skips.
    // Now using skipMutex.tryLock() which is more reliable.
    
    // FIX: DAO for marking news as read when navigating
    private val readNewsDao by lazy { AppDatabase.getDatabase(this).readNewsDao() }
    
    // FIX: Use LocalSyncRepository for proper sync queue integration
    private val localSyncRepo by lazy { com.skul9x.rssreader.RssApplication.getLocalSyncRepository() }
    
    // FIX: Track if user is navigating in single mode (Next/Prev pressed)
    // When true, service should NOT auto-stop after reading one item
    @Volatile private var isNavigatingInSingleMode: Boolean = false
    
    // Track current content being read
    @Volatile private var currentFullText: String = ""
    @Volatile private var currentTitle: String = ""
    
    // Resume support: Track if current item finished reading
    @Volatile private var isItemCompleted: Boolean = true

    // Binder for Activity connection
    inner class LocalBinder : Binder() {
        fun getService(): NewsReaderService = this@NewsReaderService
    }
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        createNotificationChannel()
        initializeComponents()
        initializeMediaSession()
    }
    
    // ... (skipping initializeComponents, initializeMediaSession, onStartCommand, startForegroundSafely) ...
    
    private fun initializeComponents() {
        ttsManager = TtsManager(this)
        ttsManager.initialize()
        
        geminiClient = GeminiApiClient(this)
        articleFetcher = ArticleContentFetcher()
        articleFetcher.setCustomSelectorManager(com.skul9x.rssreader.data.local.CustomSelectorManager.getInstance(this))
        
        // Register preference listener
        AppPreferences.getInstance(this).registerOnSharedPreferenceChangeListener(preferenceListener)
        
        // Log initial settings
        val prefs = AppPreferences.getInstance(this)
        DebugLogger.log(TAG, ">>> Service Created. " + prefs.getAllSettingsInfo())
        
        // Cleanup cache older than 1 day (24 hours)
        serviceScope.launch(Dispatchers.IO) {
            try {
                geminiClient.cleanupCache(24 * 60 * 60 * 1000L)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cleanup cache: ${e.message}")
            }
        }
        
        // Initialize WakeLock to keep CPU active while reading
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RSSReader::NewsReading"
        )
        
        // Observe TTS progress
        serviceScope.launch {
            ttsManager.readingProgress.collect { progress ->
                // Update state with progress
                _serviceState.update { it.copy(readingProgress = progress) }
            }
        }
        
        // Setup pause callback for resume support
        ttsManager.onPauseRequested = {
            saveResumeState()
        }
    }

    // Listener for settings changes (Real-time update)
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // Log ANY preference change
        val appPreferences = AppPreferences.getInstance(this)
        DebugLogger.log(TAG, ">>> Preference changed: $key. " + appPreferences.getAllSettingsInfo())

        if (key == "audio_stream_mode") { // Hardcoded key check to match AppPreferences constant
            val newMode = appPreferences.getAudioStreamMode()
            Log.d(TAG, "Preference changed: $key -> $newMode")
            ttsManager.updateAudioStream(newMode)
        }
    }

    private fun initializeMediaSession() {
        // FIX: Explicitly create a PendingIntent for the MediaButtonReceiver
        // This is crucial for some Android versions/devices to route events correctly
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this, com.skul9x.rssreader.media.MediaButtonReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 
            0, 
            mediaButtonIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSessionCompat(this, SESSION_TAG).apply {
            // FIX: Explicitly set the media button receiver
            setMediaButtonReceiver(pendingIntent)
            
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    // FIX: Log ALL media button events received by MediaSession
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    
                    if (keyEvent != null) {
                        DebugLogger.log(TAG, ">>> MediaSession.onMediaButtonEvent: keyCode=${keyEvent.keyCode}, action=${keyEvent.action}")
                        Log.d(TAG, "MediaSession event: ${android.view.KeyEvent.keyCodeToString(keyEvent.keyCode)}")
                    } else {
                        DebugLogger.log(TAG, ">>> MediaSession.onMediaButtonEvent: NULL key event")
                    }
                    
                    // Allow super to handle default behaviors or custom logic
                    // If we return true, we consume it. If false, system might handle it.
                    // But for MediaSessionCompat, super.onMediaButtonEvent calls onPlay/onPause/etc based on checking the intent.
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
                
                override fun onPlay() {
                    DebugLogger.log(TAG, ">>> MediaSession.onPlay() called")
                    Log.d(TAG, "MediaSession: Play")
                    // Resume if paused (future enhancement)
                }
                
                override fun onPause() {
                    DebugLogger.log(TAG, ">>> MediaSession.onPause() called")
                    Log.d(TAG, "MediaSession: Pause")
                    stopReading()
                }
                
                override fun onStop() {
                    DebugLogger.log(TAG, ">>> MediaSession.onStop() called")
                    Log.d(TAG, "MediaSession: Stop")
                    stopReading()
                }
                
                override fun onSkipToNext() {
                    DebugLogger.log(TAG, ">>> MediaSession.onSkipToNext() called - newsItems.size=${newsItems.size}, currentIndex=$currentNewsIndex")
                    Log.d(TAG, "MediaSession: Next")
                    skipToNext()
                }
                
                override fun onSkipToPrevious() {
                    DebugLogger.log(TAG, ">>> MediaSession.onSkipToPrevious() called - newsItems.size=${newsItems.size}, currentIndex=$currentNewsIndex")
                    Log.d(TAG, "MediaSession: Previous")
                    skipToPrevious()
                }
            })
            
            isActive = true
        }
        
        DebugLogger.log(TAG, "MediaSession initialized and active")
        updatePlaybackState(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            DebugLogger.log(TAG, ">>> onStartCommand: action=${intent?.action}, newsItems.size=${newsItems.size}, currentIndex=$currentNewsIndex")
            Log.d(TAG, "onStartCommand: ${intent?.action}")
    
            // CRITICAL: Must call startForeground immediately to avoid ANR/Crash on Android 8+
            if (intent?.action == ACTION_START_READ_ALL || intent?.action == ACTION_READ_SINGLE || intent?.action == ACTION_RESUME) {
                startForegroundSafely("Đang chuẩn bị...")
            }
            
            when (intent?.action) {
                ACTION_START_READ_ALL -> {
                    DebugLogger.log(TAG, "Command: Start Read All")
                    @Suppress("DEPRECATION")
                    val items = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(EXTRA_NEWS_ITEMS, NewsItem::class.java)
                    } else {
                        intent.getParcelableArrayListExtra(EXTRA_NEWS_ITEMS)
                    }
                    
                    if (items != null && items.isNotEmpty()) {
                        newsItems = items
                        currentNewsIndex = 0
                        isReadAllMode = true
                        readFullContent = false
                        startReadingAll()
                    } else {
                        Log.e(TAG, "No items to read")
                        DebugLogger.log(TAG, "Error: No items to read for Read All")
                        stopSelf()
                    }
                }
                ACTION_READ_SINGLE -> {
                    DebugLogger.log(TAG, "Command: Read Single")
                    @Suppress("DEPRECATION")
                    val items = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(EXTRA_NEWS_ITEMS, NewsItem::class.java)
                    } else {
                        intent.getParcelableArrayListExtra(EXTRA_NEWS_ITEMS)
                    }
                    val index = intent.getIntExtra(EXTRA_NEWS_INDEX, 0)
                    val readFull = intent.getBooleanExtra(EXTRA_READ_FULL, false)
                    
                    // FIX: Ignore duplicate tap - if already reading this exact item, do nothing
                    if (_serviceState.value.isReading && currentNewsIndex == index && !isReadAllMode) {
                        DebugLogger.log(TAG, "Already reading item $index, ignoring duplicate tap")
                        return START_NOT_STICKY
                    }
                    
                    if (items != null && items.isNotEmpty() && index in items.indices) {
                        newsItems = items
                        currentNewsIndex = index
                        isReadAllMode = false
                        readFullContent = readFull
                        // FIX: Reset navigation flag when user taps UI directly
                        isNavigatingInSingleMode = false
                        startReadingSingle(items[index], readFull)
                    } else {
                        // Fallback: single item passed directly
                        @Suppress("DEPRECATION")
                        val item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(EXTRA_NEWS_ITEM, NewsItem::class.java)
                        } else {
                            intent.getParcelableExtra(EXTRA_NEWS_ITEM)
                        }
                        if (item != null) {
                            newsItems = listOf(item)
                            currentNewsIndex = 0
                            isReadAllMode = false
                            readFullContent = readFull
                            startReadingSingle(item, readFull)
                        } else {
                            Log.e(TAG, "No item to read")
                            DebugLogger.log(TAG, "Error: No item to read for Read Single")
                            stopSelf()
                        }
                    }
                }
                ACTION_STOP -> {
                    DebugLogger.log(TAG, "Command: Stop")
                    stopReading()
                    stopSelf()
                }
                ACTION_NEXT -> {
                    DebugLogger.log(TAG, "Command: Next (newsItems.size=${newsItems.size}, currentIndex=$currentNewsIndex)")
                    if (newsItems.isEmpty()) {
                        DebugLogger.log(TAG, "Command: Next IGNORED - No news items loaded!")
                        stopSelf()  // FIX: Don't leave service running when nothing to do
                    } else {
                        skipToNext()
                    }
                }
                ACTION_PREVIOUS -> {
                    DebugLogger.log(TAG, "Command: Previous (newsItems.size=${newsItems.size}, currentIndex=$currentNewsIndex)")
                    if (newsItems.isEmpty()) {
                        DebugLogger.log(TAG, "Command: Previous IGNORED - No news items loaded!")
                        stopSelf()  // FIX: Don't leave service running when nothing to do
                    } else {
                        skipToPrevious()
                    }
                }
                ACTION_PLAY -> {
                    DebugLogger.log(TAG, "Command: Play/Resume (Not fully implemented)")
                    // Future: Implement resume logic here
                    // mediaSession?.controller?.transportControls?.play()
                }
                ACTION_RESUME -> {
                    DebugLogger.log(TAG, "Command: Resume")
                    val state = _resumeState.value
                    if (state.isResumable) {
                        // startForegroundSafely called above for this action too
                        resumeFromState(state)
                    } else {
                        DebugLogger.log(TAG, "No resumable content available")
                        stopSelf()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in onStartCommand", e)
            DebugLogger.log(TAG, ">>> CRITICAL EXCEPTION in onStartCommand: ${e.message}")
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(contentText: String) {
        try {
            val notification = createNotification(contentText)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            DebugLogger.log(TAG, ">>> startForegroundSafely called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            DebugLogger.log(TAG, "Error starting foreground service: ${e.message}")
            stopSelf()
        }
    }

    private fun startReadingSingle(news: NewsItem, readFull: Boolean) {
        try {
            readAllJob?.cancel()
            
            // FIX #6: DO NOT clear resume state immediately
            // If this read fails before content loads, user can still resume old content
            // Resume state will be cleared when speakSentencesAndWait completes successfully
            // clearResumeState() <-- REMOVED
            ttsManager.resetSentenceTracking()
            ttsManager.clearSentenceSnapshot()  // Clear old snapshot from TtsManager
            isItemCompleted = false // Reset completion flag
            
            // FIX: Release WakeLock before re-acquiring to prevent memory leak
            releaseWakeLockSafely()
            
            // Use smart monitor instead of hard lock
            startWakeLockMonitor()
            
            // Capture navigation state at start (before coroutine)
            val wasNavigating = isNavigatingInSingleMode
            DebugLogger.log(TAG, ">>> startReadingSingle: news=${news.title}, wasNavigating=$wasNavigating")
            
            // Store current title for resume
            currentTitle = news.translatedTitle ?: news.title
            
            readAllJob = serviceScope.launch {
                DebugLogger.log(TAG, ">>> [Coroutine] startReadingSingle coroutine STARTED for: ${news.title}")
                _serviceState.update { state ->
                    state.copy(
                        isReading = true,
                        currentIndex = currentNewsIndex,
                        isReadAllMode = false,
                        currentTitle = news.title,
                        error = null
                    )
                }
                updatePlaybackState(true)
                
                try {
                    DebugLogger.log(TAG, ">>> [Coroutine] About to call ensureSilentAudioRunning")
                    // === ENSURE SILENT AUDIO FROM START ===
                    try {
                        ttsManager.ensureSilentAudioRunning()
                        DebugLogger.log(TAG, ">>> [Coroutine] ensureSilentAudioRunning completed")
                    } catch (e: Exception) {
                        DebugLogger.log(TAG, ">>> [Coroutine] ensureSilentAudioRunning FAILED: ${e.message}")
                    }
                    
                    geminiClient.refreshApiKeys()
                    
                    val titleToSpeak = news.translatedTitle ?: news.title
                    
                    updateNotification("Đang đọc: $titleToSpeak")
                    updateMetadata(titleToSpeak, news.sourceName)
                    
                    // Speak title (not tracked for resume - title is short)
                    val introJob = launch {
                        ttsManager.speakAndWait(titleToSpeak)
                    }
                    
                    // Prepare content
                    val contentDeferred = async(Dispatchers.IO) {
                        try {
                            var content = news.getContentForSummary()
                            
                            // Fetch full content if needed (short RSS or explicit read full)
                            if (readFull || (content.length < 500 && news.link.isNotBlank())) {
                                val fullContent = articleFetcher.fetchArticleContent(news.link)
                                if (fullContent != null && fullContent.length > content.length) {
                                    content = fullContent
                                }
                            }
                            
                            if (readFull) {
                                content
                            } else {
                                when (val result = geminiClient.summarizeForTts(content, news.link)) {
                                    is GeminiApiClient.SummarizeResult.Success -> result.text
                                    else -> news.description.take(200)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Error preparing content: ${e.message}")
                            news.description.take(200)
                        }
                    }
                    
                    introJob.join()
                    val textToRead = contentDeferred.await()
                    
                    if (!isActive) return@launch
                    
                    // Store full text for resume and split into sentences
                    currentFullText = textToRead
                    val sentences = ttsManager.splitTextIntoSentences(textToRead)
                    
                    _serviceState.update { it.copy(currentSummary = textToRead) }
                    
                    // Use sentence-based TTS for resume support
                    // FIX: Check if reading completed or was interrupted
                    val completedNormally = ttsManager.speakSentencesAndWait(sentences)
                    
                    // Only mark as completed and clear state if finished normally
                    if (completedNormally) {
                        isItemCompleted = true
                        
                        // Clear resume state on successful completion
                        clearResumeState()
                        ttsManager.resetSentenceTracking()
                    } else {
                        // Reading was interrupted (phone call, other audio app, etc.)
                        // Resume state was already saved by onPauseRequested callback
                        DebugLogger.log(TAG, ">>> Reading interrupted, keeping resume state")
                        return@launch  // Exit early, don't continue to the finish logic
                    }
                    
                    // FIX: Only auto-stop if this was NOT a navigation (initial read)
                    if (wasNavigating) {
                        DebugLogger.log(TAG, ">>> Single read finished (was navigating) - keeping service alive for more navigation")
                        updateNotification("Đã đọc xong: ${news.translatedTitle ?: news.title}")
                        _serviceState.update { state ->
                            state.copy(
                                isReading = false,
                                currentIndex = currentNewsIndex
                            )
                        }
                        updatePlaybackState(false)
                    } else {
                        DebugLogger.log(TAG, ">>> Single read finished (initial read) - stopping service")
                        stopReading()
                        stopSelf()
                    }
                    
                } catch (e: CancellationException) {
                    Log.d(TAG, "Reading cancelled")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading single news", e)
                    DebugLogger.log(TAG, ">>> Exception in reading loop: ${e.message}")
                    _serviceState.update { state ->
                        state.copy(
                            isReading = false,
                            error = "Lỗi: ${e.message}"
                        )
                    }
                    stopReading()
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, ">>> CRITICAL ERROR in startReadingSingle: ${e.message}")
            Log.e(TAG, "CRITICAL ERROR in startReadingSingle", e)
            stopSelf()
        }
    }

    private fun startReadingAll() {
        startReadingAllFromIndex(0)
    }

    private fun startReadingAllFromIndex(fromIndex: Int) {
        readAllJob?.cancel()
        
        // FIX: Release WakeLock before re-acquiring to prevent memory leak
        releaseWakeLockSafely()
        
        // Acquire WakeLock smartly to prevent CPU sleep
        startWakeLockMonitor()
        Log.d(TAG, "WakeLock monitoring started, starting from index $fromIndex")
        
        readAllJob = serviceScope.launch {
            _serviceState.update { state ->
                state.copy(
                    isReading = true,
                    currentIndex = fromIndex,
                    isReadAllMode = true,
                    error = null
                )
            }
            updatePlaybackState(true)

            val ordinalWords = listOf("nhất", "hai", "ba", "bốn", "năm")
            val itemsToRead = newsItems.take(5)
            
            try {
                ttsManager.ensureSilentAudioRunning()
                geminiClient.refreshApiKeys()
                
                for (index in fromIndex until itemsToRead.size) {
                    if (!isActive) break
                    
                    val news = itemsToRead[index]
                    currentNewsIndex = index // Update tracking variable
                    isItemCompleted = false // Reset completion flag
                    
                    // Update state
                    val titleToSpeak = news.translatedTitle ?: news.title
                    _serviceState.update { state ->
                        state.copy(
                            currentIndex = index,
                            currentTitle = titleToSpeak
                        )
                    }
                    updateNotification("Đang đọc: $titleToSpeak")
                    updateMetadata(titleToSpeak, news.sourceName)
                    
                    val ordinal = ordinalWords.getOrElse(index) { "${index + 1}" }
                    
                    // Speak title
                    val introJob = launch {
                        ttsManager.speakAndWait("Tin thứ $ordinal, $titleToSpeak.")
                    }
                    
                    // Summarize in background
                    val summaryDeferred = async(Dispatchers.IO) {
                        try {
                            var content = news.getContentForSummary()
                            if (content.length < 500 && news.link.isNotBlank()) {
                                val fullContent = articleFetcher.fetchArticleContent(news.link)
                                if (fullContent != null && fullContent.length > content.length) {
                                    content = fullContent
                                }
                            }
                            
                            when (val result = geminiClient.summarizeForTts(content, news.link)) {
                                is GeminiApiClient.SummarizeResult.Success -> result.text
                                else -> news.description.take(200)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Error summarizing content: ${e.message}")
                            news.description.take(200)
                        }
                    }
                    
                    introJob.join()
                    val summaryText = summaryDeferred.await()
                    
                    if (!isActive) break
                    
                    // Speak summary
                    _serviceState.update { it.copy(currentSummary = summaryText) }
                    ttsManager.speakAndWait(summaryText)
                    
                    // Mark completed
                    isItemCompleted = true
                    
                    // Pause between items
                    if (index < itemsToRead.size - 1) {
                        delay(1000)
                    }
                }
                
                // Finished
                val closingMsg = "Đã đọc xong ${itemsToRead.size} tin, mời anh chị chọn 5 tin khác để đọc ạ."
                ttsManager.speakAndWait(closingMsg)
                
                _serviceState.update { state ->
                    state.copy(
                        isReading = false,
                        currentIndex = -1,
                        currentSummary = closingMsg
                    )
                }
                updatePlaybackState(false)
                
                // Stop service after completion
                delay(2000)
                stopSelf()
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Reading cancelled")
                _serviceState.update { it.copy(isReading = false, currentIndex = -1) }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading news", e)
                _serviceState.update { state ->
                    state.copy(
                        isReading = false,
                        currentIndex = -1,
                        error = "Lỗi khi đọc tin: ${e.message}"
                    )
                }
                updatePlaybackState(false)
                stopSelf()
            }
        }
    }

    fun stopReading() {
        DebugLogger.log(TAG, ">>> Stopping reading")
        
        // FIX #3: Prevent overwriting existing valid resume state with empty state
        // This happens if audio focus loss ALREADY saved state, then stop() is called.
        val currentState = _resumeState.value
        if (!currentState.isResumable) {
            // Only save if we don't already have a valid resume state
            saveResumeState()
        } else {
             DebugLogger.log(TAG, ">>> stopReading: Resume state already exists, skipping save to avoid overwrite")
        }
        
        readAllJob?.cancel()
        ttsManager.stop()
        _serviceState.update { it.copy(isReading = false, currentIndex = -1, isReadAllMode = false) }
        updatePlaybackState(false)
        
        // FIX: Reset navigation flag
        isNavigatingInSingleMode = false
        
        // Release WakeLock and cancel monitor
        wakeLockJob?.cancel()
        releaseWakeLockSafely()
    }
    
    /**
     * Save current reading position for resume functionality.
     * Called when audio focus is lost (e.g., phone call).
     */
    private fun saveResumeState() {
        // FIX: Use getSentenceStateSnapshot() which uses cached snapshot captured 
        // BEFORE stop() was called (avoiding race condition)
        val snapshot = ttsManager.getSentenceStateSnapshot()
        val sentences = snapshot.sentences
        val sentenceIndex = snapshot.currentIndex
        
        val news = newsItems.getOrNull(currentNewsIndex)
        val title = news?.translatedTitle ?: news?.title ?: currentTitle

        // Case 1: Has content sentences -> Save precise position
        if (sentences.isNotEmpty() && sentenceIndex < sentences.size) {
            _resumeState.update {
                ResumeState(
                    isResumable = true,
                    newsIndex = currentNewsIndex,
                    newsTitle = title,
                    sentences = sentences,
                    currentSentenceIndex = sentenceIndex,
                    isReadAllMode = isReadAllMode,
                    readFullContent = readFullContent,
                    fullText = currentFullText,
                    newsItem = news
                )
            }
            DebugLogger.log(TAG, ">>> Resume state saved (Precise): sentenceIndex=$sentenceIndex, title=$title")
            // FIX #4: Clear snapshot AFTER successful save
            ttsManager.clearSentenceSnapshot()
        } 
        // Case 2: No content yet (Title or Loading) but NOT completed -> Save "Start from beginning"
        else if (!isItemCompleted && newsItems.isNotEmpty()) {
            _resumeState.update {
                ResumeState(
                    isResumable = true,
                    newsIndex = currentNewsIndex,
                    newsTitle = title,
                    sentences = emptyList(), // Empty sentences means "restart item"
                    currentSentenceIndex = 0,
                    isReadAllMode = isReadAllMode,
                    readFullContent = readFullContent,
                    newsItem = news
                )
            }
            DebugLogger.log(TAG, ">>> Resume state saved (Restart Item): No sentences yet, title=$title")
            // FIX #4: Clear snapshot AFTER successful save
            ttsManager.clearSentenceSnapshot()
        }
        else {
            DebugLogger.log(TAG, ">>> No content to save for resume (Completed=$isItemCompleted, Sentences=${sentences.size})")
            // Don't clear snapshot if we didn't save - might be needed later
        }
    }
    
    /**
     * Resume playback from saved state.
     */
    /**
     * Resume playback from saved state.
     */
    private fun resumeFromState(state: ResumeState) {
        DebugLogger.log(TAG, ">>> Resuming from state: sentenceIndex=${state.currentSentenceIndex}, sentences=${state.sentences.size}")
        
        // FIX #2: DO NOT clear resume state immediately. 
        // Keep it until we successfully finish reading or update it with new progress.
        // clearResumeState()  <-- REMOVED
        
        // Restore service state
        currentNewsIndex = state.newsIndex
        isReadAllMode = state.isReadAllMode
        readFullContent = state.readFullContent
        currentTitle = state.newsTitle ?: ""
        
        // Re-acquire WakeLock
        releaseWakeLockSafely()
        wakeLock?.acquire(30 * 60 * 1000L)
        
        // IF sentences are empty, it means we imply "Restart Item" (interrupted during title/loading)
        if (state.sentences.isEmpty()) {
            DebugLogger.log(TAG, ">>> Resume: Sentences empty -> Restarting item from beginning")
            
            // FIX #7: Safety check for invalid index
            if (state.newsIndex < 0) {
                DebugLogger.log(TAG, ">>> Error: Invalid newsIndex (${state.newsIndex}), cannot resume")
                stopSelf()
                return
            }
            
            if (isReadAllMode) {
                // In Read All, we ideally need the full list. If missing, we can't fully restore Read All context easily
                // without re-detching. For now, try to restart current item.
                val news = newsItems.getOrNull(currentNewsIndex) ?: state.newsItem
                 if (news != null) {
                    if (newsItems.isEmpty()) {
                         // Reconstruct list with dummy items to maintain index alignment for UI
                         val paddingList = ArrayList<NewsItem>()
                         for (i in 0 until state.newsIndex) {
                             paddingList.add(NewsItem(id = "dummy_$i", title = "Tin đã qua", description = "", content = "", link = "", pubDate = ""))
                         }
                         paddingList.add(news)
                         newsItems = paddingList
                         currentNewsIndex = state.newsIndex
                    }
                    startReadingAllFromIndex(currentNewsIndex)
                } else {
                     DebugLogger.log(TAG, ">>> Error: Cannot restart Read All, news item missing")
                     stopSelf()
                }
            } else {
                val news = newsItems.getOrNull(currentNewsIndex) ?: state.newsItem
                if (news != null) {
                    if (newsItems.isEmpty()) {
                         // Reconstruct list with dummy items to maintain index alignment for UI
                         val paddingList = ArrayList<NewsItem>()
                         for (i in 0 until state.newsIndex) {
                             paddingList.add(NewsItem(id = "dummy_$i", title = "Tin đã qua", description = "", content = "", link = "", pubDate = ""))
                         }
                         paddingList.add(news)
                         newsItems = paddingList
                         currentNewsIndex = state.newsIndex
                    }
                    startReadingSingle(news, readFullContent)
                } else {
                    DebugLogger.log(TAG, ">>> Error: Cannot restart, news item not found")
                    stopSelf()
                }
            }
            return
        }
        
        // Normal Resume (Mid-content)
        readAllJob?.cancel()
        readAllJob = serviceScope.launch {
            _serviceState.update { s ->
                s.copy(
                    isReading = true,
                    currentIndex = currentNewsIndex,
                    isReadAllMode = isReadAllMode,
                    currentTitle = currentTitle,
                    error = null
                )
            }
            updatePlaybackState(true)
            updateNotification("Đang đọc tiếp: $currentTitle")
            
            try {
                // Resume from the saved sentence index
                // FIX #1: Check completedNormally return value
                val completedNormally = ttsManager.speakSentencesAndWait(
                    sentences = state.sentences,
                    startIndex = state.currentSentenceIndex
                )
                
                if (completedNormally) {
                    // Mark as completed ONLY if finished normally
                    isItemCompleted = true
                    
                    // FIX #2: Now clear resume state on successful completion
                    clearResumeState()
                    ttsManager.resetSentenceTracking()
                    
                    // Completed successfully
                    if (isReadAllMode && currentNewsIndex < newsItems.size - 1) {
                        // Continue to next news in Read All mode
                        currentNewsIndex++
                        startReadingAllFromIndex(currentNewsIndex)
                    } else {
                        // Single read completed
                        DebugLogger.log(TAG, ">>> Resume completed")
                        _serviceState.update { it.copy(isReading = false) }
                        updatePlaybackState(false)
                        stopSelf()
                    }
                } else {
                    // FIX #1: Interrupted again (e.g. 2nd phone call)
                    // Resume state is auto-saved by onPauseRequested callback
                    // We just exit here gracefully
                    DebugLogger.log(TAG, ">>> Resume interrupted again")
                    return@launch
                }
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Resume reading cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming", e)
                _serviceState.update { it.copy(isReading = false, error = "Lỗi: ${e.message}") }
                stopSelf()
            }
        }
    }
    
    /**
     * Start a coroutine to monitor and extend WakeLock intelligently.
     * Strategy:
     * 1. Acquire lock for 10 minutes (safer than 30m).
     * 2. Every 5 minutes, check if we are still reading.
     * 3. If yes -> Extend lock for another 10 minutes.
     * 4. If no -> Release lock and stop monitoring.
     */
    private fun startWakeLockMonitor() {
        wakeLockJob?.cancel() // Cancel any existing monitor
        
        wakeLockJob = serviceScope.launch {
            DebugLogger.log(TAG, ">>> WakeLock Monitor: Started")
            
            try {
                // Initial acquisition
                if (wakeLock?.isHeld == true) wakeLock?.release()
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
                DebugLogger.log(TAG, ">>> WakeLock Monitor: Acquired (10 mins)")
                
                while (isActive) {
                    // Check every 5 minutes
                    delay(5 * 60 * 1000L)
                    
                    if (_serviceState.value.isReading) {
                        // Still reading -> Extend lock
                        // Note: acquire() with timeout acts as a reference counted lock or just updates internal state depending on implementation.
                        // Ideally release then acquire to reset timer safely or just acquire again if ref counted.
                        // Best practice for "Extending": Release old, Acquire new.
                        
                        // Safety: Release potentially old lock (if ref count > 1, this decrements. If not ref counted, it releases).
                        // To be clean: Ensure we hold only 1 reference.
                        if (wakeLock?.isHeld == true) {
                            wakeLock?.release()
                        }
                        wakeLock?.acquire(10 * 60 * 1000L)
                        DebugLogger.log(TAG, ">>> WakeLock Monitor: Extended (10 mins)")
                    } else {
                        // Not reading anymore -> Stop monitoring
                        DebugLogger.log(TAG, ">>> WakeLock Monitor: Not reading, releasing lock")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock monitor error: ${e.message}")
            } finally {
                releaseWakeLockSafely()
            }
        }
    }

    /**
     * Safely release WakeLock if held.
     * FIX: Centralized to prevent memory leaks.
     */
    private fun releaseWakeLockSafely() {
        // Also cancel monitor job if it's not the one calling this (to avoid self-cancellation issues if running within it, though finally block handles it)
        // Ideally explicitly cancel job when stopping reading.
        
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
                DebugLogger.log(TAG, ">>> WakeLock released")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing WakeLock: ${e.message}")
            }
        }
    }

    /**
     * Skip to the next news item.
     * In Read All mode: skip current, continue reading from next until end.
     * In Single mode: just read the next item.
     * 
     * FIX: Uses Mutex for non-blocking synchronization (avoids blocking Main Thread).
     * FIX: Marks navigated news as read in database.
     */
    private fun skipToNext() {
        // FIX: Debounce - ignore if called too quickly
        val now = System.currentTimeMillis()
        val timeSinceLastSkip = now - lastSkipTime
        if (timeSinceLastSkip < SKIP_DEBOUNCE_MS) {
            Log.d(TAG, "skipToNext: Debounced (${timeSinceLastSkip}ms < ${SKIP_DEBOUNCE_MS}ms)")
            DebugLogger.log(TAG, ">>> NEXT: Debounced - bấm quá nhanh (${timeSinceLastSkip}ms)")
            return
        }
        lastSkipTime = now  // Update immediately to prevent rapid calls
        
        // FIX: Launch in coroutine with Mutex - no more isSkipping flag!
        serviceScope.launch {
            // FIX: Use tryLock() - if mutex is already held, another skip is in progress
            val acquired = skipMutex.tryLock()
            if (!acquired) {
                Log.d(TAG, "skipToNext: Mutex already locked, ignoring")
                DebugLogger.log(TAG, ">>> NEXT: BLOCKED - mutex locked (another skip in progress)")
                return@launch
            }
            
            DebugLogger.log(TAG, ">>> NEXT: Mutex acquired")
            
            try {
                val currentItem = newsItems.getOrNull(currentNewsIndex)
                val currentTitle = currentItem?.title ?: "(none)"
                Log.d(TAG, "skipToNext: currentIndex=$currentNewsIndex, total=${newsItems.size}, isReadAllMode=$isReadAllMode")
                DebugLogger.log(TAG, ">>> NEXT: Đang đọc tin [$currentNewsIndex]: $currentTitle")
                
                // Check if we're at the last item
                if (currentNewsIndex >= newsItems.size - 1) {
                    Log.d(TAG, "Already at last item, ignoring Next")
                    DebugLogger.log(TAG, ">>> NEXT: Đã ở tin cuối cùng, không thể Next")
                    // DON'T return here - let finally run!
                } else {
                    // Cancel current reading
                    readAllJob?.cancel()
                    ttsManager.stop()
                    
                    // Move to next
                    currentNewsIndex++
                    
                    // FIX: Capture values inside lock to prevent race
                    val targetIndex = currentNewsIndex
                    val targetItem = newsItems.getOrNull(targetIndex)
                    val isReadAll = isReadAllMode
                    val readFull = readFullContent
                    
                    if (targetItem != null) {
                        DebugLogger.log(TAG, ">>> NEXT: Chuyển sang tin [$targetIndex]: ${targetItem.title}")
                        
                        // FIX: Mark the target news as read - FIRE AND FORGET (don't block skip!)
                        // Using launch instead of withContext to prevent mutex deadlock
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                localSyncRepo?.markAsRead(targetItem.id) 
                                    ?: readNewsDao.markAsRead(ReadNewsItem(newsId = targetItem.id))
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to mark news as read: ${e.message}")
                            }
                        }
                        
                        if (isReadAll) {
                            // Continue reading from this index until end
                            startReadingAllFromIndex(targetIndex)
                        } else {
                            // FIX: Mark that user is navigating so service won't auto-stop
                            isNavigatingInSingleMode = true
                            DebugLogger.log(TAG, ">>> NEXT: Calling startReadingSingle for item $targetIndex")
                            startReadingSingle(targetItem, readFull)
                            DebugLogger.log(TAG, ">>> NEXT: startReadingSingle returned (coroutine launched)")
                        }
                    } else {
                        Log.e(TAG, "skipToNext: Invalid index $targetIndex")
                        DebugLogger.log(TAG, ">>> NEXT: Lỗi - không tìm thấy tin ở index $targetIndex")
                    }
                }
            } catch (e: CancellationException) {
                DebugLogger.log(TAG, ">>> NEXT: Cancelled")
                throw e  // Re-throw to allow proper cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error in skipToNext", e)
                DebugLogger.log(TAG, ">>> NEXT: Error - ${e.message}")
            } finally {
                DebugLogger.log(TAG, ">>> NEXT: Mutex released")
                skipMutex.unlock()  // Always release the mutex
            }
        }
    }

    /**
     * Skip to the previous news item.
     * In Read All mode: go back to previous, then continue reading until end.
     * In Single mode: just read the previous item.
     * 
     * FIX: Uses Mutex for non-blocking synchronization (avoids blocking Main Thread).
     * FIX: Marks navigated news as read in database.
     */
    private fun skipToPrevious() {
        // FIX: Debounce - ignore if called too quickly
        val now = System.currentTimeMillis()
        val timeSinceLastSkip = now - lastSkipTime
        if (timeSinceLastSkip < SKIP_DEBOUNCE_MS) {
            Log.d(TAG, "skipToPrevious: Debounced (${timeSinceLastSkip}ms < ${SKIP_DEBOUNCE_MS}ms)")
            DebugLogger.log(TAG, ">>> PREV: Debounced - bấm quá nhanh (${timeSinceLastSkip}ms)")
            return
        }
        lastSkipTime = now  // Update immediately to prevent rapid calls
        
        // FIX: Launch in coroutine with Mutex - no more isSkipping flag!
        serviceScope.launch {
            // FIX: Use tryLock() - if mutex is already held, another skip is in progress
            val acquired = skipMutex.tryLock()
            if (!acquired) {
                Log.d(TAG, "skipToPrevious: Mutex already locked, ignoring")
                DebugLogger.log(TAG, ">>> PREV: BLOCKED - mutex locked (another skip in progress)")
                return@launch
            }
            
            DebugLogger.log(TAG, ">>> PREV: Mutex acquired")
            
            try {
                val currentItem = newsItems.getOrNull(currentNewsIndex)
                val currentTitle = currentItem?.title ?: "(none)"
                Log.d(TAG, "skipToPrevious: currentIndex=$currentNewsIndex, isReadAllMode=$isReadAllMode")
                DebugLogger.log(TAG, ">>> PREV: Đang đọc tin [$currentNewsIndex]: $currentTitle")
                
                // Check if we're at the first item
                if (currentNewsIndex <= 0) {
                    Log.d(TAG, "Already at first item, ignoring Previous")
                    DebugLogger.log(TAG, ">>> PREV: Đã ở tin đầu tiên, không thể Previous")
                    // DON'T return here - let finally run!
                } else {
                    // Cancel current reading
                    readAllJob?.cancel()
                    ttsManager.stop()
                    
                    // Move to previous
                    currentNewsIndex--
                    
                    // FIX: Capture values inside lock to prevent race
                    val targetIndex = currentNewsIndex
                    val targetItem = newsItems.getOrNull(targetIndex)
                    val isReadAll = isReadAllMode
                    val readFull = readFullContent
                    
                    if (targetItem != null) {
                        DebugLogger.log(TAG, ">>> PREV: Chuyển sang tin [$targetIndex]: ${targetItem.title}")
                        
                        // FIX: Mark the target news as read - FIRE AND FORGET (don't block skip!)
                        // Using launch instead of withContext to prevent mutex deadlock
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                localSyncRepo?.markAsRead(targetItem.id) 
                                    ?: readNewsDao.markAsRead(ReadNewsItem(newsId = targetItem.id))
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to mark news as read: ${e.message}")
                            }
                        }
                        
                        if (isReadAll) {
                            // Continue reading from this index until end
                            startReadingAllFromIndex(targetIndex)
                        } else {
                            // FIX: Mark that user is navigating so service won't auto-stop
                            isNavigatingInSingleMode = true
                            DebugLogger.log(TAG, ">>> PREV: Calling startReadingSingle for item $targetIndex")
                            startReadingSingle(targetItem, readFull)
                            DebugLogger.log(TAG, ">>> PREV: startReadingSingle returned (coroutine launched)")
                        }
                    } else {
                        Log.e(TAG, "skipToPrevious: Invalid index $targetIndex")
                        DebugLogger.log(TAG, ">>> PREV: Lỗi - không tìm thấy tin ở index $targetIndex")
                    }
                }
            } catch (e: CancellationException) {
                DebugLogger.log(TAG, ">>> PREV: Cancelled")
                throw e  // Re-throw to allow proper cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error in skipToPrevious", e)
                DebugLogger.log(TAG, ">>> PREV: Error - ${e.message}")
            } finally {
                DebugLogger.log(TAG, ">>> PREV: Mutex released")
                skipMutex.unlock()  // Always release the mutex
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Đọc tin tức",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo khi đang đọc tin tức"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Media Actions
        val prevIntent = Intent(this, NewsReaderService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, NewsReaderService::class.java).apply { action = ACTION_STOP } // Using STOP for now as pause
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, NewsReaderService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Load static cover image
        val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.news_cover)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RSS Reader - Đang đọc tin")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(largeIcon)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(pausePendingIntent) // Stop when swiped away
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            
            // Add Media Actions: Previous (0), Pause (1), Next (2)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            
            // Apply MediaStyle
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Show all 3 buttons in compact view
            )
            
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT) // FIX: Explicitly mark as Transport (Media)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMetadata(title: String, source: String?) {
        // FIX: Provide a "Fake" duration (e.g. 5 minutes) to convince Car Head Unit this is a music track.
        // Cars often treat "Unknown Duration" (0 or -1) as a live stream or voice command.
        val fakeDurationMs = 5 * 60 * 1000L 
        
        val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.news_cover)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, source ?: "RSS Reader")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Tin Tức Tổng Hợp")
            // FIX: Add Duration
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, fakeDurationMs)
            // FIX: Add Album Art (Bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIcon)
            // FIX: Add Album Art URI (optional but helpful for some systems)
            // .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "android.resource://$packageName/${R.drawable.news_cover}")
            .build()
        
        mediaSession?.setMetadata(metadata)
    }

    /**
     * Called when user removes app from recent apps list.
     * Stop TTS and self-destruct.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App removed from recent apps, stopping service")
        stopReading()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        
        // FIX: Use centralized WakeLock release
        releaseWakeLockSafely()
        
        // Unregister listener
        AppPreferences.getInstance(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        
        readAllJob?.cancel()
        serviceScope.cancel()
        ttsManager.shutdown()
        
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null
        
        // Reset static state so UI stops blinking
        resetState()
    }
}

/**
 * State for the news reader service.
 */
data class NewsReaderState(
    val isReading: Boolean = false,
    val currentIndex: Int = -1,
    val isReadAllMode: Boolean = false,
    val currentTitle: String? = null,
    val currentSummary: String? = null,
    val error: String? = null,
    val readingProgress: Float = 0f  // NEW
)
