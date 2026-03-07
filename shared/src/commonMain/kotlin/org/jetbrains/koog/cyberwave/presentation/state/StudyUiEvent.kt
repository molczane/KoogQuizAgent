package org.jetbrains.koog.cyberwave.presentation.state

import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

sealed interface StudyUiEvent {
    data class TopicsTextChanged(val topicsText: String) : StudyUiEvent

    data class MaxQuestionsChanged(val maxQuestions: Int) : StudyUiEvent

    data class DifficultyChanged(val difficulty: Difficulty) : StudyUiEvent

    data class SpecificInstructionsChanged(val specificInstructions: String) : StudyUiEvent

    data object SubmitGeneration : StudyUiEvent

    data class GenerationCompleted(val screenModel: StudyScreenModel) : StudyUiEvent

    data object StartQuiz : StudyUiEvent

    data class AnswerSelected(val optionIndex: Int) : StudyUiEvent

    data object AdvanceQuiz : StudyUiEvent

    data object RestartQuiz : StudyUiEvent

    data object ReturnToInput : StudyUiEvent
}
