package com.skul9x.rssreader.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton OkHttpClient for the entire app.
 * Provides a single, shared HTTP client instance for better performance via connection pooling.
 */
object NetworkClient {

    /**
     * Shared OkHttpClient instance.
     * - Connection pooling: Reuses TCP connections to the same hosts.
     * - Thread-safe: OkHttpClient is designed to be shared.
     * - Configured for news fetching and API calls.
     */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
