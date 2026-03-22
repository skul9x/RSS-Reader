package com.skul9x.rssreader.utils

import android.text.Html

/**
 * Default implementation of HtmlCleaner using Android's native Html class.
 */
class AndroidHtmlCleaner : HtmlCleaner {
    
    companion object {
        private val SCRIPT_REGEX = Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val STYLE_REGEX = Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }

    override fun clean(html: String): String {
        if (html.isBlank()) return ""
        
        // Pre-remove script and style tags
        var cleaned = html
            .replace(SCRIPT_REGEX, "")
            .replace(STYLE_REGEX, "")
        
        // Phase 3: Map HTML structural tags to newlines BEFORE parsing
        // This preserves paragraph breaks, line breaks, and list structures
        cleaned = cleaned
            // Block elements: Add double newline for paragraph separation
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</article>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</section>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</h[1-6]>""", RegexOption.IGNORE_CASE), "\n\n")
            // Line breaks: Single newline
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            // List items: Newline + bullet
            .replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "")
            // List containers: Add spacing
            .replace(Regex("""</ul>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</ol>""", RegexOption.IGNORE_CASE), "\n\n")
        
        // Use Android's Html.fromHtml to strip remaining tags
        val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(cleaned)
        }
        
        // Convert to string and normalize excessive whitespace (but keep newlines!)
        return spanned.toString()
            .replace(Regex("""\n{3,}"""), "\n\n") // Max 2 consecutive newlines
            .replace(Regex("""[ \t]+"""), " ") // Normalize spaces/tabs
            .trim()
    }
}
