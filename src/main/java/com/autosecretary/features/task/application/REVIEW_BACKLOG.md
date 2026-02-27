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

### [warning] TaskListItem 20-parameter constructor with same-typed adjacents
**File:** `listmodel/TaskListItem.java:44-64`
20 parameters, 8 of which are `String`. Adjacent same-typed parameters make transposition bugs compile-silent.
**Fix suggestion:** Group into value types: `ProgressState(current, target, unit, stepDelta)`, `GoalDecoration(icon, colorHex)`.

---

### [warning] TaskListItem.calendarEvent conflates taskId and slotId
**File:** `listmodel/TaskListItem.java:92-100`
Calendar event factory assigns the same synthetic `eventId` to both `taskId` and `slotId`. Code that reads these fields without checking `isCalendarEvent()` first will get non-DB identifiers. The guard check works today but the contract is implicit.
**Fix suggestion:** Use `null` for fields meaningless on calendar events, or a type-safe sealed hierarchy.

---

---

### [nit] ListRowAdapter.bindDeadline — three-branch if/else with repeated setters
**File:** `../ui/list/ListRowAdapter.java:236-263`
Each branch sets the same three properties (text, colour, contentDescription) on the same view. The duplication makes adding a new urgency tier error-prone.
**Fix suggestion:** Push display resolution into `DeadlineUrgency` (resource IDs) or extract a lookup record.
