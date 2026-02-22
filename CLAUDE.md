# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**WARNING: `assembleDebug` (and any `assemble` variant that includes debug) automatically increments `release/version.txt`, copies the APK to `release/`, and pushes to GitHub via `git push`. Do NOT run it without intending a release.**

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK — ALSO pushes to GitHub (see warning above)
./gradlew test                   # Run all unit tests (JUnit 4 + Robolectric)
./gradlew testDebugUnitTest      # Run debug unit tests only
./gradlew testDebugUnitTest --tests "com.autosecretary.SomeTest"              # Single test class
./gradlew testDebugUnitTest --tests "com.autosecretary.SomeTest.methodName"   # Single test method
```

No tests exist yet — the `test/` source set is configured in Gradle but the directory itself does not exist.

## Project Layout

This is a non-standard Android project using flat source directories (no `app/` module):

| Path | Purpose |
|------|---------|
| `src/` | Active Java source (flat, not `src/main/java/`) |
| `old/` | Legacy code being migrated — do not modify |
| `res/` | Android resources |
| `AndroidManifest.xml` | Root-level manifest |
| `build.gradle.kts` | Single-module Kotlin DSL build |
| `release/` | Built APK + version counter |

Source set mapping in Gradle: `java.srcDirs("src")`, `res.srcDirs("res")`, `manifest.srcFile("AndroidManifest.xml")`.

## Architecture

**MVVM with Room** — the app is being rebuilt from a legacy SQLite/custom-parser architecture.

```
views/              → MainActivity (fragment host), MainViewModel (empty)
views/taskTab/      → ListFragment, TaskViewModel, ListRowAdapter
views/models/       → ViewSlotList (presentation model with filtering/sorting)
services/           → Business logic (SlotGenerator)
database/           → AppDatabase, Converters
database/task/      → Room entities + DAO (Task, TaskCore, TaskSlot, TaskRelation, TaskPrefSlot, TaskFollowUp)
config/             → SharedPreferences wrappers (Preferences)
constants/          → Enums (Priority, Period)
```

### Navigation

`MainActivity` hosts a `FragmentContainerView` + `BottomNavigationView` with two tabs:
- **Schedule** (`tab_schedule`) → `ListFragment`
- **Tasks** (`tab_manage`) → placeholder `ListFragment` (no management UI exists yet)

`ListFragment` has an internal `MaterialButtonToggleGroup` that switches between two display modes:
- **Checklist** — filtered to today, only scheduled slots, sorted by time
- **Manage** — filtered to today, includes unscheduled tasks, sorted by task-parent tree then title

Fragment swapping via `getSupportFragmentManager().replace()`. `TaskViewModel` is scoped to the Activity (`requireActivity()`) so it's shared across fragments.

### Data flow

```
TaskViewModel constructor → background thread:
  → masterList.fromList(taskDao.readAll())   // builds ViewSlotList from all tasks

ListFragment "Generieren" button → vm.updateList() → background thread:
  → SlotGenerator.generateSlots(taskDao, start, end)  // currently broken — see Known Bugs
      → readAll() → Task.buildTree() → assignSlot() → writeList()
  → masterList.fromList(taskDao.readAll())   // refresh master list
  → filterList() → sortList() → displayList.postValue()
  → main thread → adapter.setList() → RecyclerView redraws

ListFragment toggle switch → vm.filters/vm.sorters update → vm.filterList()
  → masterList.filter(predicate) → sortList()
      → masterList.sort() → buildTree() → sortTree() → flatten(with depth)
  → displayList.postValue() → adapter.setList()

