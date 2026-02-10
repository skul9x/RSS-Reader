package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class TuoiTreExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Tuoi Tre pattern
        private val SAPO_REGEX = Regex("""<h2.*?data-role="sapo".*?>(.*?)</h2>""", REGEX_OPTIONS)
        private val BODY_START_REGEX = Regex("""<div.*?data-role="content".*?>""", REGEX_OPTIONS)
        private val BODY_REGEX = Regex("""<div.*?id="main-detail-body".*?>(.*?)</div>""", REGEX_OPTIONS)
        private val PARAGRAPH_REGEX = Regex("""<p[^>]*>(.*?)</p>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("tuoitre.vn")
    }

    override fun extractByRegex(html: String): String? {
        val content = StringBuilder()

        // 1. Extract Sapo
        val sapoMatch = SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(htmlCleaner.clean(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }

        // 2. Extract Body
        // Method: Find start marker, then extract paragraphs until end marker
        val startMatch = BODY_START_REGEX.find(html)
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
             val match = BODY_REGEX.find(html)
             if (match != null) {
                 bodyHtml = match.groupValues[1]
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
        val workingDoc = doc.clone()
        workingDoc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news, .c-box").remove()
        
        val content = StringBuilder()
        val body = workingDoc.select("#main-detail-body")
        content.append(body.select("p").eachText().joinToString("\n\n"))
        
        val result = content.toString().trim()
        return if (result.length > 50) result else null
    }
}
