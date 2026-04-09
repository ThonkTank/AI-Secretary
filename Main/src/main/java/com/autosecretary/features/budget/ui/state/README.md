# Budget UI — `state/` package

Immutable view-state objects and enums posted via `LiveData` from `BudgetViewModel` to `BudgetFragment`. These are the only types that should cross the ViewModel → Fragment boundary; the Fragment must not depend on domain or data types directly.

## Files at a glance

| File | Role |
|---|---|
| `BudgetUiState` | Enum: `LOADING` / `EMPTY` / `CONTENT` / `ERROR` — controls which views are visible on the budget screen. |
| `TimeRangeFilter` | Enum: `DAYS_30` / `MONTHS_3` / `MONTHS_12` — the selected lookback window for the balance chart. |
| `BudgetSummaryData` | Monthly income, expense, net, and account running balance (all in cents). |
| `BudgetTransactionRow` | Display model for one row in the transaction list. Built via builder pattern from `MonthlyOverviewItem`. |
| `BudgetLimitBar` | Display model for one category spending-limit progress bar; includes spent, base limit, and rollover-adjusted effective limit. |
| `BudgetChartPoint` | Single data point on the balance chart: a formatted date label and a balance in cents. |
| `UiText` | Deferred string wrapper — holds either a `@StringRes` ID or a raw string, resolved to a `String` only when a `Context` is available. Keeps `BudgetViewModel` free of Android Context dependencies. |

## Why `UiText` instead of `String`?

`ViewModel` instances must not hold a `Context` reference (they outlive the `Activity`/`Fragment`). `UiText` lets the ViewModel post status messages as resource IDs — resolved to actual strings by the Fragment when it has a valid `Context`. See `UiText.java` for the full pattern explanation.
