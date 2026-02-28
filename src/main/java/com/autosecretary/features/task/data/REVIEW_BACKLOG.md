# Review Backlog — features/task/data

## Open Issues

### Deferred (out-of-scope or cross-cutting)

- [nit] TaskPlannedMeal.java:26 — `recipeId` is `long`, inconsistent with the project-wide UUID String PK convention. Changing it requires updating the meal feature's persistence layer (cross-feature, higher-risk change).

### In-scope considerations

- [consider] TaskCore.java:88-90 — `repsPerDay()` on `TaskCore` is a one-line forwarding method that delegates to `repetition.repsPerDay()`. Callers already access `core.repsPerDay()`; inlining to `core.repetition.repsPerDay()` removes one level of indirection at the cost of slightly more verbose call sites. Low gain, low risk. Not worth fixing unless more forwarding methods accumulate.

- [documented] TaskDao.java:65-68 — `writePlannedMeals` uses upsert without deletion (intentional per comment). Behavior is documented: removed planned meals persist as orphans because they may be pre-staged. No fix needed; behavior is intentional and documented.

---

## Resolved Issues (previous run)

- ✅ `TaskSlot.java` — added comments to `parent`, `chainId`, `children`, `scheduled`, `score`, `realStart`, `realEnd`; added enum javadoc for `DisplacementGroupType`.
- ✅ `TaskCore.java` — added units to `cooldown` (days), `minDuration`/`maxDuration` (minutes), `fixedDuration` (minutes); documented `mealType`, `adaptive`, `History` fields; added `SchedulingType` enum javadoc; documented `Repetition` sub-fields.
- ✅ `TaskPrefSlot.java` — expanded docstring with adaptive EMA explanation, multi-pattern semantics, and storage note; added field comments.
