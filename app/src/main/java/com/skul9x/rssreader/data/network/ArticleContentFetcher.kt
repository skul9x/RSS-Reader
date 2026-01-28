package com.skul9x.rssreader.data.network

import android.util.Log
import com.skul9x.rssreader.utils.ActivityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

/**
 * Fetches full article content from news website URLs.
 * Supports VnExpress and generic article extraction.
 */
class ArticleContentFetcher(
    private val htmlCleaner: com.skul9x.rssreader.utils.HtmlCleaner = com.skul9x.rssreader.utils.AndroidHtmlCleaner(),
    private val logger: com.skul9x.rssreader.utils.AppLogger = com.skul9x.rssreader.utils.AndroidAppLogger()
) {
    companion object {
        private const val TAG = "ArticleContentFetcher"
        
        // Pre-compiled Regex patterns for performance (compiled once, reused many times)
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Genk patterns
        private val GENK_SAPO_REGEX = Regex("""<h2[^>]*class="[^"]*knc-sapo[^"]*"[^>]*>(.*?)</h2>""", REGEX_OPTIONS)
        private val GENK_CONTENT_REGEX = Regex("""<div[^>]*(?:class="[^"]*knc-content[^"]*"|id="ContentDetail")[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        
        // VnExpress patterns
        private val VNEXPRESS_DESCRIPTION_REGEX = Regex("""<meta[^>]*property="og:description"[^>]*content="([^"]+)"[^>]*>""")
        private val VNEXPRESS_FCK_DETAIL_REGEX = Regex("""<(?:article|div)[^>]*class="[^"]*fck_detail[^"]*"[^>]*>(.*?)</(?:article|div)>""", REGEX_OPTIONS)
        private val VNEXPRESS_NORMAL_PARAGRAPH_REGEX = Regex("""<p[^>]*class="[^"]*Normal[^"]*"[^>]*>(.*?)</p>""", REGEX_OPTIONS)
        
        // Tuoi Tre pattern
        private val TUOITRE_SAPO_REGEX = Regex("""<h2.*?data-role="sapo".*?>(.*?)</h2>""", REGEX_OPTIONS)
        private val TUOITRE_BODY_START_REGEX = Regex("""<div.*?data-role="content".*?>""", REGEX_OPTIONS)
        private val TUOITRE_BODY_REGEX = Regex("""<div.*?id="main-detail-body".*?>(.*?)</div>""", REGEX_OPTIONS)
        
        // Thanh Nien pattern
        private val THANHNIEN_BODY_REGEX = Regex("""<div[^>]*(?:id="abody"|id="cms-body"|class="[^"]*detail-content[^"]*")[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        
        // Dan Tri pattern
        private val DANTRI_BODY_REGEX = Regex("""<div[^>]*class="[^"]*singular-content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)

        // Nguoi Quan Sat pattern
        private val NGUOIQUANSAT_SAPO_REGEX = Regex("""<p[^>]*class="[^"]*sc-longform-header-sapo[^"]*"[^>]*>(.*?)</p>""", REGEX_OPTIONS)
        private val NGUOIQUANSAT_BODY_START_REGEX = Regex("""<article[^>]*class="[^"]*entry[^"]*"[^>]*>""", REGEX_OPTIONS)

        // VietnamNet pattern
        private val VIETNAMNET_SAPO_REGEX = Regex("""<h2[^>]*class="[^"]*content-detail-sapo[^"]*"[^>]*>(.*?)</h2>""", REGEX_OPTIONS)
        private val VIETNAMNET_BODY_START_REGEX = Regex("""<div[^>]*id="maincontent"[^>]*>""", REGEX_OPTIONS)

        // VTC News pattern
        private val VTCNEWS_SAPO_REGEX = Regex("""<h2[^>]*class="[^"]*font18[^"]*bold[^"]*inline-nb[^"]*"[^>]*>(.*?)</h2>""", REGEX_OPTIONS)
        private val VTCNEWS_BODY_START_REGEX = Regex("""<div[^>]*class="[^"]*edittor-content[^"]*"[^>]*>""", REGEX_OPTIONS)
        
        // Generic patterns
        private val GENERIC_ARTICLE_REGEX = Regex("""<article[^>]*>(.*?)</article>""", REGEX_OPTIONS)
        private val GENERIC_ARTICLE_CONTENT_REGEX = Regex("""<div[^>]*class="[^"]*article[-_]?content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val GENERIC_POST_CONTENT_REGEX = Regex("""<div[^>]*class="[^"]*post[-_]?content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val GENERIC_ENTRY_CONTENT_REGEX = Regex("""<div[^>]*class="[^"]*entry[-_]?content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val GENERIC_CONTENT_BODY_REGEX = Regex("""<div[^>]*class="[^"]*content[-_]?body[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        
        // Shared patterns
        private val PARAGRAPH_REGEX = Regex("""<p[^>]*>(.*?)</p>""", REGEX_OPTIONS)
        
        // CleanHtml patterns
        private val SCRIPT_REGEX = Regex("""<script[^>]*>.*?</script>""", REGEX_OPTIONS)
        private val STYLE_REGEX = Regex("""<style[^>]*>.*?</style>""", REGEX_OPTIONS)
        private val WHITESPACE_REGEX = Regex("""\s+""")
        
        // Cookie extraction pattern for anti-bot protection
        private val COOKIE_SET_REGEX = Regex("""document\.cookie\s*=\s*["']([^"']+)["']""")
        private val RELOAD_PATTERN = Regex("""window\.location\.reload""")
    }

    // Use shared OkHttpClient singleton
    private val client = NetworkClient.okHttpClient

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
     * Data class containing both title and content from an article.
     */
    data class ArticleData(
        val title: String,
        val content: String
    )
    
    /**
     * Fetch article with both title and content.
     * Returns ArticleData with title extracted from HTML <title> or og:title meta tag.
     * Handles cookie-based anti-bot protection by detecting and retrying with extracted cookie.
     */
    /**
     * Fetch article with both title and content.
     * Returns ArticleData with title extracted from HTML <title> or og:title meta tag.
     * Handles cookie-based anti-bot protection by detecting and retrying with extracted cookie.
     * Handles "soft redirects" (e.g. AI-Hay wrapper pages).
     */
    suspend fun fetchArticleWithTitle(url: String, redirectCount: Int = 0): ArticleData? {
        if (redirectCount > 3) {
            logger.logEvent(TAG, url, "Too many redirects", isError = true)
            return null
        }

        // First attempt without cookie
        val (html, extractedCookie) = fetchHtmlWithCookieDetection(url, null)
        
        if (html == null) return null
        
        // Check if this is a cookie-set-and-reload page
        if (extractedCookie != null && html.length < 500 && RELOAD_PATTERN.containsMatchIn(html)) {
            logger.logEvent(
                eventType = "COOKIE_BYPASS",
                url = url,
                message = "Phát hiện anti-bot, retry với cookie",
                details = "Cookie: ${extractedCookie.take(30)}...",
                isError = false
            )
            
            // Retry with the extracted cookie
            val (retryHtml, _) = fetchHtmlWithCookieDetection(url, extractedCookie)
            if (retryHtml != null && retryHtml.length > 500) {
                return extractArticleFromHtml(retryHtml, url)
            }
        }
        
        // SPECIAL SUPPORT: AI-Hay Redirects
        if (url.contains("ai-hay.vn")) {
            val redirectUrl = extractAiHayRedirect(html)
            if (redirectUrl != null && redirectUrl != url) {
                // Determine title for the new URL if possible, or pass null to let it be extracted
                logger.logEvent(
                    eventType = "REDIRECT", 
                    url = url, 
                    message = "Phát hiện redirect từ AI-Hay", 
                    details = "Target: $redirectUrl"
                )
                return fetchArticleWithTitle(redirectUrl, redirectCount + 1)
            }
        }

        // Parse HTML once
        val doc = try {
            Jsoup.parse(html, url)
        } catch (e: Exception) {
            logger.logJsoup(url, false, "Initial Jsoup parse failed: ${e.message}")
            return null
        }

        // SPECIAL SUPPORT: Voz.vn Redirects
        if (url.contains("voz.vn")) {
            val redirectUrl = extractVozOriginalLink(doc, url)
            if (redirectUrl != null && redirectUrl != url) {
                logger.logEvent(
                    eventType = "REDIRECT", 
                    url = url, 
                    message = "Phát hiện redirect từ Voz.vn", 
                    details = "Target: $redirectUrl"
                )
                return fetchArticleWithTitle(redirectUrl, redirectCount + 1)
            }

            // Fallback: If no link, check if there is a QUOTE block in the first post
            // This is common for "Diem Bao" where the OP quotes the article content
            val quoteArticle = extractVozQuoteAsArticle(doc, url)
            if (quoteArticle != null) {
                return quoteArticle
            }
        }

        return extractArticleFromDocument(doc, html, url)
    }

    private fun extractAiHayRedirect(html: String): String? {
        // Check forceRedirect: "forceRedirect":"https://..."
        val forceRegex = Regex(""""forceRedirect":"([^"]+)"""")
        val forceMatch = forceRegex.find(html)
        if (forceMatch != null) {
            return forceMatch.groupValues[1].replace("\\/", "/")
        }

        // Check contentShare.url: "contentShare":{..."url":"https://..."}
        // This is a bit looser, looking for the url field inside the structure
        val shareUrlRegex = Regex(""""contentShare".*?"url":"([^"]+)"""")
        val shareMatch = shareUrlRegex.find(html)
        if (shareMatch != null) {
            return shareMatch.groupValues[1].replace("\\/", "/")
        }
        
        return null
    }

    /**
     * Extracts the original link from a Voz.vn forum post.
     * Looks for the first post and tries to find an unfurled link or external link.
     */
    private fun extractVozOriginalLink(doc: Document, url: String): String? {
        try {
            // Find first post in the thread
            val firstPost = doc.selectFirst(".message--post") ?: return null

            // Priority 1: Check for unfurl link (the preview box)
            val unfurlLink = firstPost.selectFirst(".bbCodeBlock--unfurl")?.attr("data-url")
            if (!unfurlLink.isNullOrBlank()) {
                return unfurlLink
            }

            // Priority 2: Check for explicit external link inside the post content
            // Exclude internal links, images, and attachments
            val externalLink = firstPost.select("a.link--external")
                .firstOrNull { 
                    !it.attr("href").contains("voz.vn") && 
                    !it.attr("href").startsWith("/") 
                }?.attr("href")
            
            if (!externalLink.isNullOrBlank()) {
                return externalLink
            }
        } catch (e: Exception) {
            logger.logEvent(TAG, url, "Error extracting Voz link: ${e.message}", isError = true)
        }
        return null
    }

    /**
     * Extracts content from a Quote block in Voz post #1.
     * Returns a complete ArticleData object if successful.
     */
    private fun extractVozQuoteAsArticle(doc: Document, url: String): ArticleData? {
        try {
            val firstPost = doc.selectFirst(".message--post") ?: return null
            
            // Find the quote block: .bbCodeBlock--quote
            val quoteBlock = firstPost.selectFirst(".bbCodeBlock--quote .bbCodeBlock-content") ?: return null
            
            // Extract text/html from the quote
            // We use html() to preserve formatting (br, b, i, etc) inside the quote
            val contentHtml = quoteBlock.html()
            
            if (contentHtml.isNotBlank()) {
                // Use the page title as the article title
                // Voz titles are usually: "Title... | vozForums"
                var title = doc.title()
                val separatorIndex = title.lastIndexOf("|")
                if (separatorIndex > 0) {
                    title = title.substring(0, separatorIndex).trim()
                }

                // Clean up the content slightly if needed, but keep structure
                val cleanContent = cleanHtml(contentHtml)

                return ArticleData(
                    title = title,
                    content = cleanContent
                )
            }
        } catch (e: Exception) {
            logger.logEvent(TAG, url, "Error extracting Voz quote: ${e.message}", isError = true)
        }
        return null
    }
    
    /**
     * Fetch HTML and detect cookie-based protection.
     * Returns pair of (html, extractedCookie).
     */
    private suspend fun fetchHtmlWithCookieDetection(url: String, cookie: String?): Pair<String?, String?> {
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
     * Extract article data from HTML after fetching.
     * Uses pre-parsed Document to save CPU.
     */
    private suspend fun extractArticleFromDocument(doc: Document, html: String, url: String): ArticleData? {
        if (html.length < 500) {
            logger.logJsoup(url, false, "HTML too short: ${html.length} bytes")
            return null
        }
        
        return try {
            withContext(Dispatchers.Default) {
                // We pass Doc for Jsoup extraction, but Html string might be needed for Regex fallback
                val title = extractTitle(doc, url)
                val content = extractContent(doc, html, url)
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
    
    /**
     * Extract page title from HTML.
     * Tries: og:title meta tag -> title tag -> fallback to domain
     */
    private fun extractTitle(doc: Document, url: String): String {
        return try {
            // 1. Try og:title (usually cleanest)
            val ogTitle = doc.select("meta[property=og:title]").attr("content")
            if (ogTitle.isNotBlank()) {
                return cleanTitle(ogTitle)
            }
            
            // 2. Try twitter:title
            val twitterTitle = doc.select("meta[name=twitter:title]").attr("content")
            if (twitterTitle.isNotBlank()) {
                return cleanTitle(twitterTitle)
            }
            
            // 3. Try <title> tag
            val htmlTitle = doc.title()
            if (htmlTitle.isNotBlank()) {
                return cleanTitle(htmlTitle)
            }
            
            // 4. Try h1 tag (first one)
            val h1 = doc.select("h1").first()?.text()
            if (!h1.isNullOrBlank()) {
                return cleanTitle(h1)
            }
            
            // 5. Fallback to domain name
            android.net.Uri.parse(url).host ?: "Bài viết"
        } catch (e: Exception) {
            logger.logError(TAG, "Error extracting title", e)
            "Bài viết"
        }
    }

    /**
     * @Deprecated Use extractTitle(doc, url) instead
     */
    private fun extractTitle(html: String, url: String): String {
        return try {
            val doc = Jsoup.parse(html)
            extractTitle(doc, url)
        } catch (e: Exception) {
             android.net.Uri.parse(url).host ?: "Bài viết"
        }
    }
    
    /**
     * Clean up title by removing common suffixes like " - VnExpress", " | Báo Thanh Niên"
     */
    private fun cleanTitle(title: String): String {
        // Common separators used by news sites
        val separators = listOf(" - ", " | ", " – ", " — ", " :: ", " // ")
        
        var cleaned = title.trim()
        
        // Remove site name suffix (usually after separator)
        for (separator in separators) {
            val lastIndex = cleaned.lastIndexOf(separator)
            if (lastIndex > 20) { // Only remove if there's enough content before
                cleaned = cleaned.substring(0, lastIndex).trim()
                break
            }
        }
        
        return cleaned
    }

    private var customSelectorManager: com.skul9x.rssreader.data.local.CustomSelectorManager? = null

    fun setCustomSelectorManager(manager: com.skul9x.rssreader.data.local.CustomSelectorManager) {
        this.customSelectorManager = manager
    }

    /**
     * Check if the URL belongs to a domain where we prefer Regex extraction over Jsoup.
     */
    private fun isRegexPreferred(url: String): Boolean {
        return url.contains("genk.vn") ||
               url.contains("vnexpress.net") ||
               url.contains("tuoitre.vn") ||
               url.contains("thanhnien.vn") ||
               url.contains("dantri.com.vn") ||
               url.contains("nguoiquansat.vn") ||
               url.contains("vietnamnet.vn") ||
               url.contains("vtcnews.vn")
    }

    /**
     * Extract content using standard Jsoup or RegexFallback based on domain.
     * With Phase 2 Optimization: Regex is tried first for supported domains!
     */
    private fun extractContent(doc: Document, html: String, url: String): String? {
        // 0. Try Custom Selector (User Defined)
        val customSelector = customSelectorManager?.getSelectorForUrl(url)
        if (customSelector != null) {
            try {
                // Clone/Clean strictly for custom selector? 
                // We should be careful modifying the passed 'doc' if it is used elsewhere?
                // For now, let's clone it if we are going to modify (remove elements)
                val workingDoc = doc.clone()
                workingDoc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news").remove()
                


            } catch (e: Exception) {
                logger.logError(TAG, "Custom selector extraction failed: $customSelector", e)
            }
        }

        // 1. OPTIMIZATION: Try Regex Priority for supported domains
        if (isRegexPreferred(url)) {
            val regexResult = when {
                url.contains("vnexpress.net") -> extractVnExpressContent(html)
                url.contains("genk.vn") -> extractGenkContent(html)
                url.contains("tuoitre.vn") -> extractTuoiTreContent(html)
                url.contains("thanhnien.vn") -> extractThanhNienContent(html)
                url.contains("dantri.com.vn") -> extractDanTriContent(html)
                url.contains("nguoiquansat.vn") -> extractNguoiQuanSatContent(html)
                url.contains("vietnamnet.vn") -> extractVietnamNetContent(html)
                url.contains("vtcnews.vn") -> extractVtcNewsContent(html)
                else -> null
            }
            
            if (regexResult != null && regexResult.length > 200) {
                logger.logJsoup(url, true, "Regex extraction success: ${regexResult.length} chars")
                return regexResult
            } else {
                logger.logJsoup(url, false, "Regex extraction failed/short, falling back to Jsoup")
            }
        }

        // 2. Try Jsoup extraction (Standard/Fallback)
        val jsoupContent = extractWithJsoup(doc, url)
        if (jsoupContent != null && jsoupContent.length > 200) {
             return jsoupContent
        }

        // 3. Fallback to Legacy methods for NON-RegexPreferred domains if Jsoup failed
        // (For RegexPreferred domains, we already tried them in Step 1)
        if (!isRegexPreferred(url)) {
             // This block might be redundant if we covered all cases in Step 1 & 2
             // But valid for cases where we added a fallback in Step 1 but didn't list it in isRegexPreferred?
             // Actually, for consistency, let's just stick to Jsoup or Generic extraction here.
             return extractGenericContent(html)
        }
        
        return null
    }

    /**
     * @Deprecated Use extractContent(doc, html, url)
     */
    private fun extractContent(html: String, url: String): String? {
        val doc = Jsoup.parse(html)
        return extractContent(doc, html, url)
    }
    
    /**
     * Structure for a potential content container found in HTML
     */
    data class ContentCandidate(
        val selector: String,
        val className: String,
        val idName: String,
        val textLength: Int,
        val previewText: String,
        val fullText: String,
        val score: Int
    )

    /**
     * Scans the HTML to find potential main content containers.
     * Returns a list of candidates sorted by likelihood (score).
     */
    fun scanForPotentialContainers(html: String): List<ContentCandidate> {
        return try {
            val doc = Jsoup.parse(html)
            
            // Remove noise to get accurate text length counts
            doc.select("script, style, nav, header, footer, .menu, .sidebar, .ads").remove()
            
            val candidates = mutableListOf<ContentCandidate>()
            
            // Find all block elements that might contain the article
            val elements = doc.select("div, article, section, main")
            
            for (el in elements) {
                val text = el.text()
                val length = text.length
                
                // Only consider substantial text blocks
                if (length > 300) {
                    val id = el.id()
                    val className = el.className()
                    
                    // Skip if no identifier (hard to target) unless it's <article> or <main>
                    if (id.isBlank() && className.isBlank() && el.tagName() !in listOf("article", "main")) {
                        continue
                    }
                    
                    // Construct a selector
                    val selector = when {
                        id.isNotBlank() -> "#$id"
                        className.isNotBlank() -> ".${className.replace(" ", ".")}" // Replace spaces with dots for combined classes
                        else -> el.tagName()
                    }
                    
                    // Simple scoring heuristic
                    var score = length
                    if (className.contains("body") || className.contains("content") || className.contains("article")) score += 500
                    if (id.contains("body") || id.contains("content")) score += 500
                    if (el.tagName() == "article") score += 300
                    
                    // Count paragraphs (strong indicator of article body)
                    val pCount = el.select("p").size
                    score += (pCount * 100)

                    val preview = text.take(100).replace("\n", " ").trim() + "..."
                    
                    // Store full text for preview (limit to 20KB to avoid memory issues)
                    val fullText = text.take(20000)
                    
                    candidates.add(ContentCandidate(selector, className, id, length, preview, fullText, score))
                }
            }
            
            // Deduplicate and sort by score descending
            candidates.distinctBy { it.selector }
                .sortedByDescending { it.score }
                .take(15) // Return top 15 results
            
        } catch (e: Exception) {
            logger.logError(TAG, "Error scanning HTML", e)
            emptyList()
        }
    }

    /**
     * Extract content using Jsoup (HTML Parser).
     * This is the most robust method as it parses the DOM correctly.
     */
    private fun extractWithJsoup(doc: Document, url: String): String? {
        return try {
            // Clone doc so we don't affect other extractors if we remove elements
            val workingDoc = doc.clone()
            
            // Remove unwanted elements globally
            workingDoc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news, .c-box").remove()

            val content = StringBuilder()

            when {
                url.contains("genk.vn") || url.contains("cafef.vn") -> {
                    // GenK / CafeF (desktop & mobile): Sapo + Content
                    // Mobile selectors: .detail-sapo, .detail-content, .singular-content
                    // Desktop selectors: .knc-sapo, #ContentDetail, .knc-content
                    val sapo = workingDoc.select(".knc-sapo, .detail-sapo, .sapo, h2.sapo").text()
                    if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")
                    
                    val body = workingDoc.select("#ContentDetail, .knc-content, .detail-content, .singular-content, .cms-body, article .content")
                    // Extract text from paragraphs only to be clean
                    val paragraphs = body.select("p").eachText()
                    if (paragraphs.isNotEmpty()) {
                        content.append(paragraphs.joinToString("\n\n"))
                    } else {
                        // Fallback: get whole text if no p tags
                        content.append(body.text())
                    }
                }
                url.contains("vnexpress.net") -> {
                    // VnExpress: Description + fck_detail
                    val desc = workingDoc.select("meta[property=og:description]").attr("content")
                    if (desc.isNotEmpty()) content.append(desc).append("\n\n")
                    
                    val body = workingDoc.select(".fck_detail")
                    val paragraphs = body.select("p.Normal").eachText()
                    if (paragraphs.isNotEmpty()) {
                        content.append(paragraphs.joinToString("\n\n"))
                    } else {
                        content.append(body.text())
                    }
                }
                url.contains("dantri.com.vn") -> {
                    // Dantri: Description + singular-content
                    val desc = workingDoc.select("meta[property=og:description]").attr("content")
                    if (desc.isNotEmpty()) content.append(desc).append("\n\n")

                    val body = workingDoc.select(".singular-content, .e-magazine__body, article")
                    if (body.hasText()) {
                         content.append(body.select("p").eachText().joinToString("\n\n"))
                    } else {
                        // Fallback for tricky layouts
                         val potentialBody = workingDoc.select("[data-role='content'], .dt-news__content")
                         if (potentialBody.hasText()) {
                             content.append(potentialBody.select("p").eachText().joinToString("\n\n"))
                         }
                    }
                }
                url.contains("thanhnien.vn") -> {
                    // Thanh Nien: Description + detail-content
                    val desc = workingDoc.select("meta[property=og:description]").attr("content")
                    if (desc.isNotEmpty()) content.append(desc).append("\n\n")

                    val body = workingDoc.select(".detail-content, .c-detail .c-detail__body, #cms-body")
                    if (body.hasText()) {
                        content.append(body.select("p").eachText().joinToString("\n\n"))
                    } else {
                         // Fallback
                         content.append(workingDoc.select("article").text())
                    }
                }
                url.contains("tuoitre.vn") -> {
                    val body = workingDoc.select("#main-detail-body")
                    content.append(body.select("p").eachText().joinToString("\n\n"))
                }
                url.contains("nguoiquansat.vn") -> {
                    // Nguoi Quan Sat
                    val sapo = workingDoc.select(".sc-longform-header-sapo").text()
                    if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")

                    val body = workingDoc.select(".entry, .normal-article-content")
                    // Remove header from body selection to avoid duplication
                    body.select(".sc-longform-header").remove()
                    
                    if (body.hasText()) {
                        content.append(body.select("p").eachText().joinToString("\n\n"))
                    }
                }
                url.contains("vietnamnet.vn") -> {
                    // VietnamNet Jsoup Fallback (if regex fails or we want consistent Jsoup path)
                    // Sapo
                    val sapo = workingDoc.select(".content-detail-sapo").text()
                    if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")
                    
                    // Body
                    // Exclude specific blocks inside maincontent that are not article text
                    val body = workingDoc.select("#maincontent")
                    body.select(".ck-cms-insert-neww-group, .insert-wiki-content, .article-relate, .inner-article").remove()
                    
                    if (body.hasText()) {
                         content.append(body.select("p").eachText().joinToString("\n\n"))
                    }
                }
                url.contains("vtcnews.vn") -> {
                    // VTC News
                    // Sapo is the h2 with inline-nb class
                    val sapo = workingDoc.select("h2.font18.bold.inline-nb").text()
                    if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")
                    
                    // Body from edittor-content
                    val body = workingDoc.select(".edittor-content")
                    // Remove related elements
                    body.select(".relate-listnews, .author-make, .keylink, .div-interaction, figure").remove()
                    
                    if (body.hasText()) {
                        content.append(body.select("p").eachText().joinToString("\n\n"))
                    }
                }
                url.contains("ai-hay.vn") -> {
                    // AI-Hay
                    // 1. Try SSR Selector (div.ai-answer-text)
                    val body = workingDoc.select(".ai-answer-text, .markdown-content, .ai-answer-text-md")
                    if (body.hasText()) {
                        // Try structured tags first
                        val structured = body.select("p, li").eachText().joinToString("\n\n")
                        if (structured.length > 100) {
                            content.append(structured)
                        } else {
                            // Fallback to whole text (preserves whitespace/newlines in text nodes)
                            content.append(body.map { it.wholeText() }.joinToString("\n\n"))
                        }
                    } 
                    
                    // 2. Fallback to JSON data if HTML extraction failed/empty
                    if (content.length < 50) {
                         val script = workingDoc.select("script#__global__").html()
                         if (script.isNotEmpty()) {
                             // Regex to extract text from listAnswerEntry
                             // Pattern looks for: "text":"(captured group)", inside the JSON
                             // We start simple. The text can contain escaped quotes, so be careful.
                             // JSON format: "listAnswerEntry":[{"type":13,"text":"...","
                             val regex = Regex(""""listAnswerEntry".*?"text":"(.*?)",""", RegexOption.DOT_MATCHES_ALL)
                             val match = regex.find(script)
                             if (match != null) {
                                 // Unescape JSON string (basic)
                                 val rawJson = match.groupValues[1]
                                 val unescaped = rawJson.replace("\\n", "\n")
                                     .replace("\\\"", "\"")
                                     .replace("\\t", "\t")
                                     .replace("\\\\", "\\")
                                 content.append(unescaped)
                             }
                         }
                    }
                }
                else -> {
                    // Generic Jsoup extraction
                    val potentialSelectors = listOf(
                        "article",
                        ".article-content",
                        ".post-content",
                        ".entry-content",
                        ".content-body",
                        "#content",
                        "main"
                    )
                    
                    for (selector in potentialSelectors) {
                        val element = workingDoc.select(selector)
                        if (element.hasText()) {
                            val text = element.select("p").eachText().joinToString("\n\n")
                            if (text.length > 200) {
                                content.append(text)
                                break
                            }
                        }
                    }
                }
            }

            val result = content.toString().trim()
            if (result.length > 50) result else null
        } catch (e: Exception) {
            logger.logError(TAG, "Jsoup extraction failed", e)
            null
        }
    }

    /**
     * @Deprecated Use extractWithJsoup(doc, url)
     */
    private fun extractWithJsoup(html: String, url: String): String? {
        val doc = Jsoup.parse(html)
        return extractWithJsoup(doc, url)
    }

    /**
     * Extract content from Genk.vn articles.
     * Legacy Strategies (Fallback):
     * 1. Sapo (Header) separation
     * 2. Marker-based extraction (Robust for nested divs)
     * 3. Regex-based extraction (Legacy/Fallback)
     */
    private fun extractGenkContent(html: String): String? {
        val content = StringBuilder()
        
        // 1. Extract sapo (summary)
        val sapoMatch = GENK_SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(cleanHtml(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }
        
        var bodyHtml: String? = null
        
        // 2. Strategy A: Marker-based (Start/End strings)
        val startMarker1 = "id=\"ContentDetail\""
        val startMarker2 = "class=\"knc-content"
        
        var startIndex = html.indexOf(startMarker1)
        if (startIndex == -1) startIndex = html.indexOf(startMarker2)
        
        if (startIndex != -1) {
            val endMarker1 = "class=\"link-source-wrapper\""
            val endMarker2 = "data-check-position=\"genk_body_end\""
            
            val endIndex1 = html.indexOf(endMarker1, startIndex)
            val endIndex2 = html.indexOf(endMarker2, startIndex)
            
            val endIndex = if (endIndex1 != -1 && endIndex2 != -1) {
                if (endIndex1 < endIndex2) endIndex1 else endIndex2
            } else if (endIndex1 != -1) {
                endIndex1
            } else {
                endIndex2
            }
            
            if (endIndex != -1) {
                bodyHtml = html.substring(startIndex, endIndex)
            }
        }
        
        // 3. Strategy B: Regex-based (Fallback)
        if (bodyHtml == null) {
            val contentMatch = GENK_CONTENT_REGEX.find(html)
            if (contentMatch != null) {
                bodyHtml = contentMatch.groupValues[1]
            }
        }
        
        // Process the body HTML if found by either strategy
        if (bodyHtml != null) {
            val paragraphs = PARAGRAPH_REGEX
                .findAll(bodyHtml)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }
            
            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        // 4. Strategy C: Ultimate Fallback (Whole page scan)
        if (content.toString().trim().length < 200) {
            val allParagraphs = PARAGRAPH_REGEX
                .findAll(html)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 50 }
            
            val joinedAll = allParagraphs.joinToString("\n\n")
            if (joinedAll.isNotBlank()) {
                content.clear() // Clear any partial garbage
                content.append(joinedAll)
            }
        }
        
        val result = content.toString().trim()
        return if (result.length > 100) result else null
    }

    /**
     * Extract content from VnExpress articles.
     * Legacy Strategies (Fallback):
     * 1. Meta Description
     * 2. Marker-based
     * 3. Regex-based
     */
    private fun extractVnExpressContent(html: String): String? {
        val content = StringBuilder()
        
        // 1. Extract description from meta tag og:description
        val descriptionMatch = VNEXPRESS_DESCRIPTION_REGEX.find(html)
        if (descriptionMatch != null) {
            content.append(descriptionMatch.groupValues[1])
            content.append("\n\n")
        }
        
        var bodyHtml: String? = null
        
        // 2. Strategy A: Marker-based
        val startMarker = "class=\"fck_detail"
        val startIndex = html.indexOf(startMarker)
        
        if (startIndex != -1) {
            // Find end marker (usually the author or the end of the container)
            // Note: VnExpress author is usually in <p class="author"> or <span class="author">
            // But sometimes it's just the end of the fck_detail div which is hard to find without parsing
            // So we look for the next distinct section usually found after content
            
            val endMarker1 = "class=\"author\"" // Common author tag
            val endMarker2 = "class=\"width_common\"" // Often used for footer/related news containers
            val endMarker3 = "id=\"box_comment\"" // Comments section
            
            var endIndex = -1
            
            // Find the earliest valid end marker after start
            val potentials = listOf(endMarker1, endMarker2, endMarker3)
            var minEndIndex = Int.MAX_VALUE
            
            for (marker in potentials) {
                val idx = html.indexOf(marker, startIndex)
                if (idx != -1 && idx < minEndIndex) {
                    minEndIndex = idx
                }
            }
            
            if (minEndIndex != Int.MAX_VALUE) {
                endIndex = minEndIndex
            }
            
            if (endIndex != -1) {
                bodyHtml = html.substring(startIndex, endIndex)
            }
        }
        
        // 3. Strategy B: Regex-based (Fallback)
        if (bodyHtml == null) {
            val fckMatch = VNEXPRESS_FCK_DETAIL_REGEX.find(html)
            if (fckMatch != null) {
                bodyHtml = fckMatch.groupValues[1]
            }
        }
        
        // Process the body HTML if found
        if (bodyHtml != null) {
            // VnExpress places content in <p class="Normal">
            // But sometimes just <p> inside the fck_detail
            val paragraphs = PARAGRAPH_REGEX
                .findAll(bodyHtml)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }
            
            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        // 4. Strategy C: Ultimate Fallback (Whole page scan for class="Normal")
        if (content.toString().trim().length < 200) {
            val allNormalParagraphs = VNEXPRESS_NORMAL_PARAGRAPH_REGEX
                .findAll(html)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }
            
            val joinedAll = allNormalParagraphs.joinToString("\n\n")
            if (joinedAll.isNotBlank()) {
                content.clear() // Clear any partial garbage
                content.append(joinedAll)
            }
        }
        
        val result = content.toString().trim()
        return if (result.length > 100) result else null
    }

    /**
     * Extract content from Tuoi Tre articles.
     * Strategies:
     * 1. Sapo from data-role="sapo"
     * 2. Content from data-role="content" (using marker-based extraction)
     */
    private fun extractTuoiTreContent(html: String): String? {
        val content = StringBuilder()

        // 1. Extract Sapo
        val sapoMatch = TUOITRE_SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(cleanHtml(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }

        // 2. Extract Body
        // Method: Find start marker, then extract paragraphs until end marker
        val startMatch = TUOITRE_BODY_START_REGEX.find(html)
        var bodyHtml: String? = null

        if (startMatch != null) {
            val startIndex = startMatch.range.first
            // End markers for Tuoi Tre to safely stop extraction
            val endMarkers = listOf(
                "class=\"detail__related\"",
                "class=\"detail-author-bot\"",
                "class=\"comment-wrapper\"",
                "id=\"tag-container\""
            )

            var endIndex = -1
            var minEndIndex = Int.MAX_VALUE

            for (marker in endMarkers) {
                val idx = html.indexOf(marker, startIndex)
                if (idx != -1 && idx < minEndIndex) {
                    minEndIndex = idx
                }
            }

            if (minEndIndex != Int.MAX_VALUE) {
                endIndex = minEndIndex
            }

            if (endIndex != -1) {
                bodyHtml = html.substring(startIndex, endIndex)
            }
        } else {
             // Fallback: Try regex if marker fails
             val match = TUOITRE_BODY_REGEX.find(html)
             if (match != null) {
                 bodyHtml = match.groupValues[1]
             }
        }

        if (bodyHtml != null) {
            val paragraphs = PARAGRAPH_REGEX
                .findAll(bodyHtml)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }

            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }

        return if (content.length > 100) content.toString() else null
    }

    /**
     * Extract content from Nguoi Quan Sat articles.
     * Uses Sapo Regex and Marker-based body extraction.
     */
    private fun extractNguoiQuanSatContent(html: String): String? {
        val content = StringBuilder()

        // 1. Extract Sapo
        val sapoMatch = NGUOIQUANSAT_SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(cleanHtml(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }

        // 2. Extract Body using Sapo-Content separation or Markers
        // Body usually starts with <article class="entry
        val startMatch = NGUOIQUANSAT_BODY_START_REGEX.find(html)
        var bodyHtml: String? = null

        if (startMatch != null) {
            val startIndex = startMatch.range.first
            val endMarkers = listOf(
                "class=\"sc-empty-layer\"",
                "class=\"c-related-posts\"",
                "</article>"
            )

            var endIndex = -1
            var minEndIndex = Int.MAX_VALUE

            for (marker in endMarkers) {
                val idx = html.indexOf(marker, startIndex)
                if (idx != -1 && idx < minEndIndex) {
                    minEndIndex = idx
                }
            }
            if (minEndIndex != Int.MAX_VALUE) endIndex = minEndIndex

            if (endIndex != -1) {
                bodyHtml = html.substring(startIndex, endIndex)
            }
        }

        if (bodyHtml != null) {
            // Extract paragraphs to avoid ads
            val paragraphs = PARAGRAPH_REGEX
                .findAll(bodyHtml)
                .map { cleanHtml(it.groupValues[1]) }
                // Remove the sapo if it was captured again (Sapo is a p tag in the body)
                .filter { p -> 
                    val sapoText = sapoMatch?.groupValues?.get(1)?.let { cleanHtml(it) } ?: ""
                    p.isNotBlank() && p.length > 20 && p != sapoText
                }

            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        return if (content.length > 100) content.toString() else null
    }

    /**
     * Extract content from VietnamNet articles.
     * Uses Sapo Regex and Marker-based body extraction.
     */
    private fun extractVietnamNetContent(html: String): String? {
        val content = StringBuilder()

        // 1. Extract Sapo
        val sapoMatch = VIETNAMNET_SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(cleanHtml(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }

        // 2. Extract Body
        val startMatch = VIETNAMNET_BODY_START_REGEX.find(html)
        var bodyHtml: String? = null

        if (startMatch != null) {
            val startIndex = startMatch.range.first
            // End markers to safely stop extraction before related news/ads
            val endMarkers = listOf(
                "class=\"ck-cms-insert-neww-group\"",
                "class=\"vnn-template-noneditable\"", 
                "class=\"article-relate\"",
                "class=\"vnn-share-social\"",
                "id=\"adzone"
            )

            var endIndex = -1
            var minEndIndex = Int.MAX_VALUE

            for (marker in endMarkers) {
                val idx = html.indexOf(marker, startIndex)
                if (idx != -1 && idx < minEndIndex) {
                    minEndIndex = idx
                }
            }

            if (minEndIndex != Int.MAX_VALUE) {
                endIndex = minEndIndex
            }

            if (endIndex != -1) {
                bodyHtml = html.substring(startIndex, endIndex)
            } else {
                 // Fallback: take a reasonable chunk if no end marker found
                 bodyHtml = html.substring(startIndex).take(30000)
            }
        }

        if (bodyHtml != null) {
            val paragraphs = PARAGRAPH_REGEX
                .findAll(bodyHtml)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }

            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        return if (content.length > 100) content.toString() else null
    }

    /**
     * Extract content from VTC News articles.
     * Uses Sapo and edittor-content div for body extraction.
     */
    private fun extractVtcNewsContent(html: String): String? {
        val content = StringBuilder()

        // 1. Extract Sapo - VTC uses inline-nb h2 for description
        val sapoMatch = VTCNEWS_SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(cleanHtml(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }

        // 2. Extract Body from edittor-content div
        val startMatch = VTCNEWS_BODY_START_REGEX.find(html)
        var bodyHtml: String? = null

        if (startMatch != null) {
            val startIndex = startMatch.range.first
            // End markers to stop extraction before related news/ads
            val endMarkers = listOf(
                "class=\"relate-listnews\"",
                "class=\"share-social\"",
                "class=\"author-make\"",
                "class=\"keylink\"",
                "class=\"div-interaction\""
            )

            var endIndex = -1
            var minEndIndex = Int.MAX_VALUE

            for (marker in endMarkers) {
                val idx = html.indexOf(marker, startIndex)
                if (idx != -1 && idx < minEndIndex) {
                    minEndIndex = idx
                }
            }

            if (minEndIndex != Int.MAX_VALUE) {
                endIndex = minEndIndex
            }

            if (endIndex != -1) {
                bodyHtml = html.substring(startIndex, endIndex)
            } else {
                // Fallback: take a reasonable chunk
                bodyHtml = html.substring(startIndex).take(30000)
            }
        }

        if (bodyHtml != null) {
            val paragraphs = PARAGRAPH_REGEX
                .findAll(bodyHtml)
                .map { cleanHtml(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }

            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        return if (content.length > 100) content.toString() else null
    }

    /**
     * Extract content from Thanh Nien articles.
     */
    private fun extractThanhNienContent(html: String): String? {
        val match = THANHNIEN_BODY_REGEX.find(html)
        return if (match != null) {
            cleanHtml(match.groupValues[1])
        } else {
            extractGenericContent(html)
        }
    }

    /**
     * Extract content from Dan Tri articles.
     */
    private fun extractDanTriContent(html: String): String? {
        val match = DANTRI_BODY_REGEX.find(html)
        return if (match != null) {
            cleanHtml(match.groupValues[1])
        } else {
            extractGenericContent(html)
        }
    }

    /**
     * Generic content extraction for unknown websites.
     * Tries to find the main article body using common patterns.
     */
    private fun extractGenericContent(html: String): String? {
        // Try various common article container patterns (pre-compiled)
        val patterns = listOf(
            GENERIC_ARTICLE_REGEX,
            GENERIC_ARTICLE_CONTENT_REGEX,
            GENERIC_POST_CONTENT_REGEX,
            GENERIC_ENTRY_CONTENT_REGEX,
            GENERIC_CONTENT_BODY_REGEX
        )
        
        for (regex in patterns) {
            val match = regex.find(html)
            if (match != null) {
                val content = cleanHtml(match.groupValues[1])
                if (content.length > 200) {
                    return content
                }
            }
        }
        
        // Fallback: extract all paragraphs
        val paragraphs = PARAGRAPH_REGEX
            .findAll(html)
            .map { cleanHtml(it.groupValues[1]) }
            .filter { it.length > 50 }
        
        val joined = paragraphs.joinToString("\n\n")
        return if (joined.isNotBlank()) joined else null
    }

    /**
     * Clean HTML tags and entities from text using the injected cleaner.
     */
    private fun cleanHtml(html: String): String {
        return htmlCleaner.clean(html)
    }

    /**
     * Debugging tool: Extract content from raw HTML string directly.
     * Useful for testing parsers without making network requests.
     */
    suspend fun analyzeRawHtml(html: String, mockUrl: String = "https://debug.local/article"): ArticleData? {
        return extractArticleFromHtml(html, mockUrl)
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

    /**
     * Clean HTML for AI analysis - simple regex approach.
     * Removes noise elements while keeping article content structure.
     */
    fun cleanHtmlForAi(html: String): String {
        return try {
            var result = html
            
            // 1. Remove <head>...</head>
            result = result.replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
            
            // 2. Remove <script>...</script>
            result = result.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            
            // 3. Remove <style>...</style>
            result = result.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            
            // 4. Remove <svg>...</svg> (icons, logos - usually large)
            result = result.replace(Regex("<svg[^>]*>[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), "")
            
            // 5. Remove <noscript>...</noscript>
            result = result.replace(Regex("<noscript[^>]*>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), "")
            
            // 6. Remove <header>...</header>
            result = result.replace(Regex("<header[^>]*>[\\s\\S]*?</header>", RegexOption.IGNORE_CASE), "")
            
            // 7. Remove <footer>...</footer>
            result = result.replace(Regex("<footer[^>]*>[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), "")
            
            // 8. Remove <nav>...</nav>
            result = result.replace(Regex("<nav[^>]*>[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), "")
            
            // 9. Remove href attributes (keep <a> tags and classes)
            result = result.replace(Regex(" href=\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex(" href='[^']*'", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex(" target=\"[^\"]*\"", RegexOption.IGNORE_CASE), "") // Remove target="_blank"
            
            // 10. Remove HTML comments
            result = result.replace(Regex("<!--[\\s\\S]*?-->"), "")
            
            // 10. Collapse whitespace
            result = result.replace(Regex("\\s{2,}"), " ")
            result = result.replace(Regex("\\n{2,}"), "\n")
            result = result.trim()
            
            // Add size info
            val originalSize = html.length
            val newSize = result.length
            val reduction = if (originalSize > 0) ((originalSize - newSize) * 100.0 / originalSize).toInt() else 0
            
            "[Optimized: ${newSize/1024}KB, reduced ${reduction}% from ${originalSize/1024}KB]\n$result"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning HTML for AI", e)
            html.take(50000)
        }
    }

    /**
     * Cleanup resources when the Composable is disposed.
     * Note: Uses shared OkHttpClient singleton, so we only clear local references.
     */
    fun cleanup() {
        customSelectorManager = null
        Log.d(TAG, "ArticleContentFetcher cleanup completed")
    }
}

