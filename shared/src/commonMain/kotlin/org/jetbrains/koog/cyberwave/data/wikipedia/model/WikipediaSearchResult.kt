package org.jetbrains.koog.cyberwave.data.wikipedia.model

import kotlinx.serialization.Serializable

@Serializable
data class WikipediaSearchResult(
    val pageId: Long? = null,
    val title: String,
    val snippet: String,
    val canonicalUrl: String? = null,
    val isDisambiguationHint: Boolean = false,
) {
    val hasSnippet: Boolean
        get() = snippet.isNotBlank()
}
