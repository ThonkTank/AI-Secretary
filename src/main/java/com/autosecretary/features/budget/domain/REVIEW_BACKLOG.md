# Review Backlog — budget/domain

## Open Issues

- [nit] `recurring/RecurringBudgetTransaction.java:10` — `RecurringType` enum is nested inside `RecurringBudgetTransaction` but referenced by external callers. Full fix: extract `RecurringType` to a top-level `RecurringType.java` in `budget.domain.recurring`. The highest-affected file is `database/Converters.java` (above `budget/` feature tree); issue should eventually be tracked at project root or `database/` level once a dedicated refactor cycle is planned.
  **Deferred reason:** Requires updating data-layer entities and their Room converters — multi-layer refactor.

- [consider] `AmountParser.java` — pure string-parsing utility with no domain-type dependencies. Its only current consumer is `BudgetViewModel` (UI layer); the import pipeline does not use it. Keeping it in `domain/` preserves future reusability; moving to `ui/internal/` would co-locate it with its sole consumer. Revisit if a second consumer appears.

- [nit] `BudgetImportRepository.java:86` — `notifyBudgetDataUpdated()` is a non-persistence side effect living on a persistence interface. The Javadoc already flags this as a design compromise. Deferred: no clean fix without introducing a separate observer/callback boundary, which would be significant restructuring for a feature-complete app.

## Fixed This Run

- [✓] `BudgetRepository.java:97,105` — removed redundant `java.time.` qualifications on `LocalDate` in `createTransfer` and `updateTransfer` signatures (already imported)
- [✓] `RecurringTemplateScheduler.java:11` — made class `final` to match the pattern of all other utility classes in this package
- [✓] Added `domain/README.md` — package map, reading order, key design decisions
- [✓] `CategorySpendSummary.java` — added class Javadoc and field-level docs (including limitAmountCents=0 semantics)
- [✓] `MonthlyOverviewItem.java` — added class Javadoc and field-level docs for all 14 fields
- [✓] `importing/ImportTransactionType.java` — removed duplicate dead `/**` comment block
- [✓] `BudgetRepository.java` — added class-level Javadoc covering all five responsibility areas
