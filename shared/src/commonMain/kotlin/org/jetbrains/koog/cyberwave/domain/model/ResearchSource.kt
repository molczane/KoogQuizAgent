package org.jetbrains.koog.cyberwave.domain.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ResearchSource")
@LLMDescription("A Wikipedia source used to generate study notes and quiz questions.")
data class ResearchSource(
    @property:LLMDescription("Stable identifier used by summary cards and questions to reference this source.")
    val id: String,
    @property:LLMDescription("Wikipedia article title.")
    val title: String,
    @property:LLMDescription("Canonical Wikipedia URL for the article.")
    val url: String,
    @property:LLMDescription("Short snippet or summary describing why this source was selected.")
    val snippet: String,
)
