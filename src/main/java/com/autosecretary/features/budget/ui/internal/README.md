# Budget UI — `internal/` package

Implementation helpers for `BudgetFragment` and `BudgetViewModel`. Nothing in this package is part of the public API; external code should only reference types in `budget/ui/` (root) and `budget/ui/state/`.

## Files at a glance

| File | Role |
|---|---|
| `BudgetOverviewLoader` | Queries the repository, maps transactions to display rows, computes summary and chart data — all in one background pass. Owned by `BudgetViewModel`. |
| `BudgetSummaryPresentationMapper` | Maps aggregated domain items to `BudgetSummaryData` and `BudgetLimitBar` state objects. Also provides the shared `categoryLabel()` utility (icon + name string). |
| `BudgetBalanceChartView` | Custom `View` that draws the balance line chart from a list of `BudgetChartPoint` objects. All rendering is self-contained in `onDraw`. |
| `BudgetTransactionDialogController` | Manages add / edit / delete dialogs for individual transactions. |
| `BudgetTransferDialogController` | Manages the account-to-account transfer dialog. Uses `setOnShowListener` to block dismiss until the date field is valid. |
| `BudgetLimitDialogController` | Manages the "set / edit budget limit" dialog for expense categories. |
| `BudgetRecurringSuggestionsDialogController` | Manages the post-import dialog that lists detected recurring-payment patterns and lets the user select which to save as templates. |
| `BudgetImportPickerController` | Wraps the system file picker (`OpenDocument`) for CSV/PDF import. Must be registered in `Fragment.onCreate()`. |
| `CurrencyFormatter` | Static utility: formats cent amounts as Euro strings in various signed/unsigned styles (German locale). |
| `SpinnerHelper` | Static utility: binds typed lists to `Spinner` widgets and reads back selections without boilerplate `ArrayAdapter` code. |

## Dialog controller pattern

Each `*DialogController` is instantiated in `BudgetFragment.onCreate()` (before `onStart`) so that `ActivityResultLauncher` registrations — required by `BudgetImportPickerController` — happen at the correct lifecycle stage. The controllers hold a reference to the fragment to access `requireContext()` and the fragment manager, but they do not store view references; views are looked up fresh each time a dialog is shown.
