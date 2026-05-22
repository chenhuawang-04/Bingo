# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app (`:app`) built with Kotlin and Jetpack Compose.

- `app/src/main/java/com/xty/englishhelper/`: production code by layer:
  - `data/` (Room, Retrofit, repository implementations, sync)
  - `domain/` (models, repository contracts, use cases, engines)
  - `ui/` (Compose screens/components, navigation, design tokens)
  - `di/` (Hilt modules), `util/` (shared utilities)
- `app/src/test/java/`: JVM unit tests (default fast feedback loop).
- `app/src/androidTest/java/`: instrumentation tests (including migration coverage).
- `app/schemas/`: Room schema snapshots; update when schema changes.
- `docs/`: architecture notes, design decisions, and developer docs.

## Build, Test, and Development Commands
Run from the repository root:

- `.\gradlew.bat assembleDebug`: build the debug APK.
- `.\gradlew.bat testDebugUnitTest`: run JVM unit tests in `app/src/test`.
- `.\gradlew.bat connectedDebugAndroidTest`: run instrumentation tests on a device/emulator.
- `.\gradlew.bat lintDebug`: run Android lint checks.

Use Android Studio for interactive debugging, Layout Inspector, and Compose previews.

## Coding Style & Naming Conventions
- Use Kotlin idioms and null-safety; prefer small, focused functions.
- Indentation: 4 spaces; keep code readable and composables simple.
- Respect layering (`data` -> `domain` -> `ui`); avoid cross-layer shortcuts.
- Naming patterns:
  - UI: `*ViewModel`, `*Screen`, `*UiState`
  - Domain contracts: `*Repository`
  - Data implementations: `*RepositoryImpl`
  - Business actions: `*UseCase` / grouped `*UseCases`

## Testing Guidelines
- Frameworks: JUnit4, MockK, `kotlinx-coroutines-test`, Room testing.
- Mirror source package paths under test directories.
- Test class names should end with `Test` (for example, `PlanProgressRulesTest`).
- Add tests for behavior changes in parsers, planners, repositories, and ViewModels.
- For DB migration/schema changes, add instrumentation migration tests and refresh `app/schemas`.

## Commit & Pull Request Guidelines
- Follow Conventional Commits used in history:
  - `feat(scope): ...`, `fix(scope): ...`, `refactor: ...`, `test: ...`, `docs: ...`
- Keep commits focused, buildable, and paired with relevant tests.
- PRs should include:
  - concise summary and affected modules
  - linked issue/task (if available)
  - test evidence (`testDebugUnitTest`, lint output, UI screenshots when applicable)
  - notes on schema/API/config changes and migration impact
