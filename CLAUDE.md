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
- `./gradlew copyToRelease` copies the built debug APK to `ops/release/` and writes the next value to `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact` depends on `copyToRelease` and `pushToGitHub`; Git push happens only when this task is run.

**No automated tests.** At this stage they add unnecessary overhead. Do not write tests.

## Project Layout

Standard Android project structure (single module, no `app/` directory):

| Path | Purpose |
|------|---------|
| `src/main/java/com/autosecretary/` | Active Java source |
| `src/main/res/` | Android resources |
| `src/main/AndroidManifest.xml` | Manifest |
| `history/legacy/` | Legacy snapshots (reference-only, not part of active source set) |
| `build.gradle.kts` | Single-module Kotlin DSL build |
| `ops/release/` | Built APK + version counter |

Uses default Gradle source set conventions (no custom `sourceSets` block). All packages are fully qualified under `com.autosecretary.*`.

## Architecture

**MVVM with Room** — the app is being rebuilt from a legacy SQLite/custom-parser architecture.

```
views/              → MainActivity (fragment host)
views/taskTab/      → ListFragment, TaskViewModel, ListRowAdapter, TaskEditDialog
views/models/       → ViewSlotList (presentation model with filtering/sorting)
services/           → TaskLifecycleManager (period advancement, streak tracking, adaptive time adjustment)
services/taskPlanning/ → SlotGenerator (greedy slot assignment), TaskScorer (scoring cache + composite scoring)
database/           → AppDatabase, Converters
database/task/      → Room entities + DAO (Task, TaskCore, TaskSlot, TaskRelation, TaskPrefSlot, TaskPrerequisite)
config/             → SharedPreferences wrappers (Preferences)
constants/          → Enums (Priority, Period)
util/               → TreeBuilder<T> (generic tree build/flatten/sort, used via static instances on Task, TaskSlot, ViewSlotList)
```

All paths above are relative to `src/main/java/com/autosecretary/`.

### Navigation

`MainActivity` hosts a `FragmentContainerView` + `BottomNavigationView` with two tabs:
- **Tasks** (`tab_schedule`) → `ListFragment`
- **placeholder** (`tab_manage`) → placeholder `ListFragment` (no management UI exists yet)

`ListFragment` has an internal `MaterialButtonToggleGroup` that switches between two display modes:
- **Checklist** — filtered to today, only scheduled slots, sorted by time
- **Manage** — filtered to today, includes unscheduled tasks, sorted by task-parent tree then title

Both modes always filter to `LocalDate.now()` — there is no UI to view a different day.

Fragment swapping via `getSupportFragmentManager().beginTransaction().replace().commit()`. `TaskViewModel` is scoped to the Activity (`requireActivity()`) so it's shared across fragments.

**Task creation:** `ListFragment` has a "+ Neue Task" button (`NewTaskButton`) that calls `vm.createNewTask()` — which initializes a blank `Task` with a default `TaskPrefSlot` (start 06:00, all days), sets `vm.selectedTask`, and sets `vm.isNewTask = true` — then opens `TaskEditDialog` with tag "create".

**Task editing:** Long-press on a list row sets `vm.selectedTask` and opens `TaskEditDialog` (a `DialogFragment`). The dialog title is dynamic: "Task erstellen" when `vm.isNewTask`, "Task bearbeiten" otherwise. The dialog reads/writes fields directly on the shared `Task` object, then calls `vm.saveEditedTask()` → `taskDao.write(task)` on the background executor (also resets `isNewTask = false`). Layout: `fragment_task_editor.xml`.

The dialog has five sections: basic info (title, description, priority), scheduling (deadline with date picker + clear button, closeOnMiss, min/max duration, cooldown), repetition (toggle + reps/perPeriod/periodUnit), prefSlots (dynamically built rows with day picker + time picker) + adaptive checkbox, and progress (toggle + unit/target/current/resetPerRep/min-maxPerRep).

**Repetition↔PrefSlots reactivity:** Changing repetition fields triggers `onRepetitionChanged()` which recalculates `repsPerDay` and adds/removes `TaskPrefSlot` entries to match, then rebuilds the prefSlot UI. PrefSlots are deep-copied into `editablePrefSlots` on dialog open so edits are non-destructive until "Speichern". The day picker disables days already taken by other prefSlots in the same repetition group.

