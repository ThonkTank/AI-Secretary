# Task UI package conventions

## Folder responsibilities
- `list/`: main task list surface (fragment, adapter, and list-specific view model/state).
- `edit/`: task edit flow (dialog, presenter, form/state handling).
- `widget/`: home-screen widget integration.
- `ui/` root: cross-surface UI elements shared across multiple task surfaces.
- Put editable UI session objects in `ui/edit/state/` (for example: `TaskEditState`, `PrefSlotEditState`).
- Keep UI behavior classes (dialogs, presenters, adapters, binders) in `ui/` and `ui/internal/`.
- Do not introduce `ui/model` for editable state; use `ui/edit/state` consistently.

## Sub-package docs (start here for a new surface)

Each sub-package has its own README with entry-point tables, data-flow diagrams, and reading orders:

- [`list/README.md`](list/README.md) — task list screen: checklist/manage modes, two-phase checkoff,
  data flow, calendar integration, day navigation, and reading order.
- [`edit/README.md`](edit/README.md) — task edit dialog: entry points, state location, internal
  boundaries, and how the editor classes fit together.
- [`edit/internal/editor/README.md`](edit/internal/editor/README.md) — form wiring internals:
  section binder, goal/pref-slot controllers, validator, and input reader.
- [`widget/README.md`](widget/README.md) — home-screen widget: RemoteViews pattern, three-class
  architecture, data flow, design constraints, and public Android references.
