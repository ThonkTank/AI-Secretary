# Task data layer

## Package layout

`features/task/data/` contains DAO and DAO-near persistence types only. The task
aggregate and Room-annotated task model live in `features/task/domain/model/`
because they are domain-owned objects even though Room persists them.

### DAOs

- `TaskDao` — main task CRUD. Reads/writes `domain/model` task objects and uses
  REPLACE conflict strategy for upsert-style writes.
- `TaskScheduleConfigDao` — schedule config CRUD.
- `TaskTransitionStatDao` — transition stat CRUD.

### Data-owned persistence types

- `TaskScheduleConfig` — persisted per-day schedule configuration
  (`task_schedule_config`).
- `TaskTransitionStat` — persisted learned transition statistics
  (`task_transition_stats`).

## Domain model persisted by this package

The following Room-annotated types are intentionally in `domain/model/`:

- `TaskCore` — primary task entity (`task_core`), with embedded Repetition,
  Progress, and History classes.
- `Task` — Room POJO assembled via `@Embedded` + `@Relation` from five tables
  (not an `@Entity`).
- `TaskSlot` — scheduled execution window (`task_slots`).
- `TaskPrefSlot` — preferred day/time pattern (`task_pref_slots`).
- `TaskCategory` — flat grouping of tasks (`task_category`); referenced by `TaskCore.categoryId`.
- `TaskPrerequisite` — prerequisite links (`task_prerequisites`).
- `TaskPlannedMeal` — task-to-meal association (`task_planned_meals`).
- `TaskPrefSlotFactory` — creates default `TaskPrefSlot` instances for new tasks.

## Key design choice: `Task` is a POJO, not an `@Entity`

`TaskCore` is the primary task `@Entity`. It maps to the `task_core` table.

`Task` is a Room POJO assembled by Room at query time from six tables using
`@Embedded` + `@Relation`. It is what callers work with because it carries all
associated data (slots, prefSlots, prerequisites, relations, planned meals) in
one object. Newly constructed `Task` instances also start with non-null empty
relation lists so editor code does not need to defensively initialise them.

All writes go through `TaskDao.write(Task)` or `TaskDao.writeList(List<Task>)`,
which break the object back into its parts and upsert each table individually.

See [Room `@Relation` documentation](https://developer.android.com/training/data-storage/room/relationships)
for how Room assembles multi-table queries.

## Recommended reading order

1. **`../domain/model/Task.java`** — the aggregate root; understand its
   structure before anything else.
2. **`../domain/model/TaskCore.java`** — the primary persisted task entity; all
   domain fields live here.
3. **`../domain/model/TaskSlot.java`** — the scheduled/completed time block;
   understand the two-phase lifecycle.
4. **`../domain/model/TaskPrefSlot.java`** — preferred timing patterns and
   adaptive adjustment.
5. **`TaskDao.java`** — the write/read interface; pay attention to the
   `writeDependents` javadoc which explains the delete-vs-upsert strategy for
   each sub-table.
6. **`TaskScheduleConfig.java`** and **`TaskTransitionStat.java`** — the
   data-owned task persistence records that are not part of the task aggregate.

## Placement convention

Place new DAO or DAO-near task persistence files directly in this package.
Place new task aggregate/model behavior in `domain/model/` or another domain
package. Use `read*/write*/delete*` method naming in DAOs (project convention:
`write*` = upsert via REPLACE).

## Public resources

- [Room overview](https://developer.android.com/training/data-storage/room) — persistence library
- [Room `@Relation`](https://developer.android.com/training/data-storage/room/relationships) — how multi-table POJOs work
- [Room `@Embedded`](https://developer.android.com/reference/androidx/room/Embedded) — how inner classes are flattened to columns
