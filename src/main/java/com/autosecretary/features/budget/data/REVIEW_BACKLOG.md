# Review Backlog — budget/data

## Open Issues

| Sev | File:Line | Issue |
|-----|-----------|-------|
| [warning] | dao/BudgetTransactionDao.java:48,63,77,85 + dao/BudgetLookupDao.java:47 | `CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END` repeated 5 times. No compile-time guard if `TransactionDirection` enum storage changes. Requires SQL view; Room doesn't support constants in @Query. **Deferred pending architectural decision on view support.** |
| [warning] | dao/BudgetRecurringTemplateDao.java:26,34 | Methods `findActiveExpenseTemplatesForAccountInRange` and `findActiveExpenseTemplatesForActiveAccountsInRange` share four identical filter conditions. Any business-rule change must be applied twice. Requires SQL view. **Deferred.** |
| [nit] | dao/BudgetLookupDao.java:45–54 | `rebuildAllAccountBalances()` is a bulk UPDATE on `budget_account` housed in a "Lookup" DAO. Reads from `budget_transaction` to write to `budget_account` — cross-table mutation in a read-oriented interface. Moving to `BudgetTransactionDao` would be equally awkward (DAO writing to another table). Correct fix is a dedicated `BudgetAccountDao` or moving balance maintenance to a repository-level transaction helper. **Deferred — broader DAO restructure.** |
| [inconsistent] | entity/ (all 6 entities) | Entity naming: `Entity` suffix applied inconsistently. **Promoted to `budget/REVIEW_BACKLOG.md`.** |

---

### [inconsistent] DAO method naming — **promoted to `features/REVIEW_BACKLOG.md`** (cross-feature scope)

---

### [rename] `BudgetLookupDao` — "Lookup" suffix hides write operations

**Paths involved:** `dao/BudgetLookupDao.java`, `repository/BudgetRoomRepository.java`, `repository/BudgetImportRoomRepository.java`, `database/AppDatabase.java`, `app/AppCompositionRoot.java`

**What makes it hard to navigate today:** The name `BudgetLookupDao` implies a read-only interface ("lookup = find/query"). In practice it also performs writes: `adjustCurrentBalanceCents`, `rebuildAllAccountBalances`, `insertAccount`, `insertCategory`. A reader expecting read-only methods here will be surprised; a reader looking for "where do I put a new account write?" will not think to look in `BudgetLookupDao`.

**Proposed structural change:** Rename to `BudgetAccountCategoryDao` — accurately signals that this DAO manages all account and category operations (reads and writes). Alternatively `BudgetReferenceDataDao`.

**Why it reduces mental load:** The name matches the DAO's actual scope (accounts + categories, both R/W) rather than its access pattern ("lookup"). Callers in `BudgetRoomRepository` and `BudgetImportRoomRepository` would use `accountCategoryDao` which is self-describing.

**Tradeoffs / risks:** Touches 4+ files outside `budget/data/`. Medium churn. Deferred.

*(New finding — structure review)*

