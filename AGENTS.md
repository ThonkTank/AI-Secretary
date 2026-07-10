# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android app (no `app/` subdirectory). Active code lives in `src/main/`:
- `src/main/java/com/autosecretary/`: Java source, organized by feature (`features/task`, `features/budget`) and layer (`ui`, `application`, `domain`, `data`).
- `src/main/res/`: layouts, drawables, values, and widget resources.
- `src/main/AndroidManifest.xml`: app manifest.
- `/home/aaron/Schreibtisch/projects/references/`: global local-only third-party source mirror and readable extracts for source-backed decisions. It is ignored by Git and must not be staged or pushed unless the user explicitly approves a specific source file.

Reference-only legacy snapshots are in `.history/` and are not part of the runtime source set. Release artifacts and version metadata are in `ops/release/`.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root:
- `./gradlew checkArchitecture`: blocking architecture and repository-policy checks.
- `./gradlew assembleDebug`: safe local build; creates `build/outputs/apk/debug/AutoSecretary.apk`.
- `./gradlew installDebug`: installs debug build to a connected device/emulator.
- `./gradlew copyToRelease`: copies APK to `ops/release/` and increments `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact`: runs release copy plus Git add/commit/push. Use only for intentional release publishing.
- `./test_schedule.sh`: end-to-end manual scheduling validation via `adb` and log checks.

## Coding Style & Naming Conventions
Follow existing Java style in `src/main/java`: 4-space indentation, braces on declaration lines, and descriptive class names. Keep packages fully qualified under `com.autosecretary.*`.

Architecture-sensitive changes must keep `./gradlew checkArchitecture` green. That task is repo-local and enforces the import matrix, class reachability, DB-version documentation sync, executor ownership, application-layer naming, lifecycle, ViewModel, UI helper, package declaration, and release-task safety principles.

Work that uses external sources or local source evidence for decisions must use the global `source-references` skill and cite preserved local paths under `/home/aaron/Schreibtisch/projects/references/` or direct repo paths.

Work on agent-facing instruction artifacts (`AGENTS.md`, `SKILL.md`, prompts, or related rule markdown) must use the global `agent-instruction-engineering` skill.

## Testing Guidelines
Required behavior invariants must be covered by tight JVM end-to-end tests under `src/test`, as defined in `CLAUDE.md` and `docs/ARCHITECTURE_ROADMAP.md`. Use Robolectric plus in-memory Room for tests that drive UI ViewModel/DataService → application → domain → data and assert observable results.

Do not add instrumented/Espresso test suites under `src/androidTest` unless a future roadmap phase explicitly requires them.

Validation:
- Architecture check: `./gradlew checkArchitecture`
- Build check: `./gradlew assembleDebug`
- JVM behavior tests: `./gradlew testDebugUnitTest`
- Runtime check: launch app and verify core flows (task list, task creation/edit, schedule generation)
- Optional script: `./test_schedule.sh`

## Commit & Pull Request Guidelines
Recent history favors short, imperative commit messages, with occasional Conventional Commit prefixes (for example, `fix(build): ...`, `feat(ui): ...`). Keep subject lines specific and scoped.

For PRs:
- Describe behavior changes and touched areas (`features/task/...`, `features/budget/...`).
- Link related issues/tasks when applicable.
- Include manual validation steps and results.
- Add screenshots for UI-visible changes.
- Avoid running release side-effect tasks unless the PR is explicitly a release update.
