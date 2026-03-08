package org.jetbrains.koog.cyberwave.data.openai

actual fun createPlatformApiKeyProvider(): ApiKeyProvider = JvmEnvironmentApiKeyProvider()
