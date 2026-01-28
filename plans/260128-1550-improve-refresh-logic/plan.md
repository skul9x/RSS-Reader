# Plan: Improve Refresh Logic & Optimization
Created: 2026-01-28 15:50
Status: ✅ Complete

## Overview
Optimizing the News Refresh flow to be smarter and more efficient. 
1. **Flash Refresh**: Enabling "Voz-only" fetch on manual refresh mixed with cached items for instant results.
2. **Smart Query**: Filtering "Read" items directly in SQL to prevent returning empty lists when cache is full of read items.
3. **True Refresh**: Ensuring the UI actually requests new data instead of just shuffling local cache.

## Tech Stack
- **Database**: Room (SQLite)
- **Logic**: Kotlin Coroutines
- **UI**: Jetpack Compose

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Database Optimization | ✅ Complete | 100% |
| 02 | Logic & UI Connection | ✅ Complete | 100% |
| 03 | Verification | ✅ Complete | 100% |

## Checklist
- [x] Database excludes read items properly?
- [x] Refresh button fetches new data (Voz)?
- [x] No crash when `excludedIds` is empty?

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
