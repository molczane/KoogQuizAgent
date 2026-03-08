# Tasks

## Usage

This file is the planning backlog for implementation agents.

Rules:

* Work one task at a time.
* Keep tasks small enough for clean review.
* Do not start a later task if it depends on unresolved earlier decisions.
* When implementing, update task status in the same change if appropriate.

Status legend:

* `planned`
* `in_progress`
* `done`
* `blocked`

## Phase 0: Repo foundation

* `T001` `done` - Create the target module layout for v1: `:shared` and `:composeApp`.
* `T002` `done` - Remove unused plain JS target and keep Desktop JVM + WasmJS only.
* `T003` `done` - Add Koog, serialization, and test dependencies with centralized version management.
* `T004` `done` - Add repository-local agent context files and keep them synchronized with implementation.

## Phase 1: Domain and contracts

* `T010` `done` - Define domain models for study requests, difficulty, sources, summaries, quiz questions, and result states.
* `T011` `done` - Implement parsing and validation for `topicsText`, `maxQuestions`, and `specificInstructions`.
* `T012` `done` - Define the structured UI payload schema used by the app.
* `T013` `done` - Define shared UI state models and state transitions.

## Phase 2: Wikipedia integration

* `T020` `done` - Define `WikipediaClient` interfaces in shared code.
* `T021` `done` - Implement Wikipedia search in shared code.
* `T022` `done` - Implement summary/article fetch in shared code.
* `T023` `done` - Implement deterministic article selection and evidence sufficiency rules.

## Phase 3: Agent workflow

* `T030` `done` - Define class-based Koog tools for Wikipedia search and fetch operations.
* `T031` `done` - Implement the strategy graph that enforces the research-first workflow.
* `T032` `done` - Implement structured output generation for the final study-and-quiz payload.
* `T033` `done` - Implement insufficient-source and validation-error paths.
* `T034` `done` - Integrate `specificInstructions` as optional low-priority prompt context.

## Phase 3B: LLM-mediated tool orchestration refactor

* `T035` `done` - Design stage-scoped search and fetch subgraphs where the LLM can call tools but only from an explicit per-stage allowlist.
* `T036` `done` - Refactor the search stage so the LLM requests Wikipedia searches through Koog tool-calling nodes instead of direct Kotlin tool execution.
* `T037` `done` - Preserve deterministic article selection and evidence policies while converting tool-call results back into workflow state.
* `T038` `done` - Refactor the fetch stage so the LLM requests article fetches through Koog tool-calling nodes instead of direct Kotlin tool execution.
* `T039` `done` - Add graph and integration tests that prove tool routing, stage-limited tool access, and no retrieval-tool leakage into final payload generation.

## Phase 4: Direct OpenAI platform wiring

* `T040` `done` - Define an `ApiKeyProvider` abstraction for platform-specific OpenAI key access.
* `T041` `done` - Implement JVM key loading from local environment configuration.
* `T042` `done` - Implement WasmJS dev-only key provision flow.
* `T043` `done` - Implement shared `PlatformOpenAiGateway` that builds `simpleOpenAIExecutor(apiKey)`.
* `T044` `done` - Add clear missing-key and invalid-mode error states in the UI/application layer.

## Phase 4B: Local provider toggle and Ollama support

* `T045` `done` - Define a shared `LocalLlmProvider` model and extend the form/input state to carry provider selection.
* `T046` `done` - Replace the OpenAI-specific gateway with a provider-aware local LLM gateway that supports OpenAI and Ollama executors.
* `T047` `done` - Implement provider-specific local runtime configuration on JVM and WasmJS, keeping OpenAI key handling intact and Ollama on the default local host.
* `T048` `done` - Add the UI toggle and helper copy that explains OpenAI setup vs local Ollama setup, including the requirement to run the Ollama model locally.
* `T049` `done` - Add tests and docs coverage for provider selection, gateway behavior, and failure messaging.

## Phase 5: Compose UI
T
* `T050` `done` - Build the input screen with topics, question count, difficulty, and specific instructions.
* `T051` `done` - Build the researching/loading experience.
* `T052` `done` - Build the summary screen with source list and `Start the quiz` action.
* `T053` `done` - Build the single-choice quiz screen.
* `T054` `done` - Build the quiz results screen and failure states.

## Phase 6: Quality and observability

* `T060` `done` - Add unit tests for validation, topic parsing, selection rules, and scoring logic.
* `T061` `done` - Add Koog graph tests using mock LLM/tool facilities.
* `T062` `done` - Add contract tests for the structured output schema.
* `T063` `done` - Add OpenTelemetry or equivalent tracing hooks suitable for Koog workflows.
* `T064` `done` - Add regression checks that ensure no OpenAI key is committed and the local-only web mode is documented.

## Phase 7: Future hardening

* `T070` `planned` - Add an optional `:server` Ktor module for secure web proxy mode.
* `T071` `planned` - Implement `ProxyLlmGateway` against the Ktor API.
* `T072` `planned` - Switch WasmJS to proxy mode for deployable secure web usage.

## Recommended first implementation slice

The best first vertical slice is:

1. `T001`
2. `T002`
3. `T003`
4. `T010`
5. `T011`
6. `T012`

That creates a stable base before networking and agent work begin.

## Review checklist for every task

* Does the change match `ai-docs/REQUIREMENTS.md`?
* Does it preserve the shared workflow design?
* Does it avoid committing or hardcoding the OpenAI key?
* Does it keep the UI driven by structured models?
* Does it include tests or explain the testing gap?
