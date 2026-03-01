# Budget data layer

## Package layout

`features/budget/data/` is split into sub-packages by concern:

- **`entity/`** — Room entity classes persisted to the database.
  - `BudgetAccountEntity` (`budget_account`)
  - `BudgetCategoryEntity` (`budget_category`)
  - `BudgetTransactionEntity` (`budget_transaction`)
  - `BudgetLimitEntity` (`budget_limit`)
  - `BudgetImportEntity` (`budget_import`)
  - `BudgetRecurringTemplateEntity` (`budget_recurring_template`)

- **`dao/`** — DAO interfaces for entity access and aggregate queries.
  - `BudgetAccountCategoryDao` — accounts, categories, balance adjustments
  - `BudgetTransactionDao` — transaction CRUD, timeline deltas, balance queries
  - `BudgetLimitDao` — budget limits and category spend summaries
  - `BudgetImportDao` — import record lifecycle (pending/completed/failed)
  - `BudgetRecurringTemplateDao` — recurring template queries and status updates

- **`repository/`** — Room-backed implementations of domain repository interfaces.
  - `BudgetRoomRepository` — implements `BudgetRepository`
  - `BudgetImportRoomRepository` — implements `BudgetImportRepository`

- **`api/`** — External API integration: HTTP clients and secure credential storage.
  - `ClaudeStatementApiClient` — sends PDF statements to Claude API, parses JSON response
  - `ClaudeApiKeyStore` — AES-256-GCM encrypted storage of Claude API key via Android Keystore

## Repository Layer

**What is a repository?** A repository is a higher-level abstraction over DAOs. While DAOs handle single-table queries (e.g., "find all transactions"), repositories implement domain operations that may span multiple tables (e.g., "create a transfer between accounts and adjust balances") or maintain cross-table invariants.

**Entry points:**
- `BudgetRoomRepository` implements `BudgetRepository` (domain interface)
  - Coordinates account, category, transaction, and limit operations
  - Maintains invariants across multiple tables (e.g., transfer pairs, balance consistency)
  - Used by domain logic and app-layer use-cases
  - Key pattern: null/blank `accountId` means "aggregate over all active accounts"

- `BudgetImportRoomRepository` implements `BudgetImportRepository` (domain interface)
  - Manages the import workflow: records, transactions, recurring templates
  - Coordinates multiple DAOs to implement import phases
  - Synchronizes recurring template state after imports complete
  - Used by the import application service

**When to use:** Domain layer and application layer code receives repositories via dependency injection. Code that needs to persist or query budget data uses a repository interface, never DAOs directly.

**When to extend:** Add a new repository method when your operation spans multiple DAOs or maintains cross-table invariants. For single-table queries, extend the appropriate DAO instead.

## Placement convention

Place new data-layer files in exactly one of these packages:

- `entity/` — persisted Room entities
- `dao/` — DAO interfaces for entity access and aggregate queries
- `repository/` — Room-backed implementations of domain repositories
- `api/` — external API HTTP clients and secure credential storage (e.g. Claude API integration)
