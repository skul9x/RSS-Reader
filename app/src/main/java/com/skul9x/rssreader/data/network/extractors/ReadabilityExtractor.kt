package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import net.dankito.readability4j.Readability4J

/**
 * Content extractor powered by Mozilla's Readability algorithm (via Readability4J).
 * 
 * Used as the "smart fallback" between site-specific extractors and generic regex/jsoup.
 * Readability4J analyzes the DOM structure to identify the main article content,
 * similar to Firefox's Reader View.
 */
class ReadabilityExtractor(private val htmlCleaner: HtmlCleaner) {

    companion object {
        private const val MIN_CONTENT_LENGTH = 200
        private const val MIN_HTML_LENGTH = 500
        private const val TAG = "ReadabilityExtractor"
    }

    /**
     * Extract article content using Readability4J algorithm.
     * 
     * @param html Raw HTML string of the page
     * @param url  URL of the page (used by Readability4J for resolving relative links)
     * @return Cleaned text content, or null if extraction fails/content too short
     */
    fun extract(html: String, url: String): String? {
        // Edge case 1: HTML quá ngắn (< 500 chars) → skip Readability4J
        if (html.length < MIN_HTML_LENGTH) return null

        return try {
            val readability = Readability4J(url, html)
            val article = readability.parse()
            
            // Strategy 1: Plain text (best for Gemini API - fewer tokens)
            val textContent = article.textContent?.trim()
            if (textContent != null && textContent.length > MIN_CONTENT_LENGTH) {
                // Edge case 2: Kiểm tra tỉ lệ link text / total text
                if (isNavigationHeavy(article.content ?: "", textContent.length)) {
                    return null
                }
                return cleanupText(textContent)
            }
            
            // Strategy 2: Clean HTML → strip tags → plain text
            val htmlContent = article.content
            if (htmlContent != null) {
                val cleaned = htmlCleaner.clean(htmlContent)
                if (cleaned.length > MIN_CONTENT_LENGTH) {
                    if (isNavigationHeavy(htmlContent, cleaned.length)) {
                        return null
                    }
                    return cleaned
                }
            }
            
            null // Readability4J couldn't extract meaningful content
        } catch (e: Exception) {
            null // Swallow exception, let caller fallback to GenericExtractor
        }
    }

    /**
     * Check if the content is likely navigation/sidebar by measuring link density.
     */
    private fun isNavigationHeavy(htmlContent: String, totalTextLength: Int): Boolean {
        if (htmlContent.isBlank() || totalTextLength == 0) return true
        
        return try {
            val doc = org.jsoup.Jsoup.parse(htmlContent)
            val links = doc.select("a")
            var linkTextLength = 0
            for (link in links) {
                linkTextLength += link.text().length
            }
            
            (linkTextLength.toDouble() / totalTextLength.toDouble()) > 0.5
        } catch (e: Exception) {
            false // Default to not navigation if parsing fails
        }
    }
    
    /**
     * Clean up extracted text: normalize whitespace, remove excessive newlines.
     */
    private fun cleanupText(text: String): String {
        return text
            .replace(Regex("\\n{3,}"), "\n\n")    // Max 2 consecutive newlines
            .replace(Regex("[ \\t]{2,}"), " ")      // Collapse spaces/tabs
            .trim()
    }
}
