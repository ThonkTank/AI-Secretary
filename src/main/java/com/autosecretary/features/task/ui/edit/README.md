# Task edit UI package

## Entry points
- `TaskEditDialog`: dialog fragment that renders and drives the task edit form.
- `TaskEditSessionController`: owns create/edit session state and save flow.
- `TaskEditPresenter`: applies form input and maps edit state back to persistence model.

## Internal boundaries
- `internal/` is reserved for task-edit-only helpers.
- `internal/editor/` contains form view wiring, validation, and input collection.
- `internal/mapper/` contains `TaskEditStateMapper` for edit-state ↔ task mapping.
- `internal/PrefSlotUIBuilder` is edit-specific preferred-slot UI construction.

List-screen classes (`TaskListFragment`, `ListRowAdapter`, `TaskViewModel`, etc.) remain in `features.task.ui` root.
