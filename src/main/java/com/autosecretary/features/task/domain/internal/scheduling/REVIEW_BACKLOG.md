# Review Backlog — task/domain/internal/scheduling

## Open Issues

[warning] DefaultTaskSlotGenerator.java:162–169 — Five mutable instance fields (`newSlots`, `allTasksById`, `schedulingDay`, `planningState`, `lastConflicts`) are re-initialised at the start of each public scheduling call. This is an implicit, invisible per-call contract: miss one field in a new code path and the state corrupts silently. Bundle them into a per-call context object and pass it through private methods instead.

[warning] DefaultTaskSlotGenerator.java:233–270 vs 280–323 — `generateSlotsForWindow` and `generateSlotsForDayInternal` duplicate the same initialisation sequence (scorer reset, transitionStats, task-tree build, allTasksById). They diverge in maintenance timing: the window path calls `scorer.maintenance()` lazily inside `tryPlaceChain` per evaluation; the day path calls it eagerly upfront for all tasks and pre-populates `onSlotAssigned` for in-progress slots. A bug fix or change to one sequence won't propagate to the other.

[warning] DefaultTaskSlotGenerator.java + TaskScorer.java — Both have telescoping constructor chains (6 constructors in generator, 4 in scorer). Adding a new option requires touching all constructors in both files. Replace with builder or config object.

[warning] TaskScorer.java:518–519 — `minDayDistance() < Integer.MAX_VALUE` guard for sentinel value. Semantically should be `!= Integer.MAX_VALUE` or use a named constant, though practically unreachable.

[warning] TaskScorer.java:95–170 — `CompletionState.withIncrementedScheduledToday()` and `TaskScoringSnapshot.withIncrementedScheduledToday()` have identical names but one is a leaf update and the other a delegation point. Rename the leaf to `incrementScheduledToday()` to disambiguate.

[nit] TaskScorer.java:400 vs 406 — `deadlineExpired` uses `day.isAfter(deadline)` (exclusive) while `applyUrgencyMultiplier` uses `remainingDays <= 0` (inclusive). Deadline day gets overdue scoring but is NOT blocked — intentional but undocumented. Add a comment.

[nit] DefaultTaskSlotGenerator.java:153 — `DEFAULT_WINDOW` hardcodes 6:00/21:00, duplicating `DEFAULT_START`/`DEFAULT_END` in `TaskScheduleConfigRepository`. If defaults change, both sites must be updated. Extract shared constants or have the generator read defaults from the config repository.

[warning] DefaultTaskSlotGenerator.java:471-529 — `tryPlaceChain` is ~58 lines mixing cursor placement, prerequisite checking, overlap detection, chain expansion, and scoring. The `scorer.maintenance()` call inside the evaluation loop is a hidden side effect — callers evaluating candidates don't expect scoring state mutation. Extract the inner loop body.

[warning] DefaultTaskSlotGenerator.java:344-376 — `assignGlobalBestFitAcrossWindow` uses `while (true)` with no iteration guard. Relies on `best` becoming null as the schedule fills. If a bug prevents `applyPlacement` from reducing candidates, the loop runs forever. Add a max-iteration safety counter.

