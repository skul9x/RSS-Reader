package com.skul9x.rssreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing activity logs to track shared link processing flow.
 * Helps debug errors in link receiving, content extraction, and Gemini API calls.
 */
@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Long = System.currentTimeMillis(),
    
    val eventType: String, // LINK_RECEIVED, FETCH_START, FETCH_SUCCESS, FETCH_ERROR, etc.
    
    val url: String,
    
    val message: String,
    
    val details: String? = null, // Truncated content, error messages, etc.
    
    val isError: Boolean = false
)
