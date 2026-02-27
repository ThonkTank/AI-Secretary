# Budget UI – Review Backlog

## Open Issues

### [warning] `renderLimitBars` + `renderTransactions` — full reinflation on every reload
**File:** `BudgetFragment.java` (renderLimitBars / renderTransactions methods)

Full inflate-and-addView loops with `removeAllViews()` on every LiveData emission. For 50+ transactions this is significant main-thread work. Consider RecyclerView or diffing to avoid reinflation when content is unchanged.

### [consider] RadioGroup listener vs. observer asymmetry
**File:** `BudgetFragment.java`

`RadioGroup` listener uses `if/else if` chain while the inverse observer uses a `switch` expression. The `if/else` is the only viable form due to non-constant R fields. Asymmetry is a genuine readability note but not fixable without moving the mapping into `TimeRangeFilter` itself (e.g. a `fromRadioId(int)` factory method).

### [inconsistent] Date parse error handling inconsistent across dialogs
`BudgetTransactionDialogController.parseDateInput` silently falls back to `LocalDate.now()` on invalid input; `BudgetTransferDialogController` shows an inline field error and blocks submission. Fix: use the `setOnShowListener` pattern in `BudgetTransactionDialogController` to intercept positive-button clicks and validate before dismissing.

### [inconsistent] BudgetViewModel vs TaskViewModel thread-posting — **promoted to `features/REVIEW_BACKLOG.md`** (cross-feature scope)
