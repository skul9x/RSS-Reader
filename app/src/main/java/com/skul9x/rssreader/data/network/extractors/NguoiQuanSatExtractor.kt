package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class NguoiQuanSatExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Nguoi Quan Sat pattern
        private val SAPO_REGEX = Regex("""<p[^>]*class="[^"]*sc-longform-header-sapo[^"]*"[^>]*>(.*?)</p>""", REGEX_OPTIONS)
        private val BODY_START_REGEX = Regex("""<article[^>]*class="[^"]*entry[^"]*"[^>]*>""", REGEX_OPTIONS)
        private val PARAGRAPH_REGEX = Regex("""<p[^>]*>(.*?)</p>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("nguoiquansat.vn")
    }

    override fun extractByRegex(html: String): String? {
        val content = StringBuilder()

        // 1. Extract Sapo
        val sapoMatch = SAPO_REGEX.find(html)
        if (sapoMatch != null) {
            content.append(htmlCleaner.clean(sapoMatch.groupValues[1]))
            content.append("\n\n")
        }

        // 2. Extract Body using Sapo-Content separation or Markers
        // Body usually starts with <article class="entry
        val startMatch = BODY_START_REGEX.find(html)
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
                .map { htmlCleaner.clean(it.groupValues[1]) }
                // Remove the sapo if it was captured again (Sapo is a p tag in the body)
                .filter { p -> 
                    val sapoText = sapoMatch?.groupValues?.get(1)?.let { htmlCleaner.clean(it) } ?: ""
                    p.isNotBlank() && p.length > 20 && p != sapoText
                }

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

        // Nguoi Quan Sat
        val sapo = doc.select(".sc-longform-header-sapo").text()
        if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")

        val body = doc.select(".entry, .normal-article-content").firstOrNull()
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
