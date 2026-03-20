package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class GenkExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Genk patterns
        private val SAPO_REGEX = Regex("""<h2[^>]*class="[^"]*knc-sapo[^"]*"[^>]*>(.*?)</h2>""", REGEX_OPTIONS)
        private val CONTENT_REGEX = Regex("""<div[^>]*(?:class="[^"]*knc-content[^"]*"|id="ContentDetail")[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val PARAGRAPH_REGEX = Regex("""<p[^>]*>(.*?)</p>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("genk.vn") || url.contains("cafef.vn")
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
            val contentMatch = CONTENT_REGEX.find(html)
            if (contentMatch != null) {
                bodyHtml = contentMatch.groupValues[1]
            }
        }
        
        // Process the body HTML if found by either strategy
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
        
        // 4. Strategy C: Ultimate Fallback (Whole page scan)
        if (content.toString().trim().length < 200) {
            val allParagraphs = PARAGRAPH_REGEX
                .findAll(html)
                .map { htmlCleaner.clean(it.groupValues[1]) }
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

    override fun extractByJsoup(doc: Document, url: String): String? {
        val workingDoc = doc.clone()
        workingDoc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news, .c-box").remove()
        
        val content = StringBuilder()

        // GenK / CafeF (desktop & mobile): Sapo + Content
        // Mobile selectors: .detail-sapo, .detail-content, .singular-content
        // Desktop selectors: .knc-sapo, #ContentDetail, .knc-content
        val sapo = workingDoc.select(".knc-sapo, .detail-sapo, .sapo, h2.sapo").text()
        if (sapo.isNotEmpty()) content.append(sapo).append("\n\n")
        
        val body = workingDoc.select("#ContentDetail, .knc-content, .detail-content, .singular-content, .cms-body, article .content")
        // Extract text from paragraphs only to be clean
        val paragraphs = body.select("p").eachText()
        if (paragraphs.isNotEmpty()) {
            content.append(paragraphs.joinToString("\n\n"))
        } else {
            // Fallback: get whole text if no p tags
            content.append(body.text())
        }
        
        val result = content.toString().trim()
        return if (result.length > 50) result else null
    }
}
