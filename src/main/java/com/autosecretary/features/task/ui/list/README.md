# task/ui/list — Task List Screen

This package implements the main task list screen: the daily task checklist and
task management view. It is the primary surface a user interacts with every day.

## Entry points

| Class | Role |
|---|---|
| `TaskListFragment` | Fragment. Inflates the layout, wires views to `TaskViewModel`, forwards user interactions. |
| `TaskViewModel` | Owns the display state. Fetches data via `TaskDataService`, applies filters and sorting, exposes `LiveData` for the Fragment. |
| `ListRowAdapter` | RecyclerView adapter. Binds `ViewSlot` items to task or calendar row views. |
| `TaskViewModelFactory` | `ViewModelProvider.Factory` that wires dependencies into `TaskViewModel`. Used by `AppCompositionRoot`. |
| `TaskDescriptionDialog` | Popup dialog shown when the user taps a task title; displays title and description. |
| `ListConfig` | Enum for the two display modes (CHECKLIST / MANAGE). Defines the filter predicate and sort comparator for each mode. |

`state/` sub-package:

| Class | Role |
|---|---|
| `ViewSlot` | Wraps a `TaskListItem` with tree context (`depth`, `children`) for RecyclerView indentation and category-group rendering. |
| `ViewSlotList` | Rebuilds the display list from the master slot list in one pass: filter, optionally merge calendar events, then sort and flatten for display. |

## Two display modes

**Checklist mode** (default tab): shows only the slots scheduled for the selected day,
sorted by start time. The search bar is hidden. Each slot is an individual scheduled
execution window.

**Manage mode**: shows all tasks for the selected day grouped under synthetic category
headers and sorted by title. The search bar is visible and filters by title. Each category
header can be expanded or collapsed in place (state keyed by category id).

The user switches between modes via a `MaterialButtonToggleGroup`. Switching calls
`vm.applyChecklistPreset()` or `vm.applyManagePreset()`, which updates `activeListConfig`
in `TaskViewModel` and triggers `filterList()`.

## Two-phase checkoff

Completing a task slot takes two taps on the checkbox:

1. **First tap** — records `slot.realStart`; the slot becomes STARTED (row background turns green).
2. **Second tap** — records `slot.realEnd` and sets `slot.completed = true`; the slot is DONE.

This is enforced in `CheckOffTaskUseCase` (application layer). The adapter's
`bindCheckboxControls()` just forwards taps to `TaskViewModel.checkOff()`.

## Data flow

```
TaskDataService.loadAllMapped()
    ↓
ViewSlotList.fromList()            ← replaces the master list; never filtered in place
    ↓
ViewSlotList.rebuildDisplay()      ← apply ListConfig.matches() + search query,
                                      merge calendar rows, build tree, sort, flatten
    ↓
TaskViewModel.displayList (LiveData<List<ViewSlot>>)
    ↓
ListRowAdapter.setList()           ← notifyDataSetChanged(), RecyclerView redraws
```

The master list (`allSlots` inside `ViewSlotList`) is never modified after `fromList()`.
Every `filterList()` call rebuilds `displaySlots` from scratch.

## Calendar events

Calendar events appear as read-only rows in Checklist mode. They are injected after the
task filter and are not stored in the master list. They require `READ_CALENDAR` permission;
`TaskListFragment` requests the permission at startup via `calendarPermissionLauncher`.
When the result arrives, `TaskViewModel.onCalendarPermissionChanged()` triggers a
`filterList()` call so the calendar rows appear or disappear immediately.

## Day navigation

The user can navigate up to `TaskViewModel.MAX_DAY_OFFSET` days forward from today.
Navigation to past days is blocked (today is the minimum). When the selected day is not
today, `adapter.setInteractionsEnabled(false)` is called, which disables all checkboxes,
timers, and edit buttons on every row — the list becomes read-only for historical viewing.

## Reading order for a new contributor

1. `ListConfig.java` — understand the two modes and their filter/sort rules (small, start here).
2. `ViewSlot.java` + `ViewSlotList.java` — understand how the master list is built, filtered, and sorted.
3. `TaskViewModel.java` — understand how data flows from services to the display list.
4. `TaskListFragment.java` — see how the views are wired to the ViewModel.
5. `ListRowAdapter.java` — see how individual rows are bound (two row types: task and calendar).
