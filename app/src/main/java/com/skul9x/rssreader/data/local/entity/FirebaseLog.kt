package com.skul9x.rssreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "firebase_logs")
data class FirebaseLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "SUCCESS", "ERROR", "INFO"
    val message: String,
    val details: String? = null // Stacktrace or JSON data
)
