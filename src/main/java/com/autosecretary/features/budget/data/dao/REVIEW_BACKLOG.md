# Review Backlog — budget/data/dao

## Open Issues

### [warning] SQL CASE WHEN sign convention repeated 5 times
**Files/Lines:**
- `BudgetTransactionDao.java:88` — getDailyDeltasForAccount
- `BudgetTransactionDao.java:115` — getMonthlyDeltasForAccount
- `BudgetTransactionDao.java:139` — getNetAmountBeforeDateForAccount
- `BudgetTransactionDao.java:154` — getNetBalanceCents
- `BudgetLookupDao.java:125` — rebuildAllAccountBalances

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

### [warning] BudgetRecurringTemplateDao — WHERE clause duplication
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

### [nit] BudgetImportDao.markCompleted() — excessive parameters
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

---

## Fixed Issues

### [improve] BudgetTransactionDao.deleteWithLinked() — unnecessary variable extraction
**File/Line:** `BudgetTransactionDao.java:298`

**What was fixed:**
The method had an unnecessary intermediate variable `linkedId` that only renamed the field without adding semantic meaning. Removed the variable to make the flow more direct.

**How it was fixed:**
Changed from:
```java
String linkedId = transaction.linkedTransactionId;
deleteById(transaction.id);
if (linkedId != null) {
    deleteById(linkedId);
}
```

To:
```java
deleteById(transaction.id);
if (transaction.linkedTransactionId != null) {
    deleteById(transaction.linkedTransactionId);
}
```

**Benefits:**
- Eliminates an intermediate variable that doesn't add meaning
- Makes the intent clearer: delete the transaction, then conditionally delete its linked partner
- Reduces cognitive load with fewer names to track

### [warning] BudgetImportDao — hardcoded status strings instead of enum
**File/Line:** `BudgetImportDao.java:49, 70`

**What was fixed:**
The `markCompleted()` and `markFailed()` methods were hardcoding status values as strings ('COMPLETED', 'FAILED') in SQL instead of using the `ImportStatus` enum type. This created magic strings with no compile-time connection to the enum.

**How it was fixed:**
1. Created a new `updateImportStatus()` method that accepts `ImportStatus` as a parameter
2. Refactored `markCompleted()` and `markFailed()` to be default methods that delegate to `updateImportStatus()`
3. This eliminates the magic strings and makes the code type-safe while maintaining backward compatibility

**Benefits:**
- If `ImportStatus` enum ever changes, the query will now use the correct values automatically
- Future code that needs to set import status must use the enum, not hardcoded strings
- The API surface is unchanged (existing `markCompleted()` and `markFailed()` still work)

### [simplify] BudgetLimitDao — Using CASE WHEN to filter instead of WHERE/ON
**Files/Lines:**
- `BudgetLimitDao.java:42` — getExpenseCentsForCategoryAndMonth
- `BudgetLimitDao.java:68` — getCategorySpendTotals (the SUM clause)

**What was fixed:**
Both queries used `CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END` to filter for EXPENSE transactions in the SELECT/SUM clause. This was less clear than filtering with WHERE or in the JOIN condition.

**How it was fixed:**
1. For `getExpenseCentsForCategoryAndMonth`: Moved `type = 'EXPENSE'` to the WHERE clause and removed the CASE WHEN, simplifying the query to `SUM(amountCents)`
2. For `getCategorySpendTotals`: Moved `t.type = 'EXPENSE'` to the LEFT JOIN ON condition and simplified the SUM to `SUM(t.amountCents)`

**Benefits:**
- Makes the filtering intent obvious at first glance (EXPENSE only) without requiring readers to parse the CASE logic
- Reduces indirection: the filter is now in the WHERE or JOIN ON where readers expect to find it
- Query logic is more direct and easier to maintain
