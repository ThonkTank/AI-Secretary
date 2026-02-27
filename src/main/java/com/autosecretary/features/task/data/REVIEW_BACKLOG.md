# Review Backlog — features/task/data

## Open Issues

### Deferred (out-of-scope or cross-cutting)

- [nit] TaskPlannedMeal.java:26 — `recipeId` is `long`, inconsistent with the project-wide UUID String PK convention. Changing it requires updating the meal feature's persistence layer (cross-feature, higher-risk change).

### In-scope considerations

- [consider] TaskCore.java:61-63 — `repsPerDay()` on `TaskCore` is a one-line forwarding method that delegates to `repetition.repsPerDay()`. Callers already access `core.repsPerDay()`; inlining to `core.repetition.repsPerDay()` removes one level of indirection at the cost of slightly more verbose call sites. Low gain, low risk. Not worth fixing unless more forwarding methods accumulate.

- [consider] TaskPrerequisite.java:33-37 — The 2-arg constructor duplicates the first two lines of the 3-arg constructor. Could delegate via `this(taskId, prerequisiteId, 0)`, but since `minGapMinutes` already defaults to `0` at the field declaration, this is purely cosmetic.

