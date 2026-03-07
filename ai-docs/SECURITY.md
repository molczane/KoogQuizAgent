# Security

## Core rule

The OpenAI API key must exist only in the JVM Ktor backend.

It must never be:

* committed to the repository
* embedded in the Desktop app bundle
* embedded in the WasmJS bundle
* stored in shared resources
* logged in plaintext

## Backend requirements

The `:server` module must:

* read `OPENAI_API_KEY` from environment configuration
* expose a narrow proxy API
* use a model allowlist
* apply sensible timeouts
* sanitize logs
* return safe error payloads

The backend should not be a generic unrestricted pass-through if a narrower contract is enough.

## Client requirements

### WasmJS

WasmJS must always use `ProxyLlmGateway`.

There is no acceptable v1 case where the browser receives the OpenAI key.

### Desktop JVM

Desktop should default to `ProxyLlmGateway` too.

`DirectOpenAiGateway` is allowed only for local developer testing and must be:

* JVM-only
* opt-in
* clearly separated from default behavior

## Wikipedia access

Wikipedia access is public and does not require secrets for v1.

Still apply these rules:

* only call approved Wikipedia endpoints
* validate and normalize article titles
* handle missing or malformed responses safely

## Input handling

Treat `specificInstructions` as untrusted user input.

Apply basic constraints:

* trim whitespace
* cap maximum length
* never let it override core workflow rules

Treat `topicsText` the same way:

* normalize separators
* trim items
* discard empties
* cap topic count

## Logging rules

Do not log:

* API keys
* raw authorization headers
* full prompts in production by default
* full structured outputs if they contain sensitive debug details

Prefer logging:

* request IDs
* timing
* topic count
* selected model
* number of sources
* number of generated questions

## Failure handling

Use safe failure modes:

* validation error for bad form input
* insufficient sources for weak evidence
* temporary failure for network/LLM issues

Do not silently fall back to hallucinated content.

## Future hardening

Out of scope for v1, but worth keeping in mind:

* rate limiting
* authentication
* abuse protection
* server-side persistence
* stricter proxy contracts
