# Review Backlog — `task/application/`

---


### [drift] Application layer imports UI-layer widget classes
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

## Acknowledged Good Patterns

### [keep] `CalendarQueryHelper` extraction — shared calendar query boilerplate
**Path:** `internal/calendar/CalendarQueryHelper.java`

`CalendarReader` and `DeviceCalendarBlockedIntervalProvider` previously duplicated permission checking, day-boundary millis computation, URI building, and cursor lifecycle management. All four shared steps are now extracted into `CalendarQueryHelper.queryDay()` with a `CursorRowProcessor<T>` callback. Each consumer provides only its own row-mapping logic. The helper uses `context.getApplicationContext()` defensively and manages the cursor with try-with-resources. No open issues.

