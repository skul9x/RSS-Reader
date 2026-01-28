package com.skul9x.rssreader.utils

/**
 * Android implementation of AppLogger that delegates to the singleton ActivityLogger.
 */
class AndroidAppLogger : AppLogger {
    override fun log(tag: String, message: String, e: Throwable?) {
        if (e != null) {
            android.util.Log.e(tag, message, e)
        } else {
            android.util.Log.d(tag, message)
        }
    }

    override fun logError(tag: String, message: String, e: Throwable?) {
         android.util.Log.e(tag, message, e)
    }

    override fun logEvent(eventType: String, url: String, message: String, details: String, isError: Boolean) {
        ActivityLogger.log(eventType, url, message, if (details.isNotEmpty()) details else null, isError)
    }

    override fun logJsoup(url: String, isSuccess: Boolean, message: String) {
        ActivityLogger.logJsoupParse(url, isSuccess, message)
    }

    override fun logHttp(url: String, code: Int, message: String) {
        if (code >= 400 || message.isNotEmpty()) {
             if (code >= 400) {
                 ActivityLogger.logHttpError(url, "Status $code: $message")
             } else if (message.isNotEmpty()) {
                 ActivityLogger.logHttpSuccess(url, code) // Or generic log
             }
        }
    }
}
