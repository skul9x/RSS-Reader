# Phase 03: Local Database Schema (Room + DataStore)
Status: ⬜ Pending  
Dependencies: Phase 02

## Objective
Set up local storage for read status using Room Database for structured data and DataStore for sync metadata.

## Requirements
### Functional
- [ ] Create Room entity for read news items
- [ ] Store: newsItemId, readTimestamp, deviceType, syncStatus
- [ ] Create DAO with insert, query, delete operations
- [ ] Store sync metadata (last sync time, pending count) in DataStore
- [ ] Auto-delete items older than 30 days

### Non-Functional
- [ ] Performance: Query read status should be < 50ms
- [ ] Data integrity: Use transactions for batch operations

## Implementation Steps
1. [ ] Add Room dependency (if not exists):
   ```kotlin
   implementation("androidx.room:room-runtime:2.6.1")
   implementation("androidx.room:room-ktx:2.6.1")
   ksp("androidx.room:room-compiler:2.6.1")
   ```

2. [ ] Create `ReadNewsEntity.kt`:
   ```kotlin
   @Entity(tableName = "read_news")
   data class ReadNewsEntity(
       @PrimaryKey val newsItemId: String,
       val readTimestamp: Long,
       val deviceType: String, // "smartphone" or "androidbox"
       val syncStatus: SyncStatus // PENDING, SYNCED, FAILED
   )
   
   enum class SyncStatus { PENDING, SYNCED, FAILED }
   ```

3. [ ] Create `ReadNewsDao.kt`:
   ```kotlin
   @Dao
   interface ReadNewsDao {
       @Insert(onConflict = OnConflictStrategy.REPLACE)
       suspend fun insert(item: ReadNewsEntity)
       
       @Insert(onConflict = OnConflictStrategy.REPLACE)
       suspend fun insertAll(items: List<ReadNewsEntity>)
       
       @Query("SELECT * FROM read_news WHERE syncStatus = :status")
       fun getPendingSync(status: SyncStatus): Flow<List<ReadNewsEntity>>
       
       @Query("SELECT * FROM read_news WHERE newsItemId = :id")
       suspend fun getById(id: String): ReadNewsEntity?
       
       @Query("SELECT EXISTS(SELECT 1 FROM read_news WHERE newsItemId = :id)")
       suspend fun isRead(id: String): Boolean
       
       @Query("DELETE FROM read_news WHERE readTimestamp < :timestamp")
       suspend fun deleteOlderThan(timestamp: Long)
       
       @Query("UPDATE read_news SET syncStatus = :newStatus WHERE syncStatus = :oldStatus")
       suspend fun updateSyncStatus(oldStatus: SyncStatus, newStatus: SyncStatus)
   }
   ```

4. [ ] Create `AppDatabase.kt`:
   ```kotlin
   @Database(entities = [ReadNewsEntity::class], version = 1)
   abstract class AppDatabase : RoomDatabase() {
       abstract fun readNewsDao(): ReadNewsDao
       
       companion object {
           @Volatile private var INSTANCE: AppDatabase? = null
           fun getDatabase(context: Context): AppDatabase { ... }
       }
   }
   ```

5. [ ] Create `SyncPreferences.kt` using DataStore:
   ```kotlin
   class SyncPreferences(context: Context) {
       private val dataStore = context.dataStore
       
       val lastSyncTime: Flow<Long>
       suspend fun updateLastSyncTime(timestamp: Long)
       
       val pendingSyncCount: Flow<Int>
       suspend fun updatePendingCount(count: Int)
   }
   ```

6. [ ] Create `LocalSyncRepository.kt`:
   ```kotlin
   class LocalSyncRepository(
       private val dao: ReadNewsDao,
       private val prefs: SyncPreferences
   ) {
       suspend fun markAsRead(newsId: String, deviceType: String)
       suspend fun isNewsRead(newsId: String): Boolean
       suspend fun getPendingItems(): List<ReadNewsEntity>
       suspend fun markAsSynced(newsIds: List<String>)
       suspend fun cleanupOldItems()
   }
   ```

7. [ ] Implement auto-cleanup (30 days):
   ```kotlin
   suspend fun cleanupOldItems() {
       val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
       dao.deleteOlderThan(thirtyDaysAgo)
   }
   ```

## Files to Create/Modify
- `app/src/main/java/[package]/data/local/ReadNewsEntity.kt` - NEW
- `app/src/main/java/[package]/data/local/ReadNewsDao.kt` - NEW
- `app/src/main/java/[package]/data/local/AppDatabase.kt` - NEW or MODIFY
- `app/src/main/java/[package]/data/local/SyncPreferences.kt` - NEW
- `app/src/main/java/[package]/data/repository/LocalSyncRepository.kt` - NEW
- `app/build.gradle.kts` - Add Room dependencies (MODIFY)

## Test Criteria
- [ ] Insert read news → Query returns it
- [ ] Mark as read → `isRead()` returns true
- [ ] 30-day cleanup removes old items
- [ ] Pending items query returns only PENDING status
- [ ] Batch insert 1000 items completes < 1 second

## Notes
- **Device Type Detection:**
  ```kotlin
  fun getDeviceType(context: Context): String {
      val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
      return if (uiMode == Configuration.UI_MODE_TYPE_CAR) "androidbox" else "smartphone"
  }
  ```

- **Migration Strategy:** If existing database schema conflicts, create migration or increment version.

---
Next Phase: [phase-04-firestore-sync.md](file:///C:/Users/Admin/Desktop/Test_code/RSS-Reader-main/plans/260120-2238-read-status-sync/phase-04-firestore-sync.md)
