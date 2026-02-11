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
        val workingDoc = doc.clone()
        workingDoc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news, .c-box").remove()
        
        val content = StringBuilder()

        // Thanh Nien: Description + detail-content
        val desc = workingDoc.select("meta[property=og:description]").attr("content")
        if (desc.isNotEmpty()) content.append(desc).append("\n\n")

        val body = workingDoc.select(".detail-content, .c-detail .c-detail__body, #cms-body")
        if (body.hasText()) {
            content.append(body.select("p").eachText().joinToString("\n\n"))
        } else {
             // Fallback
             content.append(workingDoc.select("article").text())
        }
        
        val result = content.toString().trim()
        return if (result.length > 50) result else null
    }
}
