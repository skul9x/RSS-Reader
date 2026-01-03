package com.skul9x.rssreader.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

/**
 * Manages MediaSession to handle media button events from car steering wheel.
 * Supports Next/Previous buttons to switch between news items.
 * Also handles Audio Focus to ensure the car system recognizes this app as the active media source.
 */
class MediaButtonManager(
    private val context: Context,
    private val onNextPressed: () -> Unit,
    private val onPreviousPressed: () -> Unit,
    private val onPlayPausePressed: () -> Unit
) {
    companion object {
        private const val TAG = "MediaButtonManager"
        private const val SESSION_TAG = "RssReaderMediaSession"
    }

    private var mediaSession: MediaSessionCompat? = null
    
    // Audio Focus management - required for car steering wheel button recognition
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * Initialize the MediaSession. Call this in Activity onCreate.
     */
    fun initialize() {
        if (mediaSession != null) return

        mediaSession = MediaSessionCompat(context, SESSION_TAG).apply {
            // Set flags to handle media buttons and transport controls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Set the callback to handle button presses
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "Play button pressed")
                    onPlayPausePressed()
                }

                override fun onPause() {
                    Log.d(TAG, "Pause button pressed")
                    onPlayPausePressed()
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "Next button pressed")
                    onNextPressed()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "Previous button pressed")
                    onPreviousPressed()
                }

                override fun onStop() {
                    Log.d(TAG, "Stop button pressed")
                    onPlayPausePressed()
                }
                
                // Some car systems send FastForward/Rewind instead of Next/Previous
                override fun onFastForward() {
                    Log.d(TAG, "FastForward button pressed -> treating as Next")
                    onNextPressed()
                }

                override fun onRewind() {
                    Log.d(TAG, "Rewind button pressed -> treating as Previous")
                    onPreviousPressed()
                }
            })

            // Set initial playback state - Ready but not playing
            updatePlaybackState(false)
        }

        Log.d(TAG, "MediaSession initialized")
    }
    
    /**
     * Activate or deactivate the MediaSession and Audio Focus.
     * Call setActive(true) when app is visible, setActive(false) when app goes to background.
     * This ensures steering wheel buttons only control this app when it's in foreground.
     */
    fun setActive(active: Boolean) {
        val session = mediaSession ?: return
        if (active) {
            val result = requestAudioFocus()
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                session.isActive = true
                Log.d(TAG, "MediaSession activated with Audio Focus")
            } else {
                Log.w(TAG, "Audio Focus not granted, activating session anyway")
                session.isActive = true
            }
        } else {
            session.isActive = false
            abandonAudioFocus()
            Log.d(TAG, "MediaSession deactivated, Audio Focus released")
        }
    }
    
    /**
     * Request Audio Focus so the car system recognizes this app as the active media source.
     */
    private fun requestAudioFocus(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "Audio focus changed: $focusChange")
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> Log.d(TAG, "Audio focus changed: $focusChange") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }
    
    /**
     * Abandon Audio Focus to let other apps (like Spotify) take over media control.
     */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    /**
     * Update the playback state. Call this when TTS starts/stops.
     */
    fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    /**
     * Update the metadata for display (optional - shows on some car displays).
     */
    fun updateMetadata(title: String, sourceName: String? = null) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, sourceName ?: "RSS Reader")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Tin Tức")
            .build()

        mediaSession?.setMetadata(metadata)
    }

    /**
     * Release the MediaSession. Call this in Activity onDestroy.
     */
    fun release() {
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null
        Log.d(TAG, "MediaSession released")
    }

    /**
     * Get the MediaSession token for integration with other components.
     */
    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken
}
