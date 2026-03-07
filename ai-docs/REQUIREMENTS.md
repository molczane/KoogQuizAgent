## Product definition

**Workshop app:** a Koog-based (documentation in `koog-docs` folder) KMP learning assistant that lets the user provide:

* one or more topics
* the maximum number of questions
* difficulty level
* optional specific instructions

The app must research those topics on Wikipedia first, produce short study notes, and then generate a quiz with at most `X` single-choice questions. The same app should run on **Desktop JVM** and **WasmJS**. The UI is rendered from a structured response; the model does **not** generate raw UI code. Koog’s structured output features are a good fit for this because they return validated data structures from agent flows. ([Koog][1])

The intended user flow is:

1. fill in the learning form
2. run the agent
3. review the generated study summary and sources
4. press `Start the quiz`
5. answer the quiz and review results

## Scope decisions

For **v1**, these decisions are frozen:

* **Single agent, not multi-agent**
* **Wikipedia only**
* **No MCP in v1**
* **User-facing targets only:** Desktop JVM and WasmJS
* **Structured JSON output only**
* **Single-choice only**
* **Form-based input**, not free-form prompt parsing
* **Local direct OpenAI mode** on both JVM and WasmJS for development/testing

## Execution mode

The active implementation mode for this repository is:

* **`localDirect`** — the Koog agent runs directly on Desktop JVM and WasmJS using `simpleOpenAIExecutor(apiKey)`

In this mode:

* the shared workflow is the same on both platforms
* the `apiKey` is obtained differently per platform
* the WasmJS setup is acceptable for **local development only**
* this mode is **not** a secure deployment model for the web

A future hardening mode may be added later:

* **`secureProxy`** — a JVM Ktor backend hosts or proxies OpenAI access for web safely

## Architecture requirements

### 1. Module layout

The planned module split is:

* `:shared` — KMP shared business logic, agent strategy, domain models, state models, interfaces
* `:composeApp` — Compose Multiplatform UI shell and target entry points

An optional future module may be added later:

* `:server` — JVM-only Ktor backend for secure OpenAI access

### 2. Shared code

The following must live in `commonMain`:

* input models and validators
* agent prompt / strategy definition
* Wikipedia tool interfaces
* structured output models
* quiz/domain models
* UI state models
* application use cases

The shared agent flow should be identical on JVM and WasmJS.

### 3. Platform-specific code

`jvmMain` and `wasmJsMain` in shared/UI modules should only contain:

* app entry point
* HTTP engine wiring
* UI rendering glue
* platform configuration

### 4. LLM access

Define an `LlmGateway` abstraction with two implementations:

* `PlatformOpenAiGateway` — default, used by both JVM and WasmJS in `localDirect` mode
* `ProxyLlmGateway` — optional future implementation for `secureProxy` mode

`PlatformOpenAiGateway` should wrap `simpleOpenAIExecutor(apiKey)`.

The `apiKey` must be provided differently per platform:

* JVM: typically from `OPENAI_API_KEY`
* WasmJS: from a dev-only local source, such as runtime entry or ignored local config

For this workshop starter, `PlatformOpenAiGateway` is the default everywhere because the goal is to demonstrate Koog running directly on both platforms. If a secure web mode is added later, the `:server` module should use **Ktor**. ([OpenAI Platform][2])

### 5. Wikipedia access

Define a `WikipediaClient` abstraction in shared code. It should support:

* `searchTopics(query)`
* `fetchArticle(title)`
* `fetchArticleSummary(title)` or equivalent simplified content fetch

The search phase should use MediaWiki’s full-text search API. MediaWiki’s public APIs also support CORS for unauthenticated requests when `origin=*` is supplied, so the WasmJS target can call Wikipedia directly without extra keys. The MediaWiki REST API is also intended for app/script usage and returns JSON or HTML with a streamlined interface. ([MediaWiki][4])

## Agent behavior requirements

This is the core contract for the implementing agent.

### UI input

The user-facing form must contain:

* `topicsText: String`
* `maxQuestions: Int`
* `difficulty: Easy | Medium | Hard`
* `specificInstructions: String?`

`topicsText` is parsed by application code into `topics: List<String>` before the core workflow starts.

### Validated agent input

The agent receives:

* `topics: List<String>`
* `maxQuestions: Int`
* `difficulty: Easy | Medium | Hard`
* `specificInstructions: String?`

### Mandatory workflow

The agent must always follow this order:

1. validate the input
2. search Wikipedia
3. select candidate articles
4. fetch article content
5. synthesize study notes
6. generate quiz
7. return a structured response

### Hard rules

* The agent **must search first** before generating learning content.
* The agent must **not** rely on prior model knowledge when Wikipedia evidence is available.
* The agent must **not invent facts** when search is weak.
* If evidence is insufficient, the agent should reduce the number of questions or return an error state instead of hallucinating.
* `specificInstructions` may influence emphasis or style, but must **not** override workflow order, source policy, question limits, or output schema.
* The final response must be **JSON only** and must validate against the schema.

## Wikipedia research policy

To keep behavior predictable for participants, the retrieval policy is explicit:

* For each topic, run one Wikipedia search.
* Consider the top `3–5` results.
* Prefer non-disambiguation articles.
* Keep at most `1–3` selected articles per topic.
* Store source metadata: title, URL, short snippet/summary.
* Every summary card and every quiz question must reference at least one source.

