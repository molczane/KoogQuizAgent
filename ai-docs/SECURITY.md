# Security

## Core rule

The OpenAI API key must never be committed to the repository.

It must never be:

* committed to the repository
* stored in shared resources
* logged in plaintext

## Active mode: local direct execution

The current repository plan allows direct OpenAI access on both JVM and WasmJS for **local development only**.

This is acceptable only because the project is currently intended for local testing and multiplatform demo purposes.

This is **not** an acceptable production or public deployment security model for the web.

## Client requirements

### WasmJS

WasmJS may use a direct OpenAI key in the current local-only mode.

That means:

* the key can be exposed to the browser
* the mode must be treated as insecure
* the key must never be committed
* the app must clearly be understood as local-only when using this mode

Recommended dev-only approaches:

* manual runtime entry of the key
* ignored local config file not checked into git

Avoid:

* hardcoding the key in source
* committing the key in resources or config files intended for version control

### Desktop JVM

Desktop may use a direct OpenAI key in local development.

Recommended approach:

* read `OPENAI_API_KEY` from environment variables

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

If the project later adds secure web support, introduce a JVM Ktor backend and switch WasmJS to proxy mode.

That future backend should:

* read `OPENAI_API_KEY` from environment configuration
* expose a narrow proxy API
* use a model allowlist
* apply sensible timeouts
* sanitize logs
* return safe error payloads

Additional hardening worth keeping in mind:

* rate limiting
* authentication
* abuse protection
* server-side persistence
* stricter proxy contracts
