# Review Backlog — `task/application/listmodel/`

---

## Code Design Issues (deferred, not onboarding-related)

### [warning] TaskListItem 20-parameter constructor with same-typed adjacents
**File:** `TaskListItem.java:44-64`
20 parameters, 8 of which are `String`. Adjacent same-typed parameters make transposition bugs compile-silent.
**Fix suggestion:** Group into value types: `ProgressState(current, target, unit, stepDelta)`, `GoalDecoration(icon, colorHex)`.

*(Demoted from `application/REVIEW_BACKLOG.md`)*

---

### [warning] TaskListItem.calendarEvent conflates taskId and slotId
**File:** `TaskListItem.java:92-100`
Calendar event factory assigns the same synthetic `eventId` to both `taskId` and `slotId`. Code that reads these fields without checking `isCalendarEvent()` first will get non-DB identifiers. The guard check works today but the contract is implicit.
**Fix suggestion:** Use `null` for fields meaningless on calendar events, or a type-safe sealed hierarchy.

*(Demoted from `application/REVIEW_BACKLOG.md`)*

---
