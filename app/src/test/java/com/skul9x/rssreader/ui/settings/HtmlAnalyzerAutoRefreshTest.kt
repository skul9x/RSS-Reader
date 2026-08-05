package com.skul9x.rssreader.ui.settings

import com.skul9x.rssreader.data.local.CustomSelectorManager
import com.skul9x.rssreader.data.network.ContentExtractorRegistry
import com.skul9x.rssreader.data.network.RedirectResolver
import com.skul9x.rssreader.utils.AppLogger
import com.skul9x.rssreader.utils.HtmlCleaner
import io.mockk.every
import io.mockk.mockk
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlAnalyzerAutoRefreshTest {

    class TestHtmlCleaner : HtmlCleaner {
        override fun clean(html: String): String {
            return html.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
        }
    }

    class TestAppLogger : AppLogger {
        override fun log(tag: String, message: String, e: Throwable?) {}
        override fun logError(tag: String, message: String, e: Throwable?) {}
        override fun logEvent(eventType: String, url: String, message: String, details: String, isError: Boolean) {}
        override fun logJsoup(url: String, isSuccess: Boolean, message: String) {}
        override fun logHttp(url: String, code: Int, message: String) {}
    }

    @Test
    fun `test custom selector extraction auto refresh effect`() {
        val cleaner = TestHtmlCleaner()
        val logger = TestAppLogger()
        val redirectResolver = RedirectResolver(logger, cleaner)
        val registry = ContentExtractorRegistry(cleaner, logger, redirectResolver)

        val customSelectorManager = mockk<CustomSelectorManager>(relaxed = true)
        registry.setCustomSelectorManager(customSelectorManager)

        val url = "https://example.com/article"
        val customSelector = ".custom-article-body"

        every { customSelectorManager.getSelectorForUrl(url) } returns customSelector

        val longContent = "This is custom selector content that is very long. ".repeat(10)
        val html = """
            <html>
            <body>
                <div class="header">Header content</div>
                <div class="custom-article-body">
                    <p>$longContent</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val result = registry.extractContent(doc, html, url)

        assertNotNull("Result should not be null when custom selector matches", result)
        assertTrue("Extracted content should contain custom selector body", result!!.contains("custom selector content"))
    }
}
