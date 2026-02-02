package com.skul9x.rssreader.data.local

import androidx.room.*
import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for read news history.
 * Tracks which news items have been read/summarized to hide them from selection
 * and supports cross-device synchronization.
 */
@Dao
interface ReadNewsDao {

    /**
     * Mark a news item as read.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsRead(item: ReadNewsItem)

    /**
     * Insert multiple read items at once (batch operation).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ReadNewsItem>)

    /**
     * Get all news IDs that were read today (since todayStart timestamp).
     */
    @Query("SELECT newsId FROM read_news WHERE readAt >= :todayStart")
    suspend fun getTodayReadIds(todayStart: Long): List<String>

    /**
     * Get all news IDs that have been read.
     */
    @Query("SELECT newsId FROM read_news")
    suspend fun getAllReadIds(): List<String>

    /**
     * Get count of news read today.
     */
    @Query("SELECT COUNT(*) FROM read_news WHERE readAt >= :todayStart")
    suspend fun getTodayReadCount(todayStart: Long): Int

    /**
     * Check if a news item has been read.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM read_news WHERE newsId = :newsId)")
    suspend fun isRead(newsId: String): Boolean

    /**
     * Get a single read item by ID.
     */
    @Query("SELECT * FROM read_news WHERE newsId = :newsId")
    suspend fun getById(newsId: String): ReadNewsItem?

    /**
     * Get multiple read items by IDs (batch).
     */
    @Query("SELECT * FROM read_news WHERE newsId IN (:newsIds)")
    suspend fun getByIds(newsIds: List<String>): List<ReadNewsItem>

    // ========== SYNC OPERATIONS ==========

    /**
     * Get all items with pending sync status.
     */
    @Query("SELECT * FROM read_news WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<ReadNewsItem>

    /**
     * Get items pending sync as Flow (for observation).
     */
    @Query("SELECT * FROM read_news WHERE syncStatus = 'PENDING'")
    fun getPendingSyncFlow(): Flow<List<ReadNewsItem>>

    /**
     * Get count of pending sync items.
     */
    @Query("SELECT COUNT(*) FROM read_news WHERE syncStatus = 'PENDING'")
    suspend fun getPendingCount(): Int

    /**
     * Update sync status for specific items.
     */
    @Query("UPDATE read_news SET syncStatus = :newStatus WHERE newsId IN (:newsIds)")
    suspend fun updateSyncStatus(newsIds: List<String>, newStatus: SyncStatus)

    /**
     * Mark all pending items as synced.
     */
    @Query("UPDATE read_news SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING'")
    suspend fun markAllAsSynced()

    // ========== CLEANUP OPERATIONS ==========

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

    /**
     * Get total count of read items.
     */
    @Query("SELECT COUNT(*) FROM read_news")
    suspend fun getTotalCount(): Int
}
