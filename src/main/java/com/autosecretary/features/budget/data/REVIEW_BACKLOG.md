# Review Backlog — budget/data

## Open Issues

| Sev | File:Line | Issue |
|-----|-----------|-------|
| [warning] | dao/TransactionDao.java:74,89,103,111 + dao/BudgetLookupDao.java:47 | `CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END` repeated 5 times. No compile-time guard if `TransactionDirection` enum storage changes. Requires SQL view; Room doesn't support constants in @Query. **Deferred pending architectural decision on view support.** |
| [warning] | dao/BudgetRecurringTemplateDao.java:26,34 | Methods `findActiveExpenseTemplatesForAccountInRange` and `findActiveExpenseTemplatesForActiveAccountsInRange` share four identical filter conditions. Any business-rule change must be applied twice. Requires SQL view. **Deferred.** |
| [nit] | repository/BudgetRoomRepository.java:264 | `isAllAccounts` sentinel (null or blank = "all accounts") has hidden API contract not visible at `BudgetRepository` interface. Consider explicit constant or `Optional<String>` documentation. |
| [warning] | repository/BudgetRoomRepository.java:109–119 | Two competing balance-update strategies: `applyExpenseToAccountBalance` is a Java read-modify-write; `BudgetLookupDao.rebuildAllAccountBalances()` does the same in SQL. Write paths that bypass the Java method silently diverge. Remove the Java method and use SQL rebuild everywhere. |
