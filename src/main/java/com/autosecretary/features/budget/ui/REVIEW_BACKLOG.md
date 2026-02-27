# Budget UI – Review Backlog

## Open Issues

[warning] BudgetFragment:294–378 — `showTransactionDialog` is ~85 lines handling inflation, spinner binding, pre-population, and dialog construction in one method; extract a controller class in the style of BudgetTransferDialogController.

[warning] BudgetFragment:469–498 — `renderLimitBars` + `renderTransactions` use inflate-and-addView loops with `removeAllViews()` — full reinflation on every reload. For 50+ transactions this is significant main-thread work. Consider RecyclerView or at least diffing to avoid reinflation when content is unchanged.

[consider] BudgetFragment:229–239 — `RadioGroup` listener uses `if/else if` chain while the inverse observer (196–203) uses a `switch` expression; the two paths are asymmetric. A switch on `checkedId` (int) with `R.id.*` case labels is blocked by the project's non-constant R fields — the if/else is the only viable form here. The asymmetry is a genuine readability note but not fixable without moving the mapping into `TimeRangeFilter` itself (e.g. a `fromRadioId(int)` factory method).

[inconsistent] BudgetFragment:parseDateInput vs BudgetTransferDialogController:80–83 — date parse errors are handled differently across the two dialogs. `parseDateInput` silently falls back to `LocalDate.now()` on invalid input; `BudgetTransferDialogController` shows an inline field error and blocks submission. Fixing `showTransactionDialog` requires the `setOnShowListener` pattern (manually intercepting the positive button) to prevent dialog auto-dismiss on validation failure — this is non-trivial and blocked on refactoring the transaction dialog into a controller class (see warning above).

[inconsistent] BudgetViewModel vs TaskViewModel — different mechanisms for posting results from background to the main thread
**Files:** `BudgetViewModel.java:48,69,138`, `TaskViewModel.java:153`
**Observed patterns:**
- `BudgetViewModel`: injects `Consumer<Runnable> postToMain` and calls `postToMain.accept(() -> liveData.setValue(x))` — explicitly marshals to main before calling `setValue()`
- `TaskViewModel`: calls `liveData.postValue(x)` directly from any thread — standard thread-safe LiveData API, no injection required
**Why it matters:** Both achieve the same result. The `postToMain` injection in BudgetViewModel adds a constructor dependency and extra indirection with no benefit over `postValue()`. New ViewModels following the BudgetViewModel pattern will needlessly repeat the injection.
**Canonical recommendation:** `liveData.postValue(x)` (TaskViewModel pattern) — simpler, no injection required, idiomatic Android.
**Fix:** Replace `postToMain.accept(() -> liveData.setValue(x))` calls with `liveData.postValue(x)` throughout `BudgetViewModel`; remove the `postToMain` constructor parameter; update `BudgetViewModelFactory` and `AppCompositionRoot`. **Deferred — medium-scope change.**

*(New finding — conventions review)*
