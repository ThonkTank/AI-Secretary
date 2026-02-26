# Budget UI – Review Backlog

## Open Issues

[warning] BudgetFragment:384–406 — `categoriesForType()` and `activeAccounts()` filtering lives in the Fragment and is called 5+ times across two dialog methods; move to ViewModel or a dedicated filter helper.

[warning] BudgetFragment:294–378 — `showTransactionDialog` is ~85 lines handling inflation, spinner binding, pre-population, and dialog construction in one method; extract a controller class in the style of BudgetTransferDialogController.

[warning] BudgetFragment:469–498 — `renderLimitBars` + `renderTransactions` use inflate-and-addView loops with `removeAllViews()` — full reinflation on every reload. For 50+ transactions this is significant main-thread work. Consider RecyclerView or at least diffing to avoid reinflation when content is unchanged.

[nit] BudgetFragment:228–237 — `RadioGroup` listener uses `if/else if` chain while the inverse observer (196–203) uses `switch`; adding a new `TimeRangeFilter` requires updating both asymmetric paths. Unify by extracting a bidirectional mapping into `TimeRangeFilter` itself.

[nit] BudgetFragment:463 — `showEditLimitDialog` receives `bar.getBaseLimitCents() / 100.0` (euros as double) while the rest of the system works in cents; pass cents directly to avoid the conversion.

[nit] BudgetViewModel.java:414–431 — `saveBudgetLimitFromString` still receives an amount string and parses it inside the ViewModel; the Fragment already has access to `amountParser`. Consider passing cents directly from the dialog to avoid the ViewModel re-parsing.

[warning] BudgetViewModel.java:84-107 — Constructor accepts 11 parameters. `budgetSeedService` is only called once at init (line 145) and is not a primary ViewModel concern. Move seed step to factory or startup use case to reduce parameter count.

[warning] BudgetViewModel.java:258-318 — `addTransfer` and `updateTransfer` are structurally identical: parse amount → bail on error → call use case → check result → post error or reload. Error-posting block appears 3× in the file. Extract a shared `executeTransferOperation` helper.

[nit] BudgetFragment.java:519-521 + BudgetRecurringSuggestionsDialogController.java:181-183 — `getColorFromResources` is duplicated across two classes (identical `ContextCompat.getColor` wrapper). Will be copied again as new dialog controllers are added. Extract to a shared static utility.
