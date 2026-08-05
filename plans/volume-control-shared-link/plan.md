# Plan: Shared Link Screen Volume Control Button

Created: 2026-08-05
Status: 🟢 Completed

## Overview
Add a volume control button at the top-right corner of the "Link được chia sẻ" (Shared Link) screen (`SharedLinkScreen`). When clicked, the button cycles device media volume through 80% -> 85% -> 90% -> 80% (loop). When the screen opens, device volume remains unmodified until the user explicitly clicks the button. Initially, the button is rendered in a dimmed/muted state.

## Tech Stack & Architecture
- **Language:** Kotlin 1.9+
- **UI Framework:** Jetpack Compose (Material Design 3 TopAppBar actions)
- **Audio Service:** Android `AudioManager` (STREAM_MUSIC)
- **State Management:** `SharedLinkViewModel` & `SharedLinkUiState` via Kotlin Coroutines `StateFlow`
- **Testing:** JUnit 4, Mockito / Kotlin mocks for Android `AudioManager` and `SharedLinkViewModel` unit testing
- **Testing:** JUnit 4, Mockito / Kotlin mocks for Android `AudioManager` and `SharedLinkViewModel` unit testing

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Volume Controller & Step Cycle Logic | ✅ Completed | 100% |
| 02 | ViewModel State & Volume Toggle Handler | ✅ Completed | 100% |
| 03 | SharedLinkScreen UI TopBar Button | ✅ Completed | 100% |

## Verification & Testing Strategy
- File-based unit tests for `VolumeController` in `app/src/test/java/com/skul9x/rssreader/utils/VolumeControllerTest.kt`.
- File-based unit tests for `SharedLinkViewModel` state & cycling in `app/src/test/java/com/skul9x/rssreader/ui/sharedlink/SharedLinkViewModelTest.kt`.
- File-based tests for UI state modeling in `app/src/test/java/com/skul9x/rssreader/ui/sharedlink/SharedLinkScreenStateTest.kt`.
- Command execution: `./gradlew test`
