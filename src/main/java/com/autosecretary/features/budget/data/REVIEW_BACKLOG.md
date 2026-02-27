# Review Backlog — budget/data

## Open Issues

| Sev | File:Line | Issue |
|-----|-----------|-------|
| [warning] | dao/TransactionDao.java:48,63,77,85 + dao/BudgetLookupDao.java:47 | `CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END` repeated 5 times. No compile-time guard if `TransactionDirection` enum storage changes. Requires SQL view; Room doesn't support constants in @Query. **Deferred pending architectural decision on view support.** |
| [warning] | dao/BudgetRecurringTemplateDao.java:26,34 | Methods `findActiveExpenseTemplatesForAccountInRange` and `findActiveExpenseTemplatesForActiveAccountsInRange` share four identical filter conditions. Any business-rule change must be applied twice. Requires SQL view. **Deferred.** |
| [nit] | dao/BudgetLookupDao.java:45–54 | `rebuildAllAccountBalances()` is a bulk UPDATE on `budget_account` housed in a "Lookup" DAO. Reads from `budget_transaction` to write to `budget_account` — cross-table mutation in a read-oriented interface. Moving to `TransactionDao` would be equally awkward (DAO writing to another table). Correct fix is a dedicated `BudgetAccountDao` or moving balance maintenance to a repository-level transaction helper. **Deferred — broader DAO restructure.** |
