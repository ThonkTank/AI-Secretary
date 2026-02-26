# Review Backlog — `app/`

## Open Issues

### [warning] Factory methods not synchronized despite `resetForDataReload()` being synchronized
**Files:** `AppCompositionRoot.java:71, 174, 247`

`resetForDataReload()` is `synchronized` but `createTaskViewModelFactory()` and `createBudgetViewModelFactory()` are not. A concurrent reset during factory construction can produce a partially-wired object graph that misses the reset.

**Fix:** Either synchronize the factory methods, or accept that `reset` is only called on the UI thread (document it clearly).

### [warning] `createTaskViewModelFactory` is 77 lines
**File:** `AppCompositionRoot.java:72-149`

Mixes object construction with side-effect field assignments (`this.taskScheduleConfigService = ...`, `regenerateScheduleUseCase = ...`). These are two responsibilities colliding in one method. Extract the dependency construction from the field wiring.
