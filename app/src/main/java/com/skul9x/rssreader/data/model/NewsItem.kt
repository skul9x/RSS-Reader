package com.skul9x.rssreader.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing a single news item from an RSS feed.
 * Not persisted locally - only held in memory during app session.
 * Implements Parcelable for passing to NewsReaderService.
 */
@Parcelize
data class NewsItem(
    val id: String,
    val title: String,
    val description: String,
    val content: String,
    val link: String,
    val pubDate: String,
    val imageUrl: String? = null,
    val feedId: Long = 0,
    val feedName: String = "",
    val sourceName: String = "",  // For display in notification
    val translatedTitle: String? = null,
    val isTranslating: Boolean = false
) : Parcelable {
    /**
     * Get the best available content for AI summarization.
     * Prioritizes full content, falls back to description.
     */
    fun getContentForSummary(): String {
        return when {
            content.isNotBlank() -> content
            description.isNotBlank() -> description
            else -> title
        }
    }
}
