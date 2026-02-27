# Review Backlog — `task/application/`

---

### [drift] Application layer imports UI-layer widget classes
**Files:**
- `CheckOffTaskUseCase.java:5,52` — imports and calls `BudgetWidgetProvider.notifyWidgetUpdate()`
- `application/internal/scheduling/DailyPlanningReceiver.java:10,32` — imports and calls `TaskWidgetProvider.notifyWidgetUpdate()`

**Why it matters:** Both callers are in the application layer; both widget providers live in the UI layer. This reverses the expected UI → Application dependency direction. Adding a new widget type requires touching scheduling and use-case code. The pattern may propagate to other use cases.

**Suggested alternative:** Introduce a `WidgetRefreshNotifier` abstraction in `application/` (or `app/`) that wraps the broadcast; both callers depend on the abstraction, not on concrete widget classes. Requires coordinated change across at least two sites — defer until both can be migrated together.

**Note:** Promoted from `application/internal/scheduling/REVIEW_BACKLOG.md` — the issue covers files in both `scheduling/` and the root application package.

---

### [drift] `AdjustTaskProgressUseCase` sets `task.core.completed` directly, bypassing domain services
**File:** `AdjustTaskProgressUseCase.java:49-51`

**What:** When progress reaches its target, the use case writes `task.core.completed = true` and `slot.completed = true` directly. The domain's `TaskCompletionService` and `TaskLifecycleManager` are not called — so streak, history, and adaptive adjustments are skipped for progress-based completions.

**Why it matters:** Two code paths can complete a task: `CheckOffTaskUseCase` (via `TaskCompletionService` → full lifecycle) and `AdjustTaskProgressUseCase` (inline field write → no lifecycle). A task completed via progress will never update its streak or carryoverDebt. This inconsistency will widen as the domain grows.

**Suggested alternative:** When `next >= target`, call `TaskCompletionService.checkOff()` (or a dedicated lifecycle hook) instead of writing `completed` directly. If the two-phase STARTED/COMPLETED logic doesn't fit the progress use case, extract a `completeDirectly(task, slot)` method in `TaskCompletionService` that handles lifecycle without phase tracking.

**Note:** Requires understanding `TaskCompletionService` behavior in depth before fixing. Defer until the completion lifecycle is stable.

---
