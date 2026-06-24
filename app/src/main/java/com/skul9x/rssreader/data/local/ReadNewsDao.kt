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
     * Lấy danh sách các ID ĐÃ ĐỌC từ một list ID truyền vào.
     * Sử dụng để check nhanh DB (tránh N+1 memory issues) thay vì kéo toàn bộ lịch sử lên RAM.
     */
    @Query("SELECT newsId FROM read_news WHERE newsId IN (:newsIds)")
    suspend fun getAlreadyReadIds(newsIds: List<String>): List<String>

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

    /**
     * Get IDs from the given list that currently have PENDING sync status.
     * Used to protect PENDING items from being overwritten by remote data.
     */
    @Query("SELECT newsId FROM read_news WHERE newsId IN (:newsIds) AND syncStatus = 'PENDING'")
    suspend fun getPendingIds(newsIds: List<String>): List<String>

    /**
     * Batch upsert remote items, skipping any that would overwrite PENDING local items.
     * Uses 2-query batch strategy instead of N individual queries:
     *   1. Fetch all PENDING IDs from input set (1 query)
     *   2. Insert non-PENDING items in batch (1 query)
     *
     * Handles SQLite's 999 variable limit by chunking internally.
     */
    @Transaction
    suspend fun upsertFromRemoteBatch(items: List<ReadNewsItem>) {
        if (items.isEmpty()) return

        // Step 1: Find which items from the input are currently PENDING locally
        val allIds = items.map { it.newsId }
        val pendingIds = mutableSetOf<String>()
        allIds.chunked(900).forEach { chunk ->
            pendingIds.addAll(getPendingIds(chunk))
        }

        // Step 2: Filter out PENDING items (protect local changes)
        val safeToInsert = if (pendingIds.isEmpty()) {
            items
        } else {
            items.filter { it.newsId !in pendingIds }
        }

        // Step 3: Batch insert (Room handles batch efficiently with @Insert)
        if (safeToInsert.isNotEmpty()) {
            insertAll(safeToInsert)
        }
    }

    /**
     * Insert/update a remote item only if it doesn't overwrite a PENDING item.
     * WARNING: This does NOT perform conflict resolution (earliest-wins).
     * Conflict resolution MUST be done by the caller (SyncCoordinator.mergeWithLocal).
     * Only items that SHOULD be written should be passed to this method.
     */
    @Deprecated("Use upsertFromRemoteBatch for better performance")
    @Query("""
        INSERT OR REPLACE INTO read_news (newsId, readAt, deviceType, syncStatus)
        SELECT :newsId, :readAt, :deviceType, :syncStatus
        WHERE NOT EXISTS (
            SELECT 1 FROM read_news WHERE newsId = :newsId AND syncStatus = 'PENDING'
        )
    """)
    suspend fun upsertFromRemoteIfNotPending(
        newsId: String, readAt: Long, deviceType: String, syncStatus: SyncStatus
    )

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
