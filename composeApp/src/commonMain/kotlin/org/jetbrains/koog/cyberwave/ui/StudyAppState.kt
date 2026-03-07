package org.jetbrains.koog.cyberwave.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiEvent
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiState
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiStateReducer

@Stable
class StudyAppState(
    initialState: StudyUiState = StudyUiState.initial(),
) {
    var uiState: StudyUiState by mutableStateOf(initialState)
        private set

    fun dispatch(event: StudyUiEvent) {
        uiState = StudyUiStateReducer.reduce(uiState, event)
    }
}

@Composable
fun rememberStudyAppState(
    initialState: StudyUiState = StudyUiState.initial(),
): StudyAppState = remember(initialState) { StudyAppState(initialState) }
