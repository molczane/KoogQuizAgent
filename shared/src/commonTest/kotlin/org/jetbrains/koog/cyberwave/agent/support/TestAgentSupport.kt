package org.jetbrains.koog.cyberwave.agent.support

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.koog.cyberwave.agent.tool.FetchWikipediaArticleTool
import org.jetbrains.koog.cyberwave.agent.tool.SearchWikipediaTool
import org.jetbrains.koog.cyberwave.agent.workflow.StudyResearchWorkflow
import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest

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
    maxAgentIterations: Int = 32,
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

class ToolCallingSearchPromptExecutor(
    private val structuredResponseJson: String? = null,
    private val scriptedSearchTopics: List<String>? = null,
    private val scriptedFetchTitles: List<String>? = null,
    private val searchCompletionMessage: String = StudyResearchWorkflow.SEARCH_STAGE_COMPLETE,
    private val fetchCompletionMessage: String = StudyResearchWorkflow.FETCH_STAGE_COMPLETE,
) : PromptExecutor {
    var executeCalls: Int = 0
        private set

    var searchStageCalls: Int = 0
        private set

    var structuredPayloadCalls: Int = 0
        private set

    val emittedSearchToolTopics: MutableList<String> = mutableListOf()
    val emittedFetchToolTitles: MutableList<String> = mutableListOf()
    var fetchStageCalls: Int = 0
        private set
    private var activeTopics: List<String>? = null
    private var nextTopicIndex: Int = 0
    private var activeFetchTitles: List<String>? = null
    private var nextFetchIndex: Int = 0

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        executeCalls += 1

        return when {
            tools.any { descriptor -> descriptor.name == SearchWikipediaTool.NAME } -> {
                searchStageCalls += 1
                handleSearchStage(prompt)
            }

            tools.any { descriptor -> descriptor.name == FetchWikipediaArticleTool.NAME } -> {
                fetchStageCalls += 1
                handleFetchStage(prompt)
            }

            else -> {
                structuredPayloadCalls += 1
                listOf(
                    Message.Assistant(
                        structuredResponseJson ?: error("A structured payload response must be configured for this executor."),
                        ResponseMetaInfo.Empty,
                    ),
                )
            }
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = emptyFlow()

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = error("Moderation should not be used in this test.")

    override fun close() = Unit

    private fun handleSearchStage(prompt: Prompt): List<Message.Response> {
        val request = extractSearchStageRequest(prompt)
        if (activeTopics != request.topics) {
            activeTopics = request.topics
            nextTopicIndex = 0
        }

        val searchPlan = scriptedSearchTopics ?: request.topics
        return if (nextTopicIndex < searchPlan.size) {
            val nextTopic = searchPlan[nextTopicIndex]
            nextTopicIndex += 1
            emittedSearchToolTopics += nextTopic
            listOf(
                Message.Tool.Call(
                    id = "search-${emittedSearchToolTopics.size}",
                    tool = SearchWikipediaTool.NAME,
                    content = json.encodeToString(SearchWikipediaTool.Args(topic = nextTopic)),
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )
        } else {
            activeTopics = null
            nextTopicIndex = 0
            listOf(Message.Assistant(searchCompletionMessage, ResponseMetaInfo.Empty))
        }
    }

    private fun handleFetchStage(prompt: Prompt): List<Message.Response> {
        val requestedTitles = extractFetchStageTitles(prompt)
        if (activeFetchTitles != requestedTitles) {
            activeFetchTitles = requestedTitles
            nextFetchIndex = 0
        }

        val fetchPlan = scriptedFetchTitles ?: requestedTitles
        return if (nextFetchIndex < fetchPlan.size) {
            val nextTitle = fetchPlan[nextFetchIndex]
            nextFetchIndex += 1
            emittedFetchToolTitles += nextTitle
            listOf(
                Message.Tool.Call(
                    id = "fetch-${emittedFetchToolTitles.size}",
                    tool = FetchWikipediaArticleTool.NAME,
                    content = json.encodeToString(FetchWikipediaArticleTool.Args(title = nextTitle)),
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )
        } else {
            activeFetchTitles = null
            nextFetchIndex = 0
            listOf(Message.Assistant(fetchCompletionMessage, ResponseMetaInfo.Empty))
        }
    }

    private fun extractSearchStageRequest(prompt: Prompt): ValidatedStudyRequest {
        val payload =
            prompt.messages
                .asReversed()
                .asSequence()
                .filterIsInstance<Message.User>()
                .mapNotNull { message ->
                    message.content
                        .lineSequence()
                        .firstOrNull { line -> line.startsWith(StudyResearchWorkflow.SEARCH_STAGE_REQUEST_PREFIX) }
                        ?.removePrefix(StudyResearchWorkflow.SEARCH_STAGE_REQUEST_PREFIX)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                }.firstOrNull()
                ?: error("Search stage request payload was not found in the prompt.")

        return json.decodeFromString(ValidatedStudyRequest.serializer(), payload)
    }

    private fun extractFetchStageTitles(prompt: Prompt): List<String> {
        val payload =
            prompt.messages
                .asReversed()
                .asSequence()
                .filterIsInstance<Message.User>()
                .mapNotNull { message ->
                    message.content
                        .lineSequence()
                        .firstOrNull { line -> line.startsWith(StudyResearchWorkflow.FETCH_STAGE_REQUEST_PREFIX) }
                        ?.removePrefix(StudyResearchWorkflow.FETCH_STAGE_REQUEST_PREFIX)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                }.firstOrNull()
                ?: error("Fetch stage request payload was not found in the prompt.")

        val selectedArticles =
            json.parseToJsonElement(payload)
                .jsonObject
                .getValue("selectedArticles")
                .jsonArray

        return buildList {
            selectedArticles.forEach { selection ->
                selection
                    .jsonObject
                    .getValue("articles")
                    .jsonArray
                    .forEach { article ->
                        add(article.jsonObject.getValue("title").jsonPrimitive.content)
                    }
            }
        }.distinctBy(String::lowercase)
    }

    private companion object {
        private val json: Json = Json { ignoreUnknownKeys = true }
    }
}
