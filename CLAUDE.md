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
- **Tasks** (`tab_schedule`) → `ListFragment`
- **placeholder** (`tab_manage`) → placeholder `ListFragment` (no management UI exists yet)

`ListFragment` has an internal `MaterialButtonToggleGroup` that switches between two display modes:
- **Checklist** — filtered to today, only scheduled slots, sorted by time
- **Manage** — filtered to today, includes unscheduled tasks, sorted by task-parent tree then title

Both modes always filter to `LocalDate.now()` — there is no UI to view a different day.

Fragment swapping via `getSupportFragmentManager().beginTransaction().replace().commit()`. `TaskViewModel` is scoped to the Activity (`requireActivity()`) so it's shared across fragments.

### Data flow

```
TaskViewModel constructor:
  → background thread (executor):
      → masterList.fromList(taskDao.readAll())   // builds ViewSlotList from all tasks
  → main thread (concurrent with above):
      → start/end from LocalDateTime.of(today, prefs.readPrefTime(day, start/end))
      → generator = new SlotGenerator(taskDao, start, end)   // instantiated once, holds stale times if app runs past midnight

ListFragment "Generieren" button → vm.updateList() → background thread:
  → generator.generateSlots()               // instance method, no params
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

**Threading note:** In the constructor, `SlotGenerator` is created on the main thread while `masterList.fromList()` runs concurrently on the executor. These are independent, but if the user taps "Generieren" before the executor finishes `fromList()`, the single-threaded executor will queue correctly.

DB seeding happens in `AppDatabase.onCreate()` callback (runs once on first DB creation, using its own `Executors.newSingleThreadExecutor()` — separate from the ViewModel's executor).

All DB access in `TaskViewModel` runs on a background thread via `Executors.newSingleThreadExecutor()`.

`TaskDAO.writeList()` uses a 3-pass strategy:
1. **Pass 1 — Cores**: Insert all `TaskCore` rows, call `setId()` to propagate the core ID to slots, followUps, and prefSlots. With UUID PKs, IDs are pre-generated in field initializers — the insert-then-propagate pattern is vestigial and currently broken (see Known Bugs)
2. **Pass 2 — Relations**: Write `TaskRelation` entries linking parents to children (IDs now exist)
3. **Pass 3 — Rest**: Write follow-ups, pref slots, and task slots; back-fill slot IDs into `task.slots.get(i).id` (vestigial with UUID PKs — slot IDs are pre-generated)

`SlotGenerator.generateSlots()` calls `taskDao.writeList(taskTree)` at the end — effectively a full rewrite of the entire database on each generation.

### Task model

`Task` is a Room POJO (not a `@Entity`). Room assembles it via `@Embedded` + `@Relation` from five actual database tables. All entities use `String id = UUID.randomUUID().toString()` as their `@PrimaryKey` (migrated from auto-generated Long — see Known Bugs for incomplete migration):

| Class | Table | Role |
|-------|-------|------|
| `TaskCore` | `task_core` | One row per task — title, scheduling params, embedded sub-objects |
| `TaskSlot` | `task_slots` | Scheduled/completed time blocks. FK `taskId` (Long) → `task_core.id`. Has `parent` (Long) for parent-child slot hierarchy and `score` |
| `TaskRelation` | `task_relation` | Parent-child links between tasks. FK `child` (Long) → `task_core.id`. `child` and `parent` columns (both Long) point to `task_core.id` |
| `TaskPrefSlot` | `task_pref_slots` | Preferred weekday/time. FK `taskId` (Long) → `task_core.id` |
| `TaskFollowUp` | `task_follow_ups` | Follow-up links. FK `taskId` (Long) → `task_core.id` |

`TaskCore` uses `@Embedded` for three static inner classes (`Repetition`, `Progress`, `History`) — their fields are flattened into `task_core` columns with prefixes (`repetition_`, `progress_`, `history_`). Defaults: `cooldown = 1`, `minDuration = 5`, `maxDuration = 10`, `priority = MEDIUM`, `closeOnMiss = true`, `created = LocalDate.now()`.

Parent-child relationship: `TaskRelation` entity links tasks via `child`/`parent` columns. `Task.buildTree()` reads `task.parents` (a `@Relation` list with `entityColumn = "child"`) and builds the in-memory tree. `Task.flatten()` does the inverse — collects all tasks from a tree into a flat list for writing.

`TaskSlot` also has its own tree structure: `TaskSlot.parent` (Long) + `TaskSlot.buildTree()` builds a slot hierarchy. `TaskSlot.children` is an `@Ignore` field initialized to `new ArrayList<>()`.

The convenience `Task` constructor initializes `slots`, `followUps`, and `prefSlots` as empty lists, but does **not** initialize `parents` (left null — see Known Bugs).

### ViewSlotList (presentation model)

`ViewSlotList` is the presentation layer between Room data and the RecyclerView. It holds two lists: `viewSlots` (master, all data) and `displaySlots` (filtered/sorted subset sent to UI).

`ViewSlot` is a static nested class of `ViewSlotList` with fields: `Task task`, `TaskSlot slot`, `int depth`, `List<ViewSlot> children`. One ViewSlot per TaskSlot; tasks with no slots get a synthetic empty-slot ViewSlot (with `day = LocalDate.now()`).

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

`Task.score()` applies these layers in order, each multiplying the running total:

1. **Hard constraints** → return 0: cooldown not met (`sinceLast() < cooldown`), slot too short for `minDuration`, progress requires more time than available (`requiredTimePerRep()`), past deadline with `closeOnMiss`
2. **Priority base** → `core.priority.value` (100 / 200 / 400 / 10000)
3. **Child influence** → `priority * avgChildPriority` (can dramatically inflate parent scores)
4. **Preferred time fit** → `1 - abs(deviation / 8)` factor; 8+ hours deviation → negative score (filtered by greedy loop's `> 0` check, but not a clean zero)
5. **Urgency** → `requiredDays / remainingDays`; overdue = hardcoded 100
6. **Aging** → `1 + (daysSinceLastActivity / 10)`

Note: `checkSlots()` runs inside `score()` on every call, making scoring O(tasks x slots) per greedy iteration. `checkSlots()` populates `completions`, `isComplete`, `lastCompletion`, and `lastScheduled` — but `completions` and `isComplete` are never read by `score()` or any other external method.

### Slot generation

`SlotGenerator` greedily assigns tasks to time slots using composite scores. Children are scheduled **inside** their parent's time block — child slots inherit the parent's cursor as their start. The `assignSlot()` method recurses: it calls itself with `bestTask.children` and the current slot as `parentSlot`.

## Refactoring Status

The app has three feature domains. Only tasks are actively being rebuilt:

| Feature | Status | Location |
|---------|--------|----------|
| Task scheduling | **Active** — Room + MVVM + Fragments | `src/` |
| Budget/Finance | Not migrated | `old/controller/budgetTab/`, `old/entities/` |
| Meal planning | Not migrated | `old/controller/mealTab/`, `old/entities/` |

The `old/` directory contains 80+ Java files spanning widgets, scheduling, budget management (with Claude API integration), meal planning (recipes/ingredients), and a custom SQLite repo layer with hand-written parsers.

## Known Bugs
- **UUID PK migration incomplete** — All `@PrimaryKey` fields were changed from auto-generated `Long` to `String` (UUID), but FK/reference fields still use `Long`: `TaskRelation.child`/`.parent`, `TaskSlot.taskId`/`.parent`, `TaskPrefSlot.taskId`, `TaskFollowUp.taskId`. Also affects: `Task.setId(long)` parameter type, `TaskDAO.writeCore()` return type (`long`), `TaskDAO.writeSlots()` return type (`long[]`), `SlotGenerator.assignSlot()` assignments to `slot.taskId` and `slot.parent`, and all `buildTree()` methods using `Map<Long, ...>` for String-keyed IDs. Code does not compile in current state.
- **`Task` convenience constructor doesn't initialize `parents`** — `parents` is left `null` (not an empty list). Room-constructed Tasks (via `readAll()`) get `parents` populated by `@Relation`, but manually constructed tasks (e.g. in `AppDatabase.onCreate()` seeding) don't. Currently latent: seed tasks only pass through `writeList()`/`flatten()` which iterate `children`, not `parents`. Would crash if manually constructed tasks were ever passed to `buildTree()`.
- **`TaskRelation` constructor arg order** — constructor is `TaskRelation(Long child, Long parent)` but `TaskDAO.writeList()` calls `new TaskRelation(task.core.id, child.core.id)` where `task` is the parent. Arguments are swapped, writing inverted parent-child relationships.
- **`Task.buildTree()` NPE** — if a child references a parent not in the result set, `mappedTasks.get(parent.parent)` returns null, then `.children.add(task)` throws NPE.
- **`TaskSlot.buildTree()` NPE** — same pattern: if `slot.parent` references a slot not in the list, `mappedSlots.get(slot.parent)` returns null.
- **`ViewSlotList.buildTree()` NPE** — same pattern: `mappedVS.get(parent)` can return null if parent ViewSlot was filtered out.
- **`ViewSlotList.flatten()` cycle detection abandons siblings** — `if (!visited.add(vs)) return result;` returns the entire accumulated result immediately on a cycle, skipping all remaining siblings in the current loop iteration. Should `continue` instead of `return`.
- **`AppDatabase.getInstance()` not thread-safe** — no `synchronized` block, concurrent first calls could create duplicate instances.
- **`TaskSlot.scheduled` never set** — `SlotGenerator.assignSlot()` creates slots but never sets `scheduled = true`. The checklist filter works by coincidence (checks `vs.slot.start != null` instead of `vs.slot.scheduled`).
- **`TaskViewModel.sortList()` NPE with unscheduled slots** — The `byTime` comparator calls `a.slot.start.compareTo(b.slot.start)` without null check. If `filters.displayUnscheduled` is `true` while `sorters.byTime` is also `true`, synthetic ViewSlots (with `slot.start == null`) will pass through the filter and NPE in the comparator. Currently safe by coincidence: `ListFragment` never enables both simultaneously.

## Not Yet Implemented

- No task creation/editing UI — `MainActivity`'s second tab is a placeholder (`ListFragment` again). A management fragment still needs to be built.
- Several `AndroidManifest` permissions (`RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `READ_CALENDAR`, `REQUEST_INSTALL_PACKAGES`) are dead declarations from the legacy architecture with no corresponding code in `src/`.
- `TaskDAO.deleteAllCore()` and `deleteCore(long id)` exist but are never called anywhere in the codebase.
- `TaskDAO.readByDue(LocalDate day)` exists but is never called.
- `Task.setParentId(long id)` exists but is never called.

