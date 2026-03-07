package org.jetbrains.koog.cyberwave.domain.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("QuizPayload")
@LLMDescription("Quiz data prepared for rendering by the app.")
data class QuizPayload(
    @property:LLMDescription("Maximum number of questions requested by the user.")
    val maxQuestions: Int,
    @property:LLMDescription("Single-choice questions prepared for the quiz.")
    val questions: List<QuizQuestion>,
)
