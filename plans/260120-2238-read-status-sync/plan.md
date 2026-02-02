# Plan: Batch + Timer Sync - Read Status Synchronization
Created: 2026-01-20 22:38:21 +07:00  
Status: 🟡 Pending

## Overview
Implement bi-directional read status synchronization between smartphone and Android box using Firebase Firestore. Sync strategy: batch 10 items OR every 5 minutes OR when app goes to background.

## Tech Stack
- **Auth:** Firebase Authentication + Google Sign-In
- **Remote Database:** Firebase Firestore
- **Local Database:** Room Database + DataStore (tùy implementation)
- **Scheduler:** WorkManager (batch timer) + Lifecycle Observer (background detection)
- **Language:** Kotlin
- **Platform:** Android

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Setup Firebase & Dependencies | ⬜ Pending | 0% |
| 02 | Implement Google Sign-In | ⬜ Pending | 0% |
| 03 | Local Database Schema | ⬜ Pending | 0% |
| 04 | Firestore Schema & Sync Logic | ⬜ Pending | 0% |
| 05 | Batch Queue Manager | ⬜ Pending | 0% |
| 06 | Timer & Background Sync | ⬜ Pending | 0% |
| 07 | UI Integration & Conflict Resolution | ⬜ Pending | 0% |
| 08 | Testing & Cleanup | ⬜ Pending | 0% |

## Key Features
✅ Batch sync: 10 items/batch  
✅ Timer trigger: 5 minutes  
✅ Background trigger: App lifecycle  
✅ Conflict resolution: Prioritize earliest timestamp + smartphone deviceType  
✅ Offline support: Queue persists via WorkManager  
✅ Auto cleanup: Delete items > 30 days old  
✅ Silent sync: No UI indicators  

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`

## Estimated Effort
- **Total Tasks:** ~45-50 tasks
- **Estimated Sessions:** 3-4 coding sessions
- **Complexity:** Medium-High (Firebase + WorkManager + Conflict Resolution)
