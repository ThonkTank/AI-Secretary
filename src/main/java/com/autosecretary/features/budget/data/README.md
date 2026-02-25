# Budget data model (canonical as of AppDatabase v8)

## Canonical Room entities

The canonical persisted budget model is the `budget_*` schema introduced in `AppDatabase` migration `7 -> 8`:

- `BudgetAccount` (`budget_account`)
- `BudgetCategory` (`budget_category`)
- `BudgetTransactionEntity` (`budget_transaction`)
- `BudgetLimit` (`budget_limit`)

These are the entities registered in `AppDatabase` version 8 and should be used for all new budget feature work.

## Current package layout (flat `data/`)

`features/budget/data/` is currently a flat package. The classes here are grouped by purpose:

- **Canonical entities + DAOs**: core persisted budget schema and CRUD/query access.
  - `BudgetAccount`, `BudgetCategory`, `BudgetTransactionEntity`, `BudgetLimit`
  - `BudgetLookupDao`, `TransactionDao`, `BudgetLimitDao`
- **Import metadata persistence**: import bookkeeping storage.
  - `BudgetImportEntity`, `BudgetImportDao`, `BudgetImportRoomRepository`
- **Recurring template persistence**: recurring suggestion templates persisted for import workflows.
  - `BudgetRecurringTemplateEntity`, `BudgetRecurringTemplateDao`
- **Repository implementations**: adapters used by application/UI layers.
  - `BudgetRoomRepository`, `BudgetWidgetRoomRepository`
- **Read-model/projection classes**: query results for summaries, charting, and widgets.
  - `IncomeExpenseSummary`, `MonthlyTransactionOverviewItem`, `CategorySpendTotal`, `AccountBalanceTotal`, `AccountDailyDeltaPoint`, `AccountMonthlyDeltaPoint`

## Placement rule for new code

- Add new budget persistence entities, DAOs, repositories, and query projection models directly in `features/budget/data/`.
- Keep canonical budget persistence on the `budget_*` schema classes listed above.
- Do not add references to `data/importing` or `data/legacy` while this package remains flat.
