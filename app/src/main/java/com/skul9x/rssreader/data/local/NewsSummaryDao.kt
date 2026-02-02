package com.skul9x.rssreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NewsSummaryDao {
    @Query("SELECT * FROM news_summaries WHERE url = :url")
    suspend fun getSummary(url: String): NewsSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: NewsSummary)

    @Query("DELETE FROM news_summaries WHERE timestamp < :timestamp")
    suspend fun deleteOldSummaries(timestamp: Long)
}
