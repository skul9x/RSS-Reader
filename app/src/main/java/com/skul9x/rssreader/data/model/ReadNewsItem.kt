package com.skul9x.rssreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for tracking read/summarized news items.
 * Used to hide already-read news from appearing again on the same day.
 */
@Entity(tableName = "read_news")
data class ReadNewsItem(
    @PrimaryKey 
    val newsId: String,  // MD5 hash from NewsItem.id
    val readAt: Long = System.currentTimeMillis()
)
