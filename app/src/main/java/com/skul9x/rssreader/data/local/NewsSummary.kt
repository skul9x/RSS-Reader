package com.skul9x.rssreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to cache Gemini API summaries for news items.
 * Key is the news URL.
 */
@Entity(tableName = "news_summaries")
data class NewsSummary(
    @PrimaryKey
    val url: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)
