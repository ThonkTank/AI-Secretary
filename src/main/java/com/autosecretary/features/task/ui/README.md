# Task UI package conventions

## Folder responsibilities
- `list/`: main task list surface (fragment, adapter, and list-specific view model/state).
- `edit/`: task edit flow (dialog, presenter, form/state handling).
- `widget/`: home-screen widget integration.
- `ui/` root: cross-surface UI elements shared across multiple task surfaces.
- Put editable UI session objects in `ui/edit/state/` (for example: `TaskEditState`, `PrefSlotEditState`).
- Keep UI behavior classes (dialogs, presenters, adapters, binders) in `ui/` and `ui/internal/`.
- Do not introduce `ui/model` for editable state; use `ui/edit/state` consistently.
