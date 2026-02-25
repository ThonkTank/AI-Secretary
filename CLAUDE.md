# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> [!WARNING]
> `copyToRelease` and `publishReleaseArtifact` have side effects:
> - `copyToRelease` copies the debug APK to `ops/release/` **and increments** `ops/release/version.txt`.
> - `publishReleaseArtifact` runs `copyToRelease` and then `pushToGitHub`, which executes `git add ops/release/`, `git commit --allow-empty`, and `git push`.
>
> **Safe local build command (no version bump, commit, or push):** `./gradlew assembleDebug`

## Build Commands

- `./gradlew assembleDebug` only builds the debug APK (`AutoSecretary.apk`) and has no Git side effects.
- `./gradlew installDebug` builds the debug APK and installs it directly to a connected device/emulator.
- `./gradlew copyToRelease` copies the built debug APK to `ops/release/` and writes the next value to `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact` depends on `copyToRelease` and `pushToGitHub`; Git push happens only when this task is run.

**No automated tests — this is a strict project rule.** Automated tests (unit tests, instrumented tests, Robolectric, etc.) are not used in this project and must not be added. Do not write tests, do not suggest writing tests, and do not add any test framework dependencies to `build.gradle.kts`. PRs that introduce automated tests will be rejected. Validation is done manually via `./gradlew assembleDebug` and manual test scripts such as `ops/check_only.sh`.

## Quick Start

### Prerequisites

- **Java 17** — source and target compatibility are set to Java 17. Ensure your `JAVA_HOME` points to JDK 17.
- **Android SDK Platform 35** and **Android Build Tools 35.x** (required for `compileSdk = 35` / `targetSdk = 35`).
- **Gradle wrapper** — use `./gradlew` (not system Gradle); it's preconfigured for version 8.10.2.

### Verify it works

Build a debug APK locally (safe, no side effects):
```bash
./gradlew assembleDebug
```

Success looks like:
- Build completes without errors.
- APK appears at `build/outputs/apk/debug/AutoSecretary.apk`.
- App launches and shows the task list screen with a "Generieren" button and "+ Neue Task" button.

## Glossary

Quick reference for terminology used throughout the codebase:

- **Task**: Main work item (Java `Task` / `TaskCore`). Stores title, scheduling parameters, and progress. Created by "Neue Task" button.
- **Slot**: One concrete scheduled execution window of a task (Java `TaskSlot`, DB table `task_slots`). Displayed as start/end times in list rows.
- **PrefSlot**: Preferred day/time pattern for scheduling (Java `TaskPrefSlot`, DB table `task_pref_slots`). Set via the editor's day picker + time picker section.
- **Repetition**: How often a task repeats in a period window (Java `TaskCore.Repetition`). Controlled by "Wiederholung" fields in the editor.
- **Period**: Unit of repetition window — day, week, or month (Java enum `Period`). Selected via `EditPeriodUnit` dropdown.
- **Streak**: Count of consecutive successful periods (Java `TaskCore.History.currentStreak`). Displayed in rows as `3x`.
- **Adaptive**: Flag to auto-adjust preferred times from real completion times (Java `TaskCore.adaptive`). Checkbox "Adaptive Zeiten" in editor.
- **closeOnMiss**: Flag to close tasks when deadline/period limits are missed (Java `TaskCore.closeOnMiss`). Checkbox "Bei Überschreitung schließen" in editor.
- **Checklist mode**: List view optimized for quick checking off (filtered to today, scheduled slots only, sorted by time).
- **Manage mode**: List view optimized for editing/organizing (filtered to today, includes unscheduled tasks, grouped by task hierarchy, sorted by title).

## Project Layout

Standard Android project structure (single module, no `app/` directory):

| Path | Purpose |
|------|---------|
| `src/main/java/com/autosecretary/` | Active Java source |
| `src/main/res/` | Android resources |
| `src/main/AndroidManifest.xml` | Manifest |
| `history/legacy/` | Legacy snapshots (reference-only, not part of active source set) |
| `history/old/` | Older archived code (meal/nutrition tabs, services) |
| `build.gradle.kts` | Single-module Kotlin DSL build |
| `ops/release/` | Built APK + version counter |
| `ops/test_schedule.sh`, `ops/check_only.sh` | Manual validation scripts |

