package com.skul9x.rssreader.data.network.extractors

import org.jsoup.nodes.Document

/**
 * Interface for site-specific content extraction strategies.
 */
interface SiteContentExtractor {
    
    /**
     * Checks if this extractor supports the given URL.
     */
    fun supports(url: String): Boolean
    
    /**
     * Attempt to extract content using Regex (faster, less robust).
     * Returns null if Regex extraction fails or is not implemented.
     */
    fun extractByRegex(html: String): String?
    
    /**
     * Extract content using Jsoup DOM (slower, more robust).
     * Returns null if extraction fails.
     */
    fun extractByJsoup(doc: Document, url: String): String?
    
    /**
     * Whether to attempt Regex extraction before Jsoup.
     * Default is true for performance.
     */
    val preferRegex: Boolean get() = true
}
