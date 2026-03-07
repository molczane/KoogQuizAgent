package org.jetbrains.koog.cyberwave.presentation.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PrimaryActionId {
    @SerialName("start_quiz")
    START_QUIZ,

    @SerialName("retry")
    RETRY,
}

@Serializable
@SerialName("PrimaryAction")
@LLMDescription("Primary action surfaced by the app for the current screen.")
data class PrimaryAction(
    @property:LLMDescription("Stable action identifier used by the UI.")
    val id: PrimaryActionId,
    @property:LLMDescription("Label shown on the primary button.")
    val label: String,
)
