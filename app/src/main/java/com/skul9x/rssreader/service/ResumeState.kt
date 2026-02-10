package com.skul9x.rssreader.service

import com.skul9x.rssreader.data.model.NewsItem


/**
 * State for resuming interrupted TTS playback.
 * Stored in memory only (session-scoped).
 */
data class ResumeState(
    /** Whether there's content that can be resumed */
    val isResumable: Boolean = false,
    
    /** Index of the news item in the current list */
    val newsIndex: Int = -1,
    
    /** Title of the news for UI display */
    val newsTitle: String? = null,
    
    /** All sentences to be read */
    val sentences: List<String> = emptyList(),
    
    /** Index of the next sentence to read (resume point) */
    val currentSentenceIndex: Int = 0,
    
    /** Whether this was from Read All mode */
    val isReadAllMode: Boolean = false,
    
    /** Whether reading full content (vs summary) */
    val readFullContent: Boolean = false,
    
    /** The full text (for progress calculation) */
    val fullText: String = "",
    
    /** The NewsItem object itself (needed if service restarts and loses list) */
    val newsItem: NewsItem? = null
)
