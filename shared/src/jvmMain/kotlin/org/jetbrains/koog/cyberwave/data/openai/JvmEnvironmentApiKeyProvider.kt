package org.jetbrains.koog.cyberwave.data.openai

class JvmEnvironmentApiKeyProvider(
    private val environment: (String) -> String? = System::getenv,
    private val variableName: String = DEFAULT_ENVIRONMENT_VARIABLE,
) : ApiKeyProvider {
    override fun resolve(): ApiKeyProviderResult {
        val apiKey = environment(variableName)?.trim().orEmpty()

        return if (apiKey.isNotEmpty()) {
            ApiKeyProviderResult.Available(apiKey = apiKey)
        } else {
            ApiKeyProviderResult.Unavailable(
                error =
                    OpenAiConfigurationError(
                        kind = OpenAiConfigurationErrorKind.MISSING_API_KEY,
                        title = "OpenAI API key is missing",
                        message =
                            "Set $variableName in your local shell or IDE run configuration before generating study content.",
                    ),
            )
        }
    }

    companion object {
        const val DEFAULT_ENVIRONMENT_VARIABLE: String = "OPENAI_API_KEY"
    }
}
