package com.skul9x.rssreader.data.repository

import com.skul9x.rssreader.data.local.dao.FirebaseLogDao
import com.skul9x.rssreader.data.local.entity.FirebaseLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseLogRepository private constructor(
    private val firebaseLogDao: FirebaseLogDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Log types
    companion object {
        const val TYPE_SUCCESS = "SUCCESS"
        const val TYPE_ERROR = "ERROR"
        const val TYPE_INFO = "INFO"

        @Volatile
        private var INSTANCE: FirebaseLogRepository? = null

        fun getInstance(firebaseLogDao: FirebaseLogDao): FirebaseLogRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseLogRepository(firebaseLogDao)
                INSTANCE = instance
                instance
            }
        }
    }

    fun log(type: String, message: String, details: String? = null) {
        scope.launch {
            val log = FirebaseLog(
                type = type,
                message = message,
                details = details
            )
            firebaseLogDao.insert(log)
            // Trigger cleanup if needed - simple check or period cleanup? 
            // Querying count every insert might be expensive? 
            // Let's do a cleanup of logs > 1000 items. 
            // Since deleteOldLogs checks count/IDs internally or via offset, we can just call it occasionally or every time.
            // On a BG thread, calling every time is acceptable for low frequency sync events.
            firebaseLogDao.deleteOldLogs(1000)
        }
    }
    
    // Convenience methods
    fun logSuccess(message: String, details: String? = null) {
        log(TYPE_SUCCESS, message, details)
    }

    fun logError(message: String, error: Throwable? = null) {
        val details = error?.stackTraceToString()
        log(TYPE_ERROR, message, details)
    }
    
    fun logInfo(message: String, details: String? = null) {
        log(TYPE_INFO, message, details)
    }

    fun getAllLogs(): Flow<List<FirebaseLog>> {
        return firebaseLogDao.getAllLogs()
    }

    fun clearAllLogs() {
        scope.launch {
            firebaseLogDao.deleteAll()
        }
    }
}
