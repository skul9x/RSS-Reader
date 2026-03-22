package com.skul9x.rssreader.ui.settings.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.skul9x.rssreader.data.local.entity.FirebaseLog
import com.skul9x.rssreader.data.repository.FirebaseLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FirebaseLogViewModel(
    private val repository: FirebaseLogRepository
) : ViewModel() {

    private val _filterType = MutableStateFlow<String?>(null) // null = All, "ERROR", "SUCCESS"

    val uiState: StateFlow<FirebaseLogUiState> = combine(
        repository.getAllLogs(),
        _filterType
    ) { logs, filter ->
        val filteredLogs = if (filter == null) {
            logs
        } else {
            logs.filter { it.type == filter }
        }
        FirebaseLogUiState(
            logs = filteredLogs,
            currentFilter = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FirebaseLogUiState()
    )

    fun setFilter(type: String?) {
        _filterType.value = type
    }
    
    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    // Factory to inject repository
    class Factory(private val repository: FirebaseLogRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FirebaseLogViewModel::class.java)) {
                return FirebaseLogViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class FirebaseLogUiState(
    val logs: List<FirebaseLog> = emptyList(),
    val currentFilter: String? = null
)
