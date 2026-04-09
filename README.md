# Koog Quiz Agent App

CyberWave is a Kotlin Multiplatform workshop app that demonstrates how to build a Koog-based learning agent that:

* researches Wikipedia first
* generates structured study notes
* generates a single-choice quiz
* lets you switch between OpenAI and a local Ollama model
* runs from one shared codebase on Desktop JVM and Web WasmJS

The app is intentionally set up for local workshop use. The current WasmJS OpenAI mode uses a direct key in the browser, which is acceptable only for local demos and testing. It is insecure for deployment and not a secure deployment model.

## Workshop setup

Recommended local setup:

* IntelliJ IDEA
* Kotlin Multiplatform plugin enabled in IntelliJ
* JDK 17 or newer available to Gradle
* OpenAI API key for the OpenAI path, or a local Ollama installation for the Ollama path

Project modules:

* [/shared](./shared/src) contains shared domain, workflow, Koog strategy, data access, and tests
* [/composeApp](./composeApp/src) contains the Compose Multiplatform UI and target entry points
* [/docs](./docs) contains workshop-facing diagrams and reference notes

## Open in IntelliJ

1. Open the repository root in IntelliJ IDEA.
2. Let Gradle import the project.
3. Make sure the Kotlin Multiplatform plugin is enabled.
4. Wait for indexing and Gradle sync to finish before running targets.

## Run Desktop JVM

Run the Gradle configuration for the JVM target, typically `composeApp [jvm]`. After the app starts, use the provider toggle in the request form:

* `OpenAI` uses your local OpenAI key
* `Ollama` uses `llama3.2` through the default local Ollama host at `http://localhost:11434`

### OpenAI on JVM

The desktop OpenAI path reads the key from the JVM environment.

In IntelliJ, set this in the `composeApp [jvm]` run configuration:

```text
OPENAI_API_KEY=your_openai_api_key
```

Or run from the terminal.

On macOS/Linux:

```shell
OPENAI_API_KEY=your_openai_api_key ./gradlew :composeApp:run
```

On Windows:

```powershell
$env:OPENAI_API_KEY="your_openai_api_key"
.\gradlew.bat :composeApp:run
```

### Ollama on JVM

The desktop Ollama path does not use an API key.

Before you generate a lesson with the `Ollama` toggle selected:

1. Install and start Ollama locally.
2. Run the model locally at least once:

```shell
ollama run llama3.2
```

3. Keep Ollama running on its default local host:

```text
http://localhost:11434
```

The app expects that host and the local `llama3.2` model. If Ollama is not running, or the model is missing, generation will fail with a local-runtime error.

## Run Web WasmJS

Run the Gradle configuration for the web target, typically `composeApp [wasmJs]`. After the app starts in the browser, choose the provider in the form UI.

The browser target does not read IntelliJ or terminal environment variables at runtime.

### OpenAI on WasmJS

The WasmJS OpenAI path reads the key from browser `localStorage` in local-only direct mode.

Start the dev server from IntelliJ. After the browser opens, configure `localStorage` once for `http://localhost:8080`:

1. Open browser DevTools.
2. Run:

```js
localStorage.setItem("cyberwave.openai.mode", "local_direct")
localStorage.setItem("cyberwave.openai.apiKey", "YOUR_OPENAI_API_KEY")
location.reload()
```

3. In the app UI, select `OpenAI`.

### Ollama on WasmJS

The WasmJS Ollama path does not use a browser key. It calls your local Ollama host directly from the browser.

Before you generate a lesson with the `Ollama` toggle selected:

1. Install and start Ollama locally.
2. Run the model locally at least once:

```shell
ollama run llama3.2
```

3. Make sure Ollama is running at:

```text
http://localhost:11434
```

4. In the app UI, select `Ollama`.

This is still local-only workshop mode. The browser must be able to reach your local Ollama host.

### Start the WasmJS dev server from the terminal

On macOS/Linux:

```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

On Windows:

```shell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

### Clear the browser setup

To remove the local WasmJS key from the browser:

```js
localStorage.removeItem("cyberwave.openai.mode")
localStorage.removeItem("cyberwave.openai.apiKey")
```

## Security note for participants

Important rules for the workshop:

* never commit an OpenAI API key
* do not hardcode the key in source files or resources
* treat the current WasmJS direct-key OpenAI mode as local-only
* local Ollama mode is for a machine where Ollama is already running
* do not present the current WasmJS mode as production-secure

More detail is in [SECURITY.md](./ai-docs/SECURITY.md).

## Koog strategy reference

The research-first strategy graph used by the app is documented in [docs/study-research-workflow.md](./docs/study-research-workflow.md).

That document includes a Mermaid diagram of the graph implemented in [StudyResearchWorkflow.kt](./shared/src/commonMain/kotlin/org/jetbrains/koog/cyberwave/agent/workflow/StudyResearchWorkflow.kt).

## Useful project docs

Start here if you are working on the repo during the workshop:

* [AGENTS.md](./AGENTS.md)
* [ai-docs/REQUIREMENTS.md](./ai-docs/REQUIREMENTS.md)
* [ai-docs/ARCHITECTURE.md](./ai-docs/ARCHITECTURE.md)
* [ai-docs/AGENT_WORKFLOW.md](./ai-docs/AGENT_WORKFLOW.md)
* [ai-docs/SECURITY.md](./ai-docs/SECURITY.md)
* [ai-docs/TASKS.md](./ai-docs/TASKS.md)