All packages are fully qualified under `com.autosecretary.*`.

### Feature package layout rule (`features/task`)

- Keep public entry points in stable packages:
  - `features/task/ui/ListFragment`
  - `features/task/ui/TaskViewModelFactory`
  - `features/task/application/*UseCase`
- Move non-entry helpers to internal packages:
  - `features/task/internal/...`, or
  - `features/task/{domain,ui}/internal/...`
- Prioritize high-churn helpers (mappers/builders) when migrating classes.

### Layout resource naming rule (`src/main/res/layout/`)

Use the pattern `<feature>_<surface>_<kind>` for all layout file names. Segments are lowercase snake_case, with `kind` being one of: `activity`, `fragment`, `item`, or `widget`.

Examples: `app_main_activity.xml`, `task_list_fragment.xml`, `task_row_item.xml`, `task_list_widget.xml`.

## Architecture

**MVVM with Room** — feature-based package structure with clean layering (UI → Application → Domain → Data).

```
app/                            → MainActivity (fragment host), AppCompositionRoot (DI wiring), AutoSecretaryApplication
features/task/ui/               → ListFragment, TaskViewModel, ListRowAdapter, TaskEditDialog,
                                   TaskEditPresenter, PrefSlotUIBuilder, TaskViewModelFactory, TaskEditSessionController
features/task/ui/state/         → TaskEditState, PrefSlotEditState, ViewSlotList (mutable UI + presentation models)
features/task/ui/edit/          → TaskEditDialog, TaskEditPresenter
features/task/ui/edit/internal/ → TaskEditStateMapper, TaskEditFormValidator, TaskEditSectionBinder, PrefSlotUIBuilder
features/task/application/      → TaskAsyncDataService, CheckOffTaskUseCase, RegenerateScheduleUseCase,
                                   TaskListItem (flat read-only display model), TaskListItemMapper (Task → TaskListItem)
features/task/data/             → Task, TaskCore, TaskSlot, TaskPrefSlot, TaskPrefSlotFactory,
                                   TaskPrerequisite, TaskRelation, TaskDAO, TaskSeedDataFactory
features/task/domain/           → TaskSlotGenerator (interface), TaskLifecycleManager,
                                   TaskCompletionService, TaskTreeOperations, TaskPlanningState
features/task/domain/internal/scheduling/ → DefaultTaskSlotGenerator (implementation), TaskScorer (scoring engine)
database/                       → AppDatabase, Converters
config/                         → Preferences (SharedPreferences wrappers)
shared/                         → Period, Priority (shared domain enums)
util/                           → TreeBuilder<T> (generic tree build/flatten/sort)
```

All paths above are relative to `src/main/java/com/autosecretary/`.

### Dependency Injection

`AppCompositionRoot` (in `app/`) is the manual DI root. It wires the full dependency graph:

```
AppDatabase → TaskDAO → TaskAsyncDataService, CheckOffTaskUseCase, RegenerateScheduleUseCase
TaskLifecycleManager → TaskCompletionService, TaskScorer → TaskSlotGenerator (DefaultTaskSlotGenerator)
TaskListItemMapper → TaskAsyncDataService
All → TaskViewModelFactory → TaskViewModel
```

Created fresh in `ListFragment.onViewCreated()`. The single-threaded `ExecutorService` is created here with an `UncaughtExceptionHandler` that logs to `Log.e("TaskUseCase", ...)`.

`TaskPlanningState` is created by `TaskSlotGenerator.createPlanningState()` and passed through the multi-day generation loop to track which tasks have been scheduled across days, ensuring intelligent distribution across the week (no stale midnight issue).

### Navigation

`MainActivity` hosts a `FragmentContainerView` + `BottomNavigationView` with two tabs:
- **Tasks** (`tab_schedule`) → `ListFragment`
- **Budget** (`tab_manage`) → `BudgetFragment`

