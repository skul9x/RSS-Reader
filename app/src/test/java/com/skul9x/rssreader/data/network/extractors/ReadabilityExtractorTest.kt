package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadabilityExtractorTest {

    private val fakeHtmlCleaner = object : HtmlCleaner {
        override fun clean(html: String): String {
            // Simple tag stripping for testing
            return html.replace(Regex("<[^>]*>"), "").trim()
        }
    }

    private val extractor = ReadabilityExtractor(fakeHtmlCleaner)

    @Test
    fun testExtractFromValidArticle() {
        val longText = "This is a long enough content to be extracted by Readability4J. ".repeat(10)
        val html = """
            <html>
                <body>
                    <article>
                        <h1>Title</h1>
                        <p>$longText</p>
                    </article>
                </body>
            </html>
        """.trimIndent()
        
        val result = extractor.extract(html, "https://example.com/article")
        assertNotNull("Should extract content from valid article", result)
        assertTrue("Extracted content should contain original text", result!!.contains("This is a long enough content"))
    }

    @Test
    fun testExtractFromEmptyHtml() {
        val result = extractor.extract("", "https://example.com")
        assertNull("Should return null for empty HTML", result)
    }

    @Test
    fun testExtractFromShortHtml() {
        val html = "<html><body><p>Too short</p></body></html>"
        val result = extractor.extract(html, "https://example.com")
        assertNull("Should return null for HTML < 500 chars", result)
    }

    @Test
    fun testExtractFromNavigationPage() {
        val html = """
            <html>
                <body>
                    <article>
                        <nav>
                            <ul>
                                <li><a href="/1">Link 1 with some text</a></li>
                                <li><a href="/2">Link 2 with some text</a></li>
                                <li><a href="/3">Link 3 with some text</a></li>
                                <li><a href="/4">Link 4 with some text</a></li>
                                <li><a href="/5">Link 5 with some text</a></li>
                            </ul>
                        </nav>
                        <p>Small content here.</p>
                    </article>
                </body>
            </html>
        """.trimIndent()
        
        val result = extractor.extract(html, "https://example.com")
        assertNull("Should return null for navigation-heavy content", result)
    }

    @Test
    fun testVietnameseEncoding() {
        val vietnameseText = "Chào mừng bạn đến với trình đọc RSS. Đây là nội dung tiếng Việt có dấu. ".repeat(10)
        val html = """
            <html>
                <body>
                    <article>
                        <p>$vietnameseText</p>
                    </article>
                </body>
            </html>
        """.trimIndent()
        
        val result = extractor.extract(html, "https://example.com")
        assertNotNull("Should extract Vietnamese content", result)
        assertTrue("Extracted content should preserve Vietnamese characters", result!!.contains("tiếng Việt"))
    }
}
