# Review Backlog — budget/data/dao

## Open Issues

### [warning] SQL CASE WHEN sign convention repeated 5 times @skill:review-smells
**Files/Lines:**
- `BudgetTransactionDao.java:88` — getDailyDeltasForAccount
- `BudgetTransactionDao.java:115` — getMonthlyDeltasForAccount
- `BudgetTransactionDao.java:139` — getNetAmountBeforeDateForAccount
- `BudgetTransactionDao.java:154` — getNetBalanceCents
- `BudgetAccountCategoryDao.java:128` — rebuildAllAccountBalances

**What the smell is:**
The SQL pattern `CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END` implements the sign convention (INCOME adds, EXPENSE subtracts) and is duplicated 5 times across two files. No compile-time guard if `TransactionDirection` enum storage changes.

**Why it will cause problems:**
1. If the income/expense column name or enum representation ever changes, all 5 queries must be updated in sync
2. The hardcoded string `'INCOME'` has no compile-time connection to the enum — a future developer might change one without noticing the SQL strings
3. New queries using this pattern will naturally perpetuate the duplication

**Concrete fix:**
Create a SQL view (e.g., `budget_transaction_signed`) with a pre-computed `signedAmountCents` column that applies the sign convention. Room supports views via `@DatabaseView`. Then all queries join to this view instead of computing the sign inline.

**Status:** Deferred — Room's compile-time query validator cannot validate queries against views (views are created at runtime). Attempted Room @DatabaseView approach failed with "[SQLITE_ERROR] SQL error or missing database (no such table: budget_transaction_signed_view)" during annotation processing. This is a fundamental limitation of Room's query validation; would require either disabling validation, using @RawQuery (losing type safety), or migrating away from Room.

---

### [warning] BudgetRecurringTemplateDao — WHERE clause duplication @skill:review-smells
**Files/Lines:**
- `BudgetRecurringTemplateDao.java:46-53` — findActiveExpenseTemplatesForAccountInRange
- `BudgetRecurringTemplateDao.java:65-74` — findActiveExpenseTemplatesForActiveAccountsInRange

**What the smell is:**
Both methods filter for the same three conditions:
- `active = 1`
- `transactionType = 'EXPENSE'`
- `nextDue BETWEEN :fromDate AND :toDate`

If the business rule for "active expense templates in a date range" changes, both queries must be updated in sync.

**Why it will cause problems:**
1. Any change to what "active" or "expense in range" means must be applied to two places
2. Divergence risk: one query might be updated while the other is forgotten
3. The duplication hides the fact that these are variants of the same logical query

**Concrete fix:**
Extract the shared filter conditions into a SQL view that filters for active expense templates. Both queries can then share the base query logic more cleanly.

**Status:** DEFER — simpler duplication pattern; can be addressed separately from the sign-convention view.

---

### [consider] N+1 per-transaction DB queries during import
**File:** `BudgetTransactionDao.java:231` (`existsByImportHash`), `BudgetAccountCategoryDao.java:47` (`findCategoryById` via `isKnownCategory`)

**What the problem is:**
`BudgetImportUseCase.buildTransactions()` calls two single-row lookups in a loop for every parsed transaction:
1. `repository.existsTransactionByImportHash(txHash)` — one `SELECT COUNT(*) > 0 WHERE importHash = ?` per transaction
2. `repository.isKnownCategory(categoryId)` — one `SELECT * WHERE id = ? LIMIT 1` per transaction

For a 100-transaction PDF import, this is up to 200 individual DB queries.

**Expected impact:**
Each query is fast: `importHash` and `id` are both indexed. At ~0.1ms per query, 200 queries ≈ 20ms overhead. The import already takes several seconds (Claude API call), so 20ms is imperceptible in practice. Impact is low at realistic statement sizes (< 200 transactions).

**Recommended fix (requires changes outside `/data`):**
- Replace `isKnownCategory` loop calls with a single `loadActiveCategoriesForImport()` (already exists in `BudgetImportRepository`) called before the loop; use a `Set<String>` for O(1) membership check.
- Replace `existsTransactionByImportHash` loop calls with a batch `WHERE importHash IN (:hashes)` query to resolve all hashes at once.
Both fixes are primarily in `BudgetImportUseCase.java` (application layer, outside scope).

**Status:** Deferred — primary fix requires changes to `BudgetImportUseCase.java` outside `/budget/data`. Impact is also low at realistic import sizes.

---

### [nit] BudgetImportDao.markCompleted() — excessive parameters @skill:review-simplicity
**File/Lines:** `BudgetImportDao.java:38`

**What the smell is:**
The method signature has 5 parameters:
```java
void markCompleted(String id, int total, int imported, int autoCategorized,
                   LocalDate periodStart, LocalDate periodEnd)
```

The four integer/date parameters form a cohesive "ImportResult" concept that always move together.

**Why it will cause problems:**
1. Call sites are error-prone: `dao.markCompleted(id, t, i, a, start, end)` — unclear which param is which
2. Adding a new statistic (e.g., failedCount, successRate) will force signature changes
3. The parameter grouping is not self-documenting

**Attempted fix:**
Creating a value object would require Room to support complex object binding in @Query, which requires:
- Either inline field extraction with Room's SpEL syntax (not supported for UPDATE queries)
- Or moving the logic to a default method with manual extraction (adds complexity without solving the readability issue)

**Status:** Deferred — Room's @Query limitations make value object extraction in UPDATE statements infeasible without significant refactoring.
