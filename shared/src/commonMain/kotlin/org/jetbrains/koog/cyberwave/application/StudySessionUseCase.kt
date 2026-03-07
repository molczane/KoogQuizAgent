package org.jetbrains.koog.cyberwave.application

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
                } finally {
                    gatewayResult.promptExecutor.close()
                }

            is PlatformOpenAiGatewayResult.ConfigurationError ->
                configurationErrorModel(
                    input = input,
                    error = gatewayResult.error,
                )
        }

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

    private companion object {
        private const val RETRY_LABEL: String = "Retry"
    }
}
