# Review Backlog — features/task/data

## Open Issues

### Deferred (out-of-scope or cross-cutting)

- [nit] TaskPlannedMeal.java:26 — `recipeId` is `long`, inconsistent with the project-wide UUID String PK convention. Changing it requires updating the meal feature's persistence layer (cross-feature, higher-risk change).

### In-scope considerations

- [consider] TaskCore.java:61-63 — `repsPerDay()` on `TaskCore` is a one-line forwarding method that delegates to `repetition.repsPerDay()`. Callers already access `core.repsPerDay()`; inlining to `core.repetition.repsPerDay()` removes one level of indirection at the cost of slightly more verbose call sites. Low gain, low risk. Not worth fixing unless more forwarding methods accumulate.

- [warning] TaskDao.java:60–71 — `writeDependents` uses delete-and-reinsert for prefSlots, prerequisites, and relations, but upserts without deletion for slots (intentional: preserve history) and plannedMeals (undocumented). If a planned meal is removed from in-memory `task.plannedMeals` and saved, the removed meal persists as an orphan in DB. Add `deletePlannedMealsByTaskId` + re-insert pattern, or document the asymmetry.

---

## Resolved Issues (this run — onboarding)

- ✅ `TaskSlot.java` — added comments to `parent`, `chainId`, `children`, `scheduled`, `score`, `realStart`, `realEnd`; added enum javadoc for `DisplacementGroupType`.
- ✅ `TaskCore.java` — added units to `cooldown` (days), `minDuration`/`maxDuration` (minutes), `fixedDuration` (minutes); documented `mealType`, `adaptive`, `History` fields; added `SchedulingType` enum javadoc with TERMIN "not yet exposed in UI" note; documented `Repetition` sub-fields.
- ✅ `TaskPrefSlot.java` — expanded docstring with adaptive EMA explanation, multi-pattern semantics, and storage note; added field comments for `days` and `start`.
- ✅ `data/README.md` — added "Key design choice: Task is a POJO, not an @Entity" section, recommended reading order, and public resource links (Room, @Relation, @Embedded).
