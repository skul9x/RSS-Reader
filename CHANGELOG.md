# Changelog

## [Unreleased] - 2026-02-11

### Audited
- **Firebase Sync Flow:**
  - Performed comprehensive audit of Sync logic. Verified 10 reported logic errors.
  - Confirmed 8 bugs, including two **High Severity** issues:
    - **BUG 3:** Indefinite 5-minute polling loop caused by overlapping periodic and one-time tasks.
    - **BUG 4:** Potential race conditions in `triggerImmediateSync` due to non-unique work enqueuing.
  - Identified **Medium Severity** architectural limitation in timestamp-based sync (BUG 6) and singleton persistence across sessions (BUG 8).
  - Verified 1 low-priority cosmetic issue (Unused import).
  - Documentation of audit results saved to `.brain/audit_verification.md`.

### Fixed
- **Firebase Sync Subsystem (High & Medium Severity):**
  - **BUG 3: Fixed Polling Loop (High)**: Removed redundant `scheduleNextSync()` self-chaining in `SyncWorker`, relying solely on the 15-minute periodic schedule.
  - **BUG 4: Unique Work Enforcement (High)**: Modified `triggerImmediateSync` to use `enqueueUniqueWork` with `REPLACE` policy, preventing concurrent sync workers and data races.
  - **BUG 8: Global Sign-out Cleanup (High)**: Implemented `onUserSignOut` orchestration to clear local DB, reset sync timestamps, cancel background tasks, and nullify singleton instances (`SyncCoordinator`, `LocalSyncRepository`, etc.) when a user logs out.
  - **BUG 2: Batch DB Writes (Medium)**: Added `@Transaction` batch upsert method to `ReadNewsDao`, reducing first-sync I/O from N transactions to 1.
  - **BUG 5: Reliable Background Flush (Medium)**: Upgraded `AppLifecycleObserver` to use `applicationScope` instead of an ephemeral coroutine scope, ensuring data is uploaded even if the process is killed shortly after going to background.
  - **BUG 6: Cursor Precision Edge-case (Medium)**: Switched from `>` to `>=` in Firestore query with 1ms buffer to prevent skipping items updated in the same millisecond.
  - **BUG 10: Recovery Safety (Medium)**: Removed misleading `performFullSyncWithRetry` no-op and switched to direct calls, delegating retry responsibility to WorkManager's exponential backoff.
- **Summary Reading Flow:**
  - **Corrected Rotation Logic (H1):** Fixed `suggestWithRetry` to use MODEL-FIRST rotation (consistent with summarization), preventing missed model/key combinations.
  - **Thread-Safe API Key Access (H2):** Secured `apiKeys` access in translation functions with `stateMutex.withLock` to prevent concurrent modification race conditions.
  - **Sentence-Level Resume tracking (H3):** Switched "Read All" mode to use sentence-based TTS tracking, enabling mid-article resume support after interruptions.
  - **Robust JSON Extraction (M1):** Replaced brittle string manipulation with Regex-based extraction and markdown code block stripping for reliable batch translation parsing.
  - **Enhanced API Key Security (M3):** Implemented SHA-256 hashing for API keys in `ModelQuotaManager` to prevent plaintext key storage in device settings.
  - **Battery-Smart WakeLock (M4):** Replaced hard 30-minute WakeLock with a dynamic monitor that releases automatically when reading completes.
  - **Android 8+ Compat (M5):** Fixed "Reading from shared link" crashes by using `startForegroundService` for background service initialization.
  - Verified with a successful Gradle build (`assembleDebug`).

## [Unreleased] - 2026-02-10

### Added
- **Firebase Integration:**
  - Added Firebase BoM, Auth, Firestore dependencies
  - Added WorkManager and Coil dependencies
  - Added Google Services plugin
- **Authentication:**
  - Implemented Google Sign-In flow (`AuthManager`)
  - Added `AuthViewModel` for UI state
  - Added Google Sign-In button in Settings
- **Local Database (Sync):**
  - Updated `ReadNewsItem` entity with `deviceType` and `syncStatus`
  - Added sync queries to `ReadNewsDao`
  - Created `LocalSyncRepository`
  - Created `SyncPreferences` with DataStore
  - Migrated Database to version 7

### Changed
- **Settings Screen:**
  - Added Account Section with Avatar and Sign-out support
  - Integrated AuthViewModel
  
### Fixed
- **Build System:**
  - Resolved `AnimatedVisibility` implicit receiver error in `MainScreenPortrait.kt` by using fully qualified name.

### Documentation
- Created `firebase-setup-guide.md`
- Created `read-status-sync_spec.md`
- Updated Architecture and Schema docs

## [Unreleased] - 2026-02-10

### Added
- **Gemini API Logging:**
  - Implemented START, SUCCESS, ERROR, and FALLBACK event logging for Gemini API.
  - Removed generic "Auto-detect" placeholders; logs now show specific model names and API key indices.
  - Added "Gemini" filter chip to Activity Logs screen.
  - Added custom `SyncProblem` icon for fallback events.

### Fixed
- **Gemini API Reliability:**
  - Resolved **Mixed Synchronization (Mutex vs @Synchronized)** bug in `GeminiApiClient` by unifying on `Mutex`, preventing potential race conditions and deadlocks.
  - Fixed **Double-Prompting** bug in translation logic where batch prompts were being wrapped twice, causing instruction conflicts and wasting tokens.
  - Removed obsolete synchronous methods (`refreshApiKeysSync`, `resetSync`) to ensure thread safety.
  - Finalized import updates across `NewsReaderService`, `HtmlAnalyzerScreen`, and `SharedLinkViewModel` after modularization.
  - Verified stability with a successful debug build.

### Refactored
- **Article Content Fetcher:**
  - Decomposed `ArticleContentFetcher.kt` (1522 lines) into 19 smaller files using Facade + Strategy pattern.
  - Created `HtmlFetcher`, `RedirectResolver`, `TitleExtractor`, `HtmlSanitizer`.
  - Created `ContentExtractorRegistry` and `extractors/` package with 10+ site-specific implementations.
  - Reduced main class size by ~85% while maintaining public API compatibility.
  - Moved data models (`ArticleData`, `ContentCandidate`) to top-level files.
- **Gemini API Client:**
  - Decomposed `GeminiApiClient.kt` (~1100 lines) into a modular `gemini/` package.
  - Created `GeminiModels.kt` for result types.
  - Created `GeminiPrompts.kt` for prompt construction logic.
  - Created `GeminiResponseHelper.kt` for JSON processing and text cleaning.
  - Reduced main class size and improved separation of concerns.
  - Unified all state access under a single `stateMutex`.
  - *Note: Verification and import updates in consumer classes pending.*

### Changed
- **HTML Analyzer:**
  - Updated `HtmlAnalyzerScreen.kt` to use new top-level `ContentCandidate` class.

### Improving
- **Testability:**
  - Refactoring enables easier unit testing of individual components.
- **Log Clarity:**
  - Enhanced traceability by logging specific model names and key indices.

## [Unreleased] - 2026-02-10 (Legacy)
