package org.jetbrains.koog.cyberwave.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ValidationIssue(
    val field: String,
    val message: String,
)
