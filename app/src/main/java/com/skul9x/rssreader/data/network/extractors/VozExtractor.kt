package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.data.network.RedirectResolver
import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.nodes.Document

class VozExtractor(
    private val htmlCleaner: HtmlCleaner,
    private val redirectResolver: RedirectResolver
) : SiteContentExtractor {

    override fun supports(url: String): Boolean {
        return url.contains("voz.vn")
    }

    override fun extractByRegex(html: String): String? {
        return null // Regex not suitable for Voz forum structure
    }
    
    // Voz uses Redirect or Quote extraction, mostly logic resides in RedirectResolver
    // But this standard extractor can serve as a fallback if redirect failed and we are left with the thread page
    
    override val preferRegex: Boolean get() = false

    override fun extractByJsoup(doc: Document, url: String): String? {
        // Phase 2 Fix: Removed doc.clone() to prevent memory leak
        
        // Try to extract content from the first post directly, assuming it IS the article content
        // This is what extractVozQuoteAsArticle did, but here we return just the String
        
        try {
            val firstPost = doc.selectFirst(".message--post") ?: return null
            
            // Check for Quote block first
            val quoteBlock = firstPost.selectFirst(".bbCodeBlock--quote .bbCodeBlock-content")
            if (quoteBlock != null) {
                return htmlCleaner.clean(quoteBlock.html())
            }
            
            // Fallback: entire first post content
            // .message-content .bbWrapper
            val content = firstPost.select(".message-content .bbWrapper").html()
            if (content.isNotBlank()) {
                return htmlCleaner.clean(content)
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return null
    }
}
