package com.skul9x.rssreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.skul9x.rssreader.data.model.ActivityLog
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ActivityLog operations.
 */
@Dao
interface ActivityLogDao {
    
    @Insert
    suspend fun insert(log: ActivityLog)
    
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActivityLog>>
    
    @Query("SELECT * FROM activity_logs WHERE isError = 1 ORDER BY timestamp DESC")
    fun getErrorLogs(): Flow<List<ActivityLog>>
    
    @Query("DELETE FROM activity_logs")
    suspend fun deleteAll()
    
    @Query("DELETE FROM activity_logs WHERE id = :logId")
    suspend fun deleteById(logId: Long)

    @Query("DELETE FROM activity_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldLogs(cutoffTimestamp: Long)
    
    @Query("SELECT COUNT(*) FROM activity_logs")
    suspend fun getLogCount(): Int
}
