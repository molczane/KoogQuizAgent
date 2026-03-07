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
        val page = fetchPageContent(title = title, summaryOnly = true)
        return page.toSummary(baseUrl = normalizedWikiBaseUrl)
    }

    override suspend fun fetchArticle(title: String): WikipediaArticle {
        val page = fetchPageContent(title = title, summaryOnly = false)
        return WikipediaArticle(
            summary = page.toSummary(baseUrl = normalizedWikiBaseUrl),
            plainTextContent = normalizePageText(page.extract),
        )
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

    private suspend fun fetchPageContent(
        title: String,
        summaryOnly: Boolean,
    ): QueryPageDto {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Wikipedia title must not be blank." }

        val responseText =
            httpClient.get("$normalizedWikiBaseUrl/w/api.php") {
                parameter("action", "query")
                parameter("titles", normalizedTitle)
                parameter("redirects", "1")
                parameter("prop", "extracts|info|pageimages|pageprops")
                parameter("inprop", "url")
                parameter("piprop", "thumbnail")
                parameter("pithumbsize", SUMMARY_THUMBNAIL_SIZE)
                parameter("ppprop", "disambiguation|description|wikibase-shortdesc")
                parameter("explaintext", "1")
                parameter("exsectionformat", "plain")
                if (summaryOnly) {
                    parameter("exintro", "1")
                }
                parameter("format", "json")
                parameter("formatversion", FORMAT_VERSION)
                parameter("origin", ALLOW_ALL_ORIGINS)
            }.bodyAsText()

        val response = apiJson.decodeFromString<PageQueryResponse>(responseText)
        val page = response.query?.pages.orEmpty().firstOrNull()
            ?: throw NoSuchElementException("Wikipedia page not found: $normalizedTitle")

        if (page.missing == true) {
            throw NoSuchElementException("Wikipedia page not found: ${page.title ?: normalizedTitle}")
        }

        if (page.invalidreason != null) {
            throw IllegalArgumentException(page.invalidreason)
        }

        return page
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

    private fun normalizePageText(rawText: String): String =
        rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString(separator = "\n") { line -> line.trimEnd() }
            .replace(ThreeOrMoreLineBreaksPattern, "\n\n")
            .trim()

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

    private fun QueryPageDto.toSummary(baseUrl: String): WikipediaArticleSummary {
        val normalizedExtract = normalizePageText(extract)
        val resolvedTitle = title ?: throw IllegalStateException("Wikipedia response did not include a title.")
        val description =
            pageprops["wikibase-shortdesc"]
                ?: pageprops["description"]
                ?: pageprops["wikibase_description"]

        return WikipediaArticleSummary(
            pageId = pageid,
            title = resolvedTitle,
            canonicalUrl = fullurl ?: buildCanonicalUrl(baseUrl = baseUrl, title = resolvedTitle),
            description = description?.trim()?.ifBlank { null },
            extract = normalizedExtract,
            thumbnailUrl = thumbnail?.source,
            isDisambiguation = "disambiguation" in pageprops || looksLikeDisambiguation(resolvedTitle, normalizedExtract),
        )
    }

    @Serializable
    private data class PageQueryResponse(
        val query: PageQueryDto? = null,
    )

    @Serializable
    private data class PageQueryDto(
        val pages: List<QueryPageDto> = emptyList(),
    )

    @Serializable
    private data class QueryPageDto(
        @SerialName("pageid")
        val pageid: Long? = null,
        val title: String? = null,
        val extract: String = "",
        val fullurl: String? = null,
        val thumbnail: ThumbnailDto? = null,
        val pageprops: Map<String, String> = emptyMap(),
        val missing: Boolean? = null,
        val invalidreason: String? = null,
    )

    @Serializable
    private data class ThumbnailDto(
        val source: String,
    )

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
        private const val SUMMARY_THUMBNAIL_SIZE = 320

        private val DefaultApiJson =
            Json {
                ignoreUnknownKeys = true
            }

        private val HtmlTagPattern = Regex("<[^>]+>")
        private val HtmlNumericEntityPattern = Regex("&#x?[0-9A-Fa-f]+;")
        private val WhitespacePattern = Regex("\\s+")
        private val ThreeOrMoreLineBreaksPattern = Regex("\n{3,}")
    }
}

const val DEFAULT_WIKI_BASE_URL: String = "https://en.wikipedia.org"
