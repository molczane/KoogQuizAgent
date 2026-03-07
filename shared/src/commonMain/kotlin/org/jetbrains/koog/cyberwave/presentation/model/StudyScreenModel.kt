package org.jetbrains.koog.cyberwave.presentation.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.koog.cyberwave.domain.model.QuizPayload
import org.jetbrains.koog.cyberwave.domain.model.ResearchSource
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.SummaryCard

@Serializable
@SerialName("StudyScreenModel")
@LLMDescription("Structured payload rendered by the learning app after the agent finishes its workflow.")
data class StudyScreenModel(
    @property:LLMDescription("Title shown at the top of the generated screen.")
    val screenTitle: String,
    @property:LLMDescription("Normalized topics used for research and quiz generation.")
    val topics: List<String>,
    @property:LLMDescription("Summary cards shown before the quiz starts.")
    val summaryCards: List<SummaryCard> = emptyList(),
    @property:LLMDescription("Quiz payload to render when the user starts the quiz.")
    val quiz: QuizPayload? = null,
    @property:LLMDescription("Sources supporting summaries and quiz questions.")
    val sources: List<ResearchSource> = emptyList(),
    @property:LLMDescription("Outcome state for the generated screen.")
    val state: StudyGenerationState,
    @property:LLMDescription("Primary action available to the user, if any.")
    val primaryAction: PrimaryAction? = null,
    @property:LLMDescription("Error banner content for failure states, if any.")
    val error: StudyScreenError? = null,
)