`ListFragment` has an internal `MaterialButtonToggleGroup` that switches between two display modes:
- **Checklist** — filtered to today, only scheduled slots, sorted by time
- **Manage** — filtered to today, includes unscheduled tasks, grouped by task-parent tree, sorted by title

Both modes always filter to `LocalDate.now()` — there is no UI to view a different day.

Fragment swapping via `getSupportFragmentManager().beginTransaction().replace().commit()`. `TaskViewModel` is scoped to the Activity (`requireActivity()`) so it's shared across fragments.

**Task creation:** `ListFragment` has a "+ Neue Task" button (`NewTaskButton`) that calls `vm.createNewTask()` — which initializes a blank `Task` with a default `TaskPrefSlot` (start 06:00, all days from `TaskPrefSlotFactory`), maps it to `TaskEditState`, sets `selectedTask` and `selectedBaseTask`, and sets `isNewTask = true` — then opens `TaskEditDialog` with tag "create".

**Task editing:** Long-press on a list row calls `vm.beginEditTask(taskId)` — which does an async DB read via `TaskAsyncDataService.loadTask()`, maps the `Task` to `TaskEditState`, and posts both `selectedBaseTask` (raw `Task`) and `selectedTask` (edit model) — then opens `TaskEditDialog`.

**TaskEditDialog** works with `TaskEditState` (a mutable UI POJO), not the `Task` directly. A `TaskEditPresenter` handles form logic (repetition↔prefSlots reactivity, form collection, validation). On save: `collectAllFields()` → `presenter.applyForm(input)` → `presenter.toTaskForSave(vm.requireSelectedBaseTask())` produces the `Task` to persist → `vm.saveEditedTask(mappedTask)`.

The dialog has five sections: basic info (title, description, priority), scheduling (deadline with date picker + clear button, closeOnMiss, min/max duration, cooldown), repetition (toggle + reps/perPeriod/periodUnit), prefSlots (dynamically built via `PrefSlotUIBuilder` with day picker + time picker) + adaptive checkbox, and progress (toggle + unit/target/current/resetPerRep/min-maxPerRep).

**Repetition↔PrefSlots reactivity:** Changing repetition fields triggers `presenter.onRepetitionChanged()` which recalculates `repsPerDay` and adds/removes `PrefSlotEditState` entries to match, then rebuilds the prefSlot UI via `PrefSlotUIBuilder`. The day picker disables days already taken by other prefSlots in the same repetition group.

### Data flow

```
TaskViewModel constructor:
  → refreshList() → taskAsyncDataService.loadAllMapped(callback)
      → executor: taskDao.readAll() → TaskListItemMapper.map() → List<TaskListItem>
      → callback: masterList.fromList(items) → filterList() → sortList()
      → displayList.postValue() → adapter.setList() → RecyclerView redraws

ListFragment "Generieren" button → vm.updateList():
  → regenerateScheduleUseCase.execute(onDone)
      → executor: taskDao.readAll()
      → generator.createPlanningState() + multi-day loop:
         - generator.recordPreservedSlots() to track cross-day scheduling
         - for each day: generator.generateSlotsForDay(), generator.recordScheduledSlotsForDay()
      → taskDao.writeList(scheduledTasks)
      → onDone → refreshList() (same as above)

ListFragment toggle switch → vm.applyChecklistPreset() / vm.applyManagePreset():
  → sets day + activeListConfig (enum: CHECKLIST / MANAGE)
  → filterList() → builds predicate per config → masterList.filter(predicate)
  → sortList() → builds comparator per config → masterList.sort(groupByTaskParent, comparator)
  → displayList.postValue() → adapter.setList()

ListFragment checkbox → vm.checkOff(viewSlot):
  → checkOffTaskUseCase.execute(viewSlot.item, onChanged)
      → executor: taskDao.read(taskId) → find slot by slotId
      → completionService.checkOff(task, slot, lifecycleManager) → CompletionPhase
      Phase 1 (STARTED): slot.realStart = now → writeSlot only → green in-progress background
      Phase 2 (COMPLETED): slot.realEnd = now, slot.completed = true
          → lifecycleManager.updateStreak(task, slot)
          → task.recordCompletion(duration, trackDuration)
          → if trackDuration && task.core.adaptive: lifecycleManager.adaptPrefSlot(task, slot)
          → taskDao.write(task) + taskDao.writeSlot(slot)
      → onChanged → refreshList()

ListFragment long press → vm.beginEditTask(viewSlot.item.taskId):
  → taskAsyncDataService.loadTask(taskId, callback)
      → callback: selectedBaseTask.postValue(task), selectedTask.postValue(mapper.fromTask(task))
  → TaskEditDialog.show()
  → dialog reads TaskEditState fields, user edits via TaskEditPresenter
  → "Speichern" → collectAllFields() → presenter.applyForm(input)
      → presenter.toTaskForSave(vm.requireSelectedBaseTask()) → Task
      → vm.saveEditedTask(mappedTask) → taskAsyncDataService.saveTask(task, onSaved)
          → executor: taskDao.write(task) → onSaved: isNewTask=false, refreshList()
```

