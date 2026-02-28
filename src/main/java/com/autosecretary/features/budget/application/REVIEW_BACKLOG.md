# Review Backlog — budget/application

## Open Issues

### [nit] `BudgetSeedService.addDemoTx` — 8-parameter method
**File:** `BudgetSeedService.java:115`

`addDemoTx(out, accountId, categoryId, ref, day, type, amountCents, note)` takes 8 arguments,
well above the 4-param threshold. All positional arguments are the same type mix; swapping
`amountCents` and `day` would compile silently and seed wrong amounts on wrong days.

**Suggested fix:** Introduce a local record `DemoEntry(int day, TransactionDirection direction, long amountCents, String note)`
to carry the per-transaction data and loop over a `List<DemoEntry>` in `seedDemoTransactions`.
**Deferred** — the method is only called from one stable seed path. Low churn risk.

---

## Fixed This Run

✅ [nit] **`StatementFileParser.parse()` — `isPdf()` computed twice per call** —
`parse()` called `isPdf(fileName, mimeType)` explicitly, then called `accepts()` which also
calls `isPdf()`. Replaced `!accepts(...)` with `!isCsv(...)` — equivalent because at that
point `isPdf` is already known to be false.
(`StatementFileParser.java:46`)

---

✅ [critical] **Pattern detection silently failed for accounts with any TRANSFER transactions** —
`loadTransactionsForAccount` returns all transactions including TRANSFERs, but
`BudgetTransactionMapper::toDomain` throws `IllegalArgumentException` on TRANSFER records.
The outer try-catch swallowed the error and returned no suggestions.
Fixed by filtering out TRANSFER records before the `toDomain` mapping.
(`importing/BudgetImportUseCase.java:106-108`)

---

## Acknowledged Good Patterns

### [keep] `importing/README.md` comprehensive pipeline documentation
**Path:** `application/importing/README.md`

Excellent documentation with:
- Pipeline diagram (ASCII art)
- CSV format specification with example
- Error scenarios with user-facing messages
- Troubleshooting section
- Testing instructions
- Clear class responsibilities and entry points
- Reference links to domain and data layer docs

A newcomer reading this understands the entire import workflow without asking questions.

---

### [keep] `importing/` sub-package for all import-related code
**Path:** `application/importing/`

The import sub-package cleanly groups all import-related orchestration (use cases,
transaction mapping, file parsing) while keeping unrelated workflow use cases at the
`application/` root. A reader scanning for import behaviour knows exactly where to look.

---

### [keep] Flat use-case surface at `application/` root
**Path:** `application/CalculateEffectiveBudgetLimitUseCase.java`,
`application/CreateTransferUseCase.java`,
`application/LoadBudgetWidgetSummaryUseCase.java`,
`application/BudgetSeedService.java`

Four files at the root — a reader can take in the complete public orchestration surface
without drilling into sub-packages. Adding a new workflow use case here is straightforward.

---

### [keep] Excellent method-level javadoc in importing use cases
**Path:** `application/importing/BudgetImportUseCase.java`,
`application/importing/ApplyRecurringSuggestionsUseCase.java`

Both use cases include detailed method javadocs explaining:
- Purpose and when/how the method is used
- Parameters and return values
- Side effects and error handling
- References to related domain concepts and README docs

This makes the code teachable even without external docs.
