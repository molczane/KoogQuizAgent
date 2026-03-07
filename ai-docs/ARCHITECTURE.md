# Architecture

## Goal

Build a clean, reviewable KMP application that:

* runs on Desktop JVM and WasmJS
* uses Koog in shared KMP code
* researches Wikipedia before generating content
* runs the Koog agent directly on both platforms in local development
* renders the UI from structured data only

## Planned module layout

### `:shared`

Kotlin Multiplatform module with the app's business logic and contracts.

Responsibilities:

* domain models
* input parsing and validation
* Koog strategy graph
* Koog tool definitions
* gateway interfaces
* Wikipedia client abstraction
* structured response models
* UI state models
* application use cases

### `:composeApp`

Compose Multiplatform UI module.

Responsibilities:

* form screen
* loading/researching screen
* summary screen
* quiz screen
* results screen
* target entry points for JVM and WasmJS

### Optional future `:server`

JVM-only Ktor backend.

Responsibilities:

* hold `OPENAI_API_KEY`
* expose a narrow LLM proxy API
* configure timeouts, retries, logging, and model allowlist
* never expose the API key to clients

This module is not part of the active v1 plan. It is a future hardening option.

## Boundary rules

### Shared layer

`commonMain` must own all business decisions:

* what input is valid
* when generation can start
* research policy
* evidence sufficiency rules
* quiz generation constraints
* structured output schema

### UI layer

The UI should be a thin adapter over shared state and use cases.

The UI must not:

* contain prompt logic
* contain Wikipedia selection logic
* contain quiz correctness logic
* construct ad hoc JSON payloads

### Future server layer

If a secure web mode is added later, the server should be responsible for secure OpenAI access only.

It should not:

* own the Wikipedia workflow
* render any UI
* contain product-specific quiz logic

## Source set expectations

### `commonMain`

Put these here:

* `StudyRequest`
* `Difficulty`
* `ValidatedStudyRequest`
* `ResearchSource`
* `SummaryCard`
* `QuizQuestion`
* `QuizPayload`
* `StudyScreenModel`
* `StudyUiState`
* validators
* state reducers
* Koog strategy and tool contracts

### `jvmMain`

Put these here only when platform-specific:

* HTTP engine wiring
* platform API key provider
* `PlatformOpenAiGateway` wiring
* desktop app entry

### `wasmJsMain`

Put these here only when platform-specific:

* HTTP engine wiring
* dev-only platform API key provider
* `PlatformOpenAiGateway` wiring
* wasm app entry

## Suggested package layout

Use package-by-layer inside `:shared`, not package-by-platform:

* `domain`
* `application`
* `agent`
* `data`
* `presentation`

Suggested meaning:

* `domain` = pure models and rules
* `application` = use cases and orchestration entry points
* `agent` = Koog strategy, prompts, tool descriptors, structured output contracts
* `data` = gateway interfaces and client implementations
* `presentation` = UI state and reducers, no Compose code

## Request flow

1. UI collects `topicsText`, `maxQuestions`, `difficulty`, and optional `specificInstructions`.
2. Shared application code parses and validates the form.
3. Shared Koog strategy runs:
   * search Wikipedia
   * select articles
   * fetch content
   * generate structured summary and quiz
4. LLM calls go through `PlatformOpenAiGateway` using a platform-provided `apiKey`.
5. Wikipedia calls go directly to MediaWiki from the shared client implementation.
6. Shared code returns a structured screen model.
7. UI renders summary and exposes `Start the quiz`.

## Key abstractions

### `LlmGateway`

Required implementations:

* `PlatformOpenAiGateway` for direct local execution on JVM and WasmJS
* `ProxyLlmGateway` as an optional future secure mode

### `WikipediaClient`

Required operations:

* search
* article summary fetch
* article content fetch

### `StudySessionUseCase`

Single application entry point for v1.

Input:

* validated request

Output:

* structured result or failure state

### `ApiKeyProvider`

Recommended platform abstraction for obtaining the OpenAI key.

Expected behavior:

* JVM provider reads from environment or local developer configuration
* WasmJS provider reads from a dev-only local source
* no implementation should commit secrets to the repository

## Architectural constraints

* Remove the plain `js` target if it is not used.
* Use class-based Koog tools only.
* Keep the final UI driven by structured models, never raw LLM text.
* Treat `specificInstructions` as optional prompt guidance, not a way to override system rules.
* Keep the agent workflow deterministic where possible.
* Prefer explicit nodes and transitions over large monolithic agent prompts.
* Treat WasmJS direct-key support as local-only and non-production.
