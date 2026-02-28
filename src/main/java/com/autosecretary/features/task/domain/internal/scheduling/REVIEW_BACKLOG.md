# Review Backlog — task/domain/internal/scheduling

## Open Issues

### Pre-existing (carried forward)

[coupling] DefaultTaskSlotGenerator.java:248–253 — Five mutable instance fields (`newSlots`, `allTasksById`, `schedulingDay`, `planningState`, `lastConflicts`) are re-initialised at the start of each public scheduling call via `initSchedulingRun`. This is an implicit, invisible per-call contract: miss one field in a new code path and the state corrupts silently. Bundle them into a per-call context object and pass it through private methods instead. **Why it matters:** any future public entry point must replicate the same init sequence or risk stale state from a previous call.

[drift] DefaultTaskSlotGenerator.java:258–292 + TaskScorer.java:79–93 — Both have telescoping constructor chains (5 in generator, 2 in scorer). Adding a new option requires touching all constructors in both files. **Suggested:** replace with builder or config object.

[coupling] DefaultTaskSlotGenerator.java:668 — `scorer.maintenance(task, ...)` is called inside `tryPlaceChain` during the evaluation loop. `maintenance()` mutates task state via `lifecycleManager.advancePeriods()` and `syncPeriodCompletions()`. This means evaluating a candidate placement has side effects on the task domain object, and repeated evaluations of the same task at different start times see mutated state. The day path avoids this by calling maintenance eagerly upfront. **Why it matters:** evaluation should be side-effect-free; the current pattern makes scoring order-dependent in the window path. **Suggested:** separate the state-mutating lifecycle advance from the read-only snapshot computation, or ensure maintenance is idempotent for repeated calls with the same day.

[consider] DefaultTaskSlotGenerator.java:799–837 — `buildTaskChains` only iterates the top-level `tasks` parameter (tree roots). Child tasks with prerequisites would be invisible to the chain-building logic. If children are expected to have prerequisites, they need to be included in the iteration. If not, this is fine as-is. **Why it matters:** adding a prerequisite to a child task would silently have no effect on chain scheduling.


## Resolved this run

- ✅ Removed dead code `TaskScorer.isPrefSlotConsumed()` — package-private method never called from anywhere in the codebase

- ✅ Cached budget eligibility in `TaskScorer.maintenance()` — previously called live `budgetEligibilityService.eligibilityFor()` on every `score()` invocation (O(n×m) calls per run); now computed once per task per run and stored in `TaskScoringSnapshot.budgetEligible`
- ✅ Fixed chain loss-score underpricing in `applyPlacement` — previously each chain slot stored `gainScore / chainSize` as `displacementScore`, so atomic deduplication in `computeAtomicLoss` counted only 1/N of the true chain value; now the first slot stores the full `gainScore` as `displacementScore` and subsequent slots store 0
- ✅ Converted `ChainNode` from hand-written class to record — consistent with other value carriers in the file (`SchedulingRunInit`, scoring records in `TaskScorer`)
- ✅ Converted `ChainPlacement` from hand-written class to record — `netScore` computed via canonical constructor delegation
- ✅ Extracted `GROUP_PREFIX_CHAIN`, `GROUP_PREFIX_SLOT`, `GROUP_PREFIX_FIXED` string constants — previously the same `"chain:"`, `"slot:"`, `"fixed:"` literals appeared independently in `toCandidate` and `finalizeAssignment`

## Previously resolved (prior runs)

- ✅ Created README.md with algorithm overview, two scheduling modes, scoring-layer table, key internal types, known issues reference, and public resources
- ✅ Added javadoc to `DefaultTaskSlotGenerator` package-private `generateSlotsForDay` overloads (test/internal-injection hooks)
- ✅ Added javadoc to `TaskScorer.ScoringContext` record (argument-bundle purpose)
- ✅ Added javadoc to `TaskScorer.PreferenceFitState` record (consumed-set semantics)
- ✅ Removed premature conflict logging from `hasUnmetPrerequisites` (lines 770-771, 777-778) — prevents misleading PREREQUISITE_BLOCKED conflicts for tasks that later succeed at different start times
- ✅ Fixed TERMIN slot conflict reason code at line 975 — changed from `REASON_OUTSIDE_WINDOW` to `REASON_NO_MATCHING_GAP` for slot collisions (semantically correct since the overlap is with a placed slot, not the window boundary)
- ✅ `buildTaskChains` hoisted out of `assignGlobalBestFitAcrossWindow` while loop — was O(n) per iteration; chains are stable across iterations
- ✅ Magic `Integer.MAX_VALUE / 2` for TERMIN slot score replaced with named constant `FIXED_TASK_SCORE`
- ✅ `SchedulingRunInit` converted from private static class to record (consistent with other value holders)
