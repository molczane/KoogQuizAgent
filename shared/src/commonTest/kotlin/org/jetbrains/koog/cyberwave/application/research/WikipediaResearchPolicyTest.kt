package org.jetbrains.koog.cyberwave.application.research

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult

class WikipediaResearchPolicyTest {
    @Test
    fun `select articles prefers exact and non disambiguation results within top five`() {
        val selected =
            WikipediaResearchPolicy.selectArticles(
                topic = "Coroutine",
                searchResults =
                    listOf(
                        searchResult(title = "Coroutines in computing", snippet = "Coroutines in computing are lightweight."),
                        searchResult(title = "Coroutine", snippet = "A coroutine is a generalized subroutine."),
                        searchResult(
                            title = "Coroutine (disambiguation)",
                            snippet = "Coroutine may refer to this disambiguation page.",
                            isDisambiguationHint = true,
                        ),
                        searchResult(title = "Coroutine pattern", snippet = "Coroutine pattern usage."),
                        searchResult(title = "Subroutine", snippet = "Subroutines are callable units."),
                        searchResult(title = "Coroutine library", snippet = "Should be ignored because it is outside top five."),
                    ),
            )

        assertEquals(
            listOf("Coroutine", "Coroutine pattern", "Coroutines in computing"),
            selected.map(SelectedWikipediaArticle::title),
        )
    }

    @Test
    fun `select articles falls back to best disambiguation result when no better option exists`() {
        val selected =
            WikipediaResearchPolicy.selectArticles(
                topic = "Mercury",
                searchResults =
                    listOf(
                        searchResult(
                            title = "Mercury (disambiguation)",
                            snippet = "Mercury may refer to multiple topics on this disambiguation page.",
                            isDisambiguationHint = true,
                        ),
                        searchResult(
                            title = "Mercury disambiguation",
                            snippet = "Another disambiguation page for Mercury.",
                            isDisambiguationHint = true,
                        ),
                    ),
            )

        assertEquals(1, selected.size)
        assertEquals("Mercury (disambiguation)", selected.single().title)
    }

    @Test
    fun `select articles deduplicates repeated page ids before ranking`() {
        val selected =
            WikipediaResearchPolicy.selectArticles(
                topic = "Kotlin",
                searchResults =
                    listOf(
                        searchResult(
                            pageId = 1L,
                            title = "Kotlin",
                            snippet = "Strong exact match for Kotlin.",
                        ),
                        searchResult(
                            pageId = 1L,
                            title = "Kotlin language",
                            snippet = "Duplicate page id should be ignored.",
                        ),
                        searchResult(
                            pageId = 2L,
                            title = "Kotlin coroutine",
                            snippet = "Distinct article that should remain after deduplication.",
                        ),
                    ),
            )

        assertEquals(listOf("Kotlin", "Kotlin coroutine"), selected.map(SelectedWikipediaArticle::title))
    }

    @Test
    fun `evidence assessment is sufficient when all topics have enough usable content`() {
        val assessment =
            WikipediaResearchPolicy.assessEvidence(
                requestedTopics = listOf("Coroutine", "Structured concurrency"),
                requestedQuestionCount = 4,
                materials =
                    listOf(
                        TopicResearchMaterial(
                            topic = "Coroutine",
                            articles =
                                listOf(
                                    article(
                                        pageId = 736,
                                        title = "Coroutine",
                                        words = 950,
                                        description = "Generalized subroutine for cooperative multitasking",
                                    ),
                                ),
                        ),
                        TopicResearchMaterial(
                            topic = "Structured concurrency",
                            articles =
                                listOf(
                                    article(
                                        pageId = 1701,
                                        title = "Structured concurrency",
                                        words = 420,
                                        description = "Concurrency model with scoped lifetimes",
                                    ),
                                ),
                        ),
                    ),
            )

        assertEquals(EvidenceStatus.SUFFICIENT, assessment.status)
        assertEquals(4, assessment.recommendedQuestionCount)
        assertEquals(listOf("Coroutine", "Structured concurrency"), assessment.coveredTopics)
        assertTrue(assessment.missingTopics.isEmpty())
        assertEquals(listOf("wiki-736", "wiki-1701"), assessment.usableSources.map { source -> source.id })
    }

