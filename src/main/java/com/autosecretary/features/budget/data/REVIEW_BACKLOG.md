# Review Backlog — budget/data

## Open Issues

| Sev | File:Line | Issue |
|-----|-----------|-------|
| [warning] | dao/BudgetTransactionDao.java:48,63,77,85 + dao/BudgetLookupDao.java:47 | `CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END` repeated 5 times. No compile-time guard if `TransactionDirection` enum storage changes. Requires SQL view; Room doesn't support constants in @Query. **Deferred pending architectural decision on view support.** |
| [warning] | dao/BudgetRecurringTemplateDao.java:26,34 | Methods `findActiveExpenseTemplatesForAccountInRange` and `findActiveExpenseTemplatesForActiveAccountsInRange` share four identical filter conditions. Any business-rule change must be applied twice. Requires SQL view. **Deferred.** |
| [nit] | dao/BudgetLookupDao.java:45–54 | `rebuildAllAccountBalances()` is a bulk UPDATE on `budget_account` housed in a "Lookup" DAO. Reads from `budget_transaction` to write to `budget_account` — cross-table mutation in a read-oriented interface. Moving to `BudgetTransactionDao` would be equally awkward (DAO writing to another table). Correct fix is a dedicated `BudgetAccountDao` or moving balance maintenance to a repository-level transaction helper. **Deferred — broader DAO restructure.** |
| [inconsistent] | entity/ (all 6 entities) | Entity naming: `Entity` suffix applied inconsistently. `BudgetTransactionEntity`, `BudgetRecurringTemplateEntity`, `BudgetImportEntity` carry the suffix; `BudgetAccount`, `BudgetCategory`, `BudgetLimit` do not. Canonical direction: add `Entity` suffix to the three that lack it (makes Room entities distinguishable from domain objects at a glance). **Deferred — touches domain interface `BudgetRepository`, `AppDatabase`, `AppCompositionRoot`, and callers outside budget scope.** |

---

### [inconsistent] DAO method naming: task feature uses `read*/write*/delete*`; budget feature uses `find*/get*/insert*/update*/delete*`

**Concept / area:** DAO method naming convention

**Observed patterns:**
- Task DAOs (`TaskDao`, `TaskScheduleConfigDao`): `readAll()`, `read(id)`, `writeList()`, `write()`, `writeCore()`, `writeSlots()`, `writePrefSlots()`, `writeAll()`, `deleteCore()`, `deleteRelationsByParentId()` — custom project convention: `write*` means upsert (REPLACE strategy)
- Budget DAOs (`BudgetTransactionDao`, `BudgetLookupDao`, `BudgetLimitDao`, `BudgetImportDao`, `BudgetRecurringTemplateDao`): `findAll()`, `findById()`, `findActiveCategories()`, `getMonthlyOverview()`, `getNetBalanceCents()`, `insert()`, `insertAll()`, `update()`, `deleteById()`, `existsByImportHash()`, `markCompleted()`, `markFailed()` — standard JPA/Spring-style naming

**Why it matters:** A contributor working across features faces two incompatible conventions for "same concept" methods (`readAll` vs `findAll`, `write(entity)` vs `insert(entity)`). The task convention's `write*` naming signals an upsert semantic (REPLACE) that is invisible in the budget DAOs despite many also using `onConflict = REPLACE`.

**Canonical recommendation:** Align budget DAOs to the task convention (`read*`/`write*`/`delete*`). This is the explicit project convention documented in `task/data/TaskDao.java` Javadoc ("All writes use REPLACE conflict strategy (upsert)"). The budget DAOs using `insert(onConflict = REPLACE)` are semantically upserts and should be named `write*` for clarity. Those with `OnConflictStrategy.IGNORE` should keep a distinct name.

**Impact:** All 5 budget DAOs + all callers in `BudgetRoomRepository`, `BudgetImportRoomRepository`, `BudgetViewModel`. Large coordinated rename — deferred.

*(New finding — conventions review)*
