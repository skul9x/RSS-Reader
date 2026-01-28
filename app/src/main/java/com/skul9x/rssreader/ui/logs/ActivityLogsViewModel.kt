package com.skul9x.rssreader.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.model.ActivityLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * ViewModel for Activity Logs screen.
 * Manages log data, filtering, and cleanup operations.
 */
class ActivityLogsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val logDao = database.activityLogDao()
    
    private val _uiState = MutableStateFlow(ActivityLogsUiState())
    val uiState: StateFlow<ActivityLogsUiState> = _uiState.asStateFlow()
    
    init {
        // Load logs and cleanup old ones
        loadLogs()
        cleanupOldLogs()
    }
    
    /**
     * Load logs based on current filter.
     */
    private fun loadLogs() {
        viewModelScope.launch {
            val logsFlow = if (_uiState.value.showErrorsOnly) {
                logDao.getErrorLogs()
            } else {
                logDao.getAllLogs()
            }
            
            logsFlow.collect { logs ->
                _uiState.update { it.copy(logs = logs, isLoading = false) }
            }
        }
    }
    
    /**
     * Toggle filter between all logs and errors only.
     */
    fun toggleFilter() {
        _uiState.update { it.copy(showErrorsOnly = !it.showErrorsOnly, isLoading = true) }
        loadLogs()
    }
    
    /**
     * Clear all logs.
     */
    fun clearAllLogs() {
        viewModelScope.launch {
            logDao.deleteAll()
        }
    }
    
    /**
     * Delete a single log by ID.
     */
    fun deleteLog(logId: Long) {
        viewModelScope.launch {
            logDao.deleteById(logId)
        }
    }
    
    /**
     * Delete logs older than 7 days.
     */
    private fun cleanupOldLogs() {
        viewModelScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            logDao.deleteOldLogs(sevenDaysAgo)
        }
    }
}

/**
 * UI State for Activity Logs screen.
 */
data class ActivityLogsUiState(
    val logs: List<ActivityLog> = emptyList(),
    val isLoading: Boolean = true,
    val showErrorsOnly: Boolean = false
)
