# Review Backlog — budget/ui/internal

## Open Issues

### [warning] `BudgetTransactionDialogController.show()` is ~95 lines
**File:** `BudgetTransactionDialogController.java:71–166`
**Smell:** The method handles view inflation, spinner population, edit-mode pre-fill, title/button resolution, and dialog creation all in one block. Hard to scan and prone to accumulating more cases.
**Fix:** Extract the edit-mode pre-fill block (lines 101–112) into a private `populateExistingRow()` method. NOTE: any extraction takes ≥5 parameters due to the widget locals — defer until a cleaner shape (e.g. a small holder record) presents itself.
**Status:** DEFER — extraction would require a ≥9-parameter method which is worse than the current shape.

### [nit] Hardcoded German UI strings in `BudgetOverviewLoader` not in string resources
**File:** `BudgetOverviewLoader.java:33–35`
**Smell:** `"Überweisung"`, `"Überweisung · "`, `"Buchung"` are displayed to users in transaction row labels but are hardcoded in a background-thread data-loading class instead of string resources. Inconsistent with the rest of the UI which uses R.string.
**Fix:** Requires passing a `Context` (or resolved strings) into `BudgetOverviewLoader` from the UI layer.
**Status:** DEFER — structural change needed to pass strings from UI layer to loader.