Because `action=query&list=search` is a standard full-text search module and browser requests can be made cross-site with `origin=*`, this policy is feasible for both targets. ([MediaWiki][4])

## Quiz generation requirements

The quiz generator must be constrained hard enough that the output is always renderable.

### Required behavior

* Generate **at most `maxQuestions`** questions.
* Spread questions across the requested topics.
* Generate **single-choice** questions only in v1.
* Include:

    * prompt
    * options
    * correct answer
    * explanation
    * source references

### Quality bar

Each question must:

* be answerable from the retrieved Wikipedia material
* test understanding, not trivial string matching
* include a short explanation tied to the source article

## UI requirements

Do not let the model generate arbitrary UI code. The model should only return a fixed UI data model.

### Required form controls

The input screen must include:

* topics text input
* number-of-questions input
* difficulty selector
* optional specific-instructions text input
* primary action to start generation

### Supported UI sections

* `header`
* `topic_summary_card`
* `source_list`
* `quiz_question_card`
* `quiz_result_banner`
* `error_banner`
* `primary_action`

### Required screens/states

* input view
* loading / researching
* summary view
* quiz view
* results view
* failure / insufficient sources

That keeps the UI deterministic and easy to implement in both JVM and WasmJS.

## Structured output contract

The final response should look like this:

```json
{
  "screenTitle": "Learn: Kotlin Coroutines",
  "topics": ["Kotlin Coroutines"],
  "summaryCards": [
    {
      "title": "What coroutines are",
      "bullets": [
        "Coroutines support asynchronous, non-blocking code.",
        "They are lighter than OS threads.",
        "Suspending functions can pause without blocking a thread."
      ],
      "sourceRefs": ["src1"]
    }
  ],
  "quiz": {
    "maxQuestions": 4,
    "questions": [
      {
        "id": "q1",
        "type": "single_choice",
        "prompt": "What is a key property of a suspending function?",
        "options": [
          "It always creates a new thread",
          "It can pause without blocking a thread",
          "It only works on Android",
          "It cannot return values"
        ],
        "correctOptionIndex": 1,
        "explanation": "Suspending functions can suspend execution and resume later without blocking the underlying thread.",
        "sourceRefs": ["src1"]
      }
    ]
  },
  "sources": [
    {
      "id": "src1",
      "title": "Coroutine",
      "url": "https://en.wikipedia.org/..."
    }
  ],
  "state": "ready"
}
```

This is exactly the sort of response Koog’s structured output support is meant to produce and validate. ([Koog][5])

## Koog-specific implementation requirements

These requirements matter to whoever implements the app:

* Use **class-based Koog tools**, not annotation-based tools, in shared code, because annotation-based tools are JVM-only. ([Koog][6])
* Keep the agent definition and output models in shared KMP code.
* Use Koog structured output for the final screen model. ([Koog][5])
* Use a strategy graph to enforce the workflow order instead of relying on a free-form agent loop. ([Koog][1])
* Avoid MCP in this version, because Koog’s MCP integration is JVM-only and adds complexity you do not need for Wikipedia-only research. ([Koog][3])

## Workshop requirements

This version should also be optimized for participants:

* one OpenAI API key for local development/testing
* no OAuth
* no MCP setup
* same repository works for Desktop JVM and WasmJS
* visible “research first” flow so participants can understand the agent steps
* easy extension points:

    * change prompt wording
    * change search selection rules
    * change summary format
    * change quiz format
    * add richer difficulty logic later

## Acceptance criteria

Success is defined as follows:

1. The app runs on **Desktop JVM** and **WasmJS** from the same codebase. ([Koog][1])
2. The user enters topics, question count, difficulty, and optional specific instructions through form controls.
3. The agent always performs Wikipedia search before generating notes/questions. ([MediaWiki][4])
4. The final output validates against a strict schema. ([Koog][5])
5. The number of questions never exceeds `maxQuestions`.
6. Every question includes at least one source reference.
7. The repository never commits an OpenAI API key.
8. The WasmJS direct-key mode is clearly documented as **local-only / insecure for deployment**. ([OpenAI Platform][2])
9. The shared code uses multiplatform-safe tools, not JVM-only annotation tooling. ([Koog][6])

## Strongest recommendation

Freeze v1 at:

**one shared research-to-quiz agent + Wikipedia-only retrieval + structured UI payload + direct local OpenAI execution on JVM and WasmJS**

That is small enough to finish, stable enough for a workshop, and still demonstrates the interesting part: **one Koog-based KMP agent running on two targets, researching live information before generating a quiz**. A secure Ktor-backed web mode can be added later if needed. ([Koog][1])

[1]: https://docs.koog.ai/key-features/?utm_source=chatgpt.com "Key features - Koog"
[2]: https://platform.openai.com/docs/api-reference/introduction/how-openai-works?utm_source=chatgpt.com "API Reference - OpenAI API"
[3]: https://docs.koog.ai/model-context-protocol/?utm_source=chatgpt.com "Model Context Protocol - Koog"
[4]: https://www.mediawiki.org/wiki/API%3ASearch/en?utm_source=chatgpt.com "API:Search - MediaWiki"
[5]: https://docs.koog.ai/structured-output/?utm_source=chatgpt.com "Structured output - Koog"
[6]: https://docs.koog.ai/annotation-based-tools/?utm_source=chatgpt.com "Annotation-based tools - Koog"
