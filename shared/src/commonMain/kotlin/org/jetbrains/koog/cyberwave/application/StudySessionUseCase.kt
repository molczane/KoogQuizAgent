package org.jetbrains.koog.cyberwave.application

import kotlinx.coroutines.CancellationException
import org.jetbrains.koog.cyberwave.agent.workflow.StudyGenerationService
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationError
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationErrorKind
import org.jetbrains.koog.cyberwave.data.openai.PlatformOpenAiGateway
import org.jetbrains.koog.cyberwave.data.openai.PlatformOpenAiGatewayResult
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryAction
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

class StudySessionUseCase(
    private val wikipediaClient: WikipediaClient,
    private val openAiGateway: PlatformOpenAiGateway,
) {
    suspend fun generate(input: StudyRequestInput): StudyScreenModel =
        when (val gatewayResult = openAiGateway.open()) {
            is PlatformOpenAiGatewayResult.Ready ->
                try {
                    StudyGenerationService(
                        promptExecutor = gatewayResult.promptExecutor,
                        llmModel = gatewayResult.llmModel,
                        wikipediaClient = wikipediaClient,
                    ).generate(input)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Throwable) {
                    generationErrorModel(
                        input = input,
                        cause = exception,
                    )
                } finally {
                    gatewayResult.promptExecutor.close()
                }

            is PlatformOpenAiGatewayResult.ConfigurationError ->
                configurationErrorModel(
                    input = input,
                    error = gatewayResult.error,
                )
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
                    message = generationErrorMessage(cause),
                ),
        )

    private fun configurationErrorModel(
        input: StudyRequestInput,
        error: OpenAiConfigurationError,
    ): StudyScreenModel =
        StudyScreenModel(
            screenTitle = configurationScreenTitle(error.kind),
            topics = StudyRequestParser.parseTopics(input.topicsText),
            state = StudyGenerationState.CONFIGURATION_ERROR,
            primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = RETRY_LABEL),
            error =
                StudyScreenError(
                    title = error.title,
                    message = error.message,
                ),
        )

    private fun configurationScreenTitle(kind: OpenAiConfigurationErrorKind): String =
        when (kind) {
            OpenAiConfigurationErrorKind.MISSING_API_KEY -> "OpenAI setup required"
            OpenAiConfigurationErrorKind.INVALID_MODE -> "Local direct mode required"
        }

    private fun generationErrorMessage(cause: Throwable): String {
        val reason = cause.message?.trim().orEmpty()
        return if (reason.isEmpty()) {
            "The local research or generation flow failed before a study payload could be produced. Check your network and OpenAI setup, then try again."
        } else {
            "The local research or generation flow failed before a study payload could be produced. Reason: $reason"
        }
    }

    private companion object {
        private const val GENERATION_SCREEN_TITLE: String = "Generation interrupted"
        private const val GENERATION_ERROR_TITLE: String = "Unable to build the study session"
        private const val RETRY_LABEL: String = "Retry"
    }
}
