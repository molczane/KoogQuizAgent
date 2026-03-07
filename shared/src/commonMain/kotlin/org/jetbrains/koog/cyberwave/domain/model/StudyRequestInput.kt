package org.jetbrains.koog.cyberwave.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class StudyRequestInput(
    val topicsText: String,
    val maxQuestions: Int,
    val difficulty: Difficulty,
    val specificInstructions: String? = null,
)
