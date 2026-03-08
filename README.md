# CyberWave Workshop App

CyberWave is a Kotlin Multiplatform workshop app that demonstrates how to build a Koog-based learning agent that:

* researches Wikipedia first
* generates structured study notes
* generates a single-choice quiz
* runs from one shared codebase on Desktop JVM and Web WasmJS

The app is intentionally set up for local workshop use. The current WasmJS mode uses a direct OpenAI key in the browser, which is acceptable only for local demos and testing. It is not a secure deployment model.

## Workshop setup

Recommended local setup:

* IntelliJ IDEA
* Kotlin Multiplatform plugin enabled in IntelliJ
* JDK 17 or newer available to Gradle
* OpenAI API key for local testing

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

The desktop target reads the OpenAI key from the JVM environment.

### IntelliJ run configuration

Use the Gradle run configuration for the JVM target, typically `composeApp [jvm]`.

Set the environment variable in the IntelliJ run configuration:

```text
OPENAI_API_KEY=your_openai_api_key
```

Then run the JVM target from IntelliJ.

### Terminal

On macOS/Linux:

```shell
OPENAI_API_KEY=your_openai_api_key ./gradlew :composeApp:run
```

On Windows:

```powershell
$env:OPENAI_API_KEY="your_openai_api_key"
.\gradlew.bat :composeApp:run
```

## Run Web WasmJS

The browser target does not read IntelliJ or terminal environment variables at runtime. For WasmJS, the current local-only mode reads the OpenAI key from browser `localStorage`.

### IntelliJ run configuration

Use the Gradle run configuration for the web target, typically `composeApp [wasmJs]`.

Start the dev server from IntelliJ. After the browser opens, configure `localStorage` once for `http://localhost:8080`.

Open browser DevTools and run:

```js
localStorage.setItem("cyberwave.openai.mode", "local_direct")
localStorage.setItem("cyberwave.openai.apiKey", "YOUR_OPENAI_API_KEY")
location.reload()
```

This tells the app to use the local direct browser mode and provides the key for the current browser profile.

### Terminal

On macOS/Linux:

```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

On Windows:

```shell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

Then configure the browser with the same `localStorage` commands shown above.

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
* treat the current WasmJS direct-key mode as local-only
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
