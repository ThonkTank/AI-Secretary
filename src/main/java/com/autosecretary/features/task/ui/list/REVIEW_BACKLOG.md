# Review Backlog — task/ui/list

## Open Issues

[warning] ListRowAdapter.java:161,204 — `bindTaskRow` and `bindCalendarEventRow` are mirror-image visibility switches; adding a new view element to the row requires updating both methods in sync, which will be missed. Consider a single `setRowMode(holder, RowMode)` method that sets all visibilities in one place.

[warning] ListRowAdapter.java:458–461 — `notifyDataSetChanged()` on every `setList` call with no DiffUtil. Every task completion and timer tick causes a full rebind. Use `DiffUtil` with slot ID comparison for `setList`.

[warning] TaskListFragment.java:65-205 — `onViewCreated` is ~140 lines: ViewModel init, six observers, four button listeners, mode toggle, day nav display, adapter wiring, calendar permission. Extract named setup methods (`bindDayNavigation`, `bindModeToggle`, `bindAdapter`, `observeViewModel`).
