package org.jetbrains.koog.cyberwave.presentation.state

import org.jetbrains.koog.cyberwave.application.StudyRequestParser
import org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestInput
import org.jetbrains.koog.cyberwave.domain.model.StudyRequestValidationResult
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryAction
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel

object StudyUiStateReducer {
    fun reduce(
        currentState: StudyUiState,
        event: StudyUiEvent,
    ): StudyUiState =
        when (event) {
            is StudyUiEvent.TopicsTextChanged -> reduceFormChange(currentState) { form ->
                form.copy(topicsText = event.topicsText).clearIssuesFor(field = "topicsText")
            }

            is StudyUiEvent.MaxQuestionsChanged -> reduceFormChange(currentState) { form ->
                form.copy(maxQuestions = event.maxQuestions).clearIssuesFor(field = "maxQuestions")
            }

            is StudyUiEvent.DifficultyChanged -> reduceFormChange(currentState) { form ->
                form.copy(difficulty = event.difficulty)
            }

            is StudyUiEvent.SpecificInstructionsChanged -> reduceFormChange(currentState) { form ->
                form.copy(specificInstructions = event.specificInstructions).clearIssuesFor(field = "specificInstructions")
            }

            StudyUiEvent.SubmitGeneration -> submitGeneration(currentState)
            is StudyUiEvent.GenerationCompleted -> handleGenerationCompleted(currentState, event.screenModel)
            StudyUiEvent.StartQuiz -> startQuiz(currentState)
            is StudyUiEvent.AnswerSelected -> selectAnswer(currentState, event.optionIndex)
            StudyUiEvent.AdvanceQuiz -> advanceQuiz(currentState)
            StudyUiEvent.RestartQuiz -> restartQuiz(currentState)
            StudyUiEvent.ReturnToInput -> returnToInput(currentState)
        }

    private fun reduceFormChange(
        currentState: StudyUiState,
        mutate: (StudyFormState) -> StudyFormState,
    ): StudyUiState {
        if (currentState is StudyUiState.Loading) {
            return currentState
        }

        return StudyUiState.Input(mutate(currentState.form))
    }

    private fun submitGeneration(currentState: StudyUiState): StudyUiState {
        if (currentState is StudyUiState.Loading) {
            return currentState
        }

        val validation = StudyRequestParser.validate(currentState.form.toRequestInput())

        return when (validation) {
            is StudyRequestValidationResult.Success -> {
                val normalizedForm = currentState.form.normalizedFrom(validation.request)
                StudyUiState.Loading(
                    form = normalizedForm,
                    request = validation.request,
                )
            }

            is StudyRequestValidationResult.Failure -> {
                StudyUiState.Input(
                    form = currentState.form.copy(validationIssues = validation.issues),
                )
            }
        }
    }

    private fun handleGenerationCompleted(
        currentState: StudyUiState,
        screenModel: StudyScreenModel,
    ): StudyUiState {
        if (currentState !is StudyUiState.Loading) {
            return currentState
        }

        return when (screenModel.state) {
            StudyGenerationState.READY -> {
                val quiz = screenModel.quiz
                if (quiz == null || quiz.questions.isEmpty()) {
                    StudyUiState.Failure(
                        form = currentState.form,
                        screenModel = screenModel.asInvalidReadyFailure(),
                    )
                } else {
                    StudyUiState.Summary(
                        form = currentState.form,
                        screenModel = screenModel,
                    )
                }
            }

            StudyGenerationState.INSUFFICIENT_SOURCES,
            StudyGenerationState.CONFIGURATION_ERROR,
            StudyGenerationState.GENERATION_ERROR,
            StudyGenerationState.VALIDATION_ERROR,
            ->
                StudyUiState.Failure(
                    form = currentState.form,
                    screenModel = screenModel,
                )
        }
    }

    private fun startQuiz(currentState: StudyUiState): StudyUiState {
        if (currentState !is StudyUiState.Summary) {
            return currentState
        }

        val quiz = currentState.screenModel.quiz ?: return currentState
        if (quiz.questions.isEmpty()) {
            return currentState
        }

        return StudyUiState.QuizInProgress(
            form = currentState.form,
            screenModel = currentState.screenModel,
            session = QuizSessionState(quiz = quiz),
        )
    }

    private fun selectAnswer(
        currentState: StudyUiState,
        optionIndex: Int,
    ): StudyUiState {
        if (currentState !is StudyUiState.QuizInProgress) {
            return currentState
        }

        val question = currentState.session.currentQuestion
        if (optionIndex !in question.options.indices) {
            return currentState
        }

        return currentState.copy(
            session =
                currentState.session.copy(
                    selectedOptionByQuestionId =
                        currentState.session.selectedOptionByQuestionId +
                            (question.id to optionIndex),
                ),
        )
    }

    private fun advanceQuiz(currentState: StudyUiState): StudyUiState {
        if (currentState !is StudyUiState.QuizInProgress) {
            return currentState
        }

        if (!currentState.session.isCurrentQuestionAnswered) {
            return currentState
        }

        return if (currentState.session.isLastQuestion) {
            StudyUiState.QuizResults(
                form = currentState.form,
                screenModel = currentState.screenModel,
                results = currentState.session.toResults(),
            )
        } else {
            currentState.copy(
                session =
                    currentState.session.copy(
                        currentQuestionIndex = currentState.session.currentQuestionIndex + 1,
                    ),
            )
        }
    }

    private fun restartQuiz(currentState: StudyUiState): StudyUiState =
        when (currentState) {
            is StudyUiState.QuizInProgress ->
                currentState.copy(
                    session = QuizSessionState(quiz = currentState.session.quiz),
                )

            is StudyUiState.QuizResults -> {
                val quiz = currentState.screenModel.quiz ?: return currentState
                if (quiz.questions.isEmpty()) {
                    currentState
                } else {
                    StudyUiState.QuizInProgress(
                        form = currentState.form,
                        screenModel = currentState.screenModel,
                        session = QuizSessionState(quiz = quiz),
                    )
                }
            }

            else -> currentState
        }

    private fun returnToInput(currentState: StudyUiState): StudyUiState {
        if (currentState is StudyUiState.Loading) {
            return currentState
        }

        return StudyUiState.Input(currentState.form.clearAllIssues())
    }

    private fun StudyFormState.toRequestInput(): StudyRequestInput =
        StudyRequestInput(
            topicsText = topicsText,
            maxQuestions = maxQuestions,
            difficulty = difficulty,
            specificInstructions = specificInstructions,
        )

    private fun QuizSessionState.toResults(): QuizResultsState =
        QuizResultsState(
            questionResults =
                quiz.questions.map { question ->
                    QuizQuestionResult(
                        question = question,
                        selectedOptionIndex = selectedOptionByQuestionId[question.id],
                    )
                },
        )

    private fun StudyScreenModel.asInvalidReadyFailure(): StudyScreenModel =
        copy(
            state = StudyGenerationState.VALIDATION_ERROR,
            primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = "Try again"),
            error =
                error ?: StudyScreenError(
                    title = "Invalid study payload",
                    message = "The agent returned a ready state without quiz questions.",
                ),
        )
}
