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

* `T001` `planned` - Create the target module layout: `:shared`, `:composeApp`, `:server`.
* `T002` `planned` - Remove unused plain JS target and keep Desktop JVM + WasmJS only.
* `T003` `planned` - Add Koog, Ktor, serialization, and test dependencies with centralized version management.
* `T004` `planned` - Add repository-local agent context files and keep them synchronized with implementation.

## Phase 1: Domain and contracts

* `T010` `planned` - Define domain models for study requests, difficulty, sources, summaries, quiz questions, and result states.
* `T011` `planned` - Implement parsing and validation for `topicsText`, `maxQuestions`, and `specificInstructions`.
* `T012` `planned` - Define the structured UI payload schema used by the app.
* `T013` `planned` - Define shared UI state models and state transitions.

## Phase 2: Wikipedia integration

* `T020` `planned` - Define `WikipediaClient` interfaces in shared code.
* `T021` `planned` - Implement Wikipedia search in shared code.
* `T022` `planned` - Implement summary/article fetch in shared code.
* `T023` `planned` - Implement deterministic article selection and evidence sufficiency rules.

## Phase 3: Agent workflow

* `T030` `planned` - Define class-based Koog tools for Wikipedia search and fetch operations.
* `T031` `planned` - Implement the strategy graph that enforces the research-first workflow.
* `T032` `planned` - Implement structured output generation for the final study-and-quiz payload.
* `T033` `planned` - Implement insufficient-source and validation-error paths.
* `T034` `planned` - Integrate `specificInstructions` as optional low-priority prompt context.

## Phase 4: Ktor backend

* `T040` `planned` - Create the `:server` Ktor module and basic configuration.
* `T041` `planned` - Implement the OpenAI proxy endpoint(s) and environment-based key loading.
* `T042` `planned` - Add timeout, retry, model allowlist, and safe logging behavior.
* `T043` `planned` - Implement the shared `ProxyLlmGateway` client against the Ktor API.
* `T044` `planned` - Add optional JVM-only `DirectOpenAiGateway` for local development.

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
* `T064` `planned` - Add regression checks that ensure no client artifact contains the OpenAI key.

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
* Does it avoid leaking the OpenAI key?
* Does it keep the UI driven by structured models?
* Does it include tests or explain the testing gap?
