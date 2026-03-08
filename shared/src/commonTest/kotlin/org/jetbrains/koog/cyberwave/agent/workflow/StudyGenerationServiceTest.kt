package org.jetbrains.koog.cyberwave.agent.workflow

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.jetbrains.koog.cyberwave.agent.support.ToolCallingSearchPromptExecutor
import org.jetbrains.koog.cyberwave.agent.support.testLLModel
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.QuestionType
import org.jetbrains.koog.cyberwave.domain.model.QuizPayload
import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.domain.model.SummaryCard
import org.jetbrains.koog.cyberwave.observability.RecordingStudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.StudyWorkflowTraceStatus
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

class StudyGenerationServiceTest {
    @Test
    fun generateReturnsValidationErrorWithoutInvokingStructuredGeneration() = runTest {
        val promptExecutor = ToolCallingSearchPromptExecutor(structuredResponseJson = validReadyPayloadJson())
        val service =
            StudyGenerationService(
                promptExecutor = promptExecutor,
                llmModel = testLLModel,
                wikipediaClient = ReadyWikipediaClient(),
            )

        val result =
            service.generate(
                StudyRequestInput(
                    topicsText = "   ",
                    maxQuestions = 0,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        assertEquals(StudyGenerationState.VALIDATION_ERROR, result.state)
        assertEquals("Fix the study request", result.screenTitle)
        assertEquals(PrimaryActionId.RETRY, result.primaryAction?.id)
        assertTrue(result.error?.validationIssues?.isNotEmpty() == true)
        assertEquals(0, promptExecutor.executeCalls)
    }

    @Test
    fun generateReturnsInsufficientSourcesWithoutInvokingStructuredGeneration() = runTest {
        val promptExecutor = ToolCallingSearchPromptExecutor(structuredResponseJson = validReadyPayloadJson())
        val service =
            StudyGenerationService(
                promptExecutor = promptExecutor,
                llmModel = testLLModel,
                wikipediaClient = InsufficientWikipediaClient(),
            )

        val result =
            service.generate(
                StudyRequestInput(
                    topicsText = "Kotlin, Compose Multiplatform",
                    maxQuestions = 3,
                    difficulty = Difficulty.EASY,
                ),
            )

        assertEquals(StudyGenerationState.INSUFFICIENT_SOURCES, result.state)
        assertEquals("Not enough evidence yet", result.screenTitle)
        assertEquals(PrimaryActionId.RETRY, result.primaryAction?.id)
        assertContains(result.error?.message.orEmpty(), "Compose Multiplatform")
        assertEquals(3, promptExecutor.searchStageCalls)
        assertEquals(0, promptExecutor.structuredPayloadCalls)
    }

    @Test
    fun generateUsesStructuredPayloadGenerationForReadySnapshots() = runTest {
        val promptExecutor = ToolCallingSearchPromptExecutor(structuredResponseJson = validReadyPayloadJson())
        val tracer = RecordingStudyWorkflowTracer()
        val service =
            StudyGenerationService(
                promptExecutor = promptExecutor,
                llmModel = testLLModel,
                wikipediaClient = ReadyWikipediaClient(),
                tracer = tracer,
            )

        val result =
            service.generate(
                StudyRequestInput(
                    topicsText = "Kotlin",
                    maxQuestions = 2,
                    difficulty = Difficulty.HARD,
                ),
            )

        assertEquals(StudyGenerationState.READY, result.state)
        assertEquals("Ready Kotlin quiz", result.screenTitle)
        assertEquals(PrimaryActionId.START_QUIZ, result.primaryAction?.id)
        assertEquals(2, promptExecutor.searchStageCalls)
        assertEquals(1, promptExecutor.structuredPayloadCalls)
        assertEquals(listOf("Kotlin"), promptExecutor.emittedSearchToolTopics)
        assertContains(
            tracer.events.map { event -> "${event.spanName}:${event.status}" },
            "study_generation.payload.generate:${StudyWorkflowTraceStatus.SUCCEEDED}",
        )
        assertEquals(
            listOf(
                "study_generation.research.validate_input",
                "study_generation.research.prepare_queries",
                "study_generation.research.search_wikipedia",
                "study_generation.research.select_articles",
                "study_generation.research.fetch_articles",
                "study_generation.research.check_evidence",
            ),
            tracer.events
                .filter { event -> event.status == StudyWorkflowTraceStatus.SUCCEEDED && event.spanName.startsWith("study_generation.research.") }
                .map { event -> event.spanName },
        )
    }

    @Test
    fun generateEmitsFailedTraceWhenPayloadGenerationBreaksSchemaExpectations() = runTest {
        val promptExecutor = ToolCallingSearchPromptExecutor(structuredResponseJson = invalidReadyPayloadJson())
        val tracer = RecordingStudyWorkflowTracer()
        val service =
            StudyGenerationService(
                promptExecutor = promptExecutor,
                llmModel = testLLModel,
                wikipediaClient = ReadyWikipediaClient(),
                tracer = tracer,
            )

        val error =
            assertFailsWith<IllegalStateException> {
                service.generate(
                    StudyRequestInput(
                        topicsText = "Kotlin",
                        maxQuestions = 2,
                        difficulty = Difficulty.HARD,
                    ),
                )
            }

        assertContains(error.message.orEmpty(), "at least one valid quiz question")
        assertContains(
            tracer.events.map { event -> "${event.spanName}:${event.status}" },
            "study_generation.payload.generate:${StudyWorkflowTraceStatus.FAILED}",
        )
        assertContains(
            tracer.events.map { event -> "${event.spanName}:${event.status}" },
            "study_generation.service.generate:${StudyWorkflowTraceStatus.FAILED}",
        )
        assertEquals(2, promptExecutor.searchStageCalls)
        assertEquals(1, promptExecutor.structuredPayloadCalls)
    }

    private class ReadyWikipediaClient : WikipediaClient {
        override suspend fun search(
            query: String,
            limit: Int,
        ): List<WikipediaSearchResult> =
            listOf(
                WikipediaSearchResult(
                    pageId = 1L,
                    title = query.trim(),
                    snippet = "Strong Wikipedia evidence for ${query.trim()}.",
                    canonicalUrl = "https://en.wikipedia.org/wiki/${query.trim().replace(' ', '_')}",
                ),
            )

        override suspend fun fetchArticleSummary(title: String): WikipediaArticleSummary =
            article(title).summary

        override suspend fun fetchArticle(title: String): WikipediaArticle =
            article(title)

        private fun article(title: String): WikipediaArticle =
            WikipediaArticle(
                summary =
                    WikipediaArticleSummary(
                        pageId = 1L,
                        title = title.trim(),
                        canonicalUrl = "https://en.wikipedia.org/wiki/${title.trim().replace(' ', '_')}",
                        description = "Reference article",
                        extract = "$title has enough evidence for quiz generation.",
                    ),
                plainTextContent = List(140) { index -> "${title.trim()} fact ${index + 1}" }.joinToString(separator = " "),
            )
    }

    private class InsufficientWikipediaClient : WikipediaClient {
        override suspend fun search(
            query: String,
            limit: Int,
        ): List<WikipediaSearchResult> =
            if (query.trim() == "Kotlin") {
                listOf(
                    WikipediaSearchResult(
                        pageId = 1L,
                        title = "Kotlin",
                        snippet = "Programming language article.",
                        canonicalUrl = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                    ),
                )
            } else {
                emptyList()
            }

        override suspend fun fetchArticleSummary(title: String): WikipediaArticleSummary =
            fetchArticle(title).summary

        override suspend fun fetchArticle(title: String): WikipediaArticle =
            WikipediaArticle(
                summary =
                    WikipediaArticleSummary(
                        pageId = 1L,
                        title = title.trim(),
                        canonicalUrl = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                        description = "Programming language article",
                        extract = "Kotlin is a programming language.",
                    ),
                plainTextContent = List(140) { index -> "Kotlin detail ${index + 1}" }.joinToString(separator = " "),
            )
    }

    private companion object {
        private fun validReadyPayloadJson(): String =
            Json.encodeToString(
                StudyScreenModel.serializer(),
                StudyScreenModel(
                    screenTitle = "Ready Kotlin quiz",
                    topics = listOf("Ignored"),
                    summaryCards =
                        listOf(
                            SummaryCard(
                                title = "Kotlin overview",
                                bullets = listOf("Kotlin is modern.", "Kotlin targets the JVM."),
                                sourceRefs = listOf("wiki-1"),
                            ),
                        ),
                    quiz =
                        QuizPayload(
                            maxQuestions = 2,
                            questions =
                                listOf(
                                    QuizQuestion(
                                        id = "q1",
                                        type = QuestionType.SINGLE_CHOICE,
                                        prompt = "What is Kotlin?",
                                        options = listOf("A programming language", "A database", "A browser"),
                                        correctOptionIndex = 0,
                                        explanation = "Kotlin is a programming language.",
                                        sourceRefs = listOf("wiki-1"),
                                    ),
                                ),
                        ),
                    state = StudyGenerationState.READY,
                ),
            )

        private fun invalidReadyPayloadJson(): String =
            Json.encodeToString(
                StudyScreenModel.serializer(),
                StudyScreenModel(
                    screenTitle = "Broken Kotlin quiz",
                    topics = listOf("Kotlin"),
                    summaryCards =
                        listOf(
                            SummaryCard(
                                title = "Kotlin overview",
                                bullets = listOf("Kotlin is a language."),
                                sourceRefs = listOf("wiki-1"),
                            ),
                        ),
                    quiz =
                        QuizPayload(
                            maxQuestions = 2,
                            questions =
                                listOf(
                                    QuizQuestion(
                                        id = "q1",
                                        type = QuestionType.SINGLE_CHOICE,
                                        prompt = "Broken question",
                                        options = listOf("Only one option"),
                                        correctOptionIndex = 0,
                                        explanation = "Invalid because only one option remains.",
                                        sourceRefs = listOf("wiki-1"),
                                    ),
                                ),
                        ),
                    state = StudyGenerationState.READY,
                ),
            )
    }
}
