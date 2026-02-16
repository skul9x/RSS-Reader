package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.utils.ActivityLogger
import com.skul9x.rssreader.utils.AndroidHtmlCleaner
import com.skul9x.rssreader.utils.AndroidAppLogger
import com.skul9x.rssreader.utils.AppLogger
import com.skul9x.rssreader.utils.HtmlCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Fetches full article content from news website URLs.
 * Supports VnExpress and generic article extraction.
 *
 * Refactored to use a Facade pattern over specialized extractors.
 */
class ArticleContentFetcher(
    private val htmlCleaner: HtmlCleaner = AndroidHtmlCleaner(),
    private val logger: AppLogger = AndroidAppLogger()
) {
    companion object {
        private const val TAG = "ArticleContentFetcher"
    }

    private val htmlFetcher = HtmlFetcher(logger)
    private val redirectResolver = RedirectResolver(logger, htmlCleaner)
    private val titleExtractor = TitleExtractor(logger)
    private val htmlSanitizer = HtmlSanitizer()
    // Registry initialization order matters if it depends on others
    private val extractorRegistry = ContentExtractorRegistry(htmlCleaner, logger, redirectResolver)



    /**
     * Fetch full article content from a given URL.
     * Returns the extracted article text, or null if extraction fails.
     * Supports cancellation - HTTP request will be cancelled when coroutine is cancelled.
     */
    suspend fun fetchArticleContent(url: String): String? {
        val result = fetchArticleWithTitle(url)
        return result?.content
    }
    
    /**
     * Fetch article with both title and content.
     * Returns ArticleData with title extracted from HTML <title> or og:title meta tag.
     * Handles cookie-based anti-bot protection by detecting and retrying with extracted cookie.
     * Handles "soft redirects" (e.g. AI-Hay wrapper pages).
     */
    suspend fun fetchArticleWithTitle(url: String, redirectCount: Int = 0): ArticleData? {
        if (redirectCount > 3) {
            logger.logEvent(TAG, url, "Too many redirects", "", true)
            return null
        }

        // 1. Fetch HTML
        val (html, extractedCookie) = htmlFetcher.fetchHtmlWithCookieDetection(url, null)
        
        if (html == null) return null
        
        // 2. Anti-bot Retry (Cookie)
        if (extractedCookie != null && html.length < 500 && HtmlFetcher.RELOAD_PATTERN.containsMatchIn(html)) {
            logger.logEvent(
                eventType = "COOKIE_BYPASS",
                url = url,
                message = "Phát hiện anti-bot, retry với cookie",
                details = "Cookie: ${extractedCookie.take(30)}...",
                isError = false
            )
            
            val (retryHtml, _) = htmlFetcher.fetchHtmlWithCookieDetection(url, extractedCookie)
            if (retryHtml != null && retryHtml.length > 500) {
                return extractArticleFromHtml(retryHtml, url)
            }
        }
        
        // 3. Special Redirects (AI-Hay)
        if (url.contains("ai-hay.vn")) {
            val redirectUrl = redirectResolver.extractAiHayRedirect(html)
            if (redirectUrl != null && redirectUrl != url) {
                logger.logEvent(
                    eventType = "REDIRECT", 
                    url = url, 
                    message = "Phát hiện redirect từ AI-Hay", 
                    details = "Target: $redirectUrl",
                    isError = false
                )
                return fetchArticleWithTitle(redirectUrl, redirectCount + 1)
            }
        }

        // 4. Parse HTML
        val doc = try {
            Jsoup.parse(html, url)
        } catch (e: Exception) {
            logger.logJsoup(url, false, "Initial Jsoup parse failed: ${e.message}")
            return null
        }

        // 5. Special Redirects (Voz)
        if (url.contains("voz.vn")) {
            val redirectUrl = redirectResolver.extractVozOriginalLink(doc, url)
            if (redirectUrl != null && redirectUrl != url) {
                logger.logEvent(
                    eventType = "REDIRECT", 
                    url = url, 
                    message = "Phát hiện redirect từ Voz.vn", 
                    details = "Target: $redirectUrl",
                    isError = false
                )
                return fetchArticleWithTitle(redirectUrl, redirectCount + 1)
            }

            // Fallback: If no link, check if there is a QUOTE block in the first post
            val quoteArticle = redirectResolver.extractVozQuoteAsArticle(doc, url)
            if (quoteArticle != null) {
                return quoteArticle
            }
        }

        return extractArticleFromDocument(doc, html, url)
    }

    /**
     * Extract article data from HTML after fetching.
     * Uses pre-parsed Document to save CPU.
     */
    private suspend fun extractArticleFromDocument(doc: org.jsoup.nodes.Document, html: String, url: String): ArticleData? {
        if (html.length < 500) {
            logger.logJsoup(url, false, "HTML too short: ${html.length} bytes")
            return null
        }
        
        return try {
            withContext(Dispatchers.Default) {
                val title = titleExtractor.extractTitle(doc, url)
                val content = extractorRegistry.extractContent(doc, html, url)
                
                if (content != null && content.length > 50) {
                    logger.logJsoup(url, true, "Content length: ${content.length} chars")
                    ArticleData(title, content)
                } else {
                    logger.logJsoup(url, false, 
                        "Content too short or null. HTML length: ${html.length}, Content length: ${content?.length ?: 0}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.logJsoup(url, false, 
                "Jsoup exception: ${e.javaClass.simpleName}: ${e.message}")
            logger.logError(TAG, "Jsoup parsing error", e)
            null
        }
    }

    /**
     * Kept for backward compatibility or when Document is not available.
     * Delegates to extractArticleFromDocument after parsing.
     */
    private suspend fun extractArticleFromHtml(html: String, url: String): ArticleData? {
        val doc = try {
            Jsoup.parse(html, url)
        } catch (e: Exception) {
            return null
        }
        return extractArticleFromDocument(doc, html, url)
    }
    
    fun setCustomSelectorManager(manager: com.skul9x.rssreader.data.local.CustomSelectorManager) {
        extractorRegistry.setCustomSelectorManager(manager)
    }

    /**
     * Scans the HTML to find potential main content containers.
     * Delegates to Registry.
     */
    fun scanForPotentialContainers(html: String): List<ContentCandidate> {
        return extractorRegistry.scanForPotentialContainers(html)
    }

    /**
     * Debugging tool: Extract content from raw HTML string directly.
     */
    suspend fun analyzeRawHtml(html: String, mockUrl: String = "https://debug.local/article"): ArticleData? {
        return extractArticleFromHtml(html, mockUrl)
    }

    /**
     * Debugging tool: Fetch raw HTML from a URL regardless of extraction success.
     */
    suspend fun fetchRawHtml(url: String): String? {
        return htmlFetcher.fetchRawHtml(url)
    }

    /**
     * Clean HTML for AI analysis.
     */
    fun cleanHtmlForAi(html: String): String {
        return htmlSanitizer.cleanHtmlForAi(html)
    }

    /**
     * Cleanup resources when the Composable is disposed.
     */
    fun cleanup() {
        // Clear references in registry if needed
        // customSelectorManager is inside registry now
        logger.log(TAG, "ArticleContentFetcher cleanup completed")
    }
}
