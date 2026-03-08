package org.jetbrains.koog.cyberwave.agent.workflow

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import org.jetbrains.koog.cyberwave.agent.generation.StructuredStudyPayloadGenerator
import org.jetbrains.koog.cyberwave.agent.tool.wikipediaToolRegistry
import org.jetbrains.koog.cyberwave.data.wikipedia.WikipediaClient
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.observability.NoOpStudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.StudyWorkflowTracer
import org.jetbrains.koog.cyberwave.observability.traceSpan
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryAction
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

class StudyGenerationService(
    private val promptExecutor: PromptExecutor,
    private val llmModel: LLModel,
    wikipediaClient: WikipediaClient,
    private val tracer: StudyWorkflowTracer = NoOpStudyWorkflowTracer,
) {
    private val researchStrategy = StudyResearchWorkflow.strategy(wikipediaClient, tracer)
    private val toolRegistry = wikipediaToolRegistry(wikipediaClient)
    private val payloadGenerator =
        StructuredStudyPayloadGenerator(
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            tracer = tracer,
        )

    suspend fun generate(input: StudyRequestInput): StudyScreenModel =
        tracer.traceSpan(
            name = "study_generation.service.generate",
            attributes =
                mapOf(
                    "requested_question_count" to input.maxQuestions.toString(),
                ),
            successAttributes = { result ->
                mapOf(
                    "result_state" to result.state.name.lowercase(),
                    "result_topic_count" to result.topics.size.toString(),
                )
            },
        ) {
            val researchResult = runResearchWorkflow(input)

            when (researchResult) {
                is StudyResearchWorkflowResult.ReadyForGeneration -> payloadGenerator.generate(researchResult.snapshot)
                is StudyResearchWorkflowResult.ValidationFailed -> validationErrorModel(researchResult)
                is StudyResearchWorkflowResult.InsufficientSources -> insufficientSourcesModel(researchResult.snapshot)
            }
        }

    private suspend fun runResearchWorkflow(input: StudyRequestInput): StudyResearchWorkflowResult =
        tracer.traceSpan(
            name = "study_generation.service.run_research_workflow",
            attributes =
                mapOf(
                    "requested_question_count" to input.maxQuestions.toString(),
                ),
            successAttributes = { result ->
                when (result) {
                    is StudyResearchWorkflowResult.ReadyForGeneration ->
                        mapOf(
                            "workflow_outcome" to "ready_for_generation",
                            "usable_source_count" to result.snapshot.usableSources.size.toString(),
                        )

                    is StudyResearchWorkflowResult.InsufficientSources ->
                        mapOf(
                            "workflow_outcome" to "insufficient_sources",
                            "usable_source_count" to result.snapshot.usableSources.size.toString(),
                        )

                    is StudyResearchWorkflowResult.ValidationFailed ->
                        mapOf(
                            "workflow_outcome" to "validation_failed",
                            "issue_count" to result.issues.size.toString(),
                        )
                }
            },
        ) {
            val agent =
                AIAgent(
                    promptExecutor = promptExecutor,
                    agentConfig =
                        AIAgentConfig.withSystemPrompt(
                            prompt = RESEARCH_SYSTEM_PROMPT,
                            llm = llmModel,
                            id = "study-research-workflow",
                            maxAgentIterations = RESEARCH_MAX_ITERATIONS,
                        ),
                    strategy = researchStrategy,
                    toolRegistry = toolRegistry,
                )

            try {
                agent.run(input)
            } finally {
                agent.close()
            }
        }

    private fun validationErrorModel(
        result: StudyResearchWorkflowResult.ValidationFailed,
    ): StudyScreenModel =
        StudyScreenModel(
            screenTitle = VALIDATION_SCREEN_TITLE,
            topics = result.normalizedTopics,
            state = StudyGenerationState.VALIDATION_ERROR,
            primaryAction = retryAction(),
            error =
                StudyScreenError(
                    title = VALIDATION_ERROR_TITLE,
                    message = VALIDATION_ERROR_MESSAGE,
                    validationIssues = result.issues,
                ),
        )

    private fun insufficientSourcesModel(
        snapshot: StudyResearchSnapshot,
    ): StudyScreenModel =
        StudyScreenModel(
            screenTitle = INSUFFICIENT_SCREEN_TITLE,
            topics = snapshot.request.topics,
            sources = snapshot.usableSources,
            state = StudyGenerationState.INSUFFICIENT_SOURCES,
            primaryAction = retryAction(),
            error =
                StudyScreenError(
                    title = INSUFFICIENT_ERROR_TITLE,
                    message = insufficientSourcesMessage(snapshot),
                ),
        )

    private fun insufficientSourcesMessage(snapshot: StudyResearchSnapshot): String {
        val evidence = snapshot.evidence
        val missingTopicsPart =
            if (evidence.missingTopics.isNotEmpty()) {
                "I could not find enough reliable Wikipedia evidence for: ${evidence.missingTopics.joinToString(separator = ", ")}."
            } else {
                "I could not support ${snapshot.request.maxQuestions} questions from the available Wikipedia evidence."
            }

        val recommendation =
            when {
                evidence.recommendedQuestionCount > 0 ->
                    "Try reducing the quiz to ${evidence.recommendedQuestionCount} questions or narrowing the topics."

                else ->
                    "Try using more specific topics or simplifying the request."
            }

        return "$missingTopicsPart $recommendation"
    }

    private fun retryAction(): PrimaryAction =
        PrimaryAction(
            id = PrimaryActionId.RETRY,
            label = RETRY_LABEL,
        )

    private companion object {
        private const val RESEARCH_MAX_ITERATIONS: Int = 32
        private const val RETRY_LABEL: String = "Retry"
        private const val VALIDATION_SCREEN_TITLE: String = "Fix the study request"
        private const val VALIDATION_ERROR_TITLE: String = "Your input needs adjustment"
        private const val VALIDATION_ERROR_MESSAGE: String =
            "Provide at least one valid topic and a supported question count before generating the quiz."
        private const val INSUFFICIENT_SCREEN_TITLE: String = "Not enough evidence yet"
        private const val INSUFFICIENT_ERROR_TITLE: String = "Wikipedia evidence is too limited"
        private val RESEARCH_SYSTEM_PROMPT =
            """
            You execute a deterministic Wikipedia research workflow for a learning app.
            Follow the graph order, use only the tools exposed by the current stage, and never skip validation or evidence checks.
            """.trimIndent()
    }
}
