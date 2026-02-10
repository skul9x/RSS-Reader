# Phase 02: SQL Optimization
Status: ✅ Complete
Dependencies: Phase 01

## Objective
Optimize `CachedNewsDao.getUnreadRandomNewsFromFeeds` to replace slow `NOT IN` subquery with `LEFT JOIN` (Anti-Join).

## Requirements
- [x] Replace `AND id NOT IN (SELECT newsId FROM read_news)` with efficient SQL.
- [x] Verify syntax compatibility with Room.
  - Room supports `LEFT JOIN` and `IS NULL` for anti-joins.
  - `SELECT cached_news.*` ensures only news columns are returned, not `read_news` columns.

## Implementation Steps
1. [x] Edit `app/src/main/java/com/skul9x/rssreader/data/local/CachedNewsDao.kt`:
   - Rewrite `@Query` for `getUnreadRandomNewsFromFeeds`.
   - Usage: `SELECT cached_news.* FROM cached_news LEFT JOIN read_news ON cached_news.id = read_news.newsId WHERE read_news.newsId IS NULL AND ...`

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/local/CachedNewsDao.kt`

---
Next Phase: [Verification](./phase-03-verification.md)
