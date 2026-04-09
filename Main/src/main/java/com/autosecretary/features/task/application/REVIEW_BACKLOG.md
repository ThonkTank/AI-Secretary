# Review Backlog — `task/application/`

---


### [warning] Introduce WidgetRefreshNotifier abstraction to reverse dependency @skill:review-architecture
**Files:**
- `CheckOffTaskUseCase.java:5,52` — imports and calls `BudgetWidgetProvider.notifyWidgetUpdate()`
- `application/internal/alarms/DailyPlanningReceiver.java:10,32` — imports and calls `TaskWidgetProvider.notifyWidgetUpdate()`

**Why it matters:** Both callers are in the application layer; both widget providers live in the UI layer. This reverses the expected UI → Application dependency direction. Adding a new widget type requires touching scheduling and use-case code. The pattern may propagate to other use cases.

**Suggested alternative:** Introduce a `WidgetRefreshNotifier` abstraction in `application/` (or `app/`) that wraps the broadcast; both callers depend on the abstraction, not on concrete widget classes. Requires coordinated change across at least two sites — defer until both can be migrated together.

**Note:** Promoted from `application/internal/scheduling/REVIEW_BACKLOG.md` — the issue covers files in both `alarms/` and the root application package.

**Additional call site (same-layer, lower severity):** `ui/list/TaskViewModel.java:277` also calls
`TaskWidgetProvider.notifyWidgetUpdate()` directly. This is a UI→UI dependency (less architecturally
harmful), but it must also be updated if a `WidgetRefreshNotifier` abstraction is introduced. Tracked
separately in `ui/list/REVIEW_BACKLOG.md`.

---

### [warning] AdjustTaskProgressUseCase missing post-completion side effects
**Files:**
- `AdjustTaskProgressUseCase.java:62-68` — marks task completed via progress tracking but does not call `BookTaskCompletionExpenseUseCase` or record transition stats, unlike `CheckOffTaskUseCase`

**Why it matters:** The two completion paths are inconsistent. `CheckOffTaskUseCase` calls `BookTaskCompletionExpenseUseCase` (budget booking) and records transition stats (via `TaskSlotToggleMutation`). `AdjustTaskProgressUseCase` does neither. If a task has `budgetRequiredCents > 0` AND uses progress tracking, the expense will never be booked, and the scheduler will never learn from its completion order. This drift may silently grow if new side effects are added to one path and not the other.

**Severity:** Low in practice — no evidence that budget-required tasks use progress tracking in the current seed data or UI. But the structural inconsistency is a latent bug risk.

**Suggested alternative:** After marking the task completed, call `BookTaskCompletionExpenseUseCase.execute(task, date)` if `task.hasBudgetRequirement()`. For transition stats, either share a helper with `TaskSlotToggleMutation` or call `transitionDao.recordTransition` directly. Consider documenting the intentional omission if it is actually intentional. Requires adding `BookTaskCompletionExpenseUseCase` to the constructor and updating `AppCompositionRoot` wiring (outside this directory — deferred).
