package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.data.local.CustomSelectorManager
import com.skul9x.rssreader.data.network.extractors.*
import com.skul9x.rssreader.utils.AppLogger
import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ContentExtractorRegistry(
    private val htmlCleaner: HtmlCleaner,
    private val logger: AppLogger,
    redirectResolver: RedirectResolver
) {

    private val extractors: List<SiteContentExtractor> = listOf(
        VnExpressExtractor(htmlCleaner),
        GenkExtractor(htmlCleaner),
        TuoiTreExtractor(htmlCleaner),
        ThanhNienExtractor(htmlCleaner),
        DanTriExtractor(htmlCleaner),
        NguoiQuanSatExtractor(htmlCleaner),
        VietnamNetExtractor(htmlCleaner),
        VtcNewsExtractor(htmlCleaner),
        AiHayExtractor(htmlCleaner),
        VozExtractor(htmlCleaner, redirectResolver)
        // GenericExtractor is handled as explicit fallback
    )
    
    private val genericExtractor = GenericExtractor(htmlCleaner)
    private var customSelectorManager: CustomSelectorManager? = null

    companion object {
        private const val TAG = "ContentExtractorRegistry"
    }

    fun setCustomSelectorManager(manager: CustomSelectorManager) {
        this.customSelectorManager = manager
    }

    /**
     * Main extraction entry point.
     * Tries: Custom Selector -> Site Specific Extractor -> Generic Extractor
     */
    fun extractContent(doc: Document, html: String, url: String): String? {
        // 0. Try Custom Selector (User Defined)
        val customSelector = customSelectorManager?.getSelectorForUrl(url)
        if (customSelector != null) {
            try {
                // TODO: Implement custom selector logic properly if needed.
                // For now, this is a placeholder matching original logic structure which was incomplete/commented out
                // "val workingDoc = doc.clone()..."
            } catch (e: Exception) {
                logger.logError(TAG, "Custom selector extraction failed: $customSelector", e)
            }
        }

        // Find supporting extractor
        val extractor = extractors.find { it.supports(url) }

        if (extractor != null) {
            // 1. Regex Priority (Optimization)
            if (extractor.preferRegex) {
                val regexResult = extractor.extractByRegex(html)
                if (regexResult != null && regexResult.length > 200) {
                    logger.logJsoup(url, true, "Regex extraction success: ${regexResult.length} chars")
                    return regexResult
                } else {
                    logger.logJsoup(url, false, "Regex extraction failed/short, falling back to Jsoup")
                }
            }
            
            // 2. Jsoup Site-Specific
            val jsoupResult = extractor.extractByJsoup(doc, url)
            if (jsoupResult != null && jsoupResult.length > 200) {
                return jsoupResult
            }
        }

        // 3. Fallback to Generic (Jsoup first, then Regex if really needed, but GenericExtractor handles both internally)
        // Note: GenericExtractor methods try to find content using generic patterns
        
        // Try generic Jsoup first
        val genericJsoup = genericExtractor.extractByJsoup(doc, url)
        if (genericJsoup != null && genericJsoup.length > 200) {
             return genericJsoup
        }
        
        // Try generic Regex as last resort
        return genericExtractor.extractByRegex(html)
    }

    /**
     * Scans the HTML to find potential main content containers.
     * Returns a list of candidates sorted by likelihood (score).
     */
    fun scanForPotentialContainers(html: String): List<ContentCandidate> {
        return try {
            val doc = Jsoup.parse(html)
            
            // Remove noise to get accurate text length counts
            doc.select("script, style, nav, header, footer, .menu, .sidebar, .ads").remove()
            
            val candidates = mutableListOf<ContentCandidate>()
            
            // Find all block elements that might contain the article
            val elements = doc.select("div, article, section, main")
            
            for (el in elements) {
                val text = el.text()
                val length = text.length
                
                // Only consider substantial text blocks
                if (length > 300) {
                    val id = el.id()
                    val className = el.className()
                    
                    // Skip if no identifier (hard to target) unless it's <article> or <main>
                    if (id.isBlank() && className.isBlank() && el.tagName() !in listOf("article", "main")) {
                        continue
                    }
                    
                    // Construct a selector
                    val selector = when {
                        id.isNotBlank() -> "#$id"
                        className.isNotBlank() -> ".${className.replace(" ", ".")}" // Replace spaces with dots for combined classes
                        else -> el.tagName()
                    }
                    
                    // Simple scoring heuristic
                    var score = length
                    if (className.contains("body") || className.contains("content") || className.contains("article")) score += 500
                    if (id.contains("body") || id.contains("content")) score += 500
                    if (el.tagName() == "article") score += 300
                    
                    // Count paragraphs (strong indicator of article body)
                    val pCount = el.select("p").size
                    score += (pCount * 100)

                    val preview = text.take(100).replace("\n", " ").trim() + "..."
                    
                    // Store full text for preview (limit to 20KB to avoid memory issues)
                    val fullText = text.take(20000)
                    
                    candidates.add(ContentCandidate(selector, className, id, length, preview, fullText, score))
                }
            }
            
            // Deduplicate and sort by score descending
            candidates.distinctBy { it.selector }
                .sortedByDescending { it.score }
                .take(15) // Return top 15 results
            
        } catch (e: Exception) {
            logger.logError(TAG, "Error scanning HTML", e)
            emptyList()
        }
    }
}
