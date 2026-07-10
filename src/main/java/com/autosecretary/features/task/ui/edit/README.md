# Task edit UI package

## What this package does

The task edit UI is a `DialogFragment` that lets users create or edit a task. The overall flow is:

```
1. User taps "new task" or a task row → `TaskEditViewModel.createNewTask()` / `beginEditTask()`
2. `TaskEditStateMapper.fromTask(task)` → loads task fields into a flat `TaskEditState`
3. `TaskEditDialog` inflates the form, `TaskEditSectionBinder` binds views, controllers take over sections
4. User edits fields; `PrefSlotSectionController` handles dynamic preferred-slot rows
5. User taps Save → `TaskEditFormValidator` validates; `TaskEditFormInputReader` reads view state → `FormInput`
6. `TaskEditPresenter.applyForm(formInput, editState)` → merges edits into `TaskEditState`
7. `TaskEditStateMapper.toTask(editState)` → maps edit state back to a `Task` data object
8. `TaskEditViewModel.saveEditedTask()` persists via `TaskDataService` and bumps the list refresh version
```

The mapping round-trip (Task → TaskEditState → Task) exists because the form exposes only
a subset of task fields; preserved fields (like scheduler-managed state) must survive the round-trip.

## Entry points

- `TaskEditDialog` — the `DialogFragment`; assembles all helpers and drives the lifecycle.
- `TaskEditViewModel` — owns create/edit session state, reference-data loading, and persistence.
  Shared by `TaskListFragment` and `TaskEditDialog` so the edit surface has one primary ViewModel owner.
- `TaskEditPresenter` — applies form input to a `TaskEditState`; also owns the deadline field
  (mutated separately outside `FormInput`).

## State

Canonical edit session state lives in `state/` (`ui/edit/state`):
- `TaskEditState` — flat mutable form state holding all editable task fields plus preserved scheduler state.
- `PrefSlotEditState` — state for one preferred-slot row (day/time pattern).
- `TaskEditDefaults` — canonical default values for all new-task fields; referenced by both `TaskEditState` field initializers and `FormInput` fallbacks.

## Internal boundaries

- `internal/` holds task-edit-only helpers not intended for other packages.
- `internal/TaskEditStateMapper` — bidirectional mapper (Task ↔ TaskEditState). Read alongside `TaskEditDialog`.
- `internal/editor/` — form view wiring, validation, input reading, preferred-slot UI. See `internal/editor/README.md`.

## Recommended reading order

1. **`TaskEditState.java`** (`state/`) — understand the form state model and which fields are user-editable vs scheduler-managed.
2. **`TaskEditDialog.java`** — see how everything is assembled; the most important file in this package.
3. **`internal/TaskEditStateMapper.java`** — understand the Task → TaskEditState → Task round-trip.
4. **`internal/editor/README.md`** — then read the editor sub-package overview before diving into individual helpers.
5. **`TaskEditViewModel.java`** — understand the create/edit lifecycle and persistence flow.
6. **`TaskEditPresenter.java`** — understand how form input is merged into edit state.

## Public resources

- [Android DialogFragment](https://developer.android.com/reference/androidx/fragment/app/DialogFragment) — base class for `TaskEditDialog`
- [Android TextInputLayout and TextInputEditText](https://m3.material.io/components/text-fields/overview) — Material Design text fields used in the form
- [TimePicker](https://developer.android.com/reference/android/widget/TimePicker) — used in preferred-slot time pickers

List-screen classes live in `features.task.ui.list` (e.g. `TaskListFragment`, `ListRowAdapter`, `TaskViewModel`).
