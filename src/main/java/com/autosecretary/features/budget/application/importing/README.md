# Budget Import Application Layer

This package orchestrates the end-to-end import workflow for bank statements and recurring transaction detection.

## Overview

When a user uploads a bank statement (CSV or PDF), this layer:
1. **Parses** the file (CSV locally; PDF via Claude API)
2. **Deduplicates** using importHash to prevent re-importing the same file
3. **Maps & Enriches** transactions with account context and category resolution
4. **Persists** the result to the database
5. **Detects Patterns** to suggest recurring transactions for user review

## Import Pipeline

```
User selects file (CSV or PDF)
         ↓
internal/StatementFileParser.parse()
    ├─ CSV: parse locally using expected column format
    └─ PDF: send to Claude API (requires API key in ClaudeApiKeyStore)
         ↓
ParsedStatement (raw extraction)
    - List<ParsedTransaction> — minimal data (date, amount, payee, note)
    - periodStart, periodEnd — file's date range
         ↓
BudgetImportUseCase.buildTransactions()
    - Deduplication (check importHash against existing)
    - Category resolution (known category? else default)
    - Direction inference (INCOME vs. EXPENSE)
         ↓
ImportTransactionRecord[] (enriched, ready to persist)
         ↓
Repository.saveTransactionsBatch()
    - Persist as BudgetTransactionEntity
    - Update account balance summary
         ↓
RecurringPatternDetector.detectPatterns()
    - Analyze new transactions + account history
    - Return RecurringSuggestion[] for user review
         ↓
ImportResult (summary for UI)
    - totalTransactions, newTransactions, duplicates, autoCategorized
    - recurringSuggestions (high-confidence recurring pattern candidates)
```

## CSV Format

If importing from CSV, the file must have:
- **Header row** (ignored): any text
- **Data rows** (one per line, comma-separated):
  1. `date` — ISO 8601 format (e.g., `2024-01-15`)
  2. `amountCents` — integer; positive for income, negative for expense
  3. `payee` — string (e.g., `Amazon Inc.`, `Employer Ltd.`); empty acceptable
  4. `description` — string (e.g., `Monthly subscription`); empty acceptable
  5. `categoryId` (optional) — UUID of a known category; empty acceptable
  6. `importHash` (optional) — deduplication hash; empty acceptable

**Example:**
```csv
date,amount,payee,description,categoryId,importHash
2024-01-15,150000,Employer Ltd.,January salary,,
2024-01-20,-5000,Amazon Inc.,Electronics purchase,,abc123def456
2024-01-25,-2000,Utilities Inc.,Electric bill,,
```

**Notes:**
- Empty cells (,,) are treated as null; they're fine.
- If `importHash` is omitted, the system generates one from date+amount+payee (less reliable).
- If `categoryId` is unknown or missing, the system assigns a default category (e.g., "Uncategorized Income").

## PDF Import

PDFs are parsed by Claude API (`ClaudeStatementApiClient`). The API key must be configured in app settings (Budget → Settings → Claude API Key) and is stored securely via `ClaudeApiKeyStore`. Failure to configure the key will raise an error at import time.

## Key Classes

### internal/StatementFileParser
Routes files by type and delegates to CSV or PDF parsing.

**Entry point:** `parse(fileName, fileBytes, mimeType) → ParsedStatement`

- Accepts `.csv` or `.pdf` by file extension or MIME type
- Raises `IllegalArgumentException` if file type is unsupported
- Raises `IllegalArgumentException` if PDF mode is requested but no API key is configured

### BudgetImportUseCase
Orchestrates the full import pipeline: parse → deduplicate → map → persist → pattern detect.

**Entry point:** `execute(accountId, fileName, fileBytes, mimeType) -> ImportResult`

Runs synchronously on the caller's thread. The ViewModel creates and completes import records on
the DB executor, runs file parsing on the I/O executor, and posts the result back to the UI.

**Key internal methods:**
- `buildTransactions()` — deduplication and category resolution per transaction
- `buildTransactionFingerprint()` — generates dedup hash if parser didn't provide one

**Error handling:**
- If any step fails, the entire import is rolled back (no partial persistence).
- If pattern detection fails after a successful import, the import succeeds anyway (pattern detection is best-effort).
- User-facing errors are translated to German.

### BudgetTransactionMapper
Bidirectional mapping between domain models (for business logic) and persistence models.

**Methods:**
- `toRecord(RecurringBudgetTransaction)` — domain → persistence (ImportTransactionRecord)
- `toDomain(ImportTransactionRecord)` — persistence → domain

**Note:** Transfer records are handled separately; this mapper only handles INCOME and EXPENSE transactions.