ListFragment checkbox → vm.checkOff(slot) → taskDao.writeSlot(slot)
```

DB seeding happens in `AppDatabase.onCreate()` callback (runs once on first DB creation), not on every button press.

All DB access runs on a background thread via `Executors.newSingleThreadExecutor()`.

`TaskDAO.writeList()` uses a 3-pass strategy:
1. **Pass 1 — Cores**: Insert all `TaskCore` rows, capture generated IDs via `setId()`
2. **Pass 2 — Relations**: Write `TaskRelation` entries linking parents to children (IDs now exist)
3. **Pass 3 — Rest**: Write follow-ups, pref slots, and task slots

`SlotGenerator.generateSlots()` calls `taskDao.writeList(taskTree)` at the end — effectively a full rewrite of the entire database on each generation.

### Task model

`Task` is a Room POJO (not a `@Entity`). Room assembles it via `@Embedded` + `@Relation` from five actual database tables:

| Class | Table | Role |
|-------|-------|------|
| `TaskCore` | `task_core` | One row per task — title, scheduling params, embedded sub-objects |
| `TaskSlot` | `task_slots` | Scheduled/completed time blocks. FK → `taskId`. Has `parent` (Long) for parent-child slot hierarchy and `score` |
| `TaskRelation` | `task_relation` | Parent-child links between tasks. FK → `child`. Has `child` and `parent` columns pointing to `task_core.id` |
| `TaskPrefSlot` | `task_pref_slots` | Preferred weekday/time. FK → `taskId` |
| `TaskFollowUp` | `task_follow_ups` | Follow-up links. FK → `taskId` |

`TaskCore` uses `@Embedded` for three static inner classes (`Repetition`, `Progress`, `History`) — their fields are flattened into `task_core` columns with prefixes (`repetition_`, `progress_`, `history_`).

Parent-child relationship: `TaskRelation` entity links tasks via `child`/`parent` columns. `Task.buildTree()` reads `task.parents` (a `@Relation` list) and builds the in-memory tree. `Task.flatten()` does the inverse — collects all tasks from a tree into a flat list for writing.

`TaskSlot` also has its own tree structure: `TaskSlot.parent` (Long) + `TaskSlot.buildTree()` builds a slot hierarchy. `SlotGenerator.assignSlot()` passes a `parentSlot` parameter to set child slot hierarchy — but currently references `slot.parentSlot` which doesn't exist on `TaskSlot` (see Known Bugs).

### ViewSlotList (presentation model)

`ViewSlotList` is the presentation layer between Room data and the RecyclerView. It holds two lists: `viewSlots` (master, all data) and `displaySlots` (filtered/sorted subset sent to UI).

`ViewSlot` is an inner class of `ViewSlotList` with fields: `Task task`, `TaskSlot slot`, `int depth`, `List<ViewSlot> children`. One ViewSlot per TaskSlot; tasks with no slots get a synthetic empty-slot ViewSlot.

Processing pipeline:
1. `fromList(tasks)` — builds flat `viewSlots` from all Tasks (one ViewSlot per slot, synthetic empty slot for unscheduled)
2. `filter(predicate)` — applies Predicate to `viewSlots`, writes matching items to `displaySlots`
3. `sort(byTaskParent, comparator)` — three phases:
   - `buildTree()` — groups `displaySlots` into parent-child hierarchy (by task-parent or slot-parent depending on `byTaskParent` flag)
   - `sortTree()` — recursively sorts siblings at each level using the comparator
   - `flatten()` — DFS traversal back to flat list, setting `depth` for UI indentation; cycle detection via `visited` set

### Scoring algorithm

`Task.score()` applies these layers in order, each multiplying the running total:

1. **Hard constraints** → return 0: cooldown not met, slot too short for `minDuration`, past deadline with `closeOnMiss`
2. **Priority base** → `core.priority.value` (100 / 200 / 400 / 10000)
3. **Child influence** → `priority * avgChildPriority` (can dramatically inflate parent scores)
4. **Preferred time fit** → `1 - abs(deviation / 8)` factor; 8+ hours deviation → negative score (filtered by greedy loop's `> 0` check, but not a clean zero)
5. **Urgency** → `requiredDays / remainingDays`; overdue = hardcoded 100
6. **Aging** → `1 + (daysSinceLastActivity / 10)`

Note: `checkSlots()` runs inside `score()` on every call, making scoring O(tasks x slots) per greedy iteration.

### Slot generation

`SlotGenerator` greedily assigns tasks to time slots using composite scores. Children are scheduled **inside** their parent's time block — child slots inherit the parent's cursor as their start.

## Refactoring Status

The app has three feature domains. Only tasks are actively being rebuilt:

| Feature | Status | Location |
|---------|--------|----------|
| Task scheduling | **Active** — Room + MVVM + Fragments | `src/` |
| Budget/Finance | Not migrated | `old/controller/budgetTab/`, `old/entities/` |
| Meal planning | Not migrated | `old/controller/mealTab/`, `old/entities/` |

The `old/` directory contains 80+ Java files spanning widgets, scheduling, budget management (with Claude API integration), meal planning (recipes/ingredients), and a custom SQLite repo layer with hand-written parsers.

## Known Bugs

### Compile errors (project will not build)
- **`TaskParent` class does not exist** — `Task.java` declares `@Relation List<TaskParent> parents`, and `TaskParent` is referenced in `Task.buildTree()`, `Task.setParentId()`, and `ViewSlotList.buildTree()`. No `TaskParent.java` file exists. Either create it or change references to `TaskRelation` (with corrected `@Relation` annotation: `entity = TaskRelation.class, entityColumn = "child"`).
- **`TaskDAO` imports deleted class** — `import views.models.ViewSlot` but that class was replaced by `ViewSlotList`. The import is unused and will fail.
- **`SlotGenerator` calling convention mismatch** — `TaskViewModel.updateList()` calls `SlotGenerator.generateSlots(taskDao, start, end)` as if it were a static method with parameters. But `SlotGenerator` has a constructor `(TaskDAO, LocalDateTime, LocalDateTime)` and `generateSlots()` is an instance method with no parameters. Either make `generateSlots` static with parameters, or instantiate `SlotGenerator` first.
- **`SlotGenerator` references `slot.parentSlot`** — `TaskSlot` has `slot.parent` (Long), not `parentSlot`. Field name mismatch — `parentSlot` is typed as `TaskSlot` in `assignSlot()` but `TaskSlot` has no such field.
- **`TaskViewModel.updateList()` type mismatch** — `prefs.readPrefTime()` returns `LocalTime` but the result is assigned to `LocalDateTime` variables. Need to combine with `LocalDate` (e.g. `LocalDateTime.of(date, time)`).

### Runtime bugs
- **`TaskPrefSlot`** uses a non-autoGenerate `@PrimaryKey` defaulting to `0` — inserting multiple pref slots silently replaces each other via `OnConflictStrategy.REPLACE`.
- **`TaskRelation` constructor arg order** — constructor is `TaskRelation(Long child, Long parent)` but `TaskDAO.writeList()` calls `new TaskRelation(task.core.id, child.core.id)` where `task` is the parent. Arguments are swapped, writing inverted relationships.
- **`ViewSlot.children` never initialized** — `ViewSlotList.ViewSlot` declares `private List<ViewSlot> children;` but the constructor doesn't initialize it. `buildTree()` calls `mappedVS.get(parent).children.add(vs)` → NPE.
- **`Task.buildTree()` NPE** — if a child references a parent not in the result set, `mappedTasks.get(parent.parent)` returns null.
- **`TaskSlot.buildTree()` NPE** — same pattern: if `parent` references a slot not in the list.
- **`ViewSlotList.buildTree()` NPE** — same pattern: `mappedVS.get(parent)` can return null if parent ViewSlot was filtered out.
- **`AppDatabase.getInstance()` not thread-safe** — no `synchronized` block, concurrent first calls could create duplicate instances.
- **DB version not bumped** — `TaskRelation` was added to `@Database` entities but version is still 1. This will crash existing installs with `IllegalStateException`. Must bump version and add migration or `fallbackToDestructiveMigration()`.

## Not Yet Implemented

- No task creation/editing UI — `MainActivity`'s second tab is a placeholder (`ListFragment` again). A management fragment still needs to be built.
- Several `AndroidManifest` permissions (`RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `READ_CALENDAR`, `REQUEST_INSTALL_PACKAGES`) are dead declarations from the legacy architecture with no corresponding code in `src/`.
- `TaskDAO.deleteAllCore()` exists but is never called anywhere in the codebase.

## Key Technical Details

- **Java 17** with core library desugaring (minSdk 26, targetSdk 35)
- **Room 2.6.1** for persistence, annotation processor (not KSP)
- **XML layouts** in `res/layout/` (`activity_main`, `fragment_task_list`, `task_row`), displayed via RecyclerView + `ListRowAdapter`
- **Room DB version 1**, `exportSchema = false` — neither migrations nor `fallbackToDestructiveMigration()` are configured, so any entity change will crash at runtime with `IllegalStateException`. To change the schema: bump `version` in `@Database`, then either add a `Migration` or enable `fallbackToDestructiveMigration()` (destroys all data)
- **Package**: `com.autosecretary`
- **Single Activity + Fragments**: `views.MainActivity` hosts fragments via `FragmentContainerView`
- **Type converters** in `Converters.java` handle `LocalDate`, `LocalTime`, `DayOfWeek`, `Priority`, `Period` — all serialized to `String`
