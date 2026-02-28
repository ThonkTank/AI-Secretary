# Review Backlog — task/domain/internal/scheduling

## Open Issues

### Architectural / Structural (pre-existing, outside docs scope)

[coupling] DefaultTaskSlotGenerator.java:171–174 — Five mutable instance fields (`newSlots`, `allTasksById`, `schedulingDay`, `planningState`, `lastConflicts`) are re-initialised at the start of each public scheduling call via `initSchedulingRun`. This is an implicit, invisible per-call contract: miss one field in a new code path and the state corrupts silently. Bundle them into a per-call context object and pass it through private methods instead. **Why it matters:** any future public entry point must replicate the same init sequence or risk stale state from a previous call.

[drift] DefaultTaskSlotGenerator.java:178–212 + TaskScorer.java:60–74 — Both have telescoping constructor chains (5 in generator, 2 in scorer). Adding a new option requires touching all constructors in both files. **Suggested:** replace with builder or config object.

[coupling] DefaultTaskSlotGenerator.java:526 — `scorer.maintenance(task, ...)` is called inside `tryPlaceChain` during the evaluation loop. `maintenance()` mutates task state via `lifecycleManager.advancePeriods()` and `syncPeriodCompletions()`. This means evaluating a candidate placement has side effects on the task domain object, and repeated evaluations of the same task at different start times see mutated state. The day path avoids this by calling maintenance eagerly upfront. **Why it matters:** evaluation should be side-effect-free; the current pattern makes scoring order-dependent in the window path. **Suggested:** separate the state-mutating lifecycle advance from the read-only snapshot computation, or ensure maintenance is idempotent for repeated calls with the same day.

[consider] DefaultTaskSlotGenerator.java:643–681 — `buildTaskChains` only iterates the top-level `tasks` parameter (tree roots). Child tasks with prerequisites would be invisible to the chain-building logic. If children are expected to have prerequisites, they need to be included in the iteration. If not, this is fine as-is. **Why it matters:** adding a prerequisite to a child task would silently have no effect on chain scheduling.

### Onboarding / Documentation (fixed this run)

- ✅ Created README.md with algorithm overview, two scheduling modes, scoring-layer table, key internal types, known issues reference, and public resources
- ✅ Added javadoc to `DefaultTaskSlotGenerator` package-private `generateSlotsForDay` overloads (test/internal-injection hooks)
- ✅ Added javadoc to `TaskScorer.ScoringContext` record (argument-bundle purpose)
- ✅ Added javadoc to `TaskScorer.PreferenceFitState` record (consumed-set semantics)
