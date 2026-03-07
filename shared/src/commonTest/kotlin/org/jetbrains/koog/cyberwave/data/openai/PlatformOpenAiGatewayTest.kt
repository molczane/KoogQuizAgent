package org.jetbrains.koog.cyberwave.data.openai

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

class PlatformOpenAiGatewayTest {
    @Test
    fun `open builds prompt executor when provider returns an api key`() {
        var receivedApiKey: String? = null
        val fakeExecutor = FakePromptExecutor()
        val gateway =
            PlatformOpenAiGateway(
                apiKeyProvider =
                    FakeApiKeyProvider(
                        ApiKeyProviderResult.Available(apiKey = "sk-ready"),
                    ),
                llmModel = testLLModel,
                promptExecutorFactory = { apiKey ->
                    receivedApiKey = apiKey
                    fakeExecutor
                },
            )

        val result = gateway.open()

        val ready = assertIs<PlatformOpenAiGatewayResult.Ready>(result)
        assertEquals("sk-ready", receivedApiKey)
        assertSame(fakeExecutor, ready.promptExecutor)
        assertSame(testLLModel, ready.llmModel)
    }

    @Test
    fun `open returns configuration error without creating executor when provider is unavailable`() {
        val error =
            OpenAiConfigurationError(
                kind = OpenAiConfigurationErrorKind.INVALID_MODE,
                title = "Enable local direct mode",
                message = "Set the browser mode to local_direct.",
            )
        var factoryCalls = 0
        val gateway =
            PlatformOpenAiGateway(
                apiKeyProvider = FakeApiKeyProvider(ApiKeyProviderResult.Unavailable(error)),
                llmModel = testLLModel,
                promptExecutorFactory = { _ ->
                    factoryCalls += 1
                    FakePromptExecutor()
                },
            )

        val result = gateway.open()

        val configurationError = assertIs<PlatformOpenAiGatewayResult.ConfigurationError>(result)
        assertEquals(0, factoryCalls)
        assertEquals(error, configurationError.error)
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
