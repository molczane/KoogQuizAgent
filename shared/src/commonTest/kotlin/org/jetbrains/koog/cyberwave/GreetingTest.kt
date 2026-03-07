package org.jetbrains.koog.cyberwave

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {

    @Test
    fun greetIncludesHelloPrefix() {
        assertTrue(Greeting().greet().startsWith("Hello, "))
    }
}
