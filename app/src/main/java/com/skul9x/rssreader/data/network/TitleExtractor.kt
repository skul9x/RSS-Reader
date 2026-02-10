package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.utils.AppLogger
import org.jsoup.nodes.Document

class TitleExtractor(private val logger: AppLogger) {

    companion object {
        private const val TAG = "TitleExtractor"
    }

    /**
     * Extract page title from HTML.
     * Tries: og:title meta tag -> twitter:title -> title tag -> h1 -> fallback to domain
     */
    fun extractTitle(doc: Document, url: String): String {
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
}
