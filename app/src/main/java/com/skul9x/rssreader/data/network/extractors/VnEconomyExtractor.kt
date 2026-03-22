package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class VnEconomyExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Vneconomy patterns
        private val SAPO_REGEX = Regex("""<div[^>]*class="[^"]*news-sapo[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        // Matches <p>, <h3>, <figcaption> — covers paragraphs, section headings, and image captions
        private val CONTENT_ELEMENT_REGEX = Regex("""<(?:p|h3|figcaption)[^>]*>(.*?)</(?:p|h3|figcaption)>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("vneconomy.vn")
    }

    override fun extractByRegex(html: String): String? {
        val content = StringBuilder()
        
        // 1. Extract sapo (summary)
        val sapoMatch = SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(htmlCleaner.clean(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }
        
        var bodyHtml: String? = null
        
        // 2. Strategy: Marker-based
        val startMarker = "data-field=\"body\""
        var startIndex = html.indexOf(startMarker)
        
        if (startIndex != -1) {
            // Move past the marker tag start
            // We need to find the closing '>' of the div tag to start content extraction correctly
            val tagCloseIndex = html.indexOf(">", startIndex)
            if (tagCloseIndex != -1) {
                startIndex = tagCloseIndex + 1
            }

            // End markers
            val endMarker1 = "class=\"box-keyword\""
            val endMarker2 = "class=\"news-reference\""
            val endMarker3 = "class=\"rate-post\"" // Sometimes appears
            
            val endIndex1 = html.indexOf(endMarker1, startIndex)
            val endIndex2 = html.indexOf(endMarker2, startIndex)
            val endIndex3 = html.indexOf(endMarker3, startIndex)
            
            // Find the earliest valid end marker
            val indices = listOf(endIndex1, endIndex2, endIndex3).filter { it != -1 }
            
            val endIndex = if (indices.isNotEmpty()) {
                indices.minOrNull() ?: -1
            } else {
                -1
            }
            
            if (endIndex != -1) {
                bodyHtml = html.substring(startIndex, endIndex)
            } else {
                // Fallback: take a reasonable chunk when no end marker found
                bodyHtml = html.substring(startIndex).take(30000)
            }
        }
        
        // Process the body HTML if found
        if (bodyHtml != null) {
            val paragraphs = CONTENT_ELEMENT_REGEX
                .findAll(bodyHtml)
                .map { htmlCleaner.clean(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }
            
            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        val result = content.toString().trim()
        return if (result.length > 100) result else null
    }

    override fun extractByJsoup(doc: Document, url: String): String? {
        // Phase 2 Fix: Removed doc.clone() to prevent memory leak
        
        val content = StringBuilder()

        // Sapo
        val sapo = doc.select(".news-sapo").text()
        if (sapo.isNotEmpty()) {
            content.append(sapo).append("\n\n")
        }
        
        // Body
        val body = doc.select("[data-field=body]").firstOrNull()
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
