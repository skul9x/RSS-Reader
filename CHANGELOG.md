# Changelog

## [Unreleased] - 2026-02-11

### Fixed
- **Firebase Sync Cloud-Devices:**
  - **Atomic Sync Ordering (M1):** Reordered `markAsSynced` to execute immediately after successful upload, ensuring local and remote states are consistent even if later download steps fail.
  - **Protected Sync Flow (M2):** Wrapped merge and download-timestamp updates in an atomic try-catch block to prevent skipping items if local merging fails.
  - **Thread-Safe Batch Queue (M3):** Redesigned `BatchQueueManager` to drain the in-memory queue atomically under lock, resolving race conditions between adding and flushing items.
  - **Anti-Misuse Guards (M4):** Added `@Deprecated` warnings and enhanced documentation to `ReadNewsDao` methods to enforce usage of centralized conflict resolution logic in `SyncCoordinator`.
  - **Extended Test Suite:** Added unit tests verifying sync operation ordering and failure recovery.
  - Resolved **Conflict Resolution Mâu Thuẫn**: Inverted logic in `SyncCoordinator.mergeWithLocal()` to prioritize "Earliest Wins" (original read time) instead of "Newest Wins", ensuring data consistency across devices.
  - Resolved **Firestore Batch Limit Crash**: Implemented chunking (400 items per batch) in `FirestoreSyncRepository.uploadBatch()` to stay safely under Firestore's 500-operation limit.
  - Fixed **Silent Download Failures**: Modified `FirestoreSyncRepository.downloadSince()` to rethrow exceptions instead of returning an empty list on failure, allowing `SyncCoordinator` to trigger retry logic.
  - Added **Authentication Guard**: Implemented `isUserSignedIn()` check in `SyncCoordinator` to skip sync operations when the user is not logged in, preventing unnecessary `IllegalStateException` crashes.
  - Validated all fixes with new unit tests in `SyncCoordinatorTest`.

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
