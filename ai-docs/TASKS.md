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
* `T032` `planned` - Implement structured output generation for the final study-and-quiz payload.
* `T033` `planned` - Implement insufficient-source and validation-error paths.
* `T034` `planned` - Integrate `specificInstructions` as optional low-priority prompt context.

## Phase 4: Direct OpenAI platform wiring

* `T040` `planned` - Define an `ApiKeyProvider` abstraction for platform-specific OpenAI key access.
* `T041` `planned` - Implement JVM key loading from local environment configuration.
* `T042` `planned` - Implement WasmJS dev-only key provision flow.
* `T043` `planned` - Implement shared `PlatformOpenAiGateway` that builds `simpleOpenAIExecutor(apiKey)`.
* `T044` `planned` - Add clear missing-key and invalid-mode error states in the UI/application layer.

## Phase 5: Compose UI

* `T050` `planned` - Build the input screen with topics, question count, difficulty, and specific instructions.
* `T051` `planned` - Build the researching/loading experience.
* `T052` `planned` - Build the summary screen with source list and `Start the quiz` action.
* `T053` `planned` - Build the single-choice quiz screen.
* `T054` `planned` - Build the quiz results screen and failure states.

## Phase 6: Quality and observability

* `T060` `planned` - Add unit tests for validation, topic parsing, selection rules, and scoring logic.
* `T061` `planned` - Add Koog graph tests using mock LLM/tool facilities.
* `T062` `planned` - Add contract tests for the structured output schema.
* `T063` `planned` - Add OpenTelemetry or equivalent tracing hooks suitable for Koog workflows.
* `T064` `planned` - Add regression checks that ensure no OpenAI key is committed and the local-only web mode is documented.

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
