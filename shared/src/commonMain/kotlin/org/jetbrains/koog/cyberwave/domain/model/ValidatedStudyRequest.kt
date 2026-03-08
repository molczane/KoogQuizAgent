package org.jetbrains.koog.cyberwave.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ValidatedStudyRequest(
    val topics: List<String>,
    val maxQuestions: Int,
    val difficulty: Difficulty,
    val provider: LocalLlmProvider = LocalLlmProvider.OPENAI,
    val specificInstructions: String? = null,
)
