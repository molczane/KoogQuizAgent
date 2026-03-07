package org.jetbrains.koog.cyberwave.agent.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult

class WikipediaToolsTest {
    @Test
    fun `search tool delegates to wikipedia client and returns wrapped results`() =
        runTest {
            val client =
                FakeWikipediaClient(
                    searchResults =
                        listOf(
                            WikipediaSearchResult(
                                pageId = 736,
                                title = "Coroutine",
                                snippet = "A coroutine is a generalized subroutine.",
                                canonicalUrl = "https://en.wikipedia.org/wiki/Coroutine",
                                isDisambiguationHint = false,
                            ),
                        ),
                )

            val result =
                SearchWikipediaTool(client).execute(
                    SearchWikipediaTool.Args(
                        topic = " Coroutine ",
                        limit = 3,
                    ),
                )

            assertEquals("Coroutine", client.lastSearchQuery)
            assertEquals(3, client.lastSearchLimit)
            assertEquals("Coroutine", result.topic)
            assertEquals(1, result.results.size)
        }

    @Test
    fun `search tool validates topic and limit`() {
        assertFailsWith<IllegalArgumentException> {
            SearchWikipediaTool.Args(topic = " ", limit = 3)
        }

        assertFailsWith<IllegalArgumentException> {
            SearchWikipediaTool.Args(topic = "Coroutine", limit = 6)
        }
    }

    @Test
    fun `summary tool delegates to wikipedia client`() =
        runTest {
            val summary =
                WikipediaArticleSummary(
                    pageId = 736,
                    title = "Coroutine",
                    canonicalUrl = "https://en.wikipedia.org/wiki/Coroutine",
                    description = "Program component",
                    extract = "A coroutine is a generalized subroutine.",
                    thumbnailUrl = null,
                    isDisambiguation = false,
                )
            val client = FakeWikipediaClient(summary = summary)

            val result =
                FetchWikipediaSummaryTool(client).execute(
                    FetchWikipediaSummaryTool.Args(title = "Coroutine"),
                )

            assertEquals("Coroutine", client.lastSummaryTitle)
            assertEquals(summary, result)
        }

    @Test
    fun `article tool delegates to wikipedia client`() =
        runTest {
            val article =
                WikipediaArticle(
                    summary =
                        WikipediaArticleSummary(
                            pageId = 736,
                            title = "Coroutine",
                            canonicalUrl = "https://en.wikipedia.org/wiki/Coroutine",
                            description = "Program component",
                            extract = "A coroutine is a generalized subroutine.",
                            thumbnailUrl = null,
                            isDisambiguation = false,
                        ),
                    plainTextContent = "A coroutine is a generalized subroutine used for cooperative multitasking.",
                )
            val client = FakeWikipediaClient(article = article)

            val result =
                FetchWikipediaArticleTool(client).execute(
                    FetchWikipediaArticleTool.Args(title = "Coroutine"),
                )

            assertEquals("Coroutine", client.lastArticleTitle)
            assertEquals(article, result)
        }

    @Test
    fun `wikipedia tool registry exposes all phase three tools`() {
        val registry = wikipediaToolRegistry(FakeWikipediaClient())

        assertEquals(3, registry.tools.size)
        assertIs<SearchWikipediaTool>(registry.getTool("search_wikipedia"))
        assertIs<FetchWikipediaSummaryTool>(registry.getTool("fetch_wikipedia_summary"))
        assertIs<FetchWikipediaArticleTool>(registry.getTool("fetch_wikipedia_article"))
    }

    private class FakeWikipediaClient(
        private val searchResults: List<WikipediaSearchResult> = emptyList(),
        private val summary: WikipediaArticleSummary =
            WikipediaArticleSummary(
                pageId = 1,
                title = "Default",
                canonicalUrl = "https://en.wikipedia.org/wiki/Default",
                description = null,
                extract = "Default extract",
                thumbnailUrl = null,
                isDisambiguation = false,
            ),
        private val article: WikipediaArticle =
            WikipediaArticle(
                summary =
                    WikipediaArticleSummary(
                        pageId = 1,
                        title = "Default",
                        canonicalUrl = "https://en.wikipedia.org/wiki/Default",
                        description = null,
                        extract = "Default extract",
                        thumbnailUrl = null,
                        isDisambiguation = false,
                    ),
                plainTextContent = "Default article content",
            ),
    ) : WikipediaClient {
        var lastSearchQuery: String? = null
            private set
        var lastSearchLimit: Int? = null
            private set
        var lastSummaryTitle: String? = null
            private set
        var lastArticleTitle: String? = null
            private set

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<WikipediaSearchResult> {
            lastSearchQuery = query.trim()
            lastSearchLimit = limit
            return searchResults
        }

        override suspend fun fetchArticleSummary(title: String): WikipediaArticleSummary {
            lastSummaryTitle = title
            return summary
        }

        override suspend fun fetchArticle(title: String): WikipediaArticle {
            lastArticleTitle = title
            return article
        }
    }
}
