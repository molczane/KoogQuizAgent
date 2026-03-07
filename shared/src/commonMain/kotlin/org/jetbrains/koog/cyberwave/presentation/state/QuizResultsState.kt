package org.jetbrains.koog.cyberwave.presentation.state

import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion

data class QuizQuestionResult(
    val question: QuizQuestion,
    val selectedOptionIndex: Int?,
) {
    val isCorrect: Boolean
        get() = selectedOptionIndex == question.correctOptionIndex
}

data class QuizResultsState(
    val questionResults: List<QuizQuestionResult>,
) {
    val totalQuestions: Int
        get() = questionResults.size

    val answeredQuestions: Int
        get() = questionResults.count { result -> result.selectedOptionIndex != null }

    val correctAnswers: Int
        get() = questionResults.count(QuizQuestionResult::isCorrect)
}
