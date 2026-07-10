# Budget Import Domain

This package contains **domain-layer DTOs and type-safe enums** for the budget import pipeline. These types represent the intermediate stages of importing bank statements and classifying transactions.

## What Is This?

The importing domain layer provides:

- **Type-safe classifications** for imported transactions (INCOME, EXPENSE, TRANSFER)
- **Immutable data transfer objects** representing parsed and enriched statement data
- **Domain contracts** between the application layer (parsing/validation) and the data layer (persistence)

These types form a contract between:
- **Upstream (application layer):** `internal/StatementFileParser` (CSV/PDF extraction) and `BudgetTransactionMapper` (enrichment)
- **Downstream (data layer):** Data access objects that persist these imports as `BudgetTransactionEntity`

## Import Pipeline Overview

```
User uploads file (CSV or PDF)
         ↓
internal/StatementFileParser (application/importing/internal/)
    - CSV: parse locally
    - PDF: send to Claude API for extraction
         ↓
ParsedStatement (this package)
    - Raw extracted data: ParsedTransaction[], periodStart, periodEnd
         ↓
BudgetTransactionMapper (application/importing/)
    - Enrich with account context
    - Resolve categories
    - Infer transaction type (INCOME/EXPENSE/TRANSFER)
    - Match against recurring patterns
         ↓
ImportTransactionRecord (this package)
    - Enriched, validated, ready to persist
         ↓
Data Layer (data/repository/)
    - Persist as BudgetTransactionEntity
    - Update account balance
```

## Key Types

### ParsedTransaction
Raw output from statement parsing (CSV or PDF extraction). Contains the bare minimum: date, amount, payee, optional category. Used internally by the parser before account enrichment.

### ParsedStatement
Container for a batch of parsed transactions plus the statement's date range. Represents a complete parsed statement file before filtering or enrichment.

### ImportTransactionRecord
Final enriched transaction ready for persistence. Includes account assignment, category resolution, type classification, and optional recurring template link. This is the last step before data layer insertion.

### ImportCategory
A category available during import (user-defined or system-provided) for classifying transactions. Contains a direction indicator (INCOME/EXPENSE) to hint at typical usage.

### ImportTransactionType
Type-safe enum for classifying transactions as INCOME, EXPENSE, or TRANSFER. Each type maps to a domain `TransactionDirection` and `TransactionKind` for persistence.

### ImportStatus
Lifecycle states for an import operation (PENDING, COMPLETED, FAILED). Tracks whether an import batch succeeded, failed, or is still in progress.

## Design Notes

### Why records, not classes?
Records (Java 16+) provide immutability by default, which is ideal for DTOs. See [Java Records](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html).

### Why a separate domain layer for import types?
- **Decouples** the import workflow from the data schema (ParsedTransaction is transient; BudgetTransactionEntity is persistent)
- **Type safety** via enums (ImportTransactionType) instead of string-based type codes
- **Testability** — domain logic can be tested without a database
- **Clarity** — the domain explicitly represents "parsed" vs. "persisted" states

### Why multiple ID fields in ImportTransactionRecord?
- `id`: Unique transaction ID (becomes @PrimaryKey in BudgetTransactionEntity)
- `importId`: Batch/session ID (groups transactions from a single import operation)
- `templateId`: Optional link to a recurring pattern template (for future pattern suggestions)
- `importHash`: Deduplication hash (prevents re-importing the same statement file)

Each serves a distinct purpose in the domain model. See ImportTransactionRecord Javadoc for details.

### Why does TRANSFER have direction=EXPENSE?
Transfers are modeled as internal account movements (not real income/expense). The `INTERNAL_TRANSFER` kind distinguishes them from true expenses. See ImportTransactionType Javadoc.

## Common Workflows

### Importing a CSV
1. User selects a CSV file with columns: `date, amountCents, payee, description, [categoryId], [importHash]`
2. `internal/StatementFileParser.parse()` reads the file → emits `ParsedStatement` with `ParsedTransaction[]`
3. `BudgetTransactionMapper.toImportRecords()` enriches with account/category context → emits `ImportTransactionRecord[]`
4. Data layer persists to `BudgetTransactionEntity`

### Importing a PDF
1. User selects a PDF statement
2. `internal/StatementFileParser.parse()` base64-encodes the file and sends it to the Claude API (model `claude-sonnet-4-20250514`)
3. Claude API returns extracted transactions
4. Same enrichment and persistence as CSV

### Handling Duplicates
- Each parsed transaction gets an `importHash` (derived from date + amount + payee)
- On re-import, the data layer checks: if importHash already exists in BudgetTransactionEntity, skip it
- This prevents duplicate bookings if the same statement file is imported twice

### Recurring Pattern Matching
- During mapping, `BudgetTransactionMapper` optionally matches transactions against known recurring patterns (e.g., "Amazon every month")
- If matched, `templateId` is set to the template's UUID
- Data layer stores this link for future "apply recurring template" workflows

## When to Add New Types

### Add to this package if:
- You're introducing a new intermediate stage in the import pipeline (e.g., a validation error DTO)
- You need type-safe domain enums for parsing or mapping logic
- The type represents transient state (not persisted directly)

### Add to data layer instead if:
- The type maps directly to a database table (@Entity)
- It's a persistent domain entity (e.g., BudgetTransactionEntity, BudgetAccountEntity)

### Add to application layer instead if:
- The type is a use-case input/output (e.g., ImportRequest, ImportResult)
- It bundles domain objects with presentation metadata

## Related Packages

- **`budget/application/importing/`** — Import use-cases and the parsing pipeline
- **`budget/data/api/`** — Claude API client for PDF extraction (ClaudeStatementApiClient)
- **`budget/data/`** — Data access objects (dao/), entities (entity/), and repositories (repository/)
