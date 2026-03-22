package com.skul9x.rssreader.data.local

import android.content.Context
import android.content.SharedPreferences

enum class ScreenMode {
    AUTO,       // Tự động xoay theo cảm biến
    LANDSCAPE,  // Cố định ngang (Chế độ ô tô)
    PORTRAIT    // Cố định dọc (Chế độ điện thoại)
}

class AppPreferences private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SCREEN_MODE = "screen_mode"
        private const val KEY_AUDIO_STREAM_MODE = "audio_stream_mode"
        private const val KEY_AUDIO_MIX_MODE = "audio_mix_mode"
        
        @Volatile
        private var instance: AppPreferences? = null
        
        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    fun setScreenMode(mode: ScreenMode) {
        prefs.edit().putString(KEY_SCREEN_MODE, mode.name).apply()
    }

    fun getScreenMode(): ScreenMode {
        val modeName = prefs.getString(KEY_SCREEN_MODE, ScreenMode.LANDSCAPE.name)
        return try {
            ScreenMode.valueOf(modeName ?: ScreenMode.LANDSCAPE.name)
        } catch (e: Exception) {
            ScreenMode.LANDSCAPE
        }
    }

    // Audio Stream Mode settings
    fun setAudioStreamMode(mode: AudioStreamMode) {
        prefs.edit().putString(KEY_AUDIO_STREAM_MODE, mode.name).apply()
    }

    fun getAudioStreamMode(): AudioStreamMode {
        val modeName = prefs.getString(KEY_AUDIO_STREAM_MODE, AudioStreamMode.MEDIA.name)
        return try {
            AudioStreamMode.valueOf(modeName ?: AudioStreamMode.MEDIA.name)
        } catch (e: Exception) {
            AudioStreamMode.MEDIA
        }
    }

    // Audio Mix Mode settings (for audio focus behavior)
    fun setAudioMixMode(mode: AudioMixMode) {
        prefs.edit().putString(KEY_AUDIO_MIX_MODE, mode.name).apply()
    }

    fun getAudioMixMode(): AudioMixMode {
        val modeName = prefs.getString(KEY_AUDIO_MIX_MODE, AudioMixMode.PAUSE_MUSIC.name)
        return try {
            AudioMixMode.valueOf(modeName ?: AudioMixMode.PAUSE_MUSIC.name)
        } catch (e: Exception) {
            AudioMixMode.PAUSE_MUSIC
        }
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Returns a string representation of all current settings for debugging.
     */
    fun getAllSettingsInfo(): String {
        return """
            === Current App Settings ===
            Screen Mode: ${getScreenMode()}
            Audio Stream: ${getAudioStreamMode()}
            Audio Mix Mode: ${getAudioMixMode()}
            ============================
        """.trimIndent()
    }
}

enum class AudioStreamMode {
    MEDIA,          // Nhạc / Hệ thống
    ALARM,          // Báo thức
    NOTIFICATION,   // Thông báo
    RINGTONE,       // Nhạc chuông
    NAVIGATION      // Dẫn đường
}

enum class AudioMixMode {
    NO_MIX,         // Chiếm hoàn toàn (GAIN) - Dừng nhạc hoàn toàn
    PAUSE_MUSIC,    // Tạm dừng nhạc (GAIN_TRANSIENT) - Pause nhạc, play lại sau
    DUCK_MUSIC      // Giảm nhỏ nhạc (GAIN_TRANSIENT_MAY_DUCK)
}



