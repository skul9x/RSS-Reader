# Phase 01: Database Optimization
Status: ⬜ Pending
Dependencies: None

## Objective
Optimize `CachedNewsDao` to filter out read items directly in the SQL query. This prevents the "Cache Roulette" issue where the app fetches cached items that have already been read, resulting in empty lists or unnecessary network calls.

## Requirements
### Functional
- [ ] Update `CachedNewsDao` to accept a list of excluded IDs (read items).
- [ ] Modify `getRandomNews` to use `NOT IN (:excludedIds)`.
- [ ] Modify `getRandomNewsFromFeeds` to use `NOT IN (:excludedIds)`.

### Non-Functional
- [ ] Performance: Ensure Query doesn't slow down significantly (SQLite handles NOT IN reasonably well for small-medium lists).

## Implementation Steps
1. [ ] Modify `CachedNewsDao.kt`: Add `excludedIds` parameter to random fetch queries.
2. [ ] Modify `RssRepository.kt`: Pass the list of read IDs when calling DAO.

## Files to Modify
- `app/src/main/java/com/skul9x/rssreader/data/local/CachedNewsDao.kt`
- `app/src/main/java/com/skul9x/rssreader/data/repository/RssRepository.kt`

## Test Criteria
- [ ] Unit test: Verify DAO excludes items in the blacklist.
- [ ] Integration: calling repository returns only unread items from cache.

---
Next Phase: [Phase 02](phase-02-logic-ui.md)
