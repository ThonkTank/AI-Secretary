# Review Backlog — features/task/data

## Open Issues

### Deferred (out-of-scope or cross-cutting)

- [nit] TaskPlannedMeal.java:26 — `recipeId` is `long`, inconsistent with the project-wide UUID String PK convention. Changing it requires updating the meal feature's persistence layer (cross-feature, higher-risk change).

### In-scope considerations

- [consider] TaskCore.java:61-63 — `repsPerDay()` on `TaskCore` is a one-line forwarding method that delegates to `repetition.repsPerDay()`. Callers already access `core.repsPerDay()`; inlining to `core.repetition.repsPerDay()` removes one level of indirection at the cost of slightly more verbose call sites. Low gain, low risk. Not worth fixing unless more forwarding methods accumulate.

- [warning] TaskDAO.java:60–71 — `writeDependents` uses delete-and-reinsert for prefSlots, prerequisites, and relations, but upserts without deletion for slots (intentional: preserve history) and plannedMeals (undocumented). If a planned meal is removed from in-memory `task.plannedMeals` and saved, the removed meal persists as an orphan in DB. Add `deletePlannedMealsByTaskId` + re-insert pattern, or document the asymmetry.
