# Review Backlog — budget/data/repository

## Open Issues

### [nit] BudgetImportRoomRepository depends on too many DAOs

**Files/Lines:** `BudgetImportRoomRepository.java:36-42` (constructor params)

**What the smell is:**
The class takes 5 constructor parameters (4 DAOs + callback):
```java
public BudgetImportRoomRepository(BudgetImportDao importDao,
                                  BudgetRecurringTemplateDao templateDao,
                                  BudgetTransactionDao transactionDao,
                                  BudgetLookupDao lookupDao,
                                  Runnable onBudgetDataUpdated)
```

**Why it will cause problems:**
1. Growing coupling — if another DAO is needed, add another parameter
2. Hard to test — 5 mocks to configure
3. Violates the Single Responsibility Principle for construction (too many dependencies to wire)
4. Suggests the class may be doing too much coordination

**Concrete fix:**
Consider consolidating related DAOs into a facade or injecting a single "BudgetDataLayer" object that holds all DAOs. This is a minor refactor and depends on broader architectural decisions.

Defer this — it's a symptom of broader DAO structure (which is already being reviewed at the `/data` level).

---

## Fixed Issues

✅ [warning] **Silent failures could mask bugs** — Added DEBUG-level logging to three methods:
- `updateTransaction(String transactionId, ...)` logs when transaction not found (BudgetRoomRepository.java:207)
- `updateTransfer(String transactionId, ...)` logs when transfer validation fails (BudgetRoomRepository.java:269)
- `linkTransactionsToTemplate(List, String)` logs when parameters are invalid (BudgetImportRoomRepository.java:135)

✅ [nit] **Excessive parameter count in transfer methods** — Created `TransferDetails` record to encapsulate transfer data (sourceAccountId, targetAccountId, amountCents, bookingDate, note). Reduced `createTransfer` from 5 params to 1, and `updateTransfer` from 6 params to 2.

✅ [nit] **Conditional complexity in updateTransfer** — Extracted precondition validation into `validateTransferExists(String transactionId)` helper method returning `Optional<TransferValidation>`.

✅ [warning] **Transfer leg construction duplicated** — Extracted `populateTransferPair()` method to atomically populate both debit and credit legs, eliminating duplication in both `createTransfer()` and `updateTransfer()`.

---

## Analysis Notes

**Scope review:** Two repository classes handling budget data persistence.
- **BudgetRoomRepository.java** (407 lines): Transaction and transfer CRUD, account/category lookups
- **BudgetImportRoomRepository.java** (234 lines): Import workflow, transaction batching, recurring template sync

**Review findings:** Code demonstrates good KISS principles:
- All methods are necessary and part of the public interface
- Helper methods (isAllAccounts, normalizeNote, previousYearMonth) are well-used without over-engineering
- TransferDetails encapsulation and validateTransferExists extraction are appropriate simplifications
- No dead code or unnecessary abstractions identified
- All documented improvements from previous reviews have been properly implemented
- Constructor parameter count in BudgetImportRoomRepository (5 params, all used) is deferred as a broader architectural concern
