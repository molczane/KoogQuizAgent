package org.jetbrains.koog.cyberwave

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform