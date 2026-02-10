# Changelog

## [Unreleased] - 2026-01-20

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

### Refactored
- **Article Content Fetcher:**
  - Decomposed `ArticleContentFetcher.kt` (1522 lines) into 19 smaller files using Facade + Strategy pattern.
  - Created `HtmlFetcher`, `RedirectResolver`, `TitleExtractor`, `HtmlSanitizer`.
  - Created `ContentExtractorRegistry` and `extractors/` package with 10+ site-specific implementations.
  - Reduced main class size by ~85% while maintaining public API compatibility.
  - Moved data models (`ArticleData`, `ContentCandidate`) to top-level files.

### Changed
- **HTML Analyzer:**
  - Updated `HtmlAnalyzerScreen.kt` to use new top-level `ContentCandidate` class.

### Improving
- **Testability:**
  - Refactoring enables easier unit testing of individual components.
