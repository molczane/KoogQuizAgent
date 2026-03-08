package org.jetbrains.koog.cyberwave.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(Js)
