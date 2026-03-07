package org.jetbrains.koog.cyberwave.application

import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestValidationResult
import org.jetbrains.koog.cyberwave.domain.model.ValidatedStudyRequest
import org.jetbrains.koog.cyberwave.domain.model.ValidationIssue

object StudyRequestParser {
    private val topicSeparators = Regex("[,;\n]+")

    fun parseTopics(topicsText: String): List<String> {
        return topicsText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(topicSeparators)
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .toList()
    }

    fun validate(input: StudyRequestInput): StudyRequestValidationResult {
        val normalizedTopics = parseTopics(input.topicsText)
        val normalizedInstructions = input.specificInstructions?.trim()?.ifBlank { null }
        val issues = buildList {
            if (normalizedTopics.isEmpty()) {
                add(
                    ValidationIssue(
                        field = "topicsText",
                        message = "Enter at least one topic.",
                    ),
                )
            }

            if (normalizedTopics.size > StudyRequestConstraints.MAX_TOPICS) {
                add(
                    ValidationIssue(
                        field = "topicsText",
                        message = "Provide at most ${StudyRequestConstraints.MAX_TOPICS} topics.",
                    ),
                )
            }

            normalizedTopics
                .filter { topic -> topic.length > StudyRequestConstraints.MAX_TOPIC_LENGTH }
                .forEach { topic ->
                    add(
                        ValidationIssue(
                            field = "topicsText",
                            message = "Topic \"$topic\" exceeds ${StudyRequestConstraints.MAX_TOPIC_LENGTH} characters.",
                        ),
                    )
                }

            if (input.maxQuestions !in StudyRequestConstraints.MIN_QUESTIONS..StudyRequestConstraints.MAX_QUESTIONS) {
                add(
                    ValidationIssue(
                        field = "maxQuestions",
                        message = "Choose between ${StudyRequestConstraints.MIN_QUESTIONS} and ${StudyRequestConstraints.MAX_QUESTIONS} questions.",
                    ),
                )
            }

            if (
                normalizedInstructions != null &&
                normalizedInstructions.length > StudyRequestConstraints.MAX_SPECIFIC_INSTRUCTIONS_LENGTH
            ) {
                add(
                    ValidationIssue(
                        field = "specificInstructions",
                        message =
                            "Specific instructions must be at most " +
                                "${StudyRequestConstraints.MAX_SPECIFIC_INSTRUCTIONS_LENGTH} characters.",
                    ),
                )
            }
        }

        return if (issues.isEmpty()) {
            StudyRequestValidationResult.Success(
                request = ValidatedStudyRequest(
                    topics = normalizedTopics,
                    maxQuestions = input.maxQuestions,
                    difficulty = input.difficulty,
                    specificInstructions = normalizedInstructions,
                ),
            )
        } else {
            StudyRequestValidationResult.Failure(
                issues = issues,
                normalizedTopics = normalizedTopics,
            )
        }
    }
}
