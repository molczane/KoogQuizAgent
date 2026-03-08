package org.jetbrains.koog.cyberwave.agent.workflow

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequestOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onIsInstance
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.prompt.message.Message
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.koog.cyberwave.agent.tool.FetchWikipediaArticleTool
import org.jetbrains.koog.cyberwave.agent.tool.SearchWikipediaTool
import org.jetbrains.koog.cyberwave.application.StudyRequestParser
import org.jetbrains.koog.cyberwave.application.research.EvidenceStatus
import org.jetbrains.koog.cyberwave.application.research.TopicResearchMaterial
import org.jetbrains.koog.cyberwave.application.research.WikipediaResearchPolicy
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestValidationResult
import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest
import org.jetbrains.koog.cyberwave.observability.NoOpStudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.StudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.traceSpan

object StudyResearchWorkflow {
    const val STRATEGY_NAME: String = "study_research_workflow"
    internal const val SEARCH_STAGE_COMPLETE: String = "SEARCH_STAGE_COMPLETE"
    internal const val SEARCH_STAGE_REQUEST_PREFIX: String = "SEARCH_STAGE_REQUEST_JSON:"
    internal const val FETCH_STAGE_COMPLETE: String = "FETCH_STAGE_COMPLETE"
    internal const val FETCH_STAGE_REQUEST_PREFIX: String = "FETCH_STAGE_REQUEST_JSON:"
    private val workflowJson: Json = Json { ignoreUnknownKeys = true }

    fun strategy(
        wikipediaClient: WikipediaClient,
        tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
    ): AIAgentGraphStrategy<StudyRequestInput, StudyResearchWorkflowResult> =
        strategy(
            searchTool = SearchWikipediaTool(wikipediaClient),
            fetchArticleTool = FetchWikipediaArticleTool(wikipediaClient),
            tracer = tracer,
        )

