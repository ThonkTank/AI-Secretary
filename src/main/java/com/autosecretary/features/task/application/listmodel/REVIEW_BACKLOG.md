# Review Backlog — `task/application/listmodel/`

---

## Code Design Issues (deferred, not onboarding-related)

### [warning] TaskListItem 20-parameter constructor with same-typed adjacents
**File:** `TaskListItem.java:99-141`
20 parameters, 8 of which are `String`. Adjacent same-typed parameters make transposition bugs compile-silent. This is a design trade-off: grouping into value types would improve type safety but requires updating all field accesses throughout the codebase (ListRowAdapter, AdjustTaskProgressUseCase, etc.). Cost/benefit favors keeping the current design.
**Fix suggestion:** Document the parameter order clearly and rely on IDE refactoring tools to catch mistakes. Or: Group into value types with computed properties for backward compatibility (future effort).

*(Demoted from `application/REVIEW_BACKLOG.md`)*

---

### [warning] TaskListItem.calendarEvent conflates taskId and slotId
**File:** `TaskListItem.java:172-179`
Calendar event factory assigns the same synthetic `eventId` to both `taskId` and `slotId`. Code that reads these fields without checking `isCalendarEvent()` first will get non-DB identifiers. The guard check works today but the contract is implicit.
**Fix suggestion:** Use `null` for fields meaningless on calendar events, or a type-safe sealed hierarchy.

*(Demoted from `application/REVIEW_BACKLOG.md`)*

---
