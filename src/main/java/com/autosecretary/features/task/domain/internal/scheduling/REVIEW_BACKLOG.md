# Review Backlog — task/domain/internal/scheduling

## Open Issues

### Pre-existing (carried forward)

[coupling] DefaultTaskSlotGenerator.java:248–253 — Four mutable instance fields (`newSlots`, `allTasksById`, `planningState`, `lastConflicts`) are re-initialised at the start of each public scheduling call via `initSchedulingRun`. This is an implicit, invisible per-call contract: miss one field in a new code path and the state corrupts silently. Bundle them into a per-call context object and pass it through private methods instead. **Why it matters:** any future public entry point must replicate the same init sequence or risk stale state from a previous call. (`schedulingDay` was resolved this run: converted to a local variable in `generateSlotsForDayInternal`.)

[drift] DefaultTaskSlotGenerator.java:258–292 + TaskScorer.java:79–93 — Both have telescoping constructor chains (5 in generator, 2 in scorer). Adding a new option requires touching all constructors in both files. **Suggested:** replace with builder or config object.

[coupling] DefaultTaskSlotGenerator.java:668 — `scorer.maintenance(task, ...)` is called inside `tryPlaceChain` during the evaluation loop. `maintenance()` mutates task state via `lifecycleManager.advancePeriods()` and `syncPeriodCompletions()`. This means evaluating a candidate placement has side effects on the task domain object, and repeated evaluations of the same task at different start times see mutated state. The day path avoids this by calling maintenance eagerly upfront. **Why it matters:** evaluation should be side-effect-free; the current pattern makes scoring order-dependent in the window path. **Suggested:** separate the state-mutating lifecycle advance from the read-only snapshot computation, or ensure maintenance is idempotent for repeated calls with the same day.

[consider] DefaultTaskSlotGenerator.java:799–837 — `buildTaskChains` only iterates the top-level `tasks` parameter (tree roots). Child tasks with prerequisites would be invisible to the chain-building logic. If children are expected to have prerequisites, they need to be included in the iteration. If not, this is fine as-is. **Why it matters:** adding a prerequisite to a child task would silently have no effect on chain scheduling.

[consider] DefaultTaskSlotGenerator.java:136–172 — `DisplacementCandidate` is a hand-written class with all-final fields and no inheritance, while all other value-carrier types in the same file (`ChainNode`, `ChainPlacement`, `SchedulingRunInit`) were converted to records in a prior pass. `DisplacementCandidate` is a natural record candidate. **Deferred:** converting would change `equals()`/`hashCode()` semantics from identity-based to value-based, and the type is used as a `Set<DisplacementCandidate>` key. Since `Task` and `TaskSlot` don't override `equals()`, the change is low-risk but not zero-risk. Revisit if the type is touched for other reasons.

## Resolved this run

- ✅ Made `schedulingDay` a local variable in `generateSlotsForDayInternal` — was a mutable instance field only ever set and read within the same method; making it local removes one entry from the implicit per-call init contract described in the `[coupling]` issue above.
- ✅ `new ArrayList<>()` → `List.of()` in 4-arg `generateSlotsForDay` overload — the calendar-events list is only iterated (never mutated) in `collectOccupiedIntervals`; immutable empty list is cleaner.
- ✅ `new ArrayList<>()` → `null` in `initSchedulingRun` call to `setTransitionStats` — `setTransitionStats(null)` already has documented semantics (disables follow-up boost entirely); passing an empty list was functionally equivalent but obscured intent.

## Previously resolved (prior runs)

- ✅ Converted `DaySchedulingContext` from hand-written class to record — consistent with prior conversions of `ChainNode`, `ChainPlacement`, `SchedulingRunInit`; all field accesses updated to record accessor methods.
- ✅ Replaced `.stream().anyMatch()` with explicit `for` loop in `appendNoGapConflictsForWindow` — consistent with rest of file.
- ✅ Added `List<Integer> nodeScores` to `ChainPlacement` record — carries per-node task scores through `tryPlaceChain` to `applyPlacement`; `slot.score` now stores the actual task score instead of an averaged `gainScore / chainSize`.

