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


}
