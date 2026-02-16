package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class DanTriExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {

    companion object {
        private val REGEX_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        
        // Dan Tri pattern
        private val BODY_REGEX = Regex("""<div[^>]*class="[^"]*singular-content[^"]*"[^>]*>(.*?)</div>""", REGEX_OPTIONS)
    }

    override fun supports(url: String): Boolean {
        return url.contains("dantri.com.vn")
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
        val workingDoc = doc.clone()
        workingDoc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news, .c-box").remove()
        
        val content = StringBuilder()

        // Dantri: Description + singular-content
        val desc = workingDoc.select("meta[property=og:description]").attr("content")
        if (desc.isNotEmpty()) content.append(desc).append("\n\n")

        val body = workingDoc.select(".singular-content, .e-magazine__body, article")
        if (body.hasText()) {
             content.append(body.select("p").eachText().joinToString("\n\n"))
        } else {
            // Fallback for tricky layouts
             val potentialBody = workingDoc.select("[data-role='content'], .dt-news__content")
             if (potentialBody.hasText()) {
                 content.append(potentialBody.select("p").eachText().joinToString("\n\n"))
             }
        }
        
        val result = content.toString().trim()
        return if (result.length > 50) result else null
    }
}
