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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.skul9x.rssreader.MainActivity
import com.skul9x.rssreader.R
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.model.NewsItem
import com.skul9x.rssreader.data.network.ArticleContentFetcher
import com.skul9x.rssreader.data.network.GeminiApiClient
import com.skul9x.rssreader.tts.TtsManager
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // Intent actions
        const val ACTION_START_READ_ALL = "com.skul9x.rssreader.START_READ_ALL"
        const val ACTION_STOP = "com.skul9x.rssreader.STOP"
        const val ACTION_NEXT = "com.skul9x.rssreader.NEXT"
        const val ACTION_PREVIOUS = "com.skul9x.rssreader.PREVIOUS"
        
        const val EXTRA_NEWS_ITEMS = "news_items"
        
        // STATIC StateFlow for ViewModel to observe (global access)
        private val _serviceState = MutableStateFlow(NewsReaderState())
        val serviceState: StateFlow<NewsReaderState> = _serviceState.asStateFlow()
        
        // Reset state when Service is not running
        fun resetState() {
            _serviceState.value = NewsReaderState()
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

    private var newsItems: List<NewsItem> = emptyList()

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

    private fun initializeComponents() {
        ttsManager = TtsManager(this)
        ttsManager.initialize()
        
        geminiClient = GeminiApiClient(this)
        articleFetcher = ArticleContentFetcher()
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, SESSION_TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession: Play")
                    // Resume if paused (future enhancement)
                }
                
                override fun onPause() {
                    Log.d(TAG, "MediaSession: Pause")
                    stopReading()
                }
                
                override fun onStop() {
                    Log.d(TAG, "MediaSession: Stop")
                    stopReading()
                }
                
                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession: Next")
                    // Skip to next item (if in read-all mode)
                }
                
                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession: Previous")
                    // Skip to previous (if in read-all mode)
                }
            })
            
            isActive = true
        }
        
        updatePlaybackState(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_READ_ALL -> {
                @Suppress("DEPRECATION")
                val items = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_NEWS_ITEMS, NewsItem::class.java)
                } else {
                    intent.getParcelableArrayListExtra(EXTRA_NEWS_ITEMS)
                }
                
                if (items != null && items.isNotEmpty()) {
                    newsItems = items
                    startForeground(NOTIFICATION_ID, createNotification("Đang chuẩn bị đọc tin..."))
                    startReadingAll()
                }
            }
            ACTION_STOP -> {
                stopReading()
                stopSelf()
            }
            ACTION_NEXT -> {
                // Future: skip to next
            }
            ACTION_PREVIOUS -> {
                // Future: skip to previous
            }
        }
        
        return START_NOT_STICKY
    }

    private fun startReadingAll() {
        readAllJob?.cancel()
        
        readAllJob = serviceScope.launch {
            _serviceState.value = _serviceState.value.copy(
                isReading = true,
                currentIndex = -1,
                error = null
            )
            updatePlaybackState(true)

            val ordinalWords = listOf("nhất", "hai", "ba", "bốn", "năm")
            
            try {
                geminiClient.refreshApiKeys()
                
                for ((index, news) in newsItems.take(5).withIndex()) {
                    if (!isActive) break
                    
                    // Update state
                    _serviceState.value = _serviceState.value.copy(
                        currentIndex = index,
                        currentTitle = news.title
                    )
                    updateNotification("Đang đọc: ${news.title}")
                    updateMetadata(news.title, news.sourceName)
                    
                    val ordinal = ordinalWords.getOrElse(index) { "${index + 1}" }
                    
                    // Speak title
                    val introJob = launch {
                        ttsManager.speakAndWait("Tin thứ $ordinal, ${news.title}.")
                    }
                    
                    // Summarize in background
                    val summaryDeferred = async(Dispatchers.IO) {
                        var content = news.getContentForSummary()
                        if (content.length < 500 && news.link.isNotBlank()) {
                            val fullContent = articleFetcher.fetchArticleContent(news.link)
                            if (fullContent != null && fullContent.length > content.length) {
                                content = fullContent
                            }
                        }
                        
                        when (val result = geminiClient.summarizeForTts(content)) {
                            is GeminiApiClient.SummarizeResult.Success -> result.text
                            else -> news.description.take(200)
                        }
                    }
                    
                    introJob.join()
                    val summaryText = summaryDeferred.await()
                    
                    if (!isActive) break
                    
                    // Speak summary
                    _serviceState.value = _serviceState.value.copy(currentSummary = summaryText)
                    ttsManager.speakAndWait(summaryText)
                    
                    // Pause between items
                    if (index < minOf(5, newsItems.size) - 1) {
                        delay(1000)
                    }
                }
                
                // Finished
                val closingMsg = "Đã đọc xong ${minOf(5, newsItems.size)} tin, mời anh chị chọn 5 tin khác để đọc ạ."
                ttsManager.speakAndWait(closingMsg)
                
                _serviceState.value = _serviceState.value.copy(
                    isReading = false,
                    currentIndex = -1,
                    currentSummary = closingMsg
                )
                updatePlaybackState(false)
                
                // Stop service after completion
                delay(2000)
                stopSelf()
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Reading cancelled")
                _serviceState.value = _serviceState.value.copy(isReading = false, currentIndex = -1)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading news", e)
                _serviceState.value = _serviceState.value.copy(
                    isReading = false,
                    currentIndex = -1,
                    error = "Lỗi khi đọc tin: ${e.message}"
                )
                updatePlaybackState(false)
                stopSelf()
            }
        }
    }

    fun stopReading() {
        readAllJob?.cancel()
        ttsManager.stop()
        _serviceState.value = _serviceState.value.copy(isReading = false, currentIndex = -1)
        updatePlaybackState(false)
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

        // Stop action
        val stopIntent = Intent(this, NewsReaderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RSS Reader - Đọc tin tức")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Dừng", stopPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, source ?: "RSS Reader")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Tin Tức")
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
    val currentTitle: String? = null,
    val currentSummary: String? = null,
    val error: String? = null
)