### Data flow

```
TaskViewModel constructor:
  → background thread (executor):
      → masterList.fromList(taskDao.readAll())   // builds ViewSlotList from all tasks
  → main thread (concurrent with above):
      → start/end from LocalDateTime.of(today, prefs.readPrefTime(day, start/end))
      → lifecycleManager = new TaskLifecycleManager()
      → scorer = new TaskScorer(lifecycleManager)
      → generator = new SlotGenerator(taskDao, start, end, scorer)   // instantiated once, holds stale times if app runs past midnight

ListFragment "Generieren" button → vm.updateList() → background thread:
  → generator.generateSlots()               // instance method, no params
      → scorer.reset() → readAll() → Task.buildTree() → flatten() → scorer.maintenance() on all tasks
      → assignSlot() (uses scorer.score(), scorer.onSlotAssigned()) → writeList()
  → masterList.fromList(taskDao.readAll())   // refresh master list
  → filterList() → sortList() → displayList.postValue()
  → main thread → adapter.setList() → RecyclerView redraws

ListFragment toggle switch → vm.filters/vm.sorters update → vm.filterList()
  → masterList.filter(predicate) → sortList()
      → masterList.sort() → buildTree() → sortTree() → flatten(with depth)
  → displayList.postValue() → adapter.setList()

ListFragment checkbox → vm.checkOff(viewSlot) → two-phase start/complete:
  Phase 1 (first tap): slot.realStart = now → writeSlot → green in-progress background
  Phase 2 (second tap): slot.realEnd = now, slot.completed = true
      → lifecycleManager.updateStreak(task, slot) → history.completions++
      → if trackDuration (not quick-tap ≤3s, not stale >24h):
          trackedCompletions++, totalDuration+=, and if task.core.adaptive: lifecycleManager.adaptPrefSlot(task, slot)
      → taskDao.write(task) + taskDao.writeSlot(slot)

ListFragment long press → vm.selectedTask = viewSlot.task
  → TaskEditDialog.show()
  → dialog reads task fields, user edits
  → "Speichern" → mutates task object directly → vm.saveEditedTask()
      → executor: taskDao.write(task) → filterList()
```

**Threading note:** In the constructor, `SlotGenerator` is created on the main thread while `masterList.fromList()` runs concurrently on the executor. These are independent, but if the user taps "Generieren" before the executor finishes `fromList()`, the single-threaded executor will queue correctly.

DB seeding: Currently done via hard-coded test data in `TaskViewModel.updateList()` — it calls `taskDao.deleteAllCore()`, creates sample tasks (parent-child hierarchy, various scheduling configs), writes them, then generates slots. This is temporary scaffolding; `AppDatabase.onCreate()` no longer seeds data.

All DB access in `TaskViewModel` runs on a background thread via `Executors.newSingleThreadExecutor()` with a custom `UncaughtExceptionHandler` that logs to `Log.e("TaskViewModel", ...)`.

`TaskDAO` has two write methods:
- `writeList(List<Task>)` — 2-pass bulk write: flattens tree, inserts all `TaskCore` rows first, then writes slots/prefSlots/prerequisites/relations for each task. Used by `SlotGenerator.generateSlots()` — effectively a full rewrite of the entire database on each generation.
- `write(Task)` — single-task upsert (same structure minus flatten). Used by `saveEditedTask()` for individual task edits.

### Task model

`Task` is a Room POJO (not a `@Entity`). Room assembles it via `@Embedded` + `@Relation` from five actual database tables (TaskCore, TaskSlot, TaskRelation, TaskPrefSlot, TaskPrerequisite). All entities use `String id = UUID.randomUUID().toString()` as their `@PrimaryKey`. All FK/reference fields are also `String` (UUID):

