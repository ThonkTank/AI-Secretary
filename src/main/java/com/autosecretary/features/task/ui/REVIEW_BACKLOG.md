# Review Backlog — task/ui

## Open Issues

### [inconsistent] Duplicate `TIME_FORMATTER` constants across 4 classes @skill:review-conventions

Four classes define identical `DateTimeFormatter.ofPattern("HH:mm")` as private static constants:

- `ListRowAdapter.TIME_FORMATTER` — `task/ui/list/ListRowAdapter.java:45`
- `TaskWidgetFactory.TIME_FORMATTER` — `task/ui/widget/TaskWidgetFactory.java:29`
- `TaskScheduleConfigDialog.TIME_FORMATTER` — `task/ui/TaskScheduleConfigDialog.java:52` (adds `Locale.GERMAN`, no behavioral difference for `HH:mm`)
- `PrefSlotUIBuilder.TIME_FORMATTER` — `task/ui/edit/internal/editor/PrefSlotUIBuilder.java:36`

**Canonical recommendation:** Extract a single shared `TIME_FORMATTER` constant. However, the project convention (CLAUDE.md) discourages adding new shared utility classes. Consider placing it on an existing shared class if one becomes available, or accept the duplication as the cost of avoiding a new file.

**Impact:** 4 files, cosmetic only — no behavioral difference.

---

### [consider] `notifyDataSetChanged()` used in `ListRowAdapter.setList()` @skill:review-performance

`ListRowAdapter.setList()` (line 556) calls `notifyDataSetChanged()` which forces a full rebind
of all visible items. With DiffUtil, only changed items would be rebound, providing smoother
animations and less work per update.

**Expected impact:** At typical list sizes (10–30 items), the impact is moderate — all visible items
rebind on every data change. Becomes more noticeable with 50+ items.

**Recommended fix:** Implement `DiffUtil.Callback` comparing `ViewSlot` items by `slotId` for identity
and content equality. Replace `notifyDataSetChanged()` with `DiffUtil.calculateDiff().dispatchUpdatesTo()`.

**Tradeoffs:** Requires implementing `equals()`/content comparison on ViewSlot/TaskListItem. Risk of
subtle bugs if equality check misses a field. Significant code change for moderate benefit at current
data volumes. Deferred.

---

