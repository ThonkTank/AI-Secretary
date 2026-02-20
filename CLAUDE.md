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
| `res/` | Android resources (currently minimal) |
| `AndroidManifest.xml` | Root-level manifest |
| `build.gradle.kts` | Single-module Kotlin DSL build |
| `release/` | Built APK + version counter |

Source set mapping in Gradle: `java.srcDirs("src")`, `res.srcDirs("res")`, `manifest.srcFile("AndroidManifest.xml")`.

## Architecture

**MVVM with Room** — the app is being rebuilt from a legacy SQLite/custom-parser architecture.

```
views/          → Activities + ViewModels (LiveData)
services/       → Business logic (scheduling algorithms)
database/       → Room entities, DAOs, type converters
config/         → SharedPreferences wrappers
constants/      → Enums (Priority, Period)
```

### Data flow

```
Button → MainActivity.onClick → vm.updateList() → background thread:
  → write hardcoded test tasks (TEMPORARY — no task creation UI yet)
  → SlotGenerator.generateSlots()
      → readAll() → Task.buildTree() → assignSlot() → writeList()
  → readSlotsForDay(today) → ViewSlot.assignIndents()
  → LiveData.postValue() → main thread → adapter.setList() → RecyclerView redraws
```

All DB access runs on a background thread via `Executors.newSingleThreadExecutor()`.

`TaskDAO.write()` uses a delete-then-reinsert strategy (not `@Update`) — it deletes the existing core (cascading to related rows via `@ForeignKey(onDelete = CASCADE)` on `TaskSlot`), then re-inserts everything. `write()` is **recursive**: it calls `write(child)` for each child, so a single call on a root task cascades through the entire subtree.

`SlotGenerator.generateSlots()` calls `taskDao.writeList(taskTree)` at the end — effectively a full rewrite of the entire database on each generation.

### Task model

`Task` is a Room POJO (not a `@Entity`). Room assembles it via `@Embedded` + `@Relation` from four actual database tables:

| Class | Table | Role |
|-------|-------|------|
| `TaskCore` | `task_core` | One row per task — title, scheduling params, embedded sub-objects |
| `TaskSlot` | `task_slots` | Scheduled/completed time blocks. FK: `taskId`, `@ForeignKey(onDelete = CASCADE)`. Has `parentSlotId` for parent-child slot hierarchy and `score` for the assigned score |
| `TaskPrefSlot` | `task_pref_slots` | Preferred weekday/time. FK: `taskId` |
| `TaskFollowUp` | `task_follow_ups` | Follow-up links. FK: `taskId` |

`TaskCore` uses `@Embedded` for three static inner classes (`Repetition`, `Progress`, `History`) — their fields are flattened into `task_core` columns with prefixes (`repetition_`, `progress_`, `history_`).

Parent-child relationship: `TaskCore.parent` is a `Long` pointing to another `task_core.id`. Not declared as a Room `@ForeignKey` — handled manually by `Task.buildTree()` (static method) via a two-pass tree build from flat records.

### View model

`ViewSlot` is a Room POJO (`@Embedded TaskSlot` + `@Relation TaskCore`) returned directly by `TaskDAO.readSlotsForDay()`. Indentation is slot-based: `ViewSlot.assignIndents()` walks the flat list and computes each slot's indent level from `parentSlotId` using a lookup map. Indent width is defined in `res/values/dimens.xml` as `indent_step` (24dp).

### Scoring algorithm

`Task.score()` applies these layers in order, each multiplying the running total:

1. **Hard constraints** → return 0: cooldown not met, slot too short for `minDuration`, past deadline with `closeOnMiss`
2. **Priority base** → `core.priority.value` (100 / 200 / 400 / 10000)
3. **Child influence** → `priority * avgChildPriority` (can dramatically inflate parent scores)
4. **Preferred time fit** → `1 - abs(deviation / 8)` factor; 8+ hours deviation → negative score (filtered by greedy loop's `> 0` check, but not a clean zero)
5. **Urgency** → `requiredDays / remainingDays`; overdue = hardcoded 100
6. **Aging** → `1 + (daysSinceLastActivity / 10)`

Note: `checkSlots()` runs inside `score()` on every call, making scoring O(tasks × slots) per greedy iteration.

### Slot generation

`SlotGenerator` greedily assigns tasks to time slots using composite scores. Children are scheduled **inside** their parent's time block — child slots inherit the parent's cursor as their start.

## Refactoring Status

The app has three feature domains. Only tasks are actively being rebuilt:

| Feature | Status | Location |
|---------|--------|----------|
| Task scheduling | **Active** — Room + MVVM | `src/` |
| Budget/Finance | Not migrated | `old/controller/budgetTab/`, `old/entities/` |
| Meal planning | Not migrated | `old/controller/mealTab/`, `old/entities/` |

The `old/` directory contains 80+ Java files spanning widgets, scheduling, budget management (with Claude API integration), meal planning (recipes/ingredients), and a custom SQLite repo layer with hand-written parsers.

## Known Bugs

- `TaskPrefSlot` uses a non-autoGenerate `@PrimaryKey` defaulting to `0` — inserting multiple pref slots silently replaces each other via `OnConflictStrategy.REPLACE`.
- `Task.buildTree()` will throw `NullPointerException` if a child is in the result set but its parent is not (the `mappedTasks.get(task.core.parent)` call returns null).
- `ViewSlot.assignIndents()` will NPE if `parentSlotId` references a slot not in the list (same pattern — `indents.get(vs.slot.parentSlotId)` returns null).
- `AppDatabase.getInstance()` is not thread-safe — no `synchronized` block, so concurrent first calls could create duplicate instances.

## Not Yet Implemented

- `MainViewModel.updateList()` seeds **hardcoded test data** on every button press — no real task creation UI exists yet.
- The `CheckBox` in `TaskRowAdapter` has an `onClickListener` that routes to `vm.checkOff()`, but `checkOff()` is a no-op placeholder (`return;`).
- Several `AndroidManifest` permissions (`RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `READ_CALENDAR`, `REQUEST_INSTALL_PACKAGES`) are dead declarations from the legacy architecture with no corresponding code in `src/`.
- `TaskDAO.deleteAllCore()` exists but is never called anywhere in the codebase.

## Key Technical Details

- **Java 17** with core library desugaring (minSdk 26, targetSdk 35)
- **Room 2.6.1** for persistence, annotation processor (not KSP)
- **XML layouts** in `res/layout/` (`activity_main`, `task_row`), displayed via RecyclerView + `TaskRowAdapter`
- **Room DB version 1**, `exportSchema = false` — neither migrations nor `fallbackToDestructiveMigration()` are configured, so any entity change will crash at runtime with `IllegalStateException`. To change the schema: bump `version` in `@Database`, then either add a `Migration` or enable `fallbackToDestructiveMigration()` (destroys all data)
- **Package**: `com.autosecretary`
- **Single Activity** architecture: `views.mainView.MainActivity`
- **Type converters** in `Converters.java` handle `LocalDate`, `LocalTime`, `DayOfWeek`, `Priority`, `Period` — all serialized to `String`
