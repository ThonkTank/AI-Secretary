# Budget data model (canonical as of AppDatabase v8)

## Canonical Room entities

The canonical persisted budget model is the `budget_*` schema introduced in `AppDatabase` migration `7 -> 8`:

- `BudgetAccount` (`budget_account`)
- `BudgetCategory` (`budget_category`)
- `BudgetTransactionEntity` (`budget_transaction`)
- `BudgetLimit` (`budget_limit`)

These are the entities registered in `AppDatabase` version 8 and should be used for all new budget feature work.

## Migration status

- ✅ Canonical model: `budget_*` entities and DAOs (`BudgetLookupDao`, `TransactionDao`, `BudgetLimitDao`).
- ⚠️ Transitional legacy model: pre-v8 classes/DAO moved under `data/legacy`.

Legacy classes are retained only to support migration and historical reference and now live under `data/legacy`.
Do **not** add new usages of legacy `accounts` / `transactions` tables.

Import metadata entities live in `data/importing`.


## Placement convention (single predictable rule)

Place new data-layer files in exactly one of these packages:

- `data/entity`: persisted Room entities for canonical budget tables.
- `data/dao`: DAO interfaces for canonical entity access and aggregate queries.
- `data/repository`: Room-backed implementations of domain repositories.
- `data/projection`: read/query models returned by DAO projections.
- `data/importing`: import-specific entities, DAOs, and recurring templates.

Avoid adding new classes directly under `data/`; use one of the packages above.
