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

## LLM participation model

The target workflow is not a free-form ReAct loop across the whole app.

Instead:

* validation stays deterministic
* topic normalization stays deterministic
* article selection stays deterministic
* evidence sufficiency stays deterministic
* the LLM is used for:
  * stage-limited tool calling during search
  * stage-limited tool calling during fetch
  * final structured payload generation

The model should be able to call tools, but only inside the stage that is explicitly designed for that tool set.

## Mandatory workflow

The workflow order is fixed:

1. validate input
2. use a search-stage LLM node/subgraph to call search tools
3. choose candidate articles deterministically
4. use a fetch-stage LLM node/subgraph to call fetch tools
5. evaluate evidence sufficiency
6. synthesize summary cards and quiz as structured output
7. return structured response

The workflow must not skip or reorder these steps.

## Recommended strategy graph

Use Koog strategy graphs to enforce the workflow. A suitable v1 graph is:

1. `validateInput`
2. `prepareQueries`
3. `searchWithLlmTools`
4. `selectArticles`
5. `fetchWithLlmTools`
6. `checkEvidence`
7. `generateStructuredPayload`
8. `finish`

Recommended Koog graph mechanics for the tool-calling stages:

* `subgraph(..., tools = ...)`
* `nodeLLMRequest` or `nodeLLMSendMessageOnlyCallingTools`
* `nodeExecuteTool`
* `nodeLLMSendToolResult`

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

### `searchWithLlmTools`

This is the planned search-stage behavior after the refactor:

* the LLM receives the prepared topics
* it may call only the search tool(s) configured for that subgraph
* it should call search once per topic unless a retry is explicitly justified by the prompt design
* tool results are captured and converted back into deterministic workflow state

### `selectArticles`

Selection should be deterministic where possible:

* prefer exact-title matches
* prefer non-disambiguation results
* keep `1-3` articles per topic

Avoid delegating basic ranking rules to the LLM if plain Kotlin can do it reliably.

### `fetchWithLlmTools`

This is the planned fetch-stage behavior after the refactor:

* the LLM receives the selected article titles
* it may call only the article fetch tool(s) configured for that subgraph
* Kotlin still controls deduplication and downstream evidence checks

This stage remains a good candidate for controlled parallelism later, but stage correctness matters more than parallelism for the first refactor.

### `checkEvidence`

If evidence is too weak:

* reduce question count, or
* return an `insufficient_sources` state

Never hallucinate to fill gaps.

### `generateStructuredPayload`

Use Koog structured output for the final payload.

No Wikipedia retrieval tools should be available in this stage.

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

Tool access must be limited by stage:

* search stage: search tool(s) only
* fetch stage: fetch tool(s) only
* final generation stage: no Wikipedia retrieval tools

## Prompting rules

The system prompt for generation should state:

* the assistant is a learning assistant
* it may only use retrieved Wikipedia evidence
* it must generate single-choice questions only
* it must not exceed `maxQuestions`
* it must attach source references to summaries and questions
* it must respect the output schema exactly

The system/task prompts for tool-calling stages should state:

* which tool(s) are available in this stage
* that the model must stay within the stage objective
* that it must not invent tool results
* when the stage should stop and hand control back to deterministic Kotlin logic

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
* one unrestricted tool loop that exposes every tool for the whole workflow
* multiple quiz types
* audience personas
* persistent history or accounts
