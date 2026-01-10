package com.skul9x.rssreader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.skul9x.rssreader.utils.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

/**
 * TTS Manager for Vietnamese text-to-speech playback.
 * Handles initialization, speaking with chunking for long text, stopping, and lifecycle management.
 */
class TtsManager(context: Context) {
    // FIX: Use applicationContext to prevent Activity memory leak
    private val context: Context = context.applicationContext

    // FIX: Coroutine scope for silent audio generator
    private val ttsScope = CoroutineScope(Dispatchers.IO + Job())
    private var silentAudioTrack: AudioTrack? = null
    private var silentAudioJob: Job? = null

    companion object {
        private const val TAG = "TtsManager"
        private const val UTTERANCE_PREFIX = "rss_chunk_"
        // Android TTS has a limit around 4000 characters, use 3500 to be safe
        private const val MAX_CHUNK_SIZE = 3500
    }

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false

    // Audio focus management
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    @Volatile
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

    // Session ID to prevent Race Condition when skipping quickly
    @Volatile
    private var currentSessionId: String = ""

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
                hasAudioFocus = false // FIX: Mark focus as lost so we force a re-request next time we speak
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Can duck but we choose to pause for TTS clarity
                Log.d(TAG, "Audio focus loss can duck - pausing anyway")
                hasAudioFocus = false
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
                // FIX: Release TTS resource on init failure to prevent Resource Leak
                tts?.shutdown()
                tts = null
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

            // Get initial audio stream mode
            val appPreferences = com.skul9x.rssreader.data.local.AppPreferences.getInstance(context)
            updateAudioAttributes(appPreferences.getAudioStreamMode(), engine)

            // Set up listener
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started: $utteranceId")
                    // Note: _isSpeaking is already set to true in speak() to avoid Heisenbug
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS done: $utteranceId")
                    
