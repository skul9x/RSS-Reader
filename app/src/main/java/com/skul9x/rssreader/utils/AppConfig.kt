package com.skul9x.rssreader.utils

/**
 * Application-wide configuration checks and constants.
 */
object AppConfig {
    /**
     * Number of days to keep read history before cleanup.
     * Unified to 60 days across all repositories and sync logic.
     */
    const val READ_HISTORY_RETENTION_DAYS = 60

    /**
     * Whether to show the Debug Log (Media Buttons) section in Settings.
     * Default is false to keep the UI clean for users.
     */
    const val SHOW_DEBUG_LOG = true
}
