package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HtmlFetcher(private val logger: AppLogger) {

    companion object {
        private const val TAG = "HtmlFetcher"
        
        // Cookie extraction pattern for anti-bot protection
        val COOKIE_SET_REGEX = Regex("""document\.cookie\s*=\s*["']([^"']+)["']""")
        val RELOAD_PATTERN = Regex("""window\.location\.reload""")
    }

    // Use shared OkHttpClient singleton
    private val client = NetworkClient.okHttpClient

    /**
     * Fetch HTML and detect cookie-based protection.
     * Returns pair of (html, extractedCookie).
     */
    suspend fun fetchHtmlWithCookieDetection(url: String, cookie: String?): Pair<String?, String?> {
        return try {
            // Check if already cancelled before making request
            coroutineContext.ensureActive()
            
            // Log HTTP request start
            logger.logEvent("HTTP_REQUEST", url, "Gửi HTTP request", "", false)
            
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Ch-Ua", "\"Microsoft Edge\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Upgrade-Insecure-Requests", "1")
                .apply {
                    // Add cookie if provided (for anti-bot bypass)
                    if (cookie != null) {
                        header("Cookie", cookie)
                    }
                }
                .build()

            // Use suspendCancellableCoroutine for proper cancellation
            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = client.newCall(request)
                
                // Cancel HTTP request when coroutine is cancelled
                continuation.invokeOnCancellation {
                    logger.log(TAG, "Article fetch cancelled for: $url")
                    call.cancel()
                }
                
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        logger.logHttp(url, 0, "IOException: ${e.message}")
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(e)
                        }
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isCompleted) {
                            continuation.resume(response) { cause ->
                                response.close()
                            }
                        } else {
                            // Continuation already completed (e.g., cancelled), must close response
                            response.close()
                        }
                    }
                })
            }
            
            // Read response body on IO thread to avoid NetworkOnMainThreadException
            withContext(Dispatchers.IO) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        logger.logHttp(url, resp.code, "Success")
                        val html = resp.body?.string()
                        if (html != null && html.isNotBlank()) {
                            // Log HTML preview for debugging
                            val htmlPreview = html.take(200).replace("\n", " ").replace("\r", "")
                            logger.logEvent(
                                eventType = "HTML_RECEIVED",
                                url = url,
                                message = "Nhận HTML: ${html.length} bytes",
                                details = "Preview: $htmlPreview...",
                                isError = false
                            )
                            
                            // Try to extract cookie from anti-bot script
                            val extractedCookie = COOKIE_SET_REGEX.find(html)?.groupValues?.getOrNull(1)
                            
                            // Check for Cloudflare/Perplexity Challenge
                            // Only check if page is small (real challenges are usually < 15KB)
                            // OR if specifically titled "Just a moment..."
                            val isCloudflare = if (html.length < 20000) {
                                html.contains("Enable JavaScript and cookies to continue") || 
                                html.contains("Verify you are human") ||
                                (html.contains("Just a moment...") && !html.contains("voz.vn")) || // Voz has "Just a moment" in content sometimes
                                html.contains("challenge-platform")
                            } else {
                                // For large pages, only if title is exactly "Just a moment..."
                                html.contains("<title>Just a moment...</title>")
                            }

                            if (isCloudflare) {
                                logger.logEvent(
                                    eventType = "CLOUDFLARE_DETECTED",
                                    url = url,
                                    message = "Phát hiện chặn Anti-Bot (Cloudflare)",
                                    details = "Page size: ${html.length} bytes. Cần mở bằng trình duyệt",
                                    isError = true
                                )
                                // Return null/special error to trigger fallback
                                logger.logJsoup(url, false, "Cloudflare Challenge Detected")
                                Pair(null, null)
                            } else {
                                Pair(html, extractedCookie)
                            }
                        } else {
                            logger.logJsoup(url, false, 
                                "Response body empty or null. Length: ${html?.length ?: 0}")
                            Pair(null, null)
                        }
                    } else {
                        logger.logHttp(url, resp.code, "HTTP ${resp.code} - ${resp.message}")
                        logger.log(TAG, "HTTP ${resp.code}")
                        Pair(null, null)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.log(TAG, "Article fetch cancelled")
            throw e  // Re-throw to propagate cancellation
        } catch (e: java.net.SocketTimeoutException) {
            logger.logHttp(url, 0, "Timeout: ${e.message}")
            logger.logError(TAG, "Timeout fetching article", e)
            Pair(null, null)
        } catch (e: java.net.UnknownHostException) {
            logger.logHttp(url, 0, "DNS failed: ${e.message}")
            logger.logError(TAG, "DNS error", e)
            Pair(null, null)
        } catch (e: javax.net.ssl.SSLException) {
            logger.logHttp(url, 0, "SSL error: ${e.message}")
            logger.logError(TAG, "SSL error", e)
            Pair(null, null)
        } catch (e: java.io.IOException) {
            logger.logHttp(url, 0, "IO error: ${e.javaClass.simpleName}: ${e.message}")
            logger.logError(TAG, "IO error fetching article", e)
            Pair(null, null)
        } catch (e: Exception) {
            logger.logEvent(
                eventType = "UNEXPECTED_ERROR",
                url = url,
                message = "Lỗi không xác định",
                details = "${e.javaClass.simpleName}: ${e.message}\nStack: ${e.stackTrace.take(3).joinToString("\n")}",
                isError = true
            )
            logger.logError(TAG, "Unexpected error fetching article", e)
            Pair(null, null)
        }
    }
    
    /**
     * Debugging tool: Fetch raw HTML from a URL regardless of extraction success.
     */
    suspend fun fetchRawHtml(url: String): String? {
        val (html, extractedCookie) = fetchHtmlWithCookieDetection(url, null)
        
        if (html != null && extractedCookie != null && html.length < 500 && RELOAD_PATTERN.containsMatchIn(html)) {
             // Anti-bot retry logic
             val (retryHtml, _) = fetchHtmlWithCookieDetection(url, extractedCookie)
             return retryHtml
        }
        return html
    }
}
