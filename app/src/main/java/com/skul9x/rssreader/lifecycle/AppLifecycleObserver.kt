package com.skul9x.rssreader.lifecycle

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.skul9x.rssreader.data.sync.BatchQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Observes app lifecycle to trigger sync when app goes to background.
 * Ensures pending read statuses are synced before app is potentially killed.
 */
class AppLifecycleObserver(
    private val batchQueueManager: BatchQueueManager
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "AppLifecycleObserver"
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // App going to background → Flush queue
        Log.d(TAG, "App going to background, flushing queue")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                batchQueueManager.forceFlush()
                Log.d(TAG, "Queue flushed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush queue", e)
            }
        }
    }
}
