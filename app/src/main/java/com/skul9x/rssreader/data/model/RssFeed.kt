package com.skul9x.rssreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an RSS feed source.
 * Stored locally for persistence across app restarts.
 */
@Entity(tableName = "rss_feeds")
data class RssFeed(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastFetched: Long = 0L
)
