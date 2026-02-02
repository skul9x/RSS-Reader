package com.skul9x.rssreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.skul9x.rssreader.data.local.entity.FirebaseLog
import kotlinx.coroutines.flow.Flow

@Dao
interface FirebaseLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: FirebaseLog)

    @Query("SELECT * FROM firebase_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<FirebaseLog>>

    @Query("DELETE FROM firebase_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM firebase_logs WHERE id NOT IN (SELECT id FROM firebase_logs ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteOldLogs(limit: Int)
}
