package org.jetbrains.koog.cyberwave.presentation.state

import org.jetbrains.koog.cyberwave.domain.model.QuizPayload
import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion

data class QuizSessionState(
    val quiz: QuizPayload,
    val currentQuestionIndex: Int = 0,
    val selectedOptionByQuestionId: Map<String, Int> = emptyMap(),
) {
    init {
        require(quiz.questions.isNotEmpty()) { "Quiz session requires at least one question." }
        require(currentQuestionIndex in quiz.questions.indices) {
            "Current question index must be within the quiz question bounds."
        }
    }

    val currentQuestion: QuizQuestion
        get() = quiz.questions[currentQuestionIndex]

    val isCurrentQuestionAnswered: Boolean
        get() = selectedOptionByQuestionId.containsKey(currentQuestion.id)

    val isLastQuestion: Boolean
        get() = currentQuestionIndex == quiz.questions.lastIndex
}
