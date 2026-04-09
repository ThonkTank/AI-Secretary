# Review Backlog — features/budget (top-level)

Cross-cutting issues that span multiple sub-packages.

## Open Issues

### [consider] Decompose `ImportTransactionRecord` into sub-records @skill:review-simplicity
**File:** `domain/importing/ImportTransactionRecord.java`
The 11-field record mixes essential transaction data with import metadata. Could be split into sub-records for better cohesion, but the record is stable and not causing bugs. Defer until the domain is revisited.

### [consider] Group `BudgetViewModel` constructor dependencies into holder objects @skill:review-simplicity
**Files:** `ui/BudgetViewModel.java:96-104`, `ui/BudgetViewModelFactory.java`, `app/AppCompositionRoot.java`
Constructor accepts 9 arguments spanning three logical groups (infrastructure, use cases, presentation helpers). Not urgent since the app is feature-complete; defer unless the constructor grows further.

### [consider] `StatementFileParser` has data-layer dependency in `application/importing/` @skill:review-structure
**Paths:** `application/importing/StatementFileParser.java`
`StatementFileParser` directly imports `data.api.ClaudeStatementApiClient` and `data.api.ClaudeApiKeyStore`, making it the only file in `application/` that depends on data-layer infrastructure. Could be moved to `application/importing/internal/`. Net benefit is marginal with only 4 files in the package; defer unless the package grows.

### [consider] 1/3 Create Room projection for `MonthlyOverviewItem` in data/dao @skill:review-architecture
**Files:** `data/dao/BudgetTransactionDao.java` (queries returning `MonthlyOverviewItem`), `domain/MonthlyOverviewItem.java`
Room fills the mutable POJO in `domain/` directly from DAO queries. Creating a `data/dao/` projection and mapping to an immutable domain record would restore clean layer boundaries, but is a significant refactor.

### [consider] 2/3 Create immutable domain `MonthlyOverviewItem` record @skill:review-architecture
**Files:** `domain/MonthlyOverviewItem.java`, `data/repository/BudgetRoomRepository.java`
Part of the projection refactor — replace mutable POJO with immutable record.

### [consider] 3/3 Update presentation mappers for immutable `MonthlyOverviewItem` @skill:review-architecture
**Files:** `ui/internal/BudgetSummaryPresentationMapper.java`, `ui/internal/BudgetOverviewLoader.java`
Final step of projection refactor — update mappers to use immutable domain records.

### [consider] 1/6 Create immutable domain value objects for Account, Category, Transaction, and Limit @skill:review-architecture
**Files:** `domain/` (new files)
The domain interface (`BudgetRepository`) exposes `@Entity` data types throughout. Creating immutable domain records would decouple the domain contract from Room annotations. This is the correct architectural direction but a massive refactor touching every layer.

### [consider] 2/6 Update `BudgetRepository` interface to use domain value objects @skill:review-architecture
**File:** `domain/BudgetRepository.java`
Replace `@Entity` types in method signatures with domain records.

### [consider] 3/6 Implement read-side mappings in `BudgetRoomRepository` @skill:review-architecture
**File:** `data/repository/BudgetRoomRepository.java`
Map `@Entity` types to domain records on read.

### [consider] 4/6 Implement write-side mappings in `BudgetRoomRepository` @skill:review-architecture
**File:** `data/repository/BudgetRoomRepository.java`
Accept domain records and map to `@Entity` types on write.

### [consider] 5/6 Update application-layer callers to use domain types @skill:review-architecture
**Files:** `application/**/*UseCase.java`, application services
Refactor use cases to work with domain value objects.

### [consider] 6/6 Update UI-layer callers to use domain types @skill:review-architecture
**Files:** `ui/BudgetViewModel.java`, `ui/BudgetFragment.java`, presentation mappers
Update UI to work with domain value objects.

### [inconsistent] Use case threading pattern: some use cases own executor, others are synchronous @skill:review-conventions
**Observed patterns:**
- Synchronous use cases (caller manages threading): `CreateTransferUseCase.execute()`, `CalculateEffectiveBudgetLimitUseCase.execute()`, `LoadBudgetWidgetSummaryUseCase.execute()`
- Async use cases (own their executor, use callback): `BudgetImportUseCase.executeAsync()`, `ApplyRecurringSuggestionsUseCase.executeAsync()`
**Files:** `application/importing/BudgetImportUseCase.java`, `application/importing/ApplyRecurringSuggestionsUseCase.java`, `ui/BudgetViewModel.java:270-297,308-329`
**Problem:** The ViewModel already wraps import/recurring calls in `executor.execute()`, then the use case internally calls `executor.execute()` again (double-dispatch on same single-threaded executor). Both work correctly but the pattern is redundant and inconsistent with the other three use cases.
**Canonical:** Synchronous `execute()` — let the ViewModel manage threading uniformly.
**Impact:** 2 use cases restructured, ViewModel and AppCompositionRoot updated.

### [consider] `insertAccount`/`insertCategory` vs `create*` verb for write operations @skill:review-conventions
**Observed patterns:**
- `BudgetRepository`: `insertAccount()`, `insertCategory()` — raw DAO pass-through
- `BudgetImportRepository`: `createImport()`, `createRecurringTemplate()` — higher-level construction + persist
**Files:** `domain/BudgetRepository.java:38,40`, `domain/BudgetImportRepository.java:63,81`
**Canonical:** The difference is arguably intentional: `insert*` for pass-through operations, `create*` for operations that construct + persist. No change recommended unless the distinction is deemed unnecessary.

### [consider] `RecurringBudgetTransaction` is a mutable class where a record would suffice @skill:review-conventions
**File:** `domain/recurring/RecurringBudgetTransaction.java`
**Problem:** All other comparable domain value objects are records (e.g., `RecurringSuggestion`, `RecurringScheduleParams`, `TemplateStatusUpdate`, `ImportTransactionRecord`). `RecurringBudgetTransaction` is a mutable class with public fields and a static factory. No Room mapping requirement justifies the mutability.
**Canonical:** Convert to a Java record with the same fields. The static factory `forImport()` logic (deriving `isRecurring` from `parentRecurringId`) can live in a compact constructor.
**Impact:** 1 class rewritten, callers that set fields directly (in `BudgetImportRoomRepository.toRecord()` mapping) would need adjustment.
