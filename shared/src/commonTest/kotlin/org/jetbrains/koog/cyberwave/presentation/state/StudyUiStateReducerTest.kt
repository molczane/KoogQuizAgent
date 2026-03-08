package org.jetbrains.koog.cyberwave.presentation.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmProvider
import org.jetbrains.koog.cyberwave.domain.model.QuestionType
import org.jetbrains.koog.cyberwave.domain.model.QuizPayload
import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion
import org.jetbrains.koog.cyberwave.domain.model.ResearchSource
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.SummaryCard
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryAction
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

class StudyUiStateReducerTest {
    @Test
    fun `submit generation keeps input state when form is invalid`() {
        val initialState = StudyUiState.initial()

        val nextState = StudyUiStateReducer.reduce(initialState, StudyUiEvent.SubmitGeneration)

        val inputState = assertIs<StudyUiState.Input>(nextState)
        assertTrue(inputState.form.issuesFor("topicsText").isNotEmpty())
    }

    @Test
    fun `submit generation normalizes validated form and enters loading`() {
        val state =
            StudyUiState.Input(
                form =
                    StudyFormState(
                        topicsText = "Kotlin ; Coroutines\nkotlin",
                        maxQuestions = 3,
                        difficulty = Difficulty.HARD,
                        provider = LocalLlmProvider.OLLAMA,
                        specificInstructions = "  focus on structured concurrency  ",
                    ),
            )

        val nextState = StudyUiStateReducer.reduce(state, StudyUiEvent.SubmitGeneration)

        val loadingState = assertIs<StudyUiState.Loading>(nextState)
        assertEquals(listOf("Kotlin", "Coroutines"), loadingState.request.topics)
        assertEquals("Kotlin\nCoroutines", loadingState.form.topicsText)
        assertEquals(LocalLlmProvider.OLLAMA, loadingState.request.provider)
        assertEquals(LocalLlmProvider.OLLAMA, loadingState.form.provider)
        assertEquals("focus on structured concurrency", loadingState.form.specificInstructions)
        assertTrue(loadingState.form.validationIssues.isEmpty())
    }

    @Test
    fun `provider change updates input form state`() {
        val nextState =
            StudyUiStateReducer.reduce(
                StudyUiState.initial(),
                StudyUiEvent.ProviderChanged(LocalLlmProvider.OLLAMA),
            )

        val inputState = assertIs<StudyUiState.Input>(nextState)
        assertEquals(LocalLlmProvider.OLLAMA, inputState.form.provider)
    }

    @Test
    fun `generation completed with ready payload enters summary state`() {
        val loadingState = validLoadingState()

        val nextState =
            StudyUiStateReducer.reduce(
                loadingState,
                StudyUiEvent.GenerationCompleted(screenModel = readyScreenModel()),
            )

        val summaryState = assertIs<StudyUiState.Summary>(nextState)
        assertEquals(StudyGenerationState.READY, summaryState.screenModel.state)
        assertEquals("Learn: Kotlin Coroutines", summaryState.screenModel.screenTitle)
    }

    @Test
    fun `generation completed with insufficient sources enters failure state`() {
        val loadingState = validLoadingState()

        val nextState =
            StudyUiStateReducer.reduce(
                loadingState,
                StudyUiEvent.GenerationCompleted(screenModel = insufficientSourcesScreenModel()),
            )

        val failureState = assertIs<StudyUiState.Failure>(nextState)
        assertEquals(StudyGenerationState.INSUFFICIENT_SOURCES, failureState.screenModel.state)
        assertEquals("Need more sources", failureState.screenModel.error?.title)
    }

    @Test
    fun `generation completed with configuration error enters failure state`() {
        val loadingState = validLoadingState()

        val nextState =
            StudyUiStateReducer.reduce(
                loadingState,
                StudyUiEvent.GenerationCompleted(screenModel = configurationErrorScreenModel()),
            )

        val failureState = assertIs<StudyUiState.Failure>(nextState)
        assertEquals(StudyGenerationState.CONFIGURATION_ERROR, failureState.screenModel.state)
        assertEquals("OpenAI key missing", failureState.screenModel.error?.title)
    }

