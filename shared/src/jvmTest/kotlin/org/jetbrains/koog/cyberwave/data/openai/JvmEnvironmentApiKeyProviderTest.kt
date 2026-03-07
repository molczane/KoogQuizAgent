package org.jetbrains.koog.cyberwave.data.openai

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JvmEnvironmentApiKeyProviderTest {
    @Test
    fun `resolve returns trimmed api key when environment variable is present`() {
        val provider =
            JvmEnvironmentApiKeyProvider(
                environment = { key ->
                    if (key == "OPENAI_API_KEY") {
                        "  sk-test-123  "
                    } else {
                        null
                    }
                },
            )

        val result = provider.resolve()

        val available = assertIs<ApiKeyProviderResult.Available>(result)
        assertEquals("sk-test-123", available.apiKey)
    }

    @Test
    fun `resolve returns missing key error when environment variable is absent`() {
        val provider = JvmEnvironmentApiKeyProvider(environment = { null })

        val result = provider.resolve()

        val unavailable = assertIs<ApiKeyProviderResult.Unavailable>(result)
        assertEquals(OpenAiConfigurationErrorKind.MISSING_API_KEY, unavailable.error.kind)
        assertContains(unavailable.error.message, "OPENAI_API_KEY")
    }

    @Test
    fun `resolve returns missing key error when environment variable is blank`() {
        val provider =
            JvmEnvironmentApiKeyProvider(
                environment = { "   " },
                variableName = "CUSTOM_OPENAI_API_KEY",
            )

        val result = provider.resolve()

        val unavailable = assertIs<ApiKeyProviderResult.Unavailable>(result)
        assertContains(unavailable.error.message, "CUSTOM_OPENAI_API_KEY")
    }
}
