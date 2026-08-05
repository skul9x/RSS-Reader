# Phase 03: SharedLinkScreen UI TopBar Button

Status: 100% Completed
Dependencies: Phase 01, Phase 02

## Objective
Add a top-right action button to the `TopAppBar` on `SharedLinkScreen` ("Link được chia sẻ") that displays volume state and handles click events to cycle volume.

## Requirements
### Functional
- Position button at the top-right corner inside `TopAppBar` `actions` parameter of `SharedLinkScreen`.
- Initial Visual State (Inactive):
  - Rendered in dimmed / faded out state (e.g. `alpha = 0.4f` or muted container color).
  - Phone volume remains untouched when the screen opens.
- Active Visual State (Clicked):
  - When user clicks the button, `viewModel.cycleVolume()` is triggered.
  - Button appearance updates to full opacity (`alpha = 1.0f`) displaying active state indicator (e.g., volume icon with percentage text "80%", "85%", "90%").
- Visual Feedback:
  - Displays icon `Icons.Default.VolumeUp` (or `VolumeDown`/`VolumeMute` as appropriate) with dynamic text label showing active volume percentage when activated.

## Implementation Steps
1. Modify `TopAppBar` in `SharedLinkScreen.kt` to include `actions` composable block.
2. Render volume control button using `IconButton` or custom `FilledTonalIconButton` with `Modifier.alpha(...)` reflecting `uiState.isVolumeControlActive`.
3. Connect `onClick` to `viewModel.cycleVolume()`.

## Files to Create/Modify
- [MODIFY] [SharedLinkScreen.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/main/java/com/skul9x/rssreader/ui/sharedlink/SharedLinkScreen.kt)
- [NEW] [SharedLinkScreenStateTest.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/test/java/com/skul9x/rssreader/ui/sharedlink/SharedLinkScreenStateTest.kt)

## Test Criteria & Detailed File-Based Test Plan
- Create test file `SharedLinkScreenStateTest.kt` under `app/src/test/java/com/skul9x/rssreader/ui/sharedlink/`.
- Test cases:
  1. `testVolumeButtonOpacity_whenInactive_isDimmed()`: Verifies that when `isVolumeControlActive` is `false`, the calculated opacity alpha is `0.4f`.
  2. `testVolumeButtonOpacity_whenActive_isFullOpacity()`: Verifies that when `isVolumeControlActive` is `true`, alpha is `1.0f`.
  3. `testVolumeButtonText_reflectsCurrentPercentage()`: Verifies text mapping for 80%, 85%, 90% in UI state model.

---
All Phases Complete!
