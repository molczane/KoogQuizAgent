package org.jetbrains.koog.cyberwave.presentation.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.koog.cyberwave.domain.model.QuestionType
import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion

class QuizResultsStateTest {
    @Test
    fun `question result is correct only when selected option matches the correct answer`() {
        val correctResult = QuizQuestionResult(question = question(id = "q1", correctOptionIndex = 2), selectedOptionIndex = 2)
        val incorrectResult = QuizQuestionResult(question = question(id = "q2", correctOptionIndex = 1), selectedOptionIndex = 0)
        val unansweredResult = QuizQuestionResult(question = question(id = "q3", correctOptionIndex = 3), selectedOptionIndex = null)

        assertTrue(correctResult.isCorrect)
        assertFalse(incorrectResult.isCorrect)
        assertFalse(unansweredResult.isCorrect)
    }

    @Test
    fun `results count total answered and correct questions independently`() {
        val results =
            QuizResultsState(
                questionResults =
                    listOf(
                        QuizQuestionResult(question = question(id = "q1", correctOptionIndex = 0), selectedOptionIndex = 0),
                        QuizQuestionResult(question = question(id = "q2", correctOptionIndex = 1), selectedOptionIndex = 3),
                        QuizQuestionResult(question = question(id = "q3", correctOptionIndex = 2), selectedOptionIndex = null),
                    ),
            )

        assertEquals(3, results.totalQuestions)
        assertEquals(2, results.answeredQuestions)
        assertEquals(1, results.correctAnswers)
    }

    private fun question(
        id: String,
        correctOptionIndex: Int,
    ): QuizQuestion =
        QuizQuestion(
            id = id,
            type = QuestionType.SINGLE_CHOICE,
            prompt = "Question $id",
            options = listOf("A", "B", "C", "D"),
            correctOptionIndex = correctOptionIndex,
            explanation = "Explanation for $id",
            sourceRefs = listOf("src-$id"),
        )
}
