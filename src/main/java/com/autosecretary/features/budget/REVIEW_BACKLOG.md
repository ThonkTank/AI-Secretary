# Review Backlog — features/budget (top-level)

Cross-cutting issues that span multiple sub-packages.

## Open Issues

### [consider] `BudgetRecurringTemplateEntity.recurringValue` is a dual-purpose field spanning data/domain boundary
**Files:** `data/entity/BudgetRecurringTemplateEntity.java:76`, `domain/internal/recurring/DatePatternDetector.java:181`
The entity stores `recurringValue` with different semantics depending on `recurringType`: day-of-month for `MONTHLY_DAY`, interval days for `INTERVAL`, always 0 for `MONTHLY_LAST`/`WEEKLY`. This is a silent convention that crosses the data/domain boundary. A proper fix would require a schema migration or a sealed hierarchy which is out of scope. Deferred.

### [warning] `updateTransaction` and `saveTransaction` have too many parameters
**Files:** `domain/BudgetRepository.java:65,69`, `data/repository/BudgetRoomRepository.java:119,140`
`updateTransaction` takes 7 positional parameters; `saveTransaction` takes 6. Callers rely on argument order and mistakes (swapping two `String` arguments, or passing the wrong `LocalDate`) are silently type-safe. The parameters form a natural data clump that also appears in `createTransfer` / `updateTransfer`.
**Fix suggestion:** Introduce a `TransactionFormInput` value record and replace the parameter lists. Requires changing `BudgetRepository` interface + callers in `BudgetViewModel`, `BudgetFragment`, `CreateTransferUseCase`, etc.
**Promoted from:** `data/repository/REVIEW_BACKLOG.md`

### [warning] Volatile duplication in repository mappers
**Files:** `data/repository/BudgetImportRoomRepository.java:139-178`, `application/importing/BudgetTransactionMapper.java`
Duplicate mapper methods `toEntity()` (in BudgetImportRoomRepository) and `toRecord()`/`toDomain()` (in BudgetTransactionMapper) mirror each other's field mapping. A bug fix in one must be replicated in the other.
**Fix suggestion:** Consolidate all mapping logic into `BudgetTransactionMapper`, have `BudgetImportRoomRepository` delegate to it.
**Promoted from:** `domain/importing/REVIEW_BACKLOG.md`

### [warning] ImportTransactionRecord has 11 fields (data clump)
**File:** `domain/importing/ImportTransactionRecord.java:5-17`
Record has 11 fields consistently grouped. Requires value object decomposition affecting all consuming files.
**Fix suggestion:** Group into sub-records (e.g. `TransactionCore`, `ImportMetadata`).
**Promoted from:** `domain/importing/REVIEW_BACKLOG.md`

### [nit] Duplicate file type detection logic in StatementFileParser
**File:** `application/importing/StatementFileParser.java:108-120`
`accepts()` and `isPdf()` both check file extension/MIME type. Opportunity to centralize into a `FileType` enum. Stable and self-contained; only matters if file types expand.

### [warning] BudgetViewModel constructor takes 10 parameters
**File:** `ui/BudgetViewModel.java:78-98`
Constructor accepts 10 arguments spanning three logical groups (infrastructure, use cases, presentation helpers). Adding a dependency requires changing `BudgetViewModelFactory`, `AppCompositionRoot`, and the constructor simultaneously.
**Fix suggestion:** Group use-case dependencies into a `BudgetUseCases` holder; group presentation helpers similarly. Reduces to 3-4 arguments.

### [nit] BudgetViewModelFactory hidden inline construction
**File:** `ui/BudgetViewModelFactory.java:44-59`
`create()` constructs `CalculateEffectiveBudgetLimitUseCase` and `BudgetSeedService` inline rather than receiving them through the constructor. Mixed injection/factory patterns within one class.
**Fix suggestion:** Inject all collaborators through the factory constructor, or document the intentional inline construction.
