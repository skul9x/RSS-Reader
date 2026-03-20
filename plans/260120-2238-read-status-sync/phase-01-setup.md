# Phase 01: Setup Firebase & Dependencies
Status: ⬜ Pending  
Dependencies: None

## Objective
Configure Firebase project and add all required dependencies for Authentication, Firestore, and WorkManager.

## Requirements
### Functional
- [ ] Create Firebase project in Firebase Console
- [ ] Add Android app to Firebase project (for both smartphone & android box)
- [ ] Download and integrate `google-services.json`
- [ ] Add Firebase Authentication dependency
- [ ] Add Firestore dependency
- [ ] Add WorkManager dependency
- [ ] Configure ProGuard rules (if using R8/ProGuard)

### Non-Functional
- [ ] Performance: Dependencies should not increase APK size > 5MB
- [ ] Security: Enable App Check (optional but recommended)

## Implementation Steps
1. [ ] Go to [Firebase Console](https://console.firebase.google.com/)
2. [ ] Create new project: "RSS-Reader-Sync" (or use existing)
3. [ ] Enable Google Sign-In in Authentication > Sign-in methods
4. [ ] Enable Firestore Database (start in test mode, security rules in Phase 04)
5. [ ] Add Android app:
   - Package name: (check from AndroidManifest.xml)
   - Download `google-services.json` → Place in `app/` folder
6. [ ] Add dependencies to `app/build.gradle.kts`:
   ```kotlin
   // Firebase BoM
   implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
   implementation("com.google.firebase:firebase-auth-ktx")
   implementation("com.google.firebase:firebase-firestore-ktx")
   
   // Google Sign-In
   implementation("com.google.android.gms:play-services-auth:20.7.0")
   
   // WorkManager
   implementation("androidx.work:work-runtime-ktx:2.9.0")
   
   // Coroutines (if not already added)
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
   ```
7. [ ] Add Google Services plugin to `app/build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.gms.google-services")
   }
   ```
8. [ ] Add plugin to project-level `build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.gms.google-services") version "4.4.0" apply false
   }
   ```
9. [ ] Sync Gradle
10. [ ] Verify build success

## Files to Create/Modify
- `app/google-services.json` - Firebase configuration (NEW)
- `app/build.gradle.kts` - Add dependencies (MODIFY)
- `build.gradle.kts` (project-level) - Add Google Services plugin (MODIFY)

## Test Criteria
- [ ] App builds successfully after Gradle sync
- [ ] No dependency conflicts
- [ ] Firebase SDK initialized (check Logcat for "FirebaseApp initialization" message)

## Notes
- **SHA-1 Certificate:** Required for Google Sign-In. Get via:
  ```bash
  ./gradlew signingReport
  ```
  Add to Firebase Console > Project Settings > Your apps > SHA certificate fingerprints

- **Test Mode Firestore:** Start with test mode rules:
  ```
  allow read, write: if request.time < timestamp.date(2026, 2, 20);
  ```
  Will secure in Phase 04.

---
Next Phase: [phase-02-google-signin.md](file:///C:/Users/Admin/Desktop/Test_code/RSS-Reader-main/plans/260120-2238-read-status-sync/phase-02-google-signin.md)
