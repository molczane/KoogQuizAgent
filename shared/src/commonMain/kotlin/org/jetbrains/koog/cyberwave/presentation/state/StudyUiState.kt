package org.jetbrains.koog.cyberwave.presentation.state

import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

sealed interface StudyUiState {
    val form: StudyFormState

    data class Input(
        override val form: StudyFormState = StudyFormState(),
    ) : StudyUiState

    data class Loading(
        override val form: StudyFormState,
        val request: ValidatedStudyRequest,
    ) : StudyUiState

    data class Summary(
        override val form: StudyFormState,
        val screenModel: StudyScreenModel,
    ) : StudyUiState

    data class QuizInProgress(
        override val form: StudyFormState,
        val screenModel: StudyScreenModel,
        val session: QuizSessionState,
    ) : StudyUiState

    data class QuizResults(
        override val form: StudyFormState,
        val screenModel: StudyScreenModel,
        val results: QuizResultsState,
    ) : StudyUiState

    data class Failure(
        override val form: StudyFormState,
        val screenModel: StudyScreenModel,
    ) : StudyUiState

    companion object {
        fun initial(form: StudyFormState = StudyFormState()): StudyUiState = Input(form)
    }
}