                    synchronized(chunkLock) {
                        // FIX: Only process if callback belongs to current session
                        if (utteranceId?.contains(currentSessionId) == true) {
                            completedChunks++
                            // Only mark as not speaking when all chunks are done
                            if (completedChunks >= totalChunks) {
                                _isSpeaking.value = false
                                completedChunks = 0
                                totalChunks = 0
                            }
                        } else {
                            Log.d(TAG, "Ignored old session callback: $utteranceId")
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleTtsError(utteranceId, -1)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleTtsError(utteranceId, errorCode)
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
                // FIX: Ensure breakPoint doesn't exceed string bounds (prevent NPE/IndexOutOfBounds)
                breakPoint = minOf(MAX_CHUNK_SIZE, remaining.length - 1)
            }

            // Include the period in the chunk
            // FIX: Bounds check before accessing character
            val chunkEnd = if (breakPoint < remaining.length && remaining[breakPoint] == '.') breakPoint + 1 else breakPoint
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
        // FIX: ALWAYS request focus, never assume we have it. 
        // This ensures the system (and Car Head Unit) knows we are the ACTIVE media session.
        // if (hasAudioFocus) return true 
        
        // Read user preference for audio mixing behavior
        val prefs = com.skul9x.rssreader.data.local.AppPreferences.getInstance(context)
        val mixMode = prefs.getAudioMixMode()
        
        val focusGainType = when (mixMode) {
            com.skul9x.rssreader.data.local.AudioMixMode.NO_MIX -> AudioManager.AUDIOFOCUS_GAIN
            com.skul9x.rssreader.data.local.AudioMixMode.PAUSE_MUSIC -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            com.skul9x.rssreader.data.local.AudioMixMode.DUCK_MUSIC -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        }
        
        Log.d(TAG, "Requesting audio focus with mode: $mixMode (type: $focusGainType)")
        DebugLogger.log(TAG, ">>> Requesting AudioFocus: MixMode=$mixMode, GainType=$focusGainType (1=GAIN, 2=GAIN_TRANSIENT, 3=GAIN_TRANSIENT_MAY_DUCK)")


        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
                
            DebugLogger.log(TAG, ">>> FocusRequest Attributes: Usage=${AudioAttributes.USAGE_MEDIA}, ContentType=${AudioAttributes.CONTENT_TYPE_MUSIC}")

            audioFocusRequest = AudioFocusRequest.Builder(focusGainType)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(mixMode != com.skul9x.rssreader.data.local.AudioMixMode.DUCK_MUSIC)
                .build()

            result = audioFocusRequest?.let { 
                audioManager.requestAudioFocus(it) 
            } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            DebugLogger.log(TAG, ">>> FocusRequest Legacy: Stream=${AudioManager.STREAM_MUSIC}, GainType=$focusGainType")
            result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                focusGainType
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
     * Handle TTS errors uniformly.
     * FIX: Only process if callback belongs to current session.
     */
    private fun handleTtsError(utteranceId: String?, errorCode: Int) {
        Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
        
        synchronized(chunkLock) {
            if (utteranceId?.contains(currentSessionId) == true) {
                _isSpeaking.value = false
                _error.value = if (errorCode == -1) {
                    "Lỗi khi đọc tin tức"
                } else {
                    "Lỗi khi đọc tin tức (mã: $errorCode)"
                }
                completedChunks = 0
                totalChunks = 0
            } else {
                Log.d(TAG, "Ignored old session error: $utteranceId")
            }
        }
    }

    /**
     * Speak the given text. Automatically chunks long text.
     * Stops any current speech first and requests audio focus to pause other apps.
     * FIX: Uses Session ID to prevent Race Condition and sets _isSpeaking immediately.
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

            // Split text into chunks
            val chunks = splitTextIntoChunks(text)
            
            // FIX: Handle edge case - empty text
            if (chunks.isEmpty()) {
                Log.w(TAG, "No chunks to speak, skipping")
                _isSpeaking.value = false
                return
            }
            
            // FIX: Create new Session ID to prevent Race Condition
            val newSessionId = UUID.randomUUID().toString()
            
            synchronized(chunkLock) {
                currentSessionId = newSessionId
                completedChunks = 0
                totalChunks = chunks.size
                // FIX: Set true IMMEDIATELY to avoid Heisenbug
                _isSpeaking.value = true
            }
            
            Log.d(TAG, "Speaking ${chunks.size} chunks, sessionId=$newSessionId")

            // Queue all chunks
            val prefs = com.skul9x.rssreader.data.local.AppPreferences.getInstance(context)
            val streamMode = prefs.getAudioStreamMode()
            
            // FIX: Explicitly pass the Stream Type in the parameters Bundle.
            // This is required for some Car Head Units and older Android versions to correctly 
            // identify the audio as MUSIC instead of Voice/Navigation.
            val params = android.os.Bundle()
            val streamType = when (streamMode) {
                com.skul9x.rssreader.data.local.AudioStreamMode.MEDIA -> AudioManager.STREAM_MUSIC
                com.skul9x.rssreader.data.local.AudioStreamMode.ALARM -> AudioManager.STREAM_ALARM
                com.skul9x.rssreader.data.local.AudioStreamMode.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
                com.skul9x.rssreader.data.local.AudioStreamMode.RINGTONE -> AudioManager.STREAM_RING
                com.skul9x.rssreader.data.local.AudioStreamMode.NAVIGATION -> AudioManager.STREAM_MUSIC // Use Music for nav to ensure focus?
            }
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType)
            
            DebugLogger.log(TAG, ">>> Speaking with StreamType=$streamType (3=MUSIC) for Mode=$streamMode")
            DebugLogger.log(TAG, ">>> Params Bundle: $params")

            // FIX: Start silent audio track to keep session alive during TTS pauses
            if (streamMode == com.skul9x.rssreader.data.local.AudioStreamMode.MEDIA) {
                startSilentAudio()
            }

            chunks.forEachIndexed { index, chunk ->
                val queueMode = if (index == 0) {
                    TextToSpeech.QUEUE_FLUSH
                } else {
                    TextToSpeech.QUEUE_ADD
                }
                // FIX: Embed Session ID into utteranceId
                val utteranceId = "${newSessionId}_${UTTERANCE_PREFIX}$index"
                
                val result = engine.speak(
                    chunk,
                    queueMode,
                    params,
                    utteranceId
                )

                if (result == TextToSpeech.ERROR) {
                    Log.e(TAG, "Failed to speak chunk $index")
                    if (index == 0) {
                        _error.value = "Không thể đọc tin tức"
                        synchronized(chunkLock) {
                            _isSpeaking.value = false
                        }
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
     * FIX: Added timeout to prevent Starvation (infinite hang).
     */
    suspend fun speakAndWait(text: String) {
        speakSafely(text)
        
        // FIX: Timeout the entire reading process (5 minutes for long articles)
        val completed = withTimeoutOrNull(5 * 60 * 1000L) {
            // Wait for it to finish (isSpeaking -> false)
            isSpeaking.first { !it }
            true
        }
        
        if (completed == null) {
            Log.w(TAG, "speakAndWait timed out after 5 minutes, forcing stop")
            stop()
        }
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
            // FIX: Use synchronized to prevent Race Condition with onDone() callback
            synchronized(chunkLock) {
                _isSpeaking.value = false
                completedChunks = 0
                totalChunks = 0
            }
        }
        
        // Stop silent audio track
        stopSilentAudio()
        
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

    /**
     * Update Audio Attributes dynamically based on selected mode.
     */
    /**
     * Update Audio Attributes dynamically based on selected mode.
     */
    fun updateAudioStream(mode: com.skul9x.rssreader.data.local.AudioStreamMode) {
        tts?.let { engine ->
            updateAudioAttributes(mode, engine)
        }
    }

    private fun updateAudioAttributes(
        mode: com.skul9x.rssreader.data.local.AudioStreamMode,
        engine: TextToSpeech
    ) {
        Log.d(TAG, "Updating TTS Audio Attributes to mode: $mode")
        DebugLogger.log(TAG, ">>> updateAudioAttributes: Mode=$mode")

        val audioUsage = when (mode) {
            com.skul9x.rssreader.data.local.AudioStreamMode.MEDIA -> AudioAttributes.USAGE_MEDIA
            com.skul9x.rssreader.data.local.AudioStreamMode.ALARM -> AudioAttributes.USAGE_ALARM
            com.skul9x.rssreader.data.local.AudioStreamMode.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
            com.skul9x.rssreader.data.local.AudioStreamMode.RINGTONE -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            com.skul9x.rssreader.data.local.AudioStreamMode.NAVIGATION -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        }

        // FIX: Use CONTENT_TYPE_MUSIC when in MEDIA mode. 
        val contentType = if (mode == com.skul9x.rssreader.data.local.AudioStreamMode.MEDIA) {
            AudioAttributes.CONTENT_TYPE_MUSIC
        } else {
            AudioAttributes.CONTENT_TYPE_SPEECH
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(audioUsage)
            .setContentType(contentType)
            .build()
            
        DebugLogger.log(TAG, ">>> AudioAttributes Built: Usage=$audioUsage, ContentType=$contentType, Flags=${audioAttributes.flags}")
        DebugLogger.log(TAG, ">>> AudioAttributes.toString(): $audioAttributes")
            
        engine.setAudioAttributes(audioAttributes)
    }

    /**
     * Start playing silent audio to keep the Bluetooth/Car connection alive as "Music".
     * This prevents the car from treating the app as "Navigation/Voice" which times out.
     */
    private fun startSilentAudio() {
        if (silentAudioTrack != null) return

        try {
            Log.d(TAG, "Starting silent audio track to keep session alive...")
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            silentAudioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            silentAudioTrack?.play()

            // Feed silence
            silentAudioJob = ttsScope.launch {
                val silence = ByteArray(bufferSize) // Default is all zeros
                while (isActive && silentAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    silentAudioTrack?.write(silence, 0, silence.size)
                    delay(100) // Lower frequency to reduce CPU load
                }
            }
            DebugLogger.log(TAG, ">>> Silent Audio Track STARTED (Keep-Alive)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start silent audio", e)
        }
    }

    private fun stopSilentAudio() {
        try {
            silentAudioJob?.cancel()
            silentAudioTrack?.stop()
            silentAudioTrack?.release()
            silentAudioTrack = null
            DebugLogger.log(TAG, ">>> Silent Audio Track STOPPED")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping silent audio", e)
        }
    }
}
