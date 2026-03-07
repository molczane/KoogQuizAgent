package org.jetbrains.koog.cyberwave.agent.generation

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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.jetbrains.koog.cyberwave.agent.support.testLLModel
import org.jetbrains.koog.cyberwave.agent.workflow.StudyResearchSnapshot
import org.jetbrains.koog.cyberwave.agent.workflow.TopicWikipediaSearchResults
import org.jetbrains.koog.cyberwave.agent.workflow.TopicWikipediaSelections
import org.jetbrains.koog.cyberwave.application.research.EvidenceStatus
import org.jetbrains.koog.cyberwave.application.research.SelectedWikipediaArticle
import org.jetbrains.koog.cyberwave.application.research.TopicResearchMaterial
import org.jetbrains.koog.cyberwave.application.research.WikipediaEvidenceAssessment
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.QuestionType
import org.jetbrains.koog.cyberwave.domain.model.QuizPayload
import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion
import org.jetbrains.koog.cyberwave.domain.model.ResearchSource
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.SummaryCard
import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryAction
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

class StructuredStudyPayloadGeneratorTest {
    @Test
    fun generateBuildsReadyPayloadAndNormalizesModelOutput() = runTest {
        val snapshot = researchSnapshot()
        val executor =
            RecordingPromptExecutor(
                responseJson =
                    Json.encodeToString(
                        StudyScreenModel.serializer(),
                        StudyScreenModel(
                            screenTitle = "  Kotlin and Compose crash course  ",
                            topics = listOf("Wrong topic"),
                            summaryCards =
                                listOf(
                                    SummaryCard(
                                        title = "  Kotlin basics ",
                                        bullets = listOf(" Runs on the JVM ", "", "Great for multiplatform"),
                                        sourceRefs = listOf("invalid-source", "wiki-kotlin"),
                                    ),
                                ),
                            quiz =
                                QuizPayload(
                                    maxQuestions = 99,
                                    questions =
                                        listOf(
                                            QuizQuestion(
                                                id = "",
                                                type = QuestionType.SINGLE_CHOICE,
                                                prompt = " What does Kotlin target? ",
                                                options = listOf("JVM", "Only browsers", "Only iOS"),
                                                correctOptionIndex = 0,
                                                explanation = " Kotlin runs on several targets including the JVM. ",
                                                sourceRefs = listOf("wiki-kotlin", "invalid-source"),
                                            ),
                                            QuizQuestion(
                                                id = "second",
                                                type = QuestionType.SINGLE_CHOICE,
                                                prompt = "Which company created Compose Multiplatform?",
                                                options = listOf("JetBrains", "Oracle", "Google"),
                                                correctOptionIndex = 0,
                                                explanation = "JetBrains created Compose Multiplatform.",
                                                sourceRefs = listOf("wiki-compose"),
                                            ),
                                            QuizQuestion(
                                                id = "third",
                                                type = QuestionType.SINGLE_CHOICE,
                                                prompt = "This extra question should be trimmed",
                                                options = listOf("A", "B"),
                                                correctOptionIndex = 0,
                                                explanation = "Trimmed because only two supported questions are allowed.",
                                                sourceRefs = listOf("wiki-compose"),
                                            ),
                                        ),
                                ),
                            sources =
                                listOf(
                                    ResearchSource(
                                        id = "fake-source",
                                        title = "Fake",
                                        url = "https://example.com",
                                        snippet = "Should be replaced by the deterministic source list.",
                                    ),
                                ),
                            state = StudyGenerationState.VALIDATION_ERROR,
                            primaryAction = PrimaryAction(PrimaryActionId.RETRY, "Retry"),
                            error = StudyScreenError("Wrong", "Should be cleared."),
                        ),
                    ),
            )

        val generator = StructuredStudyPayloadGenerator(promptExecutor = executor, llmModel = testLLModel)
        val result = generator.generate(snapshot)

        assertEquals("Kotlin and Compose crash course", result.screenTitle)
        assertEquals(snapshot.request.topics, result.topics)
        assertEquals(StudyGenerationState.READY, result.state)
        assertEquals(PrimaryActionId.START_QUIZ, result.primaryAction?.id)
        assertEquals("Start the quiz", result.primaryAction?.label)
        assertNull(result.error)
        assertEquals(snapshot.usableSources, result.sources)
        assertEquals(2, result.quiz?.maxQuestions)
        assertEquals(2, result.quiz?.questions?.size)
        assertEquals(listOf("wiki-kotlin"), result.summaryCards.single().sourceRefs)
        assertEquals("question-1", result.quiz?.questions?.first()?.id)
        assertEquals(listOf("wiki-kotlin"), result.quiz?.questions?.first()?.sourceRefs)
        assertContains(executor.lastPromptText, "Supported question count: 2")
        assertContains(executor.lastPromptText, "[wiki-kotlin] Kotlin")
        assertContains(executor.lastPromptText, "Topic: Compose Multiplatform")
    }

