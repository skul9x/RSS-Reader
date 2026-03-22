package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class VtcNewsExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // VTC News pattern
        private val SAPO_REGEX = Regex("""<h2[^>]*class="[^"]*font18[^"]*bold[^"]*inline-nb[^"]*"[^>]*>(.*?)</h2>""", REGEX_OPTIONS)
        private val BODY_START_REGEX = Regex("""<div[^>]*class="[^"]*edittor-content[^"]*"[^>]*>""", REGEX_OPTIONS)
        private val PARAGRAPH_REGEX = Regex("""<p[^>]*>(.*?)</p>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("vtcnews.vn")
    }

    override fun extractByRegex(html: String): String? {
        val content = StringBuilder()

        // 1. Extract Sapo - VTC uses inline-nb h2 for description
        val sapoMatch = SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(htmlCleaner.clean(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }

        // 2. Extract Body from edittor-content div
        val startMatch = BODY_START_REGEX.find(html)
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
                .map { htmlCleaner.clean(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 20 }

            val joined = paragraphs.joinToString("\n\n")
            if (joined.isNotBlank()) {
                content.append(joined)
            }
        }
        
        return if (content.length > 100) content.toString() else null
    }

    override fun extractByJsoup(doc: Document, url: String): String? {
        // Phase 2 Fix: Removed doc.clone() to prevent memory leak
        
        val content = StringBuilder()

        // VTC News
        // Sapo is the h2 with inline-nb class
        val sapo = doc.select("h2.font18.bold.inline-nb").text()
        if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")
        
        // Body from edittor-content
        val body = doc.select(".edittor-content").firstOrNull()
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