| Class | Table | Role |
|-------|-------|------|
| `TaskCore` | `task_core` | One row per task — title, scheduling params, embedded sub-objects |
| `TaskSlot` | `task_slots` | Scheduled/completed time blocks. FK `taskId` → `task_core.id`. Has `parent` for parent-child slot hierarchy, `score`, `scheduled`/`completed` booleans, `realStart`/`realEnd` for actual execution tracking |
| `TaskRelation` | `task_relation` | Parent-child links between tasks. FK `child` → `task_core.id`. `child` and `parent` columns point to `task_core.id` |
| `TaskPrefSlot` | `task_pref_slots` | Preferred days/time. FK `taskId` → `task_core.id`. `days` is `Set<DayOfWeek>` (stored as comma-joined string via TypeConverter) |
| `TaskPrerequisite` | `task_prerequisites` | Task dependencies. FK `taskId` → `task_core.id`. `prerequisiteId` references another `task_core.id`. SlotGenerator skips tasks whose prerequisites aren't yet scheduled/completed today |

`TaskCore` uses `@Embedded` for three static inner classes (`Repetition`, `Progress`, `History`) — their fields are flattened into `task_core` columns with prefixes (`repetition_`, `progress_`, `history_`). Additional fields: `description` (String), `adaptive` (boolean, for prefTime user-behavior adaptation), `completed` (boolean, default false). Defaults: `cooldown = 1`, `minDuration = 5`, `maxDuration = 10`, `priority = MEDIUM`, `closeOnMiss = true`, `created = LocalDate.now()`.

`History` tracks: `completions`, `trackedCompletions`, `currentStreak`, `nrStreaks` (default 1), `totalDuration`, plus derived `averageStreak()` and `averageDuration()`. `trackedCompletions` and `totalDuration` only increment for non-quick-tap, non-stale completions (see checkOff below).

`Progress` tracks: `unit`, `target`, `current`, `resetPerRep`, `minPerRep`, `maxPerRep`, `totalProgress`, `totalTime` (default 10), plus derived `repsRequired()`, `timePerProgress()`, `requiredTimePerRep()`.

Parent-child relationship: `TaskRelation` entity links tasks via `child`/`parent` columns. `Task.buildTree()` reads `task.parents` (a `@Relation` list with `entityColumn = "child"`) and builds the in-memory tree. `Task.flatten()` does the inverse — collects all tasks from a tree into a flat list for writing.

`TaskSlot` also has its own tree structure: `TaskSlot.parent` (String) + `TaskSlot.buildTree()` builds a slot hierarchy. `TaskSlot.children` is an `@Ignore` field initialized to `new ArrayList<>()`.

**Orphan safety:** All three `buildTree()` methods (`Task`, `TaskSlot`, `ViewSlotList`) handle missing parent references by treating orphaned items as roots rather than crashing with NPE.

The convenience `Task` constructor `(String title, int reps, int perPeriod, Period periodUnit, LocalDate deadline, int cooldown, LocalTime start, int maxDuration)` creates a `TaskCore`, sets scheduling fields, initializes `slots`, `prefSlots`, `parents`, and `prerequisites` as empty lists, and adds `repsPerDay()` `TaskPrefSlot` entries each with `days = EnumSet.allOf(DayOfWeek.class)`.

### ViewSlotList (presentation model)

`ViewSlotList` is the presentation layer between Room data and the RecyclerView. It holds two lists: `viewSlots` (master, all data) and `displaySlots` (filtered/sorted subset sent to UI).

`ViewSlot` is a static nested class of `ViewSlotList` with fields: `Task task`, `TaskSlot slot`, `int depth`, private `List<ViewSlot> children`. One ViewSlot per TaskSlot; tasks with no slots get a synthetic empty-slot ViewSlot (with `day = LocalDate.now()`).

Processing pipeline:
1. `fromList(tasks)` — builds flat `viewSlots` from all Tasks (one ViewSlot per slot, synthetic empty slot for unscheduled)
2. `filter(predicate)` — applies Predicate to `viewSlots`, writes matching items to `displaySlots`
3. `sort(byTaskRelation, comparator)` — three phases:
   - `buildTree()` — groups `displaySlots` into parent-child hierarchy (by task-parent or slot-parent depending on `byTaskRelation` flag)
   - `sortTree()` — recursively sorts siblings at each level using the comparator
   - `flatten()` — DFS traversal back to flat list, setting `depth` for UI indentation; cycle detection via `visited` set