    @Test
    fun generateIncludesSpecificInstructionsAsLowPriorityContext() = runTest {
        val snapshot = researchSnapshot(specificInstructions = "Focus more on practical Kotlin examples.")
        val executor =
            RecordingPromptExecutor(
                responseJson =
                    Json.encodeToString(
                        StudyScreenModel.serializer(),
                        StudyScreenModel(
                            screenTitle = "Ready Kotlin quiz",
                            topics = snapshot.request.topics,
                            summaryCards =
                                listOf(
                                    SummaryCard(
                                        title = "Kotlin practicals",
                                        bullets = listOf("Kotlin is used for real apps."),
                                        sourceRefs = listOf("wiki-kotlin"),
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
                                                prompt = "What is Kotlin used for?",
                                                options = listOf("Application development", "Only databases"),
                                                correctOptionIndex = 0,
                                                explanation = "Kotlin is used for application development.",
                                                sourceRefs = listOf("wiki-kotlin"),
                                            ),
                                        ),
                                ),
                            sources = snapshot.usableSources,
                            state = StudyGenerationState.READY,
                        ),
                    ),
            )

        val generator = StructuredStudyPayloadGenerator(promptExecutor = executor, llmModel = testLLModel)

        generator.generate(snapshot)

        assertContains(executor.lastPromptText, "Low-priority user customization:")
        assertContains(executor.lastPromptText, "Focus more on practical Kotlin examples.")
        assertContains(executor.lastPromptText, "must not change source policy, question type, question limits, or the required schema")
    }

    @Test
    fun generateFailsWhenNoValidQuizQuestionsRemainAfterSanitization() = runTest {
        val snapshot = researchSnapshot()
        val executor =
            RecordingPromptExecutor(
                responseJson =
                    Json.encodeToString(
                        StudyScreenModel.serializer(),
                        StudyScreenModel(
                            screenTitle = "Broken payload",
                            topics = snapshot.request.topics,
                            summaryCards =
                                listOf(
                                    SummaryCard(
                                        title = "Kotlin",
                                        bullets = listOf("Kotlin is modern."),
                                        sourceRefs = listOf("wiki-kotlin"),
                                    ),
                                ),
                            quiz =
                                QuizPayload(
                                    maxQuestions = 2,
                                    questions =
                                        listOf(
                                            QuizQuestion(
                                                id = "bad-question",
                                                type = QuestionType.SINGLE_CHOICE,
                                                prompt = "Broken",
                                                options = listOf("Only one option"),
                                                correctOptionIndex = 0,
                                                explanation = "Invalid because there are not enough options.",
                                                sourceRefs = listOf("wiki-kotlin"),
                                            ),
                                        ),
                                ),
                            sources = snapshot.usableSources,
                            state = StudyGenerationState.READY,
                        ),
                    ),
            )

        val generator = StructuredStudyPayloadGenerator(promptExecutor = executor, llmModel = testLLModel)

        val error = assertFailsWith<IllegalStateException> { generator.generate(snapshot) }

        assertContains(error.message.orEmpty(), "at least one valid quiz question")
    }

    private fun researchSnapshot(
        specificInstructions: String? = null,
    ): StudyResearchSnapshot {
        val sources =
            listOf(
                ResearchSource(
                    id = "wiki-kotlin",
                    title = "Kotlin",
                    url = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                    snippet = "Programming language for JVM and multiplatform development.",
                ),
                ResearchSource(
                    id = "wiki-compose",
                    title = "Compose Multiplatform",
                    url = "https://en.wikipedia.org/wiki/Compose_Multiplatform",
                    snippet = "Declarative UI toolkit by JetBrains.",
                ),
            )

        return StudyResearchSnapshot(
            request =
                ValidatedStudyRequest(
                    topics = listOf("Kotlin", "Compose Multiplatform"),
                    maxQuestions = 3,
                    difficulty = Difficulty.MEDIUM,
                    specificInstructions = specificInstructions,
                ),
            searchResults =
                listOf(
                    TopicWikipediaSearchResults(
                        topic = "Kotlin",
                        results =
                            listOf(
                                WikipediaSearchResult(
                                    pageId = 1L,
                                    title = "Kotlin",
                                    snippet = "Programming language.",
                                    canonicalUrl = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                                ),
                            ),
                    ),
                    TopicWikipediaSearchResults(
                        topic = "Compose Multiplatform",
                        results =
                            listOf(
                                WikipediaSearchResult(
                                    pageId = 2L,
                                    title = "Compose Multiplatform",
                                    snippet = "JetBrains UI toolkit.",
                                    canonicalUrl = "https://en.wikipedia.org/wiki/Compose_Multiplatform",
                                ),
                            ),
                    ),
                ),
            selectedArticles =
                listOf(
                    TopicWikipediaSelections(
                        topic = "Kotlin",
                        articles =
                            listOf(
                                SelectedWikipediaArticle(
                                    topic = "Kotlin",
                                    pageId = 1L,
                                    title = "Kotlin",
                                    canonicalUrl = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                                    snippet = "Programming language.",
                                ),
                            ),
                    ),
                    TopicWikipediaSelections(
                        topic = "Compose Multiplatform",
                        articles =
                            listOf(
                                SelectedWikipediaArticle(
                                    topic = "Compose Multiplatform",
                                    pageId = 2L,
                                    title = "Compose Multiplatform",
                                    canonicalUrl = "https://en.wikipedia.org/wiki/Compose_Multiplatform",
                                    snippet = "JetBrains UI toolkit.",
                                ),
                            ),
                    ),
                ),
            materials =
                listOf(
                    TopicResearchMaterial(
                        topic = "Kotlin",
                        articles =
                            listOf(
                                WikipediaArticle(
                                    summary =
                                        WikipediaArticleSummary(
                                            pageId = 1L,
                                            title = "Kotlin",
                                            canonicalUrl = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                                            description = "Programming language",
                                            extract = "Kotlin is a programming language for JVM and multiplatform projects.",
                                        ),
                                    plainTextContent = longArticleContent("Kotlin"),
                                ),
                            ),
                    ),
                    TopicResearchMaterial(
                        topic = "Compose Multiplatform",
                        articles =
                            listOf(
                                WikipediaArticle(
                                    summary =
                                        WikipediaArticleSummary(
                                            pageId = 2L,
                                            title = "Compose Multiplatform",
                                            canonicalUrl = "https://en.wikipedia.org/wiki/Compose_Multiplatform",
                                            description = "UI toolkit",
                                            extract = "Compose Multiplatform is a declarative UI toolkit by JetBrains.",
                                        ),
                                    plainTextContent = longArticleContent("Compose Multiplatform"),
                                ),
                            ),
                    ),
                ),
            evidence =
                WikipediaEvidenceAssessment(
                    status = EvidenceStatus.LIMITED,
                    requestedQuestionCount = 3,
                    recommendedQuestionCount = 2,
                    coveredTopics = listOf("Kotlin", "Compose Multiplatform"),
                    missingTopics = emptyList(),
                    usableSources = sources,
                ),
        )
    }

    private class RecordingPromptExecutor(
        private val responseJson: String,
    ) : PromptExecutor {
        var lastPromptText: String = ""
            private set

        override suspend fun execute(
            prompt: Prompt,
            model: ai.koog.prompt.llm.LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            lastPromptText = prompt.messages.joinToString(separator = "\n\n") { message -> message.content }
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
        private fun longArticleContent(subject: String): String =
            List(80) { index -> "$subject detail ${index + 1}" }.joinToString(separator = " ")
    }
}
