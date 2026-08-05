# Phase 02: ViewModel State & Volume Toggle Handler

Status: ✅ Completed
Dependencies: Phase 01

## Objective
Integrate volume control state and cycling behavior into `SharedLinkViewModel` and `SharedLinkUiState`.

## Requirements
### Functional
- Add fields to `SharedLinkUiState`:
  - `isVolumeControlActive: Boolean = false` (default false so initial state when opening screen does NOT adjust device volume).
  - `currentVolumePercent: Int? = null` (null when inactive, 80/85/90 when active).
- Add method `cycleVolume()` in `SharedLinkViewModel`:
  - Triggers volume update using `VolumeController`.
  - When called first time: sets `isVolumeControlActive = true` and `currentVolumePercent = 80`, adjusts system volume to 80%.
  - When called 2nd time: sets `currentVolumePercent = 85`, adjusts system volume to 85%.
  - When called 3rd time: sets `currentVolumePercent = 90`, adjusts system volume to 90%.
  - When called 4th time: sets `currentVolumePercent = 80`, adjusts system volume to 80%.
- Ensure `processUrl(url)` or screen initialization DOES NOT touch volume or change `isVolumeControlActive` to true automatically.

## Implementation Steps
1. Modify `SharedLinkUiState` in `SharedLinkViewModel.kt` to include volume state properties.
2. Inject/utilize `VolumeController` instance in `SharedLinkViewModel`.
3. Implement `cycleVolume()` function in `SharedLinkViewModel`.

## Files to Create/Modify
- [MODIFY] [SharedLinkViewModel.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/main/java/com/skul9x/rssreader/ui/sharedlink/SharedLinkViewModel.kt)
- [NEW] [SharedLinkViewModelTest.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/test/java/com/skul9x/rssreader/ui/sharedlink/SharedLinkViewModelTest.kt)

## Test Criteria & Detailed File-Based Test Plan
- Create test file `SharedLinkViewModelTest.kt` under `app/src/test/java/com/skul9x/rssreader/ui/sharedlink/`.
- Test cases:
  1. `testInitialState_volumeInactiveAndNull()`: Verify initial `isVolumeControlActive` is `false` and `currentVolumePercent` is `null`.
  2. `testProcessUrl_doesNotModifyVolumeState()`: Verify processing a shared URL does not change volume state automatically.
  3. `testCycleVolume_firstClick_sets80PercentActive()`: First click sets active = true and volume = 80%.
  4. `testCycleVolume_subsequentClicks_cycles85_90_80()`: Subsequent calls advance through 85% -> 90% -> 80%.

---
Next Phase: [Phase 03: SharedLinkScreen UI TopBar Button](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/volume-control-shared-link/phase-03-ui-topbar-button.md)
