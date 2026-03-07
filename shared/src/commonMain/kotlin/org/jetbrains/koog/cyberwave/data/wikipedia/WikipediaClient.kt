package org.jetbrains.koog.cyberwave.data.wikipedia

import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult

interface WikipediaClient {
    suspend fun search(
        query: String,
        limit: Int,
    ): List<WikipediaSearchResult>

    suspend fun fetchArticleSummary(title: String): WikipediaArticleSummary

    suspend fun fetchArticle(title: String): WikipediaArticle
}
