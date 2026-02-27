# Task edit UI package

## Entry points
- `TaskEditDialog`: dialog fragment that renders and drives the task edit form.
- `TaskEditSessionController`: owns create/edit session state and save flow.
- `TaskEditPresenter`: applies form input and maps edit state back to persistence model.

## State location
- Canonical edit session state lives in `state/` (`ui/edit/state`), including `TaskEditState` and `PrefSlotEditState`.

## Internal boundaries
- `internal/` is reserved for task-edit-only helpers.
- `internal/TaskEditStateMapper` maps edit-state ↔ task (bidirectional, data layer bridge).
- `internal/editor/` contains form view wiring, validation, input collection, and preferred-slot UI.

List-screen classes live in `features.task.ui.list` (for example: `TaskListFragment`, `ListRowAdapter`, `TaskViewModel`).
