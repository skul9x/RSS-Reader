package com.skul9x.rssreader.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.skul9x.rssreader.worker.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * Scheduler for periodic and one-time sync operations.
 * Uses WorkManager for reliable background execution.
 */
class SyncScheduler(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)
    
    companion object {
        private const val TAG = "SyncScheduler"
        private const val PERIODIC_WORK_NAME = "PeriodicReadStatusSync"
        private const val ONE_TIME_WORK_NAME = "OneTimeReadStatusSync"
        
        // Android minimum for periodic work is 15 minutes
        // We use one-time work chaining for ~5 minute intervals
        private const val SYNC_INTERVAL_MINUTES = 5L
    }

    /**
     * Schedules a one-time sync after 5 minutes.
     * Chains to next sync on completion.
     */
    fun scheduleNextSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SyncWorker.WORK_NAME)
            .build()
        
        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        
        Log.d(TAG, "Scheduled next sync in $SYNC_INTERVAL_MINUTES minutes")
    }

    /**
     * Schedules a periodic sync every 15 minutes (Android minimum).
     * Acts as a fallback if one-time chaining breaks.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexTimeInterval = 5,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SyncWorker.WORK_NAME)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        
        Log.d(TAG, "Scheduled periodic sync every 15 minutes")
    }

    /**
     * Triggers an immediate sync.
     */
    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(SyncWorker.WORK_NAME)
            .build()
        
        workManager.enqueue(syncRequest)
        Log.d(TAG, "Triggered immediate sync")
    }

    /**
     * Cancels all scheduled sync work.
     */
    fun cancelAllSyncWork() {
        workManager.cancelAllWorkByTag(SyncWorker.WORK_NAME)
        Log.d(TAG, "Cancelled all sync work")
    }
}
