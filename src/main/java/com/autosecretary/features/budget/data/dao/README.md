# Budget DAO layer

## Quick reference

| Task | DAO | Method |
|------|-----|--------|
| Query all transactions for a month | BudgetTransactionDao | getMonthlyOverview() |
| Get balance timeline (daily/monthly) | BudgetTransactionDao | getDailyDeltasForAccount(), getMonthlyDeltasForAccount() |
| Create a transfer between accounts | BudgetTransactionDao | createTransferPair() |
| Adjust account balance after a transaction | BudgetAccountCategoryDao | adjustCurrentBalanceCents() |
| Query active accounts/categories | BudgetAccountCategoryDao | findActiveAccounts(), findActiveCategories() |
| Check category spending vs. limits | BudgetLimitDao | getCategorySpendTotals() |
| Manage import records | BudgetImportDao | insert(), markCompleted(), markFailed() |
| Manage recurring templates | BudgetRecurringTemplateDao | findActiveExpenseTemplates...(), updateAllTemplateStatuses() |

## Key concepts

### Transfer pairs (linked transactions)

Inter-account transfers are stored as two linked transactions:
- **Debit transaction:** `accountId = source`, `type = EXPENSE`, `amountCents = transfer amount`
- **Credit transaction:** `accountId = destination`, `type = INCOME`, `amountCents = transfer amount`

Both share the same `linkedTransactionId` and must be created/updated/deleted together using `createTransferPair()`, `updateTransferPair()`, and `deleteWithLinked()`.

### Balance calculation sign convention

- **INCOME** transactions add to balance (positive)
- **EXPENSE** transactions subtract from balance (negative)

All aggregate queries apply this convention using: `CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END`

### When to use a DAO vs. a repository

- **Use a DAO directly:** Single-table queries or simple operations (finding a transaction by ID, inserting a single transaction)
- **Use a repository:** Operations spanning multiple tables, cross-table invariants, or complex workflows (import + sync templates, transfer pairs)

Repositories wrap DAOs and are injected into application-layer code. DAOs are rarely accessed directly outside the `features/budget/data/` package.

## DAO overview

- **`BudgetTransactionDao`** — transaction CRUD, timeline queries, transfer pair operations
- **`BudgetAccountCategoryDao`** — account/category queries and balance adjustments
- **`BudgetLimitDao`** — category budget limits and spending summaries
- **`BudgetImportDao`** — import record lifecycle
- **`BudgetRecurringTemplateDao`** — recurring transaction template queries and status updates

See `features/budget/data/README.md` for repository-level architecture and the broader budget feature design.
