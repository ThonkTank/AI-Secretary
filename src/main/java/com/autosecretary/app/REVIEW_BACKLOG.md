# Review Backlog — `app/`

## Open Issues

### [consider] `getTaskViewModelFactory` mixes construction with side-effect field assignments
**File:** `AppCompositionRoot.java:75–154`

The method constructs and returns a `TaskViewModelFactory` but also silently assigns `regenerateScheduleUseCase` and `taskSlotToggleMutation` as side effects. Callers of `getRegenerateScheduleUseCase()` and `getTaskSlotToggleMutation()` trigger full task-graph initialization as a hidden side effect via delegation to this method. *Note: method was renamed `create→get` in a prior review cycle; naming inconsistency is resolved.*

**Suggested alternative:** Give `taskSlotToggleMutation` and `regenerateScheduleUseCase` their own lazy-init logic in their respective getters, separated from the factory construction path.

**Tradeoff:** Splitting out the getters duplicates some DB/handler setup. Lower-effort alternative: extract a private `initTaskGraph()` that populates all three fields, and have all three public getters call it.
