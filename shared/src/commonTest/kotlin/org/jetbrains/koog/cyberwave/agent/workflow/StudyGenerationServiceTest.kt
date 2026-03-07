package org.jetbrains.koog.cyberwave.agent.workflow

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

class StudyGenerationServiceTest {
    @Test
    fun generateReturnsValidationErrorWithoutInvokingStructuredGeneration() = runTest {
        val promptExecutor = RecordingPromptExecutor(responseJson = validReadyPayloadJson())
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
        val promptExecutor = RecordingPromptExecutor(responseJson = validReadyPayloadJson())
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
        assertEquals(0, promptExecutor.executeCalls)
    }

    @Test
    fun generateUsesStructuredPayloadGenerationForReadySnapshots() = runTest {
        val promptExecutor = RecordingPromptExecutor(responseJson = validReadyPayloadJson())
        val service =
            StudyGenerationService(
                promptExecutor = promptExecutor,
                llmModel = testLLModel,
                wikipediaClient = ReadyWikipediaClient(),
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
        assertEquals(1, promptExecutor.executeCalls)
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

    private class RecordingPromptExecutor(
        private val responseJson: String,
    ) : PromptExecutor {
        var executeCalls: Int = 0
            private set

        override suspend fun execute(
            prompt: Prompt,
            model: ai.koog.prompt.llm.LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            executeCalls += 1
            return listOf(Message.Assistant(responseJson, ResponseMetaInfo.Empty))
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: ai.koog.prompt.llm.LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = emptyFlow()

        override suspend fun moderate(
            prompt: Prompt,
            model: ai.koog.prompt.llm.LLModel,
        ): ModerationResult = error("Moderation is not used in this test.")

        override fun close() = Unit
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
    }
}
