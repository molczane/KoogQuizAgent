package org.jetbrains.koog.cyberwave.data.wikipedia.model

import kotlinx.serialization.Serializable

@Serializable
data class WikipediaArticle(
    val summary: WikipediaArticleSummary,
    val plainTextContent: String,
) {
    val hasContent: Boolean
        get() = plainTextContent.isNotBlank()
}
