# Phase 01: Volume Controller & Step Cycle Logic

Status: ✅ Completed
Dependencies: None

## Objective
Implement `VolumeController` helper class to manage device media stream volume (`AudioManager.STREAM_MUSIC`) and volume step calculations (80%, 85%, 90% cycling loop).

## Requirements
### Functional
- Define constant volume percentages sequence: `listOf(80, 85, 90)`.
- Support calculating nearest stream volume index given max volume level from `AudioManager`.
- Provide `getNextVolumePercentage(currentPercent: Int?): Int` which returns:
  - `80` if `currentPercent` is null or invalid.
  - `85` if current is `80`.
  - `90` if current is `85`.
  - `80` if current is `90` (cycling back).
- Provide `setDeviceVolume(context: Context, percentage: Int): Boolean` to safely adjust `AudioManager.STREAM_MUSIC`.
- Ensure NO volume modifications occur when initialized.

### Non-Functional
- Handle exceptions safely (e.g. `SecurityException` or missing volume permissions gracefully).

## Implementation Steps
1. Create `VolumeController.kt` utility class in package `com.skul9x.rssreader.utils`.
2. Implement step loop logic: `80% -> 85% -> 90% -> 80%`.
3. Implement `AudioManager` volume setter for `STREAM_MUSIC`.

## Files to Create/Modify
- [NEW] [VolumeController.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/main/java/com/skul9x/rssreader/utils/VolumeController.kt)
- [NEW] [VolumeControllerTest.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/test/java/com/skul9x/rssreader/utils/VolumeControllerTest.kt)

## Test Criteria & Detailed File-Based Test Plan
- Create test file `VolumeControllerTest.kt` under `app/src/test/java/com/skul9x/rssreader/utils/`.
- Test cases:
  1. `testGetNextVolumePercentage_initialNull_returns80()`: Initial state returns 80%.
  2. `testGetNextVolumePercentage_from80_returns85()`: Cycling 80% -> 85%.
  3. `testGetNextVolumePercentage_from85_returns90()`: Cycling 85% -> 90%.
  4. `testGetNextVolumePercentage_from90_returns80()`: Cycling 90% -> 80% (loop).
  5. `testCalculateVolumeIndex()`: Ensures accurate math ratio mapping percentage to `AudioManager.getStreamMaxVolume(STREAM_MUSIC)`.

---
Next Phase: [Phase 02: ViewModel State & Volume Toggle Handler](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/volume-control-shared-link/phase-02-viewmodel-integration.md)
