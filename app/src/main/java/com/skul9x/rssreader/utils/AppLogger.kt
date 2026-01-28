package com.skul9x.rssreader.utils

/**
 * Interface for application logging to allow swapping implementations for testing.
 */
interface AppLogger {
    fun log(tag: String, message: String, e: Throwable? = null)
    fun logError(tag: String, message: String, e: Throwable? = null)
    fun logEvent(eventType: String, url: String, message: String, details: String = "", isError: Boolean = false)
    fun logJsoup(url: String, isSuccess: Boolean, message: String)
    fun logHttp(url: String, code: Int = 0, message: String = "")
}
