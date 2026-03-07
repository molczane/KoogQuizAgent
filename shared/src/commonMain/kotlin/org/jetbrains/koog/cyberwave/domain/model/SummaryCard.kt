package org.jetbrains.koog.cyberwave.domain.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("SummaryCard")
@LLMDescription("A short study summary card derived from Wikipedia research.")
data class SummaryCard(
    @property:LLMDescription("Human-readable title for the summary card.")
    val title: String,
    @property:LLMDescription("Short bullet points explaining the topic.")
    val bullets: List<String>,
    @property:LLMDescription("Identifiers of the sources that support this summary card.")
    val sourceRefs: List<String>,
)
