# Task data layer

## Package layout

`features/task/data/` is flat by design. All entities, DAOs, and helpers share a `Task*` prefix
and the total file count (~13) is small enough that sub-packages would add navigation depth
without meaningfully improving discoverability.

### Entities (Room `@Entity` and `@Embedded`)
- `TaskCore` — primary entity (`task_core`), with `@Embedded` inner classes for Repetition, Progress, History
- `Task` — Room POJO assembled via `@Embedded` + `@Relation` from five tables (not an `@Entity`)
- `TaskSlot` — scheduled execution window (`task_slots`)
- `TaskPrefSlot` — preferred day/time pattern (`task_pref_slots`)
- `TaskRelation` — parent-child hierarchy (`task_relation`)
- `TaskPrerequisite` — prerequisite links (`task_prerequisites`)
- `TaskPlannedMeal` — task-to-meal association (`task_planned_meals`)
- `TaskScheduleConfig` — schedule configuration (`task_schedule_config`)
- `TaskTransitionStat` — transition statistics (`task_transition_stats`)

### DAOs
- `TaskDao` — main task CRUD (all writes use REPLACE = upsert)
- `TaskScheduleConfigDao` — schedule config CRUD
- `TaskTransitionStatDao` — transition stat CRUD

### Helpers
- `TaskPrefSlotFactory` — creates default `TaskPrefSlot` instances for new tasks

## Key design choice: `Task` is a POJO, not an `@Entity`

This is the most non-obvious decision in the data layer.

`TaskCore` is the **only** `@Entity` for tasks. It maps to the `task_core` table.

`Task` is a **Room POJO** (plain Java object) assembled by Room at query time from five tables
using `@Embedded` + `@Relation`. It is what callers actually work with because it carries all
associated data (slots, prefSlots, prerequisites, relations, planned meals) in one object.
Newly constructed `Task` instances also start with non-null empty relation lists so editor code
does not need to defensively initialise them.

```java
// Task is assembled from 5 tables — you never insert/update it directly
public class Task {
    @Embedded public TaskCore core = new TaskCore();        // from task_core
    @Relation(...) public List<TaskSlot> slots = new ArrayList<>();
    @Relation(...) public List<TaskPrefSlot> prefSlots = new ArrayList<>();
    @Relation(...) public List<TaskRelation> parents = new ArrayList<>();
    @Relation(...) public List<TaskPrerequisite> prerequisites = new ArrayList<>();
}
```

All writes go through `TaskDao.write(Task)` or `TaskDao.writeList(List<Task>)`, which break
the object back into its parts and upsert each table individually.

See [Room `@Relation` documentation](https://developer.android.com/training/data-storage/room/relationships)
for how Room assembles multi-table queries.

## Recommended reading order

1. **`Task.java`** — the aggregate root; understand its structure before anything else.
2. **`TaskCore.java`** — the actual persisted entity; all the domain fields live here.
3. **`TaskSlot.java`** — the scheduled/completed time block; understand the two-phase lifecycle.
4. **`TaskPrefSlot.java`** — preferred timing patterns and adaptive adjustment.
5. **`TaskDao.java`** — the write/read interface; pay attention to the `writeDependents` javadoc
   which explains the delete-vs-upsert strategy for each sub-table.
6. **`TaskRelation.java`**, **`TaskPrerequisite.java`** — parent-child and prerequisite links.
   Both use composite primary keys based on the linked task IDs rather than surrogate UUIDs.

## Placement convention

Place new task data-layer files directly in this package. Follow the `Task*` naming prefix.
Use `read*/write*/delete*` method naming in DAOs (project convention: `write*` = upsert via REPLACE).

## Public resources

- [Room overview](https://developer.android.com/training/data-storage/room) — persistence library
- [Room `@Relation`](https://developer.android.com/training/data-storage/room/relationships) — how multi-table POJOs work
- [Room `@Embedded`](https://developer.android.com/reference/androidx/room/Embedded) — how inner classes are flattened to columns