## Key Technical Details

- **Java 17** with core library desugaring (minSdk 26, targetSdk 35)
- **Room 2.6.1** for persistence, annotation processor (not KSP)
- **XML layouts** in `res/layout/` (`activity_main`, `fragment_task_list`, `task_row`), menu in `res/menu/bottom_nav.xml`
- **Room DB version 2**, `exportSchema = false`, `fallbackToDestructiveMigration()` enabled — any schema change just needs a version bump (data will be destroyed on upgrade). No manual migrations exist
- **Package**: `com.autosecretary`
- **Single Activity + Fragments**: `views.MainActivity` hosts fragments via `FragmentContainerView`
- **Type converters** in `Converters.java` handle `LocalDate`, `LocalTime`, `DayOfWeek`, `Priority`, `Period` — all serialized to `String`
- **Preferences**: `readPrefTime(LocalDate, boolean)` returns `LocalTime` (defaults: `06:00` start, `16:00` end); `writePrefTime(DayOfWeek, boolean, LocalTime)` — note the asymmetry: read takes `LocalDate`, write takes `DayOfWeek`
- **ListRowAdapter**: Uses `R.dimen.indent_step` (24dp) × `viewSlot.depth` for tree indentation padding; `notifyDataSetChanged()` on every update (no DiffUtil)
