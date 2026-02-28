# Budget UI Internal – Review Backlog

## Open Issues

### [inconsistent] Date parse error handling inconsistent across dialogs
**File:** `BudgetTransactionDialogController.java` (parseDateInput) vs `BudgetTransferDialogController.java`
**Demoted from:** `../REVIEW_BACKLOG.md`

`BudgetTransactionDialogController.parseDateInput` silently falls back to `LocalDate.now()` when
the user types an invalid date — the dialog saves with today's date and the user never sees an error.
`BudgetTransferDialogController` uses the `setOnShowListener` pattern to intercept the positive
button, validates the date, shows a field-level error via `setError()`, and blocks submission.
Fix: apply the same `setOnShowListener` pattern and explicit error to `BudgetTransactionDialogController`.
(Code-logic change; out of scope for this doc review pass.)
