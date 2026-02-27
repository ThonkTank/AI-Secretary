# Budget UI – Review Backlog

## Open Issues

[warning] BudgetFragment:294–378 — `showTransactionDialog` is ~85 lines handling inflation, spinner binding, pre-population, and dialog construction in one method; extract a controller class in the style of BudgetTransferDialogController.

[warning] BudgetFragment:469–498 — `renderLimitBars` + `renderTransactions` use inflate-and-addView loops with `removeAllViews()` — full reinflation on every reload. For 50+ transactions this is significant main-thread work. Consider RecyclerView or at least diffing to avoid reinflation when content is unchanged.

[consider] BudgetFragment:229–239 — `RadioGroup` listener uses `if/else if` chain while the inverse observer (196–203) uses a `switch` expression; the two paths are asymmetric. A switch on `checkedId` (int) with `R.id.*` case labels is blocked by the project's non-constant R fields — the if/else is the only viable form here. The asymmetry is a genuine readability note but not fixable without moving the mapping into `TimeRangeFilter` itself (e.g. a `fromRadioId(int)` factory method).
