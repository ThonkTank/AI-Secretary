# Review Backlog — `app/`

## Open Issues

### [consider] Inconsistent synchronization in AppCompositionRoot
**File:** `AppCompositionRoot.java`

`createTaskViewModelFactory()` (line 73) and `createBudgetViewModelFactory()` (line 165) are not synchronized, even though they do lazy init with null checks. All call sites are on the UI thread (`MainActivity.reloadUiStateAfterDataReset()`), so the mismatch is harmless but signals different threading contracts for similar methods.

**Simpler alternative:** Remove `synchronized` from `resetForDataReload()` and add a Javadoc comment stating the method must be called on the UI thread. Alternatively, add `synchronized` to the two factory methods for full consistency.

**Tradeoff:** The annotation approach is simpler; consistent synchronization is safer but adds boilerplate.

---

### [consider] `createTaskViewModelFactory` mixes construction with side-effect field assignments
**File:** `AppCompositionRoot.java:73–148`

The method constructs and returns a `TaskViewModelFactory` but also silently assigns `regenerateScheduleUseCase` and `taskSlotToggleMutation` as side effects. Callers of `getRegenerateScheduleUseCase()` and `getTaskSlotToggleMutation()` trigger factory creation as a hidden side effect.

**Simpler alternative:** Give each lazily-initialized field its own getter, or rename `createTaskViewModelFactory()` to `initTaskGraph()` to signal it initializes more than one thing.

**Tradeoff:** Full extraction duplicates some DB/handler setup. The rename is lower-effort.

