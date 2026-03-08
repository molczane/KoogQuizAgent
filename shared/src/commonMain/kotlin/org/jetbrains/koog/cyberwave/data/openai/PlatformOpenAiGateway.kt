package org.jetbrains.koog.cyberwave.data.openai

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

class PlatformOpenAiGateway(
    private val apiKeyProvider: ApiKeyProvider,
    private val llmModel: LLModel = OpenAIModels.Chat.GPT5Mini,
    private val promptExecutorFactory: (String) -> PromptExecutor = ::simpleOpenAIExecutor,
) {
    fun open(): PlatformOpenAiGatewayResult =
        when (val result = apiKeyProvider.resolve()) {
            is ApiKeyProviderResult.Available ->
                PlatformOpenAiGatewayResult.Ready(
                    promptExecutor = promptExecutorFactory(result.apiKey),
                    llmModel = llmModel,
                )

            is ApiKeyProviderResult.Unavailable ->
                PlatformOpenAiGatewayResult.ConfigurationError(result.error)
        }
}

sealed interface PlatformOpenAiGatewayResult {
    data class Ready(
        val promptExecutor: PromptExecutor,
        val llmModel: LLModel,
    ) : PlatformOpenAiGatewayResult

    data class ConfigurationError(
        val error: OpenAiConfigurationError,
    ) : PlatformOpenAiGatewayResult
}
