package com.skul9x.rssreader.data.local

import androidx.room.*
import com.skul9x.rssreader.data.model.ReadNewsItem

/**
 * Data Access Object for read news history.
 * Tracks which news items have been read/summarized to hide them from selection.
 */
@Dao
interface ReadNewsDao {

    /**
     * Mark a news item as read.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsRead(item: ReadNewsItem)

    /**
     * Get all news IDs that were read today (since todayStart timestamp).
     */
    @Query("SELECT newsId FROM read_news WHERE readAt >= :todayStart")
    suspend fun getTodayReadIds(todayStart: Long): List<String>

    /**
     * Get count of news read today.
     */
    @Query("SELECT COUNT(*) FROM read_news WHERE readAt >= :todayStart")
    suspend fun getTodayReadCount(todayStart: Long): Int

    /**
     * Delete read history older than specified timestamp.
     * Call periodically to clean up old data.
     */
    @Query("DELETE FROM read_news WHERE readAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Clear all read history.
     */
    @Query("DELETE FROM read_news")
    suspend fun clearAll()
}
