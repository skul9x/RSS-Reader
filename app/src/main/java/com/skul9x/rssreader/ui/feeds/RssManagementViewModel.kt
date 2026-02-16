package com.skul9x.rssreader.ui.feeds

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.model.RssFeed
import com.skul9x.rssreader.data.repository.RssRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for RSS feed management screen.
 * Handles CRUD operations and validation.
 */
class RssManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = RssRepository(database.rssFeedDao(), database.cachedNewsDao(), database.readNewsDao())

    // All feeds as StateFlow for reactive UI
    val feeds: StateFlow<List<RssFeed>> = repository.getAllFeeds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _editingFeed = MutableStateFlow<RssFeed?>(null)
    val editingFeed: StateFlow<RssFeed?> = _editingFeed.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Show add feed dialog.
     */
    fun showAddFeedDialog() {
        _editingFeed.value = null
        _showAddDialog.value = true
    }

    /**
     * Show edit dialog for existing feed.
     */
    fun editFeed(feed: RssFeed) {
        _editingFeed.value = feed
        _showAddDialog.value = true
    }

    /**
     * Dismiss add/edit dialog.
     */
    fun dismissDialog() {
        _showAddDialog.value = false
        _editingFeed.value = null
    }

    /**
     * Save a new or edited feed.
     */
    fun saveFeed(name: String, url: String) {
        viewModelScope.launch {
            // Validation
            if (name.isBlank()) {
                _error.value = "Tên không được để trống"
                return@launch
            }
            if (url.isBlank()) {
                _error.value = "URL không được để trống"
                return@launch
            }
            if (!isValidUrl(url)) {
                _error.value = "URL không hợp lệ"
                return@launch
            }

            try {
                val existing = _editingFeed.value
                if (existing != null) {
                    // Update existing
                    repository.updateFeed(existing.copy(name = name, url = url))
                } else {
                    // Add new
                    repository.addFeed(name, url)
                }
                dismissDialog()
            } catch (e: Exception) {
                _error.value = "Lỗi khi lưu: ${e.message}"
            }
        }
    }

    /**
     * Delete a feed.
     */
    fun deleteFeed(feed: RssFeed) {
        viewModelScope.launch {
            try {
                repository.deleteFeed(feed)
            } catch (e: Exception) {
                _error.value = "Lỗi khi xóa: ${e.message}"
            }
        }
    }

    /**
     * Toggle feed enabled/disabled.
     */
    fun toggleFeedEnabled(feed: RssFeed) {
        viewModelScope.launch {
            try {
                repository.toggleFeedEnabled(feed.id, !feed.enabled)
            } catch (e: Exception) {
                _error.value = "Lỗi: ${e.message}"
            }
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val trimmed = url.trim()
            trimmed.startsWith("http://") || trimmed.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
}