### ApplyRecurringSuggestionsUseCase
User accepts recurring suggestions → system creates templates and links existing transactions.

**Entry point:** `execute(accountId, suggestions)`

Runs synchronously on the caller's thread. For each accepted suggestion:
1. Compute the next due date based on the recurring type and today's date
2. Create a recurring template in the database
3. Link existing transactions to this template (for historical tracking)
4. Notify the UI of the update

**Recurring types** (from `RecurringSuggestion.suggestedType`):
- `MONTHLY_DAY` — fixed day of month (e.g., 15th); wraps month-end if day doesn't exist in target month
- `MONTHLY_LAST` — last day of month
- `WEEKLY` — fixed day of week (e.g., Monday)
- `INTERVAL` — every N days

## Integration Points

### UI → Application (from `budget/ui/`)
- Import flow triggered by file picker → `BudgetViewModel.importFromCsv()`: DB setup on
  `dbExecutor`, parsing on `ioExecutor`, DB completion back on `dbExecutor`
- Recurring suggestions reviewed by user → `ApplyRecurringSuggestionsUseCase.execute()`

### Application → Domain (from `budget/domain/`)
- `internal/StatementFileParser` delegates PDF extraction to `ClaudeStatementApiClient`
- `BudgetImportUseCase` calls `RecurringPatternDetector.detectPatterns()` after import
- Types used: `ParsedStatement`, `ParsedTransaction`, `RecurringSuggestion`, `RecurringBudgetTransaction`

### Application → Data (from `budget/data/`)
- `BudgetImportUseCase` persists via `BudgetImportRepository` (injected)
- `ApplyRecurringSuggestionsUseCase` creates templates via repository

## Error Scenarios

| Scenario | Error | User-Facing Message |
|----------|-------|---------------------|
| File is not CSV or PDF | `IllegalArgumentException` | "Nicht unterstütztes Dateiformat: {fileName}" |
| PDF mode but no API key configured | `IllegalArgumentException` | "Kein Claude API-Key hinterlegt. Bitte in den Budget-Einstellungen setzen." |
| CSV has <4 columns in a row | `IllegalArgumentException` | "Ungültige CSV-Zeile: {line}" |
| Date parsing fails | `IllegalArgumentException` | "Validierungsfehler beim Import: {reason}" |
| Any other error | `Exception` | "Technischer Fehler beim Import: {reason}" |
| Pattern detection fails (after import) | Logged as warning; import succeeds anyway | None; suggestions are empty |

## Testing & Validation

### Manual CSV Import
1. Create a test CSV:
   ```
   date,amountCents,payee,description
   2024-01-15,150000,Employer Ltd.,Salary
   2024-01-20,-5000,Amazon Inc.,Electronics
   ```
2. Select the file in the app's import dialog
3. Verify that transactions appear in the account and the balance chart updates
4. Re-import the same file → should deduplicate (0 new transactions reported)

### Manual PDF Import (requires API key)
1. Configure Claude API key in Budget Settings
2. Select a bank statement PDF
3. Verify extraction succeeds and transactions appear
4. Check for any required manual categorization

### Recurring Suggestions
1. Import a CSV with 3+ transactions from the same payee on regular dates (e.g., monthly on the 15th)
2. Verify a `RecurringSuggestion` is returned with high confidence
3. Accept the suggestion → verify the template is created and next-due date is correct

## Troubleshooting for Developers

| Issue | Check |
|-------|-------|
| Imports hang indefinitely | Check executor service in `AppCompositionRoot`; is background thread running? |
| Duplicate transactions appear despite same importHash | Check `findImportHashesForAccount()` and the import-hash query in `BudgetTransactionDao`. |
| Recurring suggestions never appear | Is `RecurringPatternDetector` being called? Check logs for "Pattern detection failed" warning. |
| CSV parsing fails on valid files | Check line-ending handling (split uses `\r?\n`). Verify column count ≥4. |
| PDF parsing fails with "API key missing" | Ensure API key is set in `ClaudeApiKeyStore` via budget settings. |
| Month-end recurring dates are wrong | Check `calculateNextDue()` for month-length clamping (Feb 30 → Feb 28/29). |

## References

- **Domain models:** See `features/budget/domain/importing/README.md` for ParsedTransaction, ImportTransactionRecord, and related types
- **Recurring detection:** See `features/budget/domain/recurring/README.md` for pattern detection algorithm and confidence scoring
- **Database entities:** See `features/budget/data/entity/` for BudgetTransactionEntity, BudgetRecurringTemplateEntity, etc.
- **API client:** See `features/budget/data/api/ClaudeStatementApiClient.java` for PDF extraction details
