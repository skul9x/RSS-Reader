package com.skul9x.rssreader.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching news items from RSS feeds.
 * Persisted locally to avoid fetching all feeds on every refresh.
 * 
 * Indexed by feedId for efficient querying and titleHash for deduplication.
 */
@Entity(
    tableName = "cached_news",
    indices = [
        Index(value = ["feedId"]),
        Index(value = ["titleHash"], unique = true)
    ]
)
data class CachedNewsItem(
    @PrimaryKey
    val id: String,           // MD5 hash of link + title
    val title: String,
    val description: String,
    val content: String,
    val link: String,
    val pubDate: String,
    val imageUrl: String?,
    val feedId: Long,
    val feedName: String,
    val titleHash: String,    // Lowercase trimmed title hash for deduplication
    val translatedTitle: String? = null, // Added for translation caching
    val cachedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert to in-memory NewsItem for UI consumption.
     */
    fun toNewsItem(): NewsItem = NewsItem(
        id = id,
        title = title,
        description = description,
        content = content,
        link = link,
        pubDate = pubDate,
        imageUrl = imageUrl,
        feedId = feedId,
        feedName = feedName,
        translatedTitle = translatedTitle
    )

    companion object {
        /**
         * Create CachedNewsItem from NewsItem.
         */
        fun fromNewsItem(newsItem: NewsItem): CachedNewsItem = CachedNewsItem(
            id = newsItem.id,
            title = newsItem.title,
            description = newsItem.description,
            content = newsItem.content,
            link = newsItem.link,
            pubDate = newsItem.pubDate,
            imageUrl = newsItem.imageUrl,
            feedId = newsItem.feedId,
            feedName = newsItem.feedName,
            titleHash = newsItem.title.lowercase().trim().hashCode().toString(),
            translatedTitle = newsItem.translatedTitle
        )
    }
}
