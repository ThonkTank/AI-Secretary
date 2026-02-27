# Review Backlog — `app/`

## Open Issues

### [consider] Inconsistent synchronization in AppCompositionRoot
**File:** `AppCompositionRoot.java`

`resetForDataReload()` (line 267) is `synchronized`, but `createTaskViewModelFactory()` (line 74) and `createBudgetViewModelFactory()` (line 171) are not, even though they also do lazy init with a null check. All call sites are on the UI thread (`MainActivity.reloadUiStateAfterDataReset()`), so the `synchronized` on `resetForDataReload()` buys nothing while suggesting a thread-safety guarantee that is not consistently applied.

**Simpler alternative:** Remove `synchronized` from `resetForDataReload()` and add a Javadoc comment stating the method must be called on the UI thread. Alternatively, add `synchronized` to the two factory methods to be fully consistent.

**Tradeoff:** The annotation approach is simpler (less code, no locking overhead) but relies on callers honouring the contract. Consistent synchronization is safer but adds boilerplate.

---

### [consider] `createTaskViewModelFactory` mixes construction with side-effect field assignments
**File:** `AppCompositionRoot.java:74–155`

The method constructs and returns a `TaskViewModelFactory` but also silently assigns two unrelated fields as side effects: `regenerateScheduleUseCase` and `taskSlotToggleMutation`. Callers of `getRegenerateScheduleUseCase()` and `getTaskSlotToggleMutation()` trigger factory creation as a hidden side effect. (`taskScheduleConfigRepository` was extracted to its own lazy getter.)

**Simpler alternative:** Give each lazily-initialized field its own getter with its own construction logic, eliminating the implicit coupling between `createTaskViewModelFactory()` and unrelated fields. Alternatively, rename `createTaskViewModelFactory()` to `initTaskGraph()` to signal it initializes more than one thing.

**Tradeoff:** Full extraction duplicates some DB/handler setup across methods. The rename is lower-effort and clarifies intent without restructuring.

