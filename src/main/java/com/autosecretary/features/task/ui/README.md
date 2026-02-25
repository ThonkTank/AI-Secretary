# Task UI package conventions

- Put editable UI session objects in `ui/state/` (for example: `TaskEditState`, `PrefSlotEditState`).
- Keep UI behavior classes (dialogs, presenters, adapters, binders) in `ui/` and `ui/internal/`.
- Do not introduce `ui/model` for editable state; use `ui/state` consistently.
