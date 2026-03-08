package org.jetbrains.koog.cyberwave.application

import kotlinx.coroutines.CancellationException
import org.jetbrains.koog.cyberwave.agent.workflow.StudyGenerationService
import org.jetbrains.koog.cyberwave.data.llm.PlatformLocalLlmGateway
import org.jetbrains.koog.cyberwave.data.llm.PlatformLocalLlmGatewayResult
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationError
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationErrorKind
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmDefaults
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmProvider
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.observability.NoOpStudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.StudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.traceSpan
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryAction
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

class StudySessionUseCase(
    private val wikipediaClient: WikipediaClient,
    private val localLlmGateway: PlatformLocalLlmGateway,
    private val tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
) {
    suspend fun generate(input: StudyRequestInput): StudyScreenModel =
        tracer.traceSpan(
            name = "study_session.generate",
            attributes =
                mapOf(
                    "requested_question_count" to input.maxQuestions.toString(),
                    "requested_provider" to input.provider.name.lowercase(),
                ),
            successAttributes = { result ->
                mapOf(
                    "result_state" to result.state.name.lowercase(),
                    "result_topic_count" to result.topics.size.toString(),
                )
            },
        ) {
            try {
                when (
                    val gatewayResult =
                        tracer.traceSpan(
                            name = "study_session.local_llm_gateway.open",
                            successAttributes = { result ->
                                when (result) {
                                    is PlatformLocalLlmGatewayResult.Ready ->
                                        mapOf(
                                            "gateway_outcome" to "ready",
                                            "provider" to result.provider.name.lowercase(),
                                        )

                                    is PlatformLocalLlmGatewayResult.ConfigurationError ->
                                        mapOf(
                                            "gateway_outcome" to "configuration_error",
                                            "provider" to result.provider.name.lowercase(),
                                            "configuration_kind" to result.error.kind.name.lowercase(),
                                        )
                                }
                            },
                        ) {
                            localLlmGateway.open(input.provider)
                        }
                ) {
                    is PlatformLocalLlmGatewayResult.Ready ->
                        try {
                            StudyGenerationService(
                                promptExecutor = gatewayResult.promptExecutor,
                                llmModel = gatewayResult.llmModel,
                                wikipediaClient = wikipediaClient,
                                tracer = tracer,
                            ).generate(input)
                        } finally {
                            gatewayResult.promptExecutor.close()
                        }

                    is PlatformLocalLlmGatewayResult.ConfigurationError ->
                        configurationErrorModel(
                            input = input,
                            provider = gatewayResult.provider,
                            error = gatewayResult.error,
                        )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                generationErrorModel(
                    input = input,
                    cause = exception,
                )
            }
        }

    private fun generationErrorModel(
        input: StudyRequestInput,
        cause: Throwable,
    ): StudyScreenModel =
        StudyScreenModel(
            screenTitle = GENERATION_SCREEN_TITLE,
            topics = StudyRequestParser.parseTopics(input.topicsText),
            state = StudyGenerationState.GENERATION_ERROR,
            primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = RETRY_LABEL),
            error =
                StudyScreenError(
                    title = GENERATION_ERROR_TITLE,
                    message = generationErrorMessage(input.provider, cause),
                ),
        )

    private fun configurationErrorModel(
        input: StudyRequestInput,
        provider: LocalLlmProvider,
        error: OpenAiConfigurationError,
    ): StudyScreenModel =
        StudyScreenModel(
            screenTitle = configurationScreenTitle(provider = provider, kind = error.kind),
            topics = StudyRequestParser.parseTopics(input.topicsText),
            state = StudyGenerationState.CONFIGURATION_ERROR,
            primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = RETRY_LABEL),
            error =
                StudyScreenError(
                    title = error.title,
                    message = error.message,
                ),
        )

    private fun configurationScreenTitle(
        provider: LocalLlmProvider,
        kind: OpenAiConfigurationErrorKind,
    ): String =
        when (provider) {
            LocalLlmProvider.OPENAI ->
                when (kind) {
                    OpenAiConfigurationErrorKind.MISSING_API_KEY -> "OpenAI setup required"
                    OpenAiConfigurationErrorKind.INVALID_MODE -> "Local direct mode required"
                }

            LocalLlmProvider.OLLAMA -> "Local Ollama setup required"
        }

    private fun generationErrorMessage(
        provider: LocalLlmProvider,
        cause: Throwable,
    ): String {
        val reason = cause.message?.trim().orEmpty()
        val setupGuidance =
            when (provider) {
                LocalLlmProvider.OPENAI -> "Check your network and OpenAI setup, then try again."
                LocalLlmProvider.OLLAMA ->
                    "Verify that Ollama is running locally at ${LocalLlmDefaults.OLLAMA_BASE_URL} " +
                        "and that the ${LocalLlmDefaults.OLLAMA_MODEL_NAME} model is available, then try again."
            }

        return if (reason.isEmpty()) {
            "The local research or generation flow failed before a study payload could be produced. $setupGuidance"
        } else {
            "The local research or generation flow failed before a study payload could be produced. Reason: $reason. $setupGuidance"
        }
    }

    private companion object {
        private const val GENERATION_SCREEN_TITLE: String = "Generation interrupted"
        private const val GENERATION_ERROR_TITLE: String = "Unable to build the study session"
        private const val RETRY_LABEL: String = "Retry"
    }
}
