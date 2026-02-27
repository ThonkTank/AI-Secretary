# Review Backlog — budget/domain

## Open Issues

- [warning] `BudgetImportRepository.java:87` — `notifyBudgetDataUpdated()` is a UI lifecycle operation on a domain interface; its own Javadoc flags it; fix: move to a separate `BudgetDataNotifier` interface owned by the application layer, or trigger from the use case.
  **Deferred reason:** Requires coordinated changes to `BudgetImportRoomRepository` (data layer) and `BudgetImportUseCase` (application layer) — outside this scope.

- [warning] `MonthlyOverviewItem.java` — mutable public-field POJO inconsistent with the rest of the domain layer. Room's field-injection requirement means conversion to a record requires splitting into two distinct result types (one per query, since the two queries return different column sets). Safe fix: split into `MonthlyTransactionItem` (full view) and a simpler projection type.
  **Deferred reason:** Requires confirming which Room queries produce this type (`BudgetLookupDao`), then updating both the DAO and all callers in `BudgetRoomRepository` and `BudgetViewModel`. Cross-layer refactor.

- [nit] `RecurringBudgetTransaction.java:10` — `RecurringType` enum is nested inside `RecurringBudgetTransaction` but referenced by four unrelated classes: `DatePatternDetector`, `RecurringTemplateScheduler`, `RecurringScheduleParams`, `RecurringSuggestion`. All must import `RecurringBudgetTransaction` just to access the enum, creating a misleading ownership coupling — these classes are about scheduling/detection, not about transactions. Fix: extract `RecurringType` to a top-level `RecurringType.java` in `budget.domain` and update all import sites (including any data-layer entities that store the enum).
  **Deferred reason:** Requires updating data-layer entities (e.g. `BudgetRecurringTemplateEntity`) and their Room converters in addition to the 4 domain files — multi-layer refactor needed.

- [warning] `BudgetRepository.java:4–8` — Domain interface imports data-layer `@Entity` types (`BudgetAccount`, `BudgetCategory`, `BudgetLimit`, `BudgetTransactionEntity`) from `features.budget.data.entity`. This inverts the dependency hierarchy (domain should not depend on data); any Room annotation or schema change in the entity forces a domain-layer change. Fix: define lightweight domain value objects (plain Java records/classes, no Room annotations) for Account, Category, Transaction, and Limit; have the data layer implement the domain interface by mapping to/from those types.
  **Deferred reason:** Large-scale cross-layer refactor touching `BudgetRoomRepository`, `BudgetViewModel`, and all callers in the application layer. Needs a dedicated cycle.
