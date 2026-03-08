package org.jetbrains.koog.cyberwave.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StudyGenerationState {
    @SerialName("ready")
    READY,

    @SerialName("insufficient_sources")
    INSUFFICIENT_SOURCES,

    @SerialName("configuration_error")
    CONFIGURATION_ERROR,

    @SerialName("generation_error")
    GENERATION_ERROR,

    @SerialName("validation_error")
    VALIDATION_ERROR,
}
