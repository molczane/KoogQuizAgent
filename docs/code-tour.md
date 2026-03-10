# Code Tour

This document is a workshop-oriented tour of the CyberWave codebase. It is meant to help participants answer four practical questions:

1. What is this app trying to do?
2. How is the project split across modules and layers?
3. Where does Koog fit into the flow?
4. Which files should I open first when I want to change behavior?

The repo is intentionally structured as a Kotlin Multiplatform app with most product logic in shared code. The UI is Compose Multiplatform, the agent workflow is Koog, research comes from Wikipedia, and the final UI is rendered from structured data rather than free-form text.

## What the app does

CyberWave is a learning app that:

* accepts a bounded study request from the user
* researches Wikipedia first
* generates short study notes
* generates a single-choice quiz
* returns a structured payload that the UI renders

The current user-facing targets are:

* Desktop JVM
* Web WasmJS

The current local provider options are:

* `OpenAI`
* `Ollama`

The current execution model is local workshop mode:

* JVM OpenAI reads `OPENAI_API_KEY` from the environment
* Wasm OpenAI reads the key from browser `localStorage`
* Ollama uses a fixed local host and fixed model configuration from shared code

## Technology stack

These are the main technologies in the current repo:

* Kotlin Multiplatform for shared application logic
* Compose Multiplatform for the UI
* Koog for agent execution, strategies, tools, and structured generation
* Ktor client for HTTP access to Wikipedia
* kotlinx.serialization for DTOs, domain payloads, and tool schemas
* coroutines for async orchestration

The central versions live in [../gradle/libs.versions.toml](../gradle/libs.versions.toml).

Important libraries to note:

* `ai.koog:koog-agents`
* `ai.koog:prompt-executor-llms-all`
* `io.ktor:ktor-client-core`
* `org.jetbrains.compose.*`

## Module overview

The project currently has two modules, defined in [../settings.gradle.kts](../settings.gradle.kts):

* `:shared`
* `:composeApp`

### `:shared`

This is the most important module for workshop participants. It contains almost all of the product behavior:

* domain models
* validation
* state reducers
* Koog strategy graph
* Koog tools
* Wikipedia client
* LLM gateway wiring
* application use cases
* tests

The `:shared` source set layout follows package-by-layer:

* `domain`
* `application`
* `agent`
* `data`
* `presentation`
* `observability`

That split is described in [../ai-docs/ARCHITECTURE.md](../ai-docs/ARCHITECTURE.md), and the code mostly follows it closely.

### `:composeApp`

This module owns the UI and target entry points:

* Compose screens
* app state holder
* runtime wiring
* JVM entry point
* WasmJS entry point
* platform-specific Ktor engine selection

The UI is intentionally thin. It does not own business rules, research policy, prompt construction, or quiz correctness rules.

## Architecture in one sentence

The architecture is:

* Compose UI collects a bounded request
* shared reducer validates and drives screen state
* a shared use case chooses a local LLM provider
* Koog runs a research-first strategy
* the LLM can call only stage-scoped Wikipedia tools
* deterministic Kotlin code selects evidence and validates results
* a final structured payload comes back to the UI

If you want the formal architecture baseline, read [../ai-docs/ARCHITECTURE.md](../ai-docs/ARCHITECTURE.md) next to this file.

## Recommended reading order

For workshop participants, this is the best reading order:

1. [../README.md](../README.md)
2. [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppScreen.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppScreen.kt)
3. [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppState.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppState.kt)
4. [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiStateReducer.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiStateReducer.kt)
5. [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/application/StudySessionUseCase.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/application/StudySessionUseCase.kt)
6. [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyGenerationService.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyGenerationService.kt)
7. [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyResearchWorkflow.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyResearchWorkflow.kt)
8. [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/tool/WikipediaTools.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/tool/WikipediaTools.kt)
9. [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/generation/StructuredStudyPayloadGenerator.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/generation/StructuredStudyPayloadGenerator.kt)
10. [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/wikipedia/MediaWikiWikipediaClient.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/wikipedia/MediaWikiWikipediaClient.kt)

That order follows the actual runtime path from screen interaction to agent execution.

## End-to-end request flow

This section is the most important part of the code tour.

### 1. The UI collects a bounded request

The main screen lives in [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppScreen.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppScreen.kt).

The input form exposes:

* topics text
* question count
* difficulty
* provider toggle
* optional specific instructions

