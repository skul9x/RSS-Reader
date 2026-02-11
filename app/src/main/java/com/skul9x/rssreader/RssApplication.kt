package com.skul9x.rssreader

import android.app.Application
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(batchQueueManager)
        )
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
