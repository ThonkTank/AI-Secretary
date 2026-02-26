

[nit] TaskListItem.java:147-173 — `calendarEvent()` calls the 23-parameter constructor with 16 placeholder nulls/zeros/falses for task-only fields. This propagates nulls (goalIcon, goalColorHex) that task items never expose, forcing the adapter to null-guard (ListRowAdapter:175). A separate CalendarEventListItem type, or at minimum a compact internal constructor, would eliminate the sprawl.

[warning] TaskListItemMapper.java:37-62 + TaskListItem.task() — 22-argument factory call. All positional, many same-typed. Any transposition produces a silent bug. Use a builder pattern on `TaskListItem` or split into a smaller data class.
