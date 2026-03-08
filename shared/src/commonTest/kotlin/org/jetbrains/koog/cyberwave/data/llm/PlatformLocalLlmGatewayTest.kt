package org.jetbrains.koog.cyberwave.data.llm

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.jetbrains.koog.cyberwave.agent.support.testLLModel
import org.jetbrains.koog.cyberwave.data.openai.ApiKeyProvider
import org.jetbrains.koog.cyberwave.data.openai.ApiKeyProviderResult
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationError
import org.jetbrains.koog.cyberwave.data.openai.OpenAiConfigurationErrorKind
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmProvider

class PlatformLocalLlmGatewayTest {
    @Test
    fun `open uses the OpenAI branch when provider is OPENAI`() {
        var receivedApiKey: String? = null
        val fakeExecutor = FakePromptExecutor()
        val gateway =
            PlatformLocalLlmGateway(
                openAiApiKeyProvider = FakeApiKeyProvider(ApiKeyProviderResult.Available(apiKey = "sk-ready")),
                openAiLlmModel = testLLModel,
                ollamaLlmModel = testLLModel,
                openAiPromptExecutorFactory = { apiKey ->
                    receivedApiKey = apiKey
                    fakeExecutor
                },
                ollamaPromptExecutorFactory = { error("Ollama should not be opened in the OpenAI branch.") },
            )

        val result = gateway.open(LocalLlmProvider.OPENAI)

        val ready = assertIs<PlatformLocalLlmGatewayResult.Ready>(result)
        assertEquals(LocalLlmProvider.OPENAI, ready.provider)
        assertEquals("sk-ready", receivedApiKey)
        assertSame(fakeExecutor, ready.promptExecutor)
        assertSame(testLLModel, ready.llmModel)
    }

    @Test
    fun `open returns configuration error for OpenAI without creating an executor when key is unavailable`() {
        val error =
            OpenAiConfigurationError(
                kind = OpenAiConfigurationErrorKind.INVALID_MODE,
                title = "Enable local direct mode",
                message = "Set the browser mode to local_direct.",
            )
        var openAiFactoryCalls = 0
        val gateway =
            PlatformLocalLlmGateway(
                openAiApiKeyProvider = FakeApiKeyProvider(ApiKeyProviderResult.Unavailable(error)),
                openAiLlmModel = testLLModel,
                ollamaLlmModel = testLLModel,
                openAiPromptExecutorFactory = { _ ->
                    openAiFactoryCalls += 1
                    FakePromptExecutor()
                },
                ollamaPromptExecutorFactory = { error("Ollama should not be opened in the OpenAI branch.") },
            )

        val result = gateway.open(LocalLlmProvider.OPENAI)

        val configurationError = assertIs<PlatformLocalLlmGatewayResult.ConfigurationError>(result)
        assertEquals(LocalLlmProvider.OPENAI, configurationError.provider)
        assertEquals(0, openAiFactoryCalls)
        assertEquals(error, configurationError.error)
    }

    @Test
    fun `open uses the Ollama branch without reading the OpenAI key`() {
        var providerCalls = 0
        var receivedBaseUrl: String? = null
        val fakeExecutor = FakePromptExecutor()
        val gateway =
            PlatformLocalLlmGateway(
                openAiApiKeyProvider =
                    object : ApiKeyProvider {
                        override fun resolve(): ApiKeyProviderResult {
                            providerCalls += 1
                            return ApiKeyProviderResult.Available("sk-should-not-be-read")
                        }
                    },
                openAiLlmModel = testLLModel,
                ollamaLlmModel = testLLModel,
                openAiPromptExecutorFactory = { error("OpenAI should not be opened in the Ollama branch.") },
                ollamaPromptExecutorFactory = { baseUrl ->
                    receivedBaseUrl = baseUrl
                    fakeExecutor
                },
            )

        val result = gateway.open(LocalLlmProvider.OLLAMA)

        val ready = assertIs<PlatformLocalLlmGatewayResult.Ready>(result)
        assertEquals(LocalLlmProvider.OLLAMA, ready.provider)
        assertEquals(0, providerCalls)
        assertEquals("http://localhost:11434", receivedBaseUrl)
        assertSame(fakeExecutor, ready.promptExecutor)
        assertSame(testLLModel, ready.llmModel)
    }

    private class FakeApiKeyProvider(
        private val result: ApiKeyProviderResult,
    ) : ApiKeyProvider {
        override fun resolve(): ApiKeyProviderResult = result
    }

    private class FakePromptExecutor : PromptExecutor {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> = error("Prompt execution is not used in this test.")

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = emptyFlow()

        override suspend fun moderate(
            prompt: Prompt,
            model: LLModel,
        ): ModerationResult = error("Moderation is not used in this test.")

        override fun close() = Unit
    }
}
