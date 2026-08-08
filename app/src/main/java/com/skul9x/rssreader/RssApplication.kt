package com.skul9x.rssreader

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.SyncPreferences
import com.skul9x.rssreader.data.remote.FirestoreSyncRepository
import com.skul9x.rssreader.data.repository.LocalSyncRepository
import com.skul9x.rssreader.data.sync.BatchQueueManager
import com.skul9x.rssreader.data.sync.SyncCoordinator
import com.skul9x.rssreader.data.sync.SyncScheduler
import com.skul9x.rssreader.lifecycle.AppLifecycleObserver
import com.skul9x.rssreader.util.GlobalCrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RssApplication : Application() {
    
    companion object {
        // Application-scoped CoroutineScope for background work
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        // Sync components - lazy initialized
        private var _localSyncRepository: LocalSyncRepository? = null
        private var _batchQueueManager: BatchQueueManager? = null
        private var _firebaseLogRepository: com.skul9x.rssreader.data.repository.FirebaseLogRepository? = null
        private var _lifecycleObserver: AppLifecycleObserver? = null
        
        /**
         * Get LocalSyncRepository singleton.
         * Should be called after Application.onCreate().
         */
        fun getLocalSyncRepository(): LocalSyncRepository? = _localSyncRepository
        
        /**
         * Get BatchQueueManager singleton.
         * Should be called after Application.onCreate().
         */
        fun getBatchQueueManager(): BatchQueueManager? = _batchQueueManager

        fun getFirebaseLogRepository(): com.skul9x.rssreader.data.repository.FirebaseLogRepository? = _firebaseLogRepository

        /**
         * Re-initializes sync components after a user signs in.
         * Must be called after FirebaseAuth state changes to signed-in.
         * Thread-safe: synchronized on the companion object.
         */
        fun onUserSignIn(context: Context) {
            synchronized(this) {
                // Skip if already initialized (e.g., first launch, no sign-out happened)
                if (_localSyncRepository != null && _batchQueueManager != null) {
                    return
                }

                val appContext = context.applicationContext
                val database = AppDatabase.getDatabase(appContext)
                val prefs = SyncPreferences.getInstance(appContext)

                // 1. Re-create LocalSyncRepository
                val localRepo = LocalSyncRepository.getInstance(
                    database.readNewsDao(), prefs, appContext
                )
                _localSyncRepository = localRepo

                // 2. Re-create FirebaseLogRepository
                val firebaseLogRepo = com.skul9x.rssreader.data.repository.FirebaseLogRepository
                    .getInstance(database.firebaseLogDao())
                _firebaseLogRepository = firebaseLogRepo

                // 3. Re-create FirestoreSyncRepository
                val firestoreRepo = FirestoreSyncRepository.getInstance(firebaseLogRepo)

                // 4. Re-create SyncCoordinator
                val syncCoordinator = SyncCoordinator.getInstance(
                    localRepo, firestoreRepo, firebaseLogRepo
                )

                // 5. Re-create BatchQueueManager
                val batchQueueManager = BatchQueueManager.getInstance(
                    localRepo, firestoreRepo, syncCoordinator, applicationScope
                )
                _batchQueueManager = batchQueueManager

                // 6. Wire BatchQueueManager back to LocalSyncRepository
                localRepo.setBatchQueueManager(batchQueueManager)

                // 7. Re-register lifecycle observer with new BatchQueueManager
                val observer = AppLifecycleObserver(batchQueueManager, applicationScope)
                _lifecycleObserver?.let {
                    try {
                        ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
                    } catch (e: Exception) {
                        // Ignore if lifecycle unavailable
                    }
                }
                try {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                } catch (e: Exception) {
                    // Ignore if lifecycle unavailable
                }
                _lifecycleObserver = observer
            }

            // 8. Schedule periodic sync (idempotent - KEEP policy)
            SyncScheduler(context.applicationContext).schedulePeriodicSync()

            // 9. Trigger immediate sync to download new user's read history
            SyncScheduler(context.applicationContext).triggerImmediateSync()
        }

        /**
         * Clears all sync-related state when a user signs out.
         * Ensures the next user starts with a clean slate.
         */
        suspend fun onUserSignOut(context: Context) {
            // Remove lifecycle observer
            _lifecycleObserver?.let {
                try {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
                } catch (e: Exception) {
                    // Ignore if lifecycle unavailable
                }
            }
            _lifecycleObserver = null

            // 1. Cancel scheduled sync work
            SyncScheduler(context).cancelAllSyncWork()
            
            // 2. Clear in-memory queue
            _batchQueueManager?.clearQueue()
            
            // 3. Clear local DB read history
            AppDatabase.getDatabase(context).readNewsDao().clearAll()
            
            // 4. Reset sync preferences (timestamps, etc.)
            SyncPreferences.getInstance(context).clearSyncData()
            
            // 5. Reset singleton instances to force re-initialization on next use
            SyncCoordinator.resetInstance()
            FirestoreSyncRepository.resetInstance()
            BatchQueueManager.resetInstance()
            LocalSyncRepository.resetInstance()
            
            _localSyncRepository = null
            _batchQueueManager = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Global Crash Handler
        Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(this))
        
        // Initialize Sync Components
        initializeSyncComponents()
        
        // Schedule periodic background sync
        scheduleSyncWork()
        
        // Recover any pending items from previous sessions
        recoverPendingItems()
    }
    
    /**
     * Initialize all sync-related components with proper dependency injection.
     * Handles circular dependency between LocalSyncRepository and BatchQueueManager.
     */
    private fun initializeSyncComponents() {
        val database = AppDatabase.getDatabase(this)
        val prefs = SyncPreferences.getInstance(this)
        
        // 1. Create LocalSyncRepository first (without BatchQueueManager)
        val localRepo = LocalSyncRepository.getInstance(
            database.readNewsDao(),
            prefs,
            this
        )
        _localSyncRepository = localRepo

        // 1b. Create FirebaseLogRepository
        val firebaseLogRepo = com.skul9x.rssreader.data.repository.FirebaseLogRepository.getInstance(database.firebaseLogDao())
        _firebaseLogRepository = firebaseLogRepo
        
        // 2. Create FirestoreSyncRepository
        val firestoreRepo = FirestoreSyncRepository.getInstance(firebaseLogRepo)
        
        // 3. Create SyncCoordinator
        val syncCoordinator = SyncCoordinator.getInstance(localRepo, firestoreRepo, firebaseLogRepo)
        
        // 4. Create BatchQueueManager with all dependencies
        val batchQueueManager = BatchQueueManager.getInstance(
            localRepo,
            firestoreRepo,
            syncCoordinator,
            applicationScope
        )
        _batchQueueManager = batchQueueManager
        
        // 5. Wire BatchQueueManager back to LocalSyncRepository (resolve circular dependency)
        localRepo.setBatchQueueManager(batchQueueManager)
        
        // 6. Add lifecycle observer to flush queue when app goes to background
        val observer = AppLifecycleObserver(batchQueueManager, applicationScope)
        _lifecycleObserver?.let {
            try {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
            } catch (e: Exception) {
                // Ignore if lifecycle unavailable
            }
        }
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        } catch (e: Exception) {
            // Ignore if lifecycle unavailable
        }
        _lifecycleObserver = observer
    }
    
    /**
     * Schedule periodic sync work using WorkManager.
     */
    private fun scheduleSyncWork() {
        val scheduler = SyncScheduler(this)
        scheduler.schedulePeriodicSync()
    }
    
    /**
     * Recover any pending items from previous sessions (crash recovery).
     */
    private fun recoverPendingItems() {
        applicationScope.launch {
            _batchQueueManager?.recoverPendingItems()
        }
    }
}
