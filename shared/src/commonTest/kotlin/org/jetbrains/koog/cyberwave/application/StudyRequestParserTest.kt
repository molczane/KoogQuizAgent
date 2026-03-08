package org.jetbrains.koog.cyberwave.application

import kotlin.test.Test
import kotlin.test.assertContains
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
    fun validateRejectsRequestsWithTooManyTopics() {
        val result =
            StudyRequestParser.validate(
                StudyRequestInput(
                    topicsText = "Kotlin, Coroutines, Compose, Ktor, Wasm, Koog",
                    maxQuestions = 5,
                    difficulty = Difficulty.MEDIUM,
                ),
            )

        val failure = assertIs<StudyRequestValidationResult.Failure>(result)
        assertEquals(listOf("topicsText"), failure.issues.map { issue -> issue.field })
        assertContains(failure.issues.single().message, "at most")
    }

    @Test
    fun validateRejectsTopicsLongerThanConfiguredLimit() {
        val overlongTopic = "a".repeat(StudyRequestConstraints.MAX_TOPIC_LENGTH + 1)

        val result =
            StudyRequestParser.validate(
                StudyRequestInput(
                    topicsText = overlongTopic,
                    maxQuestions = 4,
                    difficulty = Difficulty.HARD,
                ),
            )

        val failure = assertIs<StudyRequestValidationResult.Failure>(result)
        assertEquals("topicsText", failure.issues.single().field)
        assertContains(failure.issues.single().message, "exceeds ${StudyRequestConstraints.MAX_TOPIC_LENGTH} characters")
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

    @Test
    fun validateRejectsSpecificInstructionsThatExceedTheConfiguredLimit() {
        val result =
            StudyRequestParser.validate(
                StudyRequestInput(
                    topicsText = "Kotlin",
                    maxQuestions = 4,
                    difficulty = Difficulty.HARD,
                    specificInstructions = "x".repeat(StudyRequestConstraints.MAX_SPECIFIC_INSTRUCTIONS_LENGTH + 1),
                ),
            )

        val failure = assertIs<StudyRequestValidationResult.Failure>(result)
        assertEquals("specificInstructions", failure.issues.single().field)
        assertContains(
            failure.issues.single().message,
            StudyRequestConstraints.MAX_SPECIFIC_INSTRUCTIONS_LENGTH.toString(),
        )
    }
}
