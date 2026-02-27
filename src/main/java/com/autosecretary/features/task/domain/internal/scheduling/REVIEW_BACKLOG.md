# Review Backlog — task/domain/internal/scheduling

## Open Issues

[coupling] DefaultTaskSlotGenerator.java:161–164 — Five mutable instance fields (`newSlots`, `allTasksById`, `schedulingDay`, `planningState`, `lastConflicts`) are re-initialised at the start of each public scheduling call. This is an implicit, invisible per-call contract: miss one field in a new code path and the state corrupts silently. Bundle them into a per-call context object and pass it through private methods instead. **Why it matters:** any future public entry point must replicate the same init sequence or risk stale state from a previous call.

[drift] DefaultTaskSlotGenerator.java:228–265 vs 275–318 — `generateSlotsForWindow` and `generateSlotsForDayInternal` duplicate the same initialisation sequence (scorer reset, transitionStats, task-tree build, allTasksById). They diverge in maintenance timing: the window path calls `scorer.maintenance()` lazily inside `tryPlaceChain` per evaluation; the day path calls it eagerly upfront for all tasks and pre-populates `onSlotAssigned` for in-progress slots. A bug fix or change to one sequence won't propagate to the other. **Suggested:** extract a shared `initSchedulingRun()` helper.

[drift] DefaultTaskSlotGenerator.java:168–202 + TaskScorer.java:60–74 — Both have telescoping constructor chains (6 in generator, 2 in scorer). Adding a new option requires touching all constructors in both files. **Suggested:** replace with builder or config object.

[coupling] DefaultTaskSlotGenerator.java:148–152 — `DEFAULT_WINDOW` hardcodes 6:00/21:00, duplicating `DEFAULT_START`/`DEFAULT_END` in `TaskScheduleConfigRepository`. If defaults change, both sites must be updated. **Suggested:** extract shared constants or have the generator read defaults from the config repository.

[coupling] DefaultTaskSlotGenerator.java:505 — `scorer.maintenance(task, ...)` is called inside `tryPlaceChain` during the evaluation loop. `maintenance()` mutates task state via `lifecycleManager.advancePeriods()` and `syncPeriodCompletions()`. This means evaluating a candidate placement has side effects on the task domain object, and repeated evaluations of the same task at different start times see mutated state. The day path avoids this by calling maintenance eagerly upfront (line 293). **Why it matters:** evaluation should be side-effect-free; the current pattern makes scoring order-dependent in the window path. **Suggested:** separate the state-mutating lifecycle advance from the read-only snapshot computation, or ensure maintenance is idempotent for repeated calls with the same day.

[consider] DefaultTaskSlotGenerator.java:622–660 — `buildTaskChains` only iterates the top-level `tasks` parameter (tree roots). Child tasks with prerequisites would be invisible to the chain-building logic. If children are expected to have prerequisites, they need to be included in the iteration. If not, this is fine as-is. **Why it matters:** adding a prerequisite to a child task would silently have no effect on chain scheduling.

