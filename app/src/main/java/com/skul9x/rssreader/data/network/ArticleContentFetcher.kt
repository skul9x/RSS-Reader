package com.skul9x.rssreader.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

/**
 * Fetches full article content from news website URLs.
 * Supports VnExpress and generic article extraction.
 */
class ArticleContentFetcher {

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
        private val TUOITRE_BODY_REGEX = Regex("""<div[^>]*id="main-detail-body"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        
        // Thanh Nien pattern
        private val THANHNIEN_BODY_REGEX = Regex("""<div[^>]*(?:id="abody"|id="cms-body"|class="[^"]*detail-content[^"]*")[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        
        // Dan Tri pattern
        private val DANTRI_BODY_REGEX = Regex("""<div[^>]*class="[^"]*singular-content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        
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
    }

    // Use shared OkHttpClient singleton
    private val client = NetworkClient.okHttpClient

    /**
     * Fetch full article content from a given URL.
     * Returns the extracted article text, or null if extraction fails.
     * Supports cancellation - HTTP request will be cancelled when coroutine is cancelled.
     */
    suspend fun fetchArticleContent(url: String): String? {
        return try {
            // Check if already cancelled before making request
            coroutineContext.ensureActive()
            
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
                .build()

            // Use suspendCancellableCoroutine for proper cancellation
            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = client.newCall(request)
                
                // Cancel HTTP request when coroutine is cancelled
                continuation.invokeOnCancellation {
                    Log.d(TAG, "Article fetch cancelled for: $url")
                    call.cancel()
                }
                
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
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
            
            response.use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string()
                    if (html != null) {
                        // Run heavy extraction on Default dispatcher to avoid blocking caller
                        withContext(Dispatchers.Default) {
                            extractContent(html, url)
                        }
                    } else null
                } else {
                    Log.w(TAG, "HTTP ${resp.code}")
                    null
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Article fetch cancelled")
            throw e  // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching article", e)
            null
        }
    }

    /**
     * Extract article content from HTML based on the website.
     * Prioritizes Jsoup extraction, falls back to legacy methods.
     */
    private fun extractContent(html: String, url: String): String? {
        // Try Jsoup extraction first (Best Method)
        val jsoupContent = extractWithJsoup(html, url)
        if (jsoupContent != null && jsoupContent.length > 200) {
            return jsoupContent
        }

        // Fallback to legacy methods if Jsoup fails or returns too little content
        return when {
            url.contains("vnexpress.net") -> extractVnExpressContent(html)
            url.contains("genk.vn") -> extractGenkContent(html)
            url.contains("tuoitre.vn") -> extractTuoiTreContent(html)
            url.contains("thanhnien.vn") -> extractThanhNienContent(html)
            url.contains("dantri.com.vn") -> extractDanTriContent(html)
            else -> extractGenericContent(html)
        }
    }

    /**
     * Extract content using Jsoup (HTML Parser).
     * This is the most robust method as it parses the DOM correctly.
     */
    private fun extractWithJsoup(html: String, url: String): String? {
        return try {
            val doc = Jsoup.parse(html)
            
            // Remove unwanted elements globally
            doc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news").remove()

            val content = StringBuilder()

            when {
                url.contains("genk.vn") -> {
                    // GenK: Sapo + Content
                    val sapo = doc.select(".knc-sapo").text()
                    if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")
                    
                    val body = doc.select("#ContentDetail, .knc-content")
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
                    val desc = doc.select("meta[property=og:description]").attr("content")
                    if (desc.isNotEmpty()) content.append(desc).append("\n\n")
                    
                    val body = doc.select(".fck_detail")
                    val paragraphs = body.select("p.Normal").eachText()
                    if (paragraphs.isNotEmpty()) {
                        content.append(paragraphs.joinToString("\n\n"))
                    } else {
                        content.append(body.text())
                    }
                }
                url.contains("dantri.com.vn") -> {
                    // Dantri: Description + singular-content
                    val desc = doc.select("meta[property=og:description]").attr("content")
                    if (desc.isNotEmpty()) content.append(desc).append("\n\n")

                    val body = doc.select(".singular-content, .e-magazine__body, article")
                    if (body.hasText()) {
                         content.append(body.select("p").eachText().joinToString("\n\n"))
                    } else {
                        // Fallback for tricky layouts
                         val potentialBody = doc.select("[data-role='content'], .dt-news__content")
                         if (potentialBody.hasText()) {
                             content.append(potentialBody.select("p").eachText().joinToString("\n\n"))
                         }
                    }
                }
                url.contains("thanhnien.vn") -> {
                    // Thanh Nien: Description + detail-content
                    val desc = doc.select("meta[property=og:description]").attr("content")
                    if (desc.isNotEmpty()) content.append(desc).append("\n\n")

                    val body = doc.select(".detail-content, .c-detail .c-detail__body, #cms-body")
                    if (body.hasText()) {
                        content.append(body.select("p").eachText().joinToString("\n\n"))
                    } else {
                         // Fallback
                         content.append(doc.select("article").text())
                    }
                }
                url.contains("tuoitre.vn") -> {
                    val body = doc.select("#main-detail-body")
                    content.append(body.select("p").eachText().joinToString("\n\n"))
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
                        val element = doc.select(selector)
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
            Log.e(TAG, "Jsoup extraction failed", e)
            null
        }
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
     */
    private fun extractTuoiTreContent(html: String): String? {
        val match = TUOITRE_BODY_REGEX.find(html)
        return if (match != null) {
            cleanHtml(match.groupValues[1])
        } else {
            extractGenericContent(html)
        }
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
     * Clean HTML tags and entities from text using Android's native Html parser.
     * Much faster than multiple replace() calls and handles all HTML entities automatically.
     */
    private fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        
        // Pre-remove script and style tags (Html.fromHtml doesn't remove their content)
        val withoutScripts = html
            .replace(SCRIPT_REGEX, "")
            .replace(STYLE_REGEX, "")
        
        // Use Android's Html.fromHtml which is implemented in native C++ for performance
        val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(withoutScripts, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(withoutScripts)
        }
        
        // Convert to string and normalize whitespace (including newlines from <br> tags)
        return spanned.toString()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }
}

