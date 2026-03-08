package org.jetbrains.koog.cyberwave.agent.tool

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.koog.cyberwave.observability.NoOpStudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.StudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.traceSpan
import org.jetbrains.koog.cyberwave.application.research.WikipediaResearchPolicy
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult

class SearchWikipediaTool(
    private val wikipediaClient: WikipediaClient,
    private val tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
) : Tool<SearchWikipediaTool.Args, SearchWikipediaTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        name = NAME,
        description =
            """
            Search English Wikipedia for a single learning topic and return candidate articles.
            Use this before fetching summaries or full article content.
            In normal workflow keep the limit between 3 and 5 so the agent considers only the top search window defined by the app policy.
            This tool does not fetch article bodies and should not be used as evidence on its own.
            """.trimIndent(),
    ) {
    @Serializable
    @SerialName("SearchWikipediaArgs")
    data class Args(
        @property:LLMDescription("Topic or search phrase to look up on Wikipedia.")
        val topic: String,
        @property:LLMDescription("Number of top search results to return. Keep this between 3 and 5 in the normal workflow.")
        val limit: Int = WikipediaResearchPolicy.SEARCH_RESULTS_TO_CONSIDER,
    ) {
        init {
            require(topic.isNotBlank()) { "topic must not be blank." }
            require(limit in 1..WikipediaResearchPolicy.SEARCH_RESULTS_TO_CONSIDER) {
                "limit must be between 1 and ${WikipediaResearchPolicy.SEARCH_RESULTS_TO_CONSIDER}."
            }
        }
    }

    @Serializable
    @SerialName("SearchWikipediaResult")
    @LLMDescription("Top Wikipedia search results for the requested topic.")
    data class Result(
        @property:LLMDescription("Normalized topic passed to the search call.")
        val topic: String,
        @property:LLMDescription("Actual limit used for the search request.")
        val limit: Int,
        @property:LLMDescription("Candidate Wikipedia articles returned by the search endpoint.")
        val results: List<WikipediaSearchResult>,
    )

    override suspend fun execute(args: Args): Result =
        tracer.traceSpan(
            name = "study_generation.tool.search_wikipedia",
            attributes =
                mapOf(
                    "tool_name" to NAME,
                    "topic" to args.topic.trim(),
                    "limit" to args.limit.toString(),
                ),
            successAttributes = { result ->
                mapOf(
                    "result_count" to result.results.size.toString(),
                    "has_results" to result.results.isNotEmpty().toString(),
                )
            },
        ) {
            Result(
                topic = args.topic.trim(),
                limit = args.limit,
                results = wikipediaClient.search(query = args.topic, limit = args.limit),
            )
        }

    companion object {
        const val NAME: String = "search_wikipedia"
    }
}

class FetchWikipediaSummaryTool(
    private val wikipediaClient: WikipediaClient,
    private val tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
) : Tool<FetchWikipediaSummaryTool.Args, WikipediaArticleSummary>(
        argsSerializer = Args.serializer(),
        resultSerializer = WikipediaArticleSummary.serializer(),
        name = NAME,
        description =
            """
            Fetch the intro summary and metadata for a specific Wikipedia article title.
            Use this after search when the agent needs lightweight evidence, canonical URLs, or disambiguation checks.
            This tool does not return the full article body, so use fetch_wikipedia_article when deep content is required for quiz generation.
            """.trimIndent(),
    ) {
    @Serializable
    @SerialName("FetchWikipediaSummaryArgs")
    data class Args(
        @property:LLMDescription("Exact Wikipedia article title to fetch.")
        val title: String,
    ) {
        init {
            require(title.isNotBlank()) { "title must not be blank." }
        }
    }

    override suspend fun execute(args: Args): WikipediaArticleSummary =
        tracer.traceSpan(
            name = "study_generation.tool.fetch_wikipedia_summary",
            attributes =
                mapOf(
                    "tool_name" to NAME,
                    "title" to args.title.trim(),
                ),
            successAttributes = { summary ->
                mapOf(
                    "resolved_title" to summary.title,
                    "page_id" to summary.pageId?.toString().orEmpty().ifBlank { "unknown" },
                    "is_disambiguation" to summary.isDisambiguation.toString(),
                )
            },
        ) {
            wikipediaClient.fetchArticleSummary(title = args.title)
        }

    companion object {
        const val NAME: String = "fetch_wikipedia_summary"
    }
}

class FetchWikipediaArticleTool(
    private val wikipediaClient: WikipediaClient,
    private val tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
) : Tool<FetchWikipediaArticleTool.Args, WikipediaArticle>(
        argsSerializer = Args.serializer(),
        resultSerializer = WikipediaArticle.serializer(),
        name = NAME,
        description =
            """
            Fetch the full plain-text content and summary metadata for a specific Wikipedia article title.
            Use this only after deterministic article selection, because article bodies can be large.
            This tool is the primary evidence source for study-note and quiz generation.
            """.trimIndent(),
    ) {
    @Serializable
    @SerialName("FetchWikipediaArticleArgs")
    data class Args(
        @property:LLMDescription("Exact Wikipedia article title to fetch.")
        val title: String,
    ) {
        init {
            require(title.isNotBlank()) { "title must not be blank." }
        }
    }

    override suspend fun execute(args: Args): WikipediaArticle =
        tracer.traceSpan(
            name = "study_generation.tool.fetch_wikipedia_article",
            attributes =
                mapOf(
                    "tool_name" to NAME,
                    "title" to args.title.trim(),
                ),
            successAttributes = { article ->
                mapOf(
                    "resolved_title" to article.summary.title,
                    "page_id" to article.summary.pageId?.toString().orEmpty().ifBlank { "unknown" },
                    "content_length" to article.plainTextContent.length.toString(),
                )
            },
        ) {
            wikipediaClient.fetchArticle(title = args.title)
        }

    companion object {
        const val NAME: String = "fetch_wikipedia_article"
    }
}

fun wikipediaToolRegistry(
    wikipediaClient: WikipediaClient,
    tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
): ToolRegistry =
    ToolRegistry {
        tool(SearchWikipediaTool(wikipediaClient, tracer))
        tool(FetchWikipediaSummaryTool(wikipediaClient, tracer))
        tool(FetchWikipediaArticleTool(wikipediaClient, tracer))
    }
