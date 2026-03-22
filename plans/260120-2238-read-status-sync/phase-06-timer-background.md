# Phase 06: Timer & Background Sync (WorkManager)
Status: ✅ Complete  
Dependencies: Phase 05

## Objective
Implement 5-minute timer using WorkManager and background sync trigger when app goes to background.

## Requirements
### Functional
- [ ] Schedule WorkManager periodic task every 5 minutes (when app is active)
- [ ] Flush queue when app goes to background (Lifecycle observer)
- [ ] Cancel timer when app is destroyed
- [ ] Persist WorkManager state across app restarts
- [ ] Handle battery optimization (Doze mode)

### Non-Functional
- [ ] Performance: WorkManager should not drain battery
- [ ] Reliability: Timer should survive process death
- [ ] Accuracy: 5-minute interval with ±1 minute tolerance (Android limitation)

## Implementation Steps

### 1. Create `SyncWorker.kt`
```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val batchQueue = (applicationContext as NewsReaderApplication).batchQueueManager
            batchQueue.forceFlush()
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
```

### 2. Create `SyncScheduler.kt`
```kotlin
class SyncScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 5, 
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexTimeInterval = 1, // Flex window: 4-5 minutes
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "SyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork("SyncWork")
    }
}
```

### 3. Implement Background Trigger (Lifecycle Observer)
```kotlin
class AppLifecycleObserver(
    private val batchQueueManager: BatchQueueManager
) : DefaultLifecycleObserver {
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // App going to background → Flush queue
        CoroutineScope(Dispatchers.IO).launch {
            batchQueueManager.forceFlush()
        }
    }
}
```

### 4. Register Lifecycle Observer in `MainActivity.kt`
```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var lifecycleObserver: AppLifecycleObserver
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as NewsReaderApplication
        lifecycleObserver = AppLifecycleObserver(app.batchQueueManager)
        lifecycle.addObserver(lifecycleObserver)
        
        // Start periodic sync
        app.syncScheduler.schedulePeriodicSync()
    }
}
```

### 5. Handle App Startup (in `NewsReaderApplication.kt`)
```kotlin
class NewsReaderApplication : Application() {
    lateinit var batchQueueManager: BatchQueueManager
    lateinit var syncScheduler: SyncScheduler
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize dependencies
        val localRepo = LocalSyncRepository(...)
        val firestoreRepo = FirestoreSyncRepository(...)
        val syncCoordinator = SyncCoordinator(localRepo, firestoreRepo)
        
        batchQueueManager = BatchQueueManager(
            localRepo, 
            syncCoordinator,
            CoroutineScope(Dispatchers.Default)
        )
        
        syncScheduler = SyncScheduler(this)
        
        // Recover pending items from previous session
        CoroutineScope(Dispatchers.IO).launch {
            batchQueueManager.recoverPendingItems()
        }
        
        // Schedule periodic sync
        syncScheduler.schedulePeriodicSync()
    }
}
```

### 6. Handle Network Changes (Optional Enhancement)
```kotlin
// Add to SyncWorker constraints
.setRequiredNetworkType(NetworkType.CONNECTED)

// WorkManager will auto-retry when network is available
```

### 7. Testing Timer Behavior
```kotlin
// For testing, use shorter interval
@VisibleForTesting
fun scheduleTestSync() {
    val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
        repeatInterval = 15, // 15 minutes (minimum allowed)
        repeatIntervalTimeUnit = TimeUnit.MINUTES
    ).build()
    
    workManager.enqueue(syncRequest)
}
```

## Files to Create/Modify
- `app/src/main/java/[package]/worker/SyncWorker.kt` - NEW
- `app/src/main/java/[package]/data/sync/SyncScheduler.kt` - NEW
- `app/src/main/java/[package]/lifecycle/AppLifecycleObserver.kt` - NEW
- `app/src/main/java/[package]/NewsReaderApplication.kt` - MODIFY
- `app/src/main/java/[package]/ui/MainActivity.kt` - MODIFY

## Test Criteria
- [ ] Open app → WorkManager scheduled (check via `adb shell dumpsys jobscheduler`)
- [ ] Wait 5 minutes (or use `WorkManager.testDriver` to trigger) → Sync happens
- [ ] Press Home button (app to background) → Sync triggered immediately
- [ ] Airplane mode → WorkManager waits for network, then syncs
- [ ] Force-kill app → On restart, WorkManager reschedules

## Notes
- **5-minute limitation:**
  - Android 8.0+ restricts background execution
  - Minimum PeriodicWorkRequest interval: 15 minutes
  - **Solution:** Use `setInitialDelay()` + one-time work for 5-minute intervals
  
  ```kotlin
  fun scheduleNextSync() {
      val oneTimeWork = OneTimeWorkRequestBuilder<SyncWorker>()
          .setInitialDelay(5, TimeUnit.MINUTES)
          .build()
      
      workManager.enqueue(oneTimeWork)
  }
  
  // In SyncWorker.doWork():
  override suspend fun doWork(): Result {
      batchQueue.forceFlush()
      scheduleNextSync() // Chain next execution
      return Result.success()
  }
  ```

- **Doze Mode:**
  - WorkManager automatically handles Doze mode
  - Tasks will run during maintenance windows
  - For critical sync, use `setExpedited()` (requires `SCHEDULE_EXACT_ALARM` permission)

---
Next Phase: [phase-07-ui-integration.md](file:///C:/Users/Admin/Desktop/Test_code/RSS-Reader-main/plans/260120-2238-read-status-sync/phase-07-ui-integration.md)
