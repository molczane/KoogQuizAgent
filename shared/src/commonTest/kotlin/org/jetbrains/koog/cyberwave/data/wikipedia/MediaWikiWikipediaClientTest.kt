package org.jetbrains.koog.cyberwave.data.wikipedia

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
