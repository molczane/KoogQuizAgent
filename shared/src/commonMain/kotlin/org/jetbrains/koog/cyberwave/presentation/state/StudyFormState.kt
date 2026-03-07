package org.jetbrains.koog.cyberwave.presentation.state

import org.jetbrains.koog.cyberwave.application.StudyRequestConstraints
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest
import org.jetbrains.koog.cyberwave.domain.model.ValidationIssue

data class StudyFormState(
    val topicsText: String = "",
    val maxQuestions: Int = StudyRequestConstraints.MIN_QUESTIONS,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val specificInstructions: String = "",
    val validationIssues: List<ValidationIssue> = emptyList(),
) {
    fun issuesFor(field: String): List<ValidationIssue> = validationIssues.filter { issue -> issue.field == field }

    internal fun clearIssuesFor(field: String): StudyFormState =
        copy(validationIssues = validationIssues.filterNot { issue -> issue.field == field })

    internal fun clearAllIssues(): StudyFormState = copy(validationIssues = emptyList())

    internal fun normalizedFrom(request: ValidatedStudyRequest): StudyFormState =
        copy(
            topicsText = request.topics.joinToString(separator = "\n"),
            maxQuestions = request.maxQuestions,
            difficulty = request.difficulty,
            specificInstructions = request.specificInstructions.orEmpty(),
            validationIssues = emptyList(),
        )
}
