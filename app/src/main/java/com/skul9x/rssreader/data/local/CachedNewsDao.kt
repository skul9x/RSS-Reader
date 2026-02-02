package com.skul9x.rssreader.data.local

import androidx.room.*
import com.skul9x.rssreader.data.model.CachedNewsItem

/**
 * Data Access Object for cached news items.
 * Provides efficient queries for news caching and random selection.
 */
@Dao
interface CachedNewsDao {

    /**
     * Insert or replace news items (upsert).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(news: List<CachedNewsItem>)

    /**
     * Get IDs of unread news from specific feeds.
     * Used for application-level shuffling.
     */
    @Query("""
        SELECT cached_news.id FROM cached_news 
        LEFT JOIN read_news ON cached_news.id = read_news.newsId 
        WHERE cached_news.feedId IN (:feedIds) 
        AND cached_news.id NOT IN (:excludedIds) 
        AND read_news.newsId IS NULL
    """)
    suspend fun getUnreadNewsIdsFromFeeds(feedIds: List<Long>, excludedIds: List<String>): List<String>

    /**
     * Get news items by their IDs.
     */
    @Query("SELECT * FROM cached_news WHERE id IN (:ids)")
    suspend fun getNewsByIds(ids: List<String>): List<CachedNewsItem>

    /**
     * Get random news items from cache.
     * Uses SQLite RANDOM() for efficient random selection.
     * @deprecated Use application-level shuffling instead (getUnreadNewsIdsFromFeeds + getNewsByIds)
     */
    @Deprecated("Use application-level shuffling mechanism to avoid poor performance on large datasets")
    @Query("SELECT * FROM cached_news WHERE id NOT IN (:excludedIds) ORDER BY RANDOM() LIMIT :count")
    suspend fun getRandomNews(count: Int, excludedIds: List<String>): List<CachedNewsItem>

    /**
     * Get random news from specific feeds, excluding explicitly excluded IDs AND read news items.
     * Uses LEFT JOIN (Anti-Join) instead of NOT IN subquery for better performance on large datasets.
     * @deprecated Use application-level shuffling instead (getUnreadNewsIdsFromFeeds + getNewsByIds)
     */
    @Deprecated("Use application-level shuffling mechanism to avoid poor performance on large datasets")
    @Query("""
        SELECT cached_news.* FROM cached_news 
        LEFT JOIN read_news ON cached_news.id = read_news.newsId 
        WHERE cached_news.feedId IN (:feedIds) 
        AND cached_news.id NOT IN (:excludedIds) 
        AND read_news.newsId IS NULL 
        ORDER BY RANDOM() LIMIT :count
    """)
    suspend fun getUnreadRandomNewsFromFeeds(feedIds: List<Long>, count: Int, excludedIds: List<String>): List<CachedNewsItem>

    /**
     * Get random news from specific feeds.
     * @deprecated Use application-level shuffling instead
     */
    @Deprecated("Use application-level shuffling mechanism")
    @Query("SELECT * FROM cached_news WHERE feedId IN (:feedIds) AND id NOT IN (:excludedIds) ORDER BY RANDOM() LIMIT :count")
    suspend fun getRandomNewsFromFeeds(feedIds: List<Long>, count: Int, excludedIds: List<String>): List<CachedNewsItem>

    /**
     * Get news count for a specific feed.
     */
    @Query("SELECT COUNT(*) FROM cached_news WHERE feedId = :feedId")
    suspend fun getNewsCountForFeed(feedId: Long): Int

    /**
     * Get total cached news count.
     */
    @Query("SELECT COUNT(*) FROM cached_news")
    suspend fun getTotalCount(): Int

    /**
     * Delete news older than specified timestamp.
     */
    @Query("DELETE FROM cached_news WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Delete all news for a specific feed.
     */
    @Query("DELETE FROM cached_news WHERE feedId = :feedId")
    suspend fun deleteByFeedId(feedId: Long)

    /**
     * Delete all cached news.
     */
    /**
     * Update translated title for a news item.
     */
    @Query("UPDATE cached_news SET translatedTitle = :translatedTitle WHERE id = :id")
    suspend fun updateTranslatedTitle(id: String, translatedTitle: String)

    /**
     * Delete all cached news.
     */
    @Query("DELETE FROM cached_news")
    suspend fun deleteAll()
}
