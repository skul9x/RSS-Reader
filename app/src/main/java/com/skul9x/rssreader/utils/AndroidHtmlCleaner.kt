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
        val withoutScripts = html
            .replace(SCRIPT_REGEX, "")
            .replace(STYLE_REGEX, "")
        
        // Use Android's Html.fromHtml
        val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(withoutScripts, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(withoutScripts)
        }
        
        // Convert to string and normalize whitespace
        return spanned.toString()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }
}
