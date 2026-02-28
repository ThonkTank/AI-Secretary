# Review Backlog — task/ui/list

## Open Issues

### [nit] TaskViewModel.java:277 — third call site of direct `TaskWidgetProvider` dependency

`TaskViewModel.refreshList()` calls `TaskWidgetProvider.notifyWidgetUpdate(getApplication())` directly.
Two other call sites in the *application* layer are tracked in `application/REVIEW_BACKLOG.md` under the
same pattern. When the widget notification abstraction is eventually introduced to invert the
application→UI dependency, this call must also be migrated — or it will continue to hold a concrete
reference to the widget class.

**Why it matters here:** The ViewModel ideally should not know about a specific concrete widget
implementation. Any new widget type added later requires touching the ViewModel in addition to the
use-case and alarm receiver.

**Fix:** Same abstraction proposed in `application/REVIEW_BACKLOG.md` (`WidgetRefreshNotifier`);
this call site is a client of the same abstraction.

---

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

### [consider] ViewSlotList.java:100 — `applySort()` mutates `ViewSlot.depth` in-place on shared objects
The flatten-with-depth traversal writes `slot.depth` directly on the original `ViewSlot` objects. If `applySort()` is called twice with different tree structures or if another thread reads the list during sort, stale depth values are visible. In practice the single-threaded executor prevents races, but the mutable-state pattern is fragile.
**Suggested alternative:** Store depth in a wrapper or `Map<ViewSlot, Integer>` that is rebuilt each sort, keeping `ViewSlot` immutable. Defer — low risk given single-threaded access.

---
