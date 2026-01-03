package com.skul9x.rssreader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * TTS Manager for Vietnamese text-to-speech playback.
 * Handles initialization, speaking with chunking for long text, stopping, and lifecycle management.
 */
class TtsManager(private val context: Context) {

    companion object {
        private const val TAG = "TtsManager"
        private const val UTTERANCE_PREFIX = "rss_chunk_"
        // Android TTS has a limit around 4000 characters, use 3500 to be safe
        private const val MAX_CHUNK_SIZE = 3500
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    // Audio focus management
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Track chunks for long text
    private var totalChunks = 0
    private var completedChunks = 0
    private val chunkLock = Any()

    // Audio focus change listener
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss - stop and release
                Log.d(TAG, "Audio focus lost permanently")
                stop()
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss - pause
                Log.d(TAG, "Audio focus lost temporarily")
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Can duck but we choose to pause for TTS clarity
                Log.d(TAG, "Audio focus loss can duck - pausing anyway")
                stop()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus
                Log.d(TAG, "Audio focus gained")
                hasAudioFocus = true
            }
        }
    }

    /**
     * Initialize TTS engine with Vietnamese locale.
     */
    fun initialize() {
        if (isInitialized) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupVietnamese()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                _error.value = "Không thể khởi tạo Text-to-Speech"
            }
        }
    }

    private fun setupVietnamese() {
        tts?.let { engine ->
            // Try Vietnamese locale
            val vietnameseLocale = Locale("vi", "VN")
            val result = engine.setLanguage(vietnameseLocale)

            when (result) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "Vietnamese not available, trying default")
                    // Try with just "vi" language code
                    val viOnly = Locale("vi")
                    val fallbackResult = engine.setLanguage(viOnly)
                    if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
                        fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        _error.value = "Vui lòng cài đặt giọng đọc tiếng Việt trong cài đặt TTS"
                    }
                }
            }

            // Set speech rate (0.5 to 2.0, 1.0 is normal)
            engine.setSpeechRate(0.95f)

            // Set pitch (0.5 to 2.0, 1.0 is normal)
            engine.setPitch(1.0f)

            // Set audio attributes to use MEDIA stream instead of navigation/notification
            // This ensures volume control affects the correct audio stream on Android Box
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttributes)

            // Set up listener
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started: $utteranceId")
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS done: $utteranceId")
                    
                    synchronized(chunkLock) {
                        completedChunks++
                        // Only mark as not speaking when all chunks are done
                        if (completedChunks >= totalChunks) {
                            _isSpeaking.value = false
                            completedChunks = 0
                            totalChunks = 0
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error: $utteranceId")
                    _isSpeaking.value = false
                    _error.value = "Lỗi khi đọc tin tức"
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
                    _isSpeaking.value = false
                    _error.value = "Lỗi khi đọc tin tức (mã: $errorCode)"
                }
            })

            isInitialized = true
            _isReady.value = true
            Log.d(TAG, "TTS initialized successfully")
        }
    }

    /**
     * Split text into chunks at sentence boundaries.
     */
    private fun splitTextIntoChunks(text: String): List<String> {
        if (text.length <= MAX_CHUNK_SIZE) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_CHUNK_SIZE) {
                chunks.add(remaining)
                break
            }

            // Find a good break point (end of sentence)
            var breakPoint = remaining.lastIndexOf(". ", MAX_CHUNK_SIZE)
            if (breakPoint == -1 || breakPoint < MAX_CHUNK_SIZE / 2) {
                breakPoint = remaining.lastIndexOf("。", MAX_CHUNK_SIZE)
            }
            if (breakPoint == -1 || breakPoint < MAX_CHUNK_SIZE / 2) {
                breakPoint = remaining.lastIndexOf("\n", MAX_CHUNK_SIZE)
            }
            if (breakPoint == -1 || breakPoint < MAX_CHUNK_SIZE / 2) {
                breakPoint = remaining.lastIndexOf(" ", MAX_CHUNK_SIZE)
            }
            if (breakPoint == -1 || breakPoint < MAX_CHUNK_SIZE / 2) {
                breakPoint = MAX_CHUNK_SIZE
            }

            // Include the period in the chunk
            val chunkEnd = if (remaining[breakPoint] == '.') breakPoint + 1 else breakPoint
            chunks.add(remaining.substring(0, chunkEnd).trim())
            remaining = remaining.substring(chunkEnd).trim()
        }

        return chunks
    }

    /**
     * Speak the given text. Automatically chunks long text.
     * Stops any current speech first.
     */
    /**
     * Request audio focus to pause other apps' audio.
     * Returns true if focus was granted.
     */
    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(true)
                .build()

            result = audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        Log.d(TAG, "Audio focus request result: $result, granted: $hasAudioFocus")
        return hasAudioFocus
    }

    /**
     * Abandon audio focus to allow other apps to play.
     */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        Log.d(TAG, "Audio focus abandoned")
    }

    /**
     * Speak the given text. Automatically chunks long text.
     * Stops any current speech first and requests audio focus to pause other apps.
     */
    fun speak(text: String) {
        if (!isInitialized) {
            _error.value = "Text-to-Speech chưa sẵn sàng"
            return
        }

        // Request audio focus first - this will pause other apps
        if (!requestAudioFocus()) {
            Log.w(TAG, "Could not get audio focus, speaking anyway")
        }

        tts?.let { engine ->
            // Stop if currently speaking
            if (engine.isSpeaking) {
                engine.stop()
            }

            _error.value = null
            
            synchronized(chunkLock) {
                completedChunks = 0
            }



            // Split text into chunks
            val chunks = splitTextIntoChunks(text)
            
            synchronized(chunkLock) {
                totalChunks = chunks.size
            }
            
            Log.d(TAG, "Speaking ${chunks.size} chunks, total length: ${text.length}")

            // Queue all chunks
            chunks.forEachIndexed { index, chunk ->
                // Always use QUEUE_ADD for subsequent chunks, but first chunk needs to clear previous
                // Since we removed prewarm, we can just use QUEUE_FLUSH for the first one
                val queueMode = if (index == 0) {
                    TextToSpeech.QUEUE_FLUSH
                } else {
                    TextToSpeech.QUEUE_ADD
                }
                val utteranceId = "${UTTERANCE_PREFIX}$index"
                
                val result = engine.speak(
                    chunk,
                    queueMode,
                    null,
                    utteranceId
                )

                if (result == TextToSpeech.ERROR) {
                    Log.e(TAG, "Failed to speak chunk $index")
                    if (index == 0) {
                        _error.value = "Không thể đọc tin tức"
                        abandonAudioFocus()
                    }
                }
            }
        }
    }

    /**
     * Speak the given text safely by waiting for TTS initialization.
     * This prevents race condition when calling speak() immediately after initialize().
     * Use this from coroutines instead of speak() for guaranteed safety.
     */
    suspend fun speakSafely(text: String) {
        // Wait for TTS to be ready if not already
        if (!_isReady.value) {
            Log.d(TAG, "Waiting for TTS initialization...")
            _isReady.first { it }
            Log.d(TAG, "TTS is now ready, proceeding to speak")
        }
        speak(text)
    }

    /**
     * Speak the text and SUSPEND until it finishes.
     * Useful for sequential reading (e.g., Read Item 1 -> Wait -> Read Item 2).
     */
    suspend fun speakAndWait(text: String) {
        // 1. Start speaking
        speakSafely(text)
        
        // 2. Wait for it to actually start (isSpeaking -> true)
        // Timeout check could be added here in production
        isSpeaking.first { it } 
        
        // 3. Wait for it to finish (isSpeaking -> false)
        isSpeaking.first { !it }
    }

    /**
     * Stop current speech playback.
     * Note: We intentionally DO NOT abandon audio focus here to keep receiving
     * media button events from car steering wheel (like Spotify/YouTube behavior).
     * Audio focus is only released in shutdown() when the app is truly closing.
     */
    fun stop() {
        tts?.let { engine ->
            engine.stop()
            _isSpeaking.value = false
            completedChunks = 0
            totalChunks = 0
        }
        // DO NOT call abandonAudioFocus() here - keep media button control
    }

    /**
     * Clear any error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Release TTS resources. Call this when done (e.g., in Activity onDestroy).
     */
    fun shutdown() {
        tts?.let { engine ->
            engine.stop()
            engine.shutdown()
        }
        tts = null
        isInitialized = false
        _isReady.value = false
        _isSpeaking.value = false
        // Make sure to release audio focus
        abandonAudioFocus()
    }
}
