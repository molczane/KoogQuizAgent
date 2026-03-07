package org.jetbrains.koog.cyberwave.presentation.model

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.jetbrains.koog.cyberwave.domain.model.QuestionType
import org.jetbrains.koog.cyberwave.domain.model.QuizPayload
import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion
import org.jetbrains.koog.cyberwave.domain.model.ResearchSource
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.SummaryCard

class StudyScreenModelSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun serializesStableQuestionTypeAndStateValues() {
        val payload = StudyScreenModel(
            screenTitle = "Learn: Kotlin",
            topics = listOf("Kotlin"),
            summaryCards = listOf(
                SummaryCard(
                    title = "Overview",
                    bullets = listOf("Kotlin is a statically typed language."),
                    sourceRefs = listOf("src1"),
                ),
            ),
            quiz = QuizPayload(
                maxQuestions = 1,
                questions = listOf(
                    QuizQuestion(
                        id = "q1",
                        type = QuestionType.SINGLE_CHOICE,
                        prompt = "What kind of language is Kotlin?",
                        options = listOf("Statically typed", "Assembly", "Markup", "Query"),
                        correctOptionIndex = 0,
                        explanation = "Kotlin is a statically typed programming language.",
                        sourceRefs = listOf("src1"),
                    ),
                ),
            ),
            sources = listOf(
                ResearchSource(
                    id = "src1",
                    title = "Kotlin",
                    url = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                    snippet = "Wikipedia article about Kotlin.",
                ),
            ),
            state = StudyGenerationState.READY,
            primaryAction = PrimaryAction(
                id = PrimaryActionId.START_QUIZ,
                label = "Start the quiz",
            ),
        )

        val serialized = json.encodeToString(StudyScreenModel.serializer(), payload)

        assertTrue(serialized.contains("\"single_choice\""))
        assertTrue(serialized.contains("\"ready\""))
        assertTrue(serialized.contains("\"start_quiz\""))
    }
}
