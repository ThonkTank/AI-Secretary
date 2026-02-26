# Review Backlog — features/task/ui

## Open Issues

- [warning] TaskWidgetFactory.java:53-60 + TaskViewModel.java:215-232 — Checklist filter predicate (`selectedDate.equals(item.day) && item.start != null`, sort by start) is independently duplicated across two locations. Two implementations of the same "today's scheduled items" logic will drift when filtering rules change. Extract a static utility or move the predicate to a shared location (e.g. `TaskListItem`).
