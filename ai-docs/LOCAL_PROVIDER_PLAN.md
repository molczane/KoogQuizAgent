# Local Provider Toggle Plan

## Purpose

Add a workshop-friendly runtime toggle that lets participants choose between:

* OpenAI with a local development API key
* Ollama with a locally running `OllamaModels.Meta.LLAMA_3_2` model

The goal is to broaden the demo without weakening the current workflow guarantees.

## Confirmed external assumptions

This plan assumes the currently documented local runtimes:

* Koog supports `simpleOpenAIExecutor(apiKey)` for OpenAI
* Koog supports `simpleOllamaAIExecutor(baseUrl)` for Ollama
* the target local Ollama model is `OllamaModels.Meta.LLAMA_3_2`
* Ollama uses its default local host unless a future requirement adds host configuration

## Chosen v1 behavior

The first implementation slice should support:

* `OpenAI`
  * JVM reads `OPENAI_API_KEY`
  * WasmJS reads the existing local-only browser config
* `Ollama`
  * JVM connects to the default local Ollama host
  * WasmJS connects to the default local Ollama host from the browser
  * no API key is required
  * the UI must clearly say that the local model must already be running

The first implementation slice should not support:

* custom Ollama host input
* custom Ollama model selection
* mixed-provider execution inside one generation
* secure/proxy web mode

## Design direction

### Shared provider model

Introduce a shared provider enum, for example:

* `LocalLlmProvider.OPENAI`
* `LocalLlmProvider.OLLAMA`

This provider should travel through:

* `StudyFormState`
* `StudyUiEvent`
* `StudyRequestInput`
* any application entry point that currently assumes OpenAI-only runtime wiring

### Gateway direction

Replace the OpenAI-specific runtime entry with a provider-aware local gateway.

Recommended shape:

* `PlatformLocalLlmGateway`
* returns `PromptExecutor` + `LLModel`
* chooses between:
  * OpenAI executor/model
  * Ollama executor/model

This keeps the research workflow and structured payload generation unchanged.

### Runtime config direction

The gateway should consume a provider-aware runtime config rather than an OpenAI-only key provider.

Recommended behavior:

* OpenAI path:
  * resolve key
  * keep existing error semantics for missing key / invalid Wasm mode
* Ollama path:
  * use fixed model `OllamaModels.Meta.LLAMA_3_2`
  * use default local host
  * fail with a clear runtime or configuration message if Ollama is not reachable

## UI direction

The input screen should gain a small provider selector near the top of the form.

Copy requirements:

* OpenAI option:
  * mention local API key requirement
* Ollama option:
  * explicitly mention that `llama3.2` must already be running locally
  * explicitly mention the default local Ollama host

The UI should not expose advanced runtime controls in the first slice.

## Error-model direction

The current error surface is OpenAI-centric.

The implementation should generalize messaging so the user sees:

* OpenAI setup errors when OpenAI is selected
* Ollama runtime guidance when Ollama is selected

Examples:

* missing OpenAI key
* invalid Wasm OpenAI local mode
* Ollama not reachable locally
* Ollama model not available locally

## Testing direction

The minimum regression set should cover:

* provider selection moving through reducer and use case entry
* OpenAI path unchanged
* Ollama path selecting the Ollama executor and model
* failure screens reflecting provider-specific setup/runtime problems
* UI helper text changing with the selected provider

## Recommended implementation order

1. `T045` shared provider model and form wiring
2. `T046` provider-aware local gateway
3. `T047` platform/runtime config handling
4. `T048` Compose input toggle and copy
5. `T049` tests and README/security updates
