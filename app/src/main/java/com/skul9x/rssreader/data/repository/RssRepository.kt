package com.skul9x.rssreader.data.repository

import android.util.Log
import com.skul9x.rssreader.data.local.CachedNewsDao
import com.skul9x.rssreader.data.local.ReadNewsDao
import com.skul9x.rssreader.data.local.RssFeedDao
import com.skul9x.rssreader.data.model.CachedNewsItem
import com.skul9x.rssreader.data.model.NewsItem
import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.RssFeed
import com.skul9x.rssreader.data.network.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Repository for managing RSS feeds and news items.
 * Uses local caching to reduce network requests and improve performance.
 * 
 * Strategy:
 * - Cache news items in database after fetching
 * - Serve random news from cache for instant response
 * - Only fetch from network on explicit refresh (pull-to-refresh)
 * - Auto-expire cache after 1 hour
 * - Filter out news items that have been read today
 */
class RssRepository(
    private val rssFeedDao: RssFeedDao,
    private val cachedNewsDao: CachedNewsDao,
    private val readNewsDao: ReadNewsDao,
    private val rssParser: RssParser = RssParser(),
    private val localSyncRepository: LocalSyncRepository? = null
) {
    companion object {
        private const val TAG = "RssRepository"
        private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 hour
        private const val MIN_CACHE_SIZE = 20 // Minimum items to consider cache valid
        private const val READ_HISTORY_CLEANUP_DAYS = 90 // Keep read history for 90 days
    }

    /**
     * Get timestamp for start of today (00:00:00.000).
     */
    private fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Mark a news item as read.
     * Called after user selects/summarizes a news item.
     * Uses LocalSyncRepository for proper sync status tracking.
     */
    suspend fun markNewsAsRead(newsId: String) = withContext(Dispatchers.IO) {
        // Use LocalSyncRepository for sync-aware marking
        // This automatically sets device type and sync status to PENDING
        localSyncRepository?.markAsRead(newsId) ?: run {
            // Fallback if LocalSyncRepository not initialized
            readNewsDao.markAsRead(ReadNewsItem(newsId = newsId))
        }
    }

    /**
     * Get IDs of all read news items.
     */
    private suspend fun getAllReadIds(): Set<String> {
        return readNewsDao.getAllReadIds().toSet()
    }

    /**
     * Filter out already-read news items from a list.
     */
    private suspend fun filterOutReadNews(news: List<NewsItem>): List<NewsItem> {
        val readIds = getAllReadIds()
        if (readIds.isEmpty()) return news
        return news.filter { it.id !in readIds }
    }

    /**
     * Cleanup old read history (older than 7 days).
     */
    suspend fun cleanupOldReadHistory() = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (READ_HISTORY_CLEANUP_DAYS * 24 * 60 * 60 * 1000L)
        readNewsDao.deleteOlderThan(cutoffTime)
    }

    /**
     * Get count of news read today.
     */
    suspend fun getTodayReadCount(): Int = withContext(Dispatchers.IO) {
        readNewsDao.getTodayReadCount(getTodayStartTimestamp())
    }

    /**
     * Get all RSS feeds as a Flow for reactive UI updates.
     */
    fun getAllFeeds(): Flow<List<RssFeed>> = rssFeedDao.getAllFeeds()

    /**
     * Get only enabled RSS feeds.
     */
    fun getEnabledFeeds(): Flow<List<RssFeed>> = rssFeedDao.getEnabledFeeds()

    /**
     * Add a new RSS feed.
     */
    suspend fun addFeed(name: String, url: String): Long {
        val feed = RssFeed(name = name, url = url)
        return rssFeedDao.insertFeed(feed)
    }

    /**
     * Update an existing RSS feed.
     */
    suspend fun updateFeed(feed: RssFeed) {
        rssFeedDao.updateFeed(feed)
    }

    /**
     * Delete an RSS feed and its cached news.
     */
    suspend fun deleteFeed(feed: RssFeed) {
        rssFeedDao.deleteFeed(feed)
        cachedNewsDao.deleteByFeedId(feed.id)
    }

    /**
     * Toggle feed enabled/disabled state.
     */
    suspend fun toggleFeedEnabled(feedId: Long, enabled: Boolean) {
        rssFeedDao.setFeedEnabled(feedId, enabled)
    }

    /**
     * Get random news from cache, excluding already-read items.
     * Returns cached items or empty list if cache is empty.
     */
    suspend fun getRandomNewsFromCache(count: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        val enabledFeeds = rssFeedDao.getEnabledFeedsList()
        if (enabledFeeds.isEmpty()) {
            return@withContext emptyList()
        }

        val feedIds = enabledFeeds.map { it.id }
        
        // Optimize: Pass read IDs directly to SQL to avoid fetching read items
        val readIds = getAllReadIds().toList()
        
        // Fetch exactly what we need (plus a small buffer just in case)
        val cached = cachedNewsDao.getRandomNewsFromFeeds(feedIds, count + 2, readIds)
        val newsItems = cached.map { it.toNewsItem() }
        
        // Fallback filter in memory just to be safe
        val filtered = newsItems.filter { it.id !in readIds }
        
        filtered.take(count)
    }

    /**
     * Check if cache has enough items for the enabled feeds.
     */
    suspend fun isCacheValid(): Boolean = withContext(Dispatchers.IO) {
        val count = cachedNewsDao.getTotalCount()
        count >= MIN_CACHE_SIZE
    }

    /**
     * Refresh cache by fetching from network.
     * Returns random items from the freshly fetched news, excluding already-read.
     * Call this on pull-to-refresh or when cache is empty.
     */
    suspend fun refreshAndGetRandomNews(count: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        val enabledFeeds = rssFeedDao.getEnabledFeedsList()
        
        if (enabledFeeds.isEmpty()) {
            return@withContext emptyList()
        }

        // Clean up old cache entries first
        val expiryTime = System.currentTimeMillis() - CACHE_EXPIRY_MS
        cachedNewsDao.deleteOlderThan(expiryTime)
        
        // Also cleanup old read history
        cleanupOldReadHistory()

        // Fetch all feeds in parallel
        val allNewsDeferred = enabledFeeds.map { feed ->
            async {
                try {
                    rssParser.fetchFeed(feed.url, feed.id, feed.name).also {
                        rssFeedDao.updateLastFetched(feed.id, System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching ${feed.name}: ${e.message}")
                    emptyList()
                }
            }
        }

        val allNews = allNewsDeferred.awaitAll().flatten()

        if (allNews.isEmpty()) {
            // Try to return from cache if network failed
            return@withContext getRandomNewsFromCache(count)
        }

        // Cache the fetched news (upsert, duplicates handled by titleHash unique index)
        val cachedItems = allNews.map { CachedNewsItem.fromNewsItem(it) }
        try {
            cachedNewsDao.insertAll(cachedItems)
        } catch (e: Exception) {
            Log.w(TAG, "Error caching: ${e.message}")
        }

        // Filter out already-read items before returning
        val uniqueNews = allNews.distinctBy { it.id }
        val unreadNews = filterOutReadNews(uniqueNews)
        
        unreadNews
            .shuffled()
            .take(count)
    }

    /**
     * Flash Refresh: Only fetch Voz.vn, then return random mix from valid cache.
     * This is faster and ensures Voz news is always fresh, while keeping diversity.
     */
    suspend fun refreshVozAndGetRandomNews(count: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        val enabledFeeds = rssFeedDao.getEnabledFeedsList()
        if (enabledFeeds.isEmpty()) return@withContext emptyList()

        // 1. Find Voz feed
        val vozFeed = enabledFeeds.find { it.url.contains("voz.vn") } 
        
        // 2. Refresh ONLY Voz if found
        if (vozFeed != null) {
             try {
                val newItems = rssParser.fetchFeed(vozFeed.url, vozFeed.id, vozFeed.name)
                rssFeedDao.updateLastFetched(vozFeed.id, System.currentTimeMillis())
                
                // Cache immediately
                val cachedItems = newItems.map { CachedNewsItem.fromNewsItem(it) }
                cachedNewsDao.insertAll(cachedItems)
            } catch (e: Exception) {
                Log.w(TAG, "Error fast-refreshing Voz: ${e.message}")
                // Continue to serve cache even if fetch fails
            }
        } else {
             // Fallback: If no Voz, maybe user wants full refresh? 
             // For now, let's just behave like a cache fetch if Voz isn't there, 
             // or maybe we should trigger full refresh? 
             // Let's stick to "Voz only" as requested, if no Voz, do nothing network-wise.
             Log.d(TAG, "Voz feed not found or disabled for fast refresh")
        }

        // 3. Return random mix from ALL cache (Voz + others)
        // This ensures we get the "mix" effect as requested
        getRandomNewsFromCache(count)
    }

    /**
     * Smart fetch: returns from cache if valid, otherwise refreshes.
     * This is the main entry point for getting news.
     * Automatically filters out news items that have been read today.
     */
    suspend fun fetchRandomNews(count: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        // First try cache
        if (isCacheValid()) {
            val cached = getRandomNewsFromCache(count)
            if (cached.isNotEmpty()) {
                return@withContext cached
            }
        }

        // Cache empty or invalid, fetch from network
        refreshAndGetRandomNews(count)
    }

    /**
     * Pre-seed default Vietnamese news feeds if database is empty.
     */
    /**
     * Ensure default feeds exist in the database.
     */
    suspend fun seedDefaultFeedsIfEmpty() {
        val currentFeeds = rssFeedDao.getAllFeedsList()
        val existingUrls = currentFeeds.map { it.url }.toSet()

        val defaultFeeds = listOf(
            RssFeed(name = "TechCrunch", url = "https://techcrunch.com/feed/"),
            RssFeed(name = "The Verge", url = "https://www.theverge.com/rss/index.xml"),
            RssFeed(name = "Ars Technica - AI", url = "https://arstechnica.com/ai/feed/"),
            RssFeed(name = "Gizchina", url = "https://www.gizchina.com/sitemap/news.xml"),
            RssFeed(name = "Gizmodo", url = "https://gizmodo.com/feed"),
            RssFeed(name = "STAT News", url = "https://www.statnews.com/feed/"),
            RssFeed(name = "Genk - AI", url = "https://genk.vn/rss/ai.rss"),
            RssFeed(name = "Genk - Trang chủ", url = "https://genk.vn/rss/home.rss"),
            RssFeed(name = "Voz.vn - Điểm báo", url = "https://voz.vn/f/diem-bao.33/index.rss")
        )

        defaultFeeds.forEach { feed ->
            if (feed.url !in existingUrls) {
                rssFeedDao.insertFeed(feed)
            }
        }
    }

    /**
     * Clear all cached news. Useful when user wants fresh content.
     */
    suspend fun clearCache() {
        cachedNewsDao.deleteAll()
    }

    /**
     * Clear read history. Useful to show all news again.
     */
    suspend fun clearReadHistory() {
        readNewsDao.clearAll()
    }

    /**
     * Update translated titles in cache.
     */
    suspend fun updateTranslatedTitles(translations: Map<String, String>) = withContext(Dispatchers.IO) {
        translations.forEach { (id, title) ->
            try {
                cachedNewsDao.updateTranslatedTitle(id, title)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating translation for $id", e)
            }
        }
    }
}
