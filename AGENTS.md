# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android app (no `app/` subdirectory). Active code lives in `src/main/`:
- `src/main/java/com/autosecretary/`: Java source, organized by feature (`features/task`, `features/budget`) and layer (`ui`, `application`, `domain`, `data`).
- `src/main/res/`: layouts, drawables, values, and widget resources.
- `src/main/AndroidManifest.xml`: app manifest.

Reference-only legacy snapshots are in `history/legacy/` and are not part of the runtime source set. Release artifacts and version metadata are in `ops/release/`.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root:
- `./gradlew assembleDebug`: safe local build; creates `build/outputs/apk/debug/AutoSecretary.apk`.
- `./gradlew installDebug`: installs debug build to a connected device/emulator.
- `./gradlew copyToRelease`: copies APK to `ops/release/` and increments `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact`: runs release copy plus Git add/commit/push. Use only for intentional release publishing.
- `./test_schedule.sh`: end-to-end manual scheduling validation via `adb` and log checks.

## Coding Style & Naming Conventions
Follow existing Java style in `src/main/java`: 4-space indentation, braces on declaration lines, and descriptive class names. Keep packages fully qualified under `com.autosecretary.*`.

Feature layout convention for tasks: keep top-level folders as `ui`, `application`, `domain`, `data`; place implementation details under `internal/`.

Resource naming uses lowercase snake_case. Layout files follow `<feature>_<surface>_<kind>` (for example, `task_list_fragment.xml`, `task_row_item.xml`).

## Testing Guidelines
Automated tests are intentionally not used in this project. Do not add JUnit/instrumented test frameworks or `src/test` / `src/androidTest` suites.

Validation is manual:
- Build check: `./gradlew assembleDebug`
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
