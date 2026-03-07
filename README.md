This is a Kotlin Multiplatform project targeting Desktop (JVM) and Web (WasmJS).

The active Phase 0 module layout is:

* [/shared](./shared/src) for non-UI shared logic and multiplatform contracts.
* [/composeApp](./composeApp/src) for Compose UI and platform entry points.

The current web execution mode is local-only direct OpenAI usage. Do not commit API keys.

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

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
