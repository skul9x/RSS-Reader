package com.skul9x.rssreader.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.skul9x.rssreader.service.NewsReaderService
import com.skul9x.rssreader.utils.DebugLogger

/**
 * Receiver for hardware media buttons (Headset, Car Controls).
 * Forwards KeyEvents to NewsReaderService.
 * FIX: Now actually forwards events instead of just logging!
 */
class MediaButtonReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MediaButtonReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        DebugLogger.log(TAG, ">>> onReceive: action=$action")
        Log.d(TAG, "onReceive: $action")
        
        if (Intent.ACTION_MEDIA_BUTTON == action) {
            // FIX: Use Android 13+ compatible getParcelableExtra
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            
            if (keyEvent == null) {
                DebugLogger.log(TAG, ">>> KeyEvent is NULL!")
                return
            }
            
            val keyCode = keyEvent.keyCode
            val keyAction = keyEvent.action
            val keyName = KeyEvent.keyCodeToString(keyCode)
            
            DebugLogger.log(TAG, ">>> KeyEvent: code=$keyCode ($keyName), action=$keyAction (${if (keyAction == KeyEvent.ACTION_DOWN) "DOWN" else "UP"})")
            
            // Only process ACTION_DOWN to avoid double handling
            if (keyAction != KeyEvent.ACTION_DOWN) {
                DebugLogger.log(TAG, ">>> Ignoring non-DOWN action")
                return
            }
            
            // FIX: Forward media button events to NewsReaderService
            val serviceAction = when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    DebugLogger.log(TAG, ">>> NEXT button detected -> Forwarding to Service")
                    NewsReaderService.ACTION_NEXT
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    DebugLogger.log(TAG, ">>> PREVIOUS button detected -> Forwarding to Service")
                    NewsReaderService.ACTION_PREVIOUS
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    DebugLogger.log(TAG, ">>> PLAY button detected -> Forwarding to Service")
                    NewsReaderService.ACTION_PLAY
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    DebugLogger.log(TAG, ">>> STOP/PAUSE button detected -> Forwarding to Service")
                    NewsReaderService.ACTION_STOP
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    DebugLogger.log(TAG, ">>> PLAY_PAUSE button detected -> Forwarding to Service")
                    NewsReaderService.ACTION_PLAY // Using PLAY as toggle/resume for now
                }
                else -> {
                    DebugLogger.log(TAG, ">>> Unknown media key: $keyName - ignoring")
                    null
                }
            }
            
            if (serviceAction != null) {
                val serviceIntent = Intent(context, NewsReaderService::class.java).apply {
                    this.action = serviceAction
                }
                DebugLogger.log(TAG, ">>> Starting Service with action: $serviceAction")
                try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: IllegalStateException) {
                DebugLogger.log(TAG, ">>> Failed to start service: ${e.message}")
            }
            }
        }
    }
}
