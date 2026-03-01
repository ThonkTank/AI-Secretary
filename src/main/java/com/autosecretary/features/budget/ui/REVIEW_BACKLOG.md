# Budget UI – Review Backlog

## Open Issues

### [platform] Replace RadioGroup with Material SegmentedButton for time range filter @skill:review-design
**Files:** `res/layout/budget_overview_fragment.xml` lines 39–66, `ui/BudgetFragment.java` (listener wiring)

The chart time range selector (30d / 3m / 12m) uses three plain `RadioButton` elements in a `RadioGroup`. Material Design 3 provides Segmented Buttons (`com.google.android.material.button.MaterialButtonToggleGroup`) or Filter Chips for mutually exclusive option selection — both have better visual affordance and match platform conventions more closely.


### [friction] Replace manual date input with `MaterialDatePicker` in transaction dialogs @skill:review-design
**Files:** `res/layout/budget_add_transaction_dialog.xml`, `res/layout/budget_transfer_dialog.xml`, `ui/internal/BudgetTransactionDialogController.java`, `ui/internal/BudgetTransferDialogController.java`

Both dialogs use a plain `TextInputEditText` with `inputType="date"` and hint `"Datum (YYYY-MM-DD)"`. On Android, `inputType="date"` does not launch a native date picker — it only suggests a numeric keyboard. Users must type the exact ISO format; German-locale users who instinctively write "01.03.2026" will get a validation error with no guidance. Replace with `MaterialDatePicker` for native calendar-picker UX.


### [drift] Hardcoded German strings in `BudgetOverviewLoader` instead of string resources @skill:review-conventions
**File:** `internal/BudgetOverviewLoader.java:33-35`
**Problem:** `LABEL_TRANSFER = "Überweisung"`, `LABEL_TRANSFER_NOTE = "Überweisung · "`, and `LABEL_DEFAULT_BOOKING = "Buchung"` are hardcoded German strings. All other user-visible text in the budget UI goes through `R.string.*` resources. This is the only place in the budget UI layer where text is embedded in Java code.
**Canonical:** Move to `res/values/budget_strings.xml` and pass them into `BudgetOverviewLoader` (requires adding a Context parameter or a string provider).
**Impact:** 3 strings moved to resources + constructor change to accept a Context or string resolver. Structural change deferred.


### [rendering] `notifyDataSetChanged()` in BudgetTransactionAdapter @skill:review-performance
**File:** `ui/internal/BudgetTransactionAdapter.java:46`
**Problem:** `setItems()` calls `notifyDataSetChanged()` which invalidates all visible items, losing item animations and preventing RecyclerView from reusing stable-ID-matched ViewHolders. A `DiffUtil` implementation would compute the minimal diff.
**Expected impact:** Minor — the list is replaced wholesale on every month navigation. Visual: no item animations on update.
**Tradeoffs:** DiffUtil adds complexity; benefit is marginal for a fully-replaced list. Defer unless partial updates are introduced.