    @Test
    fun `generation completed with runtime generation error enters failure state`() {
        val loadingState = validLoadingState()

        val nextState =
            StudyUiStateReducer.reduce(
                loadingState,
                StudyUiEvent.GenerationCompleted(screenModel = generationErrorScreenModel()),
            )

        val failureState = assertIs<StudyUiState.Failure>(nextState)
        assertEquals(StudyGenerationState.GENERATION_ERROR, failureState.screenModel.state)
        assertEquals("Unable to build the study session", failureState.screenModel.error?.title)
    }

    @Test
    fun `quiz flow advances to results after answering last question`() {
        val summaryState =
            assertIs<StudyUiState.Summary>(
                StudyUiStateReducer.reduce(
                    validLoadingState(),
                    StudyUiEvent.GenerationCompleted(screenModel = readyScreenModel()),
                ),
            )

        val quizState =
            assertIs<StudyUiState.QuizInProgress>(
                StudyUiStateReducer.reduce(summaryState, StudyUiEvent.StartQuiz),
            )
        assertEquals("q1", quizState.session.currentQuestion.id)

        val firstAnswered =
            assertIs<StudyUiState.QuizInProgress>(
                StudyUiStateReducer.reduce(quizState, StudyUiEvent.AnswerSelected(optionIndex = 1)),
            )
        val secondQuestion =
            assertIs<StudyUiState.QuizInProgress>(
                StudyUiStateReducer.reduce(firstAnswered, StudyUiEvent.AdvanceQuiz),
            )
        assertEquals("q2", secondQuestion.session.currentQuestion.id)

        val secondAnswered =
            assertIs<StudyUiState.QuizInProgress>(
                StudyUiStateReducer.reduce(secondQuestion, StudyUiEvent.AnswerSelected(optionIndex = 0)),
            )
        val resultsState =
            assertIs<StudyUiState.QuizResults>(
                StudyUiStateReducer.reduce(secondAnswered, StudyUiEvent.AdvanceQuiz),
            )

        assertEquals(2, resultsState.results.totalQuestions)
        assertEquals(2, resultsState.results.answeredQuestions)
        assertEquals(2, resultsState.results.correctAnswers)
        assertTrue(resultsState.results.questionResults.all { result -> result.isCorrect })
    }

    @Test
    fun `restart quiz resets progress and clears previous selections`() {
        val completedQuizState =
            assertIs<StudyUiState.QuizResults>(
                StudyUiStateReducer.reduce(
                    assertIs<StudyUiState.QuizInProgress>(
                        StudyUiStateReducer.reduce(
                            assertIs<StudyUiState.QuizInProgress>(
                                StudyUiStateReducer.reduce(
                                    assertIs<StudyUiState.QuizInProgress>(
                                        StudyUiStateReducer.reduce(
                                            assertIs<StudyUiState.Summary>(
                                                StudyUiStateReducer.reduce(
                                                    validLoadingState(),
                                                    StudyUiEvent.GenerationCompleted(screenModel = readyScreenModel()),
                                                ),
                                            ),
                                            StudyUiEvent.StartQuiz,
                                        ),
                                    ),
                                    StudyUiEvent.AnswerSelected(optionIndex = 1),
                                ),
                            ),
                            StudyUiEvent.AdvanceQuiz,
                        ),
                    ).let { stateAfterAdvance ->
                        StudyUiStateReducer.reduce(stateAfterAdvance, StudyUiEvent.AnswerSelected(optionIndex = 0))
                    },
                    StudyUiEvent.AdvanceQuiz,
                ),
            )

        val restartedState =
            assertIs<StudyUiState.QuizInProgress>(
                StudyUiStateReducer.reduce(completedQuizState, StudyUiEvent.RestartQuiz),
            )

        assertEquals(0, restartedState.session.currentQuestionIndex)
        assertFalse(restartedState.session.isCurrentQuestionAnswered)
        assertTrue(restartedState.session.selectedOptionByQuestionId.isEmpty())
    }

    @Test
    fun `editing after summary returns to input state with updated form`() {
        val summaryState =
            assertIs<StudyUiState.Summary>(
                StudyUiStateReducer.reduce(
                    validLoadingState(),
                    StudyUiEvent.GenerationCompleted(screenModel = readyScreenModel()),
                ),
            )

        val nextState =
            StudyUiStateReducer.reduce(
                summaryState,
                StudyUiEvent.TopicsTextChanged("Kotlin Coroutines\nStructured concurrency"),
            )

        val inputState = assertIs<StudyUiState.Input>(nextState)
        assertEquals("Kotlin Coroutines\nStructured concurrency", inputState.form.topicsText)
    }

