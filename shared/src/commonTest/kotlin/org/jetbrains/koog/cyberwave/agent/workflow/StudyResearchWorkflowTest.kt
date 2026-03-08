package org.jetbrains.koog.cyberwave.agent.workflow

import ai.koog.agents.core.agent.AIAgent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jetbrains.koog.cyberwave.agent.support.ToolCallingSearchPromptExecutor
import org.jetbrains.koog.cyberwave.agent.support.UnusedPromptExecutor
import org.jetbrains.koog.cyberwave.agent.support.testAgentConfig
import org.jetbrains.koog.cyberwave.agent.tool.wikipediaToolRegistry
import org.jetbrains.koog.cyberwave.application.research.EvidenceStatus
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput

class StudyResearchWorkflowTest {
    @Test
    fun strategyGraphUsesTheExpectedResearchFirstNodeOrder() {
        val strategy = StudyResearchWorkflow.strategy(RecordingWikipediaClient())

        assertEquals(listOf("validateInput"), strategy.nodeStart.edges.map { edge -> edge.toNode.name })

        val validateInput = strategy.metadata.nodesMap.values.single { node -> node.name == "validateInput" }
        val prepareQueries = strategy.metadata.nodesMap.values.single { node -> node.name == "prepareQueries" }
        val searchWithLlmTools = strategy.metadata.nodesMap.values.single { node -> node.name == "searchWithLlmTools" }
        val selectArticles = strategy.metadata.nodesMap.values.single { node -> node.name == "selectArticles" }
        val fetchWithLlmTools = strategy.metadata.nodesMap.values.single { node -> node.name == "fetchWithLlmTools" }
        val checkEvidence = strategy.metadata.nodesMap.values.single { node -> node.name == "checkEvidence" }

        assertEquals(setOf("prepareQueries", "__finish__"), validateInput.edges.map { edge -> edge.toNode.name }.toSet())
        assertEquals(listOf("searchWithLlmTools"), prepareQueries.edges.map { edge -> edge.toNode.name })
        assertEquals(listOf("selectArticles"), searchWithLlmTools.edges.map { edge -> edge.toNode.name })
        assertEquals(listOf("fetchWithLlmTools"), selectArticles.edges.map { edge -> edge.toNode.name })
        assertEquals(listOf("checkEvidence"), fetchWithLlmTools.edges.map { edge -> edge.toNode.name })
        assertEquals(listOf("__finish__"), checkEvidence.edges.map { edge -> edge.toNode.name })
    }

    @Test
    fun strategyRunsSearchSelectionFetchAndEvidenceStagesInOrder() = runTest {
        val wikipediaClient = RecordingWikipediaClient()
        val strategy = StudyResearchWorkflow.strategy(wikipediaClient)
        val promptExecutor = ToolCallingSearchPromptExecutor()
        val agent =
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = testAgentConfig(),
                strategy = strategy,
                toolRegistry = wikipediaToolRegistry(wikipediaClient),
            )

