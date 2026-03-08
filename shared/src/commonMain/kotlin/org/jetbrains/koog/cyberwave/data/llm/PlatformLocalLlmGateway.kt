package org.jetbrains.koog.cyberwave.data.llm

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import org.jetbrains.koog.cyberwave.data.openai.ApiKeyProvider
import org.jetbrains.koog.cyberwave.data.openai.ApiKeyProviderResult
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationError
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmDefaults
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmProvider

class PlatformLocalLlmGateway(
    private val openAiApiKeyProvider: ApiKeyProvider,
    private val openAiLlmModel: LLModel = OpenAIModels.Chat.GPT5Mini,
    private val ollamaLlmModel: LLModel = OllamaModels.Meta.LLAMA_3_2,
    private val openAiPromptExecutorFactory: (String) -> PromptExecutor = ::simpleOpenAIExecutor,
    private val ollamaPromptExecutorFactory: (String) -> PromptExecutor = ::simpleOllamaAIExecutor,
    private val ollamaBaseUrl: String = LocalLlmDefaults.OLLAMA_BASE_URL,
) {
    fun open(provider: LocalLlmProvider): PlatformLocalLlmGatewayResult =
        when (provider) {
            LocalLlmProvider.OPENAI ->
                when (val result = openAiApiKeyProvider.resolve()) {
                    is ApiKeyProviderResult.Available ->
                        PlatformLocalLlmGatewayResult.Ready(
                            provider = provider,
                            promptExecutor = openAiPromptExecutorFactory(result.apiKey),
                            llmModel = openAiLlmModel,
                        )

                    is ApiKeyProviderResult.Unavailable ->
                        PlatformLocalLlmGatewayResult.ConfigurationError(
                            provider = provider,
                            error = result.error,
                        )
                }

            LocalLlmProvider.OLLAMA ->
                PlatformLocalLlmGatewayResult.Ready(
                    provider = provider,
                    promptExecutor = ollamaPromptExecutorFactory(ollamaBaseUrl),
                    llmModel = ollamaLlmModel,
                )
        }
}

sealed interface PlatformLocalLlmGatewayResult {
    data class Ready(
        val provider: LocalLlmProvider,
        val promptExecutor: PromptExecutor,
        val llmModel: LLModel,
    ) : PlatformLocalLlmGatewayResult

    data class ConfigurationError(
        val provider: LocalLlmProvider,
        val error: OpenAiConfigurationError,
    ) : PlatformLocalLlmGatewayResult
}
