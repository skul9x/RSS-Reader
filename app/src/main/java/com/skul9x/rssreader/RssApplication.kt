package com.skul9x.rssreader

import android.app.Application
import com.skul9x.rssreader.util.GlobalCrashHandler

class RssApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Global Crash Handler
        Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(this))
    }
}
