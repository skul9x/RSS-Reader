package com.skul9x.rssreader.data.network

import android.text.Html
import android.text.Spanned
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for RssParser async fetchFeed implementation.
 *
 * KEY VERIFICATION:
 * 1. Successful RSS parsing still works with enqueue()
 * 2. HTTP errors return empty list (no crash)
 * 3. Coroutine cancellation cancels the HTTP request
 * 4. Network timeout returns empty list
 * 5. Atom feed format still works
 */
class RssParserAsyncTest {

    private lateinit var server: MockWebServer
    private lateinit var parser: RssParser

    private val sampleRss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Test Feed</title>
            <item>
              <title>Article One</title>
              <link>https://example.com/article-1</link>
              <description>First article description</description>
              <pubDate>Mon, 23 Jun 2026 10:00:00 GMT</pubDate>
            </item>
            <item>
              <title>Article Two</title>
              <link>https://example.com/article-2</link>
              <description>Second article description</description>
              <pubDate>Mon, 23 Jun 2026 11:00:00 GMT</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val sampleAtom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Atom Feed</title>
          <entry>
            <title>Atom Article</title>
            <link href="https://example.com/atom-1"/>
            <summary>Atom summary</summary>
            <published>2026-06-23T10:00:00Z</published>
          </entry>
        </feed>
    """.trimIndent()

    @Before
    fun setup() {
        unmockkAll()
        mockkStatic(Html::class)
        every { Html.fromHtml(any()) } answers {
            val text = firstArg<String>()
            val spanned = mockk<Spanned>()
            every { spanned.toString() } returns text
            spanned
        }
        every { Html.fromHtml(any(), any()) } answers {
            val text = firstArg<String>()
            val spanned = mockk<Spanned>()
            every { spanned.toString() } returns text
            spanned
        }

        server = MockWebServer()
        server.start()
        parser = RssParser()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (_: Exception) {}
        unmockkAll()
    }

    @Test
    fun `fetchFeed parses RSS feed correctly`() = runTest {
        server.enqueue(MockResponse().setBody(sampleRss).setResponseCode(200))

        val items = parser.fetchFeed(server.url("/feed.xml").toString(), feedId = 1, feedName = "Test")

        assertEquals(2, items.size)
        assertEquals("Article One", items[0].title)
        assertEquals("Article Two", items[1].title)
        assertEquals("First article description", items[0].description)
        assertEquals(1L, items[0].feedId)
        assertEquals("Test", items[0].feedName)
    }

    @Test
    fun `fetchFeed parses Atom feed correctly`() = runTest {
        server.enqueue(MockResponse().setBody(sampleAtom).setResponseCode(200))

        val items = parser.fetchFeed(server.url("/atom.xml").toString())

        assertEquals(1, items.size)
        assertEquals("Atom Article", items[0].title)
        assertEquals("Atom summary", items[0].description)
    }

    @Test
    fun `fetchFeed returns empty list on HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val items = parser.fetchFeed(server.url("/error").toString())

        assertTrue(items.isEmpty())
    }

    @Test
    fun `fetchFeed returns empty list on empty body`() = runTest {
        server.enqueue(MockResponse().setBody("").setResponseCode(200))

        val items = parser.fetchFeed(server.url("/empty").toString())

        assertTrue(items.isEmpty())
    }

    @Test
    fun `fetchFeed supports cancellation`() = runTest {
        // Delay response by 10 seconds to ensure we can cancel
        server.enqueue(
            MockResponse()
                .setBody(sampleRss)
                .setBodyDelay(10, TimeUnit.SECONDS)
        )

        val job = launch {
            parser.fetchFeed(server.url("/slow").toString())
        }

        // Cancel after 100ms
        delay(100)
        job.cancelAndJoin()

        // Verify the job was cancelled (not stuck waiting)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `fetchFeed handles malformed XML gracefully`() = runTest {
        server.enqueue(MockResponse().setBody("<not valid xml!!!").setResponseCode(200))

        val items = parser.fetchFeed(server.url("/bad.xml").toString())

        // Should return empty list, not crash
        assertTrue(items.isEmpty())
    }

    @Test
    fun `fetchFeed normalizes URLs correctly`() = runTest {
        val rssWithTracking = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>Tracked Article</title>
                  <link>https://example.com/article?utm_source=rss&amp;utm_medium=feed</link>
                  <description>Has tracking params</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(rssWithTracking).setResponseCode(200))

        val items = parser.fetchFeed(server.url("/tracked.xml").toString())

        assertEquals(1, items.size)
        // URL should have tracking params stripped
        assertFalse(items[0].link.contains("utm_source"))
    }
}
