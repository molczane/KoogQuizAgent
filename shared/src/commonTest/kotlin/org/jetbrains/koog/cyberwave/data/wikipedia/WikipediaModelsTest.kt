package org.jetbrains.koog.cyberwave.data.wikipedia

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult

class WikipediaModelsTest {
    private val json = Json

    @Test
    fun `wikipedia article is serializable`() {
        val article =
            WikipediaArticle(
                summary =
                    WikipediaArticleSummary(
                        pageId = 736,
                        title = "Coroutine",
                        canonicalUrl = "https://en.wikipedia.org/wiki/Coroutine",
                        description = "Program component for cooperative multitasking",
                        extract = "A coroutine is a generalized subroutine used for cooperative multitasking.",
                        thumbnailUrl = null,
                        isDisambiguation = false,
                    ),
                plainTextContent = "A coroutine is a generalized subroutine used for cooperative multitasking.",
            )

        val encoded = json.encodeToString(WikipediaArticle.serializer(), article)
        val decoded = json.decodeFromString(WikipediaArticle.serializer(), encoded)

        assertContains(encoded, "\"title\":\"Coroutine\"")
        assertEquals(article, decoded)
        assertTrue(decoded.hasContent)
        assertTrue(decoded.summary.hasExtract)
    }

    @Test
    fun `search result exposes snippet availability`() {
        val resultWithSnippet =
            WikipediaSearchResult(
                pageId = 123,
                title = "Coroutine",
                snippet = "Generalized program component.",
                canonicalUrl = "https://en.wikipedia.org/wiki/Coroutine",
                isDisambiguationHint = false,
            )
        val emptySnippetResult =
            WikipediaSearchResult(
                pageId = 124,
                title = "Coroutine",
                snippet = "",
            )

        assertTrue(resultWithSnippet.hasSnippet)
        assertFalse(emptySnippetResult.hasSnippet)
    }
}