        val result =
            agent.run(
                StudyRequestInput(
                    topicsText = "Kotlin, Compose Multiplatform",
                    maxQuestions = 3,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        val ready = assertIs<StudyResearchWorkflowResult.ReadyForGeneration>(result)

        assertEquals(
            listOf(
                "search:Kotlin:5",
                "search:Compose Multiplatform:5",
                "article:Kotlin",
                "article:Compose Multiplatform",
            ),
            wikipediaClient.calls,
        )
        assertEquals(listOf("Kotlin", "Compose Multiplatform"), promptExecutor.emittedSearchToolTopics)
        assertEquals(3, promptExecutor.searchStageCalls)
        assertEquals(listOf("Kotlin", "Compose Multiplatform"), promptExecutor.emittedFetchToolTitles)
        assertEquals(3, promptExecutor.fetchStageCalls)
        assertEquals(2, ready.snapshot.searchStageMetadata.toolCallCount)
        assertEquals(StudyResearchWorkflow.SEARCH_STAGE_COMPLETE, ready.snapshot.searchStageMetadata.completionMessage)
        assertEquals(2, ready.snapshot.fetchStageMetadata.toolCallCount)
        assertEquals(StudyResearchWorkflow.FETCH_STAGE_COMPLETE, ready.snapshot.fetchStageMetadata.completionMessage)
        assertEquals(listOf("Kotlin", "Compose Multiplatform"), ready.snapshot.request.topics)
        assertEquals(2, ready.snapshot.materials.size)
        assertEquals(EvidenceStatus.SUFFICIENT, ready.snapshot.evidence.status)
        assertEquals(3, ready.snapshot.effectiveQuestionCount)
        assertEquals(2, ready.snapshot.usableSources.size)
        assertContains(ready.snapshot.usableSources.map { source -> source.title }, "Kotlin")
        assertContains(ready.snapshot.usableSources.map { source -> source.title }, "Compose Multiplatform")
    }

    @Test
    fun strategyCollapsesOutOfOrderDuplicateSearchToolCallsIntoStableTopicState() = runTest {
        val wikipediaClient = RecordingWikipediaClient()
        val strategy = StudyResearchWorkflow.strategy(wikipediaClient)
        val promptExecutor =
            ToolCallingSearchPromptExecutor(
                scriptedSearchTopics = listOf("Compose Multiplatform", "Kotlin", "Compose Multiplatform"),
            )
        val agent =
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = testAgentConfig(),
                strategy = strategy,
                toolRegistry = wikipediaToolRegistry(wikipediaClient),
            )

        val result =
            agent.run(
                StudyRequestInput(
                    topicsText = "Kotlin, Compose Multiplatform",
                    maxQuestions = 3,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        val ready = assertIs<StudyResearchWorkflowResult.ReadyForGeneration>(result)

        assertEquals(
            listOf(
                "search:Compose Multiplatform:5",
                "search:Kotlin:5",
                "search:Compose Multiplatform:5",
                "article:Kotlin",
                "article:Compose Multiplatform",
            ),
            wikipediaClient.calls,
        )
        assertEquals(listOf("Compose Multiplatform", "Kotlin", "Compose Multiplatform"), promptExecutor.emittedSearchToolTopics)
        assertEquals(3, ready.snapshot.searchStageMetadata.toolCallCount)
        assertEquals(listOf("Kotlin", "Compose Multiplatform"), ready.snapshot.searchResults.map { topicResult -> topicResult.topic })
        assertEquals(
            listOf("Kotlin", "Compose Multiplatform"),
            ready.snapshot.selectedArticles.map { selection -> selection.topic },
        )
        assertEquals(
            listOf("Kotlin", "Compose Multiplatform"),
            ready.snapshot.materials.map { material -> material.topic },
        )
    }

    @Test
    fun strategyCollapsesOutOfOrderDuplicateFetchToolCallsIntoStableMaterials() = runTest {
        val wikipediaClient = RecordingWikipediaClient()
        val strategy = StudyResearchWorkflow.strategy(wikipediaClient)
        val promptExecutor =
            ToolCallingSearchPromptExecutor(
                scriptedFetchTitles = listOf("Compose Multiplatform", "Kotlin", "Compose Multiplatform"),
            )
        val agent =
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = testAgentConfig(),
                strategy = strategy,
                toolRegistry = wikipediaToolRegistry(wikipediaClient),
            )

        val result =
            agent.run(
                StudyRequestInput(
                    topicsText = "Kotlin, Compose Multiplatform",
                    maxQuestions = 3,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        val ready = assertIs<StudyResearchWorkflowResult.ReadyForGeneration>(result)
        val articleCalls = wikipediaClient.calls.filter { call -> call.startsWith("article:") }

        assertEquals(
            listOf(
                "article:Compose Multiplatform",
                "article:Kotlin",
                "article:Compose Multiplatform",
            ),
            articleCalls,
        )
        assertEquals(listOf("Compose Multiplatform", "Kotlin", "Compose Multiplatform"), promptExecutor.emittedFetchToolTitles)
        assertEquals(3, ready.snapshot.fetchStageMetadata.toolCallCount)
        assertEquals(
            listOf("Kotlin", "Compose Multiplatform"),
            ready.snapshot.materials.map { material -> material.topic },
        )
        assertEquals(
            listOf("Kotlin", "Compose Multiplatform"),
            ready.snapshot.materials.map { material -> material.articles.single().summary.title },
        )
    }

    @Test
    fun strategyReturnsValidationFailureWithoutInvokingResearchTools() = runTest {
        val wikipediaClient = RecordingWikipediaClient()
        val strategy = StudyResearchWorkflow.strategy(wikipediaClient)
        val agent =
            AIAgent(
                promptExecutor = UnusedPromptExecutor,
                agentConfig = testAgentConfig(),
                strategy = strategy,
                toolRegistry = wikipediaToolRegistry(wikipediaClient),
            )

        val result =
            agent.run(
                StudyRequestInput(
                    topicsText = "   ",
                    maxQuestions = 3,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        val failure = assertIs<StudyResearchWorkflowResult.ValidationFailed>(result)

        assertEquals(emptyList(), wikipediaClient.calls)
        assertEquals(emptyList(), failure.normalizedTopics)
        assertEquals(listOf("topicsText"), failure.issues.map { issue -> issue.field })
    }

    @Test
    fun strategyReturnsInsufficientSourcesWhenEvidenceRemainsDisambiguationOnly() = runTest {
        val wikipediaClient = RecordingWikipediaClient()
        val strategy = StudyResearchWorkflow.strategy(wikipediaClient)
        val promptExecutor = ToolCallingSearchPromptExecutor()
        val agent =
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = testAgentConfig(),
                strategy = strategy,
                toolRegistry = wikipediaToolRegistry(wikipediaClient),
            )

        val result =
            agent.run(
                StudyRequestInput(
                    topicsText = "Mercury",
                    maxQuestions = 2,
                    difficulty = Difficulty.EASY,
                ),
            )

        val insufficient = assertIs<StudyResearchWorkflowResult.InsufficientSources>(result)

        assertEquals(
            listOf(
                "search:Mercury:5",
                "article:Mercury (disambiguation)",
            ),
            wikipediaClient.calls,
        )
        assertEquals(listOf("Mercury"), promptExecutor.emittedSearchToolTopics)
        assertEquals(2, promptExecutor.searchStageCalls)
        assertEquals(listOf("Mercury (disambiguation)"), promptExecutor.emittedFetchToolTitles)
        assertEquals(2, promptExecutor.fetchStageCalls)
        assertEquals(1, insufficient.snapshot.searchStageMetadata.toolCallCount)
        assertEquals(1, insufficient.snapshot.fetchStageMetadata.toolCallCount)
        assertEquals(EvidenceStatus.INSUFFICIENT, insufficient.snapshot.evidence.status)
        assertEquals(listOf("Mercury"), insufficient.snapshot.evidence.missingTopics)
        assertEquals(0, insufficient.snapshot.effectiveQuestionCount)
    }

    @Test
    fun strategyReusesFetchedArticleWhenMultipleTopicsResolveToTheSamePage() = runTest {
        val wikipediaClient = RecordingWikipediaClient()
        val strategy = StudyResearchWorkflow.strategy(wikipediaClient)
        val promptExecutor = ToolCallingSearchPromptExecutor()
        val agent =
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = testAgentConfig(),
                strategy = strategy,
                toolRegistry = wikipediaToolRegistry(wikipediaClient),
            )

        val result =
            agent.run(
                StudyRequestInput(
                    topicsText = "JVM, Java Virtual Machine",
                    maxQuestions = 2,
                    difficulty = Difficulty.EASY,
                ),
            )

        val ready = assertIs<StudyResearchWorkflowResult.ReadyForGeneration>(result)
        val articleCalls = wikipediaClient.calls.filter { call -> call.startsWith("article:") }

        assertEquals(listOf("JVM", "Java Virtual Machine"), promptExecutor.emittedSearchToolTopics)
        assertEquals(3, promptExecutor.searchStageCalls)
        assertEquals(listOf("Java Virtual Machine"), promptExecutor.emittedFetchToolTitles)
        assertEquals(2, promptExecutor.fetchStageCalls)
        assertEquals(2, ready.snapshot.searchStageMetadata.toolCallCount)
        assertEquals(1, ready.snapshot.fetchStageMetadata.toolCallCount)
        assertEquals(listOf("article:Java Virtual Machine"), articleCalls)
        assertTrue(ready.snapshot.materials.all { material -> material.articles.single().summary.title == "Java Virtual Machine" })
    }

    private class RecordingWikipediaClient : WikipediaClient {
        val calls = mutableListOf<String>()

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<WikipediaSearchResult> {
            calls += "search:${query.trim()}:$limit"

            return when (query.trim()) {
                "Kotlin" ->
                    listOf(
                        WikipediaSearchResult(
                            pageId = 1L,
                            title = "Kotlin",
                            snippet = "Programming language for JVM and multiplatform development.",
                            canonicalUrl = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                        ),
                    )

                "Compose Multiplatform" ->
                    listOf(
                        WikipediaSearchResult(
                            pageId = 2L,
                            title = "Compose Multiplatform",
                            snippet = "Declarative UI framework by JetBrains.",
                            canonicalUrl = "https://en.wikipedia.org/wiki/Compose_Multiplatform",
                        ),
                    )

                "JVM" ->
                    listOf(
                        WikipediaSearchResult(
                            pageId = 3L,
                            title = "Java Virtual Machine",
                            snippet = "Virtual machine for Java bytecode.",
                            canonicalUrl = "https://en.wikipedia.org/wiki/Java_virtual_machine",
                        ),
                    )

                "Java Virtual Machine" ->
                    listOf(
                        WikipediaSearchResult(
                            pageId = 3L,
                            title = "Java Virtual Machine",
                            snippet = "Execution engine for Java applications.",
                            canonicalUrl = "https://en.wikipedia.org/wiki/Java_virtual_machine",
                        ),
                    )

                "Mercury" ->
                    listOf(
                        WikipediaSearchResult(
                            pageId = 4L,
                            title = "Mercury (disambiguation)",
                            snippet = "Mercury may refer to many different topics.",
                            canonicalUrl = "https://en.wikipedia.org/wiki/Mercury_(disambiguation)",
                            isDisambiguationHint = true,
                        ),
                    )

                else -> emptyList()
            }
        }

        override suspend fun fetchArticleSummary(title: String): WikipediaArticleSummary =
            articleFor(title).summary

        override suspend fun fetchArticle(title: String): WikipediaArticle {
            calls += "article:${title.trim()}"
            return articleFor(title)
        }

        private fun articleFor(title: String): WikipediaArticle {
            val normalizedTitle = title.trim()
            return when (normalizedTitle) {
                "Kotlin" ->
                    WikipediaArticle(
                        summary =
                            WikipediaArticleSummary(
                                pageId = 1L,
                                title = "Kotlin",
                                canonicalUrl = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                                description = "Programming language",
                                extract = "Kotlin is a modern programming language.",
                            ),
                        plainTextContent = longContent("Kotlin"),
                    )

                "Compose Multiplatform" ->
                    WikipediaArticle(
                        summary =
                            WikipediaArticleSummary(
                                pageId = 2L,
                                title = "Compose Multiplatform",
                                canonicalUrl = "https://en.wikipedia.org/wiki/Compose_Multiplatform",
                                description = "Declarative UI framework",
                                extract = "Compose Multiplatform is a UI toolkit by JetBrains.",
                            ),
                        plainTextContent = longContent("Compose Multiplatform"),
                    )

                "Java Virtual Machine" ->
                    WikipediaArticle(
                        summary =
                            WikipediaArticleSummary(
                                pageId = 3L,
                                title = "Java Virtual Machine",
                                canonicalUrl = "https://en.wikipedia.org/wiki/Java_virtual_machine",
                                description = "Virtual machine",
                                extract = "The Java Virtual Machine executes Java bytecode.",
                            ),
                        plainTextContent = longContent("Java Virtual Machine"),
                    )

                "Mercury (disambiguation)" ->
                    WikipediaArticle(
                        summary =
                            WikipediaArticleSummary(
                                pageId = 4L,
                                title = "Mercury (disambiguation)",
                                canonicalUrl = "https://en.wikipedia.org/wiki/Mercury_(disambiguation)",
                                description = "Disambiguation page",
                                extract = "Mercury may refer to multiple unrelated topics.",
                                isDisambiguation = true,
                            ),
                        plainTextContent = longContent("Mercury"),
                    )

                else -> error("Unexpected article title: $normalizedTitle")
            }
        }

        private fun longContent(subject: String): String =
            List(140) { index -> "$subject fact ${index + 1}" }.joinToString(separator = " ")
    }
}
