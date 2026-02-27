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

## Placement convention

Place new task data-layer files directly in this package. Follow the `Task*` naming prefix.
Use `read*/write*/delete*` method naming in DAOs (project convention: `write*` = upsert via REPLACE).
