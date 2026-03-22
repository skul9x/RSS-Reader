package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class VnExpressExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // VnExpress patterns
        private val DESCRIPTION_REGEX = Regex("""<meta[^>]*property="og:description"[^>]*content="([^"]+)"[^>]*>""")
        private val FCK_DETAIL_REGEX = Regex("""<(?:article|div)[^>]*class="[^"]*fck_detail[^"]*"[^>]*>(.*?)</(?:article|div)>""", REGEX_OPTIONS)
        private val NORMAL_PARAGRAPH_REGEX = Regex("""<p[^>]*class="[^"]*Normal[^"]*"[^>]*>(.*?)</p>""", REGEX_OPTIONS)
        private val PARAGRAPH_REGEX = Regex("""<p[^>]*>(.*?)</p>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("vnexpress.net")
    }

    override fun extractByRegex(html: String): String? {
        val content = StringBuilder()
        
        // 1. Extract description from meta tag og:description
        val descriptionMatch = DESCRIPTION_REGEX.find(html)
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
            val fckMatch = FCK_DETAIL_REGEX.find(html)
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
                .map { htmlCleaner.clean(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }
            
            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        // 4. Strategy C: Ultimate Fallback (Whole page scan for class="Normal")
        if (content.toString().trim().length < 200) {
            val allNormalParagraphs = NORMAL_PARAGRAPH_REGEX
                .findAll(html)
                .map { htmlCleaner.clean(it.groupValues[1]) }
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

    override fun extractByJsoup(doc: Document, url: String): String? {
        // Phase 2 Fix: Removed doc.clone() to prevent memory leak
        
        val content = StringBuilder()
        
        // VnExpress: Description + fck_detail
        val desc = doc.select("meta[property=og:description]").attr("content")
        if (desc.isNotEmpty()) content.append(desc).append("\n\n")
        
        val body = doc.select(".fck_detail").firstOrNull()
        if (body != null) {
            // Extract HTML and clean with HtmlCleaner (string-based, no DOM clone)
            val rawHtml = body.html()
            val cleanedText = htmlCleaner.clean(rawHtml)
            content.append(cleanedText)
        }
        
        val result = content.toString().trim()
        return if (result.length > 50) result else null
    }
}
