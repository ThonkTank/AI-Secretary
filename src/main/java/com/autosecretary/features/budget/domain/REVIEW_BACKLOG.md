# Review Backlog — budget/domain

## Open Issues

- [consider] `AmountParser.java` — pure string-parsing utility with no domain-type dependencies. Its only current consumer is `BudgetViewModel` (UI layer); the import pipeline does not use it. Keeping it in `domain/` preserves future reusability; moving to `ui/internal/` would co-locate it with its sole consumer. Revisit if a second consumer appears.

- [nit] `BudgetImportRepository.java:86` — `notifyBudgetDataUpdated()` is a non-persistence side effect living on a persistence interface. The Javadoc already flags this as a design compromise. Deferred: no clean fix without introducing a separate observer/callback boundary, which would be significant restructuring for a feature-complete app.

- [violation] `BudgetRepository.java:1–8` — Domain interface directly imports and exposes four Room `@Entity` types from the data layer (`BudgetTransactionEntity`, `BudgetAccount`, `BudgetCategory`, `BudgetLimit`). The dependency direction should flow data→domain, not domain→data. Fixing this would require introducing domain-layer counterparts for each entity and adding mapping in the Room implementation — significant refactoring for a feature-complete app. Deferred.

- [consider] `RecurringPatternDetector.java:76–79` — `isEligibleForDetection` checks both `!tx.isRecurring` and `tx.parentRecurringId == null`. Since every construction path goes through `RecurringBudgetTransaction.forImport()`, which derives `isRecurring` directly from `parentRecurringId`, the second check is redundant in practice. Removing it would make `isRecurring` the single canonical eligibility flag. Deferred: the extra guard is harmless and provides a small defensive net should new construction paths be added without using the factory.
