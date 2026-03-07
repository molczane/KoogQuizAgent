package org.jetbrains.koog.cyberwave.data.openai

import kotlinx.browser.localStorage

class WasmBrowserApiKeyProvider(
    private val readSetting: (String) -> String? = { key -> localStorage.getItem(key) },
) : ApiKeyProvider by BrowserLocalDirectApiKeyProvider(readSetting)