**Threading:** All DB access runs on a single-threaded `ExecutorService` created in `AppCompositionRoot`. Callbacks post results that trigger `filterList()`/`sortList()` and ultimately `displayList.postValue()`.

DB seeding: `RegenerateScheduleUseCase` auto-seeds from `TaskSeedDataFactory.createDefaultTasks()` when the DB is empty (first "Generieren" after fresh install). Seeds are flattened via `TaskTreeOperations.flatten()` before writing, then re-read to get proper Room `@Relation` assembly. This is temporary scaffolding.

`TaskDAO` has two write methods:
- `writeList(List<Task>)` — 2-pass bulk write: inserts all `TaskCore` rows first, then writes slots/prefSlots/prerequisites/relations for each task. Expects a pre-flattened list (flattening is done by caller via `TaskTreeOperations.flatten()`). Used by `RegenerateScheduleUseCase`.
- `write(Task)` — single-task upsert. Used by `saveEditedTask()` and `CheckOffTaskUseCase`.

### Task model

`Task` is a Room POJO (not a `@Entity`). Room assembles it via `@Embedded` + `@Relation` from five actual database tables (TaskCore, TaskSlot, TaskRelation, TaskPrefSlot, TaskPrerequisite). All entities use `String id = UUID.randomUUID().toString()` as their `@PrimaryKey`. All FK/reference fields are also `String` (UUID):

| Class | Table | Role |
|-------|-------|------|
| `TaskCore` | `task_core` | One row per task — title, scheduling params, embedded sub-objects |
| `TaskSlot` | `task_slots` | Scheduled/completed time blocks. FK `taskId` → `task_core.id`. Has `parent` for parent-child slot hierarchy, `score`, `scheduled`/`completed` booleans, `realStart`/`realEnd` for actual execution tracking |
| `TaskRelation` | `task_relation` | Parent-child links between tasks. FK `child` → `task_core.id`. `child` and `parent` columns point to `task_core.id` |
| `TaskPrefSlot` | `task_pref_slots` | Preferred days/time. FK `taskId` → `task_core.id`. `days` is `Set<DayOfWeek>` (stored as comma-joined string via TypeConverter). Default created by `TaskPrefSlotFactory.createDefault()` |
| `TaskPrerequisite` | `task_prerequisites` | Task dependencies. FK `taskId` → `task_core.id`. `prerequisiteId` references another `task_core.id`. SlotGenerator skips tasks whose prerequisites aren't yet scheduled/completed today |

`TaskCore` uses `@Embedded` for three static inner classes (`Repetition`, `Progress`, `History`) — their fields are flattened into `task_core` columns with prefixes (`repetition_`, `progress_`, `history_`). Additional fields: `description` (String), `adaptive` (boolean, for prefTime user-behavior adaptation), `completed` (boolean, default false). Defaults: `cooldown = 1`, `minDuration = 5`, `maxDuration = 10`, `priority = MEDIUM`, `closeOnMiss = true`, `created = LocalDate.now()`.

`History` tracks: `completions`, `trackedCompletions`, `currentStreak`, `nrStreaks` (default 1), `totalDuration`, plus derived `averageStreak()` and `averageDuration()`. `trackedCompletions` and `totalDuration` only increment for non-quick-tap, non-stale completions (see checkOff above).

