# Budget Application Layer

This package contains the **orchestration logic** that bridges the budget UI and the domain/data layers.
The application layer handles use cases — workflows that don't belong in the domain (because they're Android/framework-specific)
and aren't just database lookups (which belong in the data layer).

## What's Here

### Root-Level Use Cases (synchronous, general budget operations)

These are the main entry points for budget workflows, called from the UI:

- **`LoadBudgetWidgetSummaryUseCase`** — loads net balance + free budget for the home screen widget
- **`CalculateEffectiveBudgetLimitUseCase`** — calculates a category's effective monthly limit,
  accounting for rollover from previous months (see class javadoc for rollover concept explanation)
- **`CreateTransferUseCase`** — creates/updates internal transfers between accounts
  (distinct from transactions; see class javadoc for the difference)
- **`BudgetSeedService`** — initializes default accounts, categories, and demo transactions on first app launch

### `importing/` Sub-Package (asynchronous, statement import + recurring detection)

All import-related orchestration:

- **`BudgetImportUseCase`** — end-to-end import pipeline: parse file → deduplicate → enrich → persist → detect patterns
- **`internal/StatementFileParser`** — routes parsing by file type (CSV local, PDF via Claude API)
- **`BudgetTransactionMapper`** — maps between domain and persistence models
- **`ApplyRecurringSuggestionsUseCase`** — accepts detected recurring patterns and creates templates

**See [`importing/README.md`](./importing/README.md)** for the complete import pipeline,
CSV format, error handling, and troubleshooting.

## Reading Order for Newcomers

### If you're implementing a new budget feature:

1. Start here (this README) to understand the application layer's role
2. Read the class javadocs for whichever use case you're touching
3. Check `features/budget/domain/README.md` if you need to understand domain concepts
   (e.g., what makes a transaction "recurring", how rollover works, etc.)

### If you're working on bank statement import:

1. Read [`importing/README.md`](./importing/README.md) — it has a detailed pipeline diagram,
   error scenarios, testing instructions, and troubleshooting
2. Drill into individual use cases (`BudgetImportUseCase`, `internal/StatementFileParser`, etc.)
   as needed; their javadocs reference the README for context

### If you're debugging a specific use case:

1. Find the use case class (e.g., `CalculateEffectiveBudgetLimitUseCase`)
2. Read the class javadoc first (explains intent and key concepts)
3. Read method javadocs for detailed behavior and parameters
4. Check `features/budget/data/` for repository signatures (to understand the data access contract)
5. Check `features/budget/domain/` for domain types and business logic

## Key Concepts

### Accounts

A budget account represents a container (e.g., Checking, Savings). Transactions and transfers
are tagged with an account. The app tracks balance per account.

### Categories

Categories are labels for transactions (Rent, Groceries, Salary, etc.).
Each category has a direction (INCOME or EXPENSE), icon, and optional monthly spending limit.

### Transactions

A transaction is a single flow of money in/out of an account from an external source (paycheck, purchase, etc.).
Represented as a signed amount: positive = income, negative = expense.

### Transfers

A transfer moves money between two of the user's own accounts (Checking → Savings).
It creates a paired debit/credit entry (money leaves source, enters target) on the same date.
Distinct from transactions, which are external flows.

### Rollover

When a budget category has a monthly limit, "rollover" means carrying forward unused (or overspent) budget
to the next month. For example:
- If January limit is €1000 and only €800 is spent, €200 of "unused budget" can roll forward to February
- This is typically capped to avoid extreme carryover (e.g., cap positive rollover at €100)

See `CalculateEffectiveBudgetLimitUseCase` javadoc for a detailed example.

### Import Deduplication

When a user imports a statement file, the system tracks which transactions have been imported before
using an `importHash` (SHA-256 of file contents) or a fingerprint (date + amount + payee).
This prevents the same transaction from appearing twice if the user re-imports the same file.

### Recurring Detection

After a successful import, the system analyzes all transactions in the account to detect patterns
(e.g., salary on the 1st of every month, rent on the 15th, weekly gym fee).
These are suggested to the user as "recurring transaction templates" that can be auto-generated.

## Integration Points

### UI → Application

The UI (fragments, dialogs, view models) calls use case methods. Example:

```java
// UI wants to import a CSV file
executor.execute(() -> {
    try {
        ImportResult result = budgetImportUseCase.execute(accountId, fileName, fileBytes, mimeType);
        // Post result back to UI
    } catch (RuntimeException error) {
        // Post error to UI
    }
});
```

### Application → Domain

Use cases call domain layer to access business logic and domain types.
Domain layer is not Android-aware; it has no dependencies on android.* packages.

Example: `RecurringPatternDetector` (domain) analyzes transactions and suggests patterns.

### Application → Data

Use cases call repositories (data layer contracts) to persist/load data.
Repositories abstract away Room, SQL, and other data-access concerns.

Example: `BudgetImportRepository.saveTransactionsBatch()` persists enriched transactions.

## Common Patterns

### Background Operations

Long-running operations stay synchronous at the use-case boundary. Callers dispatch them via
`ExecutorService` and post results back to the UI:

```java
executor.execute(() -> {
    Result result = useCase.execute(...);
    liveData.postValue(result);
});
```

This keeps threading ownership in the ViewModel and avoids nested executor dispatch.

### Validation + Error Handling

Use cases validate input and return user-facing error messages (in German).
Errors are communicated via thrown exceptions or Result record.

Example: `CreateTransferUseCase.Result` contains a boolean `success` and optional `errorMessage`.

### Idempotency

Some operations are idempotent and safe to retry:
- `BudgetSeedService.ensureDefaultData()` only creates data if not present
- Re-importing the same statement file is safe; duplicates are skipped

---

## Public Resources & References

- **Budget/rollover concepts:** See your accounting software (Mint, YNAB, GnuCash) or personal finance blogs
- **Room (Android SQLite):** [Official Room docs](https://developer.android.com/training/data-storage/room)
- **Executor service & threads:** [Android threading guide](https://developer.android.com/guide/components/processes-and-threads)
- **Design pattern (MVVM):** [MVVM on Android](https://developer.android.com/jetpack/guide)

---

## Files at a Glance

```
application/
├── README.md                                    (this file)
│
├── LoadBudgetWidgetSummaryUseCase.java         loads widget summary
├── CalculateEffectiveBudgetLimitUseCase.java   calculates effective monthly limit w/ rollover
├── CreateTransferUseCase.java                  creates/updates transfers between accounts
├── BudgetSeedService.java                      initializes default data on first launch
│
└── importing/                                   (import sub-package)
    ├── README.md                                (comprehensive import pipeline docs)
    ├── BudgetImportUseCase.java                 orchestrates end-to-end import
    ├── internal/StatementFileParser.java        routes CSV/PDF parsing
    ├── BudgetTransactionMapper.java             maps domain ↔ persistence models
    └── ApplyRecurringSuggestionsUseCase.java    accepts recurring patterns + creates templates
```
