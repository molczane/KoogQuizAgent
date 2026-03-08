package org.jetbrains.koog.cyberwave.platform

import io.ktor.client.HttpClient

internal expect fun createPlatformHttpClient(): HttpClient
