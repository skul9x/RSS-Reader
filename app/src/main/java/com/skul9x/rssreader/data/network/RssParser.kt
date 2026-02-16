package com.skul9x.rssreader.data.network

import com.skul9x.rssreader.data.model.NewsItem
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.security.MessageDigest

/**
 * RSS/Atom feed parser using OkHttp for network requests.
 * Supports both RSS 2.0 and Atom feed formats.
 */
class RssParser {

    companion object {
        // Pre-compiled Regex for performance (compiled once, reused many times)
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }

    // Use shared OkHttpClient singleton
    private val client = NetworkClient.okHttpClient

    /**
     * Fetch and parse an RSS feed from the given URL.
     * Uses browser-like headers to avoid being blocked by some servers.
     */
    suspend fun fetchFeed(feedUrl: String, feedId: Long = 0, feedName: String = ""): List<NewsItem> {
        val request = Request.Builder()
            .url(feedUrl)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "vi,en;q=0.9,en-US;q=0.8")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Upgrade-Insecure-Requests", "1")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string() ?: return emptyList()
                parseXml(xml, feedId, feedName)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseXml(xml: String, feedId: Long, feedName: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""
            var inItem = false
            var inEntry = false // For Atom feeds

            var title = ""
            var description = ""
            var content = ""
            var link = ""
            var pubDate = ""
            var imageUrl: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                        
                        when (currentTag) {
                            "item" -> inItem = true
                            "entry" -> inEntry = true // Atom format
                            "link" -> {
                                // Handle Atom link format
                                if (inEntry) {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (href != null && link.isEmpty()) {
                                        link = href
                                    }
                                }
                            }
                            "enclosure", "media:content", "media:thumbnail" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (url != null && imageUrl == null) {
                                    imageUrl = url
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if ((inItem || inEntry) && text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> title = cleanHtml(text)
                                "description", "summary" -> description = cleanHtml(text)
                                "content", "content:encoded" -> content = cleanHtml(text)
                                "link" -> if (link.isEmpty()) link = text
                                "pubdate", "published", "updated" -> pubDate = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name.lowercase()
                        if (tagName == "item" || tagName == "entry") {
                            if (title.isNotBlank()) {
                                items.add(
                                    NewsItem(
                                        id = generateId(link, title),
                                        title = title,
                                        description = description,
                                        content = content,
                                        link = link,
                                        pubDate = pubDate,
                                        imageUrl = imageUrl,
                                        feedId = feedId,
                                        feedName = feedName
                                    )
                                )
                            }
                            // Reset for next item
                            title = ""
                            description = ""
                            content = ""
                            link = ""
                            pubDate = ""
                            imageUrl = null
                            inItem = false
                            inEntry = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return items
    }

    /**
     * Clean HTML tags from text content using Android's native Html parser.
     * Much faster than multiple Regex replace calls and handles all HTML entities automatically.
     */
    private fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        
        // Use Android's Html.fromHtml which is implemented in native C++ for performance
        val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(html)
        }
        
        // Convert to string and normalize whitespace (including newlines from <br> tags)
        return spanned.toString()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    /**
     * Generate a unique ID for a news item based on link and title.
     */
    /**
     * Generate a unique ID for a news item based on link and title.
     * Normalizes the link to handle http/https, www/non-www, and trailing slash differences.
     */
    private fun generateId(link: String, title: String): String {
        // Normalize link: remove protocol, www, and trailing slash
        val normalizedLink = link
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
            
        val input = "$normalizedLink$title"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