- ✅ Removed redundant `score` parameter from `finalizeAssignment` — `createScheduledSlot` already sets `slot.score`; the `slot.score = score` line in `finalizeAssignment` was always a no-op. Callers updated from `finalizeAssignment(task, slot, score)` to `finalizeAssignment(task, slot)`.
- ✅ Reordered constant declarations — `REASON_NO_MATCHING_GAP` was grouped after the `GROUP_PREFIX_*` constants, splitting the four `REASON_*` constants across two blocks. Moved to be adjacent to the other `REASON_*` declarations.
- ✅ Replaced `overlaps.stream().anyMatch()` with for loop in `scheduleFixedTasks` — last remaining stream usage in the file; now consistent with the rest.
- ✅ Removed overly defensive `task.core != null` ternaries in `addConflict` — every call site already guards `task.core != null` before calling; the checks implied a precondition that cannot fail and obscured the logic.
- ✅ Extracted duplicate `Math.max(0, candidate.lossScore)` in `computeAtomicLoss` — collapsed two if/else branches sharing the same expression into a single `boolean counted` variable plus one addition.
- ✅ Inverted negated continue in `hasUnmetPrerequisites` chain lookup — `if (!id.equals(...)) continue` → `if (id.equals(...)) { ...; break; }` — positive condition is easier to read.
- ✅ Merged two consecutive `Math.max` calls in `TaskScorer.computeMaxChildPriority` — introduced named `childMax` variable making the "effective subtree priority" concept explicit.
- ✅ Replaced mutable `ArrayList` with `List.of()` in `assignGlobalBestFit` — single-element wrapper list is never mutated by the callee.
- ✅ Index-based `i > 0` instead of `summary.length() > 0` in `logDaySummary` — directly expresses "not the first element" without inspecting buffer state.
- ✅ Replaced `StringBuilder`/`length() > 0` pattern in `logGlobalCompetition` displaced section — decomposed to per-element strings in a list, joined with `String.join(", ", ...)`.

