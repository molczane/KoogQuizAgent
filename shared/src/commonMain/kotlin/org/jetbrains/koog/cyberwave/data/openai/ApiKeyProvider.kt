package org.jetbrains.koog.cyberwave.data.openai

interface ApiKeyProvider {
    fun resolve(): ApiKeyProviderResult
}

sealed interface ApiKeyProviderResult {
    data class Available(
        val apiKey: String,
    ) : ApiKeyProviderResult {
        init {
            require(apiKey.isNotBlank()) { "apiKey must not be blank." }
        }
    }

    data class Unavailable(
        val error: OpenAiConfigurationError,
    ) : ApiKeyProviderResult
}

enum class OpenAiConfigurationErrorKind {
    MISSING_API_KEY,
    INVALID_MODE,
}

data class OpenAiConfigurationError(
    val kind: OpenAiConfigurationErrorKind,
    val title: String,
    val message: String,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank." }
        require(message.isNotBlank()) { "message must not be blank." }
    }
}
