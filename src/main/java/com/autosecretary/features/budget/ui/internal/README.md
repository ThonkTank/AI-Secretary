# Budget UI — `internal/` package

Implementation helpers for `BudgetFragment` and `BudgetViewModel`. Nothing in this package is part of the public API; external code should only reference types in `budget/ui/` (root) and `budget/ui/state/`.

## Files at a glance

| File | Role |
|---|---|
| `BudgetSummaryPresentationMapper` | Maps aggregated domain items to `BudgetSummaryData` and `BudgetLimitBar` state objects. Also provides the shared `categoryLabel()` utility (icon + name string). |
| `BudgetBalanceChartView` | Custom `View` that draws the balance line chart from a list of `BudgetChartPoint` objects. All rendering is self-contained in `onDraw`. |
| `BudgetTransactionDialogController` | Manages add / edit / delete dialogs for individual transactions. |
| `BudgetTransferDialogController` | Manages the account-to-account transfer dialog. Uses `setOnShowListener` to block dismiss until the date field is valid. |
| `BudgetLimitDialogController` | Manages the "set / edit budget limit" dialog for expense categories. |
| `BudgetRecurringSuggestionsDialogController` | Manages the post-import dialog that lists detected recurring-payment patterns and lets the user select which to save as templates. |
| `CurrencyFormatter` | Static utility: formats cent amounts as Euro strings in various signed/unsigned styles (German locale). |

## Dialog controller pattern

Each `*DialogController` is instantiated in `BudgetFragment.onCreate()`. The fragment now owns the
statement-import launcher directly; dialog controllers hold a reference to the fragment to access
`requireContext()` and the fragment manager, but they do not store view references. Views are
looked up fresh each time a dialog is shown.
