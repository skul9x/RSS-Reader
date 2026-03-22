package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class ThanhNienExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Thanh Nien pattern
        private val BODY_REGEX = Regex("""<div[^>]*(?:id="abody"|id="cms-body"|class="[^"]*detail-content[^"]*")[^>]*>(.*?)</div>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("thanhnien.vn")
    }

    override fun extractByRegex(html: String): String? {
        val match = BODY_REGEX.find(html)
        return if (match != null) {
            htmlCleaner.clean(match.groupValues[1])
        } else {
            null
        }
    }

    override fun extractByJsoup(doc: Document, url: String): String? {
        // Phase 2 Fix: Removed doc.clone() to prevent memory leak
        
        val content = StringBuilder()

        // Thanh Nien: Description + detail-content
        val desc = doc.select("meta[property=og:description]").attr("content")
        if (desc.isNotEmpty()) content.append(desc).append("\n\n")

        val body = doc.select(".detail-content, .c-detail .c-detail__body, #cms-body").firstOrNull()
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
