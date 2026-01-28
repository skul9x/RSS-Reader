# Phase 08: Testing & Cleanup
Status: ✅ Complete  
Dependencies: Phase 07

## Objective
Comprehensive testing across both devices and production cleanup.

## Requirements
### Functional
- [ ] End-to-end test: Read on smartphone → Verify on android box
- [ ] Test all edge cases (crash, offline, conflict)
- [ ] Cleanup test data from Firestore
- [ ] Remove debug logs

### Non-Functional
- [ ] Performance: Sync < 2 seconds
- [ ] Battery: < 5% drain per day

## Implementation Steps

### 1. Device Testing Checklist
- [ ] **Scenario 1:** Read 10 items on smartphone → Check android box filters them
- [ ] **Scenario 2:** Read 5 items → Wait 5 minutes → Verify sync triggered
- [ ] **Scenario 3:** Read 3 items → Press Home → Verify immediate sync
- [ ] **Scenario 4:** Airplane mode → Read 10 items → Enable WiFi → Verify sync
- [ ] **Scenario 5:** Both devices read same item at same time → Verify smartphone wins

### 2. Crash Recovery Test
```kotlin
// Simulate crash
@Test
fun testCrashRecovery() {
    // Add 7 items to queue
    repeat(7) { batchQueue.addToQueue("news_$it", "smartphone") }
    
    // Simulate crash (kill process)
    Process.killProcess(Process.myPid())
    
    // On restart, verify 7 items sync
}
```

### 3. Performance Test
```kotlin
@Test
fun testLargeSync() = runBlocking {
    val items = (1..1000).map { 
        ReadNewsEntity("news_$it", System.currentTimeMillis(), "smartphone", PENDING) 
    }
    
    val startTime = System.currentTimeMillis()
    firestoreRepo.uploadBatch(items)
    val duration = System.currentTimeMillis() - startTime
    
    assertTrue(duration < 5000) // Should complete in < 5 seconds
}
```

### 4. Cleanup Tasks
- [ ] Remove all `Log.d()` statements (keep only `Log.e()` for errors)
- [ ] Remove test Firestore data
- [ ] Update Firestore security rules to production mode
- [ ] Test on low-end Android device (Android 8.0)
- [ ] Check APK size increase (should be < 5MB)

### 5. Battery Test
- [ ] Install on test device
- [ ] Use app normally for 24 hours
- [ ] Check battery stats: `Settings > Battery > App usage`
- [ ] Verify WorkManager not causing excessive wakelocks

### 6. Final Verification
- [ ] Sign in on smartphone → Read 15 items
- [ ] Open android box → Verify 15 items filtered
- [ ] Sign out on smartphone → Verify sync stops
- [ ] Sign in again → Verify data restored

## Files to Create/Modify
- All files - MODIFY (remove debug logs)
- Firebase Console - MODIFY (security rules)

## Test Criteria
- [ ] All scenarios pass without crashes
- [ ] Sync completes in < 2 seconds for 10 items
- [ ] Battery drain < 5% per day
- [ ] No memory leaks (use LeakCanary)
- [ ] Works on Android 8.0 - 14.0

## Notes
**Firestore Production Rules:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/readNews/{newsId} {
      allow read, write: if request.auth != null 
                         && request.auth.uid == userId
                         && request.time < timestamp.date(2027, 1, 20);
    }
  }
}
```

**LeakCanary Setup:**
```kotlin
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
```

---
**🎉 ALL PHASES COMPLETE!**

Return to: [plan.md](file:///C:/Users/Admin/Desktop/Test_code/RSS-Reader-main/plans/260120-2238-read-status-sync/plan.md)