- ✅ Added Javadoc to `Interval` private class (Comparable purpose and sort-order semantics)
- ✅ Added Javadoc to `SchedulingRunInit` record (return type of `initSchedulingRun`)
- ✅ Added Javadoc to `assignGlobalBestFit` (single-day adapter for the window loop)
- ✅ Added Javadoc to `findGaps` (sorted-precondition documented)
- ✅ Added Javadoc to `collectStartPoints` (why displaceable starts are included — key to displacement)
- ✅ Added Javadoc to `expandToFullChains` (atomicity guarantee and BFS reasoning)
- ✅ Added Javadoc to `findPreviousTaskIdForContext` (two-source merge for follow-up boost)
- ✅ Added Javadoc to `computeAtomicLoss` (atomic group deduplication logic)
- ✅ Added Javadoc to `removeDisplacedSlots` (dual mutation: task slot list + occupied list)
- ✅ Added Javadoc to `appendNoGapConflictsForWindow` (post-placement diagnostic pass)
- ✅ Added Javadoc to `collectOccupiedIntervals` (locking semantics and displacement eligibility)
- ✅ Added Javadoc to `toCandidate` (score priority, group ID derivation, protectedFromNormalTasks)
- ✅ Added Javadoc to `finalizeAssignment` (scorer.onSlotAssigned side effect documented)
- ✅ Added Javadoc to `dfsBuildChains` (cycle detection and backtracking step explained)
- ✅ Added Javadoc to `findLatestScheduledSlotBefore` (role in prerequisite checking)
- ✅ Added Javadoc to `TaskScorer.scanSlots` (all five fields of CompletionState explained)
- ✅ Added Javadoc to `TaskScorer.computeMaxChildPriority` (child-priority-inheritance intent)
- ✅ Added Javadoc to `TaskScorer.computePreferenceFitState` (day-constraint vs. no-constraint semantics)
- ✅ README: added displacement start-point strategy explanation (collectStartPoints section)
- ✅ README: documented `protectedFromNormalTasks` in types table
- ✅ README: added Log Output section with German vocabulary glossary
- ✅ README: added 7-day window assumption to multi-day mode description
- ✅ Merged double-Javadoc on `ChainPlacement` record — two consecutive `/** */` blocks existed; first (description) was not associated by Javadoc to the record, second (@param list) was. Merged into a single comment.
- ✅ Removed dead `parentSlot` parameter from `createScheduledSlot` — always passed `null` at both call sites; `slot.parent` was always set to `null`.
- ✅ Removed redundant null guard in `computeAtomicLoss` — `toDisplace` is populated exclusively from `expandToFullChains` which only adds non-null candidates; the `if (candidate == null) continue` guard was dead code.
- ✅ Removed redundant `slot.displacementScore = score` from `finalizeAssignment` — callers always set `displacementScore` explicitly after (or before) the call; the assignment was immediately overwritten in `applyPlacement` (chain slots) and was a no-op in `scheduleFixedTasks` (fixed tasks set the same value before the call).
- ✅ Removed dead code `TaskScorer.isPrefSlotConsumed()` — package-private method never called from anywhere in the codebase
- ✅ Cached budget eligibility in `TaskScorer.maintenance()` — previously called live `budgetEligibilityService.eligibilityFor()` on every `score()` invocation (O(n×m) calls per run); now computed once per task per run and stored in `TaskScoringSnapshot.budgetEligible`
- ✅ Fixed chain loss-score underpricing in `applyPlacement` — previously each chain slot stored `gainScore / chainSize` as `displacementScore`, so atomic deduplication in `computeAtomicLoss` counted only 1/N of the true chain value; now the first slot stores the full `gainScore` as `displacementScore` and subsequent slots store 0
- ✅ Converted `ChainNode` from hand-written class to record — consistent with other value carriers in the file (`SchedulingRunInit`, scoring records in `TaskScorer`)
- ✅ Converted `ChainPlacement` from hand-written class to record — `netScore` computed via canonical constructor delegation
- ✅ Extracted `GROUP_PREFIX_CHAIN`, `GROUP_PREFIX_SLOT`, `GROUP_PREFIX_FIXED` string constants — previously the same `"chain:"`, `"slot:"`, `"fixed:"` literals appeared independently in `toCandidate` and `finalizeAssignment`
- ✅ Created README.md with algorithm overview, two scheduling modes, scoring-layer table, key internal types, known issues reference, and public resources
- ✅ Added javadoc to `DefaultTaskSlotGenerator` package-private `generateSlotsForDay` overloads (test/internal-injection hooks)
- ✅ Added javadoc to `TaskScorer.ScoringContext` record (argument-bundle purpose)
- ✅ Added javadoc to `TaskScorer.PreferenceFitState` record (consumed-set semantics)
- ✅ Removed premature conflict logging from `hasUnmetPrerequisites` (lines 770-771, 777-778) — prevents misleading PREREQUISITE_BLOCKED conflicts for tasks that later succeed at different start times
- ✅ Fixed TERMIN slot conflict reason code at line 975 — changed from `REASON_OUTSIDE_WINDOW` to `REASON_NO_MATCHING_GAP` for slot collisions (semantically correct since the overlap is with a placed slot, not the window boundary)
- ✅ `buildTaskChains` hoisted out of `assignGlobalBestFitAcrossWindow` while loop — was O(n) per iteration; chains are stable across iterations
- ✅ Magic `Integer.MAX_VALUE / 2` for TERMIN slot score replaced with named constant `FIXED_TASK_SCORE`
- ✅ `SchedulingRunInit` converted from private static class to record (consistent with other value holders)
