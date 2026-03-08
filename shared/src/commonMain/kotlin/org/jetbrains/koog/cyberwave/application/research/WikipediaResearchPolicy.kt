package org.jetbrains.koog.cyberwave.application.research

import kotlinx.serialization.Serializable
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult
import org.jetbrains.koog.cyberwave.domain.model.ResearchSource

@Serializable
data class SelectedWikipediaArticle(
    val topic: String,
    val pageId: Long? = null,
    val title: String,
    val canonicalUrl: String? = null,
    val snippet: String,
)

data class TopicResearchMaterial(
    val topic: String,
    val articles: List<WikipediaArticle>,
)

enum class EvidenceStatus {
    SUFFICIENT,
    LIMITED,
    INSUFFICIENT,
}

data class WikipediaEvidenceAssessment(
    val status: EvidenceStatus,
    val requestedQuestionCount: Int,
    val recommendedQuestionCount: Int,
    val coveredTopics: List<String>,
    val missingTopics: List<String>,
    val usableSources: List<ResearchSource>,
)

object WikipediaResearchPolicy {
    const val SEARCH_RESULTS_TO_CONSIDER: Int = 5
    const val MAX_SELECTED_ARTICLES_PER_TOPIC: Int = 3
    const val MIN_ARTICLE_WORDS: Int = 120

    fun selectArticles(
        topic: String,
        searchResults: List<WikipediaSearchResult>,
    ): List<SelectedWikipediaArticle> {
        val normalizedTopic = normalizeForComparison(topic)
        if (normalizedTopic.isEmpty()) {
            return emptyList()
        }

        val rankedCandidates =
            deduplicate(searchResults.take(SEARCH_RESULTS_TO_CONSIDER))
                .mapIndexed { index, result ->
                    RankedSearchCandidate(
                        originalIndex = index,
                        result = result,
                        score = calculateScore(topic = normalizedTopic, result = result),
                    )
                }
                .sortedWith(
                    compareByDescending<RankedSearchCandidate> { candidate -> candidate.score }
                        .thenBy { candidate -> candidate.originalIndex }
                        .thenBy { candidate -> candidate.result.title.lowercase() },
                )

        val preferredCandidates = rankedCandidates.filterNot { candidate -> candidate.result.isDisambiguationHint }
        val selectedCandidates =
            if (preferredCandidates.isNotEmpty()) {
                preferredCandidates.take(MAX_SELECTED_ARTICLES_PER_TOPIC)
            } else {
                rankedCandidates.take(1)
            }

        return selectedCandidates.map { candidate ->
            SelectedWikipediaArticle(
                topic = topic,
                pageId = candidate.result.pageId,
                title = candidate.result.title,
                canonicalUrl = candidate.result.canonicalUrl,
                snippet = candidate.result.snippet,
            )
        }
    }

    fun assessEvidence(
        requestedTopics: List<String>,
        requestedQuestionCount: Int,
        materials: List<TopicResearchMaterial>,
    ): WikipediaEvidenceAssessment {
        val normalizedMaterials =
            materials.associateBy { material -> normalizeForComparison(material.topic) }

        val usableArticlesByTopic =
            requestedTopics.associateWith { topic ->
                normalizedMaterials[normalizeForComparison(topic)]
                    ?.articles
                    .orEmpty()
                    .filter(::isUsableArticle)
            }

        val coveredTopics =
            requestedTopics.filter { topic ->
                usableArticlesByTopic[topic].orEmpty().isNotEmpty()
            }
        val missingTopics = requestedTopics.filterNot { topic -> topic in coveredTopics }

        val uniqueUsableArticles = deduplicateArticles(usableArticlesByTopic.values.flatten())
        val supportedQuestionCount = uniqueUsableArticles.sumOf(::questionCapacity)

        val status: EvidenceStatus
        val recommendedQuestionCount: Int
        if (missingTopics.isNotEmpty() || supportedQuestionCount == 0) {
            status = EvidenceStatus.INSUFFICIENT
            recommendedQuestionCount = 0
        } else if (supportedQuestionCount < requestedQuestionCount) {
            status = EvidenceStatus.LIMITED
            recommendedQuestionCount = supportedQuestionCount
        } else {
            status = EvidenceStatus.SUFFICIENT
            recommendedQuestionCount = requestedQuestionCount
        }

        return WikipediaEvidenceAssessment(
            status = status,
            requestedQuestionCount = requestedQuestionCount,
            recommendedQuestionCount = recommendedQuestionCount,
            coveredTopics = coveredTopics,
            missingTopics = missingTopics,
            usableSources = uniqueUsableArticles.map(::toResearchSource),
        )
    }

