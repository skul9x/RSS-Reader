package com.skul9x.rssreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.skul9x.rssreader.data.model.ActivityLog
import com.skul9x.rssreader.data.model.CachedNewsItem
import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.RssFeed

/**
 * Room database for the RSS Reader app.
 * Stores RSS feed sources, cached news items, and read history for persistence.
 */
import com.skul9x.rssreader.data.local.dao.FirebaseLogDao
import com.skul9x.rssreader.data.local.entity.FirebaseLog

@Database(
    entities = [RssFeed::class, CachedNewsItem::class, ReadNewsItem::class, NewsSummary::class, ActivityLog::class, FirebaseLog::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rssFeedDao(): RssFeedDao
    abstract fun cachedNewsDao(): CachedNewsDao
    abstract fun readNewsDao(): ReadNewsDao
    abstract fun newsSummaryDao(): NewsSummaryDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun firebaseLogDao(): FirebaseLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rss_reader_database"
                )
                    .fallbackToDestructiveMigration() // Safe for cache - data can be re-fetched
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