This is already an intentional product decision. The app does not accept an unconstrained free-form “do whatever” prompt. Instead, it shapes the request into explicit controls and lets the agent operate inside those bounds.

### 2. The app state holder dispatches UI events

The bridge between Compose and shared logic is [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppState.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppState.kt).

This class:

* stores the current `StudyUiState`
* accepts `StudyUiEvent`
* delegates transitions to the shared reducer
* launches generation only when the reducer moves into `Loading`

This is a good file to show when explaining that the UI does not directly run business rules. It mostly coordinates lifecycle and coroutine execution.

### 3. Shared reducer logic decides screen transitions

The main reducer is [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiStateReducer.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiStateReducer.kt).

The state model is [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiState.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiState.kt).

This reducer handles:

* form edits
* validation-triggered submit
* loading state entry
* summary transition
* quiz progression
* results transition
* failure transition

Important architectural point:

* validation is shared logic
* quiz progression is shared logic
* failure handling is shared logic

That keeps behavior aligned across Desktop and WasmJS.

### 4. The shared use case selects the local LLM runtime

The application entry point is [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/application/StudySessionUseCase.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/application/StudySessionUseCase.kt).

This file is important because it is where platform configuration becomes product behavior.

It:

* opens the selected provider through `PlatformLocalLlmGateway`
* converts provider/configuration failures into structured screen models
* calls `StudyGenerationService`
* catches runtime failures and turns them into `GENERATION_ERROR`

This is also the best file to show when explaining why “errors are part of the product model.” The app does not just throw and crash. It turns failures into structured screens the UI can render.

### 5. Runtime wiring lives in the Compose module

The runtime assembly point is [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppRuntime.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppRuntime.kt).

This is where the app creates:

* platform `HttpClient`
* `MediaWikiWikipediaClient`
* `PlatformLocalLlmGateway`
* `ConsoleStudyWorkflowTracer`
* `StudySessionUseCase`

This file is useful in the workshop because it shows a clean dependency boundary:

* UI module wires concrete implementations
* shared module owns logic

### 6. Local LLM provider wiring is abstracted behind one gateway

Open [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/llm/PlatformLocalLlmGateway.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/llm/PlatformLocalLlmGateway.kt).

This class decides how the app opens:

* `OpenAI`
* `Ollama`

It returns:

* `Ready`
* `ConfigurationError`

That keeps provider-specific setup out of the UI and out of the workflow graph.

Important details:

* OpenAI uses `simpleOpenAIExecutor`
* Ollama uses `simpleOllamaAIExecutor`
* the gateway returns both a `PromptExecutor` and an `LLModel`

This is a good file to show when participants ask how Koog can run against multiple backends without rewriting the workflow.

### 7. Koog orchestration starts in `StudyGenerationService`

The next key file is [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyGenerationService.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyGenerationService.kt).

This service does two main things:

* runs the Koog research strategy
* if research succeeds, runs final structured payload generation

It creates:

* an `AIAgent`
* a strategy from `StudyResearchWorkflow`
* a `ToolRegistry`
* a `StructuredStudyPayloadGenerator`

This is one of the best files to show in the workshop, because it is where the high-level agent shell is visible without getting lost in node-level detail.

### 8. The research strategy is a real Koog graph

The strategy implementation is [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyResearchWorkflow.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyResearchWorkflow.kt).

This file demonstrates the most important Koog idea in the repo:

* the model is not running an unconstrained loop
* the graph enforces order
* tool visibility is limited by stage

At the top level, the graph does:

* validate input
* normalize the request
* enter the LLM-driven search subgraph
* select candidate articles deterministically
* enter the LLM-driven fetch subgraph
* assess evidence deterministically

Detailed diagram and explanation live in [study-research-workflow.md](./study-research-workflow.md).

### 9. Where the LLM actually acts

In the current implementation, the LLM acts in three places:

* inside the `searchWithLlmTools` subgraph
* inside the `fetchWithLlmTools` subgraph
* inside final structured payload generation

Inside the search subgraph, Koog uses:

* `nodeLLMRequestOnlyCallingTools`
* `nodeExecuteTool`
* `nodeLLMSendToolResult`

That means:

* the model chooses when to call `search_wikipedia`
* Koog executes the tool in Kotlin
* Koog sends the tool result back to the model

The fetch subgraph uses the same pattern for `fetch_wikipedia_article`.

After those stages, deterministic Kotlin code reclaims control and rebuilds stable state from tool outputs. This is a key design choice for reviewability and safety.