    fun strategy(
        searchTool: SearchWikipediaTool,
        fetchArticleTool: FetchWikipediaArticleTool,
        tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
    ): AIAgentGraphStrategy<StudyRequestInput, StudyResearchWorkflowResult> =
        strategy<StudyRequestInput, StudyResearchWorkflowResult>(STRATEGY_NAME) {
            val validateInput by node<StudyRequestInput, ValidationNodeResult>("validateInput") { input ->
                tracer.traceSpan(
                    name = "study_generation.research.validate_input",
                    attributes =
                        mapOf(
                            "requested_question_count" to input.maxQuestions.toString(),
                        ),
                    successAttributes = { result ->
                        when (result) {
                            is ValidationNodeResult.Valid ->
                                mapOf(
                                    "outcome" to "valid",
                                    "topic_count" to result.request.topics.size.toString(),
                                )

                            is ValidationNodeResult.Invalid ->
                                mapOf(
                                    "outcome" to "invalid",
                                    "normalized_topic_count" to result.result.normalizedTopics.size.toString(),
                                    "issue_count" to result.result.issues.size.toString(),
                                )
                        }
                    },
                ) {
                    when (val validation = StudyRequestParser.validate(input)) {
                        is StudyRequestValidationResult.Success -> ValidationNodeResult.Valid(validation.request)
                        is StudyRequestValidationResult.Failure ->
                            ValidationNodeResult.Invalid(
                                StudyResearchWorkflowResult.ValidationFailed(
                                    issues = validation.issues,
                                    normalizedTopics = validation.normalizedTopics,
                                ),
                            )
                    }
                }
            }

            val prepareQueries by node<ValidatedStudyRequest, ResearchQueryPlan>("prepareQueries") { request ->
                tracer.traceSpan(
                    name = "study_generation.research.prepare_queries",
                    attributes =
                        mapOf(
                            "input_topic_count" to request.topics.size.toString(),
                        ),
                    successAttributes = { plan ->
                        mapOf("prepared_topic_count" to plan.request.topics.size.toString())
                    },
                ) {
                    ResearchQueryPlan(
                        request =
                            request.copy(
                                topics =
                                    request.topics
                                        .asSequence()
                                        .map(String::trim)
                                        .filter(String::isNotEmpty)
                                        .distinctBy(String::lowercase)
                                        .toList(),
                            ),
                    )
                }
            }

            val searchWithLlmTools by subgraph<ResearchQueryPlan, ResearchSearchSnapshot>(
                name = "searchWithLlmTools",
                tools = listOf(searchTool),
            ) {
                val prepareSearchPrompt by node<ResearchQueryPlan, String>("prepareSearchPrompt") { plan ->
                    buildSearchStagePrompt(plan.request)
                }
                val requestSearchTool by nodeLLMRequestOnlyCallingTools("requestSearchTool")
                val executeSearchTool by nodeExecuteTool("executeSearchTool")
                val sendSearchToolResult by nodeLLMSendToolResult("sendSearchToolResult")
                val finalizeSearchResults by node<String, ResearchSearchSnapshot>("finalizeSearchResults") { completionMessage ->
                    tracer.traceSpan(
                        name = "study_generation.research.search_wikipedia",
                        attributes = emptyMap(),
                        successAttributes = { snapshot ->
                            mapOf(
                                "tool_call_count" to snapshot.metadata.toolCallCount.toString(),
                                "topic_count" to snapshot.searchResults.size.toString(),
                                "total_result_count" to snapshot.searchResults.sumOf { topicResult -> topicResult.results.size }.toString(),
                            )
                        },
                    ) {
                        require(completionMessage.trim() == SEARCH_STAGE_COMPLETE) {
                            "Search stage must finish with $SEARCH_STAGE_COMPLETE."
                        }

                        val messages = llm.readSession { prompt.messages }
                        buildSearchSnapshot(messages = messages, completionMessage = completionMessage.trim())
                    }
                }

                edge(nodeStart forwardTo prepareSearchPrompt)
                edge(prepareSearchPrompt forwardTo requestSearchTool)
                edge(requestSearchTool forwardTo executeSearchTool onToolCall(searchTool))
                edge(requestSearchTool forwardTo finalizeSearchResults onAssistantMessage { true })
                edge(executeSearchTool forwardTo sendSearchToolResult)
                edge(sendSearchToolResult forwardTo executeSearchTool onToolCall(searchTool))
                edge(sendSearchToolResult forwardTo finalizeSearchResults onAssistantMessage { true })
                edge(finalizeSearchResults forwardTo nodeFinish)
            }

            val selectArticles by node<ResearchSearchSnapshot, ResearchSelectionSnapshot>("selectArticles") { snapshot ->
                tracer.traceSpan(
                    name = "study_generation.research.select_articles",
                    attributes = mapOf("topic_count" to snapshot.searchResults.size.toString()),
                    successAttributes = { selectionSnapshot ->
                        mapOf(
                            "topic_count" to selectionSnapshot.selectedArticles.size.toString(),
                            "selected_article_count" to
                                selectionSnapshot.selectedArticles.sumOf { selection -> selection.articles.size }.toString(),
                        )
                    },
                ) {
                    val selections =
                        snapshot.searchResults.map { topicSearchResults ->
                            TopicWikipediaSelections(
                                topic = topicSearchResults.topic,
                                articles =
                                    WikipediaResearchPolicy.selectArticles(
                                        topic = topicSearchResults.topic,
                                        searchResults = topicSearchResults.results,
                                    ),
                            )
                        }

                    ResearchSelectionSnapshot(
                        request = snapshot.request,
                        searchStageMetadata = snapshot.metadata,
                        searchResults = snapshot.searchResults,
                        selectedArticles = selections,
                    )
                }
            }

            val fetchWithLlmTools by subgraph<ResearchSelectionSnapshot, ResearchMaterialsSnapshot>(
                name = "fetchWithLlmTools",
                tools = listOf(fetchArticleTool),
            ) {
                val prepareFetchPrompt by node<ResearchSelectionSnapshot, String>("prepareFetchPrompt") { snapshot ->
                    buildFetchStagePrompt(snapshot)
                }
                val requestFetchTool by nodeLLMRequestOnlyCallingTools("requestFetchTool")
                val executeFetchTool by nodeExecuteTool("executeFetchTool")
                val sendFetchToolResult by nodeLLMSendToolResult("sendFetchToolResult")
                val finalizeFetchedMaterials by node<String, ResearchMaterialsSnapshot>("finalizeFetchedMaterials") { completionMessage ->
                    tracer.traceSpan(
                        name = "study_generation.research.fetch_articles",
                        attributes = emptyMap(),
                        successAttributes = { materialsSnapshot ->
                            mapOf(
                                "tool_call_count" to materialsSnapshot.fetchStageMetadata.toolCallCount.toString(),
                                "topic_count" to materialsSnapshot.materials.size.toString(),
                                "material_article_count" to
                                    materialsSnapshot.materials.sumOf { material -> material.articles.size }.toString(),
                                "unique_article_count" to
                                    materialsSnapshot.materials
                                        .flatMap { material -> material.articles }
                                        .distinctBy { article -> article.summary.pageId }
                                        .size
                                        .toString(),
                            )
                        },
                    ) {
                        require(completionMessage.trim() == FETCH_STAGE_COMPLETE) {
                            "Fetch stage must finish with $FETCH_STAGE_COMPLETE."
                        }

                        val messages = llm.readSession { prompt.messages }
                        buildFetchMaterialsSnapshot(messages = messages, completionMessage = completionMessage.trim())
                    }
                }

                edge(nodeStart forwardTo prepareFetchPrompt)
                edge(prepareFetchPrompt forwardTo requestFetchTool)
                edge(requestFetchTool forwardTo executeFetchTool onToolCall(fetchArticleTool))
                edge(requestFetchTool forwardTo finalizeFetchedMaterials onAssistantMessage { true })
                edge(executeFetchTool forwardTo sendFetchToolResult)
                edge(sendFetchToolResult forwardTo executeFetchTool onToolCall(fetchArticleTool))
                edge(sendFetchToolResult forwardTo finalizeFetchedMaterials onAssistantMessage { true })
                edge(finalizeFetchedMaterials forwardTo nodeFinish)
            }

            val checkEvidence by node<ResearchMaterialsSnapshot, StudyResearchWorkflowResult>("checkEvidence") { snapshot ->
                tracer.traceSpan(
                    name = "study_generation.research.check_evidence",
                    attributes =
                        mapOf(
                            "requested_topic_count" to snapshot.request.topics.size.toString(),
                            "requested_question_count" to snapshot.request.maxQuestions.toString(),
                        ),
                    successAttributes = { result ->
                        val evidence =
                            when (result) {
                                is StudyResearchWorkflowResult.InsufficientSources -> result.snapshot.evidence
                                is StudyResearchWorkflowResult.ReadyForGeneration -> result.snapshot.evidence
                                is StudyResearchWorkflowResult.ValidationFailed -> error("Unexpected validation result at evidence stage.")
                            }

                        mapOf(
                            "evidence_status" to evidence.status.name.lowercase(),
                            "usable_source_count" to evidence.usableSources.size.toString(),
                            "missing_topic_count" to evidence.missingTopics.size.toString(),
                            "recommended_question_count" to evidence.recommendedQuestionCount.toString(),
                        )
                    },
                ) {
                    val evidence =
                        WikipediaResearchPolicy.assessEvidence(
                            requestedTopics = snapshot.request.topics,
                            requestedQuestionCount = snapshot.request.maxQuestions,
                            materials = snapshot.materials,
                        )

                    val researchSnapshot =
                        StudyResearchSnapshot(
                            request = snapshot.request,
                            searchStageMetadata = snapshot.searchStageMetadata,
                            fetchStageMetadata = snapshot.fetchStageMetadata,
                            searchResults = snapshot.searchResults,
                            selectedArticles = snapshot.selectedArticles,
                            materials = snapshot.materials,
                            evidence = evidence,
                        )

                    if (evidence.status == EvidenceStatus.INSUFFICIENT) {
                        StudyResearchWorkflowResult.InsufficientSources(researchSnapshot)
                    } else {
                        StudyResearchWorkflowResult.ReadyForGeneration(researchSnapshot)
                    }
                }
            }

            edge(nodeStart forwardTo validateInput)
            edge(
                (validateInput forwardTo prepareQueries)
                    .onIsInstance(ValidationNodeResult.Valid::class)
                    .transformed { it.request },
            )
            edge(
                (validateInput forwardTo nodeFinish)
                    .onIsInstance(ValidationNodeResult.Invalid::class)
                    .transformed { it.result },
            )
            edge(prepareQueries forwardTo searchWithLlmTools)
            edge(searchWithLlmTools forwardTo selectArticles)
            edge(selectArticles forwardTo fetchWithLlmTools)
            edge(fetchWithLlmTools forwardTo checkEvidence)
            edge(checkEvidence forwardTo nodeFinish)
        }

