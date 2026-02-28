# Review Backlog — features/task/data

## Open Issues

### Deferred (out-of-scope or cross-cutting)

- [nit] TaskPlannedMeal.java:26 — `recipeId` is `long`, inconsistent with the project-wide UUID String PK convention. Changing it requires updating the meal feature's persistence layer (cross-feature, higher-risk change).

### In-scope considerations

- [consider] TaskCore.java:88-90 — `repsPerDay()` on `TaskCore` is a one-line forwarding method that delegates to `repetition.repsPerDay()`. Callers already access `core.repsPerDay()`; inlining to `core.repetition.repsPerDay()` removes one level of indirection at the cost of slightly more verbose call sites. Low gain, low risk. Not worth fixing unless more forwarding methods accumulate.

- [consider] TaskCore.java:152 — `Progress.progressTarget()` is a trivial one-liner that widens `int target` to `double`. Used in exactly one place (`Task.java:58`) where the double type matters for division. Could be inlined as `(double) core.progress.target`. The semantic name adds minor clarity. Very low gain.

- [consider] TaskCore.java:154-156 — `Progress.hasTrackingTarget()` is a trivial `return target > 0;`. Used internally in `completionProgressUnits()` and externally in `TaskScorer.java:471`. Could be inlined at both sites. The name is marginally more expressive than the condition.

- [consider] TaskCore.java:168-172 — `Progress.recordTimingSample()` guards against `totalTime < 0` and `totalProgress < 0`, which are impossible from normal usage (both only ever increase). This is overly defensive for an internal method on a domain object. However, data comes from Room (could be corrupted on schema fallback), so the cost is near-zero. Borderline acceptable.

---

## Resolved Issues (previous run)

- ✅ `TaskSlot.java` — added comments to `parent`, `chainId`, `children`, `scheduled`, `score`, `realStart`, `realEnd`; added enum javadoc for `DisplacementGroupType`.
- ✅ `TaskCore.java` — added units to `cooldown` (days), `minDuration`/`maxDuration` (minutes), `fixedDuration` (minutes); documented `mealType`, `adaptive`, `History` fields; added `SchedulingType` enum javadoc; documented `Repetition` sub-fields.
- ✅ `TaskPrefSlot.java` — expanded docstring with adaptive EMA explanation, multi-pattern semantics, and storage note; added field comments.
- ✅ `TaskDao.java:109` — renamed parameter `slots` → `slot` in `writeSlot(TaskSlot slot)`.
- ✅ `TaskCore.java:152` — renamed `Progress.repsRequired()` → `progressTarget()` to accurately reflect that it returns the numeric progress target, not a repetition count. Caller `Task.java:58` updated.
- ✅ `TaskDao.java:138-144` — fixed misleading javadoc on `deletePrerequisitesByDependencyId`: replaced "removes all tasks" with "removes all prerequisite links".
- ✅ `TaskCore.java:117` — changed `Repetition.remainingReps()` return type from `double` to `int`; the subtraction `reps - periodCompletions` is always an integer value. Caller `Task.java:61` unchanged (int auto-widened to double at return site).
- ✅ `TaskPlannedMeal.java:31` — `markCompleted()` returned `this` (fluent style) but the sole caller (`Task.java:95`) discarded the return value. Changed return type to `void`.
