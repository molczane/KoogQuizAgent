package org.jetbrains.koog.cyberwave.data.openai

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BrowserLocalDirectApiKeyProviderTest {
    @Test
    fun `resolve returns invalid mode when local direct mode is not enabled`() {
        val provider = BrowserLocalDirectApiKeyProvider(readSetting = { null })

        val result = provider.resolve()

        val unavailable = assertIs<ApiKeyProviderResult.Unavailable>(result)
        assertEquals(OpenAiConfigurationErrorKind.INVALID_MODE, unavailable.error.kind)
        assertContains(unavailable.error.message, BrowserLocalDirectApiKeyProvider.STORAGE_KEY_MODE)
        assertContains(unavailable.error.message, BrowserLocalDirectApiKeyProvider.MODE_LOCAL_DIRECT)
    }

    @Test
    fun `resolve returns missing key when mode is enabled but api key is absent`() {
        val settings =
            mapOf(
                BrowserLocalDirectApiKeyProvider.STORAGE_KEY_MODE to BrowserLocalDirectApiKeyProvider.MODE_LOCAL_DIRECT,
            )
        val provider = BrowserLocalDirectApiKeyProvider(readSetting = settings::get)

        val result = provider.resolve()

        val unavailable = assertIs<ApiKeyProviderResult.Unavailable>(result)
        assertEquals(OpenAiConfigurationErrorKind.MISSING_API_KEY, unavailable.error.kind)
        assertContains(unavailable.error.message, BrowserLocalDirectApiKeyProvider.STORAGE_KEY_API_KEY)
    }

    @Test
    fun `resolve returns available when mode and api key are both present`() {
        val settings =
            mapOf(
                BrowserLocalDirectApiKeyProvider.STORAGE_KEY_MODE to "  local_direct  ",
                BrowserLocalDirectApiKeyProvider.STORAGE_KEY_API_KEY to "  sk-browser  ",
            )
        val provider = BrowserLocalDirectApiKeyProvider(readSetting = settings::get)

        val result = provider.resolve()

        val available = assertIs<ApiKeyProviderResult.Available>(result)
        assertEquals("sk-browser", available.apiKey)
    }
}
