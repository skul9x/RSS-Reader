package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.utils.AppLogger
import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class RedirectResolver(
    private val logger: AppLogger,
    private val htmlCleaner: HtmlCleaner
) {

    companion object {
        private const val TAG = "RedirectResolver"
    }

    /**
     * Helper to clean HTML for quoted content
     */
    private fun cleanHtml(html: String): String {
        return htmlCleaner.clean(html)
    }

    fun extractAiHayRedirect(html: String): String? {
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
    fun extractVozOriginalLink(doc: Document, url: String): String? {
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
     * Extracts the original link from a Voz.vn forum post using Regex (fast path before Jsoup).
     */
    fun extractVozOriginalLinkRegex(html: String): String? {
        try {
            // Priority 1: unfurl link like data-url="https://theNEXTvoz..."
            val unfurlRegex = Regex("""data-url="([^"]+)"""")
            val unfurlMatch = unfurlRegex.find(html)
            if (unfurlMatch != null) {
                val url = unfurlMatch.groupValues[1]
                if (url.startsWith("http")) return url.replace("&amp;", "&")
            }

            // Priority 2: external link class="link--external" href="..."
            val externalRegex = Regex("""class="[^"]*link--external[^"]*"[^>]*href="([^"]+)"""")
            val externalMatch = externalRegex.find(html)
            if (externalMatch != null) {
                val url = externalMatch.groupValues[1]
                if (url.startsWith("http") && !url.contains("voz.vn")) return url.replace("&amp;", "&")
            }
        } catch (e: Exception) {
            logger.logError(TAG, "Regex extract error", e)
        }
        return null
    }

    /**
     * Extracts content from a Quote block in Voz post #1.
     * Returns a complete ArticleData object if successful.
     */
    fun extractVozQuoteAsArticle(doc: Document, url: String): ArticleData? {
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
}
