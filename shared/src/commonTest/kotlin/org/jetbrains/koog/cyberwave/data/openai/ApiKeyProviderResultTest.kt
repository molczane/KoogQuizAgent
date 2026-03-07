package org.jetbrains.koog.cyberwave.data.openai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiKeyProviderResultTest {
    @Test
    fun `available result rejects blank api keys`() {
        assertFailsWith<IllegalArgumentException> {
            ApiKeyProviderResult.Available(apiKey = "   ")
        }
    }

    @Test
    fun `configuration error rejects blank titles and messages`() {
        assertFailsWith<IllegalArgumentException> {
            OpenAiConfigurationError(
                kind = OpenAiConfigurationErrorKind.MISSING_API_KEY,
                title = " ",
                message = "Set the key.",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            OpenAiConfigurationError(
                kind = OpenAiConfigurationErrorKind.INVALID_MODE,
                title = "Wrong mode",
                message = " ",
            )
        }
    }

    @Test
    fun `unavailable result retains the original configuration error`() {
        val error =
            OpenAiConfigurationError(
                kind = OpenAiConfigurationErrorKind.INVALID_MODE,
                title = "Enable local direct mode",
                message = "Set the browser mode to local_direct.",
            )

        val result = ApiKeyProviderResult.Unavailable(error)

        assertEquals(error, result.error)
    }
}
