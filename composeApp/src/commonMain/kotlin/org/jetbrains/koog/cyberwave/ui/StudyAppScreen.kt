package org.jetbrains.koog.cyberwave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.koog.cyberwave.application.StudyRequestConstraints
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmDefaults
import org.jetbrains.koog.cyberwave.domain.model.LocalLlmProvider
import org.jetbrains.koog.cyberwave.domain.model.QuestionType
import org.jetbrains.koog.cyberwave.domain.model.QuizPayload
import org.jetbrains.koog.cyberwave.domain.model.QuizQuestion
import org.jetbrains.koog.cyberwave.domain.model.ResearchSource
import org.jetbrains.koog.cyberwave.domain.model.SummaryCard
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryAction
import org.jetbrains.koog.cyberwave.presentation.model.StudyScreenModel
import org.jetbrains.koog.cyberwave.presentation.state.StudyFormState
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiEvent
import org.jetbrains.koog.cyberwave.presentation.state.StudyUiState

@Composable
fun StudyAppScreen(
    uiState: StudyUiState,
    onEvent: (StudyUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is StudyUiState.Input ->
            StudyInputScreen(
                form = uiState.form,
                onEvent = onEvent,
                modifier = modifier,
            )

        is StudyUiState.Loading ->
            ResearchingScreen(
                modifier = modifier,
                form = uiState.form,
                topicCount = uiState.request.topics.size,
            )

        is StudyUiState.Summary ->
            SummaryScreen(
                modifier = modifier,
                form = uiState.form,
                screenModel = uiState.screenModel,
                onStartQuiz = { onEvent(StudyUiEvent.StartQuiz) },
                onReturnToInput = { onEvent(StudyUiEvent.ReturnToInput) },
            )

        is StudyUiState.QuizInProgress ->
            QuizScreen(
                modifier = modifier,
                form = uiState.form,
                screenModel = uiState.screenModel,
                session = uiState.session,
                onAnswerSelected = { onEvent(StudyUiEvent.AnswerSelected(it)) },
                onAdvanceQuiz = { onEvent(StudyUiEvent.AdvanceQuiz) },
                onReturnToInput = { onEvent(StudyUiEvent.ReturnToInput) },
            )

        is StudyUiState.QuizResults ->
            ResultsScreen(
                modifier = modifier,
                form = uiState.form,
                screenModel = uiState.screenModel,
                results = uiState.results,
                onRestartQuiz = { onEvent(StudyUiEvent.RestartQuiz) },
                onReturnToInput = { onEvent(StudyUiEvent.ReturnToInput) },
            )

        is StudyUiState.Failure ->
            FailureScreen(
                modifier = modifier,
                form = uiState.form,
                screenModel = uiState.screenModel,
                onReturnToInput = { onEvent(StudyUiEvent.ReturnToInput) },
            )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyInputScreen(
    form: StudyFormState,
    onEvent: (StudyUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .safeContentPadding(),
    ) {
        val wideLayout = maxWidth >= 980.dp
        val containerPadding = if (wideLayout) PaddingValues(36.dp) else PaddingValues(horizontal = 18.dp, vertical = 24.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = containerPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                if (wideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        HeroPanel(modifier = Modifier.weight(1.05f))
                        FormPanel(
                            form = form,
                            onEvent = onEvent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        HeroPanel(modifier = Modifier.fillMaxWidth())
                        FormPanel(
                            form = form,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroPanel(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                                ),
                        ),
                    )
                    .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            ) {
                Text(
                    text = "WIKIPEDIA-FIRST WORKFLOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            Text(
                text = "Build study notes before the quiz starts.",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "CyberWave turns a bounded request into a researched lesson plan. The form shapes the request, the shared reducer validates it, and the Koog flow handles research before generation.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroMetric(
                    title = "1. Define the lane",
                    description = "Topics, question count, difficulty, and optional instructions all come from explicit controls.",
                )
                HeroMetric(
                    title = "2. Research first",
                    description = "The agent searches Wikipedia, filters candidates, and only then generates study material.",
                )
                HeroMetric(
                    title = "3. Stay renderable",
                    description = "The model returns structured screen data only. The UI remains deterministic on Desktop and Wasm.",
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InsightBadge(text = "Single-choice v1")
                InsightBadge(text = "Desktop + Wasm")
                InsightBadge(text = "OpenAI + Ollama")
                InsightBadge(text = "Source-backed summaries")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryScreen(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    onStartQuiz: () -> Unit,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quiz = screenModel.quiz ?: return
    val sourcesById = remember(screenModel.sources) { screenModel.sources.associateBy(ResearchSource::id) }
    val uriHandler = LocalUriHandler.current

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .safeContentPadding(),
    ) {
        val wideLayout = maxWidth >= 1040.dp
        val containerPadding = if (wideLayout) PaddingValues(36.dp) else PaddingValues(horizontal = 18.dp, vertical = 24.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = containerPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                if (wideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1.25f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            SummaryHero(
                                form = form,
                                screenModel = screenModel,
                                quiz = quiz,
                            )
                            SummaryCardsSection(
                                summaryCards = screenModel.summaryCards,
                                sourcesById = sourcesById,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(0.85f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            QuizReadyPanel(
                                quiz = quiz,
                                primaryActionLabel = screenModel.primaryAction?.label ?: "Start the quiz",
                                onStartQuiz = onStartQuiz,
                                onReturnToInput = onReturnToInput,
                            )
                            SourcesSection(
                                sources = screenModel.sources,
                                onOpenSource = uriHandler::openUri,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SummaryHero(
                            form = form,
                            screenModel = screenModel,
                            quiz = quiz,
                        )
                        QuizReadyPanel(
                            quiz = quiz,
                            primaryActionLabel = screenModel.primaryAction?.label ?: "Start the quiz",
                            onStartQuiz = onStartQuiz,
                            onReturnToInput = onReturnToInput,
                        )
                        SummaryCardsSection(
                            summaryCards = screenModel.summaryCards,
                            sourcesById = sourcesById,
                        )
                        SourcesSection(
                            sources = screenModel.sources,
                            onOpenSource = uriHandler::openUri,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizScreen(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    session: org.jetbrains.koog.cyberwave.presentation.state.QuizSessionState,
    onAnswerSelected: (Int) -> Unit,
    onAdvanceQuiz: () -> Unit,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = session.currentQuestion
    val selectedOptionIndex = session.selectedOptionByQuestionId[question.id]
    val questionSources =
        remember(question.sourceRefs, screenModel.sources) {
            screenModel.sources.filter { source -> source.id in question.sourceRefs }
        }
    val uriHandler = LocalUriHandler.current

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .safeContentPadding(),
    ) {
        val wideLayout = maxWidth >= 1040.dp
        val containerPadding = if (wideLayout) PaddingValues(36.dp) else PaddingValues(horizontal = 18.dp, vertical = 24.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = containerPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                QuizHero(
                    form = form,
                    screenModel = screenModel,
                    session = session,
                )
            }

            item {
                if (wideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        QuestionPanel(
                            question = question,
                            selectedOptionIndex = selectedOptionIndex,
                            onAnswerSelected = onAnswerSelected,
                            modifier = Modifier.weight(1.2f),
                        )
                        Column(
                            modifier = Modifier.weight(0.8f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            QuizActionPanel(
                                session = session,
                                selectedOptionIndex = selectedOptionIndex,
                                onAdvanceQuiz = onAdvanceQuiz,
                                onReturnToInput = onReturnToInput,
                            )
                            QuizSourcesPanel(
                                questionSources = questionSources,
                                onOpenSource = uriHandler::openUri,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        QuestionPanel(
                            question = question,
                            selectedOptionIndex = selectedOptionIndex,
                            onAnswerSelected = onAnswerSelected,
                        )
                        QuizActionPanel(
                            session = session,
                            selectedOptionIndex = selectedOptionIndex,
                            onAdvanceQuiz = onAdvanceQuiz,
                            onReturnToInput = onReturnToInput,
                        )
                        QuizSourcesPanel(
                            questionSources = questionSources,
                            onOpenSource = uriHandler::openUri,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizHero(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    session: org.jetbrains.koog.cyberwave.presentation.state.QuizSessionState,
    modifier: Modifier = Modifier,
) {
    val currentNumber = session.currentQuestionIndex + 1
    val totalQuestions = session.quiz.questions.size

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                ),
                        ),
                    )
                    .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "QUIZ IN PROGRESS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = screenModel.screenTitle,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Question $currentNumber of $totalQuestions. Choose one answer and continue when you are ready.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { currentNumber / totalQuestions.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                screenModel.topics.forEach { topic ->
                    InsightBadge(text = topic)
                }
                InsightBadge(text = form.difficulty.label)
                InsightBadge(text = form.provider.label)
                InsightBadge(text = "${(totalQuestions - currentNumber).coerceAtLeast(0)} remaining")
            }
        }
    }
}

@Composable
private fun QuestionPanel(
    question: QuizQuestion,
    selectedOptionIndex: Int?,
    onAnswerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Question",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEachIndexed { index, option ->
                    QuizOptionRow(
                        optionIndex = index,
                        optionText = option,
                        selected = selectedOptionIndex == index,
                        onSelect = { onAnswerSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizOptionRow(
    optionIndex: Int,
    optionText: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                border = BorderStroke(1.dp, borderColor),
            ) {
                Text(
                    text = optionLetter(optionIndex),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
            )
            Text(
                text = optionText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuizActionPanel(
    session: org.jetbrains.koog.cyberwave.presentation.state.QuizSessionState,
    selectedOptionIndex: Int?,
    onAdvanceQuiz: () -> Unit,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val advanceLabel = if (session.isLastQuestion) "See results" else "Next question"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Move through the quiz",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text =
                    if (selectedOptionIndex == null) {
                        "Pick one answer to continue."
                    } else {
                        "Selection recorded. You can still change it before continuing."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = onAdvanceQuiz,
                enabled = selectedOptionIndex != null,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Text(advanceLabel)
            }
            OutlinedButton(
                onClick = onReturnToInput,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to request")
            }
        }
    }
}

@Composable
private fun QuizSourcesPanel(
    questionSources: List<ResearchSource>,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Current question sources",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "The quiz stays grounded in the same Wikipedia material used for generation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (questionSources.isEmpty()) {
                Text(
                    text = "No source details are attached to this question.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                questionSources.forEach { source ->
                    SourceRow(
                        source = source,
                        onOpenSource = onOpenSource,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsScreen(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    results: org.jetbrains.koog.cyberwave.presentation.state.QuizResultsState,
    onRestartQuiz: () -> Unit,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourcesById = remember(screenModel.sources) { screenModel.sources.associateBy(ResearchSource::id) }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .safeContentPadding(),
    ) {
        val wideLayout = maxWidth >= 1040.dp
        val containerPadding = if (wideLayout) PaddingValues(36.dp) else PaddingValues(horizontal = 18.dp, vertical = 24.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = containerPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                if (wideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1.2f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            ResultsHero(
                                form = form,
                                screenModel = screenModel,
                                results = results,
                            )
                            QuestionResultsSection(
                                results = results,
                                sourcesById = sourcesById,
                            )
                        }
                        ResultsActionPanel(
                            results = results,
                            onRestartQuiz = onRestartQuiz,
                            onReturnToInput = onReturnToInput,
                            modifier = Modifier.weight(0.8f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ResultsHero(
                            form = form,
                            screenModel = screenModel,
                            results = results,
                        )
                        ResultsActionPanel(
                            results = results,
                            onRestartQuiz = onRestartQuiz,
                            onReturnToInput = onReturnToInput,
                        )
                        QuestionResultsSection(
                            results = results,
                            sourcesById = sourcesById,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailureScreen(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = screenModel.error
    val uriHandler = LocalUriHandler.current

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .safeContentPadding(),
    ) {
        val wideLayout = maxWidth >= 1040.dp
        val containerPadding = if (wideLayout) PaddingValues(36.dp) else PaddingValues(horizontal = 18.dp, vertical = 24.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = containerPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                FailureHero(
                    form = form,
                    screenModel = screenModel,
                )
            }

            item {
                if (wideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            if (error != null) {
                                FailureDetailsPanel(
                                    error = error,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (screenModel.sources.isNotEmpty()) {
                                SourcesSection(
                                    sources = screenModel.sources,
                                    onOpenSource = uriHandler::openUri,
                                )
                            }
                        }
                        FailureActionPanel(
                            form = form,
                            screenModel = screenModel,
                            onReturnToInput = onReturnToInput,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        FailureActionPanel(
                            form = form,
                            screenModel = screenModel,
                            onReturnToInput = onReturnToInput,
                        )
                        if (error != null) {
                            FailureDetailsPanel(error = error)
                        }
                        if (screenModel.sources.isNotEmpty()) {
                            SourcesSection(
                                sources = screenModel.sources,
                                onOpenSource = uriHandler::openUri,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsHero(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    results: org.jetbrains.koog.cyberwave.presentation.state.QuizResultsState,
    modifier: Modifier = Modifier,
) {
    val scorePercent =
        if (results.totalQuestions == 0) {
            0
        } else {
            (results.correctAnswers * 100) / results.totalQuestions
        }
    val unansweredQuestions = (results.totalQuestions - results.answeredQuestions).coerceAtLeast(0)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                ),
                        ),
                    )
                    .padding(28.dp),
        ) {
            val wideLayout = maxWidth >= 900.dp

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1.45f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = "QUIZ COMPLETE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = screenModel.screenTitle,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "You answered ${results.correctAnswers} out of ${results.totalQuestions} question(s) correctly.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            InsightBadge(text = "$scorePercent% score")
                            InsightBadge(text = "${results.answeredQuestions} answered")
                            InsightBadge(text = form.difficulty.label)
                            InsightBadge(text = form.provider.label)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(0.85f),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "Performance snapshot",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            HeroMetric(
                                title = "Correct answers",
                                description = "${results.correctAnswers} out of ${results.totalQuestions} were correct.",
                            )
                            HeroMetric(
                                title = "Unanswered",
                                description = "$unansweredQuestions question(s) were left without a selected option.",
                            )
                            HeroMetric(
                                title = "Review mode",
                                description = "Read the explanations below to see why each answer was right or wrong.",
                            )
                        }
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        text = "QUIZ COMPLETE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = screenModel.screenTitle,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "You answered ${results.correctAnswers} out of ${results.totalQuestions} question(s) correctly.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InsightBadge(text = "$scorePercent% score")
                        InsightBadge(text = "${results.answeredQuestions} answered")
                        InsightBadge(text = form.difficulty.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionResultsSection(
    results: org.jetbrains.koog.cyberwave.presentation.state.QuizResultsState,
    sourcesById: Map<String, ResearchSource>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Question review",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Correctness and explanations are shown only after the quiz finishes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            results.questionResults.forEachIndexed { index, questionResult ->
                QuestionResultCard(
                    index = index + 1,
                    questionResult = questionResult,
                    sourcesById = sourcesById,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionResultCard(
    index: Int,
    questionResult: org.jetbrains.koog.cyberwave.presentation.state.QuizQuestionResult,
    sourcesById: Map<String, ResearchSource>,
) {
    val selectedText =
        questionResult.selectedOptionIndex
            ?.let(questionResult.question.options::getOrNull)
            ?: "No answer selected"
    val correctText = questionResult.question.options.getOrNull(questionResult.question.correctOptionIndex).orEmpty()
    val badgeText = if (questionResult.isCorrect) "Correct" else "Needs review"
    val badgeColor = if (questionResult.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Question $index",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                InsightBadge(text = badgeText)
            }
            Text(
                text = questionResult.question.prompt,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ResultLine(
                label = "Your answer",
                value = selectedText,
                valueColor = badgeColor,
            )
            ResultLine(
                label = "Correct answer",
                value = correctText,
                valueColor = MaterialTheme.colorScheme.primary,
            )
            ResultLine(
                label = "Why",
                value = questionResult.question.explanation,
            )
            if (questionResult.question.sourceRefs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    questionResult.question.sourceRefs.forEach { sourceRef ->
                        val title = sourcesById[sourceRef]?.title ?: sourceRef
                        InsightBadge(text = title)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
        )
    }
}

@Composable
private fun ResultsActionPanel(
    results: org.jetbrains.koog.cyberwave.presentation.state.QuizResultsState,
    onRestartQuiz: () -> Unit,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Next move",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Restart the quiz to try again, or return to the request form to generate a different lesson set.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            InsightBadge(text = "${results.correctAnswers}/${results.totalQuestions} correct")
            Button(
                onClick = onRestartQuiz,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Text("Restart quiz")
            }
            OutlinedButton(
                onClick = onReturnToInput,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to request")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailureHero(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    modifier: Modifier = Modifier,
) {
    val title = screenModel.error?.title ?: screenModel.screenTitle
    val message = screenModel.error?.message ?: "The requested study session could not be produced."

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                ),
                        ),
                    )
                    .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "GENERATION STOPPED",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (screenModel.topics.isNotEmpty()) {
                    screenModel.topics.forEach { topic ->
                        InsightBadge(text = topic)
                    }
                }
                InsightBadge(text = form.difficulty.label)
            }
        }
    }
}

@Composable
private fun FailureDetailsPanel(
    error: org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "What needs attention",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error.validationIssues.isNotEmpty()) {
                error.validationIssues.forEach { issue ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = validationFieldLabel(issue.field),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = issue.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureActionPanel(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Recover from this state",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = failureGuidance(form = form, screenModel = screenModel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = onReturnToInput,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Text(screenModel.primaryAction?.label ?: "Back to request")
            }
            OutlinedButton(
                onClick = onReturnToInput,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit request")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryHero(
    form: StudyFormState,
    screenModel: StudyScreenModel,
    quiz: QuizPayload,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                                ),
                        ),
                    )
                    .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "READY TO REVIEW",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = screenModel.screenTitle,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Wikipedia research is complete. Review the summary cards, scan the sources, and start the quiz when the lesson looks right.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                screenModel.topics.forEach { topic ->
                    InsightBadge(text = topic)
                }
                InsightBadge(text = "${quiz.questions.size} questions")
                InsightBadge(text = "${screenModel.sources.size} sources")
                InsightBadge(text = form.difficulty.label)
                InsightBadge(text = form.provider.label)
            }
        }
    }
}

@Composable
private fun SummaryCardsSection(
    summaryCards: List<SummaryCard>,
    sourcesById: Map<String, ResearchSource>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Study summary",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Each card comes from the researched Wikipedia evidence that will also back the quiz questions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            summaryCards.forEach { summaryCard ->
                SummaryCardView(
                    summaryCard = summaryCard,
                    sourcesById = sourcesById,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCardView(
    summaryCard: SummaryCard,
    sourcesById: Map<String, ResearchSource>,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = summaryCard.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                summaryCard.bullets.forEach { bullet ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (summaryCard.sourceRefs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    summaryCard.sourceRefs.forEach { sourceRef ->
                        val sourceTitle = sourcesById[sourceRef]?.title ?: sourceRef
                        InsightBadge(text = sourceTitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizReadyPanel(
    quiz: QuizPayload,
    primaryActionLabel: String,
    onStartQuiz: () -> Unit,
    onReturnToInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Quiz is ready",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "The generated quiz contains ${quiz.questions.size} single-choice question(s). Start when you are ready or go back and refine the request.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(
                onClick = onStartQuiz,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Text(primaryActionLabel)
            }
            OutlinedButton(
                onClick = onReturnToInput,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to request")
            }
        }
    }
}

@Composable
private fun SourcesSection(
    sources: List<ResearchSource>,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Sources",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "These Wikipedia articles support both the summary cards and the quiz content.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sources.forEach { source ->
                SourceRow(
                    source = source,
                    onOpenSource = onOpenSource,
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: ResearchSource,
    onOpenSource: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = source.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = source.url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenSource(source.url) },
            )
        }
    }
}

private fun optionLetter(index: Int): String = ('A' + index).toString()

private fun validationFieldLabel(field: String): String =
    when (field) {
        "topicsText" -> "Topics"
        "maxQuestions" -> "Question count"
        "specificInstructions" -> "Specific instructions"
        else -> field
    }

private fun failureGuidance(
    form: StudyFormState,
    screenModel: StudyScreenModel,
): String =
    when (screenModel.state) {
        org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState.INSUFFICIENT_SOURCES ->
            "Wikipedia evidence was too thin for the requested lesson. Narrow the topics or reduce the question count, then run generation again."

        org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState.CONFIGURATION_ERROR ->
            when (form.provider) {
                LocalLlmProvider.OPENAI ->
                    "The local OpenAI setup is incomplete. Return to the request screen after fixing the key or local direct mode configuration."

                LocalLlmProvider.OLLAMA ->
                    "The local Ollama runtime is not ready. Make sure Ollama is running at ${LocalLlmDefaults.OLLAMA_BASE_URL} and that ${LocalLlmDefaults.OLLAMA_MODEL_NAME} is available locally."
            }

        org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState.GENERATION_ERROR ->
            when (form.provider) {
                LocalLlmProvider.OPENAI ->
                    "The local research or generation runtime failed before a stable payload was produced. Check network access and your local OpenAI configuration, then retry."

                LocalLlmProvider.OLLAMA ->
                    "The local research or generation runtime failed before a stable payload was produced. Verify that Ollama is running at ${LocalLlmDefaults.OLLAMA_BASE_URL} and that ${LocalLlmDefaults.OLLAMA_MODEL_NAME} is ready locally, then retry."
            }

        org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState.VALIDATION_ERROR ->
            "The request needs adjustment before generation can start. Review the flagged fields and submit again."

        else -> "Return to the request form, adjust the input, and try again."
    }

@Composable
private fun ResearchingScreen(
    form: StudyFormState,
    topicCount: Int,
    modifier: Modifier = Modifier,
) {
    val stages =
        remember(form.maxQuestions, form.difficulty, topicCount, form.provider) {
            listOf(
                "Validating $topicCount topic(s) and ${form.maxQuestions} requested question(s).",
                "Searching Wikipedia and ranking likely article candidates.",
                "Fetching article content and checking evidence coverage for ${form.difficulty.label}.",
                providerGenerationStage(form.provider),
            )
        }
    var activeStage by remember(stages) { mutableIntStateOf(0) }

    LaunchedEffect(stages) {
        activeStage = 0
        while (activeStage < stages.lastIndex) {
            kotlinx.coroutines.delay(1200)
            activeStage += 1
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RESEARCHING",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "Building a source-backed study session",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "This step runs locally: validate input, search Wikipedia, fetch article evidence, and then generate the structured lesson payload.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LinearProgressIndicator(
                    progress = { (activeStage + 1f) / stages.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Request snapshot",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = snapshotText(form),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stages.forEachIndexed { index, stage ->
                        val isActive = index == activeStage
                        val isCompleted = index < activeStage
                        val indicatorColor =
                            when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isActive -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(top = 4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(indicatorColor),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stage,
                                    style = MaterialTheme.typography.titleMedium,
                                    color =
                                        if (isActive || isCompleted) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                                Text(
                                    text =
                                        when {
                                            isCompleted -> "Completed"
                                            isActive -> "In progress"
                                            else -> "Queued"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = indicatorColor,
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = providerStallMessage(form.provider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormPanel(
    form: StudyFormState,
    onEvent: (StudyUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topicsIssues = remember(form.validationIssues) { form.issuesFor(field = "topicsText") }
    val questionIssues = remember(form.validationIssues) { form.issuesFor(field = "maxQuestions") }
    val instructionsIssues = remember(form.validationIssues) { form.issuesFor(field = "specificInstructions") }

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Study setup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Use one topic per line or separate them with commas. The reducer validates locally before any agent work begins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = form.topicsText,
                onValueChange = { onEvent(StudyUiEvent.TopicsTextChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Topics") },
                placeholder = { Text("Kotlin Coroutines\nStructured concurrency") },
                minLines = 4,
                maxLines = 6,
                isError = topicsIssues.isNotEmpty(),
                supportingText = {
                    FieldSupport(
                        issues = topicsIssues.map { it.message },
                        defaultText = "Up to ${StudyRequestConstraints.MAX_TOPICS} topics. One focused concept per topic works best.",
                    )
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Question count",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                QuestionCountStepper(
                    value = form.maxQuestions,
                    isError = questionIssues.isNotEmpty(),
                    onDecrease = {
                        onEvent(
                            StudyUiEvent.MaxQuestionsChanged(
                                (form.maxQuestions - 1).coerceAtLeast(StudyRequestConstraints.MIN_QUESTIONS),
                            ),
                        )
                    },
                    onIncrease = {
                        onEvent(
                            StudyUiEvent.MaxQuestionsChanged(
                                (form.maxQuestions + 1).coerceAtMost(StudyRequestConstraints.MAX_QUESTIONS),
                            ),
                        )
                    },
                )
                FieldSupport(
                    issues = questionIssues.map { it.message },
                    defaultText = "${StudyRequestConstraints.MIN_QUESTIONS}-${StudyRequestConstraints.MAX_QUESTIONS} questions. The agent may recommend fewer if evidence is thin.",
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Difficulty",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Difficulty.entries.forEach { difficulty ->
                        FilterChip(
                            selected = form.difficulty == difficulty,
                            onClick = { onEvent(StudyUiEvent.DifficultyChanged(difficulty)) },
                            label = { Text(difficulty.label) },
                        )
                    }
                }
                Text(
                    text = form.difficulty.supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "LLM provider",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LocalLlmProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = form.provider == provider,
                            onClick = { onEvent(StudyUiEvent.ProviderChanged(provider)) },
                            label = { Text(provider.label) },
                        )
                    }
                }
                ProviderSetupPanel(provider = form.provider)
            }

            OutlinedTextField(
                value = form.specificInstructions,
                onValueChange = { onEvent(StudyUiEvent.SpecificInstructionsChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Specific instructions") },
                placeholder = { Text("Optional: focus on practical examples or compare key trade-offs.") },
                minLines = 3,
                maxLines = 5,
                isError = instructionsIssues.isNotEmpty(),
                supportingText = {
                    FieldSupport(
                        issues = instructionsIssues.map { it.message },
                        defaultText = "Optional guidance only. It can steer emphasis, but it does not override the research-first workflow.",
                    )
                },
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Current request snapshot",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = snapshotText(form),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Button(
                onClick = { onEvent(StudyUiEvent.SubmitGeneration) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text("Research and build quiz")
            }
        }
    }
}

@Composable
private fun ProviderSetupPanel(
    provider: LocalLlmProvider,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = providerSetupTitle(provider),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = providerSetupBody(provider),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun QuestionCountStepper(
    value: Int,
    isError: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onDecrease,
            enabled = value > StudyRequestConstraints.MIN_QUESTIONS,
            modifier = Modifier.weight(0.25f),
        ) {
            Text("-")
        }
        Surface(
            modifier = Modifier.weight(0.5f),
            shape = MaterialTheme.shapes.medium,
            color =
                if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                },
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color =
                        if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Text(
                    text = "questions",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        OutlinedButton(
            onClick = onIncrease,
            enabled = value < StudyRequestConstraints.MAX_QUESTIONS,
            modifier = Modifier.weight(0.25f),
        ) {
            Text("+")
        }
    }
}

@Composable
private fun FieldSupport(
    issues: List<String>,
    defaultText: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (issues.isEmpty()) {
            Text(
                text = defaultText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            issues.forEach { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private val Difficulty.label: String
    get() =
        when (this) {
            Difficulty.EASY -> "Easy"
            Difficulty.MEDIUM -> "Medium"
            Difficulty.HARD -> "Hard"
        }

private val Difficulty.supportingText: String
    get() =
        when (this) {
            Difficulty.EASY -> "Good for recall, definitions, and broad orientation."
            Difficulty.MEDIUM -> "Balanced depth for core concepts, comparisons, and reasoning."
            Difficulty.HARD -> "Lean toward trade-offs, cause-and-effect, and less obvious distinctions."
        }

private val LocalLlmProvider.label: String
    get() =
        when (this) {
            LocalLlmProvider.OPENAI -> "OpenAI"
            LocalLlmProvider.OLLAMA -> "Ollama"
        }

private fun providerSetupTitle(provider: LocalLlmProvider): String =
    when (provider) {
        LocalLlmProvider.OPENAI -> "Use ChatGPT with a local OpenAI key"
        LocalLlmProvider.OLLAMA -> "Use a local Ollama model"
    }

private fun providerSetupBody(provider: LocalLlmProvider): String =
    when (provider) {
        LocalLlmProvider.OPENAI ->
            "Desktop reads OPENAI_API_KEY from your shell or IDE. WasmJS reads the key from browser localStorage in local_direct mode."

        LocalLlmProvider.OLLAMA ->
            "The app calls ${LocalLlmDefaults.OLLAMA_MODEL_NAME} on ${LocalLlmDefaults.OLLAMA_BASE_URL}. Start Ollama locally and make sure the model is available before generating a lesson."
    }

private fun providerGenerationStage(provider: LocalLlmProvider): String =
    when (provider) {
        LocalLlmProvider.OPENAI -> "Asking Koog with OpenAI for the structured summary and quiz payload."
        LocalLlmProvider.OLLAMA ->
            "Asking Koog with local Ollama (${LocalLlmDefaults.OLLAMA_MODEL_NAME}) for the structured summary and quiz payload."
    }

private fun providerStallMessage(provider: LocalLlmProvider): String =
    when (provider) {
        LocalLlmProvider.OPENAI -> "If this stalls locally, check your network and local OpenAI key setup, then try again."
        LocalLlmProvider.OLLAMA ->
            "If this stalls locally, make sure Ollama is running at ${LocalLlmDefaults.OLLAMA_BASE_URL} and that ${LocalLlmDefaults.OLLAMA_MODEL_NAME} is ready, then try again."
    }

private fun snapshotText(form: StudyFormState): String {
    val topicCount =
        form.topicsText
            .split(',', '\n', ';')
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .count()

    val instructionsState = if (form.specificInstructions.isBlank()) "No extra instructions" else "Custom emphasis provided"
    return "${topicCount.coerceAtLeast(0)} topic(s), ${form.maxQuestions} question(s), ${form.difficulty.label}, ${form.provider.label}, $instructionsState."
}

@Preview
@Composable
private fun StudyInputScreenPreview() {
    CyberWaveTheme {
        StudyAppScreen(
            uiState =
                StudyUiState.Input(
                    form =
                        StudyFormState(
                            topicsText = "Kotlin Coroutines\nStructured concurrency",
                            maxQuestions = 6,
                            difficulty = Difficulty.MEDIUM,
                            specificInstructions = "Focus on practical reasoning rather than syntax trivia.",
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun SummaryScreenPreview() {
    CyberWaveTheme {
        StudyAppScreen(
            uiState =
                StudyUiState.Summary(
                    form =
                        StudyFormState(
                            topicsText = "Kotlin Coroutines",
                            maxQuestions = 4,
                            difficulty = Difficulty.MEDIUM,
                        ),
                    screenModel = previewSummaryScreenModel(),
                ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun QuizScreenPreview() {
    val screenModel = previewSummaryScreenModel()

    CyberWaveTheme {
        StudyAppScreen(
            uiState =
                StudyUiState.QuizInProgress(
                    form =
                        StudyFormState(
                            topicsText = "Kotlin Coroutines",
                            maxQuestions = 4,
                            difficulty = Difficulty.MEDIUM,
                        ),
                    screenModel = screenModel,
                    session =
                        org.jetbrains.koog.cyberwave.presentation.state.QuizSessionState(
                            quiz = requireNotNull(screenModel.quiz),
                            selectedOptionByQuestionId = mapOf("q1" to 0),
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun ResultsScreenPreview() {
    val screenModel = previewSummaryScreenModel()
    val quiz = requireNotNull(screenModel.quiz)

    CyberWaveTheme {
        StudyAppScreen(
            uiState =
                StudyUiState.QuizResults(
                    form =
                        StudyFormState(
                            topicsText = "Kotlin Coroutines",
                            maxQuestions = 4,
                            difficulty = Difficulty.MEDIUM,
                        ),
                    screenModel = screenModel,
                    results =
                        org.jetbrains.koog.cyberwave.presentation.state.QuizResultsState(
                            questionResults =
                                quiz.questions.map { question ->
                                    org.jetbrains.koog.cyberwave.presentation.state.QuizQuestionResult(
                                        question = question,
                                        selectedOptionIndex = if (question.id == "q1") 0 else question.correctOptionIndex,
                                    )
                                },
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun FailureScreenPreview() {
    CyberWaveTheme {
        StudyAppScreen(
            uiState =
                StudyUiState.Failure(
                    form =
                        StudyFormState(
                            topicsText = "Kotlin Coroutines",
                            maxQuestions = 6,
                            difficulty = Difficulty.MEDIUM,
                        ),
                    screenModel = previewFailureScreenModel(),
                ),
            onEvent = {},
        )
    }
}

private fun previewSummaryScreenModel(): StudyScreenModel =
    StudyScreenModel(
        screenTitle = "Learn: Kotlin Coroutines",
        topics = listOf("Kotlin Coroutines"),
        summaryCards =
            listOf(
                SummaryCard(
                    title = "What coroutines are",
                    bullets =
                        listOf(
                            "Coroutines let asynchronous code suspend without blocking the underlying thread.",
                            "They are lighter to create and manage than OS threads in many workloads.",
                        ),
                    sourceRefs = listOf("src1"),
                ),
                SummaryCard(
                    title = "Why structured concurrency matters",
                    bullets =
                        listOf(
                            "Scopes define ownership for child coroutines.",
                            "Cancellation and failure propagate more predictably across related work.",
                        ),
                    sourceRefs = listOf("src1", "src2"),
                ),
            ),
        quiz =
            QuizPayload(
                maxQuestions = 4,
                questions =
                    listOf(
                        QuizQuestion(
                            id = "q1",
                            type = QuestionType.SINGLE_CHOICE,
                            prompt = "What does a suspending function avoid doing while it waits?",
                            options = listOf("Blocking the thread", "Returning values", "Using dispatchers", "Creating scopes"),
                            correctOptionIndex = 0,
                            explanation = "Suspending functions can pause without blocking the thread.",
                            sourceRefs = listOf("src1"),
                        ),
                    ),
            ),
        sources =
            listOf(
                ResearchSource(
                    id = "src1",
                    title = "Coroutine",
                    url = "https://en.wikipedia.org/wiki/Coroutine",
                    snippet = "General coroutine concepts and suspension behavior.",
                ),
                ResearchSource(
                    id = "src2",
                    title = "Kotlin coroutines",
                    url = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                    snippet = "Kotlin background and coroutine support context.",
                ),
            ),
        state = org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState.READY,
        primaryAction = PrimaryAction(id = PrimaryActionId.START_QUIZ, label = "Start the quiz"),
    )

private fun previewFailureScreenModel(): StudyScreenModel =
    StudyScreenModel(
        screenTitle = "Not enough evidence yet",
        topics = listOf("Kotlin Coroutines", "Structured concurrency"),
        sources =
            listOf(
                ResearchSource(
                    id = "src1",
                    title = "Coroutine",
                    url = "https://en.wikipedia.org/wiki/Coroutine",
                    snippet = "General coroutine concepts and suspension behavior.",
                ),
            ),
        state = org.jetbrains.koog.cyberwave.domain.model.StudyGenerationState.INSUFFICIENT_SOURCES,
        primaryAction = PrimaryAction(id = PrimaryActionId.RETRY, label = "Retry"),
        error =
            org.jetbrains.koog.cyberwave.presentation.model.StudyScreenError(
                title = "Wikipedia evidence is too limited",
                message = "I could not support the requested number of questions from the available Wikipedia evidence.",
            ),
    )
