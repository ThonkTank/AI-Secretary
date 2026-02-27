# Review Backlog — budget/data

## Open Issues

| Sev | File:Line | Issue |
|-----|-----------|-------|
| [warning] | dao/TransactionDao.java:48,63,77,85 + dao/BudgetLookupDao.java:47 | `CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END` repeated 5 times. No compile-time guard if `TransactionDirection` enum storage changes. Requires SQL view; Room doesn't support constants in @Query. **Deferred pending architectural decision on view support.** |
| [warning] | dao/BudgetRecurringTemplateDao.java:26,34 | Methods `findActiveExpenseTemplatesForAccountInRange` and `findActiveExpenseTemplatesForActiveAccountsInRange` share four identical filter conditions. Any business-rule change must be applied twice. Requires SQL view. **Deferred.** |
| [coupling] | repository/BudgetImportRoomRepository.java:130–131 | `notifyBudgetDataUpdated()` on `BudgetImportRepository` (domain interface) is explicitly acknowledged in its Javadoc as "not a pure persistence operation." It exists because the Room implementation owns LiveData, making a UI-lifecycle concern visible on a persistence abstraction. Fix requires introducing an Observer/callback at the application layer to decouple. **Deferred — larger refactor.** |
