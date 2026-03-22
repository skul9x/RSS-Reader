package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class GenericExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Generic patterns
        private val GENERIC_ARTICLE_REGEX = Regex("""<article[^>]*>(.*?)</article>""", REGEX_OPTIONS)
        private val GENERIC_ARTICLE_CONTENT_REGEX = Regex("""<div[^>]*class="[^"]*article[-_]?content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val GENERIC_POST_CONTENT_REGEX = Regex("""<div[^>]*class="[^"]*post[-_]?content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val GENERIC_ENTRY_CONTENT_REGEX = Regex("""<div[^>]*class="[^"]*entry[-_]?content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val GENERIC_CONTENT_BODY_REGEX = Regex("""<div[^>]*class="[^"]*content[-_]?body[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
        private val PARAGRAPH_REGEX = Regex("""<p[^>]*>(.*?)</p>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        // Supports all urls as fallback
        return true
    }

    override fun extractByRegex(html: String): String? {
        // Try common article container patterns
        val patterns = listOf(
            GENERIC_ARTICLE_REGEX,
            GENERIC_ARTICLE_CONTENT_REGEX,
            GENERIC_POST_CONTENT_REGEX,
            GENERIC_ENTRY_CONTENT_REGEX,
            GENERIC_CONTENT_BODY_REGEX
        )
        
        for (regex in patterns) {
            val match = regex.find(html)
            if (match != null) {
                val content = htmlCleaner.clean(match.groupValues[1])
                if (content.length > 200) {
                    return content
                }
            }
        }
        
        // Fallback: extract all paragraphs
        val paragraphs = PARAGRAPH_REGEX
            .findAll(html)
            .map { htmlCleaner.clean(it.groupValues[1]) }
            .filter { it.length > 50 }
        
        val joined = paragraphs.joinToString("\n\n")
        return if (joined.isNotBlank()) joined else null
    }

    override fun extractByJsoup(doc: Document, url: String): String? {
        // Phase 2 Fix: Removed doc.clone() to prevent memory leak
        // Instead, select element directly and clean HTML string
        
        val content = StringBuilder()
        
        // Generic Jsoup extraction
        val potentialSelectors = listOf(
            "article",
            ".article-content",
            ".post-content",
            ".entry-content",
            ".content-body",
            "#content",
            "main"
        )
        
        for (selector in potentialSelectors) {
            val element = doc.select(selector).firstOrNull()
            if (element != null && element.hasText()) {
                // Extract raw HTML and clean it using HtmlCleaner (string-based, no DOM clone)
                val rawHtml = element.html()
                val cleanedText = htmlCleaner.clean(rawHtml)
                
                if (cleanedText.length > 200) {
                    content.append(cleanedText)
                    break
                }
            }
        }
        
        val result = content.toString().trim()
        return if (result.length > 50) result else null
    }
}
