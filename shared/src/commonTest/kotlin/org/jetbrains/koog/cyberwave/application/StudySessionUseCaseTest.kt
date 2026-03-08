package org.jetbrains.koog.cyberwave.application

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jetbrains.koog.cyberwave.agent.support.ToolCallingSearchPromptExecutor
import org.jetbrains.koog.cyberwave.agent.support.testLLModel
import org.jetbrains.koog.cyberwave.data.llm.PlatformLocalLlmGateway
import org.jetbrains.koog.cyberwave.data.openai.ApiKeyProvider
import org.jetbrains.koog.cyberwave.data.openai.ApiKeyProviderResult
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationError
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationErrorKind
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticle
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaArticleSummary
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmDefaults
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmProvider
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

class StudySessionUseCaseTest {
    @Test
    fun `generate returns configuration error when api key is missing`() = runTest {
        val tracer = RecordingStudyWorkflowTracer()
        val useCase =
            StudySessionUseCase(
                wikipediaClient = ReadyWikipediaClient(),
                localLlmGateway =
                    gatewayFor(
                        ApiKeyProviderResult.Unavailable(
                            OpenAiConfigurationError(
                                kind = OpenAiConfigurationErrorKind.MISSING_API_KEY,
                                title = "OpenAI API key is missing",
                                message = "Set OPENAI_API_KEY locally.",
                            ),
                        ),
                    ),
                tracer = tracer,
            )

        val result =
            useCase.generate(
                StudyRequestInput(
                    topicsText = "Kotlin, Compose",
                    maxQuestions = 2,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        assertEquals(StudyGenerationState.CONFIGURATION_ERROR, result.state)
        assertEquals("OpenAI setup required", result.screenTitle)
        assertEquals(listOf("Kotlin", "Compose"), result.topics)
        assertEquals(PrimaryActionId.RETRY, result.primaryAction?.id)
        assertEquals("OpenAI API key is missing", result.error?.title)
        assertEquals(
            listOf(
                "study_session.generate:${StudyWorkflowTraceStatus.STARTED}",
                "study_session.local_llm_gateway.open:${StudyWorkflowTraceStatus.STARTED}",
                "study_session.local_llm_gateway.open:${StudyWorkflowTraceStatus.SUCCEEDED}",
                "study_session.generate:${StudyWorkflowTraceStatus.SUCCEEDED}",
            ),
            tracer.events.map { event -> "${event.spanName}:${event.status}" },
        )
        assertEquals("configuration_error", tracer.events[2].attributes["gateway_outcome"])
        assertEquals("missing_api_key", tracer.events[2].attributes["configuration_kind"])
        assertEquals("configuration_error", tracer.events.last().attributes["result_state"])
    }

    @Test
    fun `generate returns configuration error when local direct mode is invalid`() = runTest {
        val useCase =
            StudySessionUseCase(
                wikipediaClient = ReadyWikipediaClient(),
                localLlmGateway =
                    gatewayFor(
                        ApiKeyProviderResult.Unavailable(
                            OpenAiConfigurationError(
                                kind = OpenAiConfigurationErrorKind.INVALID_MODE,
                                title = "Enable local direct mode",
                                message = "Set the browser mode to local_direct.",
                            ),
                        ),
                    ),
            )

        val result =
            useCase.generate(
                StudyRequestInput(
                    topicsText = "Kotlin",
                    maxQuestions = 2,
                    difficulty = Difficulty.EASY,
                ),
            )

        assertEquals(StudyGenerationState.CONFIGURATION_ERROR, result.state)
        assertEquals("Local direct mode required", result.screenTitle)
        assertEquals("Enable local direct mode", result.error?.title)
    }

    @Test
    fun `generate returns generation error when gateway opening fails unexpectedly`() = runTest {
        val useCase =
            StudySessionUseCase(
                wikipediaClient = ReadyWikipediaClient(),
                localLlmGateway =
                    PlatformLocalLlmGateway(
                        openAiApiKeyProvider = StaticApiKeyProvider(ApiKeyProviderResult.Available("sk-live")),
                        openAiLlmModel = testLLModel,
                        ollamaLlmModel = testLLModel,
                        openAiPromptExecutorFactory = { error("Missing runtime symbol: Clock.System") },
                    ),
            )

        val result =
            useCase.generate(
                StudyRequestInput(
                    topicsText = "Kotlin",
                    maxQuestions = 2,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        assertEquals(StudyGenerationState.GENERATION_ERROR, result.state)
        assertEquals("Generation interrupted", result.screenTitle)
        assertTrue(result.error?.message?.contains("Clock.System") == true)
    }

    @Test
    fun `generate returns Ollama guidance when the local Ollama runtime cannot be opened`() = runTest {
        val useCase =
            StudySessionUseCase(
                wikipediaClient = ReadyWikipediaClient(),
                localLlmGateway =
                    PlatformLocalLlmGateway(
                        openAiApiKeyProvider = StaticApiKeyProvider(ApiKeyProviderResult.Available("sk-live")),
                        openAiLlmModel = testLLModel,
                        ollamaLlmModel = testLLModel,
                        openAiPromptExecutorFactory = { error("OpenAI should not be used in the Ollama branch.") },
                        ollamaPromptExecutorFactory = {
                            error("Unable to reach Ollama host ${LocalLlmDefaults.OLLAMA_BASE_URL}")
                        },
                    ),
            )

        val result =
            useCase.generate(
                StudyRequestInput(
                    topicsText = "Kotlin",
                    maxQuestions = 2,
                    difficulty = Difficulty.MEDIUM,
                    provider = LocalLlmProvider.OLLAMA,
                ),
            )

        assertEquals(StudyGenerationState.GENERATION_ERROR, result.state)
        assertEquals("Generation interrupted", result.screenTitle)
        assertTrue(result.error?.message?.contains(LocalLlmDefaults.OLLAMA_BASE_URL) == true)
        assertTrue(result.error?.message?.contains(LocalLlmDefaults.OLLAMA_MODEL_NAME) == true)
    }

    @Test
    fun `generate returns generation error when research fails unexpectedly`() = runTest {
        val tracer = RecordingStudyWorkflowTracer()
        val useCase =
            StudySessionUseCase(
                wikipediaClient = FailingWikipediaClient(),
                localLlmGateway =
                    PlatformLocalLlmGateway(
                        openAiApiKeyProvider = StaticApiKeyProvider(ApiKeyProviderResult.Available("sk-live")),
                        openAiLlmModel = testLLModel,
                        ollamaLlmModel = testLLModel,
                        openAiPromptExecutorFactory = { ClosablePromptExecutor(responseJson = readyPayloadJson()) },
                    ),
                tracer = tracer,
            )

        val result =
            useCase.generate(
                StudyRequestInput(
                    topicsText = "Kotlin",
                    maxQuestions = 2,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        assertEquals(StudyGenerationState.GENERATION_ERROR, result.state)
        assertEquals("Generation interrupted", result.screenTitle)
        assertEquals("Unable to build the study session", result.error?.title)
        assertTrue(result.error?.message?.contains("offline for test") == true)
        assertContains(
            tracer.events.map { event -> "${event.spanName}:${event.status}" },
            "study_generation.service.generate:${StudyWorkflowTraceStatus.FAILED}",
        )
        assertEquals("generation_error", tracer.events.last().attributes["result_state"])
    }

    @Test
    fun `generate closes the prompt executor after successful generation`() = runTest {
        val promptExecutor = ClosablePromptExecutor(responseJson = readyPayloadJson())
        val useCase =
            StudySessionUseCase(
                wikipediaClient = ReadyWikipediaClient(),
                localLlmGateway =
                    PlatformLocalLlmGateway(
                        openAiApiKeyProvider = StaticApiKeyProvider(ApiKeyProviderResult.Available("sk-live")),
                        openAiLlmModel = testLLModel,
                        ollamaLlmModel = testLLModel,
                        openAiPromptExecutorFactory = { promptExecutor },
                    ),
            )

        val result =
            useCase.generate(
                StudyRequestInput(
                    topicsText = "Kotlin",
                    maxQuestions = 2,
                    difficulty = Difficulty.HARD,
                ),
            )

        assertEquals(StudyGenerationState.READY, result.state)
        assertTrue(promptExecutor.closed)
    }

    private fun gatewayFor(result: ApiKeyProviderResult): PlatformLocalLlmGateway =
        PlatformLocalLlmGateway(
            openAiApiKeyProvider = StaticApiKeyProvider(result),
            openAiLlmModel = testLLModel,
            ollamaLlmModel = testLLModel,
            openAiPromptExecutorFactory = { ClosablePromptExecutor(responseJson = readyPayloadJson()) },
        )

    private class StaticApiKeyProvider(
        private val result: ApiKeyProviderResult,
    ) : ApiKeyProvider {
        override fun resolve(): ApiKeyProviderResult = result
    }

    private class ClosablePromptExecutor(
        private val responseJson: String,
    ) : PromptExecutor {
        private val delegate = ToolCallingSearchPromptExecutor(structuredResponseJson = responseJson)
        var closed: Boolean = false
            private set

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> = delegate.execute(prompt, model, tools)

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = delegate.executeStreaming(prompt, model, tools)

        override suspend fun moderate(
            prompt: Prompt,
            model: LLModel,
        ): ModerationResult = delegate.moderate(prompt, model)

        override fun close() {
            closed = true
        }
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
                    snippet = "Wikipedia evidence for ${query.trim()}.",
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
                plainTextContent = List(160) { index -> "${title.trim()} detail ${index + 1}" }.joinToString(separator = " "),
            )
    }

    private class FailingWikipediaClient : WikipediaClient {
        override suspend fun search(
            query: String,
            limit: Int,
        ): List<WikipediaSearchResult> = error("Wikipedia is offline for test.")

        override suspend fun fetchArticleSummary(title: String): WikipediaArticleSummary =
            error("Not used when search fails.")

        override suspend fun fetchArticle(title: String): WikipediaArticle =
            error("Not used when search fails.")
    }

    private companion object {
        private fun readyPayloadJson(): String =
            Json.encodeToString(
                StudyScreenModel.serializer(),
                StudyScreenModel(
                    screenTitle = "Ready Kotlin quiz",
                    topics = listOf("Kotlin"),
                    summaryCards =
                        listOf(
                            SummaryCard(
                                title = "Kotlin overview",
                                bullets = listOf("Kotlin is a language.", "Kotlin targets the JVM."),
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
                                        options = listOf("A language", "A browser"),
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
