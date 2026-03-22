# Plan: Landscape Continuous Reading
Created: 2026-03-21T00:45
Status: 🟡 In Progress

## Overview
Thêm chức năng đọc không giới hạn (Continuous Reading) vào layout màn hình ngang (Landscape). Hiện tại chức năng này đã hoạt động ở Portrait nhưng chưa có ở Landscape.

**Scope:** Chỉ thay đổi UI layer (`MainScreen.kt`). ViewModel và Service đã sẵn sàng.

## Tech Stack
- Frontend: Jetpack Compose (Kotlin)
- Backend Logic: MainViewModel (đã có `startContinuousReading()`)
- Service: NewsReaderService (đã có `isContinuousMode`)

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Long-Press Gesture | ✅ Complete | 100% |
| 02 | Continuous Banner | ✅ Complete | 100% |
| 03 | Auto-Scroll | ✅ Complete | 100% |
| 04 | Testing | ✅ Complete | 100% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
