# Phase 01: Database Support
Status: ✅ Complete

## Objective
Add `getByIds` method to `ReadNewsDao` and `LocalSyncRepository` to support batch fetching.

## Requirements
- [x] `ReadNewsDao`: Add query `SELECT * FROM read_news WHERE newsId IN (:ids)`
- [x] `LocalSyncRepository`: Add wrapper method `getByIds(ids: List<String>)`

## Implementation Steps
1. [x] Edit `app/src/main/java/com/skul9x/rssreader/data/local/ReadNewsDao.kt`
   - Add `suspend fun getByIds(newsIds: List<String>): List<ReadNewsItem>`
2. [x] Edit `app/src/main/java/com/skul9x/rssreader/data/repository/LocalSyncRepository.kt`
   - Add `suspend fun getByIds(newsIds: List<String>): List<ReadNewsItem>` which delegates to DAO.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/local/ReadNewsDao.kt`
- `app/src/main/java/com/skul9x/rssreader/data/repository/LocalSyncRepository.kt`

---
Next Phase: [Refactor Sync Logic](./phase-02-refactor-sync-logic.md)
