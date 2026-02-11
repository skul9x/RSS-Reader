# Phase 04: Firestore Schema & Sync Logic
Status: ✅ Complete  
Dependencies: Phase 03

## Objective
Design Firestore schema and implement bi-directional sync logic with conflict resolution.

## Requirements
### Functional
- [ ] Define Firestore schema: `users/{userId}/readNews/{newsId}`
- [ ] Implement upload (local → Firestore)
- [ ] Implement download (Firestore → local)
- [ ] Conflict resolution: Prioritize earliest timestamp + smartphone deviceType
- [ ] Secure Firestore rules (user can only access their own data)
- [ ] Handle merge conflicts (same newsId, different timestamps)

### Non-Functional
- [ ] Performance: Batch operations should use Firestore batch writes
- [ ] Security: Users cannot read/write other users' data
- [ ] Reliability: Retry failed operations up to 3 times

## Implementation Steps

### 1. Firestore Schema Design
```
users/
  {userId}/          // FirebaseAuth UID
    readNews/        // Subcollection
      {newsId}/      // Document ID = RSS item ID
        - newsItemId: String
        - readTimestamp: Long
        - deviceType: String
        - updatedAt: ServerTimestamp
```

### 2. Firestore Security Rules
- [ ] Update Firestore rules in Firebase Console:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/readNews/{newsId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### 3. Create `FirestoreSyncRepository.kt`
```kotlin
class FirestoreSyncRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun getUserCollection() = 
        firestore.collection("users")
            .document(auth.currentUser?.uid ?: throw IllegalStateException("Not signed in"))
            .collection("readNews")
    
    suspend fun uploadBatch(items: List<ReadNewsEntity>)
    suspend fun downloadAll(): List<ReadNewsEntity>
    suspend fun deleteOldItems(olderThan: Long)
}
```

### 4. Implement Upload (Batch Write)
```kotlin
suspend fun uploadBatch(items: List<ReadNewsEntity>) = withContext(Dispatchers.IO) {
    val batch = firestore.batch()
    items.forEach { item ->
        val docRef = getUserCollection().document(item.newsItemId)
        batch.set(docRef, hashMapOf(
            "newsItemId" to item.newsItemId,
            "readTimestamp" to item.readTimestamp,
            "deviceType" to item.deviceType,
            "updatedAt" to FieldValue.serverTimestamp()
        ), SetOptions.merge())
    }
    batch.commit().await()
}
```

### 5. Implement Download
```kotlin
suspend fun downloadAll(): List<ReadNewsEntity> = withContext(Dispatchers.IO) {
    getUserCollection()
        .get()
        .await()
        .documents
        .map { doc ->
            ReadNewsEntity(
                newsItemId = doc.getString("newsItemId") ?: doc.id,
                readTimestamp = doc.getLong("readTimestamp") ?: 0L,
                deviceType = doc.getString("deviceType") ?: "unknown",
                syncStatus = SyncStatus.SYNCED
            )
        }
}
```

### 6. Implement Conflict Resolution
```kotlin
suspend fun mergeWithLocal(
    remoteItems: List<ReadNewsEntity>,
    localDao: ReadNewsDao
) {
    remoteItems.forEach { remote ->
        val local = localDao.getById(remote.newsItemId)
        
        if (local == null) {
            // New item from remote → Insert
            localDao.insert(remote.copy(syncStatus = SyncStatus.SYNCED))
        } else {
            // Conflict: Choose earliest timestamp
            // If timestamps equal, prioritize smartphone
            val shouldUpdate = when {
                remote.readTimestamp < local.readTimestamp -> true
                remote.readTimestamp == local.readTimestamp -> 
                    remote.deviceType == "smartphone" && local.deviceType != "smartphone"
                else -> false
            }
            
            if (shouldUpdate) {
                localDao.insert(remote.copy(syncStatus = SyncStatus.SYNCED))
            }
        }
    }
}
```

### 7. Create `SyncCoordinator.kt`
```kotlin
class SyncCoordinator(
    private val localRepo: LocalSyncRepository,
    private val firestoreRepo: FirestoreSyncRepository
) {
    suspend fun performFullSync() {
        // 1. Upload pending local items
        val pending = localRepo.getPendingItems()
        if (pending.isNotEmpty()) {
            firestoreRepo.uploadBatch(pending)
            localRepo.markAsSynced(pending.map { it.newsItemId })
        }
        
        // 2. Download remote items
        val remote = firestoreRepo.downloadAll()
        
        // 3. Merge with conflict resolution
        firestoreRepo.mergeWithLocal(remote, localRepo.dao)
        
        // 4. Cleanup old items (both local & remote)
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        localRepo.cleanupOldItems()
        firestoreRepo.deleteOldItems(thirtyDaysAgo)
    }
}
```

### 8. Error Handling & Retry
```kotlin
suspend fun performFullSyncWithRetry(maxRetries: Int = 3) {
    repeat(maxRetries) { attempt ->
        try {
            performFullSync()
            return // Success
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            delay(1000L * (attempt + 1)) // Exponential backoff
        }
    }
}
```

## Files to Create/Modify
- `app/src/main/java/[package]/data/remote/FirestoreSyncRepository.kt` - NEW
- `app/src/main/java/[package]/data/sync/SyncCoordinator.kt` - NEW
- Firebase Console → Firestore Rules - MODIFY

## Test Criteria
- [ ] Upload 10 items → Verify in Firestore Console
- [ ] Download from Firestore → Verify local DB updated
- [ ] Conflict resolution: Earlier timestamp wins
- [ ] Same timestamp: Smartphone deviceType wins
- [ ] Retry logic: Fails 2 times, succeeds on 3rd attempt
- [ ] Security: Cannot access other user's data (test with different accounts)

## Notes
- **Firestore Costs (Free Tier):**
  - 50K reads/day, 20K writes/day
  - User's usage: ~20 reads/day, ~2 writes/day → **100% FREE**

- **Offline Support:** Firestore SDK has built-in offline cache, but we use custom queue for better control.

---
Next Phase: [phase-05-batch-queue.md](file:///C:/Users/Admin/Desktop/Test_code/RSS-Reader-main/plans/260120-2238-read-status-sync/phase-05-batch-queue.md)