    private fun validLoadingState(): StudyUiState.Loading {
        val submittedState =
            StudyUiStateReducer.reduce(
                StudyUiState.Input(
                    form =
                        StudyFormState(
                            topicsText = "Kotlin Coroutines",
                            maxQuestions = 2,
                            difficulty = Difficulty.MEDIUM,
                        ),
                ),
                StudyUiEvent.SubmitGeneration,
            )

        return assertIs(submittedState)
    }

    private fun readyScreenModel(): StudyScreenModel =
        StudyScreenModel(
            screenTitle = "Learn: Kotlin Coroutines",
            topics = listOf("Kotlin Coroutines"),
            summaryCards =
                listOf(
                    SummaryCard(
                        title = "What coroutines are",
                        bullets =
                            listOf(
                                "Coroutines support asynchronous code.",
                                "Suspending functions can pause without blocking a thread.",
                            ),
                        sourceRefs = listOf("src1"),
                    ),
                ),
            quiz =
                QuizPayload(
                    maxQuestions = 2,
                    questions =
                        listOf(
                            QuizQuestion(
                                id = "q1",
                                type = QuestionType.SINGLE_CHOICE,
                                prompt = "What is a key property of a suspending function?",
                                options =
                                    listOf(
                                        "It always creates a new thread",
                                        "It can pause without blocking a thread",
                                        "It only works on Android",
                                        "It cannot return values",
                                    ),
                                correctOptionIndex = 1,
                                explanation = "Suspending functions can pause work without blocking the thread.",
                                sourceRefs = listOf("src1"),
                            ),
                            QuizQuestion(
                                id = "q2",
                                type = QuestionType.SINGLE_CHOICE,
                                prompt = "Why are coroutines often described as lightweight?",
                                options =
                                    listOf(
                                        "They are cheaper to create than OS threads",
                                        "They can only run one at a time",
                                        "They do not use dispatchers",
                                        "They skip structured concurrency",
                                    ),
                                correctOptionIndex = 0,
                                explanation = "Coroutine scheduling is lighter than managing many OS threads.",
                                sourceRefs = listOf("src1"),
                            ),
                        ),
                ),
            sources =
                listOf(
                    ResearchSource(
                        id = "src1",
                        title = "Kotlin coroutines",
                        url = "https://en.wikipedia.org/wiki/Coroutine",
                        snippet = "Wikipedia overview of coroutine concepts.",
                    ),
                ),
            state = StudyGenerationState.READY,
            primaryAction = PrimaryAction(id = PrimaryActionId.START_QUIZ, label = "Start the quiz"),
            error = null,
        )

    private fun insufficientSourcesScreenModel(): StudyScreenModel =
        StudyScreenModel(
            screenTitle = "Learn: Kotlin Coroutines",
            topics = listOf("Kotlin Coroutines"),
            summaryCards = emptyList(),
            quiz = null,
            sources = emptyList(),
            state = StudyGenerationState.INSUFFICIENT_SOURCES,
            primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = "Try again"),
            error =
                StudyScreenError(
                    title = "Need more sources",
                    message = "Wikipedia search did not return enough evidence to build a quiz.",
                ),
        )

    private fun configurationErrorScreenModel(): StudyScreenModel =
        StudyScreenModel(
            screenTitle = "OpenAI setup required",
            topics = listOf("Kotlin Coroutines"),
            summaryCards = emptyList(),
            quiz = null,
            sources = emptyList(),
            state = StudyGenerationState.CONFIGURATION_ERROR,
            primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = "Retry"),
            error =
                StudyScreenError(
                    title = "OpenAI key missing",
                    message = "Set OPENAI_API_KEY in your local configuration.",
                ),
        )

    private fun generationErrorScreenModel(): StudyScreenModel =
        StudyScreenModel(
            screenTitle = "Generation interrupted",
            topics = listOf("Kotlin Coroutines"),
            summaryCards = emptyList(),
            quiz = null,
            sources = emptyList(),
            state = StudyGenerationState.GENERATION_ERROR,
            primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = "Retry"),
            error =
                StudyScreenError(
                    title = "Unable to build the study session",
                    message = "The local research or generation flow failed before a study payload could be produced.",
                ),
        )
}
