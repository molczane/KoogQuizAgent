package org.jetbrains.koog.cyberwave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.jetbrains.koog.cyberwave.ui.CyberWaveTheme
import org.jetbrains.koog.cyberwave.ui.StudyAppScreen
import org.jetbrains.koog.cyberwave.ui.rememberStudyAppState

@Composable
fun App() {
    CyberWaveTheme {
        val appState = rememberStudyAppState()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color(0xFFF3EADB),
                                    Color(0xFFE4F0EA),
                                    Color(0xFFD4E1D7),
                                ),
                        ),
                    ),
        ) {
            StudyAppScreen(
                uiState = appState.uiState,
                onEvent = appState::dispatch,
            )
        }
    }
}