**Naming mismatch:** `ViewSlotList.sort()` parameter is named `byTaskRelation`, but `TaskViewModel.Sorters.byTaskParent` (the field passed to it) is still named `byTaskParent`. Same concept, different names.

### TaskViewModel inner classes

`Filters`: `day` (LocalDate) — when non-null, only shows slots for that day; `displayUnscheduled` (boolean) — when false, filters out `vs.slot.start == null`.

`Sorters`: `byTaskParent` (boolean) — tree by task relations vs slot parent; `byScore`, `byTime`, `byTitle` (boolean) — chained comparators via `thenComparing`.

### Scoring algorithm

**Scoring lives in `TaskScorer`** (in `services/taskPlanning/`). It holds a `Map<String, ScoringCache>` keyed by task ID. `TaskScorer` is constructed with a `TaskLifecycleManager` dependency. The dependency graph is wired in `TaskViewModel`'s constructor: `TaskLifecycleManager` → `TaskScorer` → `SlotGenerator`. `TaskViewModel` also holds a direct reference to `TaskLifecycleManager` for use in `checkOff()`.

**Maintenance + caching:** `TaskScorer.maintenance(task)` is called once per task before the scoring loop. It delegates daily upkeep to `TaskLifecycleManager.advancePeriods(task)` and pre-computes scoring constants into a `ScoringCache`. `score()` reads from the cache; if `maintenance()` was never called, `score()` lazily calls it as a fallback. `onSlotAssigned()` increments the cached `scheduledToday` counter. `reset()` clears all caches at the start of each `generateSlots()` run.

**Lifecycle methods live in `TaskLifecycleManager`** (in `services/`). It is stateless — all methods take `Task` as a parameter and mutate it directly. Used by both `TaskScorer` (via `advancePeriods` during maintenance) and `TaskViewModel.checkOff()` (via `updateStreak` and `adaptPrefSlot`).

`TaskScorer.score()` applies these layers in order, each multiplying the running total:

1. **Hard constraints** → return 0: cooldown not met (`cache.sinceLast < cooldown`), slot too short for `minDuration`, progress requires more time than available (`requiredTimePerRep()`), past deadline with `closeOnMiss`, already complete, already scheduled enough today
2. **Priority base** → `core.priority.value` (100 / 200 / 400 / 10000)
3. **Child influence** → `Math.max(totalPrio, cache.maxChildPriority)` — parent inherits the highest child priority if it exceeds the parent's own
4. **Preferred time fit** → finds closest matching `TaskPrefSlot` from `cache.todayPrefSlots` (pre-filtered to today's day-of-week), then `Math.max(0, 1 - abs(deviation / 8))` factor; 8+ hours deviation → score clamped to 0
5. **Urgency** → `cache.requiredDays / cache.remainingDays`; overdue = hardcoded 100
6. **Aging** → `cache.agingForce` = `1 + (daysSinceLastActivity / 10)`, capped at 3.0

**Period tracking:** `Repetition` tracks discrete periods via `periodStart` (LocalDate) and `periodCompletions` (int, resets each period). `periodEnd()` = `periodStart + periodInDays()`. `TaskLifecycleManager.advancePeriods(task)` runs inside `maintenance()` once before scoring: if the current period has expired, it evaluates whether the rep goal was met (breaks streak if not), bulk-jumps `periodStart` to the current period boundary, resets `periodCompletions`, and also breaks streak for skipped empty periods.

**Streak tracking:** `TaskLifecycleManager.updateStreak(task, completedSlot)` calls `advancePeriods()`, increments `periodCompletions`, and increments `currentStreak` only when `periodCompletions == reps` (period goal met). Streaks are period-based, not consecutive-day-based.

**Adaptive preferred times:** `TaskLifecycleManager.adaptPrefSlot(task, slot)` adjusts the best-matching `TaskPrefSlot.start` using an exponential moving average (alpha=0.2) when `slot.realStart != null` and `core.adaptive` is true. Rounds to the nearest 5 minutes. Called automatically on task completion (see checkOff below).

### Slot generation

`SlotGenerator` greedily assigns tasks to time slots using composite scores. It takes a `TaskScorer` as a constructor dependency. Before the scoring loop, it calls `scorer.reset()` then `scorer.maintenance(task)` on all tasks (via `Task.flatten()` → loop) and builds an `allTasksById` lookup map. A `scheduledInSession` set tracks which tasks have been assigned slots in the current generation run.

**Prerequisite enforcement:** Before scoring each task, `hasUnmetPrerequisites()` checks all `TaskPrerequisite` entries. A prerequisite is satisfied if the referenced task is either in `scheduledInSession` (scheduled in this generation run) or already has a scheduled/completed `TaskSlot` for today. Tasks with unmet prerequisites are skipped (logged as "0 (Voraussetzung)"). The `scheduledInSession` set is shared across all recursion levels, so a root task can depend on a child task of another parent.

Children are scheduled **inside** their parent's time block — child slots inherit the parent's cursor as their start. The `assignSlot()` method recurses: it calls itself with `bestTask.children` and the current slot as `parentSlot`. After assigning a slot, it calls `scorer.onSlotAssigned(bestTask)` to update the cached `scheduledToday` counter.

## Refactoring Status

The app has three feature domains. Only tasks are actively being rebuilt:

| Feature | Status | Location |
|---------|--------|----------|
| Task scheduling | **Active** — Room + MVVM + Fragments | `src/main/java/com/autosecretary/` |
| Budget/Finance | Not migrated | `history/legacy/controller/budgetTab/`, `history/legacy/entities/` |
| Meal planning | Not migrated | `history/legacy/controller/mealTab/`, `history/legacy/entities/` |

The `history/legacy/` directory contains 80+ Java files spanning widgets, scheduling, budget management (with Claude API integration), meal planning (recipes/ingredients), and a custom SQLite repo layer with hand-written parsers.

## Not Yet Implemented

- `MainActivity`'s second tab is still a placeholder (`ListFragment` again). A management fragment still needs to be built.
- Several `AndroidManifest` permissions (`RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `READ_CALENDAR`, `REQUEST_INSTALL_PACKAGES`) are dead declarations from the legacy architecture with no corresponding code.
- `TaskDAO.deleteCore(String id)` exists but is never called anywhere in the codebase.
- `Task.setParentId(String id)` exists but is never called.

## Key Technical Details

- **Java 17** with core library desugaring (minSdk 26, targetSdk 35)
- **Room 2.6.1** for persistence, annotation processor (not KSP)
- **XML layouts** in `src/main/res/layout/` (`activity_main`, `fragment_task_list`, `task_row`, `fragment_task_editor`), menu in `src/main/res/menu/bottom_nav.xml`
- **Room DB version 6**, `exportSchema = false`, `fallbackToDestructiveMigration()` enabled — any schema change just needs a version bump (data will be destroyed on upgrade). No manual migrations exist. `AppDatabase.getInstance()` is `synchronized` (thread-safe singleton)
- **Package**: `com.autosecretary`
- **Single Activity + Fragments**: `views.MainActivity` hosts fragments via `FragmentContainerView`
- **Type converters** in `Converters.java` handle `LocalDate`, `LocalTime`, `DayOfWeek`, `Set<DayOfWeek>`, `Priority`, `Period` — all serialized to `String` (set uses comma-joined names)
- **Preferences**: `readPrefTime(LocalDate, boolean)` returns `LocalTime` (defaults: `06:00` start, `16:00` end); `writePrefTime(DayOfWeek, boolean, LocalTime)` — note the asymmetry: read takes `LocalDate`, write takes `DayOfWeek`
- **ListRowAdapter**: Uses `R.dimen.indent_step` (24dp) × `viewSlot.depth` for tree indentation padding; `notifyDataSetChanged()` on every update (no DiffUtil). Takes two callbacks: `Consumer<ViewSlot> onCheck` (checkbox → two-phase checkOff) and `Consumer<ViewSlot> onLongPress` (long-press → edit dialog). Row displays include deadline countdown (color-coded: red overdue, orange ≤3 days), streak counter (`currentStreak + "x"`), and green background tint for in-progress slots (`realStart != null && !completed`). Checkbox is disabled when `slot.completed`
- **UI language**: All user-facing text is in **German** (button labels, dialog titles, strings). Examples: "Generieren", "Speichern", "Task erstellen"/"Task bearbeiten", "Neue Task". Keep new UI text in German to stay consistent.
