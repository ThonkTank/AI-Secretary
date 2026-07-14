# AI-Secretary

## What this project does

AI-Secretary is an Android app for planning everyday tasks and turning them into clear, checkable time slots. It helps users create tasks, choose when they prefer to do them, and keep track of progress over time. The app's goal is to make personal scheduling easier to understand and easier to follow day to day.

This project is a good fit for Android contributors working on UI, ViewModel/state flow, and Room persistence, as well as contributors interested in improving task scoring and scheduling behavior.

Current scope is split clearly: active product code lives in `src/`, legacy reference snapshots live in `.history/`, and release/process artifacts live in `ops/`.

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
2. After a successful build, because this repository's app is the root module, the APK is produced at:
   `build/outputs/apk/debug/AutoSecretary.apk`
   *(Optional troubleshooting: If the path differs by AGP version, run `find build/outputs -name '*.apk'` from repo root.)*

### Success criteria

Your first run is successful when the app starts and shows the task list screen with both:
- a **Generate** control, and
- a **New Task** control.

### If sync/build fails

Use the official Android setup guides to verify your environment (JDK, SDK, emulator/device, and command-line tools):
- https://developer.android.com/studio/install
- https://developer.android.com/tools

## Learn this first (public docs)

- [Android Studio installation and setup](https://developer.android.com/studio/install): ensures your local IDE, SDK components, and emulator/device workflow match how this Android app is built and run.
- [Guide to app architecture](https://developer.android.com/topic/architecture): explains the architecture patterns this repo follows for separating UI, state, and data responsibilities.
- [Lifecycle-aware components (including ViewModel)](https://developer.android.com/topic/libraries/architecture/lifecycle): clarifies how lifecycle + ViewModel behavior should drive state handling in fragments and activities.
- [Fragments overview](https://developer.android.com/guide/fragments): helps you understand the screen/navigation building blocks used throughout the app UI.
- [RecyclerView overview](https://developer.android.com/develop/ui/views/layout/recyclerview): covers list rendering patterns needed to reason about task list rows and item updates.
- [Room persistence library](https://developer.android.com/training/data-storage/room): provides the database fundamentals used by the repo's Room entities, DAOs, and persistence flow.
- [Gradle Wrapper basics](https://docs.gradle.org/current/userguide/gradle_wrapper.html): explains why this repo expects `./gradlew` for consistent Gradle versioning and reproducible builds.

## Repository map

- `src/` → active runtime application code.
- `.history/` → non-runtime historical snapshots and references.
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

### App entry point

- `src/main/java/com/autosecretary/app/MainActivity.java` (navigation host): start here to understand app entry and top-level screen wiring.

### Task feature (primary feature — read first)

- `src/main/java/com/autosecretary/features/task/ui/list/TaskListFragment.java` (main UI interactions): read next to see how task list actions are triggered from the primary screen.
- `src/main/java/com/autosecretary/features/task/ui/list/TaskViewModel.java` (state/filter/sort orchestration): this is where UI intents are converted into observable list state.
- `src/main/java/com/autosecretary/features/task/application/*UseCase*.java` (application boundary): follow these classes to see task operations coordinated between UI and domain services.
- `src/main/java/com/autosecretary/features/task/domain/scheduling/TaskSlotGenerator.java` (domain scheduling contract): this interface defines how task slot generation is expected to behave.
- `src/main/java/com/autosecretary/features/task/domain/internal/scheduling/DefaultTaskSlotGenerator.java` (default scheduler): this implementation contains the concrete scheduling flow.
- `src/main/java/com/autosecretary/features/task/domain/internal/scheduling/TaskScorer.java` (slot scoring heuristics): this class ranks candidate slots and drives ordering decisions.
- `src/main/java/com/autosecretary/features/task/data/*` (Room entities and DAO surface): inspect this package for persisted task models and database access definitions.

### Budget feature

- `src/main/java/com/autosecretary/features/budget/README.md`: start here for the budget feature reading path, covering transactions, import pipeline, recurring detection, and the balance chart.

### Meal feature

- `src/main/java/com/autosecretary/features/meal/README.md`: start here for the meal feature reading path, covering recipes, pantry, shopping lists, and household nutrition targets. Note: meal uses in-memory storage instead of Room.

### Database (shared infrastructure)

- `src/main/java/com/autosecretary/database/AppDatabase.java` (Room database root): see the central Room configuration that binds entities and DAOs.

## Build and release tasks

> [!WARNING]
> `copyToRelease` and `publishReleaseArtifact` have side effects:
> - `copyToRelease` copies the debug APK to `ops/release/` **and increments** `ops/release/version.txt`.
> - `publishReleaseArtifact` runs `copyToRelease` and then creates a GitHub Release containing `AutoSecretary.apk` and `version.txt`.
>
> **Safe local build command (no version bump, commit, or push):** `./gradlew assembleDebug`

- `./gradlew assembleDebug` only builds the debug APK (`AutoSecretary.apk`) and has no Git side effects.
- `./gradlew copyToRelease` copies the built debug APK to `ops/release/` and writes the next value to `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact` depends on `copyToRelease` and publishes those two files as the latest GitHub Release.

## First contribution (small safe change)

For your first PR, keep scope tiny and low-risk: update README text (like this section) or adjust one user-facing string in `src/main/res/values/strings.xml`.

Minimal validation in this repo:
- `./gradlew checkArchitecture`
- `./gradlew assembleDebug`

Done looks like:
- Build succeeds.
- App launches and basic navigation still works.
- No release-side-effect tasks were run.

> [!IMPORTANT]
> **Automated tests are not permitted in this project.** PRs that add unit tests, instrumented tests, or any test framework dependencies will be rejected. Validation is done manually — either by running the app or by using manual test scripts such as `test_schedule.sh`.

Reminder: for normal contributions, avoid `copyToRelease` and `publishReleaseArtifact`; see the warning in **Build and release tasks** above.

## Repository layout

- Active code lives under `src/`.
- Legacy snapshots are stored under `.history/`.
- **Legacy path semantics:** `.history/` is reference-only documentation/source history and is **not** an active source set for builds or runtime behavior.
