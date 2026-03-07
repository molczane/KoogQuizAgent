package org.jetbrains.koog.cyberwave.presentation.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.koog.cyberwave.domain.model.ValidationIssue

@Serializable
@SerialName("StudyScreenError")
@LLMDescription("Error information rendered when generation cannot continue.")
data class StudyScreenError(
    @property:LLMDescription("Short error title shown in the UI.")
    val title: String,
    @property:LLMDescription("Human-readable explanation of the error.")
    val message: String,
    @property:LLMDescription("Optional field-level validation issues for the input form.")
    val validationIssues: List<ValidationIssue> = emptyList(),
)
