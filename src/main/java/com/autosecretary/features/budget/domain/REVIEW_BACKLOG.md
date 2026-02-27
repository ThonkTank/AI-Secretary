# Review Backlog — budget/domain

## Open Issues

- [nit] `recurring/RecurringBudgetTransaction.java:10` — `RecurringType` enum is nested inside `RecurringBudgetTransaction` but referenced by external callers. Full fix: extract `RecurringType` to a top-level `RecurringType.java` in `budget.domain.recurring`. The highest-affected file is `database/Converters.java` (above `budget/` feature tree); issue should eventually be tracked at project root or `database/` level once a dedicated refactor cycle is planned.
  **Deferred reason:** Requires updating data-layer entities and their Room converters — multi-layer refactor.

- [consider] `AmountParser.java` — pure string-parsing utility with no domain-type dependencies. Its only current consumer is `BudgetViewModel` (UI layer); the import pipeline does not use it. Keeping it in `domain/` preserves future reusability; moving to `ui/internal/` would co-locate it with its sole consumer. Revisit if a second consumer appears.
