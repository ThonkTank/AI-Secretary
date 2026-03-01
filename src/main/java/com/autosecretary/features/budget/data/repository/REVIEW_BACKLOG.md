# Review Backlog — budget/data/repository

## Open Issues

### [nit] BudgetImportRoomRepository depends on too many DAOs @skill:review-architecture

**Files/Lines:** `BudgetImportRoomRepository.java:36-42` (constructor params)

**What the smell is:**
The class takes 5 constructor parameters (4 DAOs + callback):
```java
public BudgetImportRoomRepository(BudgetImportDao importDao,
                                  BudgetRecurringTemplateDao templateDao,
                                  BudgetTransactionDao transactionDao,
                                  BudgetAccountCategoryDao accountCategoryDao,
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


