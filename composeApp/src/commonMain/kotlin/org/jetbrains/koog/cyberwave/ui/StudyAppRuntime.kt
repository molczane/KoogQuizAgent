package org.jetbrains.koog.cyberwave.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.jetbrains.koog.cyberwave.application.StudySessionUseCase
import org.jetbrains.koog.cyberwave.data.openai.PlatformOpenAiGateway
import org.jetbrains.koog.cyberwave.data.openai.createPlatformApiKeyProvider
import org.jetbrains.koog.cyberwave.data.wikipedia.MediaWikiWikipediaClient

@Composable
internal fun rememberStudySessionUseCase(): StudySessionUseCase {
    val httpClient = remember { HttpClient(CIO) }

    DisposableEffect(httpClient) {
        onDispose {
            httpClient.close()
        }
    }

    return remember(httpClient) {
        StudySessionUseCase(
            wikipediaClient = MediaWikiWikipediaClient(httpClient = httpClient),
            openAiGateway = PlatformOpenAiGateway(apiKeyProvider = createPlatformApiKeyProvider()),
        )
    }
}
