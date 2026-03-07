package org.jetbrains.koog.cyberwave.data.wikipedia.model

import kotlinx.serialization.Serializable

@Serializable
data class WikipediaArticleSummary(
    val pageId: Long? = null,
    val title: String,
    val canonicalUrl: String,
    val description: String? = null,
    val extract: String,
    val thumbnailUrl: String? = null,
    val isDisambiguation: Boolean = false,
) {
    val hasExtract: Boolean
        get() = extract.isNotBlank()
}
