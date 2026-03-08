package org.jetbrains.koog.cyberwave.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LocalLlmProvider {
    OPENAI,
    OLLAMA,
}

object LocalLlmDefaults {
    const val OLLAMA_BASE_URL: String = "http://localhost:11434"
    const val OLLAMA_MODEL_NAME: String = "llama3.2"
}
