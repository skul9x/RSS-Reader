package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class AiHayExtractor(private val htmlCleaner: HtmlCleaner) : SiteContentExtractor {
    
    // AI-Hay doesn't work well with custom regex, relying on Jsoup DOM or script extraction
    // But we need to implement regex method anyway.
    
    override fun supports(url: String): Boolean {
        return url.contains("ai-hay.vn")
    }

    override fun extractByRegex(html: String): String? {
        // AI-Hay content is sometimes embedded in a JSON script tag or simple divs
        // Let's implement a simple regex check for the script data part
        
        // Pattern looks for: "text":"(captured group)", inside the JSON listAnswerEntry
        val regex = Regex(""""listAnswerEntry".*?"text":"(.*?)",""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(html)
        if (match != null) {
             // Unescape JSON string (basic)
             val rawJson = match.groupValues[1]
             val unescaped = rawJson.replace("\\n", "\n")
                 .replace("\\\"", "\"")
                 .replace("\\t", "\t")
                 .replace("\\\\", "\\")
             return unescaped
        }
        
        return null
    }
    
    // Explicitly prefer Jsoup over Regex for AI-Hay because structure is complex (SSR vs CSR)
    override val preferRegex: Boolean get() = false

    override fun extractByJsoup(doc: Document, url: String): String? {
        val workingDoc = doc.clone()
        workingDoc.select("script, style, iframe, .ads, .advertisement, .relate-container, .related-news, .c-box").remove()
        
        val content = StringBuilder()

        // AI-Hay
        // 1. Try SSR Selector (div.ai-answer-text)
        val body = workingDoc.select(".ai-answer-text, .markdown-content, .ai-answer-text-md")
        if (body.hasText()) {
            // Try structured tags first
            val structured = body.select("p, li").eachText().joinToString("\n\n")
            if (structured.length > 100) {
                content.append(structured)
            } else {
                // Fallback to whole text (preserves whitespace/newlines in text nodes)
                content.append(body.map { it.wholeText() }.joinToString("\n\n"))
            }
        } 
        
        // 2. Fallback to JSON data if HTML extraction failed/empty
        if (content.length < 50) {
             val script = workingDoc.select("script#__global__").html()
             if (script.isNotEmpty()) {
                 // Reuse regex logic but apply on the specific script content
                 val regex = Regex(""""listAnswerEntry".*?"text":"(.*?)",""", RegexOption.DOT_MATCHES_ALL)
                 val match = regex.find(script)
                 if (match != null) {
                     val rawJson = match.groupValues[1]
                     val unescaped = rawJson.replace("\\n", "\n")
                         .replace("\\\"", "\"")
                         .replace("\\t", "\t")
                         .replace("\\\\", "\\")
                     content.append(unescaped)
                 }
             }
        }
        
        val result = content.toString().trim()
        return if (result.length > 50) result else null
    }
}
