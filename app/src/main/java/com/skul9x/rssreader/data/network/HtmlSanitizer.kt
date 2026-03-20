package com.skul9x.rssreader.data.network

import android.util.Log

class HtmlSanitizer {

    companion object {
        private const val TAG = "HtmlSanitizer"
    }

    /**
     * Clean HTML for AI analysis - simple regex approach.
     * Removes noise elements while keeping article content structure.
     */
    fun cleanHtmlForAi(html: String): String {
        return try {
            var result = html
            
            // 1. Remove <head>...</head>
            result = result.replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
            
            // 2. Remove <script>...</script>
            result = result.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            
            // 3. Remove <style>...</style>
            result = result.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            
            // 4. Remove <svg>...</svg> (icons, logos - usually large)
            result = result.replace(Regex("<svg[^>]*>[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), "")
            
            // 5. Remove <noscript>...</noscript>
            result = result.replace(Regex("<noscript[^>]*>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), "")
            
            // 6. Remove <header>...</header>
            result = result.replace(Regex("<header[^>]*>[\\s\\S]*?</header>", RegexOption.IGNORE_CASE), "")
            
            // 7. Remove <footer>...</footer>
            result = result.replace(Regex("<footer[^>]*>[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), "")
            
            // 8. Remove <nav>...</nav>
            result = result.replace(Regex("<nav[^>]*>[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), "")
            
            // 9. Remove href attributes (keep <a> tags and classes)
            result = result.replace(Regex(" href=\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex(" href='[^']*'", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex(" target=\"[^\"]*\"", RegexOption.IGNORE_CASE), "") // Remove target="_blank"
            
            // 10. Remove HTML comments
            result = result.replace(Regex("<!--[\\s\\S]*?-->"), "")
            
            // 10. Collapse whitespace
            result = result.replace(Regex("\\s{2,}"), " ")
            result = result.replace(Regex("\\n{2,}"), "\n")
            result = result.trim()
            
            // Add size info
            val originalSize = html.length
            val newSize = result.length
            val reduction = if (originalSize > 0) ((originalSize - newSize) * 100.0 / originalSize).toInt() else 0
            
            "[Optimized: ${newSize/1024}KB, reduced ${reduction}% from ${originalSize/1024}KB]\n$result"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning HTML for AI", e)
            html.take(50000)
        }
    }
}
