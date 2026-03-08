package org.jetbrains.koog.cyberwave.presentation.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.koog.cyberwave.domain.model.QuestionType
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState

class StudyScreenModelContractTest {
    private val strictJson =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        }

    @Test
    fun decodesReadyPayloadThatMatchesTheStructuredOutputContract() {
        val payload =
            strictJson.decodeFromString(
                StudyScreenModel.serializer(),
                """
                {
                  "screenTitle": "Learn: Kotlin Coroutines",
                  "topics": ["Kotlin Coroutines"],
                  "summaryCards": [
                    {
                      "title": "What coroutines are",
                      "bullets": [
                        "Coroutines support asynchronous, non-blocking code.",
                        "They are lighter than OS threads."
                      ],
                      "sourceRefs": ["src1"]
                    }
                  ],
                  "quiz": {
                    "maxQuestions": 1,
                    "questions": [
                      {
                        "id": "q1",
                        "type": "single_choice",
                        "prompt": "What is a key property of a suspending function?",
                        "options": [
                          "It always creates a new thread",
                          "It can pause without blocking a thread"
                        ],
                        "correctOptionIndex": 1,
                        "explanation": "Suspending functions can pause and resume without blocking the underlying thread.",
                        "sourceRefs": ["src1"]
                      }
                    ]
                  },
                  "sources": [
                    {
                      "id": "src1",
                      "title": "Coroutine",
                      "url": "https://en.wikipedia.org/wiki/Coroutine",
                      "snippet": "Generalized subroutine for cooperative multitasking."
                    }
                  ],
                  "state": "ready",
                  "primaryAction": {
                    "id": "start_quiz",
                    "label": "Start the quiz"
                  },
                  "error": null
                }
                """.trimIndent(),
            )

        assertEquals("Learn: Kotlin Coroutines", payload.screenTitle)
        assertEquals(listOf("Kotlin Coroutines"), payload.topics)
        assertEquals(StudyGenerationState.READY, payload.state)
        assertEquals(QuestionType.SINGLE_CHOICE, payload.quiz?.questions?.single()?.type)
        assertEquals(PrimaryActionId.START_QUIZ, payload.primaryAction?.id)
        assertNull(payload.error)
    }

    @Test
    fun rejectsPayloadWhenRequiredStateFieldIsMissing() {
        val error =
            assertFailsWith<SerializationException> {
                strictJson.decodeFromString(
                    StudyScreenModel.serializer(),
                    """
                    {
                      "screenTitle": "Learn: Kotlin",
                      "topics": ["Kotlin"],
                      "summaryCards": [],
                      "quiz": null,
                      "sources": []
                    }
                    """.trimIndent(),
                )
            }

        assertEquals(true, error.message.orEmpty().contains("state"))
    }

    @Test
    fun rejectsPayloadWhenQuestionTypeFallsOutsideTheSchemaContract() {
        val error =
            assertFailsWith<SerializationException> {
                strictJson.decodeFromString(
                    StudyScreenModel.serializer(),
                    """
                    {
                      "screenTitle": "Learn: Kotlin",
                      "topics": ["Kotlin"],
                      "summaryCards": [],
                      "quiz": {
                        "maxQuestions": 1,
                        "questions": [
                          {
                            "id": "q1",
                            "type": "multiple_choice",
                            "prompt": "Broken question",
                            "options": ["A", "B"],
                            "correctOptionIndex": 0,
                            "explanation": "Broken question type",
                            "sourceRefs": ["src1"]
                          }
                        ]
                      },
                      "sources": [],
                      "state": "ready"
                    }
                    """.trimIndent(),
                )
            }

        assertEquals(true, error.message.orEmpty().contains("QuestionType"))
    }

    @Test
    fun rejectsPayloadWithUnexpectedTopLevelFieldsUnderStrictSchemaParsing() {
        val error =
            assertFailsWith<SerializationException> {
                strictJson.decodeFromString(
                    StudyScreenModel.serializer(),
                    """
                    {
                      "screenTitle": "Learn: Kotlin",
                      "topics": ["Kotlin"],
                      "summaryCards": [],
                      "quiz": null,
                      "sources": [],
                      "state": "ready",
                      "unexpectedField": "should fail"
                    }
                    """.trimIndent(),
                )
            }

        assertEquals(true, error.message.orEmpty().contains("unexpectedField"))
    }
}
