# Review Backlog — task/domain/internal/scheduling

## Open Issues

[warning] DefaultTaskSlotGenerator.java:162–169 — Five mutable instance fields (`newSlots`, `allTasksById`, `schedulingDay`, `planningState`, `lastConflicts`) are re-initialised at the start of each public scheduling call. This is an implicit, invisible per-call contract: miss one field in a new code path and the state corrupts silently. Bundle them into a per-call context object and pass it through private methods instead.

[warning] DefaultTaskSlotGenerator.java:233–270 vs 280–323 — `generateSlotsForWindow` and `generateSlotsForDayInternal` duplicate the same initialisation sequence (scorer reset, transitionStats, task-tree build, allTasksById). They diverge in maintenance timing: the window path calls `scorer.maintenance()` lazily inside `tryPlaceChain` per evaluation; the day path calls it eagerly upfront for all tasks and pre-populates `onSlotAssigned` for in-progress slots. A bug fix or change to one sequence won't propagate to the other.

[warning] DefaultTaskSlotGenerator.java + TaskScorer.java — Both have telescoping constructor chains (6 constructors in generator, 3 in scorer). Adding a new option requires touching all constructors in both files. Replace with builder or config object.

[nit] DefaultTaskSlotGenerator.java:153 — `DEFAULT_WINDOW` hardcodes 6:00/21:00, duplicating `DEFAULT_START`/`DEFAULT_END` in `TaskScheduleConfigRepository`. If defaults change, both sites must be updated. Extract shared constants or have the generator read defaults from the config repository.

[warning] DefaultTaskSlotGenerator.java:471-529 — `tryPlaceChain` is ~58 lines mixing cursor placement, prerequisite checking, overlap detection, chain expansion, and scoring. The `scorer.maintenance()` call inside the evaluation loop is a hidden side effect — callers evaluating candidates don't expect scoring state mutation. Extract the inner loop body.

[warning] DefaultTaskSlotGenerator.java:344-376 — `assignGlobalBestFitAcrossWindow` uses `while (true)` with no iteration guard. Relies on `best` becoming null as the schedule fills. If a bug prevents `applyPlacement` from reducing candidates, the loop runs forever. Add a max-iteration safety counter.

[nit] TaskSlot.java:58 — `displacementGroupType` stores one of three string literals ("CHAIN", "FIXED", "SINGLE") with no type safety; should be an enum. `DefaultTaskSlotGenerator` is the primary setter. Promoted from `task/data/REVIEW_BACKLOG.md`.

[consider] DefaultTaskSlotGenerator.java:561 — `computeAtomicLoss` guards `if (candidate == null) continue` inside a loop over `Set<DisplacementCandidate>`. The set is always built from non-null `new DisplacementCandidate(...)` calls in `expandToFullChains`, so the guard is dead. Removing it would eliminate noise, but the safety margin is low cost. Leave or remove at discretion.

