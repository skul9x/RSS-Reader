package com.skul9x.rssreader.data.network

/**
 * Data class containing both title and content from an article.
 */
data class ArticleData(
    val title: String,
    val content: String
)

/**
 * Structure for a potential content container found in HTML
 */
data class ContentCandidate(
    val selector: String,
    val className: String,
    val idName: String,
    val textLength: Int,
    val previewText: String,
    val fullText: String,
    val score: Int
)
