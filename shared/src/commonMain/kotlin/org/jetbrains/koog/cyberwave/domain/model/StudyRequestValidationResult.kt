package org.jetbrains.koog.cyberwave.domain.model

sealed interface StudyRequestValidationResult {
    data class Success(val request: ValidatedStudyRequest) : StudyRequestValidationResult

    data class Failure(
        val issues: List<ValidationIssue>,
        val normalizedTopics: List<String>,
    ) : StudyRequestValidationResult
}
