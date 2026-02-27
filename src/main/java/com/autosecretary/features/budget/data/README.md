# Budget data layer

## Package layout

`features/budget/data/` is split into sub-packages by concern:

- **`entity/`** — Room entity classes persisted to the database.
  - `BudgetAccount` (`budget_account`)
  - `BudgetCategory` (`budget_category`)
  - `BudgetTransactionEntity` (`budget_transaction`)
  - `BudgetLimit` (`budget_limit`)
  - `BudgetImportEntity` (`budget_import`)
  - `BudgetRecurringTemplateEntity` (`budget_recurring_template`)

- **`dao/`** — DAO interfaces for entity access and aggregate queries.
  - `BudgetLookupDao` — accounts, categories, balance adjustments
  - `BudgetTransactionDao` — transaction CRUD, timeline deltas, balance queries
  - `BudgetLimitDao` — budget limits and category spend summaries
  - `BudgetImportDao` — import record lifecycle (pending/completed/failed)
  - `BudgetRecurringTemplateDao` — recurring template queries and status updates

- **`repository/`** — Room-backed implementations of domain repository interfaces.
  - `BudgetRoomRepository` — implements `BudgetRepository`
  - `BudgetImportRoomRepository` — implements `BudgetImportRepository`

- **`keystore/`** — Non-Room secure storage.
  - `ClaudeApiKeyStore` — encrypted API key storage via Android Keystore + SharedPreferences

## Placement convention

Place new data-layer files in exactly one of these packages:

- `entity/` — persisted Room entities
- `dao/` — DAO interfaces for entity access and aggregate queries
- `repository/` — Room-backed implementations of domain repositories
- `api/` — external API HTTP clients (e.g. `ClaudeStatementApiClient` for PDF import via Claude API)
- `keystore/` — non-Room secure storage (Android Keystore, SharedPreferences encryption)
