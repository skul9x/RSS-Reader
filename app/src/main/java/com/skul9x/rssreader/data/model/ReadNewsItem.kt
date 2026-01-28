package com.skul9x.rssreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sync status for read news items.
 * - PENDING: Not yet synced to Firestore
 * - SYNCED: Successfully synced
 * - FAILED: Sync failed, will retry
 */
enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

/**
 * Room entity for tracking read/summarized news items.
 * Used to hide already-read news from appearing again and for cross-device sync.
 */
@Entity(tableName = "read_news")
data class ReadNewsItem(
    @PrimaryKey 
    val newsId: String,  // MD5 hash from NewsItem.id or RSS item ID
    val readAt: Long = System.currentTimeMillis(),
    val deviceType: String = "smartphone",  // "smartphone" or "androidbox"
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
