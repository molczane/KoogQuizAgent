This is a Kotlin Multiplatform project targeting Desktop (JVM) and Web (WasmJS).

The active Phase 0 module layout is:

* [/shared](./shared/src) for non-UI shared logic and multiplatform contracts.
* [/composeApp](./composeApp/src) for Compose UI and platform entry points.

The current web execution mode is local-only direct OpenAI usage. Do not commit API keys. This mode is insecure for deployment and exists only for local demo/testing of Koog on WasmJS.

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
  ```

### Local-only WasmJS OpenAI setup

The browser build does not read `OPENAI_API_KEY` from IntelliJ or terminal environment variables at runtime. For the current local-only WasmJS mode, configure the browser directly through `localStorage`:

```js
localStorage.setItem("cyberwave.openai.mode", "local_direct")
localStorage.setItem("cyberwave.openai.apiKey", "YOUR_OPENAI_API_KEY")
location.reload()
```

To clear the local browser setup:

```js
localStorage.removeItem("cyberwave.openai.mode")
localStorage.removeItem("cyberwave.openai.apiKey")
```

This direct browser-key mode is local-only and insecure for deployment. If the app is ever deployed beyond local testing, WasmJS should switch to a server-backed proxy mode.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
