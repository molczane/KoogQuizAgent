package org.jetbrains.koog.cyberwave.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestValidationResult

class StudyRequestParserTest {

    @Test
    fun parseTopicsSplitsAndDeduplicates() {
        val topics = StudyRequestParser.parseTopics(
            " Kotlin ; Coroutines\nCompose Multiplatform, kotlin ",
        )

        assertEquals(
            listOf("Kotlin", "Coroutines", "Compose Multiplatform"),
            topics,
        )
    }

    @Test
    fun validateReturnsFailureWhenTopicsMissing() {
        val result = StudyRequestParser.validate(
            StudyRequestInput(
                topicsText = "   ",
                maxQuestions = 5,
                difficulty = Difficulty.MEDIUM,
            ),
        )

        val failure = assertIs<StudyRequestValidationResult.Failure>(result)
        assertEquals("topicsText", failure.issues.single().field)
    }

    @Test
    fun validateRejectsQuestionCountOutsideRange() {
        val result = StudyRequestParser.validate(
            StudyRequestInput(
                topicsText = "Kotlin",
                maxQuestions = 0,
                difficulty = Difficulty.EASY,
            ),
        )

        val failure = assertIs<StudyRequestValidationResult.Failure>(result)
        assertEquals("maxQuestions", failure.issues.single().field)
    }

    @Test
    fun validateNormalizesBlankInstructionsToNull() {
        val result = StudyRequestParser.validate(
            StudyRequestInput(
                topicsText = "Kotlin",
                maxQuestions = 4,
                difficulty = Difficulty.HARD,
                specificInstructions = "   ",
            ),
        )

        val success = assertIs<StudyRequestValidationResult.Success>(result)
        assertNull(success.request.specificInstructions)
    }
}
