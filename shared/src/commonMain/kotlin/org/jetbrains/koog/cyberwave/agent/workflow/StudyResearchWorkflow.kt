package org.jetbrains.koog.cyberwave.agent.workflow

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.onIsInstance
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

            val searchWikipedia by node<ResearchQueryPlan, ResearchSearchSnapshot>("searchWikipedia") { plan ->
                tracer.traceSpan(
                    name = "study_generation.research.search_wikipedia",
                    attributes = mapOf("topic_count" to plan.request.topics.size.toString()),
                    successAttributes = { snapshot ->
                        mapOf(
                            "topic_count" to snapshot.searchResults.size.toString(),
                            "total_result_count" to snapshot.searchResults.sumOf { topicResult -> topicResult.results.size }.toString(),
                        )
                    },
                ) {
                    val searchResults =
                        plan.request.topics.map { topic ->
                            TopicWikipediaSearchResults(
                                topic = topic,
                                results = searchTool.execute(SearchWikipediaTool.Args(topic = topic)).results,
                            )
                        }

                    ResearchSearchSnapshot(
                        request = plan.request,
                        searchResults = searchResults,
                    )
                }
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
                        searchResults = snapshot.searchResults,
                        selectedArticles = selections,
                    )
                }
            }

            val fetchArticles by node<ResearchSelectionSnapshot, ResearchMaterialsSnapshot>("fetchArticles") { snapshot ->
                tracer.traceSpan(
                    name = "study_generation.research.fetch_articles",
                    attributes = mapOf("topic_count" to snapshot.selectedArticles.size.toString()),
                    successAttributes = { materialsSnapshot ->
                        mapOf(
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
                    val articleCache = LinkedHashMap<String, org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle>()
                    val materials =
                        snapshot.selectedArticles.map { topicSelection ->
                            val articles =
                                topicSelection.articles.map { selectedArticle ->
                                    articleCache.getOrPut(selectedArticle.title) {
                                        fetchArticleTool.execute(
                                            FetchWikipediaArticleTool.Args(title = selectedArticle.title),
                                        )
                                    }
                                }

                            TopicResearchMaterial(
                                topic = topicSelection.topic,
                                articles = articles,
                            )
                        }

                    ResearchMaterialsSnapshot(
                        request = snapshot.request,
                        searchResults = snapshot.searchResults,
                        selectedArticles = snapshot.selectedArticles,
                        materials = materials,
                    )
                }
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
            edge(prepareQueries forwardTo searchWikipedia)
            edge(searchWikipedia forwardTo selectArticles)
            edge(selectArticles forwardTo fetchArticles)
            edge(fetchArticles forwardTo checkEvidence)
            edge(checkEvidence forwardTo nodeFinish)
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
        val searchResults: List<TopicWikipediaSearchResults>,
    )

    private data class ResearchSelectionSnapshot(
        val request: ValidatedStudyRequest,
        val searchResults: List<TopicWikipediaSearchResults>,
        val selectedArticles: List<TopicWikipediaSelections>,
    )

    private data class ResearchMaterialsSnapshot(
        val request: ValidatedStudyRequest,
        val searchResults: List<TopicWikipediaSearchResults>,
        val selectedArticles: List<TopicWikipediaSelections>,
        val materials: List<TopicResearchMaterial>,
    )
}