    @Test
    fun `evidence assessment limits question count when content is usable but shallow`() {
        val assessment =
            WikipediaResearchPolicy.assessEvidence(
                requestedTopics = listOf("Coroutine"),
                requestedQuestionCount = 3,
                materials =
                    listOf(
                        TopicResearchMaterial(
                            topic = "Coroutine",
                            articles =
                                listOf(
                                    article(
                                        pageId = 736,
                                        title = "Coroutine",
                                        words = 180,
                                    ),
                                ),
                        ),
                    ),
            )

        assertEquals(EvidenceStatus.LIMITED, assessment.status)
        assertEquals(1, assessment.recommendedQuestionCount)
        assertEquals(listOf("Coroutine"), assessment.coveredTopics)
        assertTrue(assessment.missingTopics.isEmpty())
    }

    @Test
    fun `evidence assessment is insufficient when any topic lacks usable non disambiguation evidence`() {
        val assessment =
            WikipediaResearchPolicy.assessEvidence(
                requestedTopics = listOf("Coroutine", "Mercury"),
                requestedQuestionCount = 2,
                materials =
                    listOf(
                        TopicResearchMaterial(
                            topic = "Coroutine",
                            articles = listOf(article(pageId = 736, title = "Coroutine", words = 200)),
                        ),
                        TopicResearchMaterial(
                            topic = "Mercury",
                            articles =
                                listOf(
                                    article(
                                        pageId = 737,
                                        title = "Mercury (disambiguation)",
                                        words = 400,
                                        isDisambiguation = true,
                                    ),
                                ),
                        ),
                    ),
            )

        assertEquals(EvidenceStatus.INSUFFICIENT, assessment.status)
        assertEquals(0, assessment.recommendedQuestionCount)
        assertEquals(listOf("Coroutine"), assessment.coveredTopics)
        assertEquals(listOf("Mercury"), assessment.missingTopics)
    }

    @Test
    fun `evidence assessment deduplicates repeated articles across topics when building sources`() {
        val sharedArticle = article(pageId = 999, title = "Coroutine", words = 950)

        val assessment =
            WikipediaResearchPolicy.assessEvidence(
                requestedTopics = listOf("Coroutine", "Kotlin coroutine"),
                requestedQuestionCount = 2,
                materials =
                    listOf(
                        TopicResearchMaterial(topic = "Coroutine", articles = listOf(sharedArticle)),
                        TopicResearchMaterial(topic = "Kotlin coroutine", articles = listOf(sharedArticle)),
                    ),
            )

        assertEquals(EvidenceStatus.SUFFICIENT, assessment.status)
        assertEquals(1, assessment.usableSources.size)
        assertEquals("wiki-999", assessment.usableSources.single().id)
    }

    private fun searchResult(
        pageId: Long? = null,
        title: String,
        snippet: String,
        isDisambiguationHint: Boolean = false,
    ): WikipediaSearchResult =
        WikipediaSearchResult(
            pageId = pageId,
            title = title,
            snippet = snippet,
            canonicalUrl = "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}",
            isDisambiguationHint = isDisambiguationHint,
        )

    private fun article(
        pageId: Long,
        title: String,
        words: Int,
        description: String? = null,
        isDisambiguation: Boolean = false,
    ): WikipediaArticle {
        val text = List(words) { "word${it + 1}" }.joinToString(separator = " ")
        return WikipediaArticle(
            summary =
                WikipediaArticleSummary(
                    pageId = pageId,
                    title = title,
                    canonicalUrl = "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}",
                    description = description,
                    extract = text,
                    thumbnailUrl = null,
                    isDisambiguation = isDisambiguation,
                ),
            plainTextContent = text,
        )
    }
}
