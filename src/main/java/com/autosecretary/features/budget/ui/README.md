# Budget UI Layer

Public entry points in this package:
- `BudgetFragment` — the main budget screen (Fragment)
- `BudgetViewModel` / `BudgetViewModelFactory` — observable state and DI wiring

Sub-packages:
- `internal/` — private implementation helpers (dialog controllers, chart view, data loader, formatters)
- `state/` — immutable view-state data classes posted via LiveData
- `widget/` — Android home-screen widget (`BudgetWidgetProvider`)

The `internal/` package is not part of the public API; callers outside `budget/ui/` should only reference types in the root package and `state/`.
