package org.jetbrains.koog.cyberwave.data.wikipedia

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult

class MediaWikiWikipediaClient(
    private val httpClient: HttpClient,
    private val apiJson: Json = DefaultApiJson,
    private val wikiBaseUrl: String = DEFAULT_WIKI_BASE_URL,
) : WikipediaClient {
    private val normalizedWikiBaseUrl = wikiBaseUrl.trimEnd('/')

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<WikipediaSearchResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }

        val responseText =
            httpClient.get("$normalizedWikiBaseUrl/w/api.php") {
                parameter("action", "query")
                parameter("list", "search")
                parameter("srsearch", normalizedQuery)
                parameter("srnamespace", ARTICLE_NAMESPACE)
                parameter("srlimit", limit.coerceIn(MIN_SEARCH_LIMIT, MAX_SEARCH_LIMIT))
                parameter("srprop", "snippet")
                parameter("format", "json")
                parameter("formatversion", FORMAT_VERSION)
                parameter("origin", ALLOW_ALL_ORIGINS)
            }.bodyAsText()

        val response = apiJson.decodeFromString<SearchResponse>(responseText)
        return response.query
            ?.search
            .orEmpty()
            .map { result -> result.toDomainModel(normalizedWikiBaseUrl) }
    }

    override suspend fun fetchArticleSummary(title: String): WikipediaArticleSummary {
        throw UnsupportedOperationException("Article summary fetch is implemented in task T022.")
    }

    override suspend fun fetchArticle(title: String): WikipediaArticle {
        throw UnsupportedOperationException("Article fetch is implemented in task T022.")
    }

    private fun SearchResultDto.toDomainModel(baseUrl: String): WikipediaSearchResult {
        val sanitizedSnippet = sanitizeSnippet(snippet)
        return WikipediaSearchResult(
            pageId = pageid,
            title = title,
            snippet = sanitizedSnippet,
            canonicalUrl = buildCanonicalUrl(baseUrl = baseUrl, title = title),
            isDisambiguationHint = looksLikeDisambiguation(title = title, snippet = sanitizedSnippet),
        )
    }

    private fun sanitizeSnippet(rawSnippet: String): String =
        rawSnippet
            .replace(HtmlTagPattern, "")
            .replace(HtmlNumericEntityPattern) { match ->
                decodeNumericEntity(match.value)
            }
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace(WhitespacePattern, " ")
            .trim()

    private fun decodeNumericEntity(entity: String): String {
        val payload = entity.removePrefix("&#").removeSuffix(";")
        val codePoint =
            if (payload.startsWith('x', ignoreCase = true)) {
                payload.drop(1).toIntOrNull(radix = 16)
            } else {
                payload.toIntOrNull()
            }

        return codePoint
            ?.takeIf { it in Char.MIN_VALUE.code..Char.MAX_VALUE.code }
            ?.toChar()
            ?.toString()
            ?: entity
    }

    private fun buildCanonicalUrl(
        baseUrl: String,
        title: String,
    ): String {
        val normalizedTitle =
            title
                .trim()
                .replace(' ', '_')
                .split('/')
                .joinToString(separator = "/") { segment -> encodePathSegment(segment) }

        return "$baseUrl/wiki/$normalizedTitle"
    }

    private fun encodePathSegment(segment: String): String {
        val builder = StringBuilder(segment.length)
        segment.encodeToByteArray().forEach { byte ->
            val unsignedByte = byte.toInt() and 0xff
            when {
                unsignedByte in 0x30..0x39 ||
                    unsignedByte in 0x41..0x5A ||
                    unsignedByte in 0x61..0x7A ||
                    unsignedByte == '_'.code ||
                    unsignedByte == '-'.code ||
                    unsignedByte == '.'.code ||
                    unsignedByte == '~'.code ->
                    builder.append(unsignedByte.toChar())

                else -> {
                    builder.append('%')
                    builder.append(unsignedByte.toString(radix = 16).uppercase().padStart(length = 2, padChar = '0'))
                }
            }
        }
        return builder.toString()
    }

    private fun looksLikeDisambiguation(
        title: String,
        snippet: String,
    ): Boolean {
        val normalizedTitle = title.lowercase()
        val normalizedSnippet = snippet.lowercase()
        return normalizedTitle.endsWith("(disambiguation)") ||
            "disambiguation page" in normalizedSnippet ||
            normalizedSnippet.startsWith("may refer to") ||
            "may refer to:" in normalizedSnippet
    }

    @Serializable
    private data class SearchResponse(
        val query: SearchQueryDto? = null,
    )

    @Serializable
    private data class SearchQueryDto(
        val search: List<SearchResultDto> = emptyList(),
    )

    @Serializable
    private data class SearchResultDto(
        val title: String,
        val snippet: String = "",
        @SerialName("pageid")
        val pageid: Long? = null,
    )

    private companion object {
        private const val ARTICLE_NAMESPACE = 0
        private const val FORMAT_VERSION = 2
        private const val ALLOW_ALL_ORIGINS = "*"
        private const val MIN_SEARCH_LIMIT = 1
        private const val MAX_SEARCH_LIMIT = 500

        private val DefaultApiJson =
            Json {
                ignoreUnknownKeys = true
            }

        private val HtmlTagPattern = Regex("<[^>]+>")
        private val HtmlNumericEntityPattern = Regex("&#x?[0-9A-Fa-f]+;")
        private val WhitespacePattern = Regex("\\s+")
    }
}

const val DEFAULT_WIKI_BASE_URL: String = "https://en.wikipedia.org"
