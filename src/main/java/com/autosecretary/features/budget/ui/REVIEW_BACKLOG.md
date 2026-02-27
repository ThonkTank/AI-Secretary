# Budget UI – Review Backlog

## Open Issues

[warning] BudgetFragment:384–406 — `categoriesForType()` and `activeAccounts()` filtering lives in the Fragment and is called 5+ times across two dialog methods; move to ViewModel or a dedicated filter helper.

[warning] BudgetFragment:294–378 — `showTransactionDialog` is ~85 lines handling inflation, spinner binding, pre-population, and dialog construction in one method; extract a controller class in the style of BudgetTransferDialogController.

[warning] BudgetFragment:469–498 — `renderLimitBars` + `renderTransactions` use inflate-and-addView loops with `removeAllViews()` — full reinflation on every reload. For 50+ transactions this is significant main-thread work. Consider RecyclerView or at least diffing to avoid reinflation when content is unchanged.


[warning] BudgetViewModel.java:84-107 — Constructor accepts 11 parameters. `budgetSeedService` is only called once at init (line 145) and is not a primary ViewModel concern. Move seed step to factory or startup use case to reduce parameter count.

[consider] BudgetFragment:229–239 — `RadioGroup` listener uses `if/else if` chain while the inverse observer (196–203) uses a `switch` expression; the two paths are asymmetric. A switch on `checkedId` (int) with `R.id.*` case labels is blocked by the project's non-constant R fields — the if/else is the only viable form here. The asymmetry is a genuine readability note but not fixable without moving the mapping into `TimeRangeFilter` itself (e.g. a `fromRadioId(int)` factory method).

[nit] BudgetFragment.java:519-521 + BudgetRecurringSuggestionsDialogController.java:181-183 — `getColorFromResources` is duplicated across two classes (identical `ContextCompat.getColor` wrapper). Will be copied again as new dialog controllers are added. Extract to a shared static utility.

