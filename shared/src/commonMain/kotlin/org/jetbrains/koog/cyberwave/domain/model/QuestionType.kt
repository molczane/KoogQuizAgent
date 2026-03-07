package org.jetbrains.koog.cyberwave.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QuestionType {
    @SerialName("single_choice")
    SINGLE_CHOICE,
}
