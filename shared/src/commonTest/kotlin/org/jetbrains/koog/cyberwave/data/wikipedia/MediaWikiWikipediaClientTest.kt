package org.jetbrains.koog.cyberwave.data.wikipedia

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MediaWikiWikipediaClientTest {
    @Test
    fun `search maps mediawiki response and normalizes snippets`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("en.wikipedia.org", request.url.host)
                    assertEquals("/w/api.php", request.url.encodedPath)
                    assertEquals("query", request.url.parameters["action"])
                    assertEquals("search", request.url.parameters["list"])
                    assertEquals("Coroutine", request.url.parameters["srsearch"])
                    assertEquals("3", request.url.parameters["srlimit"])
                    assertEquals("0", request.url.parameters["srnamespace"])
                    assertEquals("json", request.url.parameters["format"])
                    assertEquals("2", request.url.parameters["formatversion"])
                    assertEquals("*", request.url.parameters["origin"])

                    respond(
                        content =
                            """
                            {
                              "query": {
                                "search": [
                                  {
                                    "pageid": 736,
                                    "title": "Coroutine",
                                    "snippet": "A <span class=\"searchmatch\">coroutine</span> is a generalized subroutine for cooperative multitasking &amp; concurrency."
                                  },
                                  {
                                    "pageid": 737,
                                    "title": "Mercury (disambiguation)",
                                    "snippet": "Mercury may refer to: multiple uses on this <span class=\"searchmatch\">disambiguation</span> page."
                                  }
                                ]
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val httpClient = HttpClient(engine)
            val wikipediaClient = MediaWikiWikipediaClient(httpClient = httpClient)

            val results = wikipediaClient.search(query = "Coroutine", limit = 3)

            assertEquals(2, results.size)

            assertEquals(736L, results[0].pageId)
            assertEquals("Coroutine", results[0].title)
            assertEquals(
                "A coroutine is a generalized subroutine for cooperative multitasking & concurrency.",
                results[0].snippet,
            )
            assertEquals("https://en.wikipedia.org/wiki/Coroutine", results[0].canonicalUrl)
            assertFalse(results[0].isDisambiguationHint)

            assertEquals("Mercury (disambiguation)", results[1].title)
            assertTrue(results[1].isDisambiguationHint)
        }

    @Test
    fun `search returns empty list for blank query without network access`() =
        runTest {
            val httpClient =
                HttpClient(
                    MockEngine {
                        error("Search should not call the network when the query is blank.")
                    },
                )

            val wikipediaClient = MediaWikiWikipediaClient(httpClient = httpClient)

            val results = wikipediaClient.search(query = "   ", limit = 5)

            assertTrue(results.isEmpty())
        }

    @Test
    fun `search clamps limit to mediawiki maximum`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("500", request.url.parameters["srlimit"])
                    respond(
                        content = """{"query":{"search":[]}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val wikipediaClient = MediaWikiWikipediaClient(httpClient = HttpClient(engine))

            val results = wikipediaClient.search(query = "Coroutine", limit = 999)

            assertTrue(results.isEmpty())
        }

    @Test
    fun `fetch article summary requests intro extract and maps metadata`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("en.wikipedia.org", request.url.host)
                    assertEquals("/w/api.php", request.url.encodedPath)
                    assertEquals("query", request.url.parameters["action"])
                    assertEquals("Coroutine", request.url.parameters["titles"])
                    assertEquals("1", request.url.parameters["redirects"])
                    assertEquals("extracts|info|pageimages|pageprops", request.url.parameters["prop"])
                    assertEquals("url", request.url.parameters["inprop"])
                    assertEquals("thumbnail", request.url.parameters["piprop"])
                    assertEquals("320", request.url.parameters["pithumbsize"])
                    assertEquals("disambiguation|description|wikibase-shortdesc", request.url.parameters["ppprop"])
                    assertEquals("1", request.url.parameters["exintro"])
                    assertEquals("1", request.url.parameters["explaintext"])
                    assertEquals("plain", request.url.parameters["exsectionformat"])

                    respondJson(
                        """
                        {
                          "query": {
                            "pages": [
                              {
                                "pageid": 736,
                                "title": "Coroutine",
                                "extract": "A coroutine is a generalized subroutine.\n\nIt supports cooperative multitasking.",
                                "fullurl": "https://en.wikipedia.org/wiki/Coroutine",
                                "thumbnail": {
                                  "source": "https://upload.wikimedia.org/coroutine.png"
                                },
                                "pageprops": {
                                  "wikibase-shortdesc": "Program component for cooperative multitasking"
                                }
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    )
                }

            val wikipediaClient = MediaWikiWikipediaClient(httpClient = HttpClient(engine))

            val summary = wikipediaClient.fetchArticleSummary("Coroutine")

            assertEquals(736L, summary.pageId)
            assertEquals("Coroutine", summary.title)
            assertEquals("https://en.wikipedia.org/wiki/Coroutine", summary.canonicalUrl)
            assertEquals("Program component for cooperative multitasking", summary.description)
            assertEquals(
                "A coroutine is a generalized subroutine.\n\nIt supports cooperative multitasking.",
                summary.extract,
            )
            assertEquals("https://upload.wikimedia.org/coroutine.png", summary.thumbnailUrl)
            assertFalse(summary.isDisambiguation)
        }

    @Test
    fun `fetch article returns full plain text content and disambiguation metadata`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("Mercury", request.url.parameters["titles"])
                    assertEquals(null, request.url.parameters["exintro"])

                    respondJson(
                        """
                        {
                          "query": {
                            "pages": [
                              {
                                "pageid": 737,
                                "title": "Mercury (disambiguation)",
                                "extract": "Mercury may refer to:\n\nAstronomy\nMercury is the first planet from the Sun.\n\nChemistry\nMercury is a chemical element.",
                                "pageprops": {
                                  "disambiguation": "",
                                  "description": "Wikimedia disambiguation page"
                                }
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    )
                }

            val wikipediaClient = MediaWikiWikipediaClient(httpClient = HttpClient(engine))

            val article = wikipediaClient.fetchArticle("Mercury")

            assertEquals(737L, article.summary.pageId)
            assertEquals("Mercury (disambiguation)", article.summary.title)
            assertEquals(
                "https://en.wikipedia.org/wiki/Mercury_%28disambiguation%29",
                article.summary.canonicalUrl,
            )
            assertEquals("Wikimedia disambiguation page", article.summary.description)
            assertTrue(article.summary.isDisambiguation)
            assertEquals(
                "Mercury may refer to:\n\nAstronomy\nMercury is the first planet from the Sun.\n\nChemistry\nMercury is a chemical element.",
                article.plainTextContent,
            )
            assertTrue(article.hasContent)
        }

    @Test
    fun `fetch article summary throws when page is missing`() =
        runTest {
            val engine =
                MockEngine {
                    respondJson(
                        """
                        {
                          "query": {
                            "pages": [
                              {
                                "title": "Missing Topic",
                                "missing": true
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    )
                }

            val wikipediaClient = MediaWikiWikipediaClient(httpClient = HttpClient(engine))

            val error =
                assertFailsWith<NoSuchElementException> {
                    wikipediaClient.fetchArticleSummary("Missing Topic")
                }

            assertEquals("Wikipedia page not found: Missing Topic", error.message)
        }

    @Test
    fun `fetch article summary rejects blank title`() =
        runTest {
            val wikipediaClient =
                MediaWikiWikipediaClient(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                error("Fetch should not call the network when the title is blank.")
                            },
                        ),
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    wikipediaClient.fetchArticleSummary("   ")
                }

            assertEquals("Wikipedia title must not be blank.", error.message)
        }

    private fun MockRequestHandleScope.respondJson(content: String) =
        respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
}
