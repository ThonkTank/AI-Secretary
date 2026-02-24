# AI-Secretary

## Quick Start (first successful run)

### Prerequisites

- **Java 17 (JDK 17)**. This project is configured for Java 17 source and target compatibility.
- **Android SDK Platform 35** and **Android Build Tools 35.x** (matching `compileSdk = 35` / `targetSdk = 35`).
- **Use the included Gradle wrapper** (`./gradlew`) so you run the project with the expected Gradle version (**8.10.2**) automatically.

### Run path 1: Android Studio

1. Open Android Studio and choose **Open**, then select this repository folder.
2. Let Android Studio finish **Gradle sync** (it may prompt to install missing SDK components).
3. Start an emulator or connect an Android device with USB debugging enabled.
4. Click **Run** for the `app` configuration.

### Run path 2: Command line (CLI)

1. From the repository root, build a debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
2. After a successful build, the APK is produced at:
   `src/build/outputs/apk/debug/AutoSecretary.apk`

### Success criteria

Your first run is successful when the app starts and shows the task list screen with both:
- a **Generate** control, and
- a **New Task** control.

### If sync/build fails

Use the official Android setup guides to verify your environment (JDK, SDK, emulator/device, and command-line tools):
- https://developer.android.com/studio/install
- https://developer.android.com/tools

## Repository map

- `src/` → active runtime application code.
- `history/` → non-runtime historical snapshots and references.
- `ops/` → non-runtime operational release artifacts and metadata.

## Glossary

- **Task**: The main work item that stores title, schedule settings, and progress in code (`Task` / `TaskCore`). In the UI it is shown as each row title via `TaskTitle` and created from `+ Neue Task` (`NewTaskButton`).
- **Slot**: One concrete scheduled execution window of a task (`TaskSlot`, DB table `task_slots`). In the list row UI it maps to `StartTime` / `EndTime` (and completion via `TaskCheckBox`).
- **PrefSlot**: A preferred time pattern used by scheduling (`TaskPrefSlot`, DB table `task_pref_slots`). In the editor UI this is the `PrefSlotContainer` section (day/time pickers per preferred slot).
- **Repetition**: How often a task should repeat inside a time window (`TaskCore.Repetition`). In the editor UI this is controlled by `ToggleRepetition` and the `RepetitionContainer` fields.
- **Period**: The unit for repetition windows (day/week/month via enum `Period`). In the editor UI this maps to the period unit picker `EditPeriodUnit` (next to `EditPerPeriod`).
- **Streak**: Count of consecutive successful periods (`TaskCore.History.currentStreak`). In the task row UI it appears as `StreakDisplay` (e.g., `3x`).
- **Adaptive**: Flag to automatically adjust preferred times from real completions (`TaskCore.adaptive`). In the editor UI this is the checkbox `EditAdaptive` (label `Adaptive Zeiten`).
- **closeOnMiss**: Flag that closes tasks when deadline/period limits are missed (`TaskCore.closeOnMiss`). In the editor UI this is checkbox `EditCloseOnMiss` (label `Bei Überschreitung schließen`).
- **Manage mode**: List mode for editing/organizing tasks rather than quick checking. In the list toolbar UI this is the toggle button labeled `Manage` (`ManagementButton` in `TaskListToggle`).
- **Checklist mode**: List mode optimized for checking off planned items. In the list toolbar UI this is the toggle button labeled `Checklist` (`ChecklistButton` in `TaskListToggle`).

## Where to start reading

- `views/MainActivity.java` (navigation host): start here to see how top-level navigation and app entry flow are wired before diving into feature internals.
- `features/task/ui/ListFragment.java` (main UI interactions): read next to understand how users trigger task actions and how UI events are captured.
- `features/task/ui/TaskViewModel.java` (state/filter/sort orchestration): this shows how UI intents are translated into observable state, filtering, and sorting decisions.
- `features/task/application/*UseCase*.java` (application boundary): review these classes to see where business operations are coordinated between UI-facing logic and domain rules.
- `features/task/domain/SlotGenerator.java` and `TaskScorer.java` (scheduling logic): these files contain the core scheduling heuristics, so they explain why task ordering and slot assignment behave as they do.
- `features/task/data/*` + `database/AppDatabase.java` (Room persistence): finish here to understand how entities are stored, queried, and persisted through the Room database layer.

## Build and release tasks

> [!WARNING]
> `copyToRelease` and `publishReleaseArtifact` have side effects:
> - `copyToRelease` copies the debug APK to `ops/release/` **and increments** `ops/release/version.txt`.
> - `publishReleaseArtifact` runs `copyToRelease` and then `pushToGitHub`, which executes `git add ops/release/`, `git commit --allow-empty`, and `git push`.
>
> **Safe local build command (no version bump, commit, or push):** `./gradlew assembleDebug`

- `./gradlew assembleDebug` only builds the debug APK (`AutoSecretary.apk`) and has no Git side effects.
- `./gradlew copyToRelease` copies the built debug APK to `ops/release/` and writes the next value to `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact` depends on `copyToRelease` and `pushToGitHub`; Git push happens only when this task is run.

## Repository layout

- Active code lives under `src/`.
- Legacy snapshots are stored under `history/legacy/`.
- **Legacy path semantics:** `history/legacy/` is reference-only documentation/source history and is **not** an active source set for builds or runtime behavior.