    private fun buildSearchStagePrompt(request: ValidatedStudyRequest): String =
        """
        Search stage for the CyberWave learning workflow.
        Use the available `search_wikipedia` tool exactly once for each topic in the request payload.
        Pass the topic text verbatim and keep limit at ${WikipediaResearchPolicy.SEARCH_RESULTS_TO_CONSIDER}.
        After all required search calls are complete, reply with exactly $SEARCH_STAGE_COMPLETE and nothing else.
        $SEARCH_STAGE_REQUEST_PREFIX${workflowJson.encodeToString(request)}
        """.trimIndent()

    private fun buildFetchStagePrompt(snapshot: ResearchSelectionSnapshot): String {
        val request =
            FetchStageRequest(
                request = snapshot.request,
                searchStageMetadata = snapshot.searchStageMetadata,
                searchResults = snapshot.searchResults,
                selectedArticles = snapshot.selectedArticles,
            )

        return """
            Fetch stage for the CyberWave learning workflow.
            Use the available `fetch_wikipedia_article` tool exactly once for each unique article title in the request payload.
            Use only titles from the payload. Do not search for new titles and do not skip any unique title in the payload.
            After all required fetch calls are complete, reply with exactly $FETCH_STAGE_COMPLETE and nothing else.
            $FETCH_STAGE_REQUEST_PREFIX${workflowJson.encodeToString(request)}
            """.trimIndent()
    }

