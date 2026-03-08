package org.jetbrains.koog.cyberwave.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.koog.cyberwave.application.StudySessionUseCase
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiEvent
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiState
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiStateReducer

@Stable
class StudyAppState(
    initialState: StudyUiState = StudyUiState.initial(),
    private val studySessionUseCase: StudySessionUseCase,
    private val coroutineScope: CoroutineScope,
) {
    private var generationJob: Job? = null

    var uiState: StudyUiState by mutableStateOf(initialState)
        private set

    fun dispatch(event: StudyUiEvent) {
        val previousState = uiState
        uiState = StudyUiStateReducer.reduce(previousState, event)

        when (event) {
            StudyUiEvent.SubmitGeneration -> launchGenerationIfNeeded(previousState = previousState, nextState = uiState)
            is StudyUiEvent.GenerationCompleted -> generationJob = null
            else -> Unit
        }
    }

    private fun launchGenerationIfNeeded(
        previousState: StudyUiState,
        nextState: StudyUiState,
    ) {
        val loadingState = nextState as? StudyUiState.Loading ?: return
        if (previousState is StudyUiState.Loading) {
            return
        }

        generationJob?.cancel()
        generationJob =
            coroutineScope.launch {
                try {
                    val screenModel = studySessionUseCase.generate(loadingState.request.toInput())
                    uiState = StudyUiStateReducer.reduce(uiState, StudyUiEvent.GenerationCompleted(screenModel = screenModel))
                } catch (exception: CancellationException) {
                    throw exception
                } finally {
                    generationJob = null
                }
            }
    }

    private fun ValidatedStudyRequest.toInput(): StudyRequestInput =
        StudyRequestInput(
            topicsText = topics.joinToString(separator = "\n"),
            maxQuestions = maxQuestions,
            difficulty = difficulty,
            provider = provider,
            specificInstructions = specificInstructions,
        )
}

@Composable
fun rememberStudyAppState(
    initialState: StudyUiState = StudyUiState.initial(),
): StudyAppState {
    val coroutineScope = rememberCoroutineScope()
    val studySessionUseCase = rememberStudySessionUseCase()
    return remember(initialState, coroutineScope, studySessionUseCase) {
        StudyAppState(
            initialState = initialState,
            studySessionUseCase = studySessionUseCase,
            coroutineScope = coroutineScope,
        )
    }
}
