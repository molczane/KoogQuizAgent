package org.jetbrains.koog.cyberwave.agent.support

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

val testLLModel: LLModel =
    LLModel(
        provider = LLMProvider.OpenAI,
        id = "test-model",
        capabilities =
            listOf(
                LLMCapability.Completion,
                LLMCapability.Schema.JSON.Basic,
            ),
        contextLength = 16_384,
        maxOutputTokens = 1_024,
    )

fun testAgentConfig(
    systemPrompt: String = "Test system prompt.",
    maxAgentIterations: Int = 8,
): AIAgentConfig =
    AIAgentConfig.withSystemPrompt(
        prompt = systemPrompt,
        llm = testLLModel,
        id = "koog-cyberwave-tests",
        maxAgentIterations = maxAgentIterations,
    )

object UnusedPromptExecutor : PromptExecutor {
    override suspend fun execute(
        prompt: ai.koog.prompt.dsl.Prompt,
        model: LLModel,
        tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
    ): List<Message.Response> = error("Prompt execution should not be used in this test.")

    override fun executeStreaming(
        prompt: ai.koog.prompt.dsl.Prompt,
        model: LLModel,
        tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
    ): Flow<StreamFrame> = emptyFlow()

    override suspend fun moderate(
        prompt: ai.koog.prompt.dsl.Prompt,
        model: LLModel,
    ): ModerationResult = error("Moderation should not be used in this test.")

    override fun close() = Unit
}
