# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android application (`:app`) built with Kotlin and Jetpack Compose. Production code lives under `app/src/main/java/com/xty/englishhelper/`:

- `data/`: Room, Retrofit, sync logic, and repository implementations.
- `domain/`: models, repository contracts, use cases, and business engines.
- `ui/`: Compose screens, reusable components, navigation, and design tokens.
- `di/` and `util/`: Hilt modules and shared utilities.

Android resources and localized strings are in `app/src/main/res/`. JVM tests mirror production packages under `app/src/test/java/`; device tests and Room migration tests belong in `app/src/androidTest/java/`. Keep Room schema snapshots in `app/schemas/` and architecture notes in `docs/`.

## Build, Test, and Development Commands
Use Android Studio Hedgehog or newer, JDK 17, and Android SDK 35. Run commands from the repository root:

- `./gradlew assembleDebug` — build the debug APK.
- `./gradlew testDebugUnitTest` — run the fast JVM test suite.
- `./gradlew connectedDebugAndroidTest` — run device/emulator instrumentation tests.
- `./gradlew lintDebug` — run Android lint using `app/lint-baseline.xml`.

On Windows, replace `./gradlew` with `.\gradlew.bat`. Use Android Studio for deployment, Compose previews, and interactive debugging.

## Coding Style & Naming Conventions
Use four-space indentation, Kotlin idioms, and explicit null-safety. Keep functions focused and composables small. Preserve layer boundaries: domain code defines contracts, data code implements them, and UI code interacts through ViewModels/use cases. Follow established suffixes: `*Screen`, `*ViewModel`, `*UiState`, `*Repository`, `*RepositoryImpl`, `*UseCase`, and grouped `*UseCases`. Run lint before submitting; no separate formatter is configured.

## Testing Guidelines
Tests use JUnit 4, MockK, `kotlinx-coroutines-test`, and Room testing. Name classes with a `Test` suffix, for example `PlanProgressRulesTest`, and keep package paths aligned with source. Add focused coverage for changes to parsers, planners, repositories, use cases, and ViewModels. Database changes require migration tests plus refreshed schema snapshots. Run unit tests for every change and instrumentation tests for Room or Android-specific behavior.

## Commit & Pull Request Guidelines
Follow the repository's Conventional Commit style, such as `feat(scope): ...`, `fix(scope): ...`, `refactor: ...`, `test: ...`, or `docs: ...`. Keep each commit focused and buildable. Pull requests should summarize affected areas, link the relevant issue, report test/lint results, and include screenshots for UI changes. Call out schema, API, configuration, and migration impact explicitly.

## Security & Configuration
Do not commit `local.properties`, keystores, API keys, or credentials. Release signing reads `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` from the environment.
