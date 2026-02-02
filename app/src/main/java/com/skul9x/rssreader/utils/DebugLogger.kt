package com.skul9x.rssreader.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton logger for capturing debug events, especially for car media buttons.
 * Thread-safe implementation using atomic StateFlow updates.
 */
object DebugLogger {
    
    data class LogEntry(
        val timestamp: Long,
        val timeString: String,
        val tag: String,
        val message: String
    )
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private const val DATE_FORMAT_PATTERN = "HH:mm:ss.SSS"
    
    // Limit log size to prevent memory issues
    private const val MAX_LOGS = 1000
    
    /**
     * Create a new SimpleDateFormat instance for thread-safety.
     * SimpleDateFormat is NOT thread-safe, so we create a new instance each time.
     */
    private fun createDateFormat(): SimpleDateFormat {
        return SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.getDefault())
    }
    
    /**
     * Log a message with the given tag.
     * Thread-safe: uses atomic update operation on StateFlow.
     */
    fun log(tag: String, message: String) {
        val now = System.currentTimeMillis()
        val timeString = createDateFormat().format(Date(now))
        val entry = LogEntry(now, timeString, tag, message)
        
        // Atomic update to prevent race condition
        _logs.update { currentLogs ->
            val newLogs = currentLogs.toMutableList()
            newLogs.add(0, entry) // Add to top
            
            if (newLogs.size > MAX_LOGS) {
                newLogs.removeAt(newLogs.lastIndex)
            }
            
            newLogs.toList()
        }
    }
    
    fun clear() {
        _logs.update { emptyList() }
    }
    
    fun getLogsAsString(): String {
        val dateFormat = createDateFormat()
        val sb = StringBuilder()
        sb.append("RSS Reader Debug Logs\n")
        sb.append("Generated at: ${dateFormat.format(Date())}\n\n")
        
        // Take a snapshot of logs for thread-safety
        val logsSnapshot = _logs.value
        logsSnapshot.forEach { entry ->
            sb.append("[${entry.timeString}] ${entry.tag}: ${entry.message}\n")
        }
        
        return sb.toString()
    }
}