    private fun buildSearchSnapshot(
        messages: List<Message>,
        completionMessage: String,
    ): ResearchSearchSnapshot {
        val request = extractSearchStageRequest(messages)
        val toolResults = extractSearchToolResults(messages)
        val normalizedRequestedTopics = request.topics.associateBy { topic -> topic.lowercase() }
        val resultsByTopic = LinkedHashMap<String, List<org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult>>()

        toolResults.forEach { result ->
            val canonicalTopic = normalizedRequestedTopics[result.topic.trim().lowercase()] ?: return@forEach
            if (canonicalTopic !in resultsByTopic) {
                resultsByTopic[canonicalTopic] = result.results
            }
        }

        return ResearchSearchSnapshot(
            request = request,
            metadata =
                SearchStageMetadata(
                    toolCallCount = toolResults.size,
                    completionMessage = completionMessage,
                ),
            searchResults =
                request.topics.map { topic ->
                    TopicWikipediaSearchResults(
                        topic = topic,
                        results = resultsByTopic[topic].orEmpty(),
                    )
                },
        )
    }

    private fun buildFetchMaterialsSnapshot(
        messages: List<Message>,
        completionMessage: String,
    ): ResearchMaterialsSnapshot {
        val request = extractFetchStageRequest(messages)
        val toolResults = extractFetchToolResults(messages)
        val requestedTitlesByNormalizedTitle =
            request.uniqueArticleTitles.associateBy { title -> title.trim().lowercase() }
        val articlesByTitle = LinkedHashMap<String, org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle>()

        toolResults.forEach { article ->
            val normalizedTitle = article.summary.title.trim().lowercase()
            val requestedTitle = requestedTitlesByNormalizedTitle[normalizedTitle] ?: return@forEach
            if (normalizedTitle !in articlesByTitle) {
                articlesByTitle[requestedTitle.trim().lowercase()] = article
            }
        }

        val materials =
            request.selectedArticles.map { selection ->
                TopicResearchMaterial(
                    topic = selection.topic,
                    articles =
                        selection.articles
                            .mapNotNull { article -> articlesByTitle[article.title.trim().lowercase()] }
                            .distinctBy { article -> article.summary.pageId ?: article.summary.title.lowercase() },
                )
            }

        return ResearchMaterialsSnapshot(
            request = request.request,
            searchStageMetadata = request.searchStageMetadata,
            fetchStageMetadata =
                FetchStageMetadata(
                    toolCallCount = toolResults.size,
                    completionMessage = completionMessage,
                ),
            searchResults = request.searchResults,
            selectedArticles = request.selectedArticles,
            materials = materials,
        )
    }

