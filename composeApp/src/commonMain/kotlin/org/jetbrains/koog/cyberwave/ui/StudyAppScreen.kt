package org.jetbrains.koog.cyberwave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.koog.cyberwave.application.StudyRequestConstraints
import org.jetbrains.koog.cyberwave.domain.model.Difficulty
import org.jetbrains.koog.cyberwave.presentation.model.PrimaryActionId
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
            StagePlaceholderScreen(
                modifier = modifier,
                eyebrow = "SUMMARY",
                title = uiState.screenModel.screenTitle,
                message = "The summary view is the next step. The state routing is already in place.",
                supportingNote = "Primary action: ${uiState.screenModel.primaryAction?.label ?: "Continue"}",
                actionLabel = "Back to request",
                onAction = { onEvent(StudyUiEvent.ReturnToInput) },
            )

        is StudyUiState.QuizInProgress ->
            StagePlaceholderScreen(
                modifier = modifier,
                eyebrow = "QUIZ",
                title = "Quiz screen is next",
                message = "The reducer already tracks answers and progression. This placeholder keeps the app navigable until that screen is built.",
                supportingNote = "Current action: ${uiState.screenModel.primaryAction?.label ?: "Quiz in progress"}",
                actionLabel = "Back to request",
                onAction = { onEvent(StudyUiEvent.ReturnToInput) },
            )

        is StudyUiState.QuizResults ->
            StagePlaceholderScreen(
                modifier = modifier,
                eyebrow = "RESULTS",
                title = "Results view is next",
                message = "Scoring is ready in shared code. The dedicated results UI lands in the next slices.",
                supportingNote = "Answered ${uiState.results.questionResults.count { it.selectedOptionIndex != null }} question(s).",
                actionLabel = "Back to request",
                onAction = { onEvent(StudyUiEvent.ReturnToInput) },
            )

        is StudyUiState.Failure ->
            StagePlaceholderScreen(
                modifier = modifier,
                eyebrow = "ERROR STATE",
                title = uiState.screenModel.error?.title ?: uiState.screenModel.screenTitle,
                message = uiState.screenModel.error?.message ?: "The failure screen will be expanded in the next task.",
                supportingNote = "Action: ${uiState.screenModel.primaryAction?.label ?: "Retry"}",
                actionLabel =
                    when (uiState.screenModel.primaryAction?.id) {
                        PrimaryActionId.RETRY -> "Back to request"
                        else -> "Back to request"
                    },
                onAction = { onEvent(StudyUiEvent.ReturnToInput) },
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
                InsightBadge(text = "Local direct OpenAI")
                InsightBadge(text = "Source-backed summaries")
            }
        }
    }
}

@Composable
private fun ResearchingScreen(
    form: StudyFormState,
    topicCount: Int,
    modifier: Modifier = Modifier,
) {
    val stages =
        remember(form.maxQuestions, form.difficulty, topicCount) {
            listOf(
                "Validating $topicCount topic(s) and ${form.maxQuestions} requested question(s).",
                "Searching Wikipedia and ranking likely article candidates.",
                "Fetching article content and checking evidence coverage for ${form.difficulty.label}.",
                "Asking Koog for the structured summary and quiz payload.",
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
                        text = "If this stalls locally, check your network and local OpenAI key setup, then try again.",
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

@Composable
private fun StagePlaceholderScreen(
    eyebrow: String,
    title: String,
    message: String,
    supportingNote: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = supportingNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
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

private fun snapshotText(form: StudyFormState): String {
    val topicCount =
        form.topicsText
            .split(',', '\n', ';')
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .count()

    val instructionsState = if (form.specificInstructions.isBlank()) "No extra instructions" else "Custom emphasis provided"
    return "${topicCount.coerceAtLeast(0)} topic(s), ${form.maxQuestions} question(s), ${form.difficulty.label}, $instructionsState."
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
