package org.jetbrains.koog.cyberwave.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(CIO)
