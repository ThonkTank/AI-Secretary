# editor — form wiring, validation, and preferred-slot UI

This package contains the helper classes used by `TaskEditDialog` (in the parent
`ui/edit/` package) to set up, read, and validate the task-edit form.

## How the classes fit together

```
TaskEditDialog
 │
 ├── TaskEditSectionBinder          — inflates + populates each form section,
 │    │                               returns typed view-group handles
 │    ├─ BasicInfoViews
 │    ├─ SchedulingViews
 │    ├─ RepetitionViews
 │    └─ ProgressViews
 │
 ├── GoalSectionController          — manages goal-icon field + colour-palette grid
 │
 ├── PrefSlotSectionController      — orchestrates preferred-slot rows (day/time pickers);
 │    └── PrefSlotUIBuilder           dynamically builds + rebuilds slot UI on demand
 │
 └── TaskEditFormValidator          — validates the live views, sets inline errors
      (called on save-button click)

TaskEditFormInputReader             — reads all view state into a FormInput POJO
      (called alongside the validator when the user hits Save)
```

## Reading order for newcomers

1. Start with `TaskEditDialog` (`../TaskEditDialog.java`, one level up in `ui/edit/`) to
   see how everything is assembled.
2. Read `TaskEditSectionBinder` next — it is the foundation; the typed inner-view
   groups it returns are passed around everywhere.
3. `GoalSectionController` and `PrefSlotSectionController` own their own sections
   completely and can be read independently.
4. `PrefSlotUIBuilder` is the most complex class; the `groupByRepetition` method has
   an inline algorithm comment worth reading carefully.
5. `TaskEditFormInputReader` and `TaskEditFormValidator` are the last step — they
   consume the view references to read or validate just before Save is executed.

## Key concepts

- **PrefSlot**: a preferred day/time pattern for when the task should be scheduled.
  See `CLAUDE.md` glossary and `PrefSlotEditState` in `ui/edit/state/`.
- **repsPerDay**: how many times per day a task recurs (derived from repetition
  settings). Drives how many slot groups the pref-slot UI renders.
- **View group handle** (`*Views` inner classes in `TaskEditSectionBinder`): plain
  data classes that hold typed references to already-inflated views. Returned after
  binding so callers can read field values later without repeating `findViewById`.

## Related files outside this package

- `../TaskEditStateMapper.java` (`internal/` sibling) — bidirectional mapper between
  the `Task` data object and `TaskEditState`. Read this alongside `TaskEditDialog` to
  understand how form state is loaded from and written back to the database model.