### 10. Wikipedia tools are ordinary Koog tools

The tool implementations live in [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/tool/WikipediaTools.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/tool/WikipediaTools.kt).

Current tools:

* `SearchWikipediaTool`
* `FetchWikipediaSummaryTool`
* `FetchWikipediaArticleTool`

Each tool defines:

* a stable tool name
* description for the model
* serializable argument schema
* serializable result schema
* the Kotlin `execute` implementation

This is a very good file for workshop participants who want to add their own tools. It shows the exact shape Koog expects.

It also includes tracer-backed logging, which helps demonstrate:

* whether the model called tools
* which tool it used
* how many times it was used
* what high-level result came back

### 11. Wikipedia access is plain Ktor client code

The main client is [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/wikipedia/MediaWikiWikipediaClient.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/wikipedia/MediaWikiWikipediaClient.kt).

This class talks directly to the MediaWiki API and implements:

* `search`
* `fetchArticleSummary`
* `fetchArticle`

Important detail for the workshop:

* Koog is not replacing regular Kotlin networking
* Koog orchestrates decision-making and tool use
* Ktor still performs the actual HTTP work

That distinction helps participants separate “agent framework” from “normal app infrastructure.”

### 12. Deterministic research policy protects the workflow

While the LLM can call tools during search and fetch stages, it does not decide everything.

The policy code in [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/application/research/WikipediaResearchPolicy.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/application/research/WikipediaResearchPolicy.kt) is responsible for:

* candidate ranking
* article selection
* evidence sufficiency
* question-count recommendations

This is an important part of the architecture:

* the LLM helps retrieve and synthesize
* deterministic code protects product rules

### 13. Final output is generated as structured data

The final generation step is [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/generation/StructuredStudyPayloadGenerator.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/generation/StructuredStudyPayloadGenerator.kt).

This file:

* builds the final generation prompt
* passes explicit source-backed evidence to the model
* calls `executeStructured<StudyScreenModel>()`
* sanitizes the returned payload

This is the file to show when explaining structured output in Koog.

Important design choice:

* the model does not generate Compose UI
* the model generates a `StudyScreenModel`
* Kotlin sanitizes the payload before the UI sees it

### 14. The UI renders structured states, not raw model text

The rendered model shape is centered around:

* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/model/StudyScreenModel.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/model/StudyScreenModel.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/SummaryCard.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/SummaryCard.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/QuizQuestion.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/QuizQuestion.kt)

The Compose screen file then renders:

* input
* loading
* summary
* quiz
* results
* failure

all from shared state and structured models.

## Deterministic code vs LLM-controlled code

This is one of the most important distinctions to explain during the workshop.

### Deterministic Kotlin owns

* form validation
* topic normalization
* screen-state transitions
* quiz session progression
* Wikipedia candidate selection
* evidence sufficiency checks
* payload sanitization
* provider configuration errors

### The LLM owns

* choosing when to call the stage-scoped search tool
* choosing when to call the stage-scoped fetch tool
* synthesizing final summary cards
* writing final quiz questions inside the schema

This split is deliberate. It keeps the agent useful while avoiding “the model controls everything.”

## Platform-specific seams

This app is KMP, but not everything is identical across targets.

### HTTP client engine

The expected platform HTTP factory is in:

* [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/platform/PlatformHttpClient.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/platform/PlatformHttpClient.kt)

Actual implementations:

* JVM: [../composeApp/src/jvmMain/kotlin/org/jetbrains/koog/cyberwave/platform/PlatformHttpClient.jvm.kt](../composeApp/src/jvmMain/kotlin/org/jetbrains/koog/cyberwave/platform/PlatformHttpClient.jvm.kt)
* WasmJS: [../composeApp/src/wasmJsMain/kotlin/org/jetbrains/koog/cyberwave/platform/PlatformHttpClient.wasmJs.kt](../composeApp/src/wasmJsMain/kotlin/org/jetbrains/koog/cyberwave/platform/PlatformHttpClient.wasmJs.kt)

Why this matters:

* JVM uses `CIO`
* WasmJS uses `Js`

That is an example of platform-specific infrastructure that must stay outside shared business logic.

### OpenAI key lookup

OpenAI configuration is abstracted through:

* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/ApiKeyProvider.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/ApiKeyProvider.kt)

Platform implementations:

* JVM: [../shared/src/jvmMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/JvmEnvironmentApiKeyProvider.kt](../shared/src/jvmMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/JvmEnvironmentApiKeyProvider.kt)
* WasmJS: [../shared/src/wasmJsMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/WasmBrowserApiKeyProvider.kt](../shared/src/wasmJsMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/WasmBrowserApiKeyProvider.kt)
* WasmJS local-storage rules: [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/BrowserLocalDirectApiKeyProvider.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/BrowserLocalDirectApiKeyProvider.kt)

This is a good place to discuss why browser secret handling is different from JVM secret handling.

### Entry points

Target entry points live in:

* JVM: [../composeApp/src/jvmMain/kotlin/org/jetbrains/koog/cyberwave/main.kt](../composeApp/src/jvmMain/kotlin/org/jetbrains/koog/cyberwave/main.kt)
* WasmJS: [../composeApp/src/wasmJsMain/kotlin/org/jetbrains/koog/cyberwave/main.kt](../composeApp/src/wasmJsMain/kotlin/org/jetbrains/koog/cyberwave/main.kt)

Web bootstrap assets live in:

* [../composeApp/src/wasmJsMain/resources/index.html](../composeApp/src/wasmJsMain/resources/index.html)
* [../composeApp/src/wasmJsMain/resources/styles.css](../composeApp/src/wasmJsMain/resources/styles.css)

## Important domain and contract files

These are the shared contracts worth opening early:

* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/StudyRequestInput.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/StudyRequestInput.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/ValidatedStudyRequest.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/ValidatedStudyRequest.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/Difficulty.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/Difficulty.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/LocalLlmProvider.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/LocalLlmProvider.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/ResearchSource.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/domain/model/ResearchSource.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/model/StudyScreenModel.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/model/StudyScreenModel.kt)

These files define the shape of the app more than any screen file does.

## Important observability files

If you want to demonstrate tool usage live, open:

* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/observability/StudyWorkflowTracer.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/observability/StudyWorkflowTracer.kt)

This tracer is used by:

* the use case
* the generation service
* the workflow graph
* the tools

That is why you can see both:

* stage-level traces
* per-tool execution traces

during a live run.

## Testing structure

The app has meaningful shared tests. The most useful areas to inspect are:

* reducer and UI-state tests in `shared/src/commonTest/.../presentation/state`
* parser and research-policy tests in `shared/src/commonTest/.../application`
* workflow tests in `shared/src/commonTest/.../agent/workflow`
* tool tests in `shared/src/commonTest/.../agent/tool`
* structured-output contract tests in `shared/src/commonTest/.../presentation/model`

The important teaching point is that the app does not rely only on manual prompting. Large parts of the agent behavior are covered by ordinary Kotlin tests.

## Best files to show in the workshop

If you have limited time, these are the highest-value files to demo live:

### For architecture

* [../ai-docs/ARCHITECTURE.md](../ai-docs/ARCHITECTURE.md)
* [../README.md](../README.md)

### For UI and app flow

* [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppScreen.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppScreen.kt)
* [../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppState.kt](../composeApp/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/ui/StudyAppState.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiStateReducer.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/presentation/state/StudyUiStateReducer.kt)

### For Koog and agent behavior

* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyGenerationService.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyGenerationService.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyResearchWorkflow.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyResearchWorkflow.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/tool/WikipediaTools.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/tool/WikipediaTools.kt)
* [study-research-workflow.md](./study-research-workflow.md)

### For data access and provider setup

* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/wikipedia/MediaWikiWikipediaClient.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/wikipedia/MediaWikiWikipediaClient.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/llm/PlatformLocalLlmGateway.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/llm/PlatformLocalLlmGateway.kt)
* [../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/BrowserLocalDirectApiKeyProvider.kt](../shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/data/openai/BrowserLocalDirectApiKeyProvider.kt)

## Good first modification tasks for participants

Once participants understand the tour, these are good first exercises:

* change the text of the system prompt in `StructuredStudyPayloadGenerator`
* add another tracer attribute to a Wikipedia tool
* tighten or loosen deterministic article selection in `WikipediaResearchPolicy`
* add another validation rule in `StudyRequestParser`
* refine the summary or results UI in `StudyAppScreen`
* add a new read-only tool and expose it only in one workflow stage

Those tasks are small enough to finish during a workshop but still reveal real architectural boundaries.

## Final mental model

If participants remember only one thing, it should be this:

* Compose collects input and renders state
* shared Kotlin owns product rules
* Koog owns the agent loop and tool-calling stages
* Ktor performs HTTP work
* structured models keep the UI stable

That is the core design of this repo.
