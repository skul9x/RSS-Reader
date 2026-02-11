package com.skul9x.rssreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.SyncPreferences
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.FirebaseLogRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import com.skul9x.rssreader.data.sync.SyncCoordinator
import com.skul9x.rssreader.data.sync.SyncScheduler

/**
 * WorkManager Worker for background sync of read status.
 * Triggered periodically or when network becomes available.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "ReadStatusSync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync work (attempt: $runAttemptCount)")
        
        return try {
            val dao = AppDatabase.getDatabase(applicationContext).readNewsDao()
            val firebaseLogDao = AppDatabase.getDatabase(applicationContext).firebaseLogDao()
            val prefs = SyncPreferences.getInstance(applicationContext)
            val localRepo = LocalSyncRepository.getInstance(dao, prefs, applicationContext)
            val logRepo = FirebaseLogRepository.getInstance(firebaseLogDao)
            val firestoreRepo = FirestoreSyncRepository.getInstance(logRepo)
            val syncCoordinator = SyncCoordinator.getInstance(localRepo, firestoreRepo, logRepo)
            
            syncCoordinator.performFullSyncWithRetry()
            Log.d(TAG, "Sync completed successfully")

            // Chain next sync in 5 minutes
            SyncScheduler(applicationContext).scheduleNextSync()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            if (runAttemptCount < 3) {
                Log.d(TAG, "Will retry sync")
                Result.retry()
            } else {
                Log.e(TAG, "Max retries reached, giving up")
                // Still schedule next sync cycle even if this specific one failed max retries
                SyncScheduler(applicationContext).scheduleNextSync()
                Result.failure()
            }
        }
    }
}
