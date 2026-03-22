package com.skul9x.rssreader.data.local

import androidx.room.*
import com.skul9x.rssreader.data.model.RssFeed
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for RSS feeds.
 * Provides reactive queries using Flow for real-time updates.
 */
@Dao
interface RssFeedDao {

    @Query("SELECT * FROM rss_feeds ORDER BY name ASC")
    fun getAllFeeds(): Flow<List<RssFeed>>

    @Query("SELECT * FROM rss_feeds WHERE enabled = 1 ORDER BY name ASC")
    fun getEnabledFeeds(): Flow<List<RssFeed>>

    @Query("SELECT * FROM rss_feeds WHERE enabled = 1")
    suspend fun getEnabledFeedsList(): List<RssFeed>

    @Query("SELECT * FROM rss_feeds")
    suspend fun getAllFeedsList(): List<RssFeed>

    @Query("SELECT * FROM rss_feeds WHERE id = :id")
    suspend fun getFeedById(id: Long): RssFeed?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: RssFeed): Long

    @Update
    suspend fun updateFeed(feed: RssFeed)

    @Delete
    suspend fun deleteFeed(feed: RssFeed)

    @Query("UPDATE rss_feeds SET lastFetched = :timestamp WHERE id = :feedId")
    suspend fun updateLastFetched(feedId: Long, timestamp: Long)

    @Query("UPDATE rss_feeds SET enabled = :enabled WHERE id = :feedId")
    suspend fun setFeedEnabled(feedId: Long, enabled: Boolean)
}
