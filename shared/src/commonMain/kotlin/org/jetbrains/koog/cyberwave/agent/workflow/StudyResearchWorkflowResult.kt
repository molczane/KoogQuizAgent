package org.jetbrains.koog.cyberwave.agent.workflow

import kotlinx.serialization.Serializable
import org.jetbrains.koog.cyberwave.application.research.SelectedWikipediaArticle
import org.jetbrains.koog.cyberwave.application.research.TopicResearchMaterial
import org.jetbrains.koog.cyberwave.application.research.WikipediaEvidenceAssessment
import org.jetbrains.koog.cyberwave.data.wikipedia.model.WikipediaSearchResult
import org.jetbrains.koog.cyberwave.domain.model.ResearchSource
import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest
import org.jetbrains.koog.cyberwave.domain.model.ValidationIssue

@Serializable
data class TopicWikipediaSearchResults(
    val topic: String,
    val results: List<WikipediaSearchResult>,
)

@Serializable
data class TopicWikipediaSelections(
    val topic: String,
    val articles: List<SelectedWikipediaArticle>,
)

@Serializable
data class SearchStageMetadata(
    val toolCallCount: Int,
    val completionMessage: String,
)

@Serializable
data class FetchStageMetadata(
    val toolCallCount: Int,
    val completionMessage: String,
)

data class StudyResearchSnapshot(
    val request: ValidatedStudyRequest,
    val searchStageMetadata: SearchStageMetadata,
    val fetchStageMetadata: FetchStageMetadata,
    val searchResults: List<TopicWikipediaSearchResults>,
    val selectedArticles: List<TopicWikipediaSelections>,
    val materials: List<TopicResearchMaterial>,
    val evidence: WikipediaEvidenceAssessment,
) {
    val effectiveQuestionCount: Int
        get() = evidence.recommendedQuestionCount

    val usableSources: List<ResearchSource>
        get() = evidence.usableSources
}

sealed interface StudyResearchWorkflowResult {
    data class ReadyForGeneration(
        val snapshot: StudyResearchSnapshot,
    ) : StudyResearchWorkflowResult

    data class InsufficientSources(
        val snapshot: StudyResearchSnapshot,
    ) : StudyResearchWorkflowResult

    data class ValidationFailed(
        val issues: List<ValidationIssue>,
        val normalizedTopics: List<String>,
    ) : StudyResearchWorkflowResult
}
