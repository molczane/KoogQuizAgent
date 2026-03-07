package org.jetbrains.koog.cyberwave.domain.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("QuizQuestion")
@LLMDescription("A single quiz question rendered by the app.")
data class QuizQuestion(
    @property:LLMDescription("Stable question identifier.")
    val id: String,
    @property:LLMDescription("Question type. In v1 this must always be single_choice.")
    val type: QuestionType,
    @property:LLMDescription("Question prompt shown to the user.")
    val prompt: String,
    @property:LLMDescription("Answer options presented to the user.")
    val options: List<String>,
    @property:LLMDescription("Zero-based index of the correct option.")
    val correctOptionIndex: Int,
    @property:LLMDescription("Short explanation tied to the supporting source material.")
    val explanation: String,
    @property:LLMDescription("Identifiers of the sources that support the answer.")
    val sourceRefs: List<String>,
)
