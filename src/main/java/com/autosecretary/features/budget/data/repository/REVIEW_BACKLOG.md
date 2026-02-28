# Review Backlog — budget/data/repository

## Open Issues

### [warning] Silent failures could mask bugs in repository methods

**Files/Lines:**
- `BudgetRoomRepository.java:204-207` (updateTransaction by id)
- `BudgetRoomRepository.java:263-271` (updateTransfer — double null check)
- `BudgetImportRoomRepository.java:129-134` (linkTransactionsToTemplate)

**What the smell is:**
Multiple methods silently return null or false without throwing when preconditions fail:
1. `updateTransaction(id, ...)` returns silently if transaction not found (line 207)
2. `updateTransfer(...)` returns false silently if transaction is null or missing linked pair (lines 263-271)
3. `linkTransactionsToTemplate(...)` returns silently if parameters are invalid (lines 129-134)

**Why it will cause problems:**
1. Callers have no way to know if an operation succeeded or failed (except for updateTransfer which returns boolean)
2. UI may fail to refresh or display out-of-sync state
3. Silent failures compound: e.g., if a transaction lookup fails, the caller still thinks the update happened
4. Debugging is harder — you won't see an error log, just inconsistent state

**Concrete fix:**
- For `updateTransaction(id, ...)`: Either throw if not found, or return a boolean success indicator
- For `updateTransfer(...)`: The false return is correct, but consider whether a failed update should be logged or throw
- For `linkTransactionsToTemplate(...)`: Validate parameters and throw IllegalArgumentException if invalid, rather than silently skip

---

### [nit] Excessive parameter count in transfer methods

**Files/Lines:** `BudgetRoomRepository.java:232-247` (createTransfer) and `256-290` (updateTransfer)

**What the smell is:**
Both methods take 5 parameters:
```java
createTransfer(String sourceAccountId, String targetAccountId, long amountCents, LocalDate bookingDate, String note)
updateTransfer(String transactionId, String sourceAccountId, String targetAccountId, long amountCents, LocalDate bookingDate, String note)
```

The second set of parameters (sourceAccountId, targetAccountId, amountCents, bookingDate, note) appears together and could form a cohesive concept.

**Why it will cause problems:**
1. Call sites are hard to read: `repo.createTransfer(srcId, tgtId, 50000, LocalDate.now(), "note")` — which param is which?
2. Parameter order is easy to mix up
3. Adding new transfer properties (e.g., category override, transfer reason) will force method signature changes

**Concrete fix:**
Create a `TransferDetails` value object:
```java
record TransferDetails(String sourceAccountId, String targetAccountId,
    long amountCents, LocalDate bookingDate, String note) {}
```

Then methods become: `createTransfer(String txnId, TransferDetails details)`.

---

### [nit] Conditional complexity in updateTransfer

**Files/Lines:** `BudgetRoomRepository.java:256-290`

**What the smell is:**
The method has 3 levels of branching and null checks before performing the actual update:
1. Check if transaction is null OR missing linked (lines 263-266)
2. Check if linked transaction is null (lines 268-271)
3. Branch to determine debit vs credit based on direction (lines 274-281)
4. Then call populateTransferLeg twice (lines 283-286)
5. Update both legs (line 288)

**Why it will cause problems:**
1. Hard to verify correctness — the nesting and multiple exit points make mental tracking difficult
2. Harder to test — many paths to exercise
3. If new preconditions are added (e.g., validate account ownership), they accumulate in the method

**Concrete fix:**
Extract precondition checking into a helper method:
```java
private record TransferValidation(BudgetTransactionEntity debit, BudgetTransactionEntity credit) {}

private Optional<TransferValidation> validateTransferExists(String transactionId) {
    // All null checks and direction branching happen here
}
```

Then `updateTransfer` becomes cleaner:
```java
return validateTransferExists(transactionId)
    .map(validated -> {
        // Perform update
        return true;
    })
    .orElse(false);
```

---

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

✅ [warning] **Transfer leg construction duplicated** — Extracted `populateTransferLeg()` calls into a single `populateTransferPair()` method that atomically populates both debit and credit legs. Eliminates code duplication in both `createTransfer()` and `updateTransfer()`. Method now has clear responsibility and improved documentation. (BudgetRoomRepository.java:232-375)

