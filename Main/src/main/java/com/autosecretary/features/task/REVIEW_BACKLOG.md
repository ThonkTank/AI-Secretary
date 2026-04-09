# Review Backlog — features/task/

Issues affecting the task feature that don't fit cleanly into a single sub-package backlog.

## Open Issues

*(None.)*

## Resolved Issues

### [stale] README.md "Where to start reading" contained three wrong file paths
**File:** `AutoSecretary/README.md` (project root, above scope — edited directly)
**Lines:** 81, 82, 84

Three paths in the "Where to start reading" section pointed to non-existent locations:
- `features/task/ui/ListFragment.java` → should be `features/task/ui/list/TaskListFragment.java`
- `features/task/ui/TaskViewModel.java` → should be `features/task/ui/list/TaskViewModel.java`
- `features/task/domain/TaskSlotGenerator.java` → should be `features/task/domain/scheduling/TaskSlotGenerator.java`

A novice following the reading guide would get "file not found" for all three.

**Fixed:** Corrected all three paths in `README.md`.
