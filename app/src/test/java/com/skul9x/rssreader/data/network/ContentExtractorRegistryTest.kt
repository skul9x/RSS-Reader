package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.utils.AppLogger
import com.skul9x.rssreader.utils.HtmlCleaner
import org.jsoup.Jsoup
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExtractorRegistryTest {

    // Simple fakes for testing
    class TestHtmlCleaner : HtmlCleaner {
        override fun clean(html: String): String {
            return html.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
        }
    }

    class TestAppLogger : AppLogger {
        override fun log(tag: String, message: String, e: Throwable?) {}
        override fun logError(tag: String, message: String, e: Throwable?) {}
        override fun logEvent(eventType: String, url: String, message: String, details: String, isError: Boolean) {}
        override fun logJsoup(url: String, isSuccess: Boolean, message: String) {
            println("JSOUP LOG: $url - $isSuccess - $message")
        }
        override fun logHttp(url: String, code: Int, message: String) {}
    }


    private val cleaner = TestHtmlCleaner()
    private val logger = TestAppLogger()
    private val redirectResolver = RedirectResolver(logger, cleaner)
    private val registry = ContentExtractorRegistry(cleaner, logger, redirectResolver)

    @Test
    fun `test site specific extractor has priority over readability`() {
        // VnExpress content that would trigger site-specific extractor
        val longContent = "This is VnExpress specific content. It is long enough to be valid. ".repeat(20)
        val html = """
            <html>
            <head>
                <title>VnExpress</title>
                <meta property="og:description" content="VnExpress Description" />
            </head>
            <body>
                <div class="fck_detail">
                    <p class="Normal">$longContent</p>
                </div>
                <!-- Adding some padding to reach 500 chars if needed for other tests or future changes -->
                <div style="display:none">${"padding ".repeat(100)}</div>
            </body>
            </html>
        """.trimIndent()
        val url = "https://vnexpress.net/test.html"
        val doc = Jsoup.parse(html)
        
        val result = registry.extractContent(doc, html, url)
        
        assertNotNull(result)
        // Site specific extractor should work
        assertTrue(result!!.contains("VnExpress specific content"))
    }

    @Test
    fun `test readability extractor handles unknown sites`() {
        // Generic content on an unknown site
        val longText = "This is a very long article body that should be detected by the Readability algorithm. ".repeat(20)
        val html = """
            <html>
            <head><title>Unknown Site</title></head>
            <body>
                <article>
                    <h1>Article Title</h1>
                    <div id="main-content">
                        <p>$longText</p>
                    </div>
                </article>
            </body>
            </html>
        """.trimIndent()
        val url = "https://random-blog.com/post/123"
        val doc = Jsoup.parse(html)
        
        val result = registry.extractContent(doc, html, url)
        
        assertNotNull("Readability should extract content from unknown site", result)
        assertTrue("Result should contain long text from readability", result!!.contains("Readability algorithm"))
    }

    @Test
    fun `test fallback to generic when readability fails`() {
        // Content too short for Readability but maybe caught by Generic
        // Let's make HTML < 500 characters so Readability skips immediately
        val longText = "This content is intentionally made long enough to be extracted by the Generic Jsoup extractor even if Readability skips it due to HTML length. ".repeat(5)
        val shortHtml = """
            <html><body>
                <div class="content">
                    <p>$longText</p>
                </div>
            </body></html>
        """.trimIndent()
        val url = "https://unknown-site.com/short"
        val doc = Jsoup.parse(shortHtml)
        
        val result = registry.extractContent(doc, shortHtml, url)
        
        // Readability returns null, so it falls back to Generic.
        // We expect Generic Jsoup to pick it up if it's considered main content.
        assertNotNull("Should fallback to generic extractor", result)
    }

    @Test
    fun `test full priority chain works correctly`() {
        // This is a complex test to verify the order: Site-Specific > Readability > Generic
        // We already tested Site-Specific vs Readability.
        // Now let's test a case where Site-specific doesn't support, but Readability DOES.
        
        val longContent = "Meaningful article content for readability. ".repeat(20)
        val html = "<html><body><article><p>$longContent</p></article></body></html>"
        val url = "https://new-unsupported-site.com/article"
        val doc = Jsoup.parse(html)
        
        val result = registry.extractContent(doc, html, url)
        assertNotNull(result)
        assertTrue(result!!.contains("Meaningful article content"))
    }

    @Test
    fun `test readability extraction performance`() {
        val largeContent = "This is a paragraph of text that will be repeated many times to create a large HTML document for performance testing. ".repeat(100)
        val html = """
            <html><head><title>Large Article</title></head>
            <body>
                <nav>Header links</nav>
                <article>
                    <h1>Large Article Title</h1>
                    <p>$largeContent</p>
                    <p>${largeContent.reversed()}</p>
                </article>
                <aside>Sidebar</aside>
                <footer>Footer</footer>
            </body></html>
        """.trimIndent()
        
        val start = System.currentTimeMillis()
        val result = registry.extractContent(Jsoup.parse(html), html, "https://performance-test.com")
        val elapsed = System.currentTimeMillis() - start
        
        println("Extraction took ${elapsed}ms")
        assertNotNull(result)
        assertTrue("Extraction should be fast (< 500ms)", elapsed < 500)
    }
}