`Progress` tracks: `unit`, `target`, `current`, `resetPerRep`, `minPerRep`, `maxPerRep`, `totalProgress`, `totalTime` (default 10), plus derived `repsRequired()`, `timePerProgress()`, `requiredTimePerRep()`.

Parent-child relationship: `TaskRelation` entity links tasks via `child`/`parent` columns. `TaskTreeOperations.buildTree()` uses a `TreeBuilder<Task>` that reads `task.parents` (a `@Relation` list with `entityColumn = "child"`) and builds the in-memory tree. `TaskTreeOperations.flatten()` does the inverse.

`TaskSlot` also has its own tree structure: `TaskSlot.parent` (String) + `TaskSlot.buildTree()` builds a slot hierarchy. `TaskSlot.children` is an `@Ignore` field initialized to `new ArrayList<>()`.

**Orphan safety:** All `buildTree()` implementations (via `TreeBuilder<T>`) handle missing parent references by treating orphaned items as roots rather than crashing.

### TaskListItem (application-layer read model)

`TaskListItem` is a flat, immutable display model created by `TaskListItemMapper` from `Task`/`TaskSlot`. It holds pre-extracted fields: `taskId`, `slotId`, `slotParentId`, `parentTaskIds`, `title`, `day`, `start`, `end`, `deadline`, `streak`, `score`, `completed`, `inProgress`.

Has `DeadlineUrgency` enum (`NONE`, `OVERDUE`, `TODAY`, `SOON`, `FUTURE`) with `deadlineUrgency()` method used by `ListRowAdapter` for color-coded deadline display.

### ViewSlotList (presentation model)

`ViewSlotList` is the presentation layer between `TaskListItem` data and the RecyclerView. It holds two lists: `viewSlots` (master, all data) and `displaySlots` (filtered/sorted subset sent to UI).

`ViewSlot` is a static nested class with fields: `TaskListItem item`, `int depth`, private `List<ViewSlot> children`. One ViewSlot per TaskListItem.

Processing pipeline:
1. `fromList(List<TaskListItem>)` — builds flat `viewSlots`
2. `filter(predicate)` — applies Predicate to `viewSlots`, writes matching items to `displaySlots`
3. `sort(byTaskRelation, comparator)` — three phases via `TreeBuilder<ViewSlot>`:
   - `buildTree()` — groups `displaySlots` into parent-child hierarchy (by task-parent or slot-parent depending on `byTaskRelation` flag)
   - `sortTree()` — recursively sorts siblings at each level using the comparator
   - `flattenWithDepth()` — DFS traversal back to flat list, setting `depth` for UI indentation

### TaskViewModel state

`TaskViewModel` manages:
- `masterList: ViewSlotList` — in-memory copy of all task list items
- `displayList: MutableLiveData<List<ViewSlot>>` — filtered/sorted output observed by adapter
- `selectedTask: MutableLiveData<TaskEditState>` — current edit state for TaskEditDialog
- `selectedBaseTask: MutableLiveData<Task>` — raw Task used as base for `toTaskForSave()`
- `isNewTask: MutableLiveData<Boolean>` — controls dialog title ("Task erstellen" vs "Task bearbeiten")
- `activeListConfig: ListConfig` — private enum (`CHECKLIST` / `MANAGE`), no separate Filters/Sorters classes

### Scoring algorithm

**Scoring lives in `TaskScorer`** (in `features/task/domain/internal/scheduling/`). It holds a `Map<String, TaskScoringSnapshot>` keyed by task ID. `TaskScorer` is constructed with a `TaskLifecycleManager` dependency. The dependency graph is wired in `AppCompositionRoot`: `TaskLifecycleManager` → `TaskScorer` → `TaskSlotGenerator` (DefaultTaskSlotGenerator).

**Maintenance + caching:** `TaskScorer.maintenance(task)` is called once per task before the scoring loop. It delegates daily upkeep to `TaskLifecycleManager.advancePeriods(task)` and pre-computes scoring constants into a `TaskScoringSnapshot`. `score()` reads from the cache; if `maintenance()` was never called, `score()` lazily calls it as a fallback. `onSlotAssigned()` increments the cached `scheduledToday` counter. `reset()` clears all caches at the start of each daily generation run.

