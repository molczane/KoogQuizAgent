package org.jetbrains.koog.cyberwave.data.openai

class BrowserLocalDirectApiKeyProvider(
    private val readSetting: (String) -> String?,
) : ApiKeyProvider {
    override fun resolve(): ApiKeyProviderResult {
        val mode = readSetting(STORAGE_KEY_MODE)?.trim().orEmpty()
        if (mode != MODE_LOCAL_DIRECT) {
            return ApiKeyProviderResult.Unavailable(
                error =
                    OpenAiConfigurationError(
                        kind = OpenAiConfigurationErrorKind.INVALID_MODE,
                        title = "Enable local direct mode",
                        message =
                            "Set browser localStorage key \"$STORAGE_KEY_MODE\" to \"$MODE_LOCAL_DIRECT\" " +
                                "for local-only Wasm testing.",
                    ),
            )
        }

        val apiKey = readSetting(STORAGE_KEY_API_KEY)?.trim().orEmpty()
        return if (apiKey.isNotEmpty()) {
            ApiKeyProviderResult.Available(apiKey = apiKey)
        } else {
            ApiKeyProviderResult.Unavailable(
                error =
                    OpenAiConfigurationError(
                        kind = OpenAiConfigurationErrorKind.MISSING_API_KEY,
                        title = "OpenAI API key is missing",
                        message =
                            "Set browser localStorage key \"$STORAGE_KEY_API_KEY\" before generating study content in local Wasm mode.",
                    ),
            )
        }
    }

    companion object {
        const val MODE_LOCAL_DIRECT: String = "local_direct"
        const val STORAGE_KEY_MODE: String = "cyberwave.openai.mode"
        const val STORAGE_KEY_API_KEY: String = "cyberwave.openai.apiKey"
    }
}
