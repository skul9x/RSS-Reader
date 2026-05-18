package com.skul9x.rssreader.data.network.gemini

import kotlinx.serialization.Serializable

/**
 * Representation of a dynamically configured Gemini model.
 */
@Serializable
data class DynamicModel(
    val modelId: String,
    val displayName: String
)