    private fun deduplicate(results: List<WikipediaSearchResult>): List<WikipediaSearchResult> {
        val seenKeys = LinkedHashSet<String>()
        return buildList {
            results.forEach { result ->
                if (result.title.isBlank()) {
                    return@forEach
                }

                val key =
                    result.pageId?.let { pageId -> "page:$pageId" }
                        ?: "title:${normalizeForComparison(result.title)}"

                if (seenKeys.add(key)) {
                    add(result)
                }
            }
        }
    }

    private fun deduplicateArticles(articles: List<WikipediaArticle>): List<WikipediaArticle> {
        val seenKeys = LinkedHashSet<String>()
        return buildList {
            articles.forEach { article ->
                val key =
                    article.summary.pageId?.let { pageId -> "page:$pageId" }
                        ?: "title:${normalizeForComparison(article.summary.title)}"

                if (seenKeys.add(key)) {
                    add(article)
                }
            }
        }
    }

    private fun calculateScore(
        topic: String,
        result: WikipediaSearchResult,
    ): Int {
        val normalizedTitle = normalizeForComparison(result.title)
        val baseTitle = normalizeForComparison(removeParentheticalSuffix(result.title))
        val normalizedSnippet = normalizeForComparison(result.snippet)

        var score = 0
        if (!result.isDisambiguationHint) {
            score += 300
        } else {
            score -= 300
        }

        score +=
            when {
                normalizedTitle == topic -> 350
                baseTitle == topic -> 275
                baseTitle.startsWith("$topic ") || baseTitle.startsWith(topic) -> 180
                baseTitle.contains(topic) -> 120
                normalizedSnippet.contains(topic) -> 40
                else -> 0
            }

        if (result.hasSnippet) {
            score += 15
        }

        score -= kotlin.math.abs(baseTitle.length - topic.length).coerceAtMost(40)
        return score
    }

    private fun isUsableArticle(article: WikipediaArticle): Boolean {
        if (article.summary.isDisambiguation || !article.summary.hasExtract || !article.hasContent) {
            return false
        }

        return wordCount(article.plainTextContent) >= MIN_ARTICLE_WORDS
    }

    private fun questionCapacity(article: WikipediaArticle): Int {
        val words = wordCount(article.plainTextContent)
        return when {
            words >= 900 -> 3
            words >= 400 -> 2
            words >= MIN_ARTICLE_WORDS -> 1
            else -> 0
        }
    }

    private fun toResearchSource(article: WikipediaArticle): ResearchSource =
        ResearchSource(
            id = buildSourceId(article),
            title = article.summary.title,
            url = article.summary.canonicalUrl,
            snippet = buildSnippet(article),
        )

    private fun buildSourceId(article: WikipediaArticle): String =
        article.summary.pageId?.let { pageId -> "wiki-$pageId" }
            ?: "wiki-${slugify(article.summary.title)}"

    private fun buildSnippet(article: WikipediaArticle): String {
        val preferredText = article.summary.description?.trim().orEmpty().ifBlank { article.summary.extract }
        val collapsed = preferredText.replace(WhitespacePattern, " ").trim()
        return if (collapsed.length <= MAX_SOURCE_SNIPPET_LENGTH) {
            collapsed
        } else {
            collapsed.take(MAX_SOURCE_SNIPPET_LENGTH - ELLIPSIS.length).trimEnd() + ELLIPSIS
        }
    }

    private fun slugify(text: String): String =
        normalizeForComparison(text)
            .replace(' ', '-')
            .ifBlank { "source" }

    private fun normalizeForComparison(value: String): String =
        value
            .trim()
            .lowercase()
            .replace(ParentheticalSuffixPattern, " ")
            .replace(NonAlphanumericPattern, " ")
            .replace(WhitespacePattern, " ")
            .trim()

    private fun removeParentheticalSuffix(title: String): String =
        title.replace(ParentheticalSuffixPattern, " ").trim()

    private fun wordCount(text: String): Int =
        text.trim()
            .split(WhitespacePattern)
            .count { token -> token.isNotBlank() }

    private data class RankedSearchCandidate(
        val originalIndex: Int,
        val result: WikipediaSearchResult,
        val score: Int,
    )

    private const val MAX_SOURCE_SNIPPET_LENGTH: Int = 240
    private const val ELLIPSIS: String = "..."

    private val ParentheticalSuffixPattern = Regex("\\s*\\([^)]*\\)")
    private val NonAlphanumericPattern = Regex("[^a-z0-9]+")
    private val WhitespacePattern = Regex("\\s+")
}