**Lifecycle methods live in `TaskLifecycleManager`** (in `features/task/domain/`). It is stateless — all methods take `Task` as a parameter and mutate it directly. Used by both `TaskScorer` (via `advancePeriods` during maintenance), `CheckOffTaskUseCase` (via `updateStreak` and `adaptPrefSlot`), and `TaskCompletionService` (passed as parameter to `checkOff()`).

`TaskScorer.score()` applies these layers in order, each multiplying the running total:

1. **Hard constraints** → return 0: cooldown not met (`cache.sinceLast < cooldown`), slot too short for `minDuration`, progress requires more time than available (`requiredTimePerRep()`), past deadline with `closeOnMiss`, already complete, already scheduled enough today
2. **Priority base** → `core.priority.value` (LOW=100 / MEDIUM=200 / HIGH=400 / CRITICAL=10000)
3. **Child influence** → `Math.max(totalPrio, cache.maxChildPriority)` — parent inherits the highest child priority if it exceeds the parent's own
4. **Day constraint** → if task has day-specific prefSlots (`cache.hasDayConstraints`) but none match today (`todayPrefSlots` empty) → return 0. This ensures e.g. Sport (Mo/Mi/Fr) isn't scheduled on Tuesday.
5. **Preferred time fit** → finds closest matching `TaskPrefSlot` from `cache.todayPrefSlots` (pre-filtered to today's day-of-week), then `Math.max(0, 1 - abs(deviation / 8))` factor; 8+ hours deviation → score clamped to 0
6. **Urgency** → `cache.requiredDays / cache.remainingDays`; overdue = hardcoded 100
7. **Aging** → `cache.agingForce` = `1 + (daysSinceLastActivity / 10)`, capped at 3.0

**Period tracking:** `Repetition` tracks discrete periods via `periodStart` (LocalDate) and `periodCompletions` (int, resets each period). `periodEnd()` = `periodStart + periodInDays()`. `TaskLifecycleManager.advancePeriods(task)` runs inside `maintenance()` once before scoring: if the current period has expired, it evaluates whether the rep goal was met (breaks streak if not), bulk-jumps `periodStart` to the current period boundary, resets `periodCompletions`, and also breaks streak for skipped empty periods.

**Streak tracking:** `TaskLifecycleManager.updateStreak(task, completedSlot)` calls `advancePeriods()`, increments `periodCompletions`, and increments `currentStreak` only when `periodCompletions == reps` (period goal met). Streaks are period-based, not consecutive-day-based.

**Adaptive preferred times:** `TaskLifecycleManager.adaptPrefSlot(task, slot)` adjusts the best-matching `TaskPrefSlot.start` using an exponential moving average (alpha=0.2) when `slot.realStart != null` and `core.adaptive` is true. Rounds to the nearest 5 minutes. Called automatically on task completion (see checkOff above).

### Slot generation

`TaskSlotGenerator` (interface) defines the multi-day scheduling contract. `DefaultTaskSlotGenerator` (implementation in `features/task/domain/internal/scheduling/`) assigns tasks to time slots using composite scores with preferred-time-aware placement.

**Multi-day orchestration:**
1. `createPlanningState()` → returns `TaskPlanningState` to track which tasks are scheduled across days
2. `recordPreservedSlots()` → loads existing slots from database for the planning window
3. Loop for each day:
   - `generateSlotsForDay(tasks, windowStart, windowEnd, state)` → runs the daily scoring engine
   - `recordScheduledSlotsForDay()` → updates planning state with newly scheduled tasks

**Daily generation flow:**
Before the scoring loop: `scorer.reset()` → `TaskTreeOperations.buildTree(tasks)` → `flatten()` → `scorer.maintenance(task)` on all tasks → build `allTasksById` lookup map. A `scheduledInSession` set tracks which tasks have been assigned slots in the current generation run.

**Preferred-time placement:** Evaluates each task at two kinds of start times: (1) the current cursor position (greedy), and (2) each of the task's day-matching `TaskPrefSlot.start` times. The `(task, startTime)` pair with the highest score wins. Since `TaskScorer.score()` maximizes the fit factor at preferred times (fit=1.0), tasks naturally gravitate toward their preferred times, leaving gaps when spread apart.

**Recursive gap-filling:** When the best candidate's start time is after the cursor, the gap is filled recursively using the same algorithm. The "anchored" task is excluded from gap-filling to prevent premature placement. Unfillable gaps remain as free time. Recursion terminates with strictly smaller windows and growing exclusion sets.

**Prerequisite enforcement:** Before scoring each task, checks all `TaskPrerequisite` entries. A prerequisite is satisfied if the referenced task is either in `scheduledInSession` or already has a scheduled/completed `TaskSlot` for today. Tasks with unmet prerequisites are skipped.

Children are scheduled **inside** their parent's time block — child slots inherit the parent's cursor as their start. Child scheduling calls the assignment algorithm recursively, so children compete freely within their parent's time block.

## Where to Start Learning

For new developers, follow this reading path to understand the codebase:

1. **[MainActivity.java](src/main/java/com/autosecretary/app/MainActivity.java)** — Start here. Single Activity + FragmentContainerView + BottomNavigationView. Sets up the app's navigation skeleton and DI root.

2. **[ListFragment.java](src/main/java/com/autosecretary/features/task/ui/ListFragment.java)** — Main UI interactions. Observe how user intents (button clicks, checkbox taps, long-presses) trigger ViewModel methods.

3. **[TaskViewModel.java](src/main/java/com/autosecretary/features/task/ui/TaskViewModel.java)** — State and filtering/sorting orchestration. Understand how `masterList` → `filterList()` → `sortList()` → `displayList.postValue()` works.

4. **[TaskAsyncDataService.java](src/main/java/com/autosecretary/features/task/application/TaskAsyncDataService.java)** — Application-layer boundary. See how database reads/writes are coordinated on the single-threaded executor.

5. **[CheckOffTaskUseCase.java](src/main/java/com/autosecretary/features/task/application/CheckOffTaskUseCase.java)** — Completion flow. Shows how two-phase checkOff (started → completed) updates the Task and refreshes the UI.

6. **[RegenerateScheduleUseCase.java](src/main/java/com/autosecretary/features/task/application/RegenerateScheduleUseCase.java)** — Multi-day slot generation. Traces the full flow: planning state → daily generation → DAO write → refresh.

7. **[TaskSlotGenerator.java](src/main/java/com/autosecretary/features/task/domain/TaskSlotGenerator.java)** — Domain scheduling interface. Read the contract before diving into the implementation.

8. **[DefaultTaskSlotGenerator.java](src/main/java/com/autosecretary/features/task/domain/internal/scheduling/DefaultTaskSlotGenerator.java)** — Scheduling implementation. Understand the daily generation loop, preferred-time placement, and gap-filling.

9. **[TaskScorer.java](src/main/java/com/autosecretary/features/task/domain/internal/scheduling/TaskScorer.java)** — Scoring algorithm. Covers the seven-layer prioritization (hard constraints → priority → child influence → day constraints → preferred time fit → urgency → aging).

10. **[TaskDAO.java](src/main/java/com/autosecretary/features/task/data/TaskDAO.java)** and **[AppDatabase.java](src/main/java/com/autosecretary/database/AppDatabase.java)** — Room persistence layer. See how `Task` POJOs are assembled from five tables via `@Embedded` + `@Relation`.

## Manual Testing

Two validation scripts are available. Both require a connected device/emulator with USB debugging.

**`./ops/test_schedule.sh`** — full end-to-end: builds APK, uninstalls/installs it, launches the app, taps "Generieren", waits 10s, then runs 22 checks (today's schedule: time order, day constraints, parent-child scheduling, prerequisites; plus multi-day distribution). Supports `--verbose` and `--pull-db` flags.

**`./ops/check_only.sh`** — log-only shortcut: reads existing logcat (no build or install). Validates that 6 seed tasks (Sport, Einkaufen, Arbeit, Morgenroutine, Wäsche waschen, Abendspaziergang) are distributed across the correct number of days. Use this after `ops/test_schedule.sh` has already run the app.

Both scripts parse `SlotGen`-tagged logcat output.

## Refactoring Status

The app has three feature domains. Only tasks are actively being rebuilt:

| Feature | Status | Location |
|---------|--------|----------|
| Task scheduling | **Active** — Room + MVVM + Fragments | `src/main/java/com/autosecretary/` |
| Budget/Finance | Not migrated | `history/legacy/controller/budgetTab/`, `history/legacy/entities/` |
| Meal planning | Not migrated | `history/legacy/controller/mealTab/`, `history/legacy/entities/` |

The `history/legacy/` directory contains 80+ Java files spanning widgets, scheduling, budget management (with Claude API integration), meal planning (recipes/ingredients), and a custom SQLite repo layer with hand-written parsers.

## Commit Conventions

Short imperative subject lines. Conventional Commit prefixes are optional but welcome for scoped changes:
- `fix(scope): ...` for bug fixes
- `feat(scope): ...` for new features
- `refactor(scope): ...` for structural changes without behavior change

Common scopes: `ui`, `build`, `domain`, `data`, `scheduling`.

## Not Yet Implemented

- Several `AndroidManifest` permissions (`RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `READ_CALENDAR`, `REQUEST_INSTALL_PACKAGES`) are dead declarations from the legacy architecture with no corresponding code.
- `TaskDAO.deleteCore(String id)` exists but is never called anywhere in the codebase.
- `Task.setParentId(String id)` exists but is never called.
- Budget feature (`BudgetFragment`) is partially migrated from legacy code but not fully integrated into the app's main workflow.

## Key Technical Details

- **Java 17** with core library desugaring (minSdk 26, targetSdk 35)
- **Room 2.6.1** for persistence, annotation processor (not KSP)
- **AGP 8.7.3**, **Gradle 8.10.2** (use `./gradlew` wrapper)
- **XML layouts** in `src/main/res/layout/` (`activity_main`, `fragment_task_list`, `task_row`, `fragment_task_editor`), menu in `src/main/res/menu/bottom_nav.xml`
- **Room DB version 6**, `exportSchema = false`, `fallbackToDestructiveMigration()` enabled — any schema change just needs a version bump (data will be destroyed on upgrade). No manual migrations exist. `AppDatabase.getInstance()` is `synchronized` (thread-safe singleton)
- **`android.nonTransitiveRClass=true`** in `gradle.properties` — resource references must use the app's own R class
- **Package**: `com.autosecretary`
- **Single Activity + Fragments**: `app.MainActivity` hosts fragments via `FragmentContainerView`
- **`TaskPlanningState`** tracks cross-day scheduling state (which tasks scheduled on which days, total reps)
- **Type converters** in `Converters.java` handle `LocalDate`, `LocalTime`, `DayOfWeek`, `Set<DayOfWeek>`, `Priority`, `Period` — all serialized to `String` (set uses comma-joined names)
- **Preferences**: `readPrefTime(LocalDate, boolean)` returns `LocalTime` (defaults: `06:00` start, `16:00` end); `writePrefTime(DayOfWeek, boolean, LocalTime)` — note the asymmetry: read takes `LocalDate`, write takes `DayOfWeek`
- **ListRowAdapter**: Uses `R.dimen.indent_step` (24dp) × `viewSlot.depth` for tree indentation padding; `notifyDataSetChanged()` on every update (no DiffUtil). Takes two callbacks: `Consumer<ViewSlot> onCheck` (checkbox → two-phase checkOff) and `Consumer<ViewSlot> onLongPress` (long-press → edit dialog). Row displays include deadline urgency (color-coded via `TaskListItem.DeadlineUrgency`: red OVERDUE, orange TODAY/SOON, gray FUTURE), streak counter (`streak + "x"`), and green background tint for in-progress items. Checkbox is disabled when `completed` or `slotId == null`
- **UI language**: All user-facing text is in **German** (button labels, dialog titles, strings). Examples: "Generieren", "Speichern", "Task erstellen"/"Task bearbeiten", "Neue Task". Keep new UI text in German to stay consistent.
