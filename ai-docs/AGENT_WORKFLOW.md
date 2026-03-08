# Agent Workflow

## Purpose

Define the exact v1 workflow that all implementation agents must preserve.

This workflow is intentionally narrow:

* Wikipedia only
* single-choice only
* structured output only
* form-based input only

## Input contract

### User-facing form input

* `topicsText: String`
* `maxQuestions: Int`
* `difficulty: Easy | Medium | Hard`
* `specificInstructions: String?`

### Validated workflow input

* `topics: List<String>`
* `maxQuestions: Int`
* `difficulty: Easy | Medium | Hard`
* `specificInstructions: String?`

Parsing of `topicsText` into `topics` is application logic, not LLM logic.

## Mandatory workflow

The workflow order is fixed:

1. validate input
2. search Wikipedia
3. choose candidate articles
4. fetch article content
5. evaluate evidence sufficiency
6. synthesize summary cards
7. generate quiz
8. return structured response

The workflow must not skip or reorder these steps.

## Recommended strategy graph

Use Koog strategy graphs to enforce the workflow. A suitable v1 graph is:

1. `validateInput`
2. `prepareQueries`
3. `searchWikipedia`
4. `selectArticles`
5. `fetchArticles`
6. `checkEvidence`
7. `generateStructuredPayload`
8. `finish`

## Node responsibilities

### `validateInput`

Reject or fail early when:

* there are no usable topics
* `maxQuestions` is out of allowed bounds
* difficulty is missing

This should return a user-facing validation state without touching the LLM.

### `prepareQueries`

Normalize topic strings:

* trim whitespace
* remove empty items
* deduplicate

### `searchWikipedia`

For each topic:

* run one search query
* consider top `3-5` results
* keep source metadata

### `selectArticles`

Selection should be deterministic where possible:

* prefer exact-title matches
* prefer non-disambiguation results
* keep `1-3` articles per topic

Avoid delegating basic ranking rules to the LLM if plain Kotlin can do it reliably.

### `fetchArticles`

Fetch selected article summaries/content.

This stage is a good candidate for controlled parallelism.

### `checkEvidence`

If evidence is too weak:

* reduce question count, or
* return an `insufficient_sources` state

Never hallucinate to fill gaps.

### `generateStructuredPayload`

Use Koog structured output for the final payload.

The generated payload must include:

* summary cards
* quiz questions
* source references
* final state

## Tooling guidance

Use class-based Koog tools only.

Likely v1 tool set:

* `SearchWikipediaTool`
* `FetchWikipediaSummaryTool`
* `FetchWikipediaArticleTool`

Tool descriptions must be explicit about:

* what they do
* when they are used
* what inputs they expect
* what they do not do
* likely failure conditions

## Prompting rules

The system prompt for generation should state:

* the assistant is a learning assistant
* it may only use retrieved Wikipedia evidence
* it must generate single-choice questions only
* it must not exceed `maxQuestions`
* it must attach source references to summaries and questions
* it must respect the output schema exactly

`specificInstructions` should be appended as a low-priority customization field.

It may influence:

* emphasis
* tone
* areas of focus

It must not change:

* source policy
* question type
* workflow order
* schema
* safety constraints

## Output contract

The agent must return a structured model that supports these UI states:

* input
* loading
* ready
* quiz_in_progress
* quiz_complete
* insufficient_sources
* generation_error
* validation_error

`generation_error` is reserved for application-level fallback handling when the local research or generation runtime fails before the agent can produce a valid payload.

The `ready` state must include:

* normalized topics
* summary cards
* sources
* quiz payload
* primary action label for `Start the quiz`

## Explicit non-goals for v1

Do not add:

* broad web search
* MCP
* multi-agent orchestration
* free-form tool loops
* multiple quiz types
* audience personas
* persistent history or accounts
