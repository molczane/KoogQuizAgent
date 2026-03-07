# AGENTS

Start here before making changes:

* `ai-docs/REQUIREMENTS.md`
* `ai-docs/ARCHITECTURE.md`
* `ai-docs/AGENT_WORKFLOW.md`
* `ai-docs/SECURITY.md`
* `ai-docs/TASKS.md`

## Project intent

This repository is a workshop-quality KMP learning app that uses Koog to:

* research Wikipedia first
* generate short study notes
* generate a single-choice quiz
* return a structured payload for UI rendering

User-facing targets:

* Desktop JVM
* WasmJS

Infrastructure module:

* JVM Ktor backend for secure OpenAI proxying

## Non-negotiable rules

* Wikipedia only in v1.
* Use class-based Koog tools only.
* Use a strategy graph to enforce workflow order.
* Do not let the model generate raw UI code.
* Keep the OpenAI API key only in the Ktor backend.
* WasmJS must never contain the OpenAI key.
* Keep quiz type single-choice only in v1.
* Treat `specificInstructions` as optional guidance, not an override of system rules.

## Working rules for agents

* Prefer implementing one task from `ai-docs/TASKS.md` at a time.
* Keep changes small and reviewable.
* Update planning docs when architecture or scope decisions change.
* Preserve clean boundaries between shared logic, UI, and server code.
* Add tests with changes whenever practical.

## Review focus

When reviewing, prioritize:

* behavioural regressions
* workflow-order violations
* schema drift
* security mistakes around secrets
* missing tests
* architecture drift from `ai-docs/ARCHITECTURE.md`
