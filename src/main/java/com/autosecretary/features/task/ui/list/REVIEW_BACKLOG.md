# Review Backlog — task/ui/list

## Open Issues

### [drift] ListRowAdapter.java:161,204 — `bindTaskRow` and `bindCalendarEventRow` are mirror-image visibility switches
Adding a new view element to the row requires updating both methods in sync. A single `setRowMode(holder, RowMode)` that sets all visibilities in one place would prevent drift.
**Tradeoff:** Consolidation adds indirection; the two methods are small and close together. Acceptable to defer.

---

### [consider] ListRowAdapter.java:449–452 — `notifyDataSetChanged()` on every `setList` call with no DiffUtil
Every task completion and timer tick causes a full rebind. `DiffUtil` with slot ID comparison would reduce unnecessary rebinds.
**Tradeoff:** DiffUtil adds code for a list that is typically <50 items. Benefit is mainly smoother animations.

---

### [consider] TaskListFragment.java:65–204 — `onViewCreated` is ~140 lines
ViewModel init, six observers, four button listeners, mode toggle, day nav display, adapter wiring, calendar permission are all inline. Extracting named setup methods (`bindDayNavigation`, `bindModeToggle`, `bindAdapter`, `observeViewModel`) would improve readability.
**Tradeoff:** Purely readability; no behavioral change. Fragment is the only one of its kind.

---

### [consider] TaskListFragment.java:86–91 — Timer-toggle lambda could be `vm.toggleTimer(ViewSlot)`
The 5-line timer-toggle lambda in the `TaskRowActions` constructor could be a single `vm.toggleTimer(ViewSlot)` method.
**Tradeoff:** Adds a method to `TaskViewModel` just to shorten a small lambda; acceptable either way.

---

### [consider] ViewSlotList.java:100 — `applySort()` mutates `ViewSlot.depth` in-place on shared objects
The flatten-with-depth traversal writes `slot.depth` directly on the original `ViewSlot` objects. If `applySort()` is called twice with different tree structures or if another thread reads the list during sort, stale depth values are visible. In practice the single-threaded executor prevents races, but the mutable-state pattern is fragile.
**Suggested alternative:** Store depth in a wrapper or `Map<ViewSlot, Integer>` that is rebuilt each sort, keeping `ViewSlot` immutable. Defer — low risk given single-threaded access.