    private fun extractSearchToolResults(messages: List<Message>): List<SearchWikipediaTool.Result> =
        messages
            .filterIsInstance<Message.Tool.Result>()
            .filter { message -> message.tool == SearchWikipediaTool.NAME }
            .map { message ->
                workflowJson.decodeFromString(SearchWikipediaTool.Result.serializer(), message.content)
            }

    private fun extractFetchToolResults(messages: List<Message>): List<org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle> =
        messages
            .filterIsInstance<Message.Tool.Result>()
            .filter { message -> message.tool == FetchWikipediaArticleTool.NAME }
            .map { message ->
                workflowJson.decodeFromString(org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle.serializer(), message.content)
            }

    private fun extractSearchStageRequest(messages: List<Message>): ValidatedStudyRequest {
        val requestJson =
            messages
                .asReversed()
                .asSequence()
                .filterIsInstance<Message.User>()
                .mapNotNull { message ->
                    message.content
                        .lineSequence()
                        .firstOrNull { line -> line.startsWith(SEARCH_STAGE_REQUEST_PREFIX) }
                        ?.removePrefix(SEARCH_STAGE_REQUEST_PREFIX)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                }.firstOrNull()
                ?: error("Search stage request payload is missing from the LLM prompt history.")

        return workflowJson.decodeFromString(ValidatedStudyRequest.serializer(), requestJson)
    }

    private fun extractFetchStageRequest(messages: List<Message>): FetchStageRequest {
        val requestJson =
            messages
                .asReversed()
                .asSequence()
                .filterIsInstance<Message.User>()
                .mapNotNull { message ->
                    message.content
                        .lineSequence()
                        .firstOrNull { line -> line.startsWith(FETCH_STAGE_REQUEST_PREFIX) }
                        ?.removePrefix(FETCH_STAGE_REQUEST_PREFIX)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                }.firstOrNull()
                ?: error("Fetch stage request payload is missing from the LLM prompt history.")

        return workflowJson.decodeFromString(FetchStageRequest.serializer(), requestJson)
    }

    private sealed interface ValidationNodeResult {
        data class Valid(
            val request: ValidatedStudyRequest,
        ) : ValidationNodeResult

        data class Invalid(
            val result: StudyResearchWorkflowResult.ValidationFailed,
        ) : ValidationNodeResult
    }

    private data class ResearchQueryPlan(
        val request: ValidatedStudyRequest,
    )

    private data class ResearchSearchSnapshot(
        val request: ValidatedStudyRequest,
        val metadata: SearchStageMetadata,
        val searchResults: List<TopicWikipediaSearchResults>,
    )

    private data class ResearchSelectionSnapshot(
        val request: ValidatedStudyRequest,
        val searchStageMetadata: SearchStageMetadata,
        val searchResults: List<TopicWikipediaSearchResults>,
        val selectedArticles: List<TopicWikipediaSelections>,
    )

    private data class ResearchMaterialsSnapshot(
        val request: ValidatedStudyRequest,
        val searchStageMetadata: SearchStageMetadata,
        val fetchStageMetadata: FetchStageMetadata,
        val searchResults: List<TopicWikipediaSearchResults>,
        val selectedArticles: List<TopicWikipediaSelections>,
        val materials: List<TopicResearchMaterial>,
    )

    @kotlinx.serialization.Serializable
    private data class FetchStageRequest(
        val request: ValidatedStudyRequest,
        val searchStageMetadata: SearchStageMetadata,
        val searchResults: List<TopicWikipediaSearchResults>,
        val selectedArticles: List<TopicWikipediaSelections>,
    ) {
        val uniqueArticleTitles: List<String>
            get() =
                selectedArticles
                    .flatMap { selection -> selection.articles }
                    .map { article -> article.title }
                    .distinctBy(String::lowercase)
    }
}
