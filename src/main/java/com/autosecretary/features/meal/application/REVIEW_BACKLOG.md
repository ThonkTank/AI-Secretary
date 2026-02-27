# Review Backlog — meal/application

## Open Issues

[consider] TaskMealIntegrationService.java:28,128 — `DEFAULT_MEMBER_ID = 0L` and `itemId = 0L` are passed to `ConsumptionLog.Builder` for task-triggered meal completions. Any member-filtered query over `ConsumptionLog` will silently exclude these entries (memberId=0 matches no real member). Acceptable short-term placeholder, but a design decision is needed on whether task-driven consumption should contribute to per-member nutrition tracking or remain in an "unassigned" bucket. Deferred — requires a broader product decision.

[coupling] TaskMealIntegrationService.java:3-4 — imports `task.data.Task` and `task.data.TaskPlannedMeal` directly from a different feature's data layer. The dependency arrow is `meal.application → task.data`, which crosses both a feature boundary and a layer boundary. Fix: define a `TaskMealDelegate` interface in `meal.domain` (or `task.application`) with the relevant fields, and have the task feature provide the implementation. Deferred — requires coordinated change across two features.

[nit] LegacyMealImportService.java — Nine `import*` methods each repeat the same `for (int i = 0; i < rows.size(); i++) { Map<String,Object> row = rows.get(i); ... report.markMigrated(source); }` skeleton. If loop-level error handling, retry logic, or progress callbacks are ever added they must be replicated nine times. Fix: extract a private `importRows(source, rows, report, RowHandler)` helper accepting a functional interface. Low priority — this is a stable one-shot import path unlikely to accumulate new entity types.

[nit] LegacyMealImportService.java:371-453 — Parallel parsing helpers (`asString`, `asLong`, `asInt`, `asDouble`, `asBoolean`) duplicate `MapperSupport` with no documented reason for divergence. Comment at line 366 justifies `asEnum`/`asDate`/`asDateTime` divergence but not the numeric/boolean helpers. Additionally, `asBoolean` diverges subtly from `MapperSupport.asBoolean` in `"0"` handling. Fix: unify the non-divergent helpers or extend the justification comment.
